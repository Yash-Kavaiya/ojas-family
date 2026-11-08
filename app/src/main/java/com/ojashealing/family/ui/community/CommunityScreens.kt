package com.ojashealing.family.ui.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.commentThreads
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.ui.components.LikeButton
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.PostStatus
import com.ojashealing.family.data.social.Question
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.onboarding.OnboardingLayout
import com.ojashealing.family.ui.onboarding.PrimaryButton
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.thought.AuthorAvatar
import com.ojashealing.family.ui.thought.AuthorLine
import com.ojashealing.family.ui.thought.LockHint
import com.ojashealing.family.ui.thought.PostRow
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.OutlinedButton
import com.ojashealing.family.ui.components.PostComposer
import com.ojashealing.family.ui.components.PostImage


/** Community tab order: Announcements first, then Daily, then Q&A. */
object CommunityTab {
    const val NOTICES = 0
    const val DAILY = 1
    const val QA = 2
}

@Composable
fun CommunityScreen(
    vm: CommunityViewModel,
    onOpenQuestion: (String) -> Unit,
    onAsk: () -> Unit,
    onAnnounce: () -> Unit,
    daily: @Composable (canPost: Boolean) -> Unit = {},
    onOpenAnnouncement: (String) -> Unit = {},
    tab: Int = CommunityTab.NOTICES,
    onTab: (Int) -> Unit = {}
) {
    val perms by vm.permissions.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    val notices by vm.announcements.collectAsStateWithLifecycle()
    val p = perms ?: return LoadingState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = tab == CommunityTab.NOTICES, onClick = { onTab(CommunityTab.NOTICES) }, text = { Text(stringResource(R.string.community_notices), maxLines = 1, style = MaterialTheme.typography.labelLarge) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Tab(selected = tab == CommunityTab.DAILY, onClick = { onTab(CommunityTab.DAILY) }, text = { Text(stringResource(R.string.community_daily), maxLines = 1, style = MaterialTheme.typography.labelLarge) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Tab(selected = tab == CommunityTab.QA, onClick = { onTab(CommunityTab.QA) }, text = { Text(stringResource(R.string.community_qa), maxLines = 1, style = MaterialTheme.typography.labelLarge) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tab == CommunityTab.DAILY) {
                Box(Modifier.weight(1f)) { daily(p.canPostDaily) }
            } else if (tab == CommunityTab.QA) {
                val list = questions
                when {
                    list == null -> LoadingState()
                    list.isEmpty() -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.community_empty_qa), stringResource(R.string.empty_community_body))
                    else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        if (!p.canAsk) item(key = "lock", contentType = "lock") { LockHint(stringResource(R.string.community_ask_locked)) }
                        items(list, key = { it.id }, contentType = { "question" }) { q -> Box(Modifier.animateItem()) { QuestionCard(q) { onOpenQuestion(q.id) } } }
                    }
                }
            } else {
                val list = notices
                when {
                    list == null -> LoadingState()
                    list.isEmpty() -> EmptyState(Icons.Outlined.Campaign, stringResource(R.string.community_empty_notices), stringResource(R.string.empty_community_body))
                    else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        items(list, key = { it.id }, contentType = { "notice" }) { n ->
                            val liked by remember(n.id) { vm.announcementLiked(n.id) }.collectAsStateWithLifecycle(false)
                            Box(Modifier.animateItem()) {
                                AnnouncementCard(n, liked, p.canLike, onLike = { vm.setAnnouncementLiked(n.id, !liked) }, onOpen = { onOpenAnnouncement(n.id) })
                            }
                        }
                    }
                }
            }
        }
        val showFab = when (tab) { CommunityTab.QA -> p.canAsk; CommunityTab.NOTICES -> p.canAnnounce; else -> false }
        if (showFab) {
            FloatingActionButton(onClick = if (tab == CommunityTab.QA) onAsk else onAnnounce, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l)) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(if (tab == CommunityTab.QA) R.string.community_ask else R.string.community_post_notice))
            }
        }
    }
}

