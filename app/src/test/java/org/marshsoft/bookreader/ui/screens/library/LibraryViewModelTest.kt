package org.marshsoft.bookreader.ui.screens.library

import android.net.Uri
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.util.BookParser

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val bookDao = mockk<BookDao>(relaxed = true)
    private val bookParser = mockk<BookParser>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `books flow emits mapped domain models`() = runTest {
        val bookEntities = listOf(
            BookEntity(id = 1, title = "Book 1", author = "Author 1", filePath = "p1", fileType = "epub"),
            BookEntity(id = 2, title = "Book 2", author = "Author 2", filePath = "p2", fileType = "pdf")
        )
        coEvery { bookDao.getAllBooks() } returns flowOf(bookEntities)

        val viewModel = LibraryViewModel(bookDao, bookParser)
        advanceUntilIdle()

        viewModel.books.test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Book 1", result[0].title)
            assertEquals("Book 2", result[1].title)
        }
    }

    @Test
    fun `importBook inserts book into database`() = runTest {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "test.epub"
        coEvery { bookParser.saveFileToInternal(any(), any()) } returns "/internal/path"
        coEvery { bookParser.parseEpub(any()) } returns null // Simplify for now

        val viewModel = LibraryViewModel(bookDao, bookParser)
        viewModel.importBook(uri)
        advanceUntilIdle()

        coVerify { bookDao.insertBook(any()) }
    }
}
