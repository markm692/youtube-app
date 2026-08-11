package com.youtubeapp.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.FeedVideo
import com.youtubeapp.data.model.SearchItem
import com.youtubeapp.ui.home.VideoCard

/** Matches the home feed so both surfaces break to the same column count. */
private val CARD_MIN_WIDTH = 320.dp

@Composable
fun SearchScreen(
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val app = remember { context.applicationContext as YouTubeApp }
    val thumbnailMode by app.settings.thumbnailMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Search", style = MaterialTheme.typography.titleMedium)
            }
        }

        OutlinedTextField(
            value = uiState.query,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search videos...") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    viewModel.onSearch()
                }
            )
        )

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        uiState.error ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.results.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (uiState.query.isBlank()) {
                            "Type to search YouTube"
                        } else {
                            "No results for \"${uiState.query}\""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        uiState.results.filter { it.id.videoId != null },
                        key = { it.id.videoId!! }
                    ) { item ->
                        VideoCard(
                            video = item.toFeedVideo(),
                            onClick = { item.id.videoId?.let(onVideoClick) },
                            mode = thumbnailMode
                        )
                    }
                }
            }
        }
    }
}

private fun SearchItem.toFeedVideo() = FeedVideo(
    videoId = id.videoId.orEmpty(),
    title = snippet.title,
    channelTitle = snippet.channelTitle,
    thumbnailUrl = snippet.thumbnails.high?.url ?: snippet.thumbnails.medium?.url,
    publishedAt = snippet.publishedAt
)
