package org.marshsoft.bookreader.ui.screens.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.data.repository.SyncRepository
import org.marshsoft.bookreader.domain.model.Book
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.PositionsService
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.marshsoft.bookreader.util.BookParser
import java.io.File

data class ReaderUiState(
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
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
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
                    syncRepository.syncProgress(entity)
                    val updatedEntity = bookDao.getBookById(idLong) ?: entity
                    val book = updatedEntity.toDomain()
                    val publication = bookParser.parsePublication(File(book.filePath))
                    val totalPages = publication?.findService(PositionsService::class)?.positions()?.size
                    
                    _uiState.value = ReaderUiState(
                        book = book,
                        publication = publication,
                        totalPages = totalPages,
                        isLoading = false
                    )
                } else {
                    _uiState.value = ReaderUiState(isLoading = false, error = "Book not found")
                }
            } catch (e: Exception) {
                _uiState.value = ReaderUiState(isLoading = false, error = e.message)
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
        description = description,
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
                    syncRepository.syncProgress(updatedBook)
                    
                    _uiState.value = _uiState.value.copy(book = updatedBook.toDomain())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleHud() {
        _uiState.value = _uiState.value.copy(isHudVisible = !_uiState.value.isHudVisible)
    }

    fun updateTheme(theme: Theme) {
        _uiState.value = _uiState.value.copy(
            preferences = _uiState.value.preferences.copy(theme = theme)
        )
    }

    fun updateFontSize(fontSize: Double) {
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
        super.onCleared()
        uiState.value.publication?.close()
    }
}
