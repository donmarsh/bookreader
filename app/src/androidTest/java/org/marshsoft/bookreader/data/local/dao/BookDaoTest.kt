package org.marshsoft.bookreader.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.marshsoft.bookreader.data.local.BookDatabase
import org.marshsoft.bookreader.data.local.entities.BookEntity

@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var database: BookDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BookDatabase::class.java
        ).allowMainThreadQueries().build()
        bookDao = database.bookDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetAllBooks() = runBlocking {
        val book = BookEntity(
            title = "Test Book",
            author = "Test Author",
            filePath = "/path/to/book",
            fileType = "epub"
        )
        bookDao.insertBook(book)

        val allBooks = bookDao.getAllBooks().first()
        assertEquals(1, allBooks.size)
        assertEquals("Test Book", allBooks[0].title)
    }

    @Test
    fun updateBookProgress() = runBlocking {
        val book = BookEntity(
            title = "Test Book",
            author = "Test Author",
            filePath = "/path/to/book",
            fileType = "epub"
        )
        val id = bookDao.insertBook(book)
        val insertedBook = bookDao.getBookById(id)!!
        
        val updatedBook = insertedBook.copy(progress = 0.5f)
        bookDao.updateBook(updatedBook)

        val result = bookDao.getBookById(id)!!
        assertEquals(0.5f, result.progress)
    }

    @Test
    fun deleteBook() = runBlocking {
        val book = BookEntity(
            title = "Test Book",
            author = "Test Author",
            filePath = "/path/to/book",
            fileType = "epub"
        )
        val id = bookDao.insertBook(book)
        val insertedBook = bookDao.getBookById(id)!!
        
        bookDao.deleteBook(insertedBook)

        val allBooks = bookDao.getAllBooks().first()
        assertEquals(0, allBooks.size)
    }
}
