package org.marshsoft.bookreader.ui.screens.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.domain.model.Book
import org.marshsoft.bookreader.util.BookParser

class LibraryViewModel(
    private val bookDao: BookDao,
    private val bookParser: BookParser
) : ViewModel() {

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books

    init {
        viewModelScope.launch {
            bookDao.getAllBooks().collectLatest { entities ->
                _books.value = entities.map { it.toDomain() }
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            val fileType = if (uri.toString().endsWith(".epub", true)) "epub" else "pdf"
            val fileName = "book_${System.currentTimeMillis()}.$fileType"
            val filePath = bookParser.saveFileToInternal(uri, fileName) ?: return@launch
            
            var title = "Unknown Title"
            var author = "Unknown Author"
            var coverPath: String? = null

            if (fileType == "epub") {
                val epubBook = bookParser.parseEpub(uri)
                if (epubBook != null) {
                    title = epubBook.title ?: title
                    author = epubBook.metadata.authors.firstOrNull()?.toString() ?: author
                    val coverBitmap = bookParser.getEpubCover(epubBook)
                    if (coverBitmap != null) {
                        coverPath = bookParser.saveBitmapToInternal(coverBitmap, "cover_${System.currentTimeMillis()}.png")
                    }
                }
            } else {
                val metadata = bookParser.getPdfMetadata(uri)
                if (metadata != null) {
                    title = metadata.first
                    author = metadata.second
                }
                val coverBitmap = bookParser.getPdfCover(uri)
                if (coverBitmap != null) {
                    coverPath = bookParser.saveBitmapToInternal(coverBitmap, "cover_${System.currentTimeMillis()}.png")
                }
            }

            val entity = BookEntity(
                title = title,
                author = author,
                filePath = filePath,
                fileType = fileType,
                coverPath = coverPath
            )
            bookDao.insertBook(entity)
        }
    }

    private fun BookEntity.toDomain() = Book(
        id = id.toString(),
        title = title,
        author = author,
        progress = progress,
        coverUrl = coverPath,
        filePath = filePath,
        fileType = fileType
    )
}
