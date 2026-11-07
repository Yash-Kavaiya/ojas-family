package com.ojashealing.family.ui.ask

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.call.callDuration
import com.ojashealing.family.data.chat.Attachment
import com.ojashealing.family.data.chat.ChatChannel
import com.ojashealing.family.data.chat.ChatMessage
import com.ojashealing.family.data.chat.ChatRepository
import com.ojashealing.family.data.chat.ChatThread
import com.ojashealing.family.data.chat.StaffMember
import com.ojashealing.family.data.paid.Entitlements
import com.ojashealing.family.data.paid.PaidAccess
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.domain.Role
import com.ojashealing.family.ui.components.AudioPlayer
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.PostImage
import com.ojashealing.family.ui.components.SendButton
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.thought.AuthorAvatar
import com.ojashealing.family.ui.thought.LockHint
import java.text.DateFormat
import java.util.Date
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

data class ChatUi(
    val me: UserProfile,
    val channel: ChatChannel,
    val thread: ChatThread?,
    val messages: List<ChatMessage>,
    val iAmStaff: Boolean,
    /** Member Level 1 or a chat pack lets a member write; staff always can. */
    val canWrite: Boolean,
    val canAssign: Boolean
)

/** One conversation (with Swamiji or the Admin team), opened by the member or by staff from the inbox. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    repository: ChatRepository,
    profile: Flow<UserProfile?>,
    entitlements: Flow<Entitlements>,
    memberUidOrNull: String?,
    val channel: ChatChannel = ChatChannel.Swamiji,
    private val upload: suspend (collection: String, memberUid: String, file: Uri) -> Attachment = { _, _, _ -> error("uploads not available") }
) : ViewModel() {

    private val repo = repository.on(channel)
    private val me = profile.filterNotNull()

    val ui: StateFlow<ChatUi?> = me.flatMapLatest { p ->
        val memberUid = memberUidOrNull ?: p.uid
        val staff = Permissions.isChatStaff(p.viewer) && memberUid != p.uid
        combine(repo.observeThread(memberUid), repo.observeMessages(memberUid), if (staff) flowOf(Entitlements()) else entitlements) { t, m, e ->
            ChatUi(
                me = p, channel = channel, thread = t, messages = m, iAmStaff = staff,
                canWrite = staff || Permissions.canChat(p.viewer, e.active(PaidAccess.Chat)),
                canAssign = staff && channel == ChatChannel.Swamiji && (p.viewer.role == Role.Swamiji || p.viewer.role == Role.Admin)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed
    private val _seniors = MutableStateFlow<List<StaffMember>>(emptyList())
    val seniors: StateFlow<List<StaffMember>> = _seniors

    private val memberUid get() = ui.value?.let { it.thread?.memberUid ?: if (it.iAmStaff) null else it.me.uid }

    fun send(text: String, onDone: () -> Unit) {
        val u = ui.value ?: return
        if (text.isBlank() || _sending.value) return
        val member = memberUid ?: return
        _sending.value = true
        viewModelScope.launch {
            repo.send(member, u.me.uid, text, u.me.displayName, u.me.photoUrl).onSuccess { onDone() }
            _sending.value = false
        }
    }

    /** Uploads a photo, PDF or audio file, then sends it (with any typed text as its caption). */
    fun sendFile(file: Uri, caption: String, onDone: () -> Unit) {
        val u = ui.value ?: return
        if (_sending.value) return
        val member = memberUid ?: return
        _sending.value = true
        _failed.value = false
        viewModelScope.launch {
            runCatching {
                val att = upload(channel.collection, member, file)
                repo.send(member, u.me.uid, caption, u.me.displayName, u.me.photoUrl, att).getOrThrow()
            }.onSuccess { onDone() }.onFailure { _failed.value = true }
            _sending.value = false
        }
    }

    fun markRead() {
        val u = ui.value ?: return
        val t = u.thread ?: return
        if (u.iAmStaff && t.unreadByStaff) viewModelScope.launch { repo.markRead(t.memberUid, byStaff = true) }
        if (!u.iAmStaff && t.unreadByMember) viewModelScope.launch { repo.markRead(t.memberUid, byStaff = false) }
    }

    fun loadSeniors() {
        viewModelScope.launch { repo.seniorMembers().onSuccess { _seniors.value = it } }
    }

    fun assign(staff: StaffMember?) {
        val member = memberUid ?: return
        viewModelScope.launch { repo.assign(member, staff) }
    }
}

