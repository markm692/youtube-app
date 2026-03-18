package com.youtubeapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.VideoItem
import com.youtubeapp.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: YouTubeRepository? = try {
        (application as YouTubeApp).repository
    } catch (e: Exception) {
        null
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        loadPopularVideos()
    }

    fun loadPopularVideos() {
        val repo = repository ?: run {
            _uiState.value = _uiState.value.copy(error = "App not initialized")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repo.getPopularVideos()
                _uiState.value = _uiState.value.copy(
                    videos = response.items,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load videos"
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = HomeUiState()
        loadPopularVideos()
    }
}
