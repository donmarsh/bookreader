package org.marshsoft.bookreader.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.marshsoft.bookreader.BookReaderApplication
import org.marshsoft.bookreader.domain.model.Book
import org.marshsoft.bookreader.data.repository.SyncRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onMenuClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onLoginClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BookReaderApplication
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return LibraryViewModel(
                    app.database.bookDao(), 
                    app.bookParser, 
                    app.syncRepository,
                    app.syncPreferences
                ) as T
            }
        }
    )

    val books by viewModel.books.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.syncStatus) {
        when (val status = uiState.syncStatus) {
            is SyncRepository.SyncStatus.Success -> {
                snackbarHostState.showSnackbar("Library sync completed successfully")
                viewModel.clearSyncStatus()
            }
            is SyncRepository.SyncStatus.Error -> {
                snackbarHostState.showSnackbar(status.message)
                viewModel.clearSyncStatus()
            }
            else -> {}
        }
    }

    var isSearchActive by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showFolderTypeDialog by remember { mutableStateOf(false) }
    var pendingFolderUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var fullDescriptionToShow by remember { mutableStateOf<String?>(null) }
    
    val currentReading = if (searchQuery.isEmpty()) books.firstOrNull() else null

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBook(context, it) }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { 
            pendingFolderUri = it
            showFolderTypeDialog = true
        }
    }

    if (showFolderTypeDialog) {
        FolderTypeDialog(
            onDismiss = { showFolderTypeDialog = false },
            onConfirm = { types ->
                showFolderTypeDialog = false
                pendingFolderUri?.let { uri ->
                    viewModel.importFolder(context, uri, types)
                }
            }
        )
    }

    if (uiState.showFirstRunPrompt) {
        val currentUser by app.authRepository.currentUser.collectAsState()
        FirstRunSyncDialog(
            onDismiss = { viewModel.dismissFirstRunPrompt() },
            onSignInClick = onLoginClick,
            onSyncClick = { viewModel.syncLibrary(context) },
            isUserLoggedIn = currentUser != null,
            syncStatus = uiState.syncStatus
        )
    }

    if (fullDescriptionToShow != null) {
        FullDescriptionDialog(
            description = fullDescriptionToShow!!,
            onDismiss = { fullDescriptionToShow = null }
        )
    }

    if (uiState.bookToDelete != null) {
        DeleteBookDialog(
            book = uiState.bookToDelete!!,
            isSyncEnabled = app.syncPreferences.isSyncEnabled,
            onDismiss = { viewModel.cancelDeleteBook() },
            onConfirm = { removeFromCloud ->
                viewModel.deleteBook(uiState.bookToDelete!!, removeFromCloud, context)
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showImportMenu) {
                    SmallFloatingActionButton(
                        onClick = { 
                            showImportMenu = false
                            launcher.launch(arrayOf("application/epub+zip", "application/pdf"))
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import File")
                    }
                    SmallFloatingActionButton(
                        onClick = { 
                            showImportMenu = false
                            folderLauncher.launch(null)
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Import Folder")
                    }
                }
                
                FloatingActionButton(
                    onClick = { showImportMenu = !showImportMenu },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(if (showImportMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = "Import")
                }
            }
        },
        topBar = {
            if (isSearchActive) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onCloseClick = { 
                        isSearchActive = false
                        viewModel.onSearchQueryChange("")
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "THE BOOK SANCTUARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                "My Library",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    windowInsets = WindowInsets.statusBars,
                    navigationIcon = {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            item {
                if (currentReading != null) {
                    Text(
                        text = "CURRENTLY READING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Cover Image at the top - Show full image
                    AsyncImage(
                        model = currentReading.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onBookClick(currentReading.id) },
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = currentReading.title,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "BY ${currentReading.author.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    currentReading.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            ),
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { fullDescriptionToShow = it }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    currentReading.quote?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Progress bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("PROGRESS", style = MaterialTheme.typography.labelSmall)
                            Text("${(currentReading.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { currentReading.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onBookClick(currentReading.id) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("CONTINUE READING", style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = { viewModel.confirmDeleteBook(currentReading) },
                            modifier = Modifier
                                .height(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Book")
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No books imported yet. Click + to add one.", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All Books", "Philosophy", "Literature", "Science")) { tag ->
                        FilterChip(
                            selected = tag == "All Books",
                            onClick = { /* TODO */ },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            items(books.drop(1)) { book ->
                BookItem(
                    book = book,
                    onClick = { onBookClick(book.id) },
                    onDeleteClick = { viewModel.confirmDeleteBook(book) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun DeleteBookDialog(
    book: Book,
    isSyncEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var removeFromCloud by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Book") },
        text = {
            Column {
                Text("Are you sure you want to delete \"${book.title}\"?")
                if (isSyncEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { removeFromCloud = !removeFromCloud }
                    ) {
                        Checkbox(
                            checked = removeFromCloud,
                            onCheckedChange = { removeFromCloud = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Also remove from Google Drive and Cloud sync", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(removeFromCloud) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FullDescriptionDialog(
    description: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Description", 
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            ) 
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 24.sp
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = Color(0xFFFCF9F4), // Match DESIGN.md surface
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun FirstRunSyncDialog(
    onDismiss: () -> Unit,
    onSignInClick: () -> Unit,
    onSyncClick: () -> Unit,
    isUserLoggedIn: Boolean,
    syncStatus: SyncRepository.SyncStatus
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Welcome to Book Sanctuary") },
        text = {
            Column {
                Text(
                    if (isUserLoggedIn) 
                        "Welcome back! Would you like to sync your existing library and progress?" 
                    else 
                        "Sign in with Google to keep your books and reading progress synced across all your devices."
                )
                
                if (syncStatus is SyncRepository.SyncStatus.Progress) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { syncStatus.current.toFloat() / syncStatus.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = syncStatus.message,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isUserLoggedIn) {
                Button(onClick = onSignInClick) {
                    Text("Sign In")
                }
            } else {
                if (syncStatus !is SyncRepository.SyncStatus.Progress) {
                    Button(onClick = onSyncClick) {
                        Text("Sync Now")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = syncStatus !is SyncRepository.SyncStatus.Progress) {
                Text("Later")
            }
        }
    )
}

@Composable
fun FolderTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var includeEpub by remember { mutableStateOf(true) }
    var includePdf by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Folder") },
        text = {
            Column {
                Text("Select file types to search for:")
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeEpub, onCheckedChange = { includeEpub = it })
                    Text("EPUB Files")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includePdf, onCheckedChange = { includePdf = it })
                    Text("PDF Files")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val types = mutableSetOf<String>()
                    if (includeEpub) types.add("epub")
                    if (includePdf) types.add("pdf")
                    onConfirm(types)
                },
                enabled = includeEpub || includePdf
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "SEARCHING LIBRARY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(x = (-16).dp), // Adjust for TextField default start padding to align with "SEARCHING"
                    placeholder = { 
                        Text(
                            "Search by title or author...",
                            style = MaterialTheme.typography.bodyLarge
                        ) 
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
        },
        windowInsets = WindowInsets.statusBars,
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun BookItem(book: Book, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp, 80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PROGRESS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp))
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}
