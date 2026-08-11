package com.youtubeapp.ui.home

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.youtubeapp.YouTubeApp
import com.youtubeapp.ui.components.VideoRow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onChannelsClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember { context.applicationContext as YouTubeApp }
    val authManager = remember { app.authManager }

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
                    TextButton(onClick = onChannelsClick) {
                        Text("Channels", color = MaterialTheme.colorScheme.onPrimary)
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

        // Thin bar while refreshing over content, so the feed filling in wave
        // by wave doesn't look like a stall.
        if (uiState.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                // One expansion at a time: revealing a thumbnail collapses the
                // previous one, so the feed never becomes a wall of images.
                var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.videos, key = { it.videoId }) { video ->
                        VideoRow(
                            video = video,
                            expanded = expandedId == video.videoId,
                            onToggle = {
                                expandedId =
                                    if (expandedId == video.videoId) null else video.videoId
                            },
                            onPlay = { onVideoClick(video.videoId) }
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
