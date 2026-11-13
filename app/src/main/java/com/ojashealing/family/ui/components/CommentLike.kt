package com.ojashealing.family.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.R
import com.ojashealing.family.data.social.CommentLikeRepository
import com.ojashealing.family.ui.theme.LotusRose
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Who is liking, and where likes are stored; provided once for the signed-in member. */
class CommentLikes(val repo: CommentLikeRepository, val uid: String)

val LocalCommentLikes = staticCompositionLocalOf<CommentLikes?> { null }

/**
 * The count to show right after a tap, before the server has updated the comment's likeCount.
 * [base] is the count when the member tapped; once the server's [count] moves off it, it is the truth.
 */
fun shownLikeCount(count: Int, base: Int, wasLiked: Boolean, nowLiked: Boolean?): Int {
    if (nowLiked == null || count != base) return count
    val offset = when {
        nowLiked && !wasLiked -> 1
        !nowLiked && wasLiked -> -1
        else -> 0
    }
    return (count + offset).coerceAtLeast(0)
}

/**
 * A small heart and count under a comment, answer or reply. The heart fills at once and pops;
 * the count follows the server. Members who may not like yet see the count only.
 */
@Composable
fun CommentLikeAction(commentPath: String, count: Int, enabled: Boolean, modifier: Modifier = Modifier) {
    val likes = LocalCommentLikes.current ?: return
    val liked by remember(commentPath, likes.uid) { likes.repo.observeLiked(commentPath, likes.uid) }.collectAsStateWithLifecycle(false)
    var pending by remember(commentPath) { mutableStateOf<Boolean?>(null) }
    var base by remember(commentPath) { mutableIntStateOf(count) }
    var wasLiked by remember(commentPath) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // The tap is settled once the server agrees on both the heart and the count
    LaunchedEffect(liked, count) {
        if (pending != null && pending == liked && count != base) pending = null
    }

    val shownLiked = pending ?: liked
    val shownCount = shownLikeCount(count, base, wasLiked, pending)
    val pop by animateFloatAsState(if (shownLiked) 1f else 0.85f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium), label = "pop")
    val tint = if (shownLiked) LotusRose else MaterialTheme.colorScheme.onSurfaceVariant
    val likeLabel = stringResource(R.string.action_like)

    if (!enabled && shownCount == 0) return
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = likeLabel) {
                    val next = !shownLiked
                    wasLiked = liked
                    base = count
                    pending = next
                    if (next) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { likes.repo.setLiked(commentPath, likes.uid, next).onFailure { pending = null } }
                } else Modifier
            )
            .semantics { stateDescription = if (shownLiked) "liked" else "" }
            .padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            if (shownLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (enabled) likeLabel else null,
            tint = tint,
            modifier = Modifier.size(16.dp).scale(if (shownLiked) pop * 1.0f else 1f)
        )
        if (shownCount > 0) Text("$shownCount", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
