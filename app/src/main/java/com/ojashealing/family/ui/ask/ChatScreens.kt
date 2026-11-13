package com.ojashealing.family.ui.ask

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.ojashealing.family.data.chat.ChatPref
import com.ojashealing.family.data.chat.PresenceState
import com.ojashealing.family.data.chat.StaffContact
import com.ojashealing.family.data.chat.TYPING_PING_MS
import com.ojashealing.family.data.chat.isSeen
import com.ojashealing.family.data.chat.isTyping
import com.ojashealing.family.data.chat.prefKey
import com.ojashealing.family.data.chat.presenceOf
import com.ojashealing.family.data.chat.unreadFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.ojashealing.family.ui.adaptive.DraftScroll
import com.ojashealing.family.ui.adaptive.DraftScrollSaver
import com.ojashealing.family.ui.adaptive.LoadOlderWhenEnd
import com.ojashealing.family.ui.adaptive.loadOlderRow
import com.ojashealing.family.ui.adaptive.ownedInsets
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
import com.ojashealing.family.ui.theme.loopingFloat
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
import com.ojashealing.family.ui.components.LoadingSpinner
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
    val canAssign: Boolean,
    /** When the person on the other side last had the app open; null when unknown. */
    val otherLastActive: Long? = null,
    val pref: ChatPref = ChatPref(),
    val quickReplies: List<String> = emptyList()
) {
    /** The other side's read time, for my ticks. */
    val otherReadAt get() = if (iAmStaff) thread?.memberReadAt ?: 0 else thread?.staffReadAt ?: 0
    val otherTypingAt get() = if (iAmStaff) thread?.typingMember ?: 0 else thread?.typingStaff ?: 0
}

