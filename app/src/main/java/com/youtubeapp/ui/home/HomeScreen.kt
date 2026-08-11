package com.youtubeapp.ui.home

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.FeedVideo
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember { context.applicationContext as YouTubeApp }
    val authManager = remember { app.authManager }
    val greyscale by app.settings.greyscaleThumbnails.collectAsState()

    // Consent screen result -> hand the Intent back to AuthManager.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authManager.onConsentResult(result.data)
        }
    }

    val startSignIn: () -> Unit = {
        scope.launch {
            runCatching { authManager.authorize() }
                .onSuccess { authResult ->
                    authResult.pendingIntent?.let { pending ->
                        consentLauncher.launch(
                            IntentSenderRequest.Builder(pending.intentSender).build()
                        )
                    }
                }
                .onFailure { Log.e("HomeScreen", "Authorization failed", it) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "YouTube",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Your subscriptions",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { app.settings.toggleGreyscaleThumbnails() }) {
                        Text(
                            if (greyscale) "Colour" else "Grey",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    TextButton(onClick = onSearchClick) {
                        Text("Search", color = MaterialTheme.colorScheme.onPrimary)
                    }
                    TextButton(
                        onClick = { if (uiState.signedIn) viewModel.signOut() else startSignIn() }
                    ) {
                        Text(
                            if (uiState.signedIn) "Sign out" else "Sign in",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        when {
            // No trending fallback: signed out there is simply no feed.
            !uiState.signedIn -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (!uiState.authResolved) {
                        CircularProgressIndicator()
                    } else {
                        SignInPrompt(onSignIn = startSignIn)
                    }
                }
            }

            uiState.isLoading && uiState.videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null && uiState.videos.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            uiState.error ?: "Error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.refresh() }) { Text("Retry") }
                    }
                }
            }

            else -> {
                // Adaptive: the column count follows available width, so a phone
                // in portrait gets one column and a tablet in landscape gets
                // several, without branching on device type.
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.videos, key = { it.videoId }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video.videoId) },
                            greyscale = greyscale
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.widthIn(max = 420.dp).padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Sign in to see your subscriptions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "This app only shows uploads from channels you subscribe to.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onSignIn) { Text("Sign in with Google") }
        }
    }
}

/** Minimum card width; below this the grid drops to fewer columns. */
private val CARD_MIN_WIDTH = 320.dp

/**
 * Desaturating rather than hiding thumbnails: most of a thumbnail's pull is
 * colour and face contrast, while its useful signal (is this a tutorial, a
 * clip, a talking head?) survives in greyscale. Hiding them outright tends to
 * cause speculative opens just to find out what something is.
 */
private val GREYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun VideoCard(
    video: FeedVideo,
    onClick: () -> Unit,
    greyscale: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            colorFilter = if (greyscale) GREYSCALE else null
        )
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = video.channelTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
