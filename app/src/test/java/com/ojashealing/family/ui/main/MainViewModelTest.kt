package com.ojashealing.family.ui.main

import android.app.Activity
import android.net.Uri
import com.google.firebase.auth.PhoneAuthProvider
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.auth.PhoneVerification
import com.ojashealing.family.data.points.PointEvent
import com.ojashealing.family.data.points.PointsRepository
import com.ojashealing.family.data.user.ProfileEdit
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.data.user.UserRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAuth : AuthRepository {
        override val currentUid: StateFlow<String?> = MutableStateFlow("u1")
        override suspend fun signInWithGoogle(activity: Activity) = Result.success(Unit)
        override suspend fun startPhoneSignIn(activity: Activity, e164: String, resendToken: PhoneAuthProvider.ForceResendingToken?) =
            Result.success<PhoneVerification>(PhoneVerification.AutoVerified)
        override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String) = Result.success(Unit)
        override suspend fun signInWithEmail(email: String, password: String, create: Boolean) = Result.success(Unit)
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut(context: android.content.Context) {}
    }

    private class FakeUsers : UserRepository {
        override fun observe(uid: String): Flow<UserProfile?> = emptyFlow()
        override suspend fun saveProfile(uid: String, edit: ProfileEdit) = Result.success(Unit)
        override suspend fun uploadAvatar(uid: String, uri: Uri) = Result.success("")
        override suspend fun updateSettings(uid: String, fields: Map<String, Any>) = Result.success(Unit)
    }

    private class FakePoints(private val failuresBeforeSuccess: Int = 0) : PointsRepository {
        var calls = 0
        val events = MutableSharedFlow<PointEvent>()
        override suspend fun claimDailyOpen(): Result<Boolean> {
            calls++
            return if (calls <= failuresBeforeSuccess) Result.failure(IOException("offline")) else Result.success(true)
        }
        override fun observeNewEvents(uid: String): Flow<PointEvent> = events
        override suspend fun deleteAccount() = Result.success(Unit)
    }

    @Test fun claimsDailyOpenOnStart() = runTest(dispatcher) {
        val points = FakePoints()
        val vm = MainViewModel(FakeAuth(), FakeUsers(), points, retryDelaysMs = listOf(10))
        vm.onAppForeground()
        advanceUntilIdle()
        assertEquals(1, points.calls)
    }

    @Test fun retriesDailyOpenAfterNetworkError() = runTest(dispatcher) {
        val points = FakePoints(failuresBeforeSuccess = 1)
        val vm = MainViewModel(FakeAuth(), FakeUsers(), points, retryDelaysMs = listOf(10, 20))
        vm.onAppForeground()
        advanceUntilIdle()
        assertEquals(2, points.calls)
    }

    @Test fun foregroundWhileClaimRunningDoesNotDoubleCall() = runTest(dispatcher) {
        val points = FakePoints()
        val vm = MainViewModel(FakeAuth(), FakeUsers(), points, retryDelaysMs = listOf(10))
        vm.onAppForeground()
        vm.onAppForeground()
        advanceUntilIdle()
        assertEquals(1, points.calls)
    }

    @Test fun rewardEmittedFromPointEvents() = runTest(dispatcher) {
        val points = FakePoints()
        val vm = MainViewModel(FakeAuth(), FakeUsers(), points, retryDelaysMs = listOf(10))
        advanceUntilIdle()
        points.events.emit(PointEvent(amount = 1, reason = "daily", milestone = true))
        advanceUntilIdle()
        val reward = vm.reward.value!!
        assertEquals(1, reward.amount)
        assertTrue(reward.isMilestone)
        vm.clearReward()
        assertEquals(null, vm.reward.value)
    }
}
