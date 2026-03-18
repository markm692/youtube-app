package com.youtubeapp.data.repository

import com.youtubeapp.BuildConfig
import com.youtubeapp.data.model.SearchResponse
import com.youtubeapp.data.model.VideoListResponse
import com.youtubeapp.data.remote.YouTubeApiService

class YouTubeRepository(
    private val apiService: YouTubeApiService
) {
    private val apiKey = BuildConfig.YOUTUBE_API_KEY

    suspend fun searchVideos(query: String, pageToken: String? = null): SearchResponse {
        return apiService.searchVideos(
            query = query,
            pageToken = pageToken,
            apiKey = apiKey
        )
    }

    suspend fun getPopularVideos(pageToken: String? = null): VideoListResponse {
        return apiService.getPopularVideos(
            pageToken = pageToken,
            apiKey = apiKey
        )
    }

    suspend fun getVideoDetails(videoId: String): VideoListResponse {
        return apiService.getVideoDetails(
            id = videoId,
            apiKey = apiKey
        )
    }
}
