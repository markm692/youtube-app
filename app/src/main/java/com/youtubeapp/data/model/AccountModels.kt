package com.youtubeapp.data.model

// --- subscriptions.list ---

data class SubscriptionListResponse(
    val items: List<SubscriptionItem> = emptyList(),
    val nextPageToken: String? = null
)

data class SubscriptionItem(
    val id: String,
    val snippet: SubscriptionSnippet
)

data class SubscriptionSnippet(
    val title: String,
    val description: String?,
    val thumbnails: Thumbnails?,
    val resourceId: ResourceId
)

data class ResourceId(
    val kind: String?,
    val channelId: String?,
    val videoId: String?
)

// --- channels.list ---

data class ChannelListResponse(
    val items: List<ChannelItem> = emptyList()
)

data class ChannelItem(
    val id: String,
    val snippet: ChannelSnippet?,
    val contentDetails: ChannelContentDetails?
)

data class ChannelSnippet(
    val title: String,
    val thumbnails: Thumbnails?
)

data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists?
)

data class RelatedPlaylists(
    val uploads: String?,
    val likes: String?
)

// --- playlistItems.list ---

data class PlaylistItemListResponse(
    val items: List<PlaylistItem> = emptyList(),
    val nextPageToken: String? = null
)

data class PlaylistItem(
    val id: String,
    val snippet: PlaylistItemSnippet,
    val contentDetails: PlaylistItemContentDetails?
)

data class PlaylistItemSnippet(
    val title: String,
    val description: String?,
    val thumbnails: Thumbnails?,
    val channelTitle: String?,
    val videoOwnerChannelTitle: String?,
    val publishedAt: String?
)

data class PlaylistItemContentDetails(
    val videoId: String?,
    val videoPublishedAt: String?
)

/**
 * A video in the signed-in user's subscription feed, flattened from a
 * playlistItem so the UI does not have to care where it came from.
 */
data class FeedVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val publishedAt: String?
)
