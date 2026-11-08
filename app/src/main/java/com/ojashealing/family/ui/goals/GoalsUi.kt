package com.ojashealing.family.ui.goals

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.google.firebase.FirebaseNetworkException
import com.ojashealing.family.R
import com.ojashealing.family.data.goals.Goal
import com.ojashealing.family.data.goals.GoalKind
import com.ojashealing.family.data.goals.GoalProgress
import com.ojashealing.family.data.goals.GoalStatus
import com.ojashealing.family.data.goals.achieved
import com.ojashealing.family.data.goals.suggestGoal
import com.ojashealing.family.data.settings.currentAppLanguage
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The language goal titles are shown in; the app setting wins, then the phone's language. */
@Composable
fun goalLanguage(): String = currentAppLanguage().ifEmpty { Locale.getDefault().language }

data class ProfileGoalsState(
    val loaded: Boolean = false,
    val goals: List<Goal> = emptyList(),
    val suggested: Goal? = null,
    /** The suggested goal's last photo was not approved, so the card says to try again. */
    val suggestedWasRejected: Boolean = false,
    val waiting: List<Goal> = emptyList(),
    val achieved: List<Goal> = emptyList()
)

/** The member's own goals on their profile. `hideGoals` only hides them from others, never from the owner. */
class ProfileGoalsViewModel(goals: Flow<List<Goal>>, progress: Flow<Map<String, GoalProgress>>) : ViewModel() {
    val state: StateFlow<ProfileGoalsState> = combine(goals.catch { emit(emptyList()) }, progress.catch { emit(emptyMap()) }) { g, p ->
        val suggested = suggestGoal(g, p)
        ProfileGoalsState(
            loaded = true,
            goals = g,
            suggested = suggested,
            suggestedWasRejected = suggested?.let { p[it.id]?.status == GoalStatus.Rejected } ?: false,
            waiting = g.filter { p[it.id]?.status == GoalStatus.Submitted }.sortedBy { it.order },
            achieved = achieved(g, p)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileGoalsState())
}

data class GoalSubmitUiState(
    val photo: Uri? = null,
    val note: String = "",
    val busy: Boolean = false,
    @StringRes val error: Int? = null,
    val done: Boolean = false
)

/** Photo + note for one goal. The submission is only created once the photo upload succeeds. */
class GoalSubmitViewModel(private val submit: suspend (Uri, String) -> Result<Unit>) : ViewModel() {

    private val _state = MutableStateFlow(GoalSubmitUiState())
    val state: StateFlow<GoalSubmitUiState> = _state.asStateFlow()

    fun onPhoto(uri: Uri?) {
        if (uri != null) _state.update { it.copy(photo = uri, error = null) }
    }

    fun onNote(value: String) = _state.update { it.copy(note = value.take(MAX_NOTE)) }

    fun send() {
        val s = _state.value
        if (s.busy) return
        val photo = s.photo ?: run {
            _state.update { it.copy(error = R.string.goal_photo_required) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            submit(photo, s.note.trim())
                .onSuccess { _state.update { it.copy(busy = false, done = true) } }
                .onFailure { e ->
                    val error = if (e is FirebaseNetworkException) R.string.error_offline else R.string.error_generic
                    _state.update { it.copy(busy = false, error = error) }
                }
        }
    }

    companion object {
        const val MAX_NOTE = 500
    }
}

@Composable
fun ProfileGoalsCard(state: ProfileGoalsState, onSubmit: (Goal) -> Unit) {
    val lang = goalLanguage()
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.profile_goals_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        val suggested = state.suggested
        if (!state.loaded) return@OjasCard
        if (state.goals.isEmpty()) {
            Text(stringResource(R.string.profile_goals_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@OjasCard
        }
        val done = state.achieved.map { it.id }.toSet()
        val waiting = state.waiting.map { it.id }.toSet()
        // The roadmap: every goal in order, with the one to do now highlighted
        val ordered = state.goals.sortedBy { it.order }
        Text(
            stringResource(R.string.road_goals_progress, done.size, ordered.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        com.ojashealing.family.ui.components.Roadmap(
            ordered.map { g ->
                com.ojashealing.family.ui.components.RoadStep(
                    title = g.titleIn(lang),
                    detail = if (g.id == suggested?.id) g.descriptionIn(lang) else stringResource(R.string.goal_reward, g.reward),
                    state = when (g.id) {
                        in done -> com.ojashealing.family.ui.components.StepState.Done
                        in waiting -> com.ojashealing.family.ui.components.StepState.Waiting
                        suggested?.id -> com.ojashealing.family.ui.components.StepState.Current
                        else -> com.ojashealing.family.ui.components.StepState.Upcoming
                    },
                    icon = if (g.id == suggested?.id) Icons.Outlined.Flag else null
                )
            }
        ) { i ->
            val g = ordered[i]
            SuggestedGoal(g, lang, state.suggestedWasRejected, onSubmit, compact = true)
        }
        if (suggested == null && done.size == ordered.size) {
            Text(stringResource(R.string.goal_all_done), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SuggestedGoal(goal: Goal, lang: String, wasRejected: Boolean, onSubmit: (Goal) -> Unit, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (!compact) {
            Text(stringResource(R.string.goal_suggested), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(goal.titleIn(lang), style = MaterialTheme.typography.titleMedium)
            goal.descriptionIn(lang).takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.goal_reward, goal.reward), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (wasRejected) {
            Text(stringResource(R.string.goal_rejected_retry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (goal.kind == GoalKind.Photo) {
            Button(onClick = { onSubmit(goal) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.goal_submit_photo), modifier = Modifier.padding(start = Spacing.s))
            }
        } else {
            // 21-day practice goals complete on their own from the practice log
            Text(stringResource(R.string.goal_practice_auto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GoalLine(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun GoalSubmitScreen(goal: Goal?, vm: GoalSubmitViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang = goalLanguage()
    LaunchedEffect(state.done) { if (state.done) onBack() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.onPhoto(it) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                Text(goal?.titleIn(lang).orEmpty(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
            goal?.descriptionIn(lang)?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val pick = { if (!state.busy) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            // A picked photo shows whole at its own shape; tap it to choose another
            if (state.photo != null) com.ojashealing.family.ui.components.ResponsiveImage(state.photo, onClick = pick)
            else Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !state.busy, onClick = pick),
                contentAlignment = Alignment.Center
            ) {
                run {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.goal_choose_photo), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNote,
                label = { Text(stringResource(R.string.goal_note)) },
                enabled = !state.busy,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            Button(onClick = vm::send, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                if (state.busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.goal_send))
            }
            Text(stringResource(R.string.goal_review_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
