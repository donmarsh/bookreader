package org.marshsoft.bookreader.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    val fileType: String, // "epub" or "pdf"
    val coverPath: String? = null,
    val progress: Float = 0f,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val description: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val language: String? = null,
    val identifier: String? = null,
    val lastReadLocation: String? = null,
    val isUploaded: Boolean = false,
    // lastReadTimestamp at the point this book's metadata was last pushed to Firestore.
    // -1 means never synced. Lets a sync skip the Firestore round-trip entirely for books
    // whose reading position hasn't changed since the last successful push.
    val lastSyncedTimestamp: Long = -1L
)
