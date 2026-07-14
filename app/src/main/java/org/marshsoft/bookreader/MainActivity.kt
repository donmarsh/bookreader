package org.marshsoft.bookreader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import org.marshsoft.bookreader.ui.MainScreen
import org.marshsoft.bookreader.ui.theme.BookReaderTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BookReaderTheme {
                MainScreen()
            }
        }
    }
}
