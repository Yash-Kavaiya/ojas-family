package com.ojashealing.family.ui.notify

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.notify.InboxItem
import com.ojashealing.family.data.notify.NotifGroup
import com.ojashealing.family.data.notify.switchOn
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.tintedCardColor
import com.ojashealing.family.ui.theme.Spacing

fun NotifGroup.label(): Int = when (this) {
    NotifGroup.Critical -> R.string.notif_group_critical
    NotifGroup.Thought -> R.string.notif_group_thought
    NotifGroup.Activity -> R.string.notif_group_activity
    NotifGroup.Announcement -> R.string.notif_group_announcement
    NotifGroup.Qa -> R.string.notif_group_qa
}

fun NotifGroup.icon(): ImageVector = when (this) {
    NotifGroup.Critical -> Icons.Outlined.PriorityHigh
    NotifGroup.Thought -> Icons.Outlined.WbSunny
    NotifGroup.Activity -> Icons.Outlined.PhotoLibrary
    NotifGroup.Announcement -> Icons.Outlined.Campaign
    NotifGroup.Qa -> Icons.Outlined.Forum
}

/** Each group keeps one colour, so a line's group can be told at a glance. */
private fun NotifGroup.color(): Color = when (this) {
    NotifGroup.Critical -> Color(0xFFD32F2F)
    NotifGroup.Thought -> Color(0xFFE08A00)
    NotifGroup.Activity -> Color(0xFF2E7D32)
    NotifGroup.Announcement -> Color(0xFF6A3FC1)
    NotifGroup.Qa -> Color(0xFF0277BD)
}

/**
 * The bell: every notification from the last 30 days, newest first, whatever happened in the phone's tray.
 * Chips filter by group; only groups that are switched on are offered. Tapping a line opens what it is about.
 */
@Composable
fun NotificationsScreen(
    items: List<InboxItem>?,
    prefs: Map<String, Boolean>,
    onOpen: (InboxItem) -> Unit,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = NotifGroup.entries.filter { switchOn(prefs, it) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.notif_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (items.orEmpty().any { it.unread }) TextButton(onClick = onMarkAllRead) { Text(stringResource(R.string.notif_mark_all)) }
            }
            val unread = items.orEmpty().filter { it.unread }.groupingBy { it.group }.eachCount()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.notif_filter_all)) })
                groups.forEach { g ->
                    val n = unread[g] ?: 0
                    FilterChip(
                        selected = filter == g.id,
                        onClick = { filter = if (filter == g.id) null else g.id },
                        leadingIcon = { Icon(g.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text(stringResource(g.label()) + if (n > 0) " · $n" else "") },
                    )
                }
            }
            when {
                items == null -> LoadingState(Modifier.weight(1f))
                else -> {
                    val shown = items.filter { filter == null || it.group.id == filter }
                    if (shown.isEmpty()) EmptyState(Icons.Outlined.NotificationsNone, stringResource(R.string.notif_empty_title), stringResource(R.string.notif_empty_body), Modifier.weight(1f))
                    else LazyColumn(contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xl, top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        items(shown, key = { (if (it.personal) "p_" else "s_") + it.id }, contentType = { "line" }) { item -> InboxRow(item) { onOpen(item) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: InboxItem, onClick: () -> Unit) {
    val res = LocalContext.current.resources
    val (headline, detail) = notificationText(res, item.type, item.title, item.body, item.count) ?: (item.title to item.body)
    val color = item.group.color()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (item.unread) tintedCardColor(item.group.id) else MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(item.group.icon(), contentDescription = stringResource(item.group.label()), tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                headline, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                DateUtils.getRelativeTimeSpanString(item.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.unread) Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}
