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
                            lastReadTimestamp = remoteTimestamp
                        ))
                    } else {
                        remoteDoc.set(mapOf(
                            "title" to book.title,
                            "author" to book.author,
                            "fileType" to book.fileType
                        ), SetOptions.merge()).await()
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun syncBook(book: BookEntity, activityContext: Context? = null) {
        if (syncPreferences.isSyncEnabled) {
            syncProgress(book)
        } else if (syncPreferences.isDriveSyncEnabled) {
            syncBookMetadata(book, reconcileProgress = false)
        }
        uploadBook(book, activityContext)
    }

    suspend fun uploadBook(book: BookEntity, activityContext: Context? = null) {
        if (!syncPreferences.isDriveSyncEnabled) return
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
            googleDriveRepository.uploadFile(accessToken, file, identifier, book.fileType)
        }
    }

    fun syncAll(activityContext: Context? = null): Flow<SyncStatus> {
        if (syncStatus.value is SyncStatus.Progress) return syncStatus
        
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

            val localBooks = bookDao.getAllBooksOnce()
            
            // 1. Sync local metadata to Firestore first
            if (localBooks.isNotEmpty()) {
                _syncStatus.value = SyncStatus.Progress(0, localBooks.size, "Syncing local library...")
                for ((index, book) in localBooks.withIndex()) {
                    _syncStatus.value = SyncStatus.Progress(index + 1, localBooks.size, "Syncing ${book.title}...")
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
            
            for ((index, doc) in sortedDocs.withIndex()) {
                val identifier = doc.getString("identifier") ?: doc.id
                val title = doc.getString("title") ?: "Unknown Title"
                val author = doc.getString("author") ?: "Unknown Author"
                
                _syncStatus.value = SyncStatus.Progress(index + 1, totalSteps, "Syncing $title...")
                
                var localBook = bookDao.getBookByIdentifier(identifier)
                if (localBook == null) {
                    localBook = bookDao.getBookByTitleAndAuthor(title, author)
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
                val file = if (localBook.filePath.isNotEmpty()) java.io.File(localBook.filePath) else null
                if ((file == null || !file.exists()) && syncPreferences.isDriveSyncEnabled) {
                    // Try with activity context first if available, then fallback to app context
                    var accessToken = activityContext?.let { authRepository.getAccessToken(it) }
                    if (accessToken == null) {
                        accessToken = authRepository.getAccessToken(context)
                    }

                    if (accessToken != null) {
                        val destinationFile = java.io.File(context.filesDir, "book_${System.currentTimeMillis()}_${identifier}.${localBook.fileType}")
                        if (googleDriveRepository.downloadFile(accessToken, identifier, destinationFile)) {
                            val publication = bookParser.parsePublication(destinationFile)
                            val coverPath = publication?.let { pub ->
                                bookParser.getCover(pub)?.let { bitmap ->
                                    bookParser.saveBitmapToInternal(bitmap, "cover_${System.currentTimeMillis()}.png")
                                }
                            }
                            bookDao.updateBook(localBook.copy(
                                filePath = destinationFile.absolutePath,
                                coverPath = coverPath ?: localBook.coverPath
                            ))
                        }
                    }
                }
            }
            
            _syncStatus.value = SyncStatus.Success
        } catch (e: Exception) {
            e.printStackTrace()
            val error = e.message ?: "Unknown error occurred during sync"
            _syncStatus.value = SyncStatus.Error(error)
        }
    }

    fun clearSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }

    suspend fun deleteRemoteBook(book: BookEntity, deleteFromDrive: Boolean, activityContext: Context? = null) {
        val user = authRepository.currentUser.value ?: return
        val identifier = normalizedBookIdentifier(book)
        val documentId = firestoreBookId(identifier)

        // 1. Delete from Firestore
        try {
            firestore.collection("users").document(user.id)
                .collection("books").document(documentId)
                .delete().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Optionally delete from Google Drive
        if (deleteFromDrive) {
            try {
                val accessToken = authRepository.getAccessToken(activityContext ?: context)
                if (accessToken != null) {
                    googleDriveRepository.deleteFile(accessToken, identifier)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
