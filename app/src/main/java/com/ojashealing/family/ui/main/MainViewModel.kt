package com.ojashealing.family.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.points.PointsRepository
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.data.user.UserRepository
import com.ojashealing.family.ui.components.RewardEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state for the signed-in shell: the live profile, the daily-open claim and point animations. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    auth: AuthRepository,
    users: UserRepository,
    private val points: PointsRepository,
    private val retryDelaysMs: List<Long> = listOf(2_000, 5_000, 15_000, 30_000)
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = auth.currentUid
        .flatMapLatest { uid -> if (uid == null) flowOf(null) else users.observe(uid).catch { emit(null) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _reward = MutableStateFlow<RewardEvent?>(null)
    val reward: StateFlow<RewardEvent?> = _reward.asStateFlow()

    private var claimJob: Job? = null

    init {
        // Awards (daily point now, likes and comments later) arrive from the server's pointEvents ledger
        viewModelScope.launch {
            auth.currentUid.filterNotNull()
                .flatMapLatest { uid -> points.observeNewEvents(uid).catch { } }
                .collect { e -> _reward.value = RewardEvent(e.amount, e.reason, e.milestone) }
        }
    }

    /** Called each time the app comes to the foreground; the server grants at most one point per IST day. */
    fun onAppForeground() {
        if (claimJob?.isActive == true) return
        claimJob = viewModelScope.launch {
            for (attempt in 0..retryDelaysMs.size) {
                if (points.claimDailyOpen().isSuccess) return@launch
                retryDelaysMs.getOrNull(attempt)?.let { delay(it) }
            }
        }
    }

    fun clearReward() {
        _reward.value = null
    }
}
