package com.youtubeapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.FeedVideo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The home feed is the user's subscriptions and nothing else — no trending,
 * no algorithmic suggestions. Signed out, there is no feed to show, only a
 * prompt to sign in.
 */
data class HomeUiState(
    val videos: List<FeedVideo> = emptyList(),
    /** First load with nothing to show yet. */
    val isLoading: Boolean = false,
    /** Refreshing over content that is already on screen. */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    /** False until the first silent authorization attempt settles. */
    val authResolved: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YouTubeApp
    private val repository = app.repository
    private val authManager = app.authManager
    private val feedCache = app.feedCache
    private val channelPrefs = app.channelPreferences

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var refreshJob: Job? = null
    private var loadedWithExclusions: Set<String>? = null

    init {
        // Show the last known feed straight away; the refresh below replaces it
        // wave by wave. Without this a cold start is a blank spinner while the
        // per-channel calls complete.
        viewModelScope.launch {
            val cached = feedCache.load()
            if (cached.isNotEmpty() && _uiState.value.videos.isEmpty()) {
                _uiState.value = _uiState.value.copy(videos = cached.visible())
            }
        }
        // Consent persists server-side, so this returns a token with no UI and
        // takes the user straight to their subscriptions on launch.
        viewModelScope.launch {
            runCatching { authManager.authorize() }
            _uiState.value = _uiState.value.copy(authResolved = true)
        }
        viewModelScope.launch {
            authManager.signedIn.collect { signedIn ->
                _uiState.value = _uiState.value.copy(signedIn = signedIn)
                if (signedIn) {
                    load()
                } else {
                    refreshJob?.cancel()
                    loadedWithExclusions = null
                    _uiState.value = _uiState.value.copy(
                        videos = emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
            }
        }
        // Changing the channel selection re-fetches, but hide the now-excluded
        // videos immediately so the list reflects the choice without waiting.
        viewModelScope.launch {
            channelPrefs.excluded.collect { excluded ->
                if (loadedWithExclusions != null && loadedWithExclusions != excluded) {
                    _uiState.value = _uiState.value.copy(videos = _uiState.value.videos.visible())
                    load()
                }
            }
        }
    }

    fun load() {
        if (!authManager.signedIn.value) return
        val excluded = channelPrefs.excluded.value
        loadedWithExclusions = excluded
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val hasContent = _uiState.value.videos.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasContent,
                isRefreshing = hasContent,
                error = null
            )
            try {
                var latest = emptyList<FeedVideo>()
                repository.subscriptionFeed(excludedChannelIds = excluded)
                    .collect { videos ->
                        latest = videos
                        // Each wave supersedes the previous list rather than
                        // merging, so videos removed upstream don't linger.
                        _uiState.value = _uiState.value.copy(
                            videos = videos,
                            isLoading = false
                        )
                    }
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = if (latest.isEmpty()) emptyFeedMessage(excluded) else null
                )
                if (latest.isNotEmpty()) feedCache.save(latest)
            } catch (e: Exception) {
                // A 401/403 here usually means the token expired.
                authManager.invalidateToken()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Failed to load your subscriptions"
                )
            }
        }
    }

    fun refresh() = load()

    fun signOut() {
        viewModelScope.launch { feedCache.clear() }
        authManager.signOut()
    }

    private fun emptyFeedMessage(excluded: Set<String>): String =
        if (excluded.isEmpty()) {
            "No recent uploads from your subscriptions"
        } else {
            "No recent uploads from the channels you've selected"
        }

    /** Drops videos from channels that are currently switched off. */
    private fun List<FeedVideo>.visible(): List<FeedVideo> {
        val excluded = channelPrefs.excluded.value
        if (excluded.isEmpty()) return this
        return filterNot { it.channelId != null && it.channelId in excluded }
    }
}