@Composable
private fun QuestionCard(q: Question, onClick: () -> Unit) {
    OjasCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(q.author, size = 28)
            AuthorLine(q.author)
        }
        Text(q.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(stringResource(R.string.likes_count, q.likeCount), stringResource(R.string.community_answers_count, q.answerCount)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (q.status == PostStatus.Pending) Text(stringResource(R.string.post_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun QuestionDetailScreen(vm: CommunityViewModel, questionId: String, onBack: () -> Unit) {
    val perms by vm.permissions.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    val answers by remember(questionId) { vm.answers(questionId) }.collectAsStateWithLifecycle(emptyList())
    val liked by remember(questionId) { vm.liked(questionId) }.collectAsStateWithLifecycle(false)
    val sending by vm.sending.collectAsStateWithLifecycle()
    var reportTarget by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<Int?>(null) }
    val q = questions?.firstOrNull { it.id == questionId }
    val p = perms
    val openThreads = com.ojashealing.family.ui.components.rememberOpenThreads()
    var replyId by rememberSaveable { mutableStateOf<String?>(null) }
    val replyingTo = answers.firstOrNull { it.id == replyId }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
      // The answer box sits under the list, pinned above the keyboard
      Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item(key = "top", contentType = "top") {
                IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.s)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            }
            if (q == null || p == null) {
                item(key = "loading", contentType = "loading") { LoadingState(Modifier.heightIn(min = 200.dp)) }
                return@LazyColumn
            }
            item(key = "question", contentType = "question") {
                OjasCard(modifier = Modifier.padding(horizontal = Spacing.l)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        AuthorAvatar(q.author)
                        Box(Modifier.weight(1f)) { AuthorLine(q.author) }
                        PostMenu(q.author, onReport = { reportTarget = "questions/${q.id}" }, onBlock = { vm.block(q.author.uid); notice = R.string.community_blocked; onBack() })
                    }
                    Text(q.title, style = MaterialTheme.typography.titleLarge)
                    if (q.body.isNotBlank()) Text(q.body, style = MaterialTheme.typography.bodyLarge)
                    if (q.imageUrl.isNotEmpty()) PostImage(q.imageUrl)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LikeButton(liked = liked, enabled = p.canLike, onToggle = { vm.setLiked(q.id, !liked) })
                        Text(stringResource(R.string.likes_count, q.likeCount), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item(key = "answersTitle", contentType = "title") {
                Text(stringResource(R.string.community_answers), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m))
            }
            if (answers.isEmpty()) item(key = "none", contentType = "empty") {
                Text(stringResource(R.string.community_no_answers), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.l))
            }
            commentThreads(
                answers, openThreads,
                onReply = if (p.canAnswer) ({ a: Post -> replyId = a.id }) else null,
                trailing = { a: Post ->
                    PostMenu(a.author, onReport = { reportTarget = "questions/${q.id}/answers/${a.id}" }, onBlock = { vm.block(a.author.uid); notice = R.string.community_blocked })
                },
                pathOf = { a: Post -> "questions/${q.id}/answers/${a.id}" },
                canLike = p.canLike
            )
        }
        if (q != null && p != null) {
            com.ojashealing.family.ui.thought.CommentBar {
                notice?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (p.canAnswer) {
                    PostComposer(
                        hint = stringResource(R.string.community_answer_hint),
                        canAttachImage = p.canAttachImage,
                        mentionable = listOf(q.author) + answers.map { it.author },
                        busy = sending,
                        onSend = { d, sent -> vm.answer(q.id, d.text, d.photo, d.mentions, d.reply, sent) },
                        replyingTo = replyingTo,
                        onCancelReply = { replyId = null }
                    )
                } else LockHint(stringResource(R.string.community_answer_locked))
            }
        }
      }
    }

    reportTarget?.let { path ->
        ReportDialog(onDismiss = { reportTarget = null }, onSend = { reason ->
            vm.report(path, reason) { notice = R.string.community_reported }
            reportTarget = null
        })
    }
}

@Composable
private fun PostMenu(author: Author, onReport: () -> Unit, onBlock: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.community_report)) }, onClick = { open = false; onReport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.community_block)) }, onClick = { open = false; onBlock() })
        }
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.community_report), style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(value = reason, onValueChange = { reason = it.take(500) }, label = { Text(stringResource(R.string.community_report_reason)) }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSend(reason) }, enabled = reason.isNotBlank()) { Text(stringResource(R.string.action_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Shared composer for a new question (title + details) or, for admins, an announcement. */
@Composable
fun ComposePostScreen(
    title: String,
    firstLabel: String,
    secondLabel: String,
    onBack: () -> Unit,
    onSubmit: (String, String, () -> Unit) -> Unit,
    canAttachImage: Boolean = false,
    onSubmitWithPhoto: ((String, String, Uri?, () -> Unit) -> Unit)? = null
) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var photo by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pickImage = com.ojashealing.family.ui.components.rememberImagePicker { photo = it }
    OnboardingLayout(
        title = title,
        subtitle = null,
        onBack = onBack,
        bottom = {
            PrimaryButton(stringResource(R.string.action_send), onClick = {
                busy = true
                val done = { busy = false; onBack() }
                if (onSubmitWithPhoto != null) onSubmitWithPhoto(first, second, photo, done) else onSubmit(first, second, done)
            }, busy = busy, enabled = first.isNotBlank())
        }
    ) {
        OutlinedTextField(value = first, onValueChange = { first = it.take(150) }, label = { Text(firstLabel) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = second, onValueChange = { second = it.take(2000) }, label = { Text(secondLabel) }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
        if (canAttachImage) {
            photo?.let { PostImage(it.toString()) }
            OutlinedButton(onClick = { pickImage() }) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Text(stringResource(R.string.post_add_photo))
            }
            if (photo != null) Text(stringResource(R.string.post_photo_review), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
