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
    val lastReadLocation: String? = null
)