/** One conversation (with Swamiji or the Admin team), opened by the member or by staff from the inbox. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    repository: ChatRepository,
    profile: Flow<UserProfile?>,
    entitlements: Flow<Entitlements>,
    private val memberUidOrNull: String?,
    val channel: ChatChannel = ChatChannel.Swamiji,
    private val upload: suspend (collection: String, memberUid: String, file: Uri) -> Attachment = { _, _, _ -> error("uploads not available") },
    private val presence: (uid: String) -> Flow<Long?> = { flowOf(null) },
    staffContact: Flow<StaffContact?> = flowOf(null)
) : ViewModel() {

    private val repo = repository.on(channel)
    private val me = profile.filterNotNull()

    val ui: StateFlow<ChatUi?> = me.flatMapLatest { p ->
        val memberUid = memberUidOrNull ?: p.uid
        val staff = Permissions.isChatStaff(p.viewer) && memberUid != p.uid
        val base = combine(repo.observeThread(memberUid), repo.observeMessages(memberUid), if (staff) flowOf(Entitlements()) else entitlements) { t, m, e ->
            ChatUi(
                me = p, channel = channel, thread = t, messages = m, iAmStaff = staff,
                canWrite = staff || Permissions.canChat(p.viewer, e.active(PaidAccess.Chat)),
                canAssign = staff && channel == ChatChannel.Swamiji && (p.viewer.role == Role.Swamiji || p.viewer.role == Role.Admin)
            )
        }
        // Whose "online" to show: the member for staff; for a member, whoever answers the Swamiji chat
        val other = if (staff) flowOf(memberUid) else if (channel == ChatChannel.Admin) flowOf(null)
        else combine(repo.observeThread(memberUid), staffContact) { t, c -> t?.assigneeUid?.ifEmpty { null } ?: c?.swamijiUid?.ifEmpty { null } }
        val seen = other.distinctUntilChanged().flatMapLatest { uid -> if (uid == null) flowOf(null) else presence(uid).catch { emit(null) } }
        val prefs = repo.observePrefs(p.uid).catch { emit(emptyMap()) }
        val replies = if (staff) repo.observeQuickReplies(p.uid).catch { emit(emptyList()) } else flowOf(emptyList())
        combine(base, seen, prefs, replies) { u, last, pr, q ->
            u.copy(otherLastActive = last, pref = pr[prefKey(channel, memberUid)] ?: ChatPref(), quickReplies = q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed
    private val _textFailed = MutableStateFlow(false)
    /** A typed message did not go; it is still in the box. */
    val textFailed: StateFlow<Boolean> = _textFailed
    private val _seniors = MutableStateFlow<List<StaffMember>>(emptyList())
    val seniors: StateFlow<List<StaffMember>> = _seniors
    private var lastTypingPing = 0L

    // Known from how the chat was opened, so staff can write first to a member who has no conversation yet
    private val memberUid get() = ui.value?.let { it.thread?.memberUid ?: memberUidOrNull ?: it.me.uid }

    fun send(text: String, onDone: () -> Unit) {
        val u = ui.value ?: return
        if (text.isBlank() || _sending.value) return
        val member = memberUid ?: return
        _sending.value = true
        _failed.value = false
        _textFailed.value = false
        viewModelScope.launch {
            // The text stays in the box when sending fails; say so, as for files and voice notes
            repo.send(member, u.me.uid, text, u.me.displayName, u.me.photoUrl).onSuccess { onDone() }.onFailure { _textFailed.value = true }
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

    /** A recorded voice note: uploaded like an audio file, sent with its length and waveform. */
    fun sendVoice(note: VoiceNote) {
        val u = ui.value ?: return
        val member = memberUid ?: return
        _sending.value = true
        _failed.value = false
        viewModelScope.launch {
            runCatching {
                val att = upload(channel.collection, member, Uri.fromFile(note.file))
                repo.send(member, u.me.uid, "", u.me.displayName, u.me.photoUrl, att.copy(durationMs = note.durationMs, waveform = note.waveform)).getOrThrow()
            }.onFailure { _failed.value = true }
            note.file.delete()
            _sending.value = false
        }
    }

    /** Called as the member or staff types; tells the other side at most every few seconds. */
    fun typing() {
        val u = ui.value ?: return
        val t = u.thread ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingPing < TYPING_PING_MS) return
        lastTypingPing = now
        viewModelScope.launch { repo.setTyping(t.memberUid, byStaff = u.iAmStaff) }
    }

    fun markRead() {
        val u = ui.value ?: return
        val t = u.thread ?: return
        // Also refresh the read time when new messages arrive while the chat is open, so ticks turn blue
        if (unreadFor(t, u.iAmStaff) > 0 || (if (u.iAmStaff) t.unreadByStaff else t.unreadByMember) ||
            u.messages.lastOrNull { it.senderUid != u.me.uid }?.createdAt?.let { it > (if (u.iAmStaff) t.staffReadAt else t.memberReadAt) } == true
        ) viewModelScope.launch { repo.markRead(t.memberUid, byStaff = u.iAmStaff) }
    }

    fun setPref(pref: ChatPref) {
        val u = ui.value ?: return
        val member = memberUid ?: return
        viewModelScope.launch { repo.setPref(u.me.uid, prefKey(channel, member), pref) }
    }

    fun saveQuickReplies(items: List<String>) {
        val u = ui.value ?: return
        viewModelScope.launch { repo.setQuickReplies(u.me.uid, items.map { it.trim() }.filter { it.isNotEmpty() }) }
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

private fun clockTime(millis: Long) = if (millis == 0L) "" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** "Today", "Yesterday" or the date, for the separators between days. */
@Composable
private fun dayHeader(millis: Long): String {
    val day = { m: Long -> java.util.Calendar.getInstance().apply { timeInMillis = m }.let { it.get(java.util.Calendar.YEAR) * 1000 + it.get(java.util.Calendar.DAY_OF_YEAR) } }
    val now = System.currentTimeMillis()
    return when (day(millis)) {
        day(now) -> stringResource(R.string.chat_today)
        day(now - 86_400_000L) -> stringResource(R.string.chat_yesterday)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
    }
}

/** "typing…", "online" or "last seen 5 min ago" under the name. */
@Composable
private fun statusLine(u: ChatUi, now: Long): String? {
    if (isTyping(u.otherTypingAt, now)) return stringResource(R.string.chat_typing)
    return when (val p = presenceOf(u.otherLastActive, now)) {
        PresenceState.Online -> stringResource(R.string.chat_online)
        is PresenceState.LastSeen -> when {
            p.minutesAgo < 60 -> stringResource(R.string.chat_last_seen_min, p.minutesAgo.coerceAtLeast(1))
            else -> stringResource(R.string.chat_last_seen_at, time(u.otherLastActive ?: 0))
        }
        PresenceState.Unknown -> null
    }
}

@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onRenew: () -> Unit,
    onCall: ((memberUid: String, video: Boolean) -> Unit)?,
    onSchedule: (() -> Unit)? = null,
    showBack: Boolean = true,
    embedded: Boolean = false,
    saveKey: String = "chat",
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val failed by vm.failed.collectAsStateWithLifecycle()
    val textFailed by vm.textFailed.collectAsStateWithLifecycle()
    val seniors by vm.seniors.collectAsStateWithLifecycle()
    var held by rememberSaveable(saveKey, stateSaver = DraftScrollSaver) { mutableStateOf(DraftScroll("", 0, 0)) }
    var draft by remember(saveKey) { mutableStateOf(held.draft) }
    var assigning by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editingReplies by remember { mutableStateOf(false) }
    val list = rememberLazyListState(held.index, held.offset)
    LaunchedEffect(saveKey, list) {
        snapshotFlow { DraftScroll(draft, list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) }
            .collect { held = it }
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val u = ui
    val send: (Uri?) -> Unit = { file -> if (file != null) vm.sendFile(file, draft) { draft = "" } }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), send)
    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), send)

    // A clock for "typing…" and "online" to age out
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }

    // Voice notes
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var recElapsed by remember { mutableLongStateOf(0L) }
    var recLevel by remember { mutableFloatStateOf(0f) }
    var recDrag by remember { mutableFloatStateOf(0f) }
    var micAllowed by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { micAllowed = it }
    DisposableEffect(Unit) { onDispose { if (recorder.recording) recorder.cancel() } }
    RecordingTicker(recorder, recording, onTick = { e, l -> recElapsed = e; recLevel = l }) {
        recording = false
        recorder.stop()?.let(vm::sendVoice)
    }

    // The list is a flat run of day headers and messages
    val rows = remember(u?.messages) {
        buildList {
            var lastDay = -1
            u?.messages?.forEach { m ->
                val c = java.util.Calendar.getInstance().apply { timeInMillis = m.createdAt }
                val d = c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
                if (m.createdAt > 0 && d != lastDay) { add(ChatRow.Day(m.createdAt)); lastDay = d }
                add(ChatRow.Message(m))
            }
        }
    }
    val lastMineSeenId = u?.let { s -> s.messages.lastOrNull { it.senderUid == s.me.uid && it.call == null }?.takeIf { isSeen(it.createdAt, s.otherReadAt) }?.id }
    val atBottom by remember { derivedStateOf { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= list.layoutInfo.totalItemsCount - 2 } ?: true } }
    var unseenBelow by remember { mutableIntStateOf(0) }

    LaunchedEffect(u?.messages?.size) {
        vm.markRead()
        val size = rows.size
        if (size == 0) return@LaunchedEffect
        val last = u?.messages?.lastOrNull()
        // Follow new messages when already at the bottom or when I sent it; otherwise count them
        if (atBottom || last?.senderUid == u?.me?.uid) { list.animateScrollToItem(size - 1); unseenBelow = 0 } else unseenBelow++
    }
    LaunchedEffect(atBottom) { if (atBottom) unseenBelow = 0 }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().ownedInsets(embedded)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
                if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                val title = if (u?.iAmStaff == true) u.thread?.memberName.orEmpty()
                else stringResource(if (vm.channel == ChatChannel.Admin) R.string.chat_title_admin else R.string.chat_title)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (u?.pref?.muted == true) Icon(Icons.Outlined.NotificationsOff, contentDescription = stringResource(R.string.chat_muted), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val status = u?.let { statusLine(it, now) }
                    val assigned = u?.thread?.assigneeName?.takeIf { it.isNotEmpty() && vm.channel == ChatChannel.Swamiji }
                    when {
                        status != null -> Text(
                            status, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            color = if (status == stringResource(R.string.chat_typing) || status == stringResource(R.string.chat_online)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        assigned != null -> Text(stringResource(R.string.chat_assigned_to, assigned), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (onCall != null) {
                    val member = if (u?.iAmStaff == true) u.thread?.memberUid else u?.me?.uid
                    if (member != null) {
                        IconButton(onClick = { onCall(member, false) }) { Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.calls_audio)) }
                        IconButton(onClick = { onCall(member, true) }) { Icon(Icons.Outlined.Videocam, contentDescription = stringResource(R.string.calls_video)) }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val pref = u?.pref ?: ChatPref()
                        DropdownMenuItem(
                            text = { Text(stringResource(if (pref.pinned) R.string.chat_unpin else R.string.chat_pin)) },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                            onClick = { menu = false; vm.setPref(pref.copy(pinned = !pref.pinned)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (pref.muted) R.string.chat_unmute else R.string.chat_mute)) },
                            leadingIcon = { Icon(if (pref.muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, contentDescription = null) },
                            onClick = { menu = false; vm.setPref(pref.copy(muted = !pref.muted)) }
                        )
                        if (u?.canAssign == true) DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_assign)) },
                            leadingIcon = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                            onClick = { menu = false; vm.loadSeniors(); assigning = true }
                        )
                        if (u?.iAmStaff == true) DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_quick_replies)) },
                            leadingIcon = { Icon(Icons.Outlined.Bolt, contentDescription = null) },
                            onClick = { menu = false; editingReplies = true }
                        )
                        if (onSchedule != null && u?.iAmStaff == false) DropdownMenuItem(
                            text = { Text(stringResource(R.string.calls_schedule)) },
                            leadingIcon = { Icon(Icons.Outlined.EventNote, contentDescription = null) },
                            onClick = { menu = false; onSchedule() }
                        )
                    }
                }
            }
            if (u == null) {
                LoadingSpinner(Modifier.weight(1f))
                return@Column
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (u.messages.isEmpty()) item(key = "empty") {
                        Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(rows, key = { it.key }, contentType = { row -> if (row is ChatRow.Day) "day" else "message" }) { row ->
                        when (row) {
                            is ChatRow.Day -> DayChip(dayHeader(row.at))
                            is ChatRow.Message -> {
                                val m = row.m
                                if (m.call != null) CallLogLine(m)
                                else {
                                    val mine = m.senderUid == u.me.uid
                                    Bubble(m, mine, seen = mine && isSeen(m.createdAt, u.otherReadAt))
                                    if (m.id == lastMineSeenId) {
                                        Text(
                                            stringResource(R.string.chat_seen), style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.fillMaxWidth().padding(end = Spacing.xs), textAlign = TextAlign.End
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isTyping(u.otherTypingAt, now)) item(key = "typing") { TypingBubble() }
                }
                // Back to the newest message, with how many arrived while scrolled up
                if (!atBottom && rows.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { list.animateScrollToItem(rows.size - 1) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m)
                    ) {
                        BadgedBox(badge = { if (unseenBelow > 0) Badge { Text("$unseenBelow") } }) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.chat_scroll_down))
                        }
                    }
                }
            }
            Column(
                Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = Spacing.s, vertical = Spacing.xs)
            ) {
                if (failed) Text(stringResource(R.string.chat_file_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.s))
                if (textFailed) Text(stringResource(R.string.chat_send_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = Spacing.s))
                if (u.canWrite) {
                    // Staff quick replies: one tap fills the box
                    if (u.iAmStaff && u.quickReplies.isNotEmpty() && !recording) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs)) {
                            items(u.quickReplies, key = { it }, contentType = { "reply" }) { q ->
                                SuggestionChip(onClick = { draft = q }, label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) })
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (recording) {
                            RecordingBar(recElapsed, recLevel, recDrag, Modifier.weight(1f))
                        } else {
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
                                onValueChange = { draft = it.take(2000); if (it.isNotBlank()) vm.typing() },
                                placeholder = { Text(stringResource(R.string.chat_hint), style = MaterialTheme.typography.bodyMedium) },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 5
                            )
                        }
                        // Send when there is text; otherwise hold the mic to record a voice note
                        if (draft.isNotBlank() && !recording) {
                            SendButton(onClick = { vm.send(draft) { draft = "" } }, enabled = true, busy = sending, size = 48.dp)
                        } else {
                            HoldToRecordButton(
                                canRecord = {
                                    if (!micAllowed) micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                    micAllowed && !sending
                                },
                                onStart = { recorder.start().also { if (it) { recording = true; recDrag = 0f; recElapsed = 0 } } },
                                onDrag = { recDrag = it },
                                onRelease = { cancelled ->
                                    recording = false
                                    if (cancelled) recorder.cancel()
                                    else recorder.stop()?.let(vm::sendVoice)
                                        ?: Toast.makeText(context, R.string.voice_too_short, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
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
    if (editingReplies && u != null) QuickRepliesDialog(u.quickReplies, onSave = { vm.saveQuickReplies(it); editingReplies = false }, onDismiss = { editingReplies = false })
}

private sealed interface ChatRow {
    val key: String
    data class Day(val at: Long) : ChatRow { override val key get() = "day-$at" }
    data class Message(val m: ChatMessage) : ChatRow { override val key get() = m.id }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.s), contentAlignment = Alignment.Center) {
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** Three dots that rise in turn while the other side types. */
@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    val phase by loopingFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), rest = 1f, label = "p")
    Row(
        Modifier.padding(vertical = 2.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { i ->
            val lift = if (phase.toInt() == i) 1f else 0.4f
            Box(Modifier.size(7.dp).alpha(lift).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
        }
    }
}

/** Staff: up to 20 saved replies, one per line. */
@Composable
private fun QuickRepliesDialog(current: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_quick_replies), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.chat_quick_replies_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = text, onValueChange = { text = it.take(4000) }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.lines().take(20)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** "Video call · 12:04" or "Missed call", centred in the conversation. */
@Composable
private fun CallLogLine(m: ChatMessage) {
    val c = m.call ?: return
    val connected = c.seconds > 0
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
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
                listOfNotNull(label, callDuration(c.seconds).takeIf { connected }, clockTime(m.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, mine: Boolean, seen: Boolean) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier.widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (mine) 16.dp else 4.dp, bottomEnd = if (mine) 4.dp else 16.dp)
                )
                .padding(horizontal = Spacing.m, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            m.attachment?.let { a ->
                when {
                    a.isImage -> PostImage(a.url, maxHeight = 320.dp)
                    a.isVoice -> VoiceBubble(a.url, a.durationMs, a.waveform, mine)
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
                Text(m.text, style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Time and, on my messages, the ticks: a clock while sending, ✓ sent, blue ✓✓ seen
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(clockTime(m.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (mine) {
                    val (icon, tint, label) = when {
                        m.pending -> Triple(Icons.Outlined.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, R.string.chat_sending)
                        seen -> Triple(Icons.Outlined.DoneAll, Color(0xFF1D9BF0), R.string.chat_seen)
                        else -> Triple(Icons.Outlined.Done, MaterialTheme.colorScheme.onSurfaceVariant, R.string.chat_sent)
                    }
                    Icon(icon, contentDescription = stringResource(label), tint = tint, modifier = Modifier.size(14.dp))
                }
            }
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
class InboxViewModel(private val repo: ChatRepository, profile: Flow<UserProfile?>) : ViewModel() {
    private val me = profile.filterNotNull()
    /** My pinned and muted conversations. */
    val prefs: StateFlow<Map<String, ChatPref>> = me.flatMapLatest { p -> repo.observePrefs(p.uid).catch { emit(emptyMap()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setPref(channel: ChatChannel, memberUid: String, pref: ChatPref) {
        viewModelScope.launch { me.first().let { p -> repo.setPref(p.uid, prefKey(channel, memberUid), pref) } }
    }
    val isAdmin: StateFlow<Boolean> = me.flatMapLatest { flowOf(it.viewer.role == Role.Admin) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** Swamiji and admins may look up any member to start a chat or call; senior members only answer. */
    val canFind: StateFlow<Boolean> = me.flatMapLatest { flowOf(it.viewer.role == Role.Admin || it.viewer.role == Role.Swamiji) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val threads: StateFlow<List<ChatThread>?> = me.flatMapLatest { p -> repo.observeInbox(p.viewer, p.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val threadsHasMore: StateFlow<Boolean> = repo.inboxHasMore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun loadOlderThreads() { viewModelScope.launch { repo.loadOlderInbox() } }
    /** Admin-team conversations (admins only). */
    private val adminRepo = repo.on(ChatChannel.Admin)
    val adminThreads: StateFlow<List<ChatThread>?> = me.flatMapLatest { p ->
        if (p.viewer.role == Role.Admin) adminRepo.observeInbox(p.viewer, p.uid) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val adminHasMore: StateFlow<Boolean> = adminRepo.inboxHasMore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun loadOlderAdmin() { viewModelScope.launch { adminRepo.loadOlderInbox() } }
}

/** Staff: member conversations. Admins also get an Admin-team tab. Call requests and history sit on top. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InboxScreen(
    vm: InboxViewModel,
    onBack: () -> Unit,
    onOpen: (memberUid: String, channel: ChatChannel) -> Unit,
    onRequests: () -> Unit = {},
    onHistory: () -> Unit = {},
    onFind: () -> Unit = {},
    showBack: Boolean = true,
    embedded: Boolean = false,
) {
    val canFind by vm.canFind.collectAsStateWithLifecycle()
    val threads by vm.threads.collectAsStateWithLifecycle()
    val adminThreads by vm.adminThreads.collectAsStateWithLifecycle()
    val isAdmin by vm.isAdmin.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().ownedInsets(embedded)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.ask_staff_inbox), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            if (canFind) {
                Button(onClick = onFind, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs)) {
                    Icon(Icons.Outlined.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.find_member_action), modifier = Modifier.padding(start = Spacing.xs), maxLines = 1)
                }
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
            val more by (if (channel == ChatChannel.Admin) vm.adminHasMore else vm.threadsHasMore).collectAsStateWithLifecycle()
            val loadOlder = if (channel == ChatChannel.Admin) vm::loadOlderAdmin else vm::loadOlderThreads
            // Pinned first, then newest
            val list = (if (channel == ChatChannel.Admin) adminThreads else threads)
                ?.sortedWith(compareByDescending<ChatThread> { prefs[prefKey(channel, it.memberUid)]?.pinned == true }.thenByDescending { it.lastAt })
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(Icons.Outlined.Forum, stringResource(R.string.chat_inbox_empty), "", Modifier.weight(1f))
                else -> {
                    val inboxState = rememberLazyListState()
                    LoadOlderWhenEnd(inboxState, more, list.size, loadOlder)
                    LazyColumn(state = inboxState, contentPadding = PaddingValues(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(list, key = { it.memberUid }, contentType = { "thread" }) { t ->
                        val pref = prefs[prefKey(channel, t.memberUid)] ?: ChatPref()
                        val unread = unreadFor(t, iAmStaff = true)
                        Box {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                                    .combinedClickable(onClick = { onOpen(t.memberUid, channel) }, onLongClick = { menuFor = t.memberUid })
                            ) {
                                Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                    AuthorAvatar(Author(t.memberUid, t.memberName, t.memberPhoto, Role.Member), 44)
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(t.memberName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            if (pref.pinned) Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.chat_pinned), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (pref.muted) Icon(Icons.Outlined.NotificationsOff, contentDescription = stringResource(R.string.chat_muted), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            t.lastMessage, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(time(t.lastAt), style = MaterialTheme.typography.labelSmall,
                                            color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (unread > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(if (unread > 99) "99+" else "$unread") }
                                    }
                                }
                            }
                            DropdownMenu(expanded = menuFor == t.memberUid, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (pref.pinned) R.string.chat_unpin else R.string.chat_pin)) },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = { menuFor = null; vm.setPref(channel, t.memberUid, pref.copy(pinned = !pref.pinned)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (pref.muted) R.string.chat_unmute else R.string.chat_mute)) },
                                    leadingIcon = { Icon(Icons.Outlined.NotificationsOff, contentDescription = null) },
                                    onClick = { menuFor = null; vm.setPref(channel, t.memberUid, pref.copy(muted = !pref.muted)) }
                                )
                            }
                        }
                    }
                    loadOlderRow(more, loadOlder)
                    }
                }
            }
        }
    }
}
