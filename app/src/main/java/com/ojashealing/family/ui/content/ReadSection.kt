package com.ojashealing.family.ui.content

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.ojashealing.family.R
import com.ojashealing.family.data.read.ReadAnswerMax
import com.ojashealing.family.data.read.ReadQa
import com.ojashealing.family.data.read.ReadQaRepository
import com.ojashealing.family.data.read.ReadQuestionMax
import com.ojashealing.family.data.read.ReadTicks
import com.ojashealing.family.data.read.ttsLanguageFor
import com.ojashealing.family.data.read.validReadQa
import com.ojashealing.family.domain.Role as UserRole
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.SectionHeader
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** Only admins and Swamiji add, change or remove the questions; the server rules say the same. */
fun canEditRead(viewer: Viewer?): Boolean = viewer?.role == UserRole.Admin || viewer?.role == UserRole.Swamiji

class ReadQaViewModel(private val repo: ReadQaRepository, private val ticks: ReadTicks, private val uid: () -> String?) : ViewModel() {
    /** Null until the first list arrives. Listened to only while the Practice tab is on screen. */
    val items: StateFlow<List<ReadQa>?> = repo.observe()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val read: StateFlow<Set<String>> = ticks.read

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    fun setDone(id: String, done: Boolean) = ticks.set(id, done)

    /** Adds a new one when [id] is null. [done] gets null on success, else a message to show. */
    fun save(id: String?, question: String, answer: String, done: (Int?) -> Unit) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            val r = if (id == null) repo.add(question, answer, uid()) else repo.update(id, question, answer)
            _saving.value = false
            done(r.exceptionOrNull()?.let { if (it is FirebaseNetworkException) R.string.error_offline else R.string.read_save_failed })
        }
    }

    fun delete(id: String, done: (Int?) -> Unit) {
        viewModelScope.launch {
            val r = repo.delete(id)
            done(r.exceptionOrNull()?.let { R.string.read_delete_failed })
        }
    }
}

/** What the Practice tab needs to show the Read section. */
@Stable
class ReadUi(
    val items: List<ReadQa>?,
    val read: Set<String>,
    val canEdit: Boolean,
    val saving: Boolean,
    val onSetDone: (id: String, done: Boolean) -> Unit,
    val onSave: (id: String?, question: String, answer: String, done: (Int?) -> Unit) -> Unit,
    val onDelete: (id: String, done: (Int?) -> Unit) -> Unit,
)

/** Which question is open and which is being written; held by the Practice tab. */
@Stable
class ReadState {
    var openId by mutableStateOf<String?>(null)
    /** "" for a new question, an id to change one, null when no editor is open. */
    var editingId by mutableStateOf<String?>(null)
}

/**
 * Practice > Read: only the questions in their boxes, no likes or replies. Each time it comes on
 * screen a random question not yet Done is put first; Done ones stay in the list with a tick.
 */
@Composable
fun ReadPractice(ui: ReadUi) {
    val state = remember { ReadState() }
    val items = ui.items
    // Picked once per visit, so the list doesn't jump while the member reads or marks Done
    var firstId by remember { mutableStateOf<String?>(null) }
    if (items != null && (firstId == null || items.none { it.id == firstId })) {
        firstId = ReadTicks.pickFirst(items.map { it.id }, ui.read)
    }
    when {
        items == null -> LoadingState()
        items.isEmpty() && !ui.canEdit -> Column(Modifier.fillMaxSize()) {
            SectionHeader(stringResource(R.string.read_title))
            EmptyState(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.read_empty_title),
                body = stringResource(R.string.read_empty_body),
                modifier = Modifier.weight(1f)
            )
        }
        else -> {
            val ordered = items.firstOrNull { it.id == firstId }?.let { listOf(it) + (items - it) } ?: items
            LazyColumn(contentPadding = PaddingValues(top = Spacing.s, bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                readSection(ui, state, ordered, firstId)
            }
        }
    }
    ReadOverlays(ui, state)
}

private fun LazyListScope.readSection(ui: ReadUi, state: ReadState, items: List<ReadQa>, firstId: String?) {
    item(key = "readHeader", contentType = "readHeader") {
        Column {
            SectionHeader(
                stringResource(R.string.read_title),
                modifier = Modifier.padding(top = Spacing.m),
                action = if (ui.canEdit) {
                    {
                        TextButton(onClick = { state.editingId = "" }) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.read_add), modifier = Modifier.padding(start = Spacing.xs))
                        }
                    }
                } else null
            )
            val done = items.count { it.id in ui.read }
            Text(
                if (items.isEmpty()) stringResource(R.string.read_empty_staff)
                else stringResource(R.string.read_progress, done, items.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l)
            )
        }
    }
    items(items, key = { "read_" + it.id }, contentType = { "readQa" }) { qa ->
        ReadRow(
            qa, read = qa.id in ui.read, first = qa.id == firstId,
            onOpen = { state.openId = qa.id },
            modifier = Modifier.padding(horizontal = Spacing.l).animateItem()
        )
    }
}

