package com.youtubeapp.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How much of a thumbnail to show.
 *
 * HIDDEN is the default: thumbnails are the most engagement-optimised element
 * on the platform. The modes stay switchable because the point is to find out
 * which one actually changes behaviour, and that needs a control.
 */
enum class ThumbnailMode {
    HIDDEN,
    GREY,
    COLOUR;

    val label: String
        get() = when (this) {
            HIDDEN -> "Text"
            GREY -> "Grey"
            COLOUR -> "Colour"
        }

    fun next(): ThumbnailMode = entries[(ordinal + 1) % entries.size]
}

class SettingsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _thumbnailMode = MutableStateFlow(
        runCatching { ThumbnailMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(ThumbnailMode.HIDDEN)
    )
    val thumbnailMode: StateFlow<ThumbnailMode> = _thumbnailMode

    fun cycleThumbnailMode() {
        val next = _thumbnailMode.value.next()
        prefs.edit().putString(KEY_MODE, next.name).apply()
        _thumbnailMode.value = next
    }

    private companion object {
        const val KEY_MODE = "thumbnail_mode"
    }
}
