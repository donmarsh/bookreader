package org.marshsoft.bookreader.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTest {
    @Test
    fun book_constructor_setsProperties() {
        val book = Book(
            id = "1",
            title = "Title",
            author = "Author",
            progress = 0.5f,
            coverUrl = "cover",
            filePath = "path",
            fileType = "epub",
            quote = "quote"
        )
        assertEquals("1", book.id)
        assertEquals("Title", book.title)
        assertEquals("Author", book.author)
        assertEquals(0.5f, book.progress)
        assertEquals("cover", book.coverUrl)
        assertEquals("path", book.filePath)
        assertEquals("epub", book.fileType)
        assertEquals("quote", book.quote)
    }
}