@Composable
private fun ReadRow(qa: ReadQa, read: Boolean, first: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val readLabel = stringResource(if (read) R.string.read_done else R.string.read_not_done)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (first) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { stateDescription = readLabel }
    ) {
        val textColor = if (first) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (first) {
                Text(stringResource(R.string.read_start_here), style = MaterialTheme.typography.labelLarge, color = textColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(
                    if (read) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    qa.question,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** The open answer and the editor; drawn over the Practice tab. */
@Composable
fun ReadOverlays(ui: ReadUi, state: ReadState) {
    val items = ui.items.orEmpty()
    state.openId?.let { id ->
        val qa = items.firstOrNull { it.id == id }
        if (qa == null) androidx.compose.runtime.LaunchedEffect(id) { state.openId = null }
        else ReadAnswerSheet(
            qa, ui,
            onEdit = { state.editingId = qa.id },
            onDeleted = { state.openId = null },
            onClose = { state.openId = null }
        )
    }
    state.editingId?.let { id ->
        ReadEditor(
            existing = items.firstOrNull { it.id == id },
            saving = ui.saving,
            onSave = { q, a, done -> ui.onSave(id.ifEmpty { null }, q, a, done) },
            onClose = { state.editingId = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadAnswerSheet(qa: ReadQa, ui: ReadUi, onEdit: () -> Unit, onDeleted: () -> Unit, onClose: () -> Unit) {
    val done = qa.id in ui.read
    val voice = rememberReadAloud()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = { voice.stop(); onClose() }, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            Text(qa.question, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                val speaking = voice.speaking
                FilledTonalButton(
                    onClick = { if (speaking) voice.stop() else voice.speak(qa.question + ".\n" + qa.answer) },
                    enabled = voice.ready,
                ) {
                    Icon(if (speaking) Icons.Outlined.StopCircle else Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(if (speaking) R.string.ai_stop_reading else R.string.ai_read_aloud), modifier = Modifier.padding(start = Spacing.s))
                }
                // Done is the member's own mark; the question stays in the list with a tick
                androidx.compose.material3.FilterChip(
                    selected = done,
                    onClick = { ui.onSetDone(qa.id, !done) },
                    label = { Text(stringResource(if (done) R.string.read_done else R.string.read_mark_done)) },
                    leadingIcon = {
                        Icon(if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
            SelectionContainer {
                Text(qa.answer, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            if (ui.canEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    OutlinedButton(onClick = { voice.stop(); onEdit() }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_edit), modifier = Modifier.padding(start = Spacing.s))
                    }
                    OutlinedButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.action_delete), modifier = Modifier.padding(start = Spacing.s))
                    }
                }
                deleteError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.read_delete_title)) },
            text = { Text(qa.question, maxLines = 4, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    voice.stop()
                    ui.onDelete(qa.id) { err -> if (err == null) onDeleted() else deleteError = err }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

@Composable
private fun ReadEditor(existing: ReadQa?, saving: Boolean, onSave: (String, String, (Int?) -> Unit) -> Unit, onClose: () -> Unit) {
    var question by rememberSaveable(existing?.id) { mutableStateOf(existing?.question.orEmpty()) }
    var answer by rememberSaveable(existing?.id) { mutableStateOf(existing?.answer.orEmpty()) }
    var error by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(stringResource(if (existing == null) R.string.read_add_title else R.string.read_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it.take(ReadQuestionMax) },
                    label = { Text(stringResource(R.string.read_question)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it.take(ReadAnswerMax) },
                    label = { Text(stringResource(R.string.read_answer)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    minLines = 6,
                )
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && validReadQa(question, answer),
                onClick = { onSave(question, answer) { err -> if (err == null) onClose() else error = err } }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onClose) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/** The phone's own voice reading a text, in Hindi, Gujarati or English by the text's script. */
@Stable
class ReadAloud internal constructor() {
    internal var engine: TextToSpeech? = null
    var ready by mutableStateOf(false)
        internal set
    var speaking by mutableStateOf(false)
        internal set
    internal var lastId: String? = null
    private var run = 0

    fun speak(text: String) {
        val tts = engine ?: return
        tts.stop()
        val tag = ttsLanguageFor(text)
        val wanted = Locale.forLanguageTag(tag)
        // A phone without a Gujarati voice reads Gujarati poorly in English; Hindi is closer
        if (tts.setLanguage(wanted) < TextToSpeech.LANG_AVAILABLE && tag == "gu-IN") tts.setLanguage(Locale.forLanguageTag("hi-IN"))
        val parts = speechChunks(text, (runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 100).coerceAtLeast(500))
        if (parts.isEmpty()) return
        run++
        parts.forEachIndexed { i, part ->
            val id = "read_${run}_$i"
            if (i == parts.lastIndex) lastId = id
            tts.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        speaking = true
    }

    fun stop() {
        engine?.stop()
        speaking = false
    }
}

/** Splits [text] into pieces no longer than [max] characters, at line or sentence ends where it can. */
fun speechChunks(text: String, max: Int): List<String> {
    val out = mutableListOf<String>()
    var rest = text.trim()
    while (rest.length > max) {
        val window = rest.substring(0, max)
        val cut = listOf('\n', '।', '.', '?', '!', ',', ' ').firstNotNullOfOrNull { c -> window.lastIndexOf(c).takeIf { it > max / 3 } } ?: (max - 1)
        out += rest.substring(0, cut + 1).trim()
        rest = rest.substring(cut + 1).trim()
    }
    if (rest.isNotEmpty()) out += rest
    return out.filter { it.isNotBlank() }
}

@Composable
fun rememberReadAloud(): ReadAloud {
    val context = LocalContext.current
    val voice = remember { ReadAloud() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == voice.lastId) scope.launch { voice.speaking = false }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    scope.launch { voice.speaking = false }
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {}
            })
            voice.engine = tts
            voice.ready = true
        }
        onDispose {
            voice.ready = false
            voice.speaking = false
            voice.engine = null
            tts.stop()
            tts.shutdown()
        }
    }
    return voice
}
