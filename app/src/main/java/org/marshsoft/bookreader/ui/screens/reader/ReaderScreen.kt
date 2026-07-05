package org.marshsoft.bookreader.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CHAPTER IV",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "The Architecture of Silence",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "CURATED ARCHIVE NO. 442",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "On the deliberate\npreservation of\nthought in the\ndigital age.",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 44.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                )
                Spacer(modifier = Modifier.height(48.dp))
                
                Text(
                    text = "I t was once believed that the weight of a book was the primary measure of its importance. In the great libraries of Alexandria or the quiet monasteries of the North, the physical presence of a scroll or codex was a testament to the endurance of human observation. But in our modern landscape, where the ink never truly dries and the pages are infinitely thin, the nature of weight has shifted.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 32.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "The Scholarly Sanctuary was founded on a singular premise: that the digital environment should not be a place of distraction, but a vessel for focus. By removing the traditional boundaries of the screen—the sharp lines, the vibrating notifications, the relentless grid—we return to the essence of the editorial experience.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 32.sp
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                AsyncImage(
                    model = "https://example.com/library_view.jpg",
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Consider the tactile nature of paper—the way light softens as it hits the grain. Our Digital Curator philosophy attempts to mimic this behavior through tonal depth. We use the background not as a void, but as a surface. It is the \"linen-bound\" approach to interface design.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 32.sp
                    )
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "As you move through these pages, notice the rhythmic asymmetry. The wide margins are not wasted space; they are lungs. They allow the text to breathe, and in turn, they allow the reader to think. In the silence between the letters, the most profound discoveries are often made.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 32.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(100.dp))
            }

            // Floating HUD
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .width(300.dp)
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
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.TextFields, contentDescription = "Font settings")
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.WbSunny, contentDescription = "Brightness")
                    }
                    
                    Slider(
                        value = 0.44f,
                        onValueChange = {},
                        modifier = Modifier.width(100.dp)
                    )
                    
                    Text(
                        "P.124 / 288",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
