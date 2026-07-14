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
import kotlinx.coroutines.flow.first

class SyncRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val authRepository: AuthRepository,
    private val syncPreferences: SyncPreferences,
    private val googleDriveRepository: GoogleDriveRepository
) {
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

    suspend fun uploadBook(book: BookEntity) {
        if (!syncPreferences.isDriveSyncEnabled) return
        authRepository.currentUser.value ?: return
        val identifier = book.identifier ?: "${book.title}_${book.author}".hashCode().toString()
        val file = java.io.File(book.filePath)
        if (!file.exists()) return

        val accessToken = authRepository.getAccessToken() ?: return
        googleDriveRepository.uploadFile(accessToken, file, identifier, book.fileType)
    }

    suspend fun syncAll() {
        if (!syncPreferences.isSyncEnabled) return
        try {
            val books = bookDao.getAllBooks().first()
            for (book in books) {
                syncProgress(book)
                
                // If book file is missing and Drive sync is enabled, try to restore
                val file = java.io.File(book.filePath)
                if (!file.exists() && syncPreferences.isDriveSyncEnabled) {
                    val accessToken = authRepository.getAccessToken()
                    if (accessToken != null) {
                        val identifier = book.identifier ?: "${book.title}_${book.author}".hashCode().toString()
                        googleDriveRepository.downloadFile(accessToken, identifier, file)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
