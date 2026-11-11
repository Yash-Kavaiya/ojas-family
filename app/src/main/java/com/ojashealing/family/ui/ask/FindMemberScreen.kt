package com.ojashealing.family.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.user.MemberRow
import com.ojashealing.family.domain.Role
import com.ojashealing.family.ui.adaptive.ownedInsets
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.thought.AuthorAvatar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Who Swamiji and admins may reach out to: members and senior members, never another staff account or themselves. */
fun reachable(rows: List<MemberRow>, myUid: String): List<MemberRow> =
    rows.filter { it.uid != myUid && (it.role == Role.Member || it.role == Role.SeniorMember) }

/**
 * Swamiji and admins look a member up by name to start a chat or a call.
 * [search] is a name-prefix lookup on users/ (staff may read every profile).
 */
class FindMemberViewModel(
    private val search: suspend (String) -> Result<List<MemberRow>>,
    private val myUid: String,
) : ViewModel() {
    private val _rows = MutableStateFlow<List<MemberRow>?>(null)
    val rows: StateFlow<List<MemberRow>?> = _rows
    private var job: Job? = null

    init { query("") }

    fun query(text: String) {
        job?.cancel()
        job = viewModelScope.launch {
            if (text.isNotBlank()) delay(250)
            val q = text.trim()
            // Names are stored as typed, so also try a capitalised first letter ("ra" finds "Ravi")
            val variants = listOf(q, q.replaceFirstChar { it.titlecase() }).distinct()
            val found = variants.flatMap { search(it).getOrElse { emptyList() } }.distinctBy { it.uid }
            _rows.value = reachable(found, myUid)
        }
    }
}

@Composable
fun FindMemberScreen(
    vm: FindMemberViewModel,
    onBack: () -> Unit,
    onChat: (MemberRow) -> Unit,
    onCall: (MemberRow, video: Boolean) -> Unit,
    embedded: Boolean = false,
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().ownedInsets(embedded)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.s)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(stringResource(R.string.find_member_title), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it; vm.query(it) }, singleLine = true,
                label = { Text(stringResource(R.string.members_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l)
            )
            val list = rows
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(Icons.Outlined.PersonSearch, stringResource(R.string.members_empty), "", Modifier.weight(1f))
                else -> LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(list, key = { it.uid }, contentType = { "member" }) { m -> MemberContactRow(m, onChat, onCall) }
                }
            }
        }
    }
}

@Composable
private fun MemberContactRow(m: MemberRow, onChat: (MemberRow) -> Unit, onCall: (MemberRow, Boolean) -> Unit) {
    val name = m.name.ifEmpty { m.uid.take(8) }
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            AuthorAvatar(Author(m.uid, name, m.photoUrl, m.role), 44)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        stringResource(if (m.role == Role.SeniorMember) R.string.role_senior_member else R.string.role_member),
                        stringResource(R.string.profile_member_level, m.memberLevel)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            ContactAction(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.ask_chat_short), true, { onChat(m) }, Modifier.weight(1f))
            ContactAction(Icons.Outlined.Call, stringResource(R.string.calls_audio_short), true, { onCall(m, false) }, Modifier.weight(1f))
            ContactAction(Icons.Outlined.Videocam, stringResource(R.string.calls_video_short), true, { onCall(m, true) }, Modifier.weight(1f))
        }
    }
}
