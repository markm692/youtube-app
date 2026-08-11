package com.youtubeapp.ui.channels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.YouTubeApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChannelRow(
    val channelId: String,
    val title: String,
    val enabled: Boolean
)

data class ChannelsUiState(
    val channels: List<ChannelRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val enabledCount: Int get() = channels.count { it.enabled }
}

class ChannelsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as YouTubeApp
    private val repository = app.repository
    private val prefs = app.channelPreferences

    private val _uiState = MutableStateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState

    /** Set when anything changed, so the caller knows to refresh the feed. */
    var selectionChanged: Boolean = false
        private set

    init {
        load()
        viewModelScope.launch {
            prefs.excluded.collect { excluded ->
                _uiState.value = _uiState.value.copy(
                    channels = _uiState.value.channels.map {
                        it.copy(enabled = it.channelId !in excluded)
                    }
                )
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val excluded = prefs.excluded.value
                val rows = repository.getMySubscriptions()
                    .mapNotNull { sub ->
                        val id = sub.snippet.resourceId.channelId ?: return@mapNotNull null
                        ChannelRow(
                            channelId = id,
                            title = sub.snippet.title,
                            enabled = id !in excluded
                        )
                    }
                    .distinctBy { it.channelId }
                    .sortedBy { it.title.lowercase() }
                _uiState.value = ChannelsUiState(channels = rows, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Could not load your subscriptions"
                )
            }
        }
    }

    fun toggle(channelId: String, enabled: Boolean) {
        selectionChanged = true
        prefs.setEnabled(channelId, enabled)
    }

    fun enableAll() {
        selectionChanged = true
        prefs.enableAll()
    }

    fun disableAll() {
        selectionChanged = true
        prefs.disableAll(_uiState.value.channels.map { it.channelId })
    }
}
