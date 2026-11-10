package com.ojashealing.family.ui.ask

import com.ojashealing.family.ui.components.appLocale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ojashealing.family.ui.adaptive.LoadOlderWhenEnd
import com.ojashealing.family.ui.adaptive.loadOlderRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.call.Call
import com.ojashealing.family.data.call.CallRepository
import com.ojashealing.family.data.call.CallRequest
import com.ojashealing.family.data.call.CallRequestRepository
import com.ojashealing.family.data.call.IST
import com.ojashealing.family.data.call.RequestStatus
import com.ojashealing.family.data.call.callDuration
import com.ojashealing.family.data.call.callOutcome
import com.ojashealing.family.data.call.canJoinNow
import com.ojashealing.family.data.call.daySlots
import com.ojashealing.family.data.call.pickedDateAt
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.domain.Role
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.SendPillButton
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.thought.AuthorAvatar
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Fri, 7 Nov · 4:30 pm" in India time, which is how every call time is shown. */
fun istTime(millis: Long): String {
    if (millis <= 0) return ""
    val f = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault()).apply { timeZone = IST }
    return f.format(Date(millis))
}

/** Call history, call requests and booking, for the member and for staff. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallsViewModel(
    private val requests: CallRequestRepository,
    private val calls: CallRepository,
    profile: Flow<UserProfile?>
) : ViewModel() {
    private val me = profile.filterNotNull()
    val profile: StateFlow<UserProfile?> = me.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<Call>?> = me.flatMapLatest { p ->
        val staff = Permissions.isChatStaff(p.viewer)
        calls.observeHistory(p.uid, isMember = !staff, isAdmin = p.viewer.role == Role.Admin)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val historyHasMore: StateFlow<Boolean> = calls.historyHasMore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun loadOlderHistory() { viewModelScope.launch { calls.loadOlderHistory() } }

    val mine: StateFlow<List<CallRequest>?> = me.flatMapLatest { requests.observeMine(it.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Staff: Swamiji and admins see Swamiji-channel requests; admins also the Admin-team ones. */
    val forStaff: StateFlow<List<CallRequest>?> = me.flatMapLatest { p ->
        val r = p.viewer.role
        if (r == Role.Swamiji || r == Role.Admin) requests.observeForStaff(swamijiSide = true, adminSide = r == Role.Admin) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun observe(id: String) = requests.observe(id)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun book(channel: String, video: Boolean, at: Long, note: String, onDone: () -> Unit) {
        val p = profile.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            requests.request(p.uid, p.displayName, p.photoUrl, channel, video, at, note).onSuccess { onDone() }
            _busy.value = false
        }
    }

    fun answer(r: CallRequest, status: RequestStatus, proposedAt: Long? = null) {
        val p = profile.value ?: return
        viewModelScope.launch { requests.answer(r.id, status, p.uid, p.displayName, proposedAt) }
    }

    fun cancel(r: CallRequest) = viewModelScope.launch { requests.cancel(r.id) }
    fun accept(r: CallRequest) = viewModelScope.launch { requests.acceptProposal(r) }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------- Call history ----------------

