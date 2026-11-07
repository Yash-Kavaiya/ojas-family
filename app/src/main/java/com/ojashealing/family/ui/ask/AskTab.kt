package com.ojashealing.family.ui.ask

import androidx.compose.foundation.clickable
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
    onOpenChat: () -> Unit,
    onCallSwamiji: () -> Unit,
    onPlans: (PaidAccess) -> Unit,
    onInbox: () -> Unit
) {
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    val p = profile ?: return LoadingState()
    val staff = Permissions.isChatStaff(p.viewer)
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item(key = "personal") {
            SectionHeader(stringResource(R.string.ask_personal))
            Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                if (staff) {
                    PersonalCard(Icons.Outlined.Inbox, stringResource(R.string.ask_staff_inbox), stringResource(R.string.ask_staff_inbox_desc), null) {
                        Button(onClick = onInbox) { Text(stringResource(R.string.ask_staff_inbox)) }
                    }
                } else {
                    val chat = entitlements[PaidAccess.Chat]?.takeIf { it.isActive() }
                    PersonalCard(Icons.Outlined.Chat, stringResource(R.string.ask_chat), stringResource(R.string.ask_chat_desc),
                        chat?.let { stringResource(R.string.plan_active, formatDate(it.expiresAt)) }) {
                        // Past chats stay readable after a pack ends
                        if (chat != null) Button(onClick = onOpenChat) { Text(stringResource(R.string.ask_open_chat)) }
                        else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            Button(onClick = { onPlans(PaidAccess.Chat) }) { Text(stringResource(R.string.ask_see_plans)) }
                            OutlinedButton(onClick = onOpenChat) { Text(stringResource(R.string.ask_open_chat)) }
                        }
                    }
                    val call = entitlements[PaidAccess.Call]?.takeIf { it.isActive() }
                    val callLine = call?.let { c ->
                        listOfNotNull(stringResource(R.string.plan_active, formatDate(c.expiresAt)), c.credits?.let { stringResource(R.string.ask_calls_left, it) })
                            .joinToString(" · ")
                    }
                    PersonalCard(Icons.Outlined.Call, stringResource(R.string.ask_call), stringResource(R.string.ask_call_desc), callLine) {
                        if (call?.canCall() == true) Button(onClick = onCallSwamiji) { Text(stringResource(R.string.ask_call_now)) }
                        else Button(onClick = { onPlans(PaidAccess.Call) }) { Text(stringResource(R.string.ask_see_plans)) }
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