internal fun time(millis: Long) = if (millis == 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onRenew: () -> Unit,
    onCall: ((memberUid: String, video: Boolean) -> Unit)?,
    onSchedule: (() -> Unit)? = null
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val seniors by vm.seniors.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var assigning by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val u = ui
    val send: (Uri?) -> Unit = { file -> if (file != null) vm.sendFile(file, draft) { draft = "" } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), send)
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), send)
    LaunchedEffect(u?.messages?.size) {
        vm.markRead()
        u?.messages?.size?.takeIf { it > 0 }?.let { list.animateScrollToItem(it - 1) }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                val title = if (u?.iAmStaff == true) u.thread?.memberName.orEmpty()
                else stringResource(if (vm.channel == ChatChannel.Admin) R.string.chat_title_admin else R.string.chat_title)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    u?.thread?.assigneeName?.takeIf { it.isNotEmpty() && vm.channel == ChatChannel.Swamiji }?.let {
                        Text(stringResource(R.string.chat_assigned_to, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (u?.canAssign == true) IconButton(onClick = { vm.loadSeniors(); assigning = true }) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.chat_assign))
                }
                if (onSchedule != null && u?.iAmStaff == false) IconButton(onClick = onSchedule) {
                    Icon(Icons.Outlined.EventNote, contentDescription = stringResource(R.string.calls_schedule))
                }
                if (onCall != null) {
                    val member = if (u?.iAmStaff == true) u.thread?.memberUid else u?.me?.uid
                    if (member != null) {
                        IconButton(onClick = { onCall(member, false) }) { Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.calls_audio)) }
                        IconButton(onClick = { onCall(member, true) }) { Icon(Icons.Outlined.Videocam, contentDescription = stringResource(R.string.calls_video)) }
                    }
                }
            }
            if (u == null) {
                LoadingState(Modifier.weight(1f))
                return@Column
            }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                if (u.messages.isEmpty()) item(key = "empty") {
                    Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(u.messages, key = { it.id }) { m ->
                    if (m.call != null) CallLogLine(m) else Bubble(m, mine = m.senderUid == u.me.uid)
                }
            }
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                if (failed) Text(stringResource(R.string.chat_file_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (u.canWrite) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { attaching = true }, enabled = !sending) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.chat_attach))
                            }
                            DropdownMenu(expanded = attaching, onDismissRequest = { attaching = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_photo)) },
                                    leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                    onClick = { attaching = false; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_pdf)) },
                                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                    onClick = { attaching = false; docPicker.launch("application/pdf") }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_attach_audio)) },
                                    leadingIcon = { Icon(Icons.Outlined.MusicNote, contentDescription = null) },
                                    onClick = { attaching = false; docPicker.launch("audio/*") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(2000) },
                            placeholder = { Text(stringResource(R.string.chat_hint)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            trailingIcon = {
                                SendButton(
                                    onClick = { vm.send(draft) { draft = "" } },
                                    enabled = draft.isNotBlank(),
                                    busy = sending,
                                    size = 40.dp,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        )
                    }
                } else {
                    LockHint(stringResource(R.string.chat_locked_level))
                    TextButton(onClick = onRenew) { Text(stringResource(R.string.ask_see_plans)) }
                }
            }
        }
    }

    if (assigning) {
        AlertDialog(
            onDismissRequest = { assigning = false },
            title = { Text(stringResource(R.string.chat_assign_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    TextButton(onClick = { vm.assign(null); assigning = false }) { Text(stringResource(R.string.chat_assign_swamiji)) }
                    seniors.forEach { s -> TextButton(onClick = { vm.assign(s); assigning = false }) { Text(s.name) } }
                }
            },
            confirmButton = { TextButton(onClick = { assigning = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

/** "Video call · 12:04" or "Missed call", centred in the conversation. */
@Composable
private fun CallLogLine(m: ChatMessage) {
    val c = m.call ?: return
    val connected = c.seconds > 0
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (!connected) Icons.Outlined.CallMissed else if (c.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
                contentDescription = null,
                tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            val label = stringResource(
                when {
                    !connected && c.status == "declined" -> R.string.calls_declined
                    !connected -> R.string.calls_missed
                    c.video -> R.string.calls_video
                    else -> R.string.calls_audio
                }
            )
            Text(
                listOfNotNull(label, callDuration(c.seconds).takeIf { connected }, time(m.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            m.attachment?.let { a ->
                when {
                    a.isImage -> PostImage(a.url)
                    a.isAudio -> AudioPlayer(a.url)
                    else -> Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                            .padding(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (a.size > 0) Text(fileSize(a.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (m.text.isNotBlank()) {
                Text(m.text, style = MaterialTheme.typography.bodyLarge,
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(time(m.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "820 KB", "3.4 MB". */
fun fileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(repo: ChatRepository, profile: Flow<UserProfile?>) : ViewModel() {
    private val me = profile.filterNotNull()
    val isAdmin: StateFlow<Boolean> = me.flatMapLatest { flowOf(it.viewer.role == Role.Admin) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val threads: StateFlow<List<ChatThread>?> = me.flatMapLatest { p -> repo.observeInbox(p.viewer, p.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** Admin-team conversations (admins only). */
    val adminThreads: StateFlow<List<ChatThread>?> = me.flatMapLatest { p ->
        if (p.viewer.role == Role.Admin) repo.on(ChatChannel.Admin).observeInbox(p.viewer, p.uid) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Staff: member conversations. Admins also get an Admin-team tab. Call requests and history sit on top. */
@Composable
fun InboxScreen(
    vm: InboxViewModel,
    onBack: () -> Unit,
    onOpen: (memberUid: String, channel: ChatChannel) -> Unit,
    onRequests: () -> Unit = {},
    onHistory: () -> Unit = {}
) {
    val threads by vm.threads.collectAsStateWithLifecycle()
    val adminThreads by vm.adminThreads.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.ask_staff_inbox), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedButton(onClick = onRequests, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.calls_requests), modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                }
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.calls_history), modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                }
            }
            if (isAdmin) {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(top = Spacing.s)) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.chat_tab_swamiji)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.chat_tab_admin)) })
                }
            }
            val channel = if (isAdmin && tab == 1) ChatChannel.Admin else ChatChannel.Swamiji
            val list = if (channel == ChatChannel.Admin) adminThreads else threads
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.chat_inbox_empty), "", Modifier.weight(1f))
                else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    items(list, key = { it.memberUid }) { t ->
                        OjasCard(onClick = { onOpen(t.memberUid, channel) }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                AuthorAvatar(Author(t.memberUid, t.memberName, t.memberPhoto, Role.Member))
                                Column(Modifier.weight(1f)) {
                                    Text(t.memberName, style = MaterialTheme.typography.labelLarge)
                                    Text(t.lastMessage, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(time(t.lastAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (t.unreadByStaff) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}
