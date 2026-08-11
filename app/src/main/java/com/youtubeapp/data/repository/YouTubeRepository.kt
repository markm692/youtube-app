package com.youtubeapp.data.repository

import com.youtubeapp.BuildConfig
import com.youtubeapp.data.model.FeedVideo
import com.youtubeapp.data.model.PlaylistItem
import com.youtubeapp.data.model.SearchResponse
import com.youtubeapp.data.model.SubscriptionItem
import com.youtubeapp.data.model.VideoListResponse
import com.youtubeapp.data.remote.YouTubeApiService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
     * The user's subscription feed, emitted progressively.
     *
     * There is no "my subscription feed" endpoint, so a channel's uploads can
     * only be read one playlist at a time — around 170 calls for 170
     * subscriptions. Awaiting all of them meant roughly 40 seconds of blank
     * screen, so the work runs in waves and each wave is emitted as it lands:
     * the first videos appear after about a dozen calls rather than after all
     * of them.
     *
     * A wave is [CHANNELS_PER_WAVE] channels, sized so its videos fit a single
     * 50-id videos.list lookup — the wave costs its playlist calls plus exactly
     * one enrichment call.
     *
     * Each emission is the whole feed so far, newest first, so a collector can
     * render it directly.
     */
    fun subscriptionFeed(
        perChannel: Int = VIDEOS_PER_CHANNEL,
        excludeShorts: Boolean = true,
        excludedChannelIds: Set<String> = emptySet()
    ): Flow<List<FeedVideo>> = flow {
        // Filtering here rather than after fetching means a deselected channel
        // costs no calls at all, so trimming the list speeds up the feed and
        // lowers quota use proportionally.
        val channelIds = getMySubscriptions()
            .mapNotNull { it.snippet.resourceId.channelId }
            .distinct()
            .filterNot { it in excludedChannelIds }
        if (channelIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        // Resolve each channel's "uploads" playlist, 50 ids per request.
        val uploadPlaylists = channelIds.chunked(50).flatMap { chunk ->
            runCatching {
                apiService.getChannels(ids = chunk.joinToString(","), apiKey = apiKey)
                    .items
                    .mapNotNull { it.contentDetails?.relatedPlaylists?.uploads }
            }.getOrDefault(emptyList())
        }

        val region = deviceRegion()
        val accumulated = mutableListOf<FeedVideo>()
        val seen = mutableSetOf<String>()

        for (wave in uploadPlaylists.chunked(CHANNELS_PER_WAVE)) {
            val raw = coroutineScope {
                val gate = Semaphore(MAX_CONCURRENT_REQUESTS)
                wave.map { playlistId ->
                    async {
                        runCatching {
                            gate.withPermit {
                                apiService.getPlaylistItems(
                                    playlistId = playlistId,
                                    maxResults = perChannel,
                                    apiKey = apiKey
                                ).items
                            }
                            // A failing channel shouldn't take the feed with it.
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }

            val fresh = raw.mapNotNull(::toFeedVideo).filter { seen.add(it.videoId) }
            if (fresh.isEmpty()) continue

            accumulated += enrich(fresh, region, excludeShorts)
            emit(accumulated.sortedByDescending { it.publishedAt.orEmpty() })
        }
    }

    /**
     * Attaches durations and drops what cannot be watched.
     *
     * One videos.list call per 50 ids, 1 quota unit each and a flat cost
     * regardless of parts — so duration, regionRestriction and
     * liveBroadcastContent all arrive together for free.
     */
    private suspend fun enrich(
        videos: List<FeedVideo>,
        region: String?,
        excludeShorts: Boolean
    ): List<FeedVideo> {
        if (videos.isEmpty()) return videos
        val durations = mutableMapOf<String, Long>()
        val unplayable = mutableSetOf<String>()

        videos.map { it.videoId }.chunked(50).forEach { chunk ->
            runCatching {
                apiService.getVideoDurations(ids = chunk.joinToString(","), apiKey = apiKey)
                    .items
                    .forEach { item ->
                        item.contentDetails?.duration
                            ?.let(::parseIsoDurationSeconds)
                            ?.let { durations[item.id] = it }

                        // Premieres and scheduled streams report duration "P0D",
                        // which is not a PT-form value and so parses to null.
                        // They also can't be played yet.
                        if (item.snippet.liveBroadcastContent
                                ?.lowercase()?.let { it != "none" } == true
                        ) {
                            unplayable += item.id
                        }

                        // Region-locked videos fail with player error 150 no
                        // matter what status.embeddable claims.
                        item.contentDetails?.regionRestriction?.let { rr ->
                            val allowed = rr.allowed
                            val blocked = rr.blocked
                            val playable = when {
                                allowed != null -> region != null && region in allowed
                                blocked != null -> region == null || region !in blocked
                                else -> true
                            }
                            if (!playable) unplayable += item.id
                        }
                    }
            }
        }

        return videos
            .map { it.copy(durationSeconds = durations[it.videoId]) }
            .filterNot { it.videoId in unplayable }
            .filter {
                !excludeShorts ||
                    (it.durationSeconds ?: Long.MAX_VALUE) > SHORTS_MAX_SECONDS
            }
    }

    private fun toFeedVideo(item: PlaylistItem): FeedVideo? {
        val videoId = item.contentDetails?.videoId ?: return null
        return FeedVideo(
            videoId = videoId,
            title = item.snippet.title,
            channelTitle = item.snippet.videoOwnerChannelTitle
                ?: item.snippet.channelTitle.orEmpty(),
            channelId = item.snippet.videoOwnerChannelId,
            thumbnailUrl = item.snippet.thumbnails?.high?.url
                ?: item.snippet.thumbnails?.medium?.url,
            // ISO-8601 timestamps sort correctly as strings.
            publishedAt = item.contentDetails.videoPublishedAt ?: item.snippet.publishedAt
        )
    }

    /** ISO country code for this device, used to drop region-locked videos. */
    private fun deviceRegion(): String? =
        java.util.Locale.getDefault().country.takeIf { it.isNotBlank() }?.uppercase()

    companion object {
        private const val MAX_SUBSCRIPTIONS = 200
        private const val MAX_CONCURRENT_REQUESTS = 6
        private const val VIDEOS_PER_CHANNEL = 5

        /** Sized so one wave's videos fit a single 50-id videos.list call. */
        private const val CHANNELS_PER_WAVE = 10

        /**
         * YouTube raised the Shorts ceiling to 3 minutes in late 2024, so 60s
         * let most of them through — and Shorts cannot be played in an embedded
         * player at all, reporting error 150 even though videos.list returns
         * status.embeddable = true for them. Filtering at the real ceiling
         * removes both the binge-optimised format and videos that would fail to
         * play. The cost is dropping genuinely short regular uploads, which
         * cannot be told apart from Shorts through the API.
         */
        private const val SHORTS_MAX_SECONDS = 180L

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
