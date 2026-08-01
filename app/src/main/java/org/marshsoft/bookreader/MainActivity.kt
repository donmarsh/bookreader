package org.marshsoft.bookreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.commitNow
import org.marshsoft.bookreader.ui.MainScreen
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

/**
 * Stands in for a Readium navigator fragment (EpubNavigatorFragment, PdfNavigatorFragment) that
 * the system tried to restore after process death using the default no-arg constructor, which
 * those fragments don't have. It was previously hosted in a container, so it must produce a view
 * to satisfy FragmentManager's restore lifecycle; ReaderScreen detects and replaces it with the
 * real navigator once the book's publication is loaded again.
 */
private class PlaceholderNavigatorFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = View(inflater.context)
}

class MainActivity : FragmentActivity() {
    private var pendingBookUri by mutableStateOf<Uri?>(null)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        supportFragmentManager.fragmentFactory = object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                return try {
                    super.instantiate(classLoader, className)
                } catch (e: Fragment.InstantiationException) {
                    PlaceholderNavigatorFragment()
                }
            }
        }
        super.onCreate(savedInstanceState)
        pendingBookUri = intent.toBookUri()
        enableEdgeToEdge()
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        setContent {
            BookReaderTheme {
                MainScreen(
                    pendingBookUri = pendingBookUri,
                    onPendingBookUriHandled = { handledUri ->
                        if (pendingBookUri == handledUri) {
                            pendingBookUri = null
                        }
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Readium's navigator fragments (and their internal child fragments/ViewModels) can only
        // be built through book-specific factories set up in ReaderScreen; they can't survive a
        // reflection-based restore. supportFragmentManager only ever hosts that reader container,
        // so drop its fragments before FragmentManager captures state - ReaderScreen rebuilds the
        // navigator from the persisted reading position instead of relying on fragment restore.
        supportFragmentManager.fragments.forEach { fragment ->
            supportFragmentManager.commitNow(allowStateLoss = true) { remove(fragment) }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingBookUri = intent.toBookUri()
    }

    private fun Intent?.toBookUri(): Uri? {
        return this?.data?.takeIf { action == Intent.ACTION_VIEW }
    }
}
