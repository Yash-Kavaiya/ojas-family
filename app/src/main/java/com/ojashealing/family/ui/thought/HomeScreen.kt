package com.ojashealing.family.ui.thought

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import com.ojashealing.family.data.about.AboutInfo
import com.ojashealing.family.data.about.OJAS_YOUTUBE
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.commentThreads
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.PostStatus
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.ui.components.LikeButton
import com.ojashealing.family.domain.Role
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.SectionHeader
import com.ojashealing.family.ui.theme.SacredGold
import com.ojashealing.family.ui.theme.Spacing
import androidx.compose.foundation.clickable
import androidx.compose.runtime.compositionLocalOf
import com.ojashealing.family.ui.components.PostComposer
import com.ojashealing.family.ui.components.PostImage
import com.ojashealing.family.ui.components.NameBadges
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.ojashealing.family.ui.theme.LocalQuoteStyle
import com.ojashealing.family.ui.theme.OjasGradients

/** Opens a member's profile when the viewer may see other profiles (admins, staff, Member Level 3); null otherwise. */
val LocalOpenProfile = compositionLocalOf<((String) -> Unit)?> { null }

private fun dayLabel(id: String): String = LocalDateLike.parse(id)?.let { "%02d/%02d".format(it.day, it.month) } ?: id

@Composable
fun HomeScreen(
    vm: ThoughtViewModel,
    onSchedule: () -> Unit,
    onOpenThought: (String) -> Unit = {},
    about: AboutInfo = AboutInfo(),
    onOpenAbout: (String) -> Unit = {},
    onOpenScheduled: () -> Unit = {}
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val summary by vm.scheduleSummary.collectAsStateWithLifecycle()

    val state = ui ?: return LoadingState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item(key = "header", contentType = "header") { SectionHeader(stringResource(R.string.notif_thought)) }
            summary?.let { sum ->
                item(key = "scheduled", contentType = "scheduled") { ScheduledStrip(sum, onOpenScheduled, Modifier.padding(horizontal = Spacing.l)) }
            }
            val thought = state.selected
            if (thought == null) {
                item(key = "empty", contentType = "empty") {
                    EmptyState(Icons.Outlined.WbSunny, stringResource(R.string.empty_home_title), stringResource(R.string.empty_home_body))
                }
            } else {
                item(key = "days", contentType = "days") {
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        items(state.days, key = { it.id }, contentType = { "day" }) { day ->
                            FilterChip(
                                selected = day.id == thought.id,
                                onClick = { vm.select(day.id) },
                                label = { Text(if (day.id == vm.todayId) stringResource(R.string.home_today) else dayLabel(day.id)) }
                            )
                        }
                    }
                }
                item(key = "card", contentType = "card") {
                    // The thought sits in its own box; tapping it opens the thought with all its comments
                    ThoughtCard(
                        thought = thought,
                        dayText = if (thought.id == vm.todayId) stringResource(R.string.home_today) else dayLabel(thought.id),
                        liked = liked,
                        canLike = state.canLike,
                        onLike = vm::toggleLike,
                        onOpen = { onOpenThought(thought.id) },
                        preview = true,
                        modifier = Modifier.padding(horizontal = Spacing.l)
                    )
                }
            }
            item(key = "about", contentType = "about") { AboutBox(about, onOpenAbout, Modifier.padding(horizontal = Spacing.l)) }
        }
        if (state.canSchedule) {
            FloatingActionButton(
                onClick = onSchedule,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l)
            ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_schedule)) }
        }
    }
}

