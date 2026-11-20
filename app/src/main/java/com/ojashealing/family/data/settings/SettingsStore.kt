package com.ojashealing.family.data.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Two themes (owner, 2026-11-20): [Light] is Healing Blue and [Dark] is Forest Sage. [System], for
 * people who never chose, follows the phone. [HealingBlue] and [ForestSage] are older saved choices
 * and read as Light and Dark. Stored by name, so older choices still read.
 */
enum class ThemeMode { System, Light, Dark, HealingBlue, ForestSage }

val SupportedLanguages = listOf("en", "hi", "gu")

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

interface ThemeSettings {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

// Device-local preferences. Anything other members or the server need lives in users/{uid} instead.
class SettingsStore(private val context: Context) : ThemeSettings {

    private val themeKey = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    private val introKey = booleanPreferencesKey("intro_seen")

    /** True after the 5-second intro has played on this phone. Later opens use the 3-second light. */
    val introSeen: Flow<Boolean> = context.settingsDataStore.data.map { it[introKey] == true }

    suspend fun markIntroSeen() {
        context.settingsDataStore.edit { it[introKey] = true }
    }

    private val introFaceUidKey = androidx.datastore.preferences.core.stringPreferencesKey("intro_face_uid")
    private val introFaceUrlKey = androidx.datastore.preferences.core.stringPreferencesKey("intro_face_url")

    /**
     * The member's approved face photo for the opening animation, as (uid, url). Kept on the phone
     * because the animation plays before the profile has loaded; the uid stops one account's photo
     * showing for another account on the same phone.
     */
    val introFace: Flow<Pair<String, String>> = context.settingsDataStore.data.map {
        it[introFaceUidKey].orEmpty() to it[introFaceUrlKey].orEmpty()
    }

    /** An empty [url] goes back to the plain animation. */
    suspend fun setIntroFace(uid: String, url: String) {
        context.settingsDataStore.edit { it[introFaceUidKey] = uid; it[introFaceUrlKey] = url }
    }

    private val pointsExplainedKey = booleanPreferencesKey("points_explained")

    /** False until "How Moksha Points work" has been shown once on this phone. */
    val pointsExplained: Flow<Boolean> = context.settingsDataStore.data.map { it[pointsExplainedKey] == true }

    suspend fun markPointsExplained() {
        context.settingsDataStore.edit { it[pointsExplainedKey] = true }
    }
}

// AppCompat stores the choice itself (autoStoreLocales), so it survives restarts without DataStore
fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

// Empty string means the user has not chosen yet, which sends onboarding to the language screen
fun currentAppLanguage(): String =
    AppCompatDelegate.getApplicationLocales().get(0)?.language?.takeIf { it in SupportedLanguages } ?: ""
