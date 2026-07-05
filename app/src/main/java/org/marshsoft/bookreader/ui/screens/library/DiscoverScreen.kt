package org.marshsoft.bookreader.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import org.marshsoft.bookreader.domain.model.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "The Scholarly Sanctuary",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    placeholder = { Text("Search for your next odyssey...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    "THE ARCHIVIST'S CHOICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "Curated Collections",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { /* TODO */ }, contentPadding = PaddingValues(0.dp)) {
                    Text("View all volumes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                CollectionCard(
                    title = "Revisited",
                    subtitle = "A meticulous selection of early 20th century prose that defined the movement's obsession with nature and emotion.",
                    imageUrl = "https://example.com/revisited.jpg"
                )
                Spacer(modifier = Modifier.height(16.dp))
                CollectionCard(
                    title = "Modernist Manifestos",
                    subtitle = "12 Essential Volumes",
                    imageUrl = "https://example.com/modernist.jpg"
                )
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Trending Now",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Row {
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(listOf(
                        Book("5", "The Silent Atlas", "Isolde Thorne", 4.8f, "https://example.com/atlas.jpg", "", "epub"),
                        Book("6", "Brutalist Dreams", "Marcus Vance", 4.6f, "https://example.com/brutalist.jpg", "", "epub")
                    )) { book ->
                        TrendingBookItem(book)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    "Explore Genres",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GenreCard("Philosophy", Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                        GenreCard("Speculative Fiction", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GenreCard("Art History", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
                        GenreCard("Natural Science", Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            item {
                FeaturedBookCard()
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun CollectionCard(title: String, subtitle: String, imageUrl: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(24.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.8f)))
            }
        }
    }
}

@Composable
fun TrendingBookItem(book: Book) {
    Column(modifier = Modifier.width(160.dp)) {
        AsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(book.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(book.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("★", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(book.progress.toString(), style = MaterialTheme.typography.labelSmall) // Reusing progress field for rating here for simplicity
        }
    }
}

@Composable
fun GenreCard(name: String, modifier: Modifier = Modifier, containerColor: Color) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun FeaturedBookCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = "https://example.com/horizon.jpg",
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp, 240.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "SPECIALLY SELECTED FOR YOU",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Beyond the Horizon",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Based on your recent interest in existentialist literature, this new translation provides fresh perspective on the human condition.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { /* TODO */ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("START READING")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { /* TODO */ }) {
                Text("DETAILS", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
