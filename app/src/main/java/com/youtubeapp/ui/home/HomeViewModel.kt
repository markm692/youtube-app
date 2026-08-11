package com.youtubeapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.FeedVideo
import com.youtubeapp.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The home feed shows the signed-in user's subscriptions when authorized,
 * and falls back to public trending otherwise.
 *
 * Note: YouTube exposes no API for the algorithmic home-page recommendations,
 * watch history, or Watch Later, so "subscriptions" is the personalized feed.
 */
data class HomeUiState(
    val videos: List<FeedVideo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    val isPersonalized: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YouTubeApp
    private val repository = app.repository
    private val authManager = app.authManager

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // Consent persists server-side, so re-authorizing on launch returns a
        // token without any UI. Without this the in-memory token is lost on every
        // restart and the user appears signed out.
        viewModelScope.launch {
            runCatching { authManager.authorize() }
        }
        viewModelScope.launch {
            authManager.signedIn.collect { signedIn ->
                _uiState.value = _uiState.value.copy(signedIn = signedIn)
                load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val signedIn = authManager.signedIn.value
            try {
                if (signedIn) {
                    val feed = repository.getSubscriptionFeed()
                    _uiState.value = _uiState.value.copy(
                        videos = feed,
                        isLoading = false,
                        isPersonalized = true,
                        error = if (feed.isEmpty()) "No uploads from your subscriptions yet" else null
                    )
                } else {
                    val response = repository.getPopularVideos()
                    _uiState.value = _uiState.value.copy(
                        videos = response.items.map { it.toFeedVideo() },
                        isLoading = false,
                        isPersonalized = false
                    )
                }
            } catch (e: Exception) {
                // A 401/403 after signing in usually means the token expired.
                if (signedIn) authManager.invalidateToken()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load videos"
                )
            }
        }
    }

    fun refresh() = load()

    fun signOut() {
        authManager.signOut()
    }
}

private fun VideoItem.toFeedVideo() = FeedVideo(
    videoId = id,
    title = snippet.title,
    channelTitle = snippet.channelTitle,
    thumbnailUrl = snippet.thumbnails.high?.url ?: snippet.thumbnails.medium?.url,
    publishedAt = snippet.publishedAt
)
