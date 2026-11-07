package com.ojashealing.family.ui.goals

import android.net.Uri
import com.ojashealing.family.R
import com.ojashealing.family.data.goals.Goal
import com.ojashealing.family.data.goals.GoalKind
import com.ojashealing.family.data.goals.GoalProgress
import com.ojashealing.family.data.goals.GoalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.net.Uri
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelsTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val photo = Uri.parse("content://media/1")

    @Test fun uploadFailureKeepsUserOnScreenWithError() = runTest(dispatcher) {
        val vm = GoalSubmitViewModel { _, _ -> Result.failure(RuntimeException("upload failed")) }
        vm.onPhoto(photo)
        vm.send()
        advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.done)
        assertFalse(s.busy)
        assertEquals(R.string.error_generic, s.error)
        assertEquals(photo, s.photo) // the chosen photo stays so the member can retry
    }

    @Test fun sendWithoutPhotoAsksForOne() = runTest(dispatcher) {
        var called = false
        val vm = GoalSubmitViewModel { _, _ -> called = true; Result.success(Unit) }
        vm.send()
        advanceUntilIdle()
        assertFalse(called)
        assertEquals(R.string.goal_photo_required, vm.state.value.error)
    }

    @Test fun successfulSendPassesNoteAndFinishes() = runTest(dispatcher) {
        var sent: Pair<Uri, String>? = null
        val vm = GoalSubmitViewModel { uri, note -> sent = uri to note; Result.success(Unit) }
        vm.onPhoto(photo)
        vm.onNote("  made dal  ")
        vm.send()
        advanceUntilIdle()
        assertTrue(vm.state.value.done)
        assertEquals(photo to "made dal", sent)
    }

    private fun g(id: String, order: Int) = Goal(id, mapOf("en" to id), emptyMap(), GoalKind.Photo, 25, order, true)

    @Test fun profileGoalsSplitSuggestedWaitingAndAchieved() = runTest(dispatcher) {
        val goals = MutableStateFlow(listOf(g("a", 1), g("b", 2), g("c", 3)))
        val progress = MutableStateFlow(mapOf(
            "a" to GoalProgress("a", GoalStatus.Approved, "", 10),
            "b" to GoalProgress("b", GoalStatus.Submitted, "", 20)
        ))
        val vm = ProfileGoalsViewModel(goals, progress)
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals("c", s.suggested?.id)
        assertEquals(listOf("b"), s.waiting.map { it.id })
        assertEquals(listOf("a"), s.achieved.map { it.id })
    }

    @Test fun rejectedSuggestionIsFlagged() = runTest(dispatcher) {
        val vm = ProfileGoalsViewModel(
            MutableStateFlow(listOf(g("a", 1))),
            MutableStateFlow(mapOf("a" to GoalProgress("a", GoalStatus.Rejected, "", 5)))
        )
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(vm.state.value.suggestedWasRejected)
    }
}
