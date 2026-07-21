package org.marshsoft.bookreader.data.repository

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.data.local.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

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

    suspend fun syncAll(activityContext: Context? = null): Flow<SyncStatus> = flow {
        if (!syncPreferences.isSyncEnabled) {
            emit(SyncStatus.Error("Sync is disabled in settings"))
            return@flow
        }
        
        try {
            val books = bookDao.getAllBooks().first()
            if (books.isEmpty()) {
                emit(SyncStatus.Success)
                return@flow
            }

            emit(SyncStatus.Progress(0, books.size, "Starting sync..."))
            
            for ((index, book) in books.withIndex()) {
                emit(SyncStatus.Progress(index + 1, books.size, "Syncing ${book.title}..."))
                syncProgress(book)
                
                // If book file is missing and Drive sync is enabled, try to restore
                val file = java.io.File(book.filePath)
                if (!file.exists() && syncPreferences.isDriveSyncEnabled) {
                    val accessToken = authRepository.getAccessToken(activityContext ?: context)
                    if (accessToken != null) {
                        val identifier = book.identifier ?: "${book.title}_${book.author}".hashCode().toString()
                        googleDriveRepository.downloadFile(accessToken, identifier, file)
                    }
                }
            }
            emit(SyncStatus.Success)
        } catch (e: Exception) {
            e.printStackTrace()
            emit(SyncStatus.Error(e.message ?: "Unknown error occurred during sync"))
        }
    }
}
