package com.ojashealing.family.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.domain.Access
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.SectionHeader
import com.ojashealing.family.ui.theme.Spacing

private fun thumbnail(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

@Composable
fun VideoTile(item: ContentItem, access: Access, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locked = access is Access.Locked
    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(model = thumbnail(item.videoId), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Dim locked videos so the lock reads before the thumbnail does
            if (locked) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (locked) Icons.Filled.Lock else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Explains what unlocks a video instead of opening it. */
@Composable
fun LockedDialog(locked: Access.Locked, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.locked_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                locked.needsUserLevel?.let { Text(stringResource(R.string.locked_user_level, it), style = MaterialTheme.typography.bodyLarge) }
                locked.needsMemberLevel?.let { Text(stringResource(R.string.locked_member_level, it), style = MaterialTheme.typography.bodyLarge) }
                Text(stringResource(R.string.locked_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } }
    )
}

@Composable
fun PracticeTab(sections: List<SectionUi>?, onPlay: (ContentItem) -> Unit) {
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    when {
        sections == null -> LoadingState()
        sections.isEmpty() -> Column(Modifier.fillMaxSize()) {
            SectionHeader(stringResource(R.string.tab_spiritual))
            EmptyState(
                icon = Icons.Outlined.SelfImprovement,
                title = stringResource(R.string.empty_spiritual_title),
                body = stringResource(R.string.empty_spiritual_body),
                modifier = Modifier.weight(1f)
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            items(sections, key = { it.section.id }, contentType = { "section" }) { section ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SectionHeader(stringResource(section.section.label()))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.l),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        items(section.items, key = { it.first.id }, contentType = { "video" }) { (item, access) ->
                            VideoTile(
                                item = item,
                                access = access,
                                onClick = { if (access is Access.Locked) locked = access else onPlay(item) },
                                modifier = Modifier.width(240.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    locked?.let { LockedDialog(it) { locked = null } }
}

@Composable
fun UnderstandingTab(items: List<Pair<ContentItem, Access>>?, onPlay: (ContentItem) -> Unit) {
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    when {
        items == null -> LoadingState()
        items.isEmpty() -> Column(Modifier.fillMaxSize()) {
            SectionHeader(stringResource(R.string.section_understanding))
            EmptyState(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.empty_ask_title),
                body = stringResource(R.string.empty_ask_body),
                modifier = Modifier.weight(1f)
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xl)) {
            item(key = "header", contentType = "header") { SectionHeader(stringResource(R.string.section_understanding)) }
            items(items, key = { it.first.id }, contentType = { "video" }) { (item, access) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { if (access is Access.Locked) locked = access else onPlay(item) }
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoTile(item = item.copy(title = ""), access = access, onClick = { if (access is Access.Locked) locked = access else onPlay(item) }, modifier = Modifier.width(140.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    locked?.let { LockedDialog(it) { locked = null } }
}
