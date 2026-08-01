package org.marshsoft.bookreader.ui.screens.reader

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.data.repository.SyncRepository
import org.marshsoft.bookreader.util.BookParser
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private val bookDao = mockk<BookDao>(relaxed = true)
    private val bookParser = mockk<BookParser>(relaxed = true)
    private val syncRepository = mockk<SyncRepository>(relaxed = true)
    private val syncPreferences = mockk<SyncPreferences>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val entity = BookEntity(
        id = 1L,
        title = "Title",
        author = "Author",
        filePath = "/path/book.epub",
        fileType = "epub"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { syncPreferences.readerFontSize } returns 1.0f
        every { syncPreferences.readerTheme } returns 0
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(bookId: String = "1") = ReaderViewModel(
        bookId = bookId,
        bookDao = bookDao,
        bookParser = bookParser,
        syncRepository = syncRepository,
        syncPreferences = syncPreferences
    )

    @Test
    fun loadBook_invalidId_setsError() = runTest {
        val viewModel = createViewModel(bookId = "not-a-number")
        advanceUntilIdle()

        assertEquals("Invalid Book ID", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadBook_bookNotFound_setsError() = runTest {
        coEvery { bookDao.getBookById(1L) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("Book not found", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadBook_success_populatesBook() = runTest {
        coEvery { bookDao.getBookById(1L) } returns entity
        coEvery { bookDao.updateBook(any()) } returns Unit
        coEvery { syncRepository.syncProgress(any()) } returns Unit
        coEvery { bookParser.parsePublication(any<File>()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("Title", state.book?.title)
        assertEquals("Author", state.book?.author)
        coVerify { syncRepository.syncProgress(any()) }
    }

    @Test
    fun updateProgress_updatesEntityAndSyncsProgress() = runTest {
        coEvery { bookDao.getBookById(1L) } returns entity
        coEvery { bookDao.updateBook(any()) } returns Unit
        coEvery { syncRepository.syncProgress(any()) } returns Unit
        coEvery { bookParser.parsePublication(any<File>()) } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        val locator = mockk<Locator>(relaxed = true)
        every { locator.locations.totalProgression } returns 0.5
        every { locator.locations.progression } returns 0.25

        val updatedEntities = mutableListOf<BookEntity>()
        viewModel.updateProgress(locator)
        advanceUntilIdle()

        coVerify { bookDao.updateBook(capture(updatedEntities)) }
        assertEquals(0.5f, updatedEntities.last().progress)
        assertEquals("Title", viewModel.uiState.value.book?.title)
    }

    @Test
    fun toggleHud_flipsVisibility() = runTest {
        coEvery { bookDao.getBookById(1L) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        val initial = viewModel.uiState.value.isHudVisible
        viewModel.toggleHud()
        assertEquals(!initial, viewModel.uiState.value.isHudVisible)
    }

    @Test
    fun updateTheme_persistsAndUpdatesState() = runTest {
        coEvery { bookDao.getBookById(1L) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateTheme(Theme.DARK)

        io.mockk.verify { syncPreferences.readerTheme = 1 }
        assertEquals(Theme.DARK, viewModel.uiState.value.preferences.theme)
    }

    @Test
    fun updateFontSize_persistsAndUpdatesState() = runTest {
        coEvery { bookDao.getBookById(1L) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateFontSize(1.8)

        io.mockk.verify { syncPreferences.readerFontSize = 1.8f }
        assertEquals(1.8, viewModel.uiState.value.preferences.fontSize)
    }

    @Test
    fun seekTo_withNoPublication_doesNothing() = runTest {
        coEvery { bookDao.getBookById(1L) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.seekTo(0.5f)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingLocator)
    }

    @Test
    fun onLocatorConsumed_clearsPendingLocator() = runTest {
        coEvery { bookDao.getBookById(1L) } returns null
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onLocatorConsumed()

        assertNull(viewModel.uiState.value.pendingLocator)
    }
}
