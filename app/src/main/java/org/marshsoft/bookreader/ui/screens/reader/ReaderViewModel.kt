package org.marshsoft.bookreader.ui.screens.reader

import androidx.lifecycle.ViewModel
import android.text.Html
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.repository.SyncRepository
import org.marshsoft.bookreader.domain.model.Book
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.PositionsService
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.marshsoft.bookreader.util.BookParser
import org.readium.r2.shared.ExperimentalReadiumApi
import java.io.File

data class ReaderUiState @OptIn(ExperimentalReadiumApi::class) constructor(
    val book: Book? = null,
    val publication: Publication? = null,
    val totalPages: Int? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val preferences: EpubPreferences = EpubPreferences(publisherStyles = false),
    val isHudVisible: Boolean = true,
    val pendingLocator: Locator? = null
)

class ReaderViewModel(
    private val bookId: String,
    private val bookDao: BookDao,
    private val bookParser: BookParser,
    private val syncRepository: SyncRepository,
    private val syncPreferences: SyncPreferences
) : ViewModel() {

    @OptIn(ExperimentalReadiumApi::class)
    private val _uiState = MutableStateFlow(
        ReaderUiState(
            preferences = EpubPreferences(
                publisherStyles = false,
                fontSize = syncPreferences.readerFontSize.toDouble(),
                theme = when (syncPreferences.readerTheme) {
                    1 -> Theme.DARK
                    2 -> Theme.SEPIA
                    else -> Theme.LIGHT
                }
            )
        )
    )
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            try {
                val idLong = bookId.toLongOrNull()
                if (idLong == null) {
                    _uiState.value = ReaderUiState(isLoading = false, error = "Invalid Book ID")
                    return@launch
                }
                
                val entity = bookDao.getBookById(idLong)
                if (entity != null) {
                    // Update last read timestamp immediately when book is opened
                    val initialUpdatedEntity = entity.copy(lastReadTimestamp = System.currentTimeMillis())
                    bookDao.updateBook(initialUpdatedEntity)

                    // Reconciling progress against Firestore needs the network; without one this
                    // can hang well past a reasonable wait, so cap it instead of blocking the
                    // book from opening at all when offline.
                    withTimeoutOrNull(5000) {
                        syncRepository.syncProgress(initialUpdatedEntity)
                    }
                    val updatedEntity = bookDao.getBookById(idLong) ?: initialUpdatedEntity
                    val book = updatedEntity.toDomain()
                    val publication = bookParser.parsePublication(File(book.filePath))
                    val totalPages = publication?.findService(PositionsService::class)?.positions()?.size
                    
                    _uiState.value = _uiState.value.copy(
                        book = book,
                        publication = publication,
                        totalPages = totalPages,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Book not found")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun BookEntity.toDomain() = Book(
        id = id.toString(),
        title = title,
        author = author,
        progress = progress,
        coverUrl = coverPath,
        filePath = filePath,
        fileType = fileType,
        description = description?.let { 
            Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().trim()
        },
        publisher = publisher,
        publishedDate = publishedDate,
        language = language,
        identifier = identifier,
        lastReadLocation = lastReadLocation
    )

    fun updateProgress(locator: Locator) {
        viewModelScope.launch {
            try {
                val progress = locator.locations.totalProgression?.toFloat()
                    ?: locator.locations.progression?.toFloat()
                    ?: 0f
                val locationJson = locator.toJSON().toString()
                
                val idLong = bookId.toLongOrNull() ?: return@launch
                val entity = bookDao.getBookById(idLong)
                if (entity != null) {
                    val updatedBook = entity.copy(
                        progress = progress,
                        lastReadLocation = locationJson,
                        lastReadTimestamp = System.currentTimeMillis()
                    )
                    bookDao.updateBook(updatedBook)
                    _uiState.value = _uiState.value.copy(book = updatedBook.toDomain())

                    // Fire-and-forget: don't let a slow/absent network delay reflecting the new
                    // progress locally, and don't let repeated page turns stack up blocked calls.
                    withTimeoutOrNull(5000) {
                        syncRepository.syncProgress(updatedBook)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleHud() {
        _uiState.value = _uiState.value.copy(isHudVisible = !_uiState.value.isHudVisible)
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun updateTheme(theme: Theme) {
        syncPreferences.readerTheme = when (theme) {
            Theme.DARK -> 1
            Theme.SEPIA -> 2
            else -> 0
        }
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(theme = theme)
        )
    }

    @OptIn(ExperimentalReadiumApi::class)
    fun updateFontSize(fontSize: Double) {
        syncPreferences.readerFontSize = fontSize.toFloat()
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(fontSize = fontSize)
        )
    }

    fun seekTo(progress: Float) {
        viewModelScope.launch {
            val publication = _uiState.value.publication ?: return@launch
            val positionsService = publication.findService(PositionsService::class) ?: return@launch
            val positions = positionsService.positions()
            if (positions.isEmpty()) return@launch
            
            val index = (progress * (positions.size - 1)).toInt().coerceIn(0, positions.size - 1)
            val locator = positions[index]
            _uiState.value = _uiState.value.copy(pendingLocator = locator)
        }
    }

    fun onLocatorConsumed() {
        _uiState.value = _uiState.value.copy(pendingLocator = null)
    }

    override fun onCleared() {
        uiState.value.publication?.close()
    }
}
