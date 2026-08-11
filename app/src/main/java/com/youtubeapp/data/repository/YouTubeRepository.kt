package com.youtubeapp.data.repository

import com.youtubeapp.BuildConfig
import com.youtubeapp.data.model.FeedVideo
import com.youtubeapp.data.model.SearchResponse
import com.youtubeapp.data.model.SubscriptionItem
import com.youtubeapp.data.model.VideoListResponse
import com.youtubeapp.data.remote.YouTubeApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class YouTubeRepository(
    private val apiService: YouTubeApiService
) {
    private val apiKey = BuildConfig.YOUTUBE_API_KEY

    // --- Public data ---

    suspend fun searchVideos(query: String, pageToken: String? = null): SearchResponse =
        apiService.searchVideos(query = query, pageToken = pageToken, apiKey = apiKey)

    suspend fun getVideoDetails(videoId: String): VideoListResponse =
        apiService.getVideoDetails(id = videoId, apiKey = apiKey)

    // --- Signed-in data ---

    suspend fun getMySubscriptions(): List<SubscriptionItem> {
        val all = mutableListOf<SubscriptionItem>()
        var pageToken: String? = null
        do {
            val page = apiService.getMySubscriptions(pageToken = pageToken, apiKey = apiKey)
            all += page.items
            pageToken = page.nextPageToken
            // Cap paging so a very large subscription list can't blow the quota.
        } while (pageToken != null && all.size < MAX_SUBSCRIPTIONS)
        return all
    }

    /**
     * Latest uploads across the user's subscriptions, newest first.
     *
     * Quota: 1 unit per subscriptions page + 1 unit per 50 channels +
     * 1 unit per channel for its uploads. ~52 units for 50 subscriptions,
     * which is cheap next to a single search (100 units).
     */
    suspend fun getSubscriptionFeed(perChannel: Int = 5): List<FeedVideo> = coroutineScope {
        val subs = getMySubscriptions()
        val channelIds = subs.mapNotNull { it.snippet.resourceId.channelId }.distinct()
        if (channelIds.isEmpty()) return@coroutineScope emptyList()

        // Resolve each channel's "uploads" playlist, 50 ids per request.
        val uploadPlaylists = channelIds.chunked(50).flatMap { chunk ->
            apiService.getChannels(ids = chunk.joinToString(","), apiKey = apiKey)
                .items
                .mapNotNull { it.contentDetails?.relatedPlaylists?.uploads }
        }

        // Fetch each channel's recent uploads concurrently, but bounded.
        val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
        val perPlaylist = uploadPlaylists.map { playlistId ->
            async {
                runCatching {
                    gate.withPermit {
                        apiService.getPlaylistItems(
                            playlistId = playlistId,
                            maxResults = perChannel,
                            apiKey = apiKey
                        ).items
                    }
                }.getOrDefault(emptyList())
            }
        }

        perPlaylist.awaitAll()
            .flatten()
            .mapNotNull { item ->
                val videoId = item.contentDetails?.videoId ?: return@mapNotNull null
                FeedVideo(
                    videoId = videoId,
                    title = item.snippet.title,
                    channelTitle = item.snippet.videoOwnerChannelTitle
                        ?: item.snippet.channelTitle.orEmpty(),
                    thumbnailUrl = item.snippet.thumbnails?.high?.url
                        ?: item.snippet.thumbnails?.medium?.url,
                    publishedAt = item.contentDetails.videoPublishedAt
                        ?: item.snippet.publishedAt
                )
            }
            // ISO-8601 timestamps sort correctly as strings.
            .sortedByDescending { it.publishedAt.orEmpty() }
    }

    companion object {
        private const val MAX_SUBSCRIPTIONS = 200
        private const val MAX_CONCURRENT_REQUESTS = 6
    }
}
