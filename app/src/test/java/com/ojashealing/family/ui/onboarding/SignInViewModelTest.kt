package com.ojashealing.family.ui.onboarding

import android.app.Activity
import android.content.Context
import com.google.firebase.auth.PhoneAuthProvider
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.auth.PhoneVerification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: submitEmail validates with android.util.Patterns
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAuth : AuthRepository {
        override val currentUid: StateFlow<String?> = MutableStateFlow(null)
        override suspend fun signInWithGoogle(activity: Activity) = Result.success(Unit)
        override suspend fun startPhoneSignIn(activity: Activity, e164: String, resendToken: PhoneAuthProvider.ForceResendingToken?) =
            Result.success<PhoneVerification>(PhoneVerification.AutoVerified)
        override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String) = Result.success(Unit)
        override suspend fun signInWithEmail(email: String, password: String, create: Boolean) = Result.success(Unit)
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut(context: Context) {}
    }

    // The ViewModel outlives the session; the next person to sign in on this phone must not see the password
    @Test fun successfulEmailSignInClearsPassword() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth())
        vm.onEmailChange("asha@example.com")
        vm.onPasswordChange("secret-pass")
        vm.submitEmail()
        advanceUntilIdle()
        assertEquals("", vm.state.value.password)
    }
}