/** Admin only: how many thoughts are scheduled, up to which day, and the first day still empty. */
@Composable
private fun ScheduledStrip(summary: ScheduleSummary, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                val head = if (summary.count == 0) stringResource(R.string.home_scheduled_none)
                else androidx.compose.ui.res.pluralStringResource(R.plurals.home_scheduled_count, summary.count, summary.count) +
                    (summary.lastId?.let { " · " + stringResource(R.string.home_scheduled_until, dayLabel(it)) }.orEmpty())
                Text(head, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.home_next_gap, dayLabel(summary.nextGapId)), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Admin: every thought scheduled after today; tapping one opens it for editing. */
@Composable
fun ScheduledThoughtsScreen(vm: ThoughtViewModel, onEdit: (String) -> Unit, onBack: () -> Unit) {
    val list by vm.scheduled.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.scheduled_title), style = MaterialTheme.typography.titleLarge)
            }
            val items = list
            when {
                items == null -> LoadingState()
                items.isEmpty() -> EmptyState(Icons.Outlined.Event, stringResource(R.string.home_scheduled_none), "")
                else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(items, key = { it.id }) { t ->
                        OjasCard(onClick = { onEdit(t.id) }) {
                            Text(dayLabel(t.id), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(t.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** The Thought of the Day box. In [preview] the text is clipped and a "comments" row invites a tap. */
@Composable
fun ThoughtCard(
    thought: com.ojashealing.family.data.social.Thought,
    dayText: String,
    liked: Boolean,
    canLike: Boolean,
    onLike: () -> Unit,
    onOpen: (() -> Unit)?,
    preview: Boolean,
    modifier: Modifier = Modifier
) {
    OjasCard(modifier = modifier, onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(OjasGradients.sunrise))
                .padding(horizontal = Spacing.m, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.notif_thought), style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.weight(1f))
            Text(dayText, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
        }
        Text(thought.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        // On Home a short preview; the whole thought, picture and comments open on tap
        Text(
            thought.text,
            style = LocalQuoteStyle.current,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (preview) 3 else Int.MAX_VALUE,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (thought.imageUrl.isNotEmpty()) {
            // Whole picture at its own shape; a little shorter on Home, where tapping opens the thought
            if (preview) com.ojashealing.family.ui.components.ResponsiveImage(thought.imageUrl, maxHeight = 320.dp)
            else PostImage(thought.imageUrl)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(liked = liked, enabled = canLike, onToggle = onLike)
            com.ojashealing.family.ui.components.RollingCount(thought.likeCount, { stringResource(R.string.likes_count, it) })
            Box(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.home_view_comments, thought.commentCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (preview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!canLike) LockHint(stringResource(R.string.home_like_locked))
    }
}

/** Below the thought: About Ojas Family, About Swamiji, the YouTube channel and any links admins add. */
@Composable
fun AboutBox(about: AboutInfo, onOpenAbout: (String) -> Unit, modifier: Modifier = Modifier) {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    OjasCard(modifier = modifier) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        AboutRow(Icons.Outlined.Spa, stringResource(R.string.about_ojas_family)) { onOpenAbout("ojas") }
        AboutRow(Icons.Outlined.SelfImprovement, stringResource(R.string.about_swamiji)) { onOpenAbout("swamiji") }
        AboutRow(Icons.Outlined.SmartDisplay, stringResource(R.string.about_youtube)) { runCatching { uri.openUri(OJAS_YOUTUBE) } }
        about.links.forEach { l -> AboutRow(Icons.Outlined.Link, l.title) { runCatching { uri.openUri(l.url) } } }
    }
}

@Composable
private fun AboutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One thought with all its comments; opened by tapping the Home card or a notification.
 * The comment box sits outside the list, pinned above the keyboard, so it never hides behind it.
 */
@Composable
fun ThoughtDetailScreen(vm: ThoughtViewModel, thoughtId: String, onBack: () -> Unit) {
    LaunchedEffect(thoughtId) { vm.select(thoughtId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val state = ui
    val thought = state?.selected?.takeIf { it.id == thoughtId }
    val openThreads = com.ojashealing.family.ui.components.rememberOpenThreads()
    var replyId by rememberSaveable { mutableStateOf<String?>(null) }
    val replyingTo = comments.firstOrNull { it.id == replyId }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.notif_thought), style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                if (state == null || thought == null) {
                    item(key = "loading") {
                        if (state == null) LoadingState(Modifier.fillMaxWidth().padding(Spacing.xl))
                        else EmptyState(Icons.Outlined.WbSunny, stringResource(R.string.empty_home_title), stringResource(R.string.empty_home_body))
                    }
                    return@LazyColumn
                }
                item(key = "card") {
                    ThoughtCard(
                        thought, if (thought.id == vm.todayId) stringResource(R.string.home_today) else dayLabel(thought.id),
                        liked, state.canLike, vm::toggleLike, onOpen = null, preview = false,
                        modifier = Modifier.padding(horizontal = Spacing.l)
                    )
                }
                item(key = "commentsTitle") {
                    Text(
                        stringResource(R.string.home_comments), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m)
                    )
                }
                if (comments.isEmpty()) item(key = "noComments") {
                    Text(stringResource(R.string.home_no_comments), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l))
                }
                commentThreads(
                    comments, openThreads,
                    onReply = if (state.canComment) ({ c: Post -> replyId = c.id }) else null,
                    pathOf = { c: Post -> "thoughts/${thought.id}/comments/${c.id}" },
                    canLike = state.canLike
                )
            }
            if (state != null && thought != null) {
                CommentBar {
                    if (state.canComment) {
                        PostComposer(
                            hint = stringResource(R.string.home_comment_hint),
                            canAttachImage = state.canAttachImage,
                            mentionable = comments.map { it.author },
                            busy = sending,
                            onSend = { d, sent -> vm.comment(d.text, d.photo, d.mentions, d.reply, sent) },
                            replyingTo = replyingTo,
                            onCancelReply = { replyId = null }
                        )
                    } else LockHint(stringResource(R.string.home_comment_locked))
                }
            }
        }
    }
}

/** The bar under a comment list that holds the comment box, drawn above the keyboard. */
@Composable
fun CommentBar(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) { content() }
    }
}

