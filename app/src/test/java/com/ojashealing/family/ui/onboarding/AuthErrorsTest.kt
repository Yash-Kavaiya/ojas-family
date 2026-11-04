package com.ojashealing.family.ui.onboarding

import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.ojashealing.family.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthErrorsTest {

    private fun invalid(code: String, msg: String = "x") = FirebaseAuthInvalidCredentialsException(code, msg)

    @Test fun offlineAndThrottled() {
        assertEquals(R.string.error_offline, authErrorFor(FirebaseNetworkException("net"), AuthStage.PhoneSend).message)
        assertEquals(R.string.error_too_many_attempts, authErrorFor(FirebaseTooManyRequestsException("quota"), AuthStage.PhoneSend).message)
    }

    @Test fun phoneErrors() {
        assertEquals(R.string.error_phone_invalid, authErrorFor(invalid("ERROR_INVALID_PHONE_NUMBER"), AuthStage.PhoneSend).message)
        assertEquals(R.string.error_otp_invalid, authErrorFor(invalid("ERROR_INVALID_VERIFICATION_CODE"), AuthStage.PhoneVerify).message)
        assertEquals(R.string.error_otp_expired, authErrorFor(invalid("ERROR_SESSION_EXPIRED"), AuthStage.PhoneVerify).message)
    }

    // Unregistered SHA-1/SHA-256 is the usual reason phone auth fails on a fresh build
    @Test fun unregisteredAppIsAConfigProblem() {
        val e = FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "This app is not authorized to use Firebase Authentication.")
        val mapped = authErrorFor(e, AuthStage.PhoneSend)
        assertEquals(R.string.error_signin_not_configured, mapped.message)
        assertTrue(mapped.configProblem)
        assertTrue(authErrorFor(FirebaseException("BILLING_NOT_ENABLED"), AuthStage.PhoneSend).configProblem)
    }

    @Test fun providerSwitchedOff() =
        assertEquals(R.string.error_signin_method_off,
            authErrorFor(FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "disabled"), AuthStage.PhoneSend).message)

    @Test fun emailErrors() {
        // With email enumeration protection, wrong password and unknown email both arrive as INVALID_CREDENTIAL
        assertEquals(R.string.error_email_wrong_password, authErrorFor(invalid("ERROR_INVALID_CREDENTIAL"), AuthStage.EmailSignIn).message)
        assertEquals(R.string.error_email_wrong_password, authErrorFor(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x"), AuthStage.EmailSignIn).message)
        assertEquals(R.string.error_account_disabled, authErrorFor(FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x"), AuthStage.EmailSignIn).message)
        assertEquals(R.string.error_email_in_use, authErrorFor(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x"), AuthStage.EmailCreate).message)
        assertEquals(R.string.error_account_other_method,
            authErrorFor(FirebaseAuthUserCollisionException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "x"), AuthStage.Google).message)
        assertEquals(R.string.error_password_weak, authErrorFor(FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "x", "short"), AuthStage.EmailCreate).message)
        assertFalse(authErrorFor(invalid("ERROR_INVALID_CREDENTIAL"), AuthStage.EmailSignIn).configProblem)
    }

    @Test fun googleErrors() {
        val none = authErrorFor(NoCredentialException(), AuthStage.Google)
        assertEquals(R.string.error_google_no_account, none.message)
        assertTrue(none.configProblem)
        val devConsole = authErrorFor(GetCredentialUnknownException("[28444] Developer console is not set up correctly."), AuthStage.Google)
        assertEquals(R.string.error_signin_not_configured, devConsole.message)
        assertEquals(R.string.error_generic, authErrorFor(GetCredentialUnknownException("boom"), AuthStage.Google).message)
    }
}
