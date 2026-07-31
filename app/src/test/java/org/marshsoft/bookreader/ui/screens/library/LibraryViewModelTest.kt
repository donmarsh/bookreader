package org.marshsoft.bookreader.ui.screens.library

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.data.repository.AuthRepository
import org.marshsoft.bookreader.data.repository.SyncRepository
import org.marshsoft.bookreader.util.BookParser
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val bookDao = mockk<BookDao>(relaxed = true)
    private val bookParser = mockk<BookParser>(relaxed = true)
    private val syncRepository = mockk<SyncRepository>(relaxed = true)
    private val syncPreferences = mockk<SyncPreferences>(relaxed = true)
    private val authRepository = mockk<AuthRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { syncPreferences.isFirstRun } returns false
        every { authRepository.currentUser } returns MutableStateFlow(null)
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
        every { bookDao.getAllBooks() } returns flowOf(bookEntities)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.books.test {
            skipItems(1)
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
        every { bookParser.getFileName(uri) } returns "test.epub"
        every { bookParser.saveFileToInternal(any(), any()) } returns "/internal/path"
        coEvery { bookParser.parsePublication(any<File>()) } returns null
        coEvery { bookDao.getBookByTitleAndAuthor(any(), any()) } returns null
        coEvery { bookDao.insertBook(any()) } returns 1L

        val viewModel = createViewModel()
        viewModel.importBook(context, uri)
        advanceUntilIdle()

        coVerify { bookDao.insertBook(any()) }
        coVerify { syncRepository.syncBook(any(), context) }
    }

    private fun createViewModel() = LibraryViewModel(
        bookDao = bookDao,
        bookParser = bookParser,
        syncRepository = syncRepository,
        syncPreferences = syncPreferences,
        authRepository = authRepository
    )
}
