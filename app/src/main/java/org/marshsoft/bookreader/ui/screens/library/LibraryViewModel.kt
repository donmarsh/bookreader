package org.marshsoft.bookreader.ui.screens.library

import android.net.Uri
import android.text.Html
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.data.local.dao.BookDao
import org.marshsoft.bookreader.data.local.entities.BookEntity
import org.marshsoft.bookreader.data.local.SyncPreferences
import org.marshsoft.bookreader.data.repository.SyncRepository
import org.marshsoft.bookreader.data.repository.AuthRepository
import org.marshsoft.bookreader.domain.model.Book
import org.marshsoft.bookreader.util.BookParser
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class LibraryUiState(
    val message: String? = null,
    val showFirstRunPrompt: Boolean = false,
    val syncStatus: SyncRepository.SyncStatus = SyncRepository.SyncStatus.Idle,
    val bookToDelete: Book? = null
)

class LibraryViewModel(
    private val bookDao: BookDao,
    private val bookParser: BookParser,
    private val syncRepository: SyncRepository,
    private val syncPreferences: SyncPreferences,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val books: StateFlow<List<Book>> = combine(_books, _searchQuery) { books, query ->
        if (query.isBlank()) {
            books
        } else {
            books.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.author.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        viewModelScope.launch {
            bookDao.getAllBooks().collectLatest { entities ->
                _books.value = entities.map { it.toDomain() }
            }
        }

        if (syncPreferences.isFirstRun && authRepository.currentUser.value == null) {
            _uiState.value = _uiState.value.copy(showFirstRunPrompt = true)
        }

        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null && _uiState.value.showFirstRunPrompt) {
                    // User signed in while the "Sign in to sync" prompt was active.
                    // Automatically enable and start sync as requested.
                    if (syncPreferences.isFirstRun) {
                        syncLibrary()
                    }
                }
            }
        }
    }

    fun dismissFirstRunPrompt() {
        syncPreferences.isFirstRun = false
        _uiState.value = _uiState.value.copy(showFirstRunPrompt = false)
    }

    fun syncLibrary(context: android.content.Context? = null) {
        viewModelScope.launch {
            // Automatically enable sync settings when user confirms first-run sync
            syncPreferences.isSyncEnabled = true
            syncPreferences.isDriveSyncEnabled = true
            
            syncRepository.syncAll(context).collect { status ->
                _uiState.value = _uiState.value.copy(syncStatus = status)
                
                if (status is SyncRepository.SyncStatus.Success || status is SyncRepository.SyncStatus.Error) {
                    syncPreferences.isFirstRun = false
                    _uiState.value = _uiState.value.copy(showFirstRunPrompt = false)
                }
            }
        }
    }

    fun clearSyncStatus() {
        _uiState.value = _uiState.value.copy(syncStatus = SyncRepository.SyncStatus.Idle)
    }

    fun importBook(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val result = importBookInternal(context, uri, null)
            when (result) {
                ImportResult.DUPLICATE -> _uiState.value = _uiState.value.copy(message = "This book is already in your library")
                ImportResult.FAILURE -> _uiState.value = _uiState.value.copy(message = "Failed to import book")
                else -> { /* Success */ }
            }
        }
    }

    fun importFolder(context: android.content.Context, treeUri: Uri, allowedTypes: Set<String>) {
        viewModelScope.launch {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            val files = mutableListOf<DocumentFile>()
            
            fun collectFiles(dir: DocumentFile) {
                dir.listFiles().forEach { file ->
                    if (file.isDirectory) {
                        collectFiles(file)
                    } else {
                        val name = file.name?.lowercase() ?: ""
                        if ((allowedTypes.contains("epub") && name.endsWith(".epub")) ||
                            (allowedTypes.contains("pdf") && name.endsWith(".pdf"))) {
                            files.add(file)
                        }
                    }
                }
            }
            
            collectFiles(root)
            
            if (files.isEmpty()) {
                _uiState.value = _uiState.value.copy(message = "No matching books found in folder")
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(message = "Importing ${files.size} books...")
            
            var importedCount = 0
            var duplicateCount = 0
            
            files.forEach { file ->
                val result = importBookInternal(context, file.uri, file.name)
                if (result == ImportResult.SUCCESS) importedCount++
                else if (result == ImportResult.DUPLICATE) duplicateCount++
            }
            
            val finalMessage = buildString {
                append("Imported $importedCount books.")
                if (duplicateCount > 0) append(" $duplicateCount duplicates skipped.")
            }
            _uiState.value = _uiState.value.copy(message = finalMessage)
        }
    }

    private enum class ImportResult { SUCCESS, DUPLICATE, FAILURE }

    private suspend fun importBookInternal(
        context: android.content.Context, 
        uri: Uri, 
        providedFileName: String?
    ): ImportResult {
        return try {
            val originalFileName = providedFileName ?: bookParser.getFileName(uri) ?: "Unknown Book"
            val fileType = if (originalFileName.endsWith(".epub", true) || 
                            uri.toString().endsWith(".epub", true)) "epub" else "pdf"

            val fileName = "book_${System.currentTimeMillis()}_${(0..1000).random()}.$fileType"
            val filePath = bookParser.saveFileToInternal(uri, fileName) ?: return ImportResult.FAILURE
            
            var title = originalFileName.substringBeforeLast(".")
            var author = "Unknown Author"
            var coverPath: String? = null
            var description: String? = null
            var publisher: String? = null
            var publishedDate: String? = null
            var language: String? = null
            var identifier: String? = null

            val publication = bookParser.parsePublication(File(filePath))
            if (publication != null) {
                // Log all metadata for debugging
                Log.d("MetadataLog", "--- Publication Metadata Start ---")
                Log.d("MetadataLog", "Title: ${publication.metadata.title}")
                Log.d("MetadataLog", "Identifier: ${publication.metadata.identifier}")
                Log.d("MetadataLog", "Authors: ${publication.metadata.authors.map { it.localizedName.string }}")
                
                Log.d("MetadataLog", "--- Other Metadata (Extra) ---")
                publication.metadata.otherMetadata.forEach { (key, value) ->
                    Log.d("MetadataLog", "$key: $value")
                }
                Log.d("MetadataLog", "--- Metadata End ---")

                // Robust Dublin Core title extraction
                val dcTitleRaw = publication.metadata.otherMetadata["http://purl.org/dc/elements/1.1/title"]
                    ?: publication.metadata.otherMetadata["dc:title"]
                
                val dcTitle = when (dcTitleRaw) {
                    is String -> dcTitleRaw
                    is List<*> -> dcTitleRaw.firstOrNull()?.toString()
                    else -> null
                }
                
                title = dcTitle ?: publication.metadata.title ?: title

                // Robust Dublin Core author extraction
                val dcCreatorRaw = publication.metadata.otherMetadata["http://purl.org/dc/elements/1.1/creator"]
                    ?: publication.metadata.otherMetadata["dc:creator"]

                val dcAuthor = when (dcCreatorRaw) {
                    is String -> dcCreatorRaw
                    is List<*> -> dcCreatorRaw.firstOrNull()?.toString()
                    else -> null
                }

                author = dcAuthor ?: publication.metadata.authors.firstOrNull()?.localizedName?.string ?: author
                description = publication.metadata.description?.let { 
                    Html.fromHtml(it, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                }
                publisher = publication.metadata.publishers.firstOrNull()?.localizedName?.string
                publishedDate = publication.metadata.published?.toString()
                language = publication.metadata.languages.firstOrNull()
                identifier = publication.metadata.identifier

                val coverBitmap = bookParser.getCover(publication)
                if (coverBitmap != null) {
                    coverPath = bookParser.saveBitmapToInternal(coverBitmap, "cover_${System.currentTimeMillis()}.png")
                }
            }

            // Duplicate check
            val isDuplicate = if (identifier != null) {
                bookDao.getBookByIdentifier(identifier) != null
            } else {
                bookDao.getBookByTitleAndAuthor(title, author) != null
            }

            if (isDuplicate) {
                File(filePath).delete()
                coverPath?.let { File(it).delete() }
                return ImportResult.DUPLICATE
            }

            val entity = BookEntity(
                title = title,
                author = author,
                filePath = filePath,
                fileType = fileType,
                coverPath = coverPath,
                description = description,
                publisher = publisher,
                publishedDate = publishedDate,
                language = language,
                identifier = identifier
            )
            bookDao.insertBook(entity)
            syncRepository.uploadBook(entity, context)
            ImportResult.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.FAILURE
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun deleteBook(book: Book, removeFromCloud: Boolean = false, context: android.content.Context? = null) {
        viewModelScope.launch {
            try {
                val idLong = book.id.toLongOrNull()
                if (idLong != null) {
                    val entity = bookDao.getBookById(idLong)
                    if (entity != null) {
                        // Delete from remote if requested
                        if (removeFromCloud) {
                            syncRepository.deleteRemoteBook(entity, true, context)
                        }
                        
                        bookDao.deleteBook(entity)
                        
                        // Delete local files
                        File(entity.filePath).delete()
                        entity.coverPath?.let { File(it).delete() }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.value = _uiState.value.copy(bookToDelete = null)
            }
        }
    }

    fun confirmDeleteBook(book: Book) {
        _uiState.value = _uiState.value.copy(bookToDelete = book)
    }

    fun cancelDeleteBook() {
        _uiState.value = _uiState.value.copy(bookToDelete = null)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
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
        identifier = identifier
    )
}
