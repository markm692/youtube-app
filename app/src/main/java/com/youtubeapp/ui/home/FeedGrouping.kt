package com.youtubeapp.ui.home

import com.youtubeapp.data.model.FeedVideo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class FeedSection(
    val label: String,
    val videos: List<FeedVideo>
)

/**
 * Splits the feed into day sections.
 *
 * A subscription feed is finite, which is its most useful property next to an
 * algorithmic one — but 800 undifferentiated rows don't feel finite. Day
 * headers give the list a shape and make reaching the end of a day visible.
 *
 * Dates are handled with Calendar rather than java.time, which needs API 26 or
 * desugaring (minSdk here is 24).
 */
object FeedGrouping {

    // YouTube returns UTC, e.g. "2026-08-10T21:00:06Z". Some responses carry
    // fractional seconds, so try both shapes.
    private val PARSERS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    ).map { pattern ->
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val WEEKDAY = SimpleDateFormat("EEEE", Locale.getDefault())
    private val DAY_MONTH = SimpleDateFormat("d MMMM", Locale.getDefault())
    private val DAY_MONTH_YEAR = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    fun group(videos: List<FeedVideo>, now: Date = Date()): List<FeedSection> {
        if (videos.isEmpty()) return emptyList()

        // Preserve the incoming order (already newest first) within each bucket.
        val sections = LinkedHashMap<String, MutableList<FeedVideo>>()
        videos.forEach { video ->
            val label = label(video.publishedAt, now)
            sections.getOrPut(label) { mutableListOf() } += video
        }
        return sections.map { (label, items) -> FeedSection(label, items) }
    }

    /** Human label for when something was published, relative to [now]. */
    fun label(publishedAt: String?, now: Date = Date()): String {
        val published = publishedAt?.let(::parse) ?: return UNKNOWN
        return when (val days = daysBetween(published, now)) {
            0 -> "Today"
            1 -> "Yesterday"
            // Weekday names stop being unambiguous once a week has passed.
            in 2..6 -> WEEKDAY.format(published)
            else -> {
                val formatter = if (sameYear(published, now)) DAY_MONTH else DAY_MONTH_YEAR
                if (days < 0) "Today" else formatter.format(published)
            }
        }
    }

    private fun parse(value: String): Date? {
        PARSERS.forEach { parser ->
            runCatching { return parser.parse(value) }
        }
        return null
    }

    /** Whole calendar days between two instants, in the device's time zone. */
    private fun daysBetween(earlier: Date, later: Date): Int {
        val a = midnight(earlier)
        val b = midnight(later)
        val diffMs = b.timeInMillis - a.timeInMillis
        return Math.round(diffMs / MILLIS_PER_DAY.toDouble()).toInt()
    }

    private fun midnight(date: Date): Calendar = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun sameYear(a: Date, b: Date): Boolean =
        midnight(a).get(Calendar.YEAR) == midnight(b).get(Calendar.YEAR)

    private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    private const val UNKNOWN = "Earlier"
}
