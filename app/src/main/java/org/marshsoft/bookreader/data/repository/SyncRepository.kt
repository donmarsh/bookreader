package org.marshsoft.bookreader.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity

class SyncRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val googleDriveRepository: GoogleDriveRepository
) {
    sealed class SyncStatus {
        object Idle : SyncStatus()
        data class Progress(val current: Int, val total: Int, val message: String) : SyncStatus()
        object Success : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    private val firestore: FirebaseFirestore = Firebase.firestore

    suspend fun syncProgress(book: BookEntity) {
        if (!syncPreferences.isSyncEnabled) return
        val user = authRepository.currentUser.value ?: return
        val identifier = book.identifier ?: "${book.title}_${book.author}".hashCode().toString()
        
        val remoteDoc = firestore.collection("users").document(user.id)
            .collection("books").document(identifier)
        
        try {
            val remoteData = remoteDoc.get().await()
            if (remoteData.exists()) {
                val remoteTimestamp = remoteData.getLong("lastReadTimestamp") ?: 0L
                if (remoteTimestamp > book.lastReadTimestamp) {
                    // Remote is newer, update local
                    val progress = remoteData.getDouble("progress")?.toFloat() ?: 0f
                    val location = remoteData.getString("lastReadLocation")
                    bookDao.updateBook(book.copy(
                        progress = progress,
                        lastReadLocation = location,
                        lastReadTimestamp = remoteTimestamp
                    ))
                    return
                }
            }
            
            // Local is newer or remote doesn't exist, update remote
            remoteDoc.set(mapOf(
                "title" to book.title,
                "author" to book.author,
                "progress" to book.progress,
                "lastReadLocation" to book.lastReadLocation,
                "lastReadTimestamp" to book.lastReadTimestamp
            )).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun uploadBook(book: BookEntity, activityContext: Context? = null) {
        if (!syncPreferences.isDriveSyncEnabled) return
        authRepository.currentUser.value ?: return
        val identifier = book.identifier ?: "${book.title}_${book.author}".hashCode().toString()
        val file = java.io.File(book.filePath)
        if (!file.exists()) return

        val accessToken = authRepository.getAccessToken(activityContext ?: context) ?: return
        googleDriveRepository.uploadFile(accessToken, file, identifier, book.fileType)
    }

    fun syncAll(activityContext: Context? = null): Flow<SyncStatus> = flow {
        if (!syncPreferences.isSyncEnabled) {
            emit(SyncStatus.Error("Sync is disabled in settings"))
            return@flow
        }
        
        val user = authRepository.currentUser.value
        if (user == null) {
            emit(SyncStatus.Error("User not signed in"))
            return@flow
        }
        
        try {
            emit(SyncStatus.Progress(0, 1, "Fetching remote library..."))
            
            // 1. Fetch all books from Firestore
            val remoteBooksQuery = firestore.collection("users").document(user.id)
                .collection("books").get().await()
            
            val remoteDocs = remoteBooksQuery.documents
            val totalSteps = remoteDocs.size
            
            emit(SyncStatus.Progress(0, totalSteps.coerceAtLeast(1), "Processing remote books..."))
            
            for ((index, doc) in remoteDocs.withIndex()) {
                val identifier = doc.id
                val title = doc.getString("title") ?: "Unknown Title"
                val author = doc.getString("author") ?: "Unknown Author"
                
                emit(SyncStatus.Progress(index + 1, totalSteps, "Syncing $title..."))
                
                var localBook = bookDao.getBookByIdentifier(identifier)
                if (localBook == null) {
                    // Try by title and author as fallback
                    localBook = bookDao.getBookByTitleAndAuthor(title, author)
                }
                
                if (localBook == null) {
                    // Create placeholder local book entry
                    // Note: file and cover will be downloaded if Drive sync is enabled
                    val newBook = BookEntity(
                        title = title,
                        author = author,
                        identifier = identifier,
                        progress = doc.getDouble("progress")?.toFloat() ?: 0f,
                        lastReadLocation = doc.getString("lastReadLocation"),
                        lastReadTimestamp = doc.getLong("lastReadTimestamp") ?: 0L,
                        filePath = "", // Temporary empty, will be updated if downloaded
                        fileType = doc.getString("fileType") ?: "epub"
                    )
                    val id = bookDao.insertBook(newBook)
                    localBook = newBook.copy(id = id)
                } else {
                    // Update existing local book if remote is newer
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
                
                // 2. If book file is missing and Drive sync is enabled, try to restore
                val file = if (localBook.filePath.isNotEmpty()) java.io.File(localBook.filePath) else null
                if ((file == null || !file.exists()) && syncPreferences.isDriveSyncEnabled) {
                    val accessToken = authRepository.getAccessToken(activityContext ?: context)
                    if (accessToken != null) {
                        val destinationFile = java.io.File(context.filesDir, "book_${System.currentTimeMillis()}_${identifier}.${localBook.fileType}")
                        if (googleDriveRepository.downloadFile(accessToken, identifier, destinationFile)) {
                            bookDao.updateBook(localBook.copy(filePath = destinationFile.absolutePath))
                        }
                    }
                }
            }
            
            emit(SyncStatus.Success)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(SyncStatus.Error(e.message ?: "Unknown error occurred during sync"))
        }
    }

    suspend fun deleteRemoteBook(book: BookEntity, deleteFromDrive: Boolean, activityContext: Context? = null) {
        val user = authRepository.currentUser.value ?: return
        val identifier = book.identifier ?: "${book.title}_${book.author}".hashCode().toString()

        // 1. Delete from Firestore
        try {
            firestore.collection("users").document(user.id)
                .collection("books").document(identifier)
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
