package com.ojashealing.family.ui.ask

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.SendButton
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The broad subject of a question, picked from keywords in English, Hindi and Gujarati. */
enum class AiTopic(@StringRes val answer: Int) {
    Meditation(R.string.ai_answer_meditation),
    Chakra(R.string.ai_answer_chakra),
    Stress(R.string.ai_answer_stress),
    Sleep(R.string.ai_answer_sleep),
    Healing(R.string.ai_answer_healing),
    Protection(R.string.ai_answer_protection),
    General(R.string.ai_answer_general)
}

private val topicWords: List<Pair<AiTopic, List<String>>> = listOf(
    AiTopic.Chakra to listOf("chakra", "kundalini", "चक्र", "कुंडलिनी", "ચક્ર", "કુંડલિની"),
    AiTopic.Protection to listOf("protect", "negative", "evil", "nazar", "सुरक्षा", "नकारात्मक", "नज़र", "રક્ષણ", "નકારાત્મક", "નજર"),
    AiTopic.Sleep to listOf("sleep", "insomnia", "dream", "नींद", "सपन", "ઊંઘ", "નીંદ", "સપન"),
    AiTopic.Stress to listOf("stress", "anxiety", "worry", "fear", "anger", "tension", "तनाव", "चिंता", "डर", "गुस्सा", "ગુસ્સો", "તણાવ", "ચિંતા", "ડર"),
    AiTopic.Healing to listOf("heal", "health", "pain", "disease", "उपचार", "स्वास्थ्य", "दर्द", "बीमार", "હીલ", "આરોગ્ય", "દુખાવો", "બીમાર"),
    AiTopic.Meditation to listOf("meditat", "dhyan", "breath", "focus", "mind", "ध्यान", "साँस", "सांस", "मन", "ધ્યાન", "શ્વાસ", "મન")
)

/** Which general answer fits [question]; [AiTopic.General] when nothing matches. */
fun aiTopicFor(question: String): AiTopic {
    val q = question.lowercase()
    return topicWords.firstOrNull { (_, words) -> words.any { it in q } }?.first ?: AiTopic.General
}

private data class AiMessage(val id: Int, val fromMe: Boolean, val text: String)

/**
 * Ask Swamiji AI, an early version: it answers with general guidance by topic and says plainly
 * that the full AI is still being built. Nothing leaves the phone.
 */
@Composable
fun SwamijiAiScreen(onBack: () -> Unit, onAskSwamiji: () -> Unit) {
    val messages = remember { mutableStateListOf<AiMessage>() }
    var draft by rememberSaveable { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val answers = AiTopic.entries.associateWith { stringResource(it.answer) }
    val devNote = stringResource(R.string.ai_dev_note)
    val suggestions = listOf(
        stringResource(R.string.ai_suggest_meditation),
        stringResource(R.string.ai_suggest_stress),
        stringResource(R.string.ai_suggest_chakra),
        stringResource(R.string.ai_suggest_sleep)
    )

    fun ask(text: String) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        messages += AiMessage(messages.size, true, q)
        draft = ""
        thinking = true
        scope.launch {
            delay(1200)
            messages += AiMessage(messages.size, false, answers.getValue(aiTopicFor(q)) + "\n\n" + devNote)
            thinking = false
        }
    }

    LaunchedEffect(messages.size, thinking) {
        val count = messages.size + 1 + (if (thinking) 1 else 0)
        list.animateScrollToItem(count - 1)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                AiAvatar(36)
                Column(Modifier.padding(start = Spacing.s).weight(1f)) {
                    Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ai_beta), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                item(key = "intro") { IntroCard(onAskSwamiji) }
                items(messages, key = { it.id }, contentType = { "message" }) { m -> AiBubble(m) }
                if (thinking) item(key = "thinking") { ThinkingDots() }
            }
            if (messages.isEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(bottom = Spacing.s)
                ) {
                    items(suggestions, key = { it }, contentType = { "suggestion" }) { s ->
                        Text(
                            s,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { ask(s) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(500) },
                placeholder = { Text(stringResource(R.string.ai_hint)) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                trailingIcon = {
                    SendButton(onClick = { ask(draft) }, enabled = draft.isNotBlank(), busy = thinking, size = 40.dp, modifier = Modifier.padding(end = 6.dp))
                }
            )
        }
    }
}

@Composable
private fun AiAvatar(size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(OjasGradients.sunrise)),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp)) }
}

@Composable
private fun IntroCard(onAskSwamiji: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Text(stringResource(R.string.ai_intro_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(stringResource(R.string.ai_intro_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            stringResource(R.string.ai_ask_real),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAskSwamiji).padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun AiBubble(m: AiMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!m.fromMe) { AiAvatar(28); Box(Modifier.size(Spacing.s)) }
        val shape = if (m.fromMe) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
        Text(
            m.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (m.fromMe) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(if (m.fromMe) Brush.linearGradient(OjasGradients.saffron) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)))
                .border(if (m.fromMe) 0.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AiAvatar(28)
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
