package org.marshsoft.bookreader.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.marshsoft.bookreader.BookReaderApplication
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as BookReaderApplication
    val viewModel: ReaderViewModel = viewModel(
        key = bookId,
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReaderViewModel(bookId, app.database.bookDao(), app.bookParser, app.syncRepository) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val book = uiState.book ?: return
    val publication = uiState.publication
    
    var sliderProgress by remember { mutableStateOf(book.progress) }
    LaunchedEffect(book.progress) {
        sliderProgress = book.progress
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = uiState.isHudVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                book.author.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (publication != null) {
                    ReadiumNavigator(
                        publication = publication,
                        initialLocatorJson = book.lastReadLocation,
                        initialProgress = book.progress,
                        preferences = uiState.preferences,
                        pendingLocator = uiState.pendingLocator,
                        onProgressChanged = { viewModel.updateProgress(it) },
                        onToggleHud = { viewModel.toggleHud() },
                        onLocatorConsumed = { viewModel.onLocatorConsumed() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Opening publication...")
                    }
                }
            }

            // Persistent bottom progress bar
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )

            // Floating HUD
            AnimatedVisibility(
                visible = uiState.isHudVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = padding.calculateBottomPadding() + 40.dp)
                        .width(320.dp)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = {
                            val nextSize = if ((uiState.preferences.fontSize ?: 1.0) >= 2.0) 0.5 else (uiState.preferences.fontSize ?: 1.0) + 0.2
                            viewModel.updateFontSize(nextSize)
                        }) {
                            Icon(Icons.Default.TextFields, contentDescription = "Font settings")
                        }
                        IconButton(onClick = {
                            val nextTheme = when (uiState.preferences.theme) {
                                Theme.DARK -> Theme.LIGHT
                                else -> Theme.DARK
                            }
                            viewModel.updateTheme(nextTheme)
                        }) {
                            Icon(
                                if (uiState.preferences.theme == Theme.DARK) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                                contentDescription = "Theme"
                            )
                        }
                        
                        Slider(
                            value = sliderProgress,
                            onValueChange = { sliderProgress = it },
                            onValueChangeFinished = { viewModel.seekTo(sliderProgress) },
                            modifier = Modifier.weight(1f)
                        )
                        
                        val currentPage = uiState.totalPages?.let { (sliderProgress * it).toInt().coerceIn(1, it) }
                        Text(
                            if (currentPage != null) "P. $currentPage / ${uiState.totalPages}" else "${(book.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReadiumNavigator(
    publication: Publication,
    initialLocatorJson: String?,
    initialProgress: Float,
    preferences: org.readium.r2.navigator.epub.EpubPreferences,
    pendingLocator: Locator?,
    onProgressChanged: (Locator) -> Unit,
    onToggleHud: () -> Unit,
    onLocatorConsumed: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is FragmentActivity) break
            c = c.baseContext
        }
        c as FragmentActivity
    }
    
    val containerId = remember { android.view.View.generateViewId() }
    var navigator by remember { mutableStateOf<org.readium.r2.navigator.VisualNavigator?>(null) }

    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        // Ensure fragment is attached before accessing its properties/viewModel
        if (nav is androidx.fragment.app.Fragment) {
            while (!nav.isAdded || nav.activity == null) {
                delay(10)
            }
        }
        nav.currentLocator.collectLatest { locator ->
            onProgressChanged(locator)
        }
    }

    AndroidView(
        factory = { ctx ->
            FragmentContainerView(ctx).apply {
                id = containerId
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { _ ->
            val fragmentManager = activity.supportFragmentManager
            val currentFragment = fragmentManager.findFragmentById(containerId)
            
            if (currentFragment == null) {
                val initialLocator = initialLocatorJson?.let { Locator.fromJSON(org.json.JSONObject(it)) }
                    ?: publication.locatorFromLink(publication.readingOrder.first())?.copy(
                        locations = Locator.Locations(progression = initialProgress.toDouble())
                    )

                val fragment = if (publication.conformsTo(Publication.Profile.PDF)) {
                    val factory = PdfNavigatorFactory(publication, PdfiumEngineProvider())
                    factory.createFragmentFactory(
                        initialLocator = initialLocator,
                        listener = object : PdfNavigatorFragment.Listener {}
                    ).instantiate(activity.classLoader, PdfNavigatorFragment::class.java.name) as PdfNavigatorFragment<*, *>
                } else {
                    val factory = EpubNavigatorFactory(
                        publication = publication,
                        configuration = EpubNavigatorFactory.Configuration(
                            defaults = EpubDefaults(
                                scroll = false,
                                publisherStyles = false
                            )
                        )
                    )
                    factory.createFragmentFactory(
                        initialLocator = initialLocator,
                        initialPreferences = preferences,
                        listener = object : EpubNavigatorFragment.Listener {
                            override fun onExternalLinkActivated(url: AbsoluteUrl) {
                            }

                            override fun onJumpToLocator(locator: Locator) {
                                // Close HUD on jump
                            }
                        }
                    ).instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                }

                val nav = fragment as org.readium.r2.navigator.VisualNavigator
                nav.addInputListener(object : InputListener {
                    override fun onTap(event: TapEvent): Boolean {
                        val view = nav.publicationView
                        val width = view.width
                        val x = event.point.x

                        if (nav is org.readium.r2.navigator.OverflowableNavigator) {
                            if (x < width * 0.2) {
                                nav.goBackward(animated = true)
                                return true
                            } else if (x > width * 0.8) {
                                nav.goForward(animated = true)
                                return true
                            }
                        }

                        onToggleHud()
                        return true
                    }
                })

                fragmentManager.commitNow {
                    replace(containerId, fragment)
                }
                
                navigator = nav
            } else {
                if (currentFragment.isAdded) {
                    if (currentFragment is EpubNavigatorFragment) {
                        currentFragment.submitPreferences(preferences)
                    }
                    
                    if (pendingLocator != null) {
                        (currentFragment as? org.readium.r2.navigator.Navigator)?.go(pendingLocator)
                        onLocatorConsumed()
                    }
                }
            }
        }
    )
}
