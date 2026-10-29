package com.ojashealing.family.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.ojashealing.family.R
import com.ojashealing.family.data.settings.ThemeMode
import com.ojashealing.family.data.settings.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Keys stored in users/{uid}.notificationPrefs; Cloud Functions read the same keys before sending
object NotificationKeys {
    const val ALL = "all"
    val categories = listOf(
        "announcements" to R.string.notif_announcements,
        "thoughtOfDay" to R.string.notif_thought,
        "communityPosts" to R.string.notif_community_posts,
        "communityInteractions" to R.string.notif_community_interactions,
        "communityMentions" to R.string.notif_community_mentions,
        "dailyPosts" to R.string.notif_daily_posts,
        "dailyReplies" to R.string.notif_daily_replies,
        "dailyMentions" to R.string.notif_daily_mentions
    )
}

/** The slice of the user profile the settings screen reads. */
data class SettingsProfile(
    val hideProfile: Boolean,
    val hideGoals: Boolean,
    val notificationPrefs: Map<String, Boolean>,
    val morningReminder: String,
    val nightReminder: String
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: String = "en",
    val hideProfile: Boolean = false,
    val hideGoals: Boolean = false,
    val notificationPrefs: Map<String, Boolean> = emptyMap(),
    val morningReminder: String = "06:00",
    val nightReminder: String = "21:30",
    val deleting: Boolean = false,
    @StringRes val deleteError: Int? = null
) {
    // Every notification is on until the user turns it off
    fun isOn(key: String): Boolean = notificationPrefs[key] ?: true
    val categoriesEnabled: Boolean get() = isOn(NotificationKeys.ALL)
}

class SettingsViewModel(
    profile: Flow<SettingsProfile?>,
    private val theme: ThemeSettings,
    private val writeSettings: suspend (Map<String, Any>) -> Result<Unit>,
    private val deleteAccount: suspend () -> Result<Unit>,
    private val signOut: suspend () -> Unit,
    private val setLanguage: (String) -> Unit,
    currentLanguage: () -> String
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(language = currentLanguage().ifEmpty { "en" }))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { theme.themeMode.collect { mode -> _state.update { it.copy(themeMode = mode) } } }
        viewModelScope.launch {
            profile.collect { p ->
                if (p != null) _state.update {
                    it.copy(
                        hideProfile = p.hideProfile,
                        hideGoals = p.hideGoals,
                        notificationPrefs = p.notificationPrefs,
                        morningReminder = p.morningReminder,
                        nightReminder = p.nightReminder
                    )
                }
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { theme.setThemeMode(mode) }
    }

    fun setLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        setLanguage.invoke(tag)
        viewModelScope.launch { writeSettings(mapOf("language" to tag)) }
    }

    fun setHideProfile(hidden: Boolean) = write(mapOf("hideProfile" to hidden)) { it.copy(hideProfile = hidden) }

    fun setHideGoals(hidden: Boolean) = write(mapOf("hideGoals" to hidden)) { it.copy(hideGoals = hidden) }

    fun setNotification(key: String, on: Boolean) {
        val prefs = _state.value.notificationPrefs + (key to on)
        write(mapOf("notificationPrefs" to prefs)) { it.copy(notificationPrefs = prefs) }
    }

    fun setReminder(morning: Boolean, hhmm: String) {
        val field = if (morning) "morningReminder" else "nightReminder"
        write(mapOf(field to hhmm)) { if (morning) it.copy(morningReminder = hhmm) else it.copy(nightReminder = hhmm) }
    }

    fun deleteAccount() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            deleteAccount.invoke()
                .onSuccess { signOut() }
                .onFailure { e ->
                    _state.update {
                        it.copy(deleting = false, deleteError = if (e is FirebaseNetworkException) R.string.error_offline else R.string.error_generic)
                    }
                }
        }
    }

    fun signOutNow() {
        viewModelScope.launch { signOut() }
    }

    // Optimistic: the switch moves at once; the Firestore snapshot confirms or corrects it
    private fun write(fields: Map<String, Any>, local: (SettingsUiState) -> SettingsUiState) {
        _state.update(local)
        viewModelScope.launch { writeSettings(fields) }
    }
}
