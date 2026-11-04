package com.ojashealing.family.ui.onboarding

import android.app.Activity
import android.content.Context
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthProvider
import com.ojashealing.family.R
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// Robolectric: submitEmail validates with android.util.Patterns
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeAuth(private val failWith: Throwable? = null) : AuthRepository {
        var sentTo: String? = null
        var emailCalls = 0
        override val currentUid: StateFlow<String?> = MutableStateFlow(null)
        override suspend fun signInWithGoogle(activity: Activity) = Result.success(Unit)
        override suspend fun startPhoneSignIn(activity: Activity, e164: String, resendToken: PhoneAuthProvider.ForceResendingToken?): Result<PhoneVerification> {
            sentTo = e164
            return failWith?.let { Result.failure(it) } ?: Result.success(PhoneVerification.AutoVerified)
        }
        override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String) = Result.success(Unit)
        override suspend fun signInWithEmail(email: String, password: String, create: Boolean): Result<Unit> {
            emailCalls++
            return failWith?.let { Result.failure(it) } ?: Result.success(Unit)
        }
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

    private val activity: Activity get() = Robolectric.buildActivity(Activity::class.java).get()

    @Test fun tenDigitNumberIsSentWithIndianCode() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("98765 43210")
        vm.sendCode(activity)
        advanceUntilIdle()
        assertEquals("+919876543210", auth.sentTo)
        assertEquals("+919876543210", vm.state.value.phone)
        assertNull(vm.state.value.error)
    }

    @Test fun shortNumberIsRejectedBeforeCallingFirebase() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("+91 98765")
        vm.sendCode(activity)
        advanceUntilIdle()
        assertNull(auth.sentTo)
        assertEquals(R.string.error_phone_invalid, vm.state.value.error)
    }

    @Test fun firebaseRejectionShowsSpecificMessage() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth(failWith = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_PHONE_NUMBER", "bad")))
        vm.onPhoneChange("9876543210")
        vm.sendCode(activity)
        advanceUntilIdle()
        assertEquals(R.string.error_phone_invalid, vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    // Accounts created elsewhere may have a 6-character password; only new passwords need 8
    @Test fun shortPasswordAllowedForSignInButNotCreate() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onEmailChange("asha@example.com")
        vm.onPasswordChange("abc123")
        vm.submitEmail()
        advanceUntilIdle()
        assertEquals(1, auth.emailCalls)

        vm.toggleCreateAccount()
        vm.onPasswordChange("abc123")
        vm.submitEmail()
        advanceUntilIdle()
        assertEquals(1, auth.emailCalls)
        assertEquals(R.string.error_password_short, vm.state.value.error)
    }

    @Test fun wrongPasswordMessage() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth(failWith = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad")))
        vm.onEmailChange("asha@example.com")
        vm.onPasswordChange("secret-pass")
        vm.submitEmail()
        advanceUntilIdle()
        assertEquals(R.string.error_email_wrong_password, vm.state.value.error)
    }
}