/** About Ojas Family or About Swamiji: the admin's text from config/about, or the built-in wording. */
@Composable
fun AboutScreen(kind: String, about: AboutInfo, onBack: () -> Unit) {
    val swamiji = kind == "swamiji"
    val text = (if (swamiji) about.swamiji else about.ojasFamily).ifBlank { stringResource(if (swamiji) R.string.about_swamiji_body else R.string.about_ojas_body) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(if (swamiji) R.string.about_swamiji else R.string.about_ojas_family), style = MaterialTheme.typography.titleLarge)
            }
            Box(
                Modifier.padding(horizontal = Spacing.l).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(OjasGradients.sunrise)).padding(Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ojas_lotus),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                )
            }
            OjasCard(modifier = Modifier.padding(horizontal = Spacing.l)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            androidx.compose.material3.OutlinedButton(
                onClick = { runCatching { uri.openUri(OJAS_YOUTUBE) } },
                modifier = Modifier.padding(horizontal = Spacing.l).fillMaxWidth()
            ) {
                Icon(Icons.Outlined.SmartDisplay, contentDescription = null)
                Text(stringResource(R.string.about_youtube), modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

@Composable
fun LockHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AuthorAvatar(author: Author, size: Int = 36) {
    val open = LocalOpenProfile.current
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (open != null && author.uid.isNotEmpty()) Modifier.clickable { open(author.uid) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (author.photo.isNotEmpty()) AsyncImage(model = author.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        else Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Role tick (gold Swamiji, blue admin, teal senior) and level coin, so trusted voices stand out
@Composable
fun AuthorLine(author: Author) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            author.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        NameBadges(author.role, author.userLevel, author.memberLevel)
    }
}

@Composable
fun PostRow(
    post: Post,
    trailing: (@Composable () -> Unit)? = null,
    onReply: (() -> Unit)? = null,
    avatarSize: Int = 32,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
    mentionName: String = "",
    likePath: String? = null,
    canLike: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.l + indent, end = Spacing.s, top = Spacing.xs, bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s + 2.dp)
    ) {
        AuthorAvatar(post.author, avatarSize)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Name, badges and time on one line, like a LinkedIn comment
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Box(Modifier.weight(1f, fill = false)) { AuthorLine(post.author) }
                if (post.createdAt > 0) {
                    Text(
                        "· " + android.text.format.DateUtils.getRelativeTimeSpanString(
                            post.createdAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val body = if (mentionName.isNotEmpty()) {
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                    append("@$mentionName ")
                    pop()
                    append(post.text)
                }
            } else androidx.compose.ui.text.AnnotatedString(post.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            if (post.imageUrl.isNotEmpty()) PostImage(post.imageUrl, Modifier.padding(top = Spacing.xs), maxHeight = 260.dp)
            if (post.status == PostStatus.Pending) {
                Text(stringResource(R.string.post_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            // Like and Reply sit together under the text, like a LinkedIn comment
            if (likePath != null || onReply != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (likePath != null && post.status == PostStatus.Visible) {
                        com.ojashealing.family.ui.components.CommentLikeAction(likePath, post.likeCount, enabled = canLike)
                    }
                    if (onReply != null) {
                        Text(
                            stringResource(R.string.action_reply),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onReply)
                                .padding(vertical = Spacing.s, horizontal = Spacing.xs)
                        )
                    }
                }
            }
        }
        trailing?.invoke()
    }
}
