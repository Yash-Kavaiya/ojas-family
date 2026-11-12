package com.ojashealing.family.ui.ask

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.ai.AiChat
import com.ojashealing.family.data.ai.AiQuota
import com.ojashealing.family.data.ai.AiRepository
import com.ojashealing.family.data.ai.AiSource
import com.ojashealing.family.data.ai.AiStreamEvent
import com.ojashealing.family.data.points.serverMessage
import com.ojashealing.family.ui.components.SendButton
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.theme.staggeredEntrance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of the conversation on screen. An answer is [streaming] while it is still being written. */
data class AiUiMessage(
    val id: String,
    val fromMe: Boolean,
    val text: String,
    val sources: List<AiSource> = emptyList(),
    val followUps: List<String> = emptyList(),
    val rating: Int = 0,
    val streaming: Boolean = false,
    /** Set when the answer failed: the reason to show, and the question to try again. */
    val error: String? = null,
    val retryQuestion: String = "",
)

/** Splits "text [1] more [2][3]" into plain parts and citation numbers, for styling. */
sealed interface AnswerPart {
    data class Plain(val text: String) : AnswerPart
    data class Cite(val n: Int) : AnswerPart
}

fun answerParts(text: String): List<AnswerPart> {
    val out = mutableListOf<AnswerPart>()
    var last = 0
    Regex("""\[(\d{1,2})]""").findAll(text).forEach { m ->
        if (m.range.first > last) out += AnswerPart.Plain(text.substring(last, m.range.first))
        out += AnswerPart.Cite(m.groupValues[1].toInt())
        last = m.range.last + 1
    }
    if (last < text.length) out += AnswerPart.Plain(text.substring(last))
    return out
}

@OptIn(ExperimentalCoroutinesApi::class)
class SwamijiAiViewModel(private val repo: AiRepository, uid: kotlinx.coroutines.flow.Flow<String?>) : ViewModel() {
    private val _messages = MutableStateFlow<List<AiUiMessage>>(emptyList())
    val messages: StateFlow<List<AiUiMessage>> = _messages
    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId
    private val _quota = MutableStateFlow<AiQuota?>(null)
    val quota: StateFlow<AiQuota?> = _quota
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val myUid = MutableStateFlow<String?>(null)
    private var job: Job? = null
    private var counter = 0

    val chats: StateFlow<List<AiChat>> = uid.filterNotNull().flatMapLatest { repo.observeChats(it) }
        .catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { myUid.value = uid.filterNotNull().first() }
        refreshQuota()
    }

    private fun refreshQuota() {
        viewModelScope.launch { repo.quota().onSuccess { _quota.value = it } }
    }

    fun ask(question: String, lang: String, genericError: String) {
        val q = question.trim()
        if (q.length < 2 || _busy.value) return
        _busy.value = true
        val answerId = "local-${counter++}"
        _messages.update { list ->
            list.filterNot { it.error != null } +
                AiUiMessage("q-$answerId", fromMe = true, text = q) +
                AiUiMessage(answerId, fromMe = false, text = "", streaming = true)
        }
        job = viewModelScope.launch {
            try {
                repo.ask(q, _chatId.value, lang).collect { e ->
                    when (e) {
                        is AiStreamEvent.Text -> _messages.update { l -> l.map { if (it.id == answerId) it.copy(text = it.text + e.piece) else it } }
                        is AiStreamEvent.Done -> {
                            _chatId.value = e.answer.chatId
                            _quota.update { it?.copy(remaining = e.answer.remaining) ?: AiQuota(e.answer.remaining, 0) }
                            _messages.update { l ->
                                l.map {
                                    if (it.id == answerId) it.copy(
                                        id = e.answer.messageId.ifBlank { answerId }, text = e.answer.text, sources = e.answer.sources,
                                        followUps = e.answer.followUps, streaming = false,
                                    ) else it
                                }
                            }
                        }
                    }
                }
                // A stream that ends without its final result keeps what arrived
                _messages.update { l -> l.map { if (it.id == answerId && it.streaming) it.copy(streaming = false) else it } }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                val reason = serverMessage(t) ?: genericError
                _messages.update { l -> l.map { if (it.id == answerId) it.copy(text = "", streaming = false, error = reason, retryQuestion = q) else it } }
            } finally {
                _busy.value = false
            }
        }
    }

    fun retry(m: AiUiMessage, lang: String, genericError: String) {
        _messages.update { l -> l.filterNot { it.id == m.id || it.id == "q-${m.id}" } }
        ask(m.retryQuestion, lang, genericError)
    }

    fun rate(m: AiUiMessage, rating: Int) {
        val chat = _chatId.value ?: return
        val next = if (m.rating == rating) 0 else rating
        _messages.update { l -> l.map { if (it.id == m.id) it.copy(rating = next) else it } }
        viewModelScope.launch { repo.rate(chat, m.id, next) }
    }

    fun newChat() {
        job?.cancel()
        _busy.value = false
        _chatId.value = null
        _messages.value = emptyList()
    }

    fun openChat(id: String) {
        val uid = myUid.value ?: return
        job?.cancel()
        _busy.value = false
        _chatId.value = id
        viewModelScope.launch {
            val stored = runCatching { repo.observeMessages(uid, id).first() }.getOrElse { emptyList() }
            _messages.value = stored.map { AiUiMessage(it.id, it.fromMe, it.text, it.sources, it.followUps, it.rating) }
        }
    }

    fun deleteChat(id: String) {
        if (_chatId.value == id) newChat()
        viewModelScope.launch { repo.deleteChat(id) }
    }
}

