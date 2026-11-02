package com.ojashealing.family.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ojashealing.family.R
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
    suspend fun approve(path: String): Result<Unit>
    suspend fun remove(path: String): Result<Unit>
    suspend fun dismissReport(id: String): Result<Unit>
}

fun ModerationRepository.asActions() = object : ModerationActions {
    override val pending = observePending()
    override val reports = observeReports()
    override suspend fun approve(path: String) = this@asActions.approve(path)
    override suspend fun remove(path: String) = this@asActions.remove(path)
    override suspend fun dismissReport(id: String) = this@asActions.dismissReport(id)
}

class ModerationViewModel(private val actions: ModerationActions) : ViewModel() {
    val pending: StateFlow<List<PendingItem>> = actions.pending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reports: StateFlow<List<Report>> = actions.reports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun approve(path: String) {
        viewModelScope.launch { actions.approve(path) }
    }

    fun remove(path: String) {
        viewModelScope.launch { actions.remove(path) }
    }

    // Removing the reported post also closes the report
    fun removeReported(report: Report) {
        viewModelScope.launch {
            actions.remove(report.targetPath)
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            item(key = "top", contentType = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                    Text(stringResource(R.string.moderation_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
            }
            item(key = "pendingTitle", contentType = "title") { Text(stringResource(R.string.moderation_pending), style = MaterialTheme.typography.titleLarge) }
            if (pending.isEmpty()) item(key = "pendingEmpty", contentType = "empty") { EmptyLine() }
            items(pending, key = { it.path }, contentType = { "pending" }) { item ->
                OjasCard {
                    Text(item.author, style = MaterialTheme.typography.labelLarge)
                    Text(item.text, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { vm.approve(item.path) }) { Text(stringResource(R.string.moderation_approve)) }
                        TextButton(onClick = { vm.remove(item.path) }) { Text(stringResource(R.string.moderation_remove_post), color = MaterialTheme.colorScheme.error) }
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
                        TextButton(onClick = { vm.removeReported(report) }) { Text(stringResource(R.string.moderation_remove_post), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLine() {
    Text(stringResource(R.string.moderation_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
