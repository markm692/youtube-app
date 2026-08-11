package com.youtubeapp.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Small persisted preferences. Deliberately a toggle rather than a hard removal:
 * the point of greyscale thumbnails is to find out whether it changes your
 * behaviour, and you can't tell that if you can't switch it back.
 */
class SettingsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _greyscaleThumbnails =
        MutableStateFlow(prefs.getBoolean(KEY_GREYSCALE, true))
    val greyscaleThumbnails: StateFlow<Boolean> = _greyscaleThumbnails

    fun setGreyscaleThumbnails(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GREYSCALE, enabled).apply()
        _greyscaleThumbnails.value = enabled
    }

    fun toggleGreyscaleThumbnails() =
        setGreyscaleThumbnails(!_greyscaleThumbnails.value)

    private companion object {
        const val KEY_GREYSCALE = "greyscale_thumbnails"
    }
}
