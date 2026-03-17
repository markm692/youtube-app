package com.youtubeapp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val title: String = "",
    val channelTitle: String = "",
    val description: String = "",
    val viewCount: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: YouTubeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    fun loadVideo(videoId: String) {
        viewModelScope.launch {
            _uiState.value = PlayerUiState(isLoading = true)
            try {
                val response = repository.getVideoDetails(videoId)
                val video = response.items.firstOrNull()
                if (video != null) {
                    _uiState.value = PlayerUiState(
                        title = video.snippet.title,
                        channelTitle = video.snippet.channelTitle,
                        description = video.snippet.description,
                        viewCount = video.statistics?.viewCount ?: "",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    title = "Failed to load details"
                )
            }
        }
    }
}
