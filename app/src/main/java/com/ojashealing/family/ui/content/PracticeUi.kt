package com.ojashealing.family.ui.content

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.data.practice.PracticeKind
import com.ojashealing.family.data.practice.PracticeRepository
import com.ojashealing.family.data.practice.PracticeStatus
import com.ojashealing.family.data.practice.practiceStatus
import com.ojashealing.family.data.social.todayIst
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun ContentSection.practiceKind(): PracticeKind? = when (this) {
    ContentSection.Morning -> PracticeKind.Morning
    ContentSection.Night -> PracticeKind.Night
    else -> null
}

/** Morning / night practice log and 21-day streaks; null status = practices locked for this viewer. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeViewModel(private val repo: PracticeRepository, profile: Flow<UserProfile?>) : ViewModel() {

    val status: StateFlow<PracticeStatus?> = profile.filterNotNull().flatMapLatest { p ->
        if (!Permissions.canDoDailyPractices(p.viewer)) flowOf(null)
        else repo.observeLogs(p.uid, todayIst()).map { practiceStatus(it, todayIst()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<Int?>(null)
    @StringRes val message: StateFlow<Int?> = _message
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun mark(kind: PracticeKind) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            repo.log(kind)
                .onSuccess { done -> _message.value = if (done.isNotEmpty()) R.string.practice_goal_done else R.string.practice_logged }
                .onFailure { _message.value = R.string.error_generic }
            _busy.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

/** Streak and "I did it" button shown with the morning and night sections and under their videos. */
@Composable
fun PracticeBar(kind: PracticeKind, status: PracticeStatus, busy: Boolean, onMark: () -> Unit, modifier: Modifier = Modifier) {
    val doneToday = if (kind == PracticeKind.Morning) status.morningToday else status.nightToday
    val streak = if (kind == PracticeKind.Morning) status.morningStreak else status.bothStreak
    Row(modifier.padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (streak > 0) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.practice_streak, streak), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        } else {
            Text(stringResource(R.string.practice_streak_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
        if (doneToday) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.practice_done_today), style = MaterialTheme.typography.labelLarge)
        } else {
            Button(onClick = onMark, enabled = !busy) {
                Text(stringResource(if (kind == PracticeKind.Morning) R.string.practice_mark_morning else R.string.practice_mark_night), maxLines = 2)
            }
        }
    }
}
