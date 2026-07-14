package org.marshsoft.bookreader.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val progress: Float,
    val coverUrl: String?,
    val filePath: String,
    val fileType: String,
    val quote: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val language: String? = null,
    val identifier: String? = null,
    val lastReadLocation: String? = null
)
