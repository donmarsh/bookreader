package org.marshsoft.bookreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import org.marshsoft.bookreader.ui.MainScreen
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class MainActivity : FragmentActivity() {
    private var pendingBookUri by mutableStateOf<Uri?>(null)

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingBookUri = intent.toBookUri()
    }

    private fun Intent?.toBookUri(): Uri? {
        return this?.data?.takeIf { action == Intent.ACTION_VIEW }
    }
}
