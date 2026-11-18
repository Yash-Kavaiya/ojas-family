package com.ojashealing.family.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.social.ViewTracker
import com.ojashealing.family.data.social.shortCount
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Counts this member's views; null in previews and tests (nothing is sent). */
val LocalViewTracker = staticCompositionLocalOf<ViewTracker?> { null }

/** An eye and how many members have seen it, shown to everyone. Nothing until the first view. */
@Composable
fun ViewCount(count: Long, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (count <= 0) return
    val label = stringResource(R.string.views_seen_by, count.toString())
    Row(
        modifier.clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Outlined.Visibility, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Text(shortCount(count), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Counts a view of [path] (e.g. "announcements/abc") once this stays on screen for [afterMs]: a
 * card scrolled past quickly does not count. Use afterMs = 0 for an item the member opened.
 */
@Composable
fun TrackView(path: String?, afterMs: Long = 1_000) {
    val tracker = LocalViewTracker.current ?: return
    if (path.isNullOrEmpty()) return
    LaunchedEffect(path) {
        if (afterMs > 0) delay(afterMs)
        tracker.markViewed(path)
    }
}

/** Counts a view when the member opens something (a video, a book): call it with the item's path. */
@Composable
fun rememberMarkViewed(): (String) -> Unit {
    val tracker = LocalViewTracker.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return androidx.compose.runtime.remember(tracker, scope) {
        { path: String -> if (tracker != null) scope.launch(kotlinx.coroutines.NonCancellable) { tracker.markViewed(path) } }
    }
}
