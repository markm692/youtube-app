package com.youtubeapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.youtubeapp.data.model.FeedVideo

/**
 * A text-first row. The thumbnail is not shown until the title is tapped, so
 * the image is something you pull deliberately rather than something pushed at
 * you — and opening a video takes a second, separate tap.
 *
 * Wide screens reveal the thumbnail at the leading edge, so it lands on the
 * same left margin the text sits on and the text slides right to make room.
 * Narrow ones reveal it underneath, where a side-by-side image would leave the
 * title too cramped to read.
 */
@Composable
fun VideoRow(
    video: FeedVideo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Cap the measure so titles don't run the full width of a tablet, which
    // hurts readability and strands the thumbnail far from the text it belongs to.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    BoxWithConstraints(modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH)) {
        val wide = maxWidth >= WIDE_BREAKPOINT

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (wide) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandHorizontally(animationSpec = tween(ANIM_MS)) + fadeIn(),
                    exit = shrinkHorizontally(animationSpec = tween(ANIM_MS)) + fadeOut()
                ) {
                    Row {
                        Thumbnail(video, onPlay, Modifier.width(THUMBNAIL_WIDTH))
                        Spacer(Modifier.width(20.dp))
                    }
                }
            }

            // Capped rather than weighted: a title stretched across the row is
            // harder to scan than one that wraps at a comfortable measure.
            Column(modifier = Modifier.widthIn(max = TITLE_MAX_WIDTH)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(video.channelTitle)
                        video.durationSeconds?.let {
                            append("  ·  ").append(formatDuration(it))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                // Narrow: thumbnail drops in below the text.
                if (!wide) {
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(animationSpec = tween(ANIM_MS)) + fadeIn(),
                        exit = shrinkVertically(animationSpec = tween(ANIM_MS)) + fadeOut()
                    ) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Thumbnail(video, onPlay, Modifier.fillMaxWidth())
                        }
                    }
                }
            }

        }
    }
    }
}

@Composable
private fun Thumbnail(
    video: FeedVideo,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPlay),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = onPlay,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("Watch")
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private val WIDE_BREAKPOINT = 600.dp
private val THUMBNAIL_WIDTH = 260.dp
private val CONTENT_MAX_WIDTH = 900.dp
private val TITLE_MAX_WIDTH = 460.dp
private const val ANIM_MS = 260
