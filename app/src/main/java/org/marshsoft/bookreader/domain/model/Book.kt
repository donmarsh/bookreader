package org.marshsoft.bookreader.domain.model

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val progress: Float,
    val coverUrl: String?,
    val filePath: String,
    val fileType: String,
    val quote: String? = null
)
