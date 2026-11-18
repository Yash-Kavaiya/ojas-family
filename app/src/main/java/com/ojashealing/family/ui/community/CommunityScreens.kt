package com.ojashealing.family.ui.community

import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.QuestionAnswer
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
import com.ojashealing.family.ui.adaptive.LoadOlderWhenEnd
import com.ojashealing.family.ui.adaptive.loadOlderRow
import com.ojashealing.family.ui.adaptive.ownedInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.pluralStringResource
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


/** Community tab order: Activity (Daily) first and shown on open, then Announcements, then Q&A. */
object CommunityTab {
    const val DAILY = 0
    const val NOTICES = 1
    const val QA = 2
}

private fun sectionOrder(tab: Int) = when (tab) { CommunityTab.DAILY -> 0; CommunityTab.NOTICES -> 1; else -> 2 }

@Composable
fun CommunityScreen(
    vm: CommunityViewModel,
    onOpenQuestion: (String) -> Unit,
    onAsk: () -> Unit,
    onAnnounce: () -> Unit,
    daily: @Composable (canPost: Boolean) -> Unit = {},
    onOpenAnnouncement: (String) -> Unit = {},
    tab: Int = CommunityTab.DAILY,
    onTab: (Int) -> Unit = {},
    /** New lines per section, for the red counts on the section tabs. */
    badges: Map<Int, Int> = emptyMap(),
    /** The section now on screen: its new posts count as seen. */
    onSectionShown: (Int) -> Unit = {},
) {
    androidx.compose.runtime.LaunchedEffect(tab) { onSectionShown(tab) }
    val perms by vm.permissions.collectAsStateWithLifecycle()
    val questions by vm.questions.collectAsStateWithLifecycle()
    val notices by vm.announcements.collectAsStateWithLifecycle()
    val questionsMore by vm.questionsHasMore.collectAsStateWithLifecycle()
    val noticesMore by vm.announcementsHasMore.collectAsStateWithLifecycle()
    val p = perms ?: return LoadingState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = tab == CommunityTab.DAILY, onClick = { onTab(CommunityTab.DAILY) }, text = { com.ojashealing.family.ui.components.TabLabel(androidx.compose.material.icons.Icons.Outlined.DynamicFeed, stringResource(R.string.community_daily), badges[CommunityTab.DAILY] ?: 0) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Tab(selected = tab == CommunityTab.NOTICES, onClick = { onTab(CommunityTab.NOTICES) }, text = { com.ojashealing.family.ui.components.TabLabel(androidx.compose.material.icons.Icons.Outlined.Campaign, stringResource(R.string.community_notices), badges[CommunityTab.NOTICES] ?: 0) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Tab(selected = tab == CommunityTab.QA, onClick = { onTab(CommunityTab.QA) }, text = { com.ojashealing.family.ui.components.TabLabel(androidx.compose.material.icons.Icons.Outlined.QuestionAnswer, stringResource(R.string.community_qa), badges[CommunityTab.QA] ?: 0) }, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The order on screen is Activity, Notices, Q&A; the slide follows that order
            androidx.compose.animation.AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = { com.ojashealing.family.ui.theme.sectionSwitch(sectionOrder(targetState) > sectionOrder(initialState)) },
                label = "communitySection",
            ) { shown ->
                if (shown == CommunityTab.DAILY) {
                    Box(Modifier.fillMaxSize()) { daily(p.canPostDaily) }
                } else if (shown == CommunityTab.QA) {
                    val list = questions
                    when {
                        list == null -> LoadingState()
                        list.isEmpty() -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.community_empty_qa), stringResource(R.string.empty_community_body))
                        else -> {
                            val qaState = rememberLazyListState()
                            LoadOlderWhenEnd(qaState, questionsMore, list.size) { vm.loadOlderQuestions() }
                            LazyColumn(state = qaState, contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                                if (!p.canAsk) item(key = "lock", contentType = "lock") { LockHint(stringResource(R.string.community_ask_locked)) }
                                items(list, key = { it.id }, contentType = { "question" }) { q -> Box(Modifier.animateItem()) { QuestionCard(q) { onOpenQuestion(q.id) } } }
                                loadOlderRow(questionsMore) { vm.loadOlderQuestions() }
                            }
                        }
                    }
                } else {
                    val list = notices
                    when {
                        list == null -> LoadingState()
                        list.isEmpty() -> EmptyState(Icons.Outlined.Campaign, stringResource(R.string.community_empty_notices), stringResource(R.string.empty_community_body))
                        else -> {
                            val noticeState = rememberLazyListState()
                            LoadOlderWhenEnd(noticeState, noticesMore, list.size) { vm.loadOlderAnnouncements() }
                            LazyColumn(state = noticeState, contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            items(list, key = { it.id }, contentType = { "notice" }) { n ->
                                val liked by remember(n.id) { vm.announcementLiked(n.id) }.collectAsStateWithLifecycle(false)
                                Box(Modifier.animateItem()) {
                                    AnnouncementCard(n, liked, p.canLike, onLike = { vm.setAnnouncementLiked(n.id, !liked) }, onOpen = { onOpenAnnouncement(n.id) })
                                }
                            }
                            loadOlderRow(noticesMore) { vm.loadOlderAnnouncements() }
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
    OjasCard(onClick = onClick, tintKey = q.id) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(q.author, size = 28)
            AuthorLine(q.author, q.createdAt)
        }
        Text(q.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        MediaLabel(if (q.imageUrl.isNotEmpty()) com.ojashealing.family.data.social.MediaKind.Image else q.mediaKind)
        com.ojashealing.family.ui.components.TrackView("questions/${q.id}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(pluralStringResource(R.plurals.likes_plural, q.likeCount, q.likeCount), stringResource(R.string.community_answers_count, q.answerCount)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            com.ojashealing.family.ui.components.ViewCount(q.viewCount)
        }
        if (q.status == PostStatus.Pending) Text(stringResource(R.string.post_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun QuestionDetailScreen(
    vm: CommunityViewModel,
    questionId: String,
    onBack: () -> Unit,
    showBack: Boolean = true,
    embedded: Boolean = false,
) {
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
      Column(Modifier.fillMaxSize().ownedInsets(embedded)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item(key = "top", contentType = "top") {
                if (showBack) IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.s)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            }
            if (q == null || p == null) {
                item(key = "loading", contentType = "loading") { LoadingState(Modifier.heightIn(min = 200.dp)) }
                return@LazyColumn
            }
            item(key = "question", contentType = "question") {
                OjasCard(modifier = Modifier.padding(horizontal = Spacing.l), tintKey = q.id) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        AuthorAvatar(q.author)
                        Box(Modifier.weight(1f)) { AuthorLine(q.author, q.createdAt) }
                        QuestionMenu(q, vm, onReport = { reportTarget = "questions/${q.id}" }, onDeleted = onBack)
                    }
                    Text(q.title, style = MaterialTheme.typography.titleLarge)
                    if (q.body.isNotBlank()) Text(com.ojashealing.family.ui.components.linkified(q.body), style = MaterialTheme.typography.bodyLarge)
                    com.ojashealing.family.ui.components.LinkPreviewFor(q.body)
                    if (q.imageUrl.isNotEmpty()) PostImage(q.imageUrl)
                    com.ojashealing.family.ui.components.PostMedia(q.mediaKind, q.mediaUrl)
                    com.ojashealing.family.ui.components.TrackView("questions/${q.id}", afterMs = 0)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LikeButton(liked = liked, enabled = p.canLike, onToggle = { vm.setLiked(q.id, !liked) })
                        Text(pluralStringResource(R.plurals.likes_plural, q.likeCount, q.likeCount), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        com.ojashealing.family.ui.components.ViewCount(q.viewCount)
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
                    ReportMenu(a.author, "questions/${q.id}/answers/${a.id}", a.text)
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

/** Report, Edit and Delete, provided by the main shell so every list of members' posts can offer them. */
class PostActions(
    val myUid: String,
    val report: (path: String, reason: String) -> Unit,
    val edit: (path: String, text: String, onDone: (Boolean) -> Unit) -> Unit,
    val delete: (path: String, onDone: (Boolean) -> Unit) -> Unit,
)

val LocalPostActions = androidx.compose.runtime.compositionLocalOf<PostActions?> { null }

/**
 * The three-dot menu on a post, comment or reply. On your own: Edit and Delete. On someone else's:
 * Report. [path] is the post's document path, as firestore.rules expects; [text] is what Edit starts
 * from, and [mayBeEmpty] lets a post with a photo, audio or video lose its words.
 */
@Composable
fun ReportMenu(author: Author, path: String, text: String, mayBeEmpty: Boolean = false, onDeleted: () -> Unit = {}) {
    val actions = LocalPostActions.current ?: return
    var reporting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (author.uid == actions.myUid) {
        ThreeDots(listOf(R.string.action_edit to { editing = true }, R.string.action_delete to { deleting = true }))
    } else {
        ThreeDots(listOf(R.string.community_report to { reporting = true }))
    }
    if (reporting) ReportDialog(onDismiss = { reporting = false }, onSend = { reason -> actions.report(path, reason); reporting = false })
    if (editing) EditPostDialog(
        title = null, text = text, mayBeEmpty = mayBeEmpty, maxText = 1000,
        onDismiss = { editing = false },
        onSave = { _, words, done -> actions.edit(path, words) { ok -> done(ok); if (ok) editing = false } },
    )
    if (deleting) DeletePostDialog(
        onDismiss = { deleting = false },
        onConfirm = { done -> actions.delete(path) { ok -> done(ok); if (ok) { deleting = false; onDeleted() } } },
    )
}

/** The question's own menu: Edit (title and details) and Delete for its author, Report for everyone else. */
@Composable
private fun QuestionMenu(q: Question, vm: CommunityViewModel, onReport: () -> Unit, onDeleted: () -> Unit) {
    val actions = LocalPostActions.current
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    if (actions != null && q.author.uid == actions.myUid) {
        ThreeDots(listOf(R.string.action_edit to { editing = true }, R.string.action_delete to { deleting = true }))
    } else {
        ThreeDots(listOf(R.string.community_report to onReport))
    }
    if (editing) EditPostDialog(
        title = q.title, text = q.body, mayBeEmpty = true, maxText = 2000,
        onDismiss = { editing = false },
        onSave = { title, body, done -> vm.editQuestion(q.id, title.orEmpty(), body) { ok -> done(ok); if (ok) editing = false } },
    )
    if (deleting) DeletePostDialog(
        onDismiss = { deleting = false },
        onConfirm = { done -> vm.deletePost("questions/${q.id}") { ok -> done(ok); if (ok) { deleting = false; onDeleted() } } },
    )
}

@Composable
private fun ThreeDots(items: List<Pair<Int, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { open = false; action() })
            }
        }
    }
}

/**
 * Change the words of your own post. [title] is shown only for a question. [onSave] gets the new
 * title and words and a callback that says whether saving worked; the dialog stays open on failure.
 */
@Composable
private fun EditPostDialog(
    title: String?,
    text: String,
    mayBeEmpty: Boolean,
    maxText: Int,
    onDismiss: () -> Unit,
    onSave: (title: String?, text: String, done: (Boolean) -> Unit) -> Unit,
) {
    var newTitle by rememberSaveable { mutableStateOf(title.orEmpty()) }
    var words by rememberSaveable { mutableStateOf(text) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val canSave = !saving && (title == null || newTitle.isNotBlank()) && (mayBeEmpty || words.isNotBlank())
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.post_edit_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (title != null) OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it.take(150) },
                    label = { Text(stringResource(R.string.community_question_title)) }, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = words, onValueChange = { words = it.take(maxText) },
                    label = if (title != null) ({ Text(stringResource(R.string.community_question_body)) }) else null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                if (failed) Text(stringResource(R.string.post_save_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                saving = true; failed = false
                onSave(title?.let { newTitle }, words) { ok -> saving = false; failed = !ok }
            }, enabled = canSave) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** Asks once before removing your own post for everyone. */
@Composable
private fun DeletePostDialog(onDismiss: () -> Unit, onConfirm: (done: (Boolean) -> Unit) -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.post_delete_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.post_delete_body), style = MaterialTheme.typography.bodyMedium)
                if (failed) Text(stringResource(R.string.post_save_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                deleting = true; failed = false
                onConfirm { ok -> deleting = false; failed = !ok }
            }, enabled = !deleting) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) } }
    )
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
    /** With a photo, audio clip or video (members allowed to attach, [canAttachImage]). */
    onSubmitWithMedia: ((String, String, Uri?, com.ojashealing.family.data.social.MediaKind?, () -> Unit) -> Unit)? = null,
    /** Whether the post is on its way, and whether the last try failed; the text stays so it can be sent again. */
    sending: Boolean = false,
    failed: Boolean = false
) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var mediaUri by rememberSaveable { mutableStateOf<String?>(null) }
    var kindId by rememberSaveable { mutableStateOf<String?>(null) }
    val kind = com.ojashealing.family.data.social.MediaKind.fromId(kindId)
    OnboardingLayout(
        title = title,
        subtitle = null,
        onBack = onBack,
        bottom = {
            PrimaryButton(stringResource(R.string.action_send), onClick = {
                val done = { onBack() }
                if (onSubmitWithMedia != null) onSubmitWithMedia(first, second, mediaUri?.let(Uri::parse), kind, done) else onSubmit(first, second, done)
            }, busy = sending, enabled = first.isNotBlank())
        }
    ) {
        OutlinedTextField(value = first, onValueChange = { first = it.take(150) }, label = { Text(firstLabel) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = second, onValueChange = { second = it.take(2000) }, label = { Text(secondLabel) }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp))
        if (failed) Text(stringResource(R.string.error_generic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (canAttachImage) {
            com.ojashealing.family.ui.components.MediaAttachPicker(mediaUri, kind, onChange = { u, k -> mediaUri = u; kindId = k?.id })
            if (mediaUri != null) Text(stringResource(R.string.post_media_review), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

