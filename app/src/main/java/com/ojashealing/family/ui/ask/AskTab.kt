package com.ojashealing.family.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** Spec 4: Understanding (4.1), personal chat (4.2) and personal call (4.3). */
@Composable
fun AskTab(
    profile: UserProfile?,
    entitlements: Entitlements,
    understanding: List<Pair<ContentItem, Access>>?,
    onPlay: (ContentItem) -> Unit,
    onOpenChat: (com.ojashealing.family.data.chat.ChatChannel) -> Unit,
    onCall: (com.ojashealing.family.data.chat.ChatChannel, video: Boolean) -> Unit,
    onPlans: (PaidAccess) -> Unit,
    onInbox: () -> Unit,
    onOpenAi: () -> Unit = {},
    onSchedule: (com.ojashealing.family.data.chat.ChatChannel) -> Unit = {},
    onHistory: () -> Unit = {},
    onMyCalls: () -> Unit = {},
    adminCallsReady: Boolean = false
) {
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    val p = profile ?: return LoadingState()
    val staff = Permissions.isChatStaff(p.viewer)
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item(key = "ai") { SwamijiAiCard(onOpenAi, Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l)) }
        item(key = "personal") {
            SectionHeader(stringResource(R.string.ask_personal))
            Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (staff) {
                    PersonalCard(Icons.Outlined.Inbox, stringResource(R.string.ask_staff_inbox), stringResource(R.string.ask_staff_inbox_desc), null) {
                        Button(onClick = onInbox) { Text(stringResource(R.string.ask_staff_inbox)) }
                    }
                } else {
                    val chatOk = Permissions.canChat(p.viewer, entitlements.active(PaidAccess.Chat))
                    val audioOk = Permissions.canAudioCall(p.viewer, entitlements[PaidAccess.Call]?.canCall() == true)
                    val videoOk = Permissions.canVideoCall(p.viewer)
                    LevelStrip(p.viewer.memberLevel.rank)
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
        item(key = "understandingHeader") { SectionHeader(stringResource(R.string.ask_understanding)) }
        when {
            understanding == null -> item(key = "loading") { LoadingState(Modifier.fillMaxWidth().padding(Spacing.xl)) }
            understanding.isEmpty() -> item(key = "empty") {
                Text(stringResource(R.string.empty_ask_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l))
            }
            else -> items(understanding, key = { it.first.id }, contentType = { "video" }) { (item, access) ->
                val open = { if (access is Access.Locked) locked = access else onPlay(item) }
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
    locked?.let { LockedDialog(it) { locked = null } }
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

/** Entry to Ask Swamiji AI: a sunrise card with a "Beta" tag, since the AI is still being built. */
@Composable
private fun SwamijiAiCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(com.ojashealing.family.ui.theme.OjasGradients.sunrise))
            .clickable(role = Role.Button, onClick = onOpen)
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
