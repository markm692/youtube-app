package com.youtubeapp.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which subscribed channels are allowed into the feed.
 *
 * Stores the *excluded* ids rather than the included ones, so the default with
 * nothing configured is "everything on", and a newly subscribed channel shows
 * up instead of silently going missing until it is discovered in a settings
 * screen.
 */
class ChannelPreferences(context: Context) {

    private val prefs =
        context.getSharedPreferences("channel_prefs", Context.MODE_PRIVATE)

    private val _excluded = MutableStateFlow(
        prefs.getStringSet(KEY_EXCLUDED, emptySet()).orEmpty().toSet()
    )
    val excluded: StateFlow<Set<String>> = _excluded

    fun isEnabled(channelId: String): Boolean = channelId !in _excluded.value

    fun setEnabled(channelId: String, enabled: Boolean) {
        val next = _excluded.value.toMutableSet().apply {
            if (enabled) remove(channelId) else add(channelId)
        }
        persist(next)
    }

    fun enableAll() = persist(emptySet())

    fun disableAll(channelIds: Collection<String>) = persist(channelIds.toSet())

    private fun persist(next: Set<String>) {
        // Copy on write: SharedPreferences must not be handed a set that is
        // later mutated, and callers hold onto the StateFlow value.
        prefs.edit().putStringSet(KEY_EXCLUDED, HashSet(next)).apply()
        _excluded.value = next
    }

    private companion object {
        const val KEY_EXCLUDED = "excluded_channel_ids"
    }
}
