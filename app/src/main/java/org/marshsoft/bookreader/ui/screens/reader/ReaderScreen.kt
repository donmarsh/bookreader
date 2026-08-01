package org.marshsoft.bookreader.ui.screens.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)
    val context = LocalContext.current
    val app = context.applicationContext as BookReaderApplication
    val viewModel: ReaderViewModel = viewModel(
        key = bookId,
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReaderViewModel(bookId, app.database.bookDao(), app.bookParser, app.syncRepository, app.syncPreferences) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Opening Book...") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (uiState.error != null) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Error") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    val book = uiState.book ?: return
    val publication = uiState.publication

    // Determine the background color based on Readium theme
    val readerBackground = when (uiState.preferences.theme) {
        Theme.DARK -> Color(0xFF121212) // Material Dark Surface
        Theme.SEPIA -> Color(0xFFF5EBCF) // Traditional Sepia
        else -> Color.White // Match Readium's default Light theme background
    }

    val contentColor = when (uiState.preferences.theme) {
        Theme.DARK -> Color.White
        else -> Color.Black
    }

    // Handle system bar visibility and icon contrast. Android 15+ draws bars edge-to-edge.
    val activity = context as? Activity
    val window = activity?.window
    if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        
        LaunchedEffect(uiState.isHudVisible, uiState.preferences.theme) {
            val useDarkIcons = uiState.preferences.theme != Theme.DARK
            insetsController.isAppearanceLightStatusBars = useDarkIcons
            insetsController.isAppearanceLightNavigationBars = useDarkIcons

            if (uiState.isHudVisible) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
        
        // Ensure status bar is restored when leaving the screen
        DisposableEffect(Unit) {
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    
    var sliderProgress by remember { mutableStateOf(book.progress) }
    LaunchedEffect(book.progress) {
        sliderProgress = book.progress
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = readerBackground
    ) { padding ->
        // We handle insets manually to avoid reader resizing when HUD toggles
        val cutoutPadding = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
        // If there's no cutout (0.dp), we use a small default to avoid sticking to the very top
        val safeTopPadding = if (cutoutPadding > 0.dp) cutoutPadding + 4.dp else 12.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Reader Content - Full screen to allow overlay without resizing
            // Dynamically calculated padding to start exactly below the camera hole/notch
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = safeTopPadding)
                    .padding(horizontal = 12.dp)
            ) {
                if (publication != null) {
                    ReadiumNavigator(
                        publication = publication,
                        initialLocatorJson = book.lastReadLocation,
                        initialProgress = book.progress,
                        preferences = uiState.preferences,
                        pendingLocator = uiState.pendingLocator,
                        backgroundColor = readerBackground,
                        onProgressChanged = { viewModel.updateProgress(it) },
                        onToggleHud = { viewModel.toggleHud() },
                        onLocatorConsumed = { viewModel.onLocatorConsumed() }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Opening Book...")
                    }
                }
            }

            // HUD Top Bar - Overlays the reader without resizing it
            AnimatedVisibility(
                visible = uiState.isHudVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.zIndex(1f)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = readerBackground.copy(alpha = 0.98f),
                    contentColor = contentColor,
                    tonalElevation = 2.dp
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    book.author.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = contentColor.copy(alpha = 0.5f)
                                )
                                Text(
                                    book.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        },
                        windowInsets = WindowInsets.statusBars,
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, 
                                    contentDescription = "Back",
                                    tint = contentColor
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* TODO */ }) {
                                Icon(
                                    Icons.Default.MoreVert, 
                                    contentDescription = "More",
                                    tint = contentColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Unspecified,
                            navigationIconContentColor = Color.Unspecified,
                            titleContentColor = contentColor,
                            actionIconContentColor = Color.Unspecified
                        )
                    )
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(bottom = 20.dp)
                        .width(320.dp)
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = readerBackground.copy(alpha = 0.98f),
                    contentColor = contentColor,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (book.fileType != "pdf") {
                            IconButton(onClick = {
                                val currentSize = uiState.preferences.fontSize ?: 1.0
                                viewModel.updateFontSize((currentSize - 0.1).coerceAtLeast(0.5))
                            }) {
                                Icon(
                                    Icons.Default.TextDecrease, 
                                    contentDescription = "Decrease font size",
                                    tint = contentColor
                                )
                            }
                            
                            IconButton(onClick = {
                                val currentSize = uiState.preferences.fontSize ?: 1.0
                                viewModel.updateFontSize((currentSize + 0.1).coerceAtMost(3.0))
                            }) {
                                Icon(
                                    Icons.Default.TextIncrease, 
                                    contentDescription = "Increase font size",
                                    tint = contentColor
                                )
                            }
                        }

                        IconButton(onClick = {
                            val nextTheme = when (uiState.preferences.theme) {
                                Theme.DARK -> Theme.LIGHT
                                Theme.LIGHT -> Theme.SEPIA
                                else -> Theme.DARK
                            }
                            viewModel.updateTheme(nextTheme)
                        }) {
                            Icon(
                                imageVector = when (uiState.preferences.theme) {
                                    Theme.DARK -> Icons.Default.Brightness7
                                    Theme.SEPIA -> Icons.Default.Brightness4
                                    else -> Icons.Default.Brightness4
                                },
                                contentDescription = "Theme",
                                tint = contentColor
                            )
                        }
                        
                        Slider(
                            value = sliderProgress,
                            onValueChange = { sliderProgress = it },
                            onValueChangeFinished = { viewModel.seekTo(sliderProgress) },
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = contentColor,
                                activeTrackColor = contentColor,
                                inactiveTrackColor = contentColor.copy(alpha = 0.2f)
                            )
                        )
                        
                        val currentPage = uiState.totalPages?.let { (sliderProgress * it).toInt().coerceIn(1, it) }
                        Text(
                            if (currentPage != null) "P. $currentPage / ${uiState.totalPages}" else "${(book.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f)
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
    backgroundColor: Color,
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
    
    val containerId = rememberSaveable { android.view.View.generateViewId() }
    var navigator by remember { mutableStateOf<org.readium.r2.navigator.VisualNavigator?>(null) }

    val addInputListeners = remember(onToggleHud) {
        { nav: org.readium.r2.navigator.VisualNavigator ->
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
        }
    }

    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        // Ensure fragment is attached before accessing its properties/viewModel
        if (nav is androidx.fragment.app.Fragment) {
            while (!nav.isAdded || nav.activity == null) {
                delay(10.milliseconds)
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
                setBackgroundColor(backgroundColor.toArgb())
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.setBackgroundColor(backgroundColor.toArgb())
            val fragmentManager = activity.supportFragmentManager
            var currentFragment = fragmentManager.findFragmentById(containerId)

            // A restored placeholder (see MainActivity's FragmentFactory) stands in for a
            // navigator fragment the system couldn't recreate after process death - discard it
            // and rebuild the real navigator below.
            if (currentFragment != null &&
                currentFragment !is EpubNavigatorFragment &&
                currentFragment !is PdfNavigatorFragment<*, *>
            ) {
                fragmentManager.commitNow { remove(currentFragment!!) }
                currentFragment = null
            }

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
                                publisherStyles = false,
                                pageMargins = 0.1 // Balanced margins for side curvature
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
                        },
                        configuration = EpubNavigatorFragment.Configuration(
                            shouldApplyInsetsPadding = false // Fully immersive, no extra space for system bars
                        )
                    ).instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
                }

                val nav = fragment as org.readium.r2.navigator.VisualNavigator
                addInputListeners(nav)

                fragmentManager.commitNow {
                    replace(containerId, fragment)
                }
                
                navigator = nav
            } else {
                if (navigator == null && currentFragment is org.readium.r2.navigator.VisualNavigator) {
                    addInputListeners(currentFragment)
                    navigator = currentFragment
                }

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
