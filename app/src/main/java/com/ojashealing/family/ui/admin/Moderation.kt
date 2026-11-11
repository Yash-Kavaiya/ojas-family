package com.ojashealing.family.ui.admin

import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Person
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ojashealing.family.ui.adaptive.LoadOlderWhenEnd
import com.ojashealing.family.ui.adaptive.loadOlderRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.goals.Goal
import com.ojashealing.family.data.goals.GoalRepository
import com.ojashealing.family.data.goals.GoalSubmission
import com.ojashealing.family.ui.goals.goalLanguage
import kotlinx.coroutines.flow.combine
import com.ojashealing.family.data.social.ModerationRepository
import com.ojashealing.family.data.social.PendingItem
import com.ojashealing.family.data.social.Report
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the moderation screen needs; backed by [ModerationRepository] in the app. */
interface ModerationActions {
    val pending: Flow<List<PendingItem>>
    val reports: Flow<List<Report>>
    val reportsHasMore: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun loadOlderReports() {}
    suspend fun approve(path: String): Result<Unit>
    suspend fun remove(path: String): Result<Unit>
    suspend fun dismissReport(id: String): Result<Unit>
    val goalSubmissions: Flow<List<GoalSubmission>>
    val goals: Flow<List<Goal>>
    suspend fun reviewGoal(uid: String, goalId: String, approve: Boolean): Result<Unit>
}

/** A member's goal photo next to the goal it is for (null if the goal was deleted). */
data class GoalReview(val submission: GoalSubmission, val goal: Goal?)

fun ModerationRepository.asActions(goalRepo: GoalRepository) = object : ModerationActions {
    override val pending = observePending()
    override val reports = observeReports()
    override val reportsHasMore = this@asActions.reportsHasMore
    override suspend fun loadOlderReports() = this@asActions.loadOlderReports()
    override suspend fun approve(path: String) = this@asActions.approve(path)
    override suspend fun remove(path: String) = this@asActions.remove(path)
    override suspend fun dismissReport(id: String) = this@asActions.dismissReport(id)
    override val goalSubmissions = goalRepo.observeSubmissions()
    override val goals = goalRepo.observeGoals()
    override suspend fun reviewGoal(uid: String, goalId: String, approve: Boolean) = goalRepo.review(uid, goalId, approve)
}

class ModerationViewModel(private val actions: ModerationActions) : ViewModel() {
    val pending: StateFlow<List<PendingItem>> = actions.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reports: StateFlow<List<Report>> = actions.reports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reportsHasMore: StateFlow<Boolean> = actions.reportsHasMore.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun loadOlderReports() { viewModelScope.launch { actions.loadOlderReports() } }

    val goalReviews: StateFlow<List<GoalReview>> = combine(actions.goalSubmissions, actions.goals) { subs, goals ->
        val byId = goals.associateBy { it.id }
        subs.map { GoalReview(it, byId[it.goalId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Approval makes the server award the goal's points, once
    fun reviewGoal(submission: GoalSubmission, approve: Boolean) {
        viewModelScope.launch { actions.reviewGoal(submission.uid, submission.goalId, approve) }
    }

    fun approve(path: String) {
        viewModelScope.launch { actions.approve(path) }
    }

    fun remove(path: String) {
        viewModelScope.launch { actions.remove(path) }
    }

    // Removing the reported post also closes the report
    fun removeReported(report: Report) {
        viewModelScope.launch {
            // The path came from the member who reported: only ever remove a member's post through it
            if (com.ojashealing.family.data.social.isReportablePath(report.targetPath)) actions.remove(report.targetPath)
            actions.dismissReport(report.id)
        }
    }

    fun dismiss(report: Report) {
        viewModelScope.launch { actions.dismissReport(report.id) }
    }
}

@Composable
fun ModerationScreen(vm: ModerationViewModel, onBack: () -> Unit) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    // Removing a post cannot be undone: one stray tap in a long list must not do it
    var confirmRemove by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }
    confirmRemove?.let { remove ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            text = { Text(stringResource(R.string.moderation_remove_confirm)) },
            confirmButton = { TextButton(onClick = { remove(); confirmRemove = null }) { Text(stringResource(R.string.moderation_remove_post), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
    val reportsMore by vm.reportsHasMore.collectAsStateWithLifecycle()
    val goalReviews by vm.goalReviews.collectAsStateWithLifecycle()
    val lang = goalLanguage()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        val reportState = rememberLazyListState()
        LoadOlderWhenEnd(reportState, reportsMore, reports.size) { vm.loadOlderReports() }
        LazyColumn(
            state = reportState,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            item(key = "top", contentType = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    Text(stringResource(R.string.moderation_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
            }
            item(key = "pendingTitle", contentType = "title") { Text(stringResource(R.string.moderation_pending), style = MaterialTheme.typography.titleLarge) }
            if (pending.isEmpty()) item(key = "pendingEmpty", contentType = "empty") { EmptyLine() }
            items(pending, key = { it.path }, contentType = { "pending" }) { item ->
                OjasCard {
                    Text(item.author, style = MaterialTheme.typography.labelLarge)
                    Text(item.text, style = MaterialTheme.typography.bodyLarge)
                    if (item.imageUrl.isNotEmpty()) {
                        com.ojashealing.family.ui.components.PostImage(item.imageUrl)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { vm.approve(item.path) }) { Text(stringResource(R.string.moderation_approve)) }
                        TextButton(onClick = { confirmRemove = { vm.remove(item.path) } }) { Text(stringResource(R.string.moderation_remove_post), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item(key = "goalsTitle", contentType = "title") { Text(stringResource(R.string.moderation_goal_photos), style = MaterialTheme.typography.titleLarge) }
            if (goalReviews.isEmpty()) item(key = "goalsEmpty", contentType = "empty") { EmptyLine() }
            items(goalReviews, key = { "goal:${it.submission.uid}/${it.submission.goalId}" }, contentType = { "goal" }) { review ->
                OjasCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(review.submission.memberPhotoUrl)
                        Column {
                            Text(
                                review.submission.memberName.ifEmpty { stringResource(R.string.moderation_unknown_member) },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(review.goal?.titleIn(lang) ?: review.submission.goalId, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    com.ojashealing.family.ui.components.PostImage(review.submission.photoUrl)
                    if (review.submission.note.isNotEmpty()) Text(review.submission.note, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { vm.reviewGoal(review.submission, approve = true) }) { Text(stringResource(R.string.moderation_approve)) }
                        TextButton(onClick = { vm.reviewGoal(review.submission, approve = false) }) { Text(stringResource(R.string.moderation_reject), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item(key = "reportsTitle", contentType = "title") { Text(stringResource(R.string.moderation_reports), style = MaterialTheme.typography.titleLarge) }
            if (reports.isEmpty()) item(key = "reportsEmpty", contentType = "empty") { EmptyLine() }
            items(reports, key = { it.id }, contentType = { "report" }) { report ->
                OjasCard {
                    Text(report.reason, style = MaterialTheme.typography.bodyLarge)
                    Text(report.targetPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { vm.dismiss(report) }) { Text(stringResource(R.string.moderation_dismiss)) }
                        TextButton(onClick = { confirmRemove = { vm.removeReported(report) } }) { Text(stringResource(R.string.moderation_remove_post), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            loadOlderRow(reportsMore) { vm.loadOlderReports() }
        }
    }
}

@Composable
private fun EmptyLine() {
    Text(stringResource(R.string.moderation_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MemberAvatar(photoUrl: String) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl.isNotEmpty()) AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp))
        else Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
