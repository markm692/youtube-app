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
    suspend fun getSubscriptionFeed(
        perChannel: Int = 5,
        excludeShorts: Boolean = true
    ): List<FeedVideo> = coroutineScope {
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
            .let { withDurations(it, excludeShorts) }
    }

    /**
     * Drops Shorts, which are the most binge-optimised format in the feed.
     *
     * There is no API flag for "is a Short", so duration is the proxy: one
     * videos.list call per 50 ids, 1 quota unit each. Note YouTube raised the
     * Shorts ceiling to 3 minutes in late 2024, so [SHORTS_MAX_SECONDS] only
     * catches the classic sub-minute ones; raise it to trade a few legitimate
     * short videos for stricter filtering. Videos whose duration can't be
     * resolved are kept rather than silently dropped.
     */
    private suspend fun withDurations(
        videos: List<FeedVideo>,
        excludeShorts: Boolean
    ): List<FeedVideo> {
        if (videos.isEmpty()) return videos
        val durations = mutableMapOf<String, Long>()
        videos.map { it.videoId }.chunked(50).forEach { chunk ->
            runCatching {
                apiService.getVideoDurations(ids = chunk.joinToString(","), apiKey = apiKey)
                    .items
                    .forEach { item ->
                        item.contentDetails?.duration
                            ?.let(::parseIsoDurationSeconds)
                            ?.let { durations[item.id] = it }
                    }
            }
        }
        // The same lookup powers both the Shorts filter and the duration shown
        // on text-only cards, so surfacing it costs no extra quota.
        return videos
            .map { it.copy(durationSeconds = durations[it.videoId]) }
            .filter {
                !excludeShorts ||
                    (it.durationSeconds ?: Long.MAX_VALUE) > SHORTS_MAX_SECONDS
            }
    }

    companion object {
        private const val MAX_SUBSCRIPTIONS = 200
        private const val MAX_CONCURRENT_REQUESTS = 6
        private const val SHORTS_MAX_SECONDS = 60L

        private val ISO_DURATION =
            Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")

        /**
         * Parses ISO-8601 durations without java.time, which needs API 26 or
         * desugaring (minSdk here is 24).
         */
        internal fun parseIsoDurationSeconds(value: String): Long? {
            val m = ISO_DURATION.matchEntire(value) ?: return null
            val hours = m.groupValues[1].toLongOrNull() ?: 0
            val minutes = m.groupValues[2].toLongOrNull() ?: 0
            val seconds = m.groupValues[3].toLongOrNull() ?: 0
            return hours * 3600 + minutes * 60 + seconds
        }
    }
}
