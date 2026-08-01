package org.marshsoft.bookreader.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.util.BookParser

class SyncRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val googleDriveRepository: GoogleDriveRepository,
    private val bookParser: BookParser
) {
    sealed class SyncStatus {
        object Idle : SyncStatus()
        data class Progress(val current: Int, val total: Int, val message: String) : SyncStatus()
        object Success : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private val firestore: FirebaseFirestore by lazy { Firebase.firestore }
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Lets UI distinguish a user-initiated sync (show progress on the main screen) from the
    // hourly background SyncWorker run (progress only surfaced in Settings).
    private val _isManualSync = MutableStateFlow(false)
    val isManualSync: StateFlow<Boolean> = _isManualSync.asStateFlow()

    private var syncJob: kotlinx.coroutines.Job? = null

    private fun normalizedBookIdentifier(book: BookEntity): String {
        return book.identifier?.takeIf { it.isNotBlank() }
            ?: "${book.title}_${book.author}"
    }

    private fun firestoreBookId(identifier: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(identifier.trim().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun syncProgress(book: BookEntity) {
        if (!syncPreferences.isSyncEnabled) return
        syncBookMetadata(book, reconcileProgress = true)
    }

    private suspend fun syncBookMetadata(book: BookEntity, reconcileProgress: Boolean) {
        val user = authRepository.currentUser.value ?: return
        val identifier = normalizedBookIdentifier(book)
        val documentId = firestoreBookId(identifier)
        
        val remoteDoc = firestore.collection("users").document(user.id)
            .collection("books").document(documentId)
        
        try {
            val remoteData = remoteDoc.get().await()
            if (remoteData.exists()) {
                val remoteTimestamp = remoteData.getLong("lastReadTimestamp") ?: 0L
                if (remoteTimestamp > book.lastReadTimestamp) {
                    if (reconcileProgress) {
                        // Remote is newer, update local
                        val progress = remoteData.getDouble("progress")?.toFloat() ?: 0f
                        val location = remoteData.getString("lastReadLocation")
                        bookDao.updateBook(book.copy(
                            progress = progress,
                            lastReadLocation = location,
                            lastReadTimestamp = remoteTimestamp,
                            lastSyncedTimestamp = remoteTimestamp
                        ))
                    } else {
                        remoteDoc.set(mapOf(
                            "title" to book.title,
                            "author" to book.author,
                            "fileType" to book.fileType
                        ), SetOptions.merge()).await()
                        bookDao.updateBook(book.copy(lastSyncedTimestamp = book.lastReadTimestamp))
                    }
                    return
                }
            }

            // Local is newer or remote doesn't exist, update remote
            remoteDoc.set(mapOf(
                "identifier" to identifier,
                "title" to book.title,
                "author" to book.author,
                "fileType" to book.fileType,
                "progress" to book.progress,
                "lastReadLocation" to book.lastReadLocation,
                "lastReadTimestamp" to book.lastReadTimestamp
            )).await()
            bookDao.updateBook(book.copy(lastSyncedTimestamp = book.lastReadTimestamp))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncBook(book: BookEntity, activityContext: Context? = null) {
        // Skip the Firestore round-trip entirely when nothing has changed locally since the
        // last successful push - remote-side updates from other devices are still picked up
        // separately when performSyncAll pulls the remote book list.
        val needsMetadataSync = book.lastSyncedTimestamp < 0 || book.lastReadTimestamp > book.lastSyncedTimestamp
        if (needsMetadataSync) {
            if (syncPreferences.isSyncEnabled) {
                syncProgress(book)
            } else if (syncPreferences.isDriveSyncEnabled) {
                syncBookMetadata(book, reconcileProgress = false)
            }
        }
        uploadBook(book, activityContext)
    }

    suspend fun uploadBook(book: BookEntity, activityContext: Context? = null) {
        if (!syncPreferences.isDriveSyncEnabled) return
        if (book.isUploaded) return // Already on Drive - skip the redundant upload on every sync
        authRepository.currentUser.value ?: return
        val identifier = normalizedBookIdentifier(book)
        val file = java.io.File(book.filePath)
        if (!file.exists()) return

        // Try with activity context first if available, then fallback to app context
        var accessToken = activityContext?.let { authRepository.getAccessToken(it) }
        if (accessToken == null) {
            accessToken = authRepository.getAccessToken(context)
        }

        if (accessToken != null) {
            val folderId = googleDriveRepository.getOrCreateAppFolder(accessToken)
            val fileId = googleDriveRepository.uploadFile(accessToken, file, identifier, book.fileType, folderId)
            if (fileId != null) {
                bookDao.updateBook(book.copy(isUploaded = true))
            }
        }
    }

    /**
     * One-time cleanup: moves book files uploaded to Drive's root before the app's dedicated
     * folder existed into that folder, so a user's Drive stays organized instead of everything
     * landing loose in "My Drive".
     */
    private suspend fun organizeDriveFolderIfNeeded(activityContext: Context? = null) {
        if (syncPreferences.isDriveFolderOrganized) return
        if (!syncPreferences.isDriveSyncEnabled) return

        var accessToken = activityContext?.let { authRepository.getAccessToken(it) }
        if (accessToken == null) {
            accessToken = authRepository.getAccessToken(context)
        }
        if (accessToken == null) return

        val folderId = googleDriveRepository.getOrCreateAppFolder(accessToken) ?: return
        val movedCount = googleDriveRepository.moveAllFilesToFolder(accessToken, folderId)
        if (movedCount != null) {
            syncPreferences.isDriveFolderOrganized = true
        }
    }

    fun syncAll(activityContext: Context? = null, isManual: Boolean = true): Flow<SyncStatus> {
        if (syncStatus.value is SyncStatus.Progress) return syncStatus

        _isManualSync.value = isManual
        syncJob?.cancel()
        syncJob = repositoryScope.launch {
            performSyncAll(activityContext)
        }
        return syncStatus
    }

    private suspend fun performSyncAll(activityContext: Context? = null) {
        if (!syncPreferences.isSyncEnabled && !syncPreferences.isDriveSyncEnabled) {
            val error = "Sync is disabled in settings"
            _syncStatus.value = SyncStatus.Error(error)
            return
        }
        
        val user = authRepository.currentUser.value
        if (user == null) {
            val error = "User not signed in"
            _syncStatus.value = SyncStatus.Error(error)
            return
        }
        
        try {
            _syncStatus.value = SyncStatus.Progress(0, 1, "Starting sync...")

            organizeDriveFolderIfNeeded(activityContext)

            val localBooks = bookDao.getAllBooksOnce()
            
            // 1. Sync local metadata to Firestore first
            if (localBooks.isNotEmpty()) {
                _syncStatus.value = SyncStatus.Progress(0, localBooks.size, "Syncing local library...")
                for ((index, book) in localBooks.withIndex()) {
                    _syncStatus.value = SyncStatus.Progress(index + 1, localBooks.size, "Syncing ${book.title}...")
                    // Re-check the book still exists locally: a large library sync can take a
                    // while, and if the user deletes a book mid-sync, this stale snapshot would
                    // otherwise re-upload its metadata to Firestore right after the delete,
                    // resurrecting it there for the next sync/reinstall to pull back down.
                    if (bookDao.getBookById(book.id) == null) continue
                    syncBook(book, activityContext)
                }
            }

            _syncStatus.value = SyncStatus.Progress(0, 1, "Fetching remote library...")
            
            // 2. Fetch all books from Firestore
            val remoteBooksQuery = firestore.collection("users").document(user.id)
                .collection("books").get().await()
            
            val remoteDocs = remoteBooksQuery.documents
            // Sort by lastReadTimestamp DESC to prioritize the most recently read book
            val sortedDocs = remoteDocs.sortedByDescending { it.getLong("lastReadTimestamp") ?: 0L }
            val totalSteps = sortedDocs.size
            
            _syncStatus.value = SyncStatus.Progress(0, totalSteps.coerceAtLeast(1), "Processing remote books...")

            // A book's identifier can change over time (e.g. blank at first upload, then a real
            // EPUB identifier once re-parsed), which leaves behind duplicate Firestore documents
            // for the same book - inflating the remote count and letting a deleted book resurface
            // from whichever duplicate the delete didn't target. Track the first doc we keep per
            // local book and delete any later doc that maps to the same one.
            val keptDocIdByLocalBookId = mutableMapOf<Long, String>()

            for ((index, doc) in sortedDocs.withIndex()) {
                val identifier = doc.getString("identifier") ?: doc.id
                val title = doc.getString("title") ?: "Unknown Title"
                val author = doc.getString("author") ?: "Unknown Author"

                _syncStatus.value = SyncStatus.Progress(index + 1, totalSteps, "Syncing $title...")

                var localBook = bookDao.getBookByIdentifier(identifier)
                if (localBook == null) {
                    localBook = bookDao.getBookByTitleAndAuthor(title, author)
                }

                val existingLocalBookId = localBook?.id
                if (existingLocalBookId != null) {
                    val keptDocId = keptDocIdByLocalBookId[existingLocalBookId]
                    if (keptDocId != null && keptDocId != doc.id) {
                        // Duplicate cloud record for a book we've already processed this sync - remove it.
                        try {
                            doc.reference.delete().await()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        continue
                    }
                    keptDocIdByLocalBookId[existingLocalBookId] = doc.id
                }

                if (localBook == null) {
                    val newBook = BookEntity(
                        title = title,
                        author = author,
                        identifier = identifier,
                        progress = doc.getDouble("progress")?.toFloat() ?: 0f,
                        lastReadLocation = doc.getString("lastReadLocation"),
                        lastReadTimestamp = doc.getLong("lastReadTimestamp") ?: 0L,
                        filePath = "",
                        fileType = doc.getString("fileType") ?: "epub"
                    )
                    val id = bookDao.insertBook(newBook)
                    localBook = newBook.copy(id = id)
                    keptDocIdByLocalBookId[id] = doc.id
                } else {
                    val remoteTimestamp = doc.getLong("lastReadTimestamp") ?: 0L
                    if (remoteTimestamp > localBook.lastReadTimestamp) {
                        val progress = doc.getDouble("progress")?.toFloat() ?: 0f
                        val location = doc.getString("lastReadLocation")
                        bookDao.updateBook(localBook.copy(
                            progress = progress,
                            lastReadLocation = location,
                            lastReadTimestamp = remoteTimestamp
                        ))
                    }
                }
                
                // 3. Prioritized Restore from Drive
                restoreBookFileFromDrive(localBook, identifier, activityContext)
            }

            _syncStatus.value = SyncStatus.Success
        } catch (e: Exception) {
            e.printStackTrace()
            val error = e.message ?: "Unknown error occurred during sync"
            _syncStatus.value = SyncStatus.Error(error)
        }
    }

    /**
     * Downloads [book]'s file from Drive and updates its local record if the file is missing.
     * Returns true if the file was (re)downloaded.
     */
    private suspend fun restoreBookFileFromDrive(
        book: BookEntity,
        identifier: String,
        activityContext: Context? = null
    ): Boolean {
        val file = if (book.filePath.isNotEmpty()) java.io.File(book.filePath) else null
        if (file != null && file.exists()) return false
        if (!syncPreferences.isDriveSyncEnabled) return false

        // Try with activity context first if available, then fallback to app context
        var accessToken = activityContext?.let { authRepository.getAccessToken(it) }
        if (accessToken == null) {
            accessToken = authRepository.getAccessToken(context)
        }
        if (accessToken == null) return false

        val destinationFile = java.io.File(context.filesDir, "book_${System.currentTimeMillis()}_${identifier}.${book.fileType}")
        if (!googleDriveRepository.downloadFile(accessToken, identifier, destinationFile)) return false

        val publication = bookParser.parsePublication(destinationFile)
        val coverPath = publication?.let { pub ->
            bookParser.getCover(pub)?.let { bitmap ->
                bookParser.saveBitmapToInternal(bitmap, "cover_${System.currentTimeMillis()}.png")
            }
        }
        bookDao.updateBook(book.copy(
            filePath = destinationFile.absolutePath,
            coverPath = coverPath ?: book.coverPath,
            isUploaded = true // A successful download means the file already exists on Drive
        ))
        return true
    }

    /**
     * Retries pulling a single book's file from Drive, for when it's stuck showing as
     * "syncing" after a failed remote pull (e.g. a dropped connection during [syncAll]).
     */
    suspend fun pullBook(book: BookEntity, activityContext: Context? = null): Boolean {
        if (authRepository.currentUser.value == null) return false
        val identifier = normalizedBookIdentifier(book)
        return try {
            restoreBookFileFromDrive(book, identifier, activityContext)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    /**
     * Deletes [book]'s cloud record(s) so it doesn't reappear on the next sync/reinstall.
     * A book's identifier can drift over time (blank at first upload, then a real EPUB
     * identifier once re-parsed), leaving duplicate Firestore documents for the same title+
     * author under different identifiers/document IDs. Deleting only the single doc computed
     * from the book's *current* identifier can miss those duplicates, letting the book resurface
     * from one of them on the next sync - so this removes every matching doc (and every Drive
     * file referenced by them), not just one.
     * Returns false if any Firestore document deletion failed (e.g. network error) - callers
     * should surface this rather than silently proceeding as if the book was fully forgotten.
     */
    suspend fun deleteRemoteBook(book: BookEntity, deleteFromDrive: Boolean, activityContext: Context? = null): Boolean {
        val user = authRepository.currentUser.value ?: return false
        val identifier = normalizedBookIdentifier(book)
        val documentId = firestoreBookId(identifier)
        val booksCollection = firestore.collection("users").document(user.id).collection("books")

        val matchingDocs = try {
            booksCollection
                .whereEqualTo("title", book.title)
                .whereEqualTo("author", book.author)
                .get().await().documents
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        // Always include the doc derived from the book's current identifier, in case the
        // title/author query missed it (e.g. stale/edited metadata).
        val docIdsToDelete = (matchingDocs.map { it.id } + documentId).toSet()
        val identifiersToDeleteFromDrive = (matchingDocs.mapNotNull { it.getString("identifier") } + identifier).toSet()

        // 1. Delete every matching Firestore document
        var firestoreDeleted = true
        for (docId in docIdsToDelete) {
            try {
                booksCollection.document(docId).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
                firestoreDeleted = false
            }
        }

        // 2. Optionally delete from Google Drive - every identifier variant this book was ever
        // uploaded under, so no duplicate Drive file is left behind either.
        if (deleteFromDrive) {
            try {
                val accessToken = authRepository.getAccessToken(activityContext ?: context)
                if (accessToken != null) {
                    for (id in identifiersToDeleteFromDrive) {
                        googleDriveRepository.deleteFile(accessToken, id)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return firestoreDeleted
    }
}
