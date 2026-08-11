package com.youtubeapp.data.remote

import com.youtubeapp.data.model.ChannelListResponse
import com.youtubeapp.data.model.PlaylistItemListResponse
import com.youtubeapp.data.model.SearchResponse
import com.youtubeapp.data.model.SubscriptionListResponse
import com.youtubeapp.data.model.VideoListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {

    // --- Signed-in endpoints (require an OAuth token) ---

    /** Channels the signed-in user subscribes to. 1 quota unit per page. */
    @GET("subscriptions")
    suspend fun getMySubscriptions(
        @Query("part") part: String = "snippet",
        @Query("mine") mine: Boolean = true,
        @Query("maxResults") maxResults: Int = 50,
        @Query("order") order: String = "alphabetical",
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String
    ): SubscriptionListResponse

    /**
     * Batch channel lookup — up to 50 comma-separated ids for 1 quota unit.
     * Used to resolve each channel's "uploads" playlist.
     */
    @GET("channels")
    suspend fun getChannels(
        @Query("part") part: String = "contentDetails,snippet",
        @Query("id") ids: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") apiKey: String
    ): ChannelListResponse

    /** The signed-in user's own channel, used to reach their liked-videos playlist. */
    @GET("channels")
    suspend fun getMyChannel(
        @Query("part") part: String = "contentDetails,snippet",
        @Query("mine") mine: Boolean = true,
        @Query("key") apiKey: String
    ): ChannelListResponse

    /** Items of a playlist — e.g. a channel's uploads. 1 quota unit. */
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 10,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String
    ): PlaylistItemListResponse

    // --- Public endpoints ---

    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("key") apiKey: String
    ): SearchResponse


    /**
     * Durations for up to 50 ids for 1 quota unit. playlistItems does not
     * return duration, so this is the only way to identify Shorts.
     */
    @GET("videos")
    suspend fun getVideoDurations(
        @Query("part") part: String = "contentDetails,snippet",
        @Query("id") ids: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("key") apiKey: String
    ): VideoListResponse

    @GET("videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "snippet,statistics",
        @Query("id") id: String,
        @Query("key") apiKey: String
    ): VideoListResponse
}
