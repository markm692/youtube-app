package com.youtubeapp.data.model

import com.google.gson.annotations.SerializedName

data class SearchResponse(
    val items: List<SearchItem>,
    val nextPageToken: String?,
    val prevPageToken: String?,
    val pageInfo: PageInfo
)

data class SearchItem(
    val id: VideoId,
    val snippet: Snippet
)

data class VideoId(
    val kind: String?,
    val videoId: String?
)

data class Snippet(
    val title: String,
    val description: String,
    val thumbnails: Thumbnails,
    val channelTitle: String,
    @SerializedName("publishedAt")
    val publishedAt: String
)

data class Thumbnails(
    val default: Thumbnail?,
    val medium: Thumbnail?,
    val high: Thumbnail?
)

data class Thumbnail(
    val url: String,
    val width: Int?,
    val height: Int?
)

data class PageInfo(
    val totalResults: Int,
    val resultsPerPage: Int
)

data class VideoListResponse(
    val items: List<VideoItem>
)

data class VideoItem(
    val id: String,
    val snippet: Snippet,
    val statistics: Statistics?
)

data class Statistics(
    val viewCount: String?,
    val likeCount: String?,
    val commentCount: String?
)
