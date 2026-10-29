package com.ojashealing.family.ui.settings

import com.ojashealing.family.data.settings.ThemeMode
import com.ojashealing.family.data.settings.ThemeSettings
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeTheme : ThemeSettings {
        override val themeMode = MutableStateFlow(ThemeMode.System)
        override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
    }

    private class Recorder {
        val writes = mutableListOf<Map<String, Any>>()
        var language = "en"
        var signedOut = false
        var deleteResult: Result<Unit> = Result.success(Unit)
    }

    private fun vm(r: Recorder, theme: ThemeSettings = FakeTheme(), prefs: Map<String, Boolean> = emptyMap()) = SettingsViewModel(
        profile = flowOf(SettingsProfile(hideProfile = false, hideGoals = false, notificationPrefs = prefs, morningReminder = "06:00", nightReminder = "21:30")),
        theme = theme,
        writeSettings = { fields -> r.writes += fields; Result.success(Unit) },
        deleteAccount = { r.deleteResult },
        signOut = { r.signedOut = true },
        setLanguage = { r.language = it },
        currentLanguage = { r.language }
    )

    @Test fun masterSwitchOffDisablesAllCategoriesInUi() = runTest(dispatcher) {
        val r = Recorder()
        val vm = vm(r, prefs = mapOf("all" to false, "announcements" to true))
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.categoriesEnabled)
        // The category's own value is kept, so turning the master switch back on restores it
        assertTrue(state.isOn("announcements"))
    }

    @Test fun togglingCategoryWritesMergedPrefs() = runTest(dispatcher) {
        val r = Recorder()
        val vm = vm(r, prefs = mapOf("announcements" to true))
        advanceUntilIdle()
        vm.setNotification("thoughtOfDay", false)
        advanceUntilIdle()
        @Suppress("UNCHECKED_CAST")
        val written = r.writes.last()["notificationPrefs"] as Map<String, Boolean>
        assertEquals(false, written["thoughtOfDay"])
        assertEquals(true, written["announcements"])
    }

    @Test fun themeChangePersists() = runTest(dispatcher) {
        val theme = FakeTheme()
        val vm = vm(Recorder(), theme)
        vm.setTheme(ThemeMode.Dark)
        advanceUntilIdle()
        assertEquals(ThemeMode.Dark, theme.themeMode.value)
        assertEquals(ThemeMode.Dark, vm.state.value.themeMode)
    }

    @Test fun languageChangeCallsSetAppLanguage() = runTest(dispatcher) {
        val r = Recorder()
        vm(r).setLanguage("gu")
        assertEquals("gu", r.language)
    }

    @Test fun deleteAccountSignsOutAfterSuccess() = runTest(dispatcher) {
        val r = Recorder()
        val vm = vm(r)
        vm.deleteAccount()
        advanceUntilIdle()
        assertTrue(r.signedOut)
    }

    @Test fun deleteAccountShowsErrorAndStaysSignedInOnFailure() = runTest(dispatcher) {
        val r = Recorder().apply { deleteResult = Result.failure(IOException("offline")) }
        val vm = vm(r)
        vm.deleteAccount()
        advanceUntilIdle()
        assertFalse(r.signedOut)
        assertNotNull(vm.state.value.deleteError)
        assertFalse(vm.state.value.deleting)
    }
}
