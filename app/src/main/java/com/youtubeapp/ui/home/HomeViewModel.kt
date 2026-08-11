package com.youtubeapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.FeedVideo
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
    /** False until the first silent authorization attempt settles. */
    val authResolved: Boolean = false
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YouTubeApp
    private val repository = app.repository
    private val authManager = app.authManager

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // Consent persists server-side, so this returns a token with no UI and
        // takes the user straight to their subscriptions on launch.
        viewModelScope.launch {
            runCatching { authManager.authorize() }
            _uiState.value = _uiState.value.copy(authResolved = true)
        }
        viewModelScope.launch {
            authManager.signedIn.collect { signedIn ->
                _uiState.value = _uiState.value.copy(signedIn = signedIn)
                if (signedIn) load() else _uiState.value = _uiState.value.copy(
                    videos = emptyList(),
                    isLoading = false,
                    error = null
                )
            }
        }
    }

    fun load() {
        if (!authManager.signedIn.value) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val feed = repository.getSubscriptionFeed()
                _uiState.value = _uiState.value.copy(
                    videos = feed,
                    isLoading = false,
                    error = if (feed.isEmpty()) "No recent uploads from your subscriptions" else null
                )
            } catch (e: Exception) {
                // A 401/403 here usually means the token expired.
                authManager.invalidateToken()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load your subscriptions"
                )
            }
        }
    }

    fun refresh() = load()

    fun signOut() = authManager.signOut()
}
