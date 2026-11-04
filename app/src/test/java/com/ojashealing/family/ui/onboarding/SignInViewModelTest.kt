package com.ojashealing.family.ui.onboarding

import android.app.Activity
import android.content.Context
import com.google.firebase.auth.FirebaseAuthException
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
import org.junit.Assert.assertNotNull
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

    private class FakeAuth(
        val emailResult: Result<Unit> = Result.success(Unit),
        val phoneResult: Result<PhoneVerification> = Result.success(PhoneVerification.AutoVerified)
    ) : AuthRepository {
        var sentTo: String? = null
        var emailUsed: String? = null
        override val currentUid: StateFlow<String?> = MutableStateFlow(null)
        override suspend fun signInWithGoogle(activity: Activity) = Result.success(Unit)
        override suspend fun startPhoneSignIn(activity: Activity, e164: String, resendToken: PhoneAuthProvider.ForceResendingToken?): Result<PhoneVerification> {
            sentTo = e164
            return phoneResult
        }
        override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String) = Result.success(Unit)
        override suspend fun signInWithEmail(email: String, password: String, create: Boolean): Result<Unit> {
            emailUsed = email
            return emailResult
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

    private val activity: Activity by lazy { Robolectric.buildActivity(Activity::class.java).get() }

    // The field starts as "+91"; people type their 10 digits after it, or clear it and type them alone
    @Test fun phoneTypedWithoutCountryCodeIsSentAsE164() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("98765 43210")
        vm.sendCode(activity)
        advanceUntilIdle()
        assertEquals("+919876543210", auth.sentTo)
        assertNull(vm.state.value.error)
    }

    @Test fun pastedNumberAfterPrefixIsSentOnce() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange(vm.state.value.phone + "+91 98765 43210")
        vm.sendCode(activity)
        advanceUntilIdle()
        assertEquals("+919876543210", auth.sentTo)
    }

    @Test fun shortPhoneShowsErrorUnderPhoneWithoutCallingFirebase() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onPhoneChange("+91 98765")
        vm.sendCode(activity)
        advanceUntilIdle()
        assertNull(auth.sentTo)
        assertEquals(AuthError(R.string.error_phone_invalid, ErrorField.Phone), vm.state.value.error)
    }

    @Test fun phoneFailureFromFirebaseIsShownWithItsCode() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth(phoneResult = Result.failure(FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "x"))))
        vm.onPhoneChange("9876543210")
        vm.sendCode(activity)
        advanceUntilIdle()
        val error = vm.state.value.error!!
        assertEquals(R.string.error_app_not_verified, error.message)
        assertEquals("ERROR_APP_NOT_AUTHORIZED", error.code)
        assertFalse(vm.state.value.busy)
    }

    @Test fun emailIsTrimmedBeforeSignIn() = runTest(dispatcher) {
        val auth = FakeAuth()
        val vm = SignInViewModel(auth)
        vm.onEmailChange(" asha@example.com ")
        vm.onPasswordChange("secret-pass")
        vm.submitEmail()
        advanceUntilIdle()
        assertEquals("asha@example.com", auth.emailUsed)
    }

    @Test fun invalidEmailErrorBelongsToEmailField() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth())
        vm.onEmailChange("asha@")
        vm.onPasswordChange("secret-pass")
        vm.submitEmail()
        assertEquals(ErrorField.Email, vm.state.value.error?.field)
        assertEquals(vm.state.value.error, vm.state.value.errorFor(ErrorField.Email))
        assertNull(vm.state.value.errorFor(ErrorField.Password, fallback = true))
    }

    @Test fun wrongPasswordSaysSoInsteadOfSomethingWentWrong() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth(emailResult = Result.failure(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "x"))))
        vm.onEmailChange("asha@example.com")
        vm.onPasswordChange("secret-pass")
        vm.submitEmail()
        advanceUntilIdle()
        assertEquals(AuthError(R.string.error_wrong_credentials, ErrorField.Password), vm.state.value.error)
    }

    @Test fun errorIsClearedWhenLeavingTheScreen() = runTest(dispatcher) {
        val vm = SignInViewModel(FakeAuth())
        vm.onEmailChange("bad")
        vm.submitEmail()
        assertNotNull(vm.state.value.error)
        vm.clearError()
        assertNull(vm.state.value.error)
    }
}