/**
 * Ask Swamiji AI: answers from Swamiji's own teachings (the Ojas Healing videos), written live,
 * with the videos it drew on underneath. For anything personal, it points to Swamiji himself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwamijiAiScreen(
    vm: SwamijiAiViewModel,
    onBack: () -> Unit,
    onAskSwamiji: () -> Unit,
    onOpenVideo: (videoId: String, title: String, startSec: Int) -> Unit,
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    val chats by vm.chats.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val context = LocalContext.current
    val lang = LocalConfiguration.current.locales[0].language.takeIf { it in listOf("en", "hi", "gu") } ?: "en"
    val genericError = stringResource(R.string.ai_error)
    val noneLeft = quota?.let { it.remaining <= 0 && it.limit > 0 } == true

    fun send(text: String) {
        if (text.isBlank() || busy || noneLeft) return
        vm.ask(text, lang, genericError)
        draft = ""
    }

    // Voice: Android's own speech recognizer in the app language, when the phone has one
    val canListen = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { draft = it }
    }
    val listenPrompt = stringResource(R.string.ai_listen_prompt)

    // Keep the newest words in view while an answer is written
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLength / 80) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.size)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                AiAvatar(36, glowing = busy)
                Column(Modifier.padding(start = Spacing.s).weight(1f)) {
                    Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (busy) stringResource(R.string.ai_writing) else stringResource(R.string.ai_subtitle),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.ai_history)) }
                IconButton(onClick = vm::newChat, enabled = messages.isNotEmpty()) { Icon(Icons.Outlined.EditNote, contentDescription = stringResource(R.string.ai_new_chat)) }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                item(key = "intro") {
                    if (messages.isEmpty()) IntroCard(onAskSwamiji, onSuggest = ::send)
                    else Spacer(Modifier.size(1.dp))
                }
                items(messages, key = { it.id }, contentType = { if (it.fromMe) "me" else "ai" }) { m ->
                    if (m.fromMe) UserBubble(m.text, Modifier.animateItem())
                    else AiBubble(
                        m,
                        isLast = m == messages.last(),
                        onSource = { s -> s.videoId?.let { onOpenVideo(it, s.title, s.startSec) } },
                        onFollowUp = ::send,
                        onRate = { r -> vm.rate(m, r) },
                        onCopy = { copy(context, m.text) },
                        onShare = { share(context, m.text) },
                        onRetry = { vm.retry(m, lang, genericError) },
                        onAskSwamiji = onAskSwamiji,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            quota?.takeIf { it.limit > 0 }?.let { q ->
                Text(
                    if (q.remaining > 0) stringResource(R.string.ai_quota_left, q.remaining) else stringResource(R.string.ai_quota_none),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (q.remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.xl),
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(1000) },
                placeholder = { Text(stringResource(R.string.ai_hint)) },
                shape = RoundedCornerShape(24.dp),
                enabled = !noneLeft,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canListen && draft.isBlank()) {
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, when (lang) { "hi" -> "hi-IN"; "gu" -> "gu-IN"; else -> "en-IN" })
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, listenPrompt)
                                runCatching { listen.launch(intent) }
                            }) { Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.ai_speak)) }
                        }
                        SendButton(onClick = { send(draft) }, enabled = draft.isNotBlank() && !noneLeft, busy = busy, size = 40.dp, modifier = Modifier.padding(end = 6.dp))
                    }
                }
            )
        }
    }
    if (showHistory) HistorySheet(chats, onOpen = { vm.openChat(it); showHistory = false }, onDelete = vm::deleteChat, onDismiss = { showHistory = false })
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Swamiji AI", text))
}

private fun share(context: Context, text: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }
}

@Composable
private fun AiAvatar(size: Int, glowing: Boolean = false) {
    val t = rememberInfiniteTransition(label = "aiGlow")
    val glow by t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900, easing = OjasEasing.Standard), RepeatMode.Reverse), label = "g")
    Box(contentAlignment = Alignment.Center) {
        if (glowing) Box(Modifier.size((size + 8).dp).alpha(glow * 0.5f).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF7CC4FF), Color.Transparent))))
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.sunrise)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntroCard(onAskSwamiji: () -> Unit, onSuggest: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(R.string.ai_suggest_blue_light),
        stringResource(R.string.ai_suggest_protection),
        stringResource(R.string.ai_suggest_meditation),
        stringResource(R.string.ai_suggest_chakra),
        stringResource(R.string.ai_suggest_stress),
        stringResource(R.string.ai_suggest_sleep),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Column(
            Modifier.fillMaxWidth().staggeredEntrance(0, key = "aiIntro").clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0B3D91), Color(0xFF2A1450))))
                .padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Text(stringResource(R.string.ai_intro_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(stringResource(R.string.ai_intro_body), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Text(
                stringResource(R.string.ai_ask_real),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFFFD54F),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAskSwamiji).padding(vertical = 4.dp)
            )
        }
        Text(stringResource(R.string.ai_try_asking), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.staggeredEntrance(1, key = "aiIntro"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.staggeredEntrance(2, key = "aiIntro")) {
            suggestions.forEach { s -> SuggestionChip(s) { onSuggest(s) } }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                .background(Brush.linearGradient(OjasGradients.saffron))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun answerText(text: String, streaming: Boolean): AnnotatedString {
    val cite = MaterialTheme.typography.labelSmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.primary, baselineShift = BaselineShift(0.3f))
    return buildAnnotatedString {
        answerParts(text).forEach { p ->
            when (p) {
                is AnswerPart.Plain -> append(p.text)
                is AnswerPart.Cite -> withStyle(cite) { append("${p.n}") }
            }
        }
        if (streaming) withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(" ▍") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiBubble(
    m: AiUiMessage,
    isLast: Boolean,
    onSource: (AiSource) -> Unit,
    onFollowUp: (String) -> Unit,
    onRate: (Int) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onAskSwamiji: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AiAvatar(28, glowing = m.streaming)
        Spacer(Modifier.size(Spacing.s))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            val shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
            Box(
                Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .animateContentSize(tween(Durations.Quick))
            ) {
                when {
                    m.error != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(m.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Row {
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                            TextButton(onClick = onAskSwamiji) { Text(stringResource(R.string.ai_ask_real_short)) }
                        }
                    }
                    m.text.isEmpty() && m.streaming -> ThinkingDots()
                    else -> Text(answerText(m.text, m.streaming), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            // The videos this answer drew on: tap to watch from that moment
            AnimatedVisibility(m.sources.isNotEmpty(), enter = fadeIn(tween(Durations.Base)) + slideInVertically(tween(Durations.Base, easing = OjasEasing.Emphasized)) { it / 4 }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ai_sources), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    m.sources.forEach { s -> SourceRow(s) { onSource(s) } }
                }
            }
            if (!m.streaming && m.error == null && m.text.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onRate(1) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (m.rating == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, stringResource(R.string.ai_helpful), modifier = Modifier.size(18.dp),
                            tint = if (m.rating == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRate(-1) }, modifier = Modifier.size(36.dp)) {
                        Icon(if (m.rating == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, stringResource(R.string.ai_not_helpful), modifier = Modifier.size(18.dp),
                            tint = if (m.rating == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.ai_copy), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Share, stringResource(R.string.ai_share), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (isLast && m.followUps.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        m.followUps.forEach { f -> SuggestionChip(f) { onFollowUp(f) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: AiSource, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = s.videoId != null, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Text("${s.n}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (s.videoId != null) {
            if (s.startSec > 0) Text("%d:%02d".format(s.startSec / 60, s.startSec % 60), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.PlayCircle, null, tint = Color(0xFFE53935), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val label = stringResource(R.string.ai_writing)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = label }) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(chats: List<AiChat>, onOpen: (String) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
            Text(stringResource(R.string.ai_history), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.m, start = Spacing.s))
            if (chats.isEmpty()) {
                Text(stringResource(R.string.ai_history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Spacing.s))
            }
            LazyColumn {
                items(chats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(c.id) }.padding(Spacing.s).animateItem(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(c.lastText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onDelete(c.id) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.ai_delete_chat)) }
                    }
                }
            }
        }
    }
}
