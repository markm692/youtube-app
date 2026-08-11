package com.youtubeapp.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.youtubeapp.YouTubeApp
import com.youtubeapp.data.model.SearchItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * search.list costs 100 quota units per call against a default budget of
 * 10,000/day — roughly 100 searches. Everything below exists to avoid
 * spending a call we don't have to: a longer debounce, a minimum query
 * length, skipping repeats, and caching results per query.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as YouTubeApp).repository

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private var lastExecutedQuery: String? = null
    private val cache = LinkedHashMap<String, List<SearchItem>>()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            // Clear stale results so the empty state matches the empty box.
            if (trimmed.isEmpty()) {
                _uiState.value = _uiState.value.copy(results = emptyList(), error = null)
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(trimmed)
        }
    }

    /** Explicit submit: bypasses the debounce but still honours the cache. */
    fun onSearch() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { search(trimmed) }
    }

    private suspend fun search(query: String) {
        cache[query]?.let { cached ->
            lastExecutedQuery = query
            _uiState.value = _uiState.value.copy(
                results = cached,
                isLoading = false,
                error = null
            )
            return
        }

        // Typing and deleting back to the same text shouldn't cost 100 units.
        if (query == lastExecutedQuery && _uiState.value.error == null) return

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val response = repository.searchVideos(query)
            lastExecutedQuery = query
            cache[query] = response.items
            if (cache.size > CACHE_ENTRIES) {
                cache.remove(cache.keys.first())
            }
            _uiState.value = _uiState.value.copy(
                results = response.items,
                isLoading = false
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = e.message ?: "Search failed"
            )
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 800L
        private const val MIN_QUERY_LENGTH = 3
        private const val CACHE_ENTRIES = 20
    }
}
