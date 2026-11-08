package com.ojashealing.family.ui.community

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.commentThreads
import com.ojashealing.family.data.social.DailyPost
import com.ojashealing.family.data.social.DailyRepository
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.PostStatus
import com.ojashealing.family.data.social.ReplyTo
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LikeButton
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.PostComposer
import com.ojashealing.family.ui.components.PostImage
import com.ojashealing.family.ui.onboarding.ErrorText
import com.ojashealing.family.ui.onboarding.OnboardingLayout
import com.ojashealing.family.ui.onboarding.PrimaryButton
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.thought.AuthorAvatar
import com.ojashealing.family.ui.thought.AuthorLine
import com.ojashealing.family.ui.thought.LockHint
import com.ojashealing.family.ui.thought.PostRow
import com.ojashealing.family.ui.thought.asAuthor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModel(
    private val repo: DailyRepository,
    profile: Flow<UserProfile?>,
    private val blocked: Flow<Set<String>>,
    private val uploadImage: suspend (uid: String, photo: Uri) -> String
) : ViewModel() {

    private val me = profile.filterNotNull()
    private val meNow = profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null = loading; posts only load for viewers who may see them (rules deny everyone else). */
    val posts: StateFlow<List<DailyPost>?> = me.flatMapLatest { p ->
        if (!Permissions.canViewDailyActivities(p.viewer)) flowOf(emptyList())
        else combine(repo.observePosts(p.viewer, p.uid), blocked) { list, b -> list.filter { it.author.uid !in b } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canView: StateFlow<Boolean?> = combine(me, flowOf(Unit)) { p, _ -> Permissions.canViewDailyActivities(p.viewer) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canAttachImage: StateFlow<Boolean> = combine(me, flowOf(Unit)) { p, _ -> Permissions.canAttachImage(p.viewer) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun replies(postId: String): Flow<List<Post>> = me.flatMapLatest { p ->
        combine(repo.observeReplies(postId, p.viewer, p.uid), blocked) { r, b -> r.filter { it.author.uid !in b } }
    }

    fun liked(postId: String): Flow<Boolean> = me.flatMapLatest { repo.observeLiked(postId, it.uid) }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    fun setLiked(postId: String, liked: Boolean) {
        val p = meNow.value ?: return
        viewModelScope.launch { repo.setLiked(postId, p.uid, liked) }
    }

    private fun send(photo: Uri?, block: suspend (UserProfile, String) -> Result<Unit>, onDone: () -> Unit) {
        val p = meNow.value ?: return
        if (_busy.value) return
        _busy.value = true
        _failed.value = false
        viewModelScope.launch {
            val ok = runCatching { photo?.let { uploadImage(p.uid, it) }.orEmpty() }
                .mapCatching { url -> block(p, url).getOrThrow() }.isSuccess
            _busy.value = false
            if (ok) onDone() else _failed.value = true
        }
    }

    fun post(text: String, photo: Uri, onDone: () -> Unit) =
        send(photo, { p, url -> repo.post(p.asAuthor(), text, url, emptyList()) }, onDone)

    fun reply(postId: String, text: String, photo: Uri?, mentions: List<String>, reply: ReplyTo?, onDone: () -> Unit) =
        send(photo, { p, url -> repo.reply(postId, p.asAuthor(), text, url, mentions, reply) }, onDone)
}

/** Third Community tab: paid members' daily seva photos. */
@Composable
fun DailyFeed(vm: DailyViewModel, canPost: Boolean, onOpen: (String) -> Unit, onCompose: () -> Unit, onJoin: () -> Unit) {
    val canView by vm.canView.collectAsStateWithLifecycle()
    val posts by vm.posts.collectAsStateWithLifecycle()
    when {
        canView == null -> LoadingState()
        canView == false -> Column(
            Modifier.fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(Spacing.s))
            Text(stringResource(R.string.daily_locked_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.daily_locked_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onJoin) { Text(stringResource(R.string.membership_join)) }
        }
        posts == null -> LoadingState()
        else -> Box(Modifier.fillMaxSize()) {
            val list = posts.orEmpty()
            if (list.isEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    EmptyState(Icons.Outlined.PhotoLibrary, stringResource(R.string.daily_empty_title), stringResource(R.string.daily_empty_body), Modifier.weight(1f))
                    if (!canPost) Box(Modifier.padding(Spacing.l)) { LockHint(stringResource(R.string.daily_post_locked)) }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    if (!canPost) item(key = "lock", contentType = "lock") { LockHint(stringResource(R.string.daily_post_locked)) }
                    items(list, key = { it.id }, contentType = { "daily" }) { post -> DailyCard(post) { onOpen(post.id) } }
                }
            }
            if (canPost) {
                FloatingActionButton(onClick = onCompose, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l)) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = stringResource(R.string.daily_new))
                }
            }
        }
    }
}

