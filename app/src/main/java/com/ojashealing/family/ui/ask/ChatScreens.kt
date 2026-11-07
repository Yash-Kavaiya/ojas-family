package com.ojashealing.family.ui.ask

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
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
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
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
    val thread: ChatThread?,
    val messages: List<ChatMessage>,
    val iAmStaff: Boolean,
    /** A member can write while their chat pack is active; staff always can. */
    val canWrite: Boolean,
    val canAssign: Boolean
)

/** One chat, opened by the member (their own) or by staff (a member's, from the inbox). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repo: ChatRepository,
    profile: Flow<UserProfile?>,
    entitlements: Flow<Entitlements>,
    memberUidOrNull: String?
) : ViewModel() {

    private val me = profile.filterNotNull()

    val ui: StateFlow<ChatUi?> = me.flatMapLatest { p ->
        val memberUid = memberUidOrNull ?: p.uid
        val staff = Permissions.isChatStaff(p.viewer) && memberUid != p.uid
        combine(repo.observeThread(memberUid), repo.observeMessages(memberUid), if (staff) flowOf(Entitlements()) else entitlements) { t, m, e ->
            ChatUi(
                me = p, thread = t, messages = m, iAmStaff = staff,
                canWrite = staff || e.active(PaidAccess.Chat),
                canAssign = staff && (p.viewer.role == Role.Swamiji || p.viewer.role == Role.Admin)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    private val _seniors = MutableStateFlow<List<StaffMember>>(emptyList())
    val seniors: StateFlow<List<StaffMember>> = _seniors

    private val memberUid get() = ui.value?.let { it.thread?.memberUid ?: it.me.uid }

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

private fun time(millis: Long) = if (millis == 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

@Composable
fun ChatScreen(vm: ChatViewModel, onBack: () -> Unit, onRenew: () -> Unit, onCall: ((memberUid: String) -> Unit)?) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val seniors by vm.seniors.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var assigning by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val u = ui
    LaunchedEffect(u?.messages?.size) {
        vm.markRead()
        u?.messages?.size?.takeIf { it > 0 }?.let { list.animateScrollToItem(it - 1) }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                val title = if (u?.iAmStaff == true) u.thread?.memberName.orEmpty() else stringResource(R.string.chat_title)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    u?.thread?.assigneeName?.takeIf { it.isNotEmpty() }?.let {
                        Text(stringResource(R.string.chat_assigned_to, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (u?.canAssign == true) IconButton(onClick = { vm.loadSeniors(); assigning = true }) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = stringResource(R.string.chat_assign))
                }
                if (u?.iAmStaff == true && onCall != null) u.thread?.let { t ->
                    IconButton(onClick = { onCall(t.memberUid) }) { Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.chat_call_member)) }
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
                items(u.messages, key = { it.id }) { m -> Bubble(m, mine = m.senderUid == u.me.uid) }
            }
            Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                if (u.canWrite) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2000) },
                        placeholder = { Text(stringResource(R.string.chat_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { vm.send(draft) { draft = "" } }, enabled = draft.isNotBlank() && !sending) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.action_send))
                            }
                        }
                    )
                } else {
                    LockHint(stringResource(R.string.chat_expired))
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

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
        ) {
            Text(m.text, style = MaterialTheme.typography.bodyLarge,
                color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(time(m.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(repo: ChatRepository, profile: Flow<UserProfile?>) : ViewModel() {
    val threads: StateFlow<List<ChatThread>?> = profile.filterNotNull().flatMapLatest { p -> repo.observeInbox(p.viewer, p.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Staff: every member chat (Swamiji, admins) or the assigned ones (senior members). */
@Composable
fun InboxScreen(vm: InboxViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val threads by vm.threads.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.ask_staff_inbox), style = MaterialTheme.typography.titleLarge)
            }
            val list = threads
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.chat_inbox_empty), "", Modifier.weight(1f))
                else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    items(list, key = { it.memberUid }) { t ->
                        OjasCard(onClick = { onOpen(t.memberUid) }) {
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
