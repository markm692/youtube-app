package com.youtubeapp.data.cache

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.youtubeapp.data.model.FeedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Last known feed, so a cold start shows something immediately instead of
 * spinning while ~180 calls complete.
 *
 * Backed by a file rather than SharedPreferences: prefs are loaded wholly into
 * memory at startup, and a few hundred videos of JSON has no business being
 * there.
 */
class FeedCache(context: Context) {

    private val file = File(context.filesDir, "feed_cache.json")
    private val gson = Gson()
    private val type = object : TypeToken<List<FeedVideo>>() {}.type

    suspend fun load(): List<FeedVideo> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            gson.fromJson<List<FeedVideo>>(file.readText(), type).orEmpty()
        }.getOrElse {
            // A corrupt cache should cost a refresh, not a crash.
            Log.w(TAG, "Discarding unreadable feed cache", it)
            runCatching { file.delete() }
            emptyList()
        }
    }

    suspend fun save(videos: List<FeedVideo>) = withContext(Dispatchers.IO) {
        runCatching {
            file.writeText(gson.toJson(videos.take(MAX_CACHED), type))
        }.onFailure { Log.w(TAG, "Could not write feed cache", it) }
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        runCatching { file.delete() }
        Unit
    }

    private companion object {
        const val TAG = "FeedCache"

        /** Enough to fill the screen many times over; the rest is re-fetched. */
        const val MAX_CACHED = 400
    }
}