@Composable
private fun DailyCard(post: DailyPost, onClick: () -> Unit) {
    OjasCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(post.author, size = 28)
            AuthorLine(post.author)
        }
        AsyncImage(
            model = post.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        if (post.text.isNotBlank()) Text(post.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Text(
            listOf(stringResource(R.string.likes_count, post.likeCount), stringResource(R.string.replies_count, post.replyCount)).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (post.status == PostStatus.Pending) Text(stringResource(R.string.post_pending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun DailyPostScreen(vm: DailyViewModel, postId: String, onBack: () -> Unit) {
    val posts by vm.posts.collectAsStateWithLifecycle()
    val replies by remember(postId) { vm.replies(postId) }.collectAsStateWithLifecycle(emptyList())
    val liked by remember(postId) { vm.liked(postId) }.collectAsStateWithLifecycle(false)
    val busy by vm.busy.collectAsStateWithLifecycle()
    val canAttach by vm.canAttachImage.collectAsStateWithLifecycle()
    val post = posts?.firstOrNull { it.id == postId }
    val openThreads = com.ojashealing.family.ui.components.rememberOpenThreads()
    var replyId by rememberSaveable { mutableStateOf<String?>(null) }
    val replyingTo = replies.firstOrNull { it.id == replyId }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
      // The reply box sits under the list, pinned above the keyboard
      Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.xs)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (post == null) {
                item(key = "loading") { LoadingState(Modifier.heightIn(min = 200.dp)) }
                return@LazyColumn
            }
            item(key = "post") {
                OjasCard(modifier = Modifier.padding(horizontal = Spacing.l)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        AuthorAvatar(post.author, size = 32)
                        AuthorLine(post.author)
                    }
                    PostImage(post.imageUrl, aspect = 4f / 3f)
                    if (post.text.isNotBlank()) Text(post.text, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LikeButton(liked = liked, enabled = true, onToggle = { vm.setLiked(post.id, !liked) })
                        Text(stringResource(R.string.likes_count, post.likeCount), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            item(key = "title") { Text(stringResource(R.string.daily_replies), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.m)) }
            if (replies.isEmpty()) item(key = "none") {
                Text(stringResource(R.string.daily_no_replies), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l))
            }
            commentThreads(replies, openThreads, onReply = { r: Post -> replyId = r.id })
        }
        if (post != null) {
            com.ojashealing.family.ui.thought.CommentBar {
                PostComposer(
                    hint = stringResource(R.string.daily_reply_hint),
                    canAttachImage = canAttach,
                    mentionable = listOf(post.author) + replies.map { it.author },
                    busy = busy,
                    onSend = { d, sent -> vm.reply(post.id, d.text, d.photo, d.mentions, d.reply, sent) },
                    replyingTo = replyingTo,
                    onCancelReply = { replyId = null }
                )
            }
        }
      }
    }
}

/** Member Level 3: share a photo of today's seva. */
@Composable
fun ComposeDailyScreen(vm: DailyViewModel, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<Uri?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val pickImage = com.ojashealing.family.ui.components.rememberImagePicker { photo = it }
    OnboardingLayout(
        title = stringResource(R.string.daily_new),
        subtitle = null,
        onBack = onBack,
        bottom = {
            PrimaryButton(stringResource(R.string.action_send), onClick = { photo?.let { vm.post(text, it, onBack) } }, busy = busy, enabled = photo != null)
        }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { pickImage() },
            contentAlignment = Alignment.Center
        ) {
            val p = photo
            if (p != null) AsyncImage(model = p, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                Text(stringResource(R.string.daily_photo_required), style = MaterialTheme.typography.labelLarge)
            }
        }
        OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, label = { Text(stringResource(R.string.daily_caption)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
        if (failed) ErrorText(stringResource(R.string.error_generic))
    }
}
