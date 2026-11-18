package com.ojashealing.family.ui.ask

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.drawWithContent
import com.ojashealing.family.ui.theme.staggeredEntrance
import com.ojashealing.family.ui.theme.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import com.ojashealing.family.domain.Book
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.call.CallRequest
import com.ojashealing.family.data.call.RequestStatus
import com.ojashealing.family.data.call.staysAsReminder
import com.ojashealing.family.data.paid.Entitlements
import com.ojashealing.family.data.paid.PaidAccess
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.Access
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.SectionHeader
import com.ojashealing.family.ui.content.LockedDialog
import com.ojashealing.family.ui.content.VideoTile
import com.ojashealing.family.ui.paid.formatDate
import com.ojashealing.family.ui.theme.Spacing

/**
 * Spec 4, in three sections: Ask (the AI, personal chat 4.2 and personal call 4.3), Video (Understanding 4.1)
 * and Books (PDF books opened from their links).
 */
@Composable
fun AskTab(
    profile: UserProfile?,
    entitlements: Entitlements,
    understanding: List<Pair<ContentItem, Access>>?,
    onPlay: (ContentItem) -> Unit,
    /** 0 = Ask, 1 = Video, 2 = Books. */
    part: Int = 0,
    onPart: (Int) -> Unit = {},
    books: List<Pair<Book, Access>>? = emptyList(),
    onOpenBook: (Book) -> Unit = {},
    onOpenChat: (com.ojashealing.family.data.chat.ChatChannel) -> Unit,
    onCall: (com.ojashealing.family.data.chat.ChatChannel, video: Boolean) -> Unit,
    onPlans: (PaidAccess) -> Unit,
    onInbox: () -> Unit,
    onOpenAi: () -> Unit = {},
    onSchedule: (com.ojashealing.family.data.chat.ChatChannel) -> Unit = {},
    onHistory: () -> Unit = {},
    onMyCalls: () -> Unit = {},
    adminCallsReady: Boolean = false,
    scheduled: List<CallRequest> = emptyList(),
    approving: Boolean = false,
    onOpenRequest: (String) -> Unit = {},
) {
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    val p = profile ?: return LoadingState()
    Column(Modifier.fillMaxSize()) {
        androidx.compose.material3.TabRow(
            selectedTabIndex = part.coerceIn(0, 2),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            listOf(
                R.string.ask_part_ask to androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline,
                R.string.ask_part_video to androidx.compose.material.icons.Icons.Outlined.PlayCircleOutline,
                R.string.ask_part_books to androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook,
            ).forEachIndexed { i, (label, icon) ->
                androidx.compose.material3.Tab(
                    selected = part == i, onClick = { onPart(i) },
                    text = { com.ojashealing.family.ui.components.TabLabel(icon, stringResource(label)) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        androidx.compose.animation.AnimatedContent(
            targetState = part.coerceIn(0, 2),
            modifier = Modifier.weight(1f),
            transitionSpec = { com.ojashealing.family.ui.theme.sectionSwitch(targetState > initialState) },
            label = "askPart",
        ) { shown ->
            Box(Modifier.fillMaxSize()) {
                when (shown) {
                    1 -> VideoPart(understanding, onPlay) { locked = it }
                    2 -> BooksPart(books, onOpenBook) { locked = it }
                    else -> AskPart(
                        p, entitlements, onOpenChat, onCall, onPlans, onInbox, onOpenAi, onSchedule, onHistory, onMyCalls,
                        adminCallsReady, scheduled, approving, onOpenRequest,
                    )
                }
            }
        }
    }
    locked?.let { LockedDialog(it) { locked = null } }
}

/** Ask: the AI card, then chat, calls and booked-call reminders with Swamiji and the Admin team. */
@Composable
private fun AskPart(
    p: UserProfile,
    entitlements: Entitlements,
    onOpenChat: (com.ojashealing.family.data.chat.ChatChannel) -> Unit,
    onCall: (com.ojashealing.family.data.chat.ChatChannel, video: Boolean) -> Unit,
    onPlans: (PaidAccess) -> Unit,
    onInbox: () -> Unit,
    onOpenAi: () -> Unit,
    onSchedule: (com.ojashealing.family.data.chat.ChatChannel) -> Unit,
    onHistory: () -> Unit,
    onMyCalls: () -> Unit,
    adminCallsReady: Boolean,
    scheduled: List<CallRequest>,
    approving: Boolean,
    onOpenRequest: (String) -> Unit,
) {
    val staff = Permissions.isChatStaff(p.viewer)
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item(key = "ai") { SwamijiAiCard(onOpenAi, Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l)) }
        item(key = "personal") {
            SectionHeader(stringResource(R.string.ask_personal))
            Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                scheduled.filter { staysAsReminder(it) }
                    .sortedBy { if (it.status == RequestStatus.Proposed) it.proposedAt else it.at }
                    .take(4)
                    .forEach { r -> CallReminderCard(r, approving) { onOpenRequest(r.id) } }
                if (staff) {
                    PersonalCard(Icons.Outlined.Inbox, stringResource(R.string.ask_staff_inbox), stringResource(R.string.ask_staff_inbox_desc), null) {
                        Button(onClick = onInbox) { Text(stringResource(R.string.ask_staff_inbox)) }
                    }
                }
                // Members (and senior members, who Swamiji or an admin may reach out to) contact only Swamiji and
                // the Admin team; there is no way to reach another member
                if (!staff || p.viewer.role == com.ojashealing.family.domain.Role.SeniorMember) {
                    val chatOk = Permissions.canChat(p.viewer, entitlements.active(PaidAccess.Chat))
                    val audioOk = Permissions.canAudioCall(p.viewer, entitlements[PaidAccess.Call]?.canCall() == true)
                    val videoOk = Permissions.canVideoCall(p.viewer)
                    if (!staff) LevelStrip(p.viewer.memberLevel.rank)
                    ContactCard(
                        title = stringResource(R.string.role_swamiji),
                        subtitle = stringResource(R.string.ask_contact_swamiji_desc),
                        icon = Icons.Outlined.SelfImprovement,
                        chatOk = chatOk, audioOk = audioOk, videoOk = videoOk, callsReady = true,
                        onChat = { if (chatOk) onOpenChat(com.ojashealing.family.data.chat.ChatChannel.Swamiji) else onPlans(PaidAccess.Membership) },
                        onAudio = { if (audioOk) onCall(com.ojashealing.family.data.chat.ChatChannel.Swamiji, false) else onPlans(PaidAccess.Membership) },
                        onVideo = { if (videoOk) onCall(com.ojashealing.family.data.chat.ChatChannel.Swamiji, true) else onPlans(PaidAccess.Membership) },
                        onSchedule = { if (audioOk || videoOk) onSchedule(com.ojashealing.family.data.chat.ChatChannel.Swamiji) else onPlans(PaidAccess.Membership) }
                    )
                    ContactCard(
                        title = stringResource(R.string.chat_tab_admin),
                        subtitle = stringResource(R.string.ask_contact_admin_desc),
                        icon = Icons.Outlined.SupportAgent,
                        chatOk = chatOk, audioOk = audioOk, videoOk = videoOk, callsReady = adminCallsReady,
                        onChat = { if (chatOk) onOpenChat(com.ojashealing.family.data.chat.ChatChannel.Admin) else onPlans(PaidAccess.Membership) },
                        onAudio = { if (audioOk) onCall(com.ojashealing.family.data.chat.ChatChannel.Admin, false) else onPlans(PaidAccess.Membership) },
                        onVideo = { if (videoOk) onCall(com.ojashealing.family.data.chat.ChatChannel.Admin, true) else onPlans(PaidAccess.Membership) },
                        onSchedule = { if (audioOk || videoOk) onSchedule(com.ojashealing.family.data.chat.ChatChannel.Admin) else onPlans(PaidAccess.Membership) }
                    )
                    entitlements[PaidAccess.Call]?.takeIf { it.isActive() && it.credits != null && p.viewer.memberLevel.rank < 2 }?.let { c ->
                        Text(stringResource(R.string.ask_calls_left, c.credits ?: 0), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.calls_history), modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                    }
                    if (!staff) OutlinedButton(onClick = onMyCalls, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.calls_my_calls), modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Video: the Understanding videos, one list. */
@Composable
private fun VideoPart(
    understanding: List<Pair<ContentItem, Access>>?,
    onPlay: (ContentItem) -> Unit,
    onLocked: (Access.Locked) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item(key = "understandingHeader") { SectionHeader(stringResource(R.string.ask_understanding)) }
        when {
            understanding == null -> item(key = "loading") { com.ojashealing.family.ui.components.LoadingSpinner(Modifier.fillMaxWidth().padding(Spacing.xl)) }
            understanding.isEmpty() -> item(key = "empty") {
                Text(stringResource(R.string.empty_ask_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l))
            }
            else -> items(understanding, key = { it.first.id }, contentType = { "video" }) { (item, access) ->
                val open = { if (access is Access.Locked) onLocked(access) else onPlay(item) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = open).padding(horizontal = Spacing.l, vertical = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoTile(item = item.copy(title = ""), access = access, onClick = open, modifier = Modifier.width(140.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Books: a shelf of covers, two across; tapping one opens its link so the phone shows the PDF. */
@Composable
private fun BooksPart(
    books: List<Pair<Book, Access>>?,
    onOpen: (Book) -> Unit,
    onLocked: (Access.Locked) -> Unit,
) {
    val markViewed = com.ojashealing.family.ui.components.rememberMarkViewed()
    Column(Modifier.fillMaxSize()) {
    SectionHeader(stringResource(R.string.ask_part_books))
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        when {
            books == null -> item(key = "loading", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { com.ojashealing.family.ui.components.LoadingSpinner(Modifier.fillMaxWidth().padding(Spacing.xl)) }
            books.isEmpty() -> item(key = "empty", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.books_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> items(books.size, key = { books[it].first.id }, contentType = { "book" }) { i ->
                val (book, access) = books[i]
                BookCard(book, locked = access is Access.Locked) {
                    if (access is Access.Locked) onLocked(access) else { markViewed("books/${book.id}"); onOpen(book) }
                }
            }
        }
    }
    }
}

// Soft cover colours for books without a picture; each book keeps its own, from its title
private val plainCovers = listOf(
    listOf(Color(0xFF3B5BA9), Color(0xFF1E2F63)),
    listOf(Color(0xFFB4532A), Color(0xFF6E2A12)),
    listOf(Color(0xFF2E7D6B), Color(0xFF134339)),
    listOf(Color(0xFF7A4BA8), Color(0xFF3D2260)),
    listOf(Color(0xFFB08A2E), Color(0xFF5E4612)),
)

@Composable
private fun BookCard(book: Book, locked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                .clip(shape)
        ) {
            if (book.coverUrl.isNotEmpty()) {
                com.ojashealing.family.ui.components.ShimmerImage(book.coverUrl, Modifier.fillMaxSize(), shape = shape, contentDescription = book.title)
            } else {
                val colors = plainCovers[(book.title.hashCode() and 0x7fffffff) % plainCovers.size]
                Column(
                    Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(colors)).padding(Spacing.m),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(28.dp))
                    Text(
                        book.title, style = MaterialTheme.typography.titleMedium, color = Color.White,
                        maxLines = 5, overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.size(width = 32.dp, height = 2.dp).background(Color.White.copy(alpha = 0.6f)))
                }
            }
            // The spine: a soft shadow line along the left edge, like a real book
            Box(Modifier.fillMaxHeight().width(6.dp).background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent))
            ))
            if (locked) Box(
                Modifier.align(Alignment.TopEnd).padding(Spacing.s).size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.locked_title), tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            com.ojashealing.family.ui.components.ViewCount(book.viewCount)
        }
    }
}

/** A booked call that stays on Ask until an hour after its time: waiting, or a confirmed reminder. */
@Composable
private fun CallReminderCard(r: CallRequest, approving: Boolean, onOpen: () -> Unit) {
    val whenAt = if (r.status == RequestStatus.Proposed) r.proposedAt else r.at
    val title = when (r.status) {
        RequestStatus.Confirmed -> stringResource(R.string.calls_reminder)
        RequestStatus.Proposed -> stringResource(R.string.calls_status_proposed)
        else -> stringResource(if (approving) R.string.calls_waiting_you else R.string.calls_waiting_approval)
    }
    val who = if (approving) r.memberName else stringResource(if (r.channel == "admin") R.string.chat_tab_admin else R.string.role_swamiji)
    OjasCard(onClick = onOpen) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (r.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(who, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(istTime(whenAt), stringResource(if (r.video) R.string.calls_video else R.string.calls_audio)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PersonalCard(icon: ImageVector, title: String, desc: String, status: String?, actions: @Composable () -> Unit) {
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Text(desc, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        status?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        actions()
    }
}

/**
 * Entry to Ask Swamiji AI: a deep healing-blue card (the same light as the practice player) with
 * a soft sheen passing over it now and then, so it reads as alive without shouting.
 */
@Composable
private fun SwamijiAiCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val reduced = com.ojashealing.family.ui.theme.LocalReducedMotion.current
    val sheen = androidx.compose.animation.core.rememberInfiniteTransition(label = "aiSheen")
    val x by sheen.animateFloat(
        initialValue = -0.6f, targetValue = 1.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(4200, delayMillis = 1800, easing = com.ojashealing.family.ui.theme.OjasEasing.Standard)
        ),
        label = "x",
    )
    Row(
        modifier = modifier
            .staggeredEntrance(0, key = "aiCard")
            .fillMaxWidth()
            .pressScale(press, pressed = 0.98f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0B3D91), androidx.compose.ui.graphics.Color(0xFF2F2A8C), androidx.compose.ui.graphics.Color(0xFF4A1E6B))))
            .drawWithContent {
                drawContent()
                if (!reduced) drawRect(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.10f), androidx.compose.ui.graphics.Color.Transparent),
                        start = androidx.compose.ui.geometry.Offset(size.width * x, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width * x + size.width * 0.35f, size.height),
                    )
                )
            }
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onOpen)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                Text(
                    stringResource(R.string.ai_beta_tag),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color(0xFF7A2E00),
                    modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .background(androidx.compose.ui.graphics.Color(0xFFFFE08A)).padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Text(stringResource(R.string.ai_card_desc), style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
    }
}

/** "Level 1: chat · Level 2: + audio · Level 3: + video", with the member's level highlighted. */
@Composable
private fun LevelStrip(level: Int) {
    val items = listOf(
        1 to stringResource(R.string.ask_level_chat),
        2 to stringResource(R.string.ask_level_audio),
        3 to stringResource(R.string.ask_level_video)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (n, label) ->
            val on = level >= n
            Column(
                Modifier.weight(1f).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.badge_member_level, n), style = MaterialTheme.typography.labelSmall,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Swamiji or the Admin team: chat, audio call, video call and schedule, each locked until the level allows it. */
@Composable
private fun ContactCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    chatOk: Boolean,
    audioOk: Boolean,
    videoOk: Boolean,
    callsReady: Boolean,
    onChat: () -> Unit,
    onAudio: () -> Unit,
    onVideo: () -> Unit,
    onSchedule: () -> Unit
) {
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(com.ojashealing.family.ui.theme.OjasGradients.saffron)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            ContactAction(Icons.Outlined.Chat, stringResource(R.string.ask_chat_short), chatOk, onChat, Modifier.weight(1f))
            if (callsReady) {
                ContactAction(Icons.Outlined.Call, stringResource(R.string.calls_audio_short), audioOk, onAudio, Modifier.weight(1f))
                ContactAction(Icons.Outlined.Videocam, stringResource(R.string.calls_video_short), videoOk, onVideo, Modifier.weight(1f))
                ContactAction(Icons.Outlined.EventNote, stringResource(R.string.calls_schedule_short), audioOk || videoOk, onSchedule, Modifier.weight(1f))
            }
        }
        if (!chatOk || !audioOk || !videoOk) {
            Text(
                stringResource(if (!chatOk) R.string.chat_locked_level else if (!audioOk) R.string.calls_audio_locked else R.string.calls_video_locked),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
