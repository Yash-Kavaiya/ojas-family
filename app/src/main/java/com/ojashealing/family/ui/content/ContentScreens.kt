package com.ojashealing.family.ui.content

import com.ojashealing.family.ui.theme.staggeredEntrance
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.ojashealing.family.ui.theme.BlueLightTint
import com.ojashealing.family.ui.theme.MorningTint
import com.ojashealing.family.ui.theme.NightTint
import com.ojashealing.family.ui.theme.OtherTint
import com.ojashealing.family.ui.theme.QuickProtectionTint
import com.ojashealing.family.ui.theme.SectionTint
import com.ojashealing.family.ui.theme.UnderstandingTint
import com.ojashealing.family.ui.theme.YogaTint
import com.ojashealing.family.domain.ContentSection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Surface
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

/**
 * The YouTube rows the Practice tab shows. Each of the four guided practices is one meditation:
 * its own media when set; until then just the first video of its section. Other sections are
 * shown in full. The rest of the guided sections' videos stay in the Spiritual library.
 */
fun practiceRows(sections: List<SectionUi>?, withMedia: Set<ContentSection>): List<SectionUi>? =
    sections?.mapNotNull { row ->
        when {
            row.section in withMedia -> null
            row.section in com.ojashealing.family.data.practice.GuidedSections -> row.copy(items = row.items.take(1)).takeIf { it.items.isNotEmpty() }
            else -> row
        }
    }

@Composable
fun PracticeTab(
    sections: List<SectionUi>?,
    onPlay: (ContentItem) -> Unit,
    practice: com.ojashealing.family.data.practice.PracticeStatus? = null,
    practiceBusy: Boolean = false,
    onMarkPractice: (com.ojashealing.family.data.practice.PracticeKind) -> Unit = {},
    guided: List<GuidedCard>? = null,
    streak: com.ojashealing.family.data.practice.StreakSummary? = null,
    onOpenGuided: (ContentSection) -> Unit = {}
) {
    var locked by remember { mutableStateOf<Access.Locked?>(null) }
    val guidedCards = guided.orEmpty()
    val rows = practiceRows(sections, guidedCards.map { it.section }.toSet())
    when {
        sections == null && guided == null -> LoadingState()
        rows.isNullOrEmpty() && guidedCards.isEmpty() -> Column(Modifier.fillMaxSize()) {
            SectionHeader(stringResource(R.string.tab_spiritual))
            EmptyState(
                icon = Icons.Outlined.SelfImprovement,
                title = stringResource(R.string.empty_spiritual_title),
                body = stringResource(R.string.empty_spiritual_body),
                modifier = Modifier.weight(1f)
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item(key = "title", contentType = "title") { SectionHeader(stringResource(R.string.tab_spiritual)) }
            streak?.let { s ->
                item(key = "streak", contentType = "streak") {
                    StreakCard(s, Modifier.padding(horizontal = Spacing.l).staggeredEntrance(0, key = "streak"))
                }
            }
            if (guidedCards.isNotEmpty()) {
                item(key = "guidedHeader", contentType = "guidedHeader") {
                    GuidedHeader(guidedCards, Modifier.padding(horizontal = Spacing.l).staggeredEntrance(1, key = "guidedHeader"))
                }
                itemsIndexed(guidedCards, key = { _, c -> "guided_" + c.section.id }, contentType = { _, _ -> "guided" }) { index, card ->
                    GuidedPracticeCard(
                        card, practice,
                        onOpen = {
                            if (card.locked) locked = Access.Locked(needsUserLevel = com.ojashealing.family.domain.UserLevel.L4.rank, needsMemberLevel = 1)
                            else onOpenGuided(card.section)
                        },
                        modifier = Modifier.padding(horizontal = Spacing.l).staggeredEntrance(2 + index, key = "guided_" + card.section.id).animateItem()
                    )
                }
            }
            items(rows.orEmpty(), key = { it.section.id }, contentType = { "section" }) { section ->
                val tint = section.section.tint()
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                // The whole box carries the section's colour, not just its title
                Surface(
                    color = if (dark) tint.boxDark else tint.boxLight,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l)
                ) {
                Column(modifier = Modifier.padding(vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(
                        stringResource(section.section.label()),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dark) tint.titleDark else tint.titleLight,
                        modifier = Modifier.padding(horizontal = Spacing.l).semantics { heading() }
                    )
                    // Morning and night: the 21-day streak and today's check-in (spec 2.3, 2.4, Goals)
                    val kind = section.section.practiceKind()
                    if (kind != null && practice != null) PracticeBar(kind, practice, practiceBusy, { onMarkPractice(kind) })
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.l),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                    ) {
                        items(section.items, key = { it.first.id }, contentType = { "video" }) { (item, access) ->
                            VideoTile(
                                item = item,
                                access = access,
                                onClick = { if (access is Access.Locked) locked = access else onPlay(item) },
                                modifier = Modifier.width(220.dp)
                            )
                        }
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

internal fun ContentSection.tint(): SectionTint = when (this) {
    ContentSection.BlueLight -> BlueLightTint
    ContentSection.QuickProtection -> QuickProtectionTint
    ContentSection.Morning -> MorningTint
    ContentSection.Night -> NightTint
    ContentSection.Yoga -> YogaTint
    ContentSection.Other -> OtherTint
    ContentSection.Understanding -> UnderstandingTint
}
