package com.ojashealing.family.ui.admin

import com.ojashealing.family.data.social.PendingItem
import com.ojashealing.family.data.social.Report
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeModeration : ModerationActions {
        val calls = mutableListOf<String>()
        override val pending = MutableStateFlow(listOf(PendingItem("thoughts/t/comments/c1", "see www.x.com", "Asha", 1)))
        override val reports = MutableStateFlow(listOf(Report("r1", "questions/q1", "spam", 2)))
        override suspend fun approve(path: String) = Result.success(Unit).also { calls += "approve:$path" }
        override suspend fun remove(path: String) = Result.success(Unit).also { calls += "remove:$path" }
        override suspend fun dismissReport(id: String) = Result.success(Unit).also { calls += "dismiss:$id" }
    }

    @Test fun listsPendingAndReports() = runTest(dispatcher) {
        val vm = ModerationViewModel(FakeModeration())
        val job = backgroundScope.launch { vm.pending.collect {} }
        val job2 = backgroundScope.launch { vm.reports.collect {} }
        advanceUntilIdle()
        assertEquals(1, vm.pending.value.size)
        assertEquals("questions/q1", vm.reports.value.single().targetPath)
        job.cancel(); job2.cancel()
    }

    @Test fun approveCallsRepository() = runTest(dispatcher) {
        val repo = FakeModeration()
        val vm = ModerationViewModel(repo)
        vm.approve("thoughts/t/comments/c1")
        vm.removeReported(Report("r1", "questions/q1", "spam", 2))
        advanceUntilIdle()
        assertEquals(listOf("approve:thoughts/t/comments/c1", "remove:questions/q1", "dismiss:r1"), repo.calls)
    }
}
