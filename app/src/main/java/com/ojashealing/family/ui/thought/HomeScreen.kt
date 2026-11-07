package com.ojashealing.family.ui.thought

import androidx.compose.foundation.background
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

private fun dayLabel(id: String): String = LocalDateLike.parse(id)?.let { "%02d/%02d".format(it.day, it.month) } ?: id

@Composable
fun HomeScreen(vm: ThoughtViewModel, onSchedule: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }

    val state = ui ?: return LoadingState()
    Box(Modifier.fillMaxSize()) {
        if (state.selected == null) {
            Column(Modifier.fillMaxSize()) {
                SectionHeader(stringResource(R.string.notif_thought))
                EmptyState(Icons.Outlined.WbSunny, stringResource(R.string.empty_home_title), stringResource(R.string.empty_home_body), Modifier.weight(1f))
            }
        } else {
            val thought = state.selected
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                item(key = "header", contentType = "header") { SectionHeader(stringResource(R.string.notif_thought)) }
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
                    OjasCard(modifier = Modifier.padding(horizontal = Spacing.l)) {
                        Text(thought.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(thought.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LikeButton(liked = liked, enabled = state.canLike, onToggle = vm::toggleLike)
                            Text(stringResource(R.string.likes_count, thought.likeCount), style = MaterialTheme.typography.labelLarge)
                        }
                        if (!state.canLike) LockHint(stringResource(R.string.home_like_locked))
                    }
                }
                item(key = "commentsTitle", contentType = "title") {
                    Text(
                        stringResource(R.string.home_comments),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = Spacing.l)
                    )
                }
                if (comments.isEmpty()) {
                    item(key = "noComments", contentType = "empty") {
                        Text(
                            stringResource(R.string.home_no_comments),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l)
                        )
                    }
                }
                items(comments, key = { it.id }, contentType = { "comment" }) { PostRow(it) }
                item(key = "composer", contentType = "composer") {
                    Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        if (state.canComment) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it.take(1000) },
                                placeholder = { Text(stringResource(R.string.home_comment_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { vm.comment(draft) { draft = "" } }, enabled = draft.isNotBlank()) {
                                        Text(stringResource(R.string.action_send))
                                    }
                                }
                            )
                        } else {
                            LockHint(stringResource(R.string.home_comment_locked))
                        }
                    }
                }
            }
        }
        if (state.canSchedule) {
            FloatingActionButton(
                onClick = onSchedule,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l)
            ) { Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.home_schedule)) }
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
    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (author.photo.isNotEmpty()) AsyncImage(model = author.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        else Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Swamiji and senior members are marked so their answers stand out
@Composable
fun AuthorLine(author: Author) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(author.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (author.role == Role.Swamiji || author.role == Role.SeniorMember) {
            Text("✦", style = MaterialTheme.typography.labelLarge, color = SacredGold)
        }
    }
}

@Composable
fun PostRow(post: Post, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        AuthorAvatar(post.author)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AuthorLine(post.author)
            Text(post.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (post.status == PostStatus.Pending) {
                Text(stringResource(R.string.post_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        trailing?.invoke()
    }
}
