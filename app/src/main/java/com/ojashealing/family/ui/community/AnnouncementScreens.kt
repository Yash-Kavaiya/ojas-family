package com.ojashealing.family.ui.community

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import com.ojashealing.family.ui.adaptive.ownedInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.commentThreads
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.Announcement
import com.ojashealing.family.data.social.MediaKind
import com.ojashealing.family.ui.components.AudioPlayer
import com.ojashealing.family.ui.components.InlineVideoPlayer
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.PostImage
import com.ojashealing.family.ui.components.SendPillButton
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.theme.noticeTintFor
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.runtime.remember
import com.ojashealing.family.ui.components.LikeButton
import com.ojashealing.family.ui.components.RollingCount
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.PostComposer
import com.ojashealing.family.ui.thought.LockHint
import com.ojashealing.family.ui.thought.PostRow

/** One announcement: a saffron header strip, the message, its picture, audio or video, and likes and comments. */
@Composable
fun AnnouncementCard(
    n: Announcement,
    liked: Boolean = false,
    canLike: Boolean = false,
    onLike: () -> Unit = {},
    onOpen: (() -> Unit)? = null
) {
    // Each announcement has its own colour: pastel in light mode, deep in dark mode (follows the app theme)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = noticeTintFor(n.id)
    val accent = if (dark) tint.accentDark else tint.accentLight
    OjasCard(
        onClick = onOpen,
        containerColor = if (dark) tint.cardDark else tint.cardLight,
        borderColor = if (dark) tint.borderDark else tint.borderLight
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Campaign, contentDescription = null, tint = if (dark) Color(0xFF1A1030) else Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.announcement_label), style = MaterialTheme.typography.labelLarge, color = accent)
                if (n.createdAt > 0) {
                    Text(
                        stringResource(R.string.posted_ago, DateUtils.getRelativeTimeSpanString(n.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(n.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (n.body.isNotBlank()) Text(com.ojashealing.family.ui.components.linkified(n.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (n.mediaUrl.isEmpty()) com.ojashealing.family.ui.components.LinkPreviewFor(n.body)
        if (n.mediaUrl.isNotEmpty()) when (n.mediaKind) {
            MediaKind.Image -> PostImage(n.mediaUrl)
            MediaKind.Audio -> AudioPlayer(n.mediaUrl)
            MediaKind.Video -> InlineVideoPlayer(n.mediaUrl)
            null -> Unit
        }
        com.ojashealing.family.ui.components.TrackView("announcements/${n.id}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(liked = liked, enabled = canLike, onToggle = onLike)
            RollingCount(n.likeCount, { pluralStringResource(R.plurals.likes_plural, it, it) })
            Box(Modifier.weight(1f))
            com.ojashealing.family.ui.components.ViewCount(n.viewCount, Modifier.padding(end = Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(pluralStringResource(R.plurals.comments_plural, n.commentCount, n.commentCount), style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}

/** An announcement with all its comments and the comment box; opened from the list or its notification. */
@Composable
fun AnnouncementDetailScreen(vm: CommunityViewModel, id: String, onBack: () -> Unit, showBack: Boolean = true, embedded: Boolean = false) {
    val perms by vm.permissions.collectAsStateWithLifecycle()
    val notices by vm.announcements.collectAsStateWithLifecycle()
    val liked by remember(id) { vm.announcementLiked(id) }.collectAsStateWithLifecycle(false)
    val comments by remember(id) { vm.announcementComments(id) }.collectAsStateWithLifecycle(emptyList())
    val sending by vm.sending.collectAsStateWithLifecycle()
    val n = notices?.firstOrNull { it.id == id }
    val p = perms
    val openThreads = com.ojashealing.family.ui.components.rememberOpenThreads()
    var replyId by rememberSaveable { mutableStateOf<String?>(null) }
    val replyingTo = comments.firstOrNull { it.id == replyId }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
      // The comment box sits under the list, pinned above the keyboard
      Column(Modifier.fillMaxSize().ownedInsets(embedded)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            Text(stringResource(R.string.announcement_label), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (n == null || p == null) {
                item(key = "loading") { LoadingState(Modifier.fillMaxWidth().heightIn(min = 200.dp)) }
                return@LazyColumn
            }
            item(key = "card") {
                Box(Modifier.padding(horizontal = Spacing.l)) {
                    AnnouncementCard(n, liked, p.canLike, onLike = { vm.setAnnouncementLiked(id, !liked) })
                }
            }
            item(key = "commentsTitle") {
                Text(stringResource(R.string.home_comments), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m))
            }
            if (comments.isEmpty()) item(key = "none") {
                Text(stringResource(R.string.home_no_comments), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l))
            }
            commentThreads(
                comments, openThreads,
                onReply = if (p.canReplyAnnouncement) ({ c: Post -> replyId = c.id }) else null,
                trailing = { c: Post -> ReportMenu(c.author, "announcements/$id/comments/${c.id}", c.text) },
                pathOf = { c: Post -> "announcements/$id/comments/${c.id}" },
                canLike = p.canLike
            )
        }
        if (n != null && p != null) {
            com.ojashealing.family.ui.thought.CommentBar {
                if (p.canReplyAnnouncement) {
                    PostComposer(
                        hint = stringResource(R.string.home_comment_hint),
                        // Replies to an announcement are words only
                        canAttachImage = false,
                        mentionable = com.ojashealing.family.ui.components.LocalStaffMentions.current + comments.map { it.author },
                        busy = sending,
                        onSend = { d, sent -> vm.commentOnAnnouncement(id, d.text, d.photo, d.mentions, d.reply, sent) },
                        replyingTo = replyingTo,
                        onCancelReply = { replyId = null }
                    )
                } else LockHint(stringResource(R.string.home_comment_locked, com.ojashealing.family.ui.components.accessRuleText(com.ojashealing.family.domain.AccessRules.ReplyAnnouncement)))
            }
        }
      }
    }
}

/** Admins write an announcement: a title and a message (a Zoom meeting, a new course). Text only. */
@Composable
fun ComposeAnnouncementScreen(vm: CommunityViewModel, onBack: () -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    val sending by vm.sending.collectAsStateWithLifecycle()
    val failed by vm.announceFailed.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.community_post_notice), style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(150) },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body, onValueChange = { body = it.take(2000) },
                    label = { Text(stringResource(R.string.field_message)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                )
                if (failed) Text(stringResource(R.string.announce_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            SendPillButton(
                text = stringResource(R.string.announce_send),
                onClick = { vm.announce(title, body, null, null, onBack) },
                enabled = title.isNotBlank(),
                busy = sending,
                modifier = Modifier.padding(Spacing.l)
            )
        }
    }
}