@Composable
fun CallHistoryScreen(vm: CallsViewModel, onBack: () -> Unit, onCallBack: (Call) -> Unit) {
    val list by vm.history.collectAsStateWithLifecycle()
    val more by vm.historyHasMore.collectAsStateWithLifecycle()
    val me by vm.profile.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Header(stringResource(R.string.calls_history), onBack)
            val calls = list
            when {
                calls == null -> LoadingState()
                calls.isEmpty() -> EmptyState(Icons.Outlined.History, stringResource(R.string.calls_history_empty), stringResource(R.string.calls_history_empty_body), Modifier.weight(1f))
                else -> {
                    val historyState = rememberLazyListState()
                    LoadOlderWhenEnd(historyState, more, calls.size) { vm.loadOlderHistory() }
                    LazyColumn(state = historyState, contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        items(calls, key = { it.id }, contentType = { "call" }) { c -> HistoryRow(c, me?.uid.orEmpty(), onCallBack) }
                        loadOlderRow(more) { vm.loadOlderHistory() }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(c: Call, myUid: String, onCallBack: (Call) -> Unit) {
    val outgoing = c.callerUid == myUid
    val outcome = callOutcome(c)
    val other = (if (outgoing) c.calleeName else c.callerName).ifEmpty { stringResource(if (c.channel == "admin") R.string.chat_tab_admin else R.string.role_swamiji) }
    val missed = outcome == "missed" || outcome == "declined"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape)
                .background((if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (missed) Icons.Outlined.CallMissed else if (c.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
                contentDescription = null,
                tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(other, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (outgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived, contentDescription = null,
                    modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                val what = stringResource(
                    when (outcome) {
                        "missed" -> R.string.calls_missed
                        "declined" -> R.string.calls_declined
                        else -> if (c.video) R.string.calls_video else R.string.calls_audio
                    }
                )
                Text(
                    listOfNotNull(what, callDuration(c.seconds).takeIf { c.seconds > 0 }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(time(c.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onCallBack(c) }) {
            Icon(if (c.video) Icons.Outlined.Videocam else Icons.Outlined.Call, contentDescription = stringResource(R.string.calls_call_back), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------- Booking a call ----------------

/** Pick a day on the calendar, a time, audio or video, then send the request. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleCallScreen(
    vm: CallsViewModel,
    channel: String,
    canAudio: Boolean,
    canVideo: Boolean,
    onBack: () -> Unit,
    onMyCalls: () -> Unit
) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }
    val dates = rememberDatePickerState(
        initialSelectedDateMillis = null,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis >= now - 86_400_000L && utcTimeMillis <= now + 60L * 86_400_000L
        }
    )
    var slot by rememberSaveable { mutableIntStateOf(-1) }
    var video by rememberSaveable { mutableStateOf(!canAudio && canVideo) }
    var note by rememberSaveable { mutableStateOf("") }
    val day = dates.selectedDateMillis
    val slots = daySlots()
    val at = if (day != null && slot >= 0) pickedDateAt(day, slots[slot].first, slots[slot].second) else 0L

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Header(stringResource(if (channel == "admin") R.string.calls_schedule_admin else R.string.calls_schedule_swamiji), onBack)
                }
                TextButton(onClick = onMyCalls) { Text(stringResource(R.string.calls_my_calls)) }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                OjasCard {
                    DatePicker(state = dates, title = null, headline = null, showModeToggle = false)
                }
                if (day != null) {
                    Text(stringResource(R.string.calls_pick_time), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        slots.forEachIndexed { i, (h, m) ->
                            val t = pickedDateAt(day, h, m)
                            FilterChip(
                                selected = slot == i,
                                onClick = { slot = i },
                                enabled = t > System.currentTimeMillis() + 30 * 60_000,
                                label = { Text(SimpleDateFormat("h:mm a", appLocale()).apply { timeZone = IST }.format(Date(t))) }
                            )
                        }
                    }
                    Text(stringResource(R.string.calls_ist_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.calls_kind), style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !video, onClick = { video = false }, enabled = canAudio, shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Outlined.Call, contentDescription = null) }, label = { Text(stringResource(R.string.calls_audio)) })
                    SegmentedButton(selected = video, onClick = { video = true }, enabled = canVideo, shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Outlined.Videocam, contentDescription = null) }, label = { Text(stringResource(R.string.calls_video)) })
                }
                if (!canVideo) Text(stringResource(R.string.calls_video_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(500) },
                    label = { Text(stringResource(R.string.calls_note)) },
                    minLines = 2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                )
                if (at > 0) Text(stringResource(R.string.calls_summary, istTime(at)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            SendPillButton(
                text = stringResource(R.string.calls_send_request),
                onClick = { vm.book(channel, video, at, note) { onMyCalls() } },
                enabled = at > 0 && (if (video) canVideo else canAudio),
                busy = busy,
                modifier = Modifier.padding(Spacing.l)
            )
        }
    }
}

// ---------------- Month calendar of requests ----------------

private fun dayKey(millis: Long): Int {
    val c = Calendar.getInstance(IST).apply { timeInMillis = millis }
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

/** A month grid; days with calls get a dot, tapping a day selects it. */
@Composable
private fun MonthCalendar(marked: Set<Int>, selected: Int, onSelect: (Int) -> Unit) {
    var month by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val cal = Calendar.getInstance(IST).apply { timeInMillis = month; set(Calendar.DAY_OF_MONTH, 1) }
    val title = SimpleDateFormat("MMMM yyyy", appLocale()).apply { timeZone = IST }.format(cal.time)
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday first
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = dayKey(System.currentTimeMillis())
    OjasCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = Calendar.getInstance(IST).apply { timeInMillis = month; add(Calendar.MONTH, -1) }.timeInMillis }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calls_prev_month))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { month = Calendar.getInstance(IST).apply { timeInMillis = month; add(Calendar.MONTH, 1) }.timeInMillis }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calls_next_month))
            }
        }
        val weekdays = remember {
            val f = SimpleDateFormat("EEEEE", Locale.getDefault())
            (0..6).map { i -> f.format(Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); add(Calendar.DAY_OF_MONTH, i) }.time) }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { Text(it, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
        }
        val cells = firstDow + days
        (0 until (cells + 6) / 7).forEach { w ->
            Row(Modifier.fillMaxWidth()) {
                (0..6).forEach { d ->
                    val n = w * 7 + d - firstDow + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..days) {
                            val key = cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + n
                            val isSel = key == selected
                            Column(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .border(if (key == today && !isSel) 1.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { onSelect(key) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("$n", style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                if (key in marked) Box(Modifier.size(5.dp).clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Requests: the member's "My calls" and the staff list ----------------

@Composable
private fun statusLabel(s: RequestStatus): Pair<String, Color> = when (s) {
    RequestStatus.Pending -> stringResource(R.string.calls_status_pending) to MaterialTheme.colorScheme.tertiary
    RequestStatus.Proposed -> stringResource(R.string.calls_status_proposed) to Color(0xFFB45309)
    RequestStatus.Confirmed -> stringResource(R.string.calls_status_confirmed) to Color(0xFF15803D)
    RequestStatus.Declined -> stringResource(R.string.calls_status_declined) to MaterialTheme.colorScheme.error
    RequestStatus.Cancelled -> stringResource(R.string.calls_status_cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
    RequestStatus.Done -> stringResource(R.string.calls_status_done) to MaterialTheme.colorScheme.primary
}

@Composable
private fun RequestCard(r: CallRequest, staffView: Boolean, onOpen: () -> Unit) {
    val (label, color) = statusLabel(r.status)
    OjasCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (staffView) AuthorAvatar(Author(r.memberUid, r.memberName, r.memberPhoto, Role.Member))
            else Icon(if (r.video) Icons.Outlined.Videocam else Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    if (staffView) r.memberName else stringResource(if (r.channel == "admin") R.string.chat_tab_admin else R.string.role_swamiji),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(istTime(if (r.status == RequestStatus.Proposed) r.proposedAt else r.at), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (r.video) R.string.calls_video else R.string.calls_audio), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = color,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** The member's booked calls on a month calendar, with the list for the chosen day. */
@Composable
fun MyCallsScreen(vm: CallsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, staffView: Boolean = false) {
    val mine by vm.mine.collectAsStateWithLifecycle()
    val staff by vm.forStaff.collectAsStateWithLifecycle()
    val list = if (staffView) staff else mine
    var selected by rememberSaveable { mutableIntStateOf(dayKey(System.currentTimeMillis())) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Header(stringResource(if (staffView) R.string.calls_requests else R.string.calls_my_calls), onBack)
            val all = list
            if (all == null) {
                LoadingState()
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                item(key = "cal") {
                    MonthCalendar(all.filter { it.status.isOpen }.map { dayKey(if (it.status == RequestStatus.Proposed) it.proposedAt else it.at) }.toSet(), selected) { selected = it }
                }
                val onDay = all.filter { dayKey(if (it.status == RequestStatus.Proposed) it.proposedAt else it.at) == selected }
                val upcoming = all.filter { it.status.isOpen && it.at >= System.currentTimeMillis() - 3_600_000 }.sortedBy { it.at }
                item(key = "dayTitle") { Text(stringResource(R.string.calls_on_day), style = MaterialTheme.typography.titleMedium) }
                if (onDay.isEmpty()) item(key = "none") {
                    Text(stringResource(R.string.calls_none_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(onDay, key = { "d" + it.id }, contentType = { "request" }) { r -> RequestCard(r, staffView) { onOpen(r.id) } }
                if (upcoming.isNotEmpty()) {
                    item(key = "upTitle") { Text(stringResource(R.string.calls_upcoming), style = MaterialTheme.typography.titleMedium) }
                    items(upcoming, key = { "u" + it.id }, contentType = { "request" }) { r -> RequestCard(r, staffView) { onOpen(r.id) } }
                }
                if (all.isEmpty()) item(key = "empty") {
                    EmptyState(Icons.Outlined.EventAvailable, stringResource(R.string.calls_none), stringResource(R.string.calls_none_body))
                }
            }
        }
    }
}

/** One request: details and the actions this person may take now. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CallRequestScreen(vm: CallsViewModel, id: String, onBack: () -> Unit, onJoin: (CallRequest, iAmStaff: Boolean) -> Unit) {
    val r by remember(id) { vm.observe(id) }.collectAsStateWithLifecycle(null)
    val me by vm.profile.collectAsStateWithLifecycle()
    var proposing by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Header(stringResource(R.string.calls_request_title), onBack)
            val req = r
            val p = me
            if (req == null || p == null) {
                LoadingState()
                return@Column
            }
            val iAmStaff = p.uid != req.memberUid
            Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                RequestCard(req, iAmStaff) {}
                if (req.note.isNotBlank()) OjasCard { Text(req.note, style = MaterialTheme.typography.bodyLarge) }
                if (req.status == RequestStatus.Proposed) {
                    Text(stringResource(R.string.calls_proposed_line, req.staffName.ifEmpty { "—" }, istTime(req.proposedAt)), style = MaterialTheme.typography.bodyLarge)
                }
                if (canJoinNow(req)) {
                    Button(onClick = { onJoin(req, iAmStaff) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (req.video) Icons.Outlined.Videocam else Icons.Outlined.Call, contentDescription = null)
                        Text(stringResource(if (iAmStaff) R.string.calls_start else R.string.calls_join), modifier = Modifier.padding(start = Spacing.s))
                    }
                } else if (req.status == RequestStatus.Confirmed) {
                    Text(stringResource(R.string.calls_join_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (iAmStaff && req.status == RequestStatus.Pending) {
                    Button(onClick = { vm.answer(req, RequestStatus.Confirmed) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calls_confirm)) }
                    OutlinedButton(onClick = { proposing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calls_propose)) }
                    TextButton(onClick = { vm.answer(req, RequestStatus.Declined) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calls_decline)) }
                }
                if (!iAmStaff && req.status == RequestStatus.Proposed) {
                    Button(onClick = { vm.accept(req) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calls_accept_time)) }
                }
                if (!iAmStaff && req.status.isOpen) {
                    TextButton(onClick = { vm.cancel(req) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.calls_cancel_request)) }
                }
            }
            if (proposing) ProposeDialog(onDismiss = { proposing = false }) { at -> vm.answer(req, RequestStatus.Proposed, at); proposing = false }
        }
    }
}

/** Staff choose another day and time to suggest. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProposeDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val dates = rememberDatePickerState(selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= now - 86_400_000L
    })
    var slot by remember { mutableIntStateOf(-1) }
    val slots = daySlots()
    val day = dates.selectedDateMillis
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calls_propose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = dates, title = null, headline = null, showModeToggle = false)
                if (day != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEachIndexed { i, (h, m) ->
                        FilterChip(selected = slot == i, onClick = { slot = i },
                            label = { Text(SimpleDateFormat("h:mm a", appLocale()).apply { timeZone = IST }.format(Date(pickedDateAt(day, h, m)))) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = day != null && slot >= 0, onClick = { if (day != null && slot >= 0) onPick(pickedDateAt(day, slots[slot].first, slots[slot].second)) }) {
                Text(stringResource(R.string.action_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** A labelled round action used on the Ask tab contact cards. */
@Composable
fun ContactAction(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            if (!enabled) Icon(
                Icons.Outlined.Lock, contentDescription = null,
                modifier = Modifier.size(14.dp).align(Alignment.BottomEnd), tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
