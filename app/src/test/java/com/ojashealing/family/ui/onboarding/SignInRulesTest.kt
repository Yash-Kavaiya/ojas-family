package com.ojashealing.family.ui.onboarding

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.AuthTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInRulesTest {

    // What people actually type into the field, which starts as "+91"
    @Test fun normalizesCommonIndianInputs() {
        listOf(
            "+919876543210",
            "+91 98765 43210",
            "9876543210",
            "09876543210",
            "919876543210",
            "00919876543210",
            "+9109876543210",      // trunk zero after the country code
            "+91919876543210",     // "+91" prefix, then "91…" typed again
            "+91+919876543210",    // "+91" prefix, then a full number pasted
            "+91-98765-43210",
        ).forEach { assertEquals(it, "+919876543210", normalizePhone(sanitizePhoneInput(it))) }
    }

    @Test fun keepsInternationalNumbers() {
        assertEquals("+14155552671", normalizePhone("+1 (415) 555-2671"))
        assertEquals("+447911123456", normalizePhone("+44 7911 123456"))
        assertTrue(isValidPhone("+14155552671"))
    }

    @Test fun rejectsIncompleteOrNonMobileIndianNumbers() {
        assertFalse(isValidPhone(normalizePhone("+91")))
        assertFalse(isValidPhone(normalizePhone("98765")))
        assertFalse(isValidPhone(normalizePhone("+91 12345 67890"))) // Indian mobiles start 6–9
        assertFalse(isValidPhone(normalizePhone("+91987654321012")))
        assertTrue(isValidPhone(normalizePhone("9876543210")))
    }

    @Test fun sanitizeKeepsOnlyDigitsAndLeadingPlus() {
        assertEquals("+919876543210", sanitizePhoneInput("+91 98765-43210"))
        assertEquals("9876543210", sanitizePhoneInput("98765 43210"))
        assertEquals("+919876543210", sanitizePhoneInput("+91+919876543210").let { normalizePhone(it) })
    }

    @Test fun emailValidation() {
        assertTrue(isValidEmail("asha@example.com"))
        assertTrue(isValidEmail("  Asha.Patel+ojas@mail.co.in "))
        assertFalse(isValidEmail("asha@"))
        assertFalse(isValidEmail("asha example.com"))
        assertFalse(isValidEmail("asha@example"))
        assertFalse(isValidEmail(""))
    }

    @Test fun wrongPasswordOrMissingAccountSaysSo() {
        // Email enumeration protection returns INVALID_CREDENTIAL for both
        val e = authErrorFor(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad"))
        assertEquals(R.string.error_wrong_credentials, e.message)
        assertEquals(ErrorField.Password, e.field)
        assertEquals(R.string.error_wrong_credentials, authErrorFor(FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x")).message)
    }

    @Test fun badOtpBelongsUnderTheCode() {
        val e = authErrorFor(FirebaseAuthInvalidCredentialsException("ERROR_INVALID_VERIFICATION_CODE", "x"), PhoneStep.Code)
        assertEquals(AuthError(R.string.error_otp_invalid, ErrorField.Code), e)
        assertEquals(R.string.error_otp_expired, authErrorFor(FirebaseAuthException("ERROR_SESSION_EXPIRED", "x"), PhoneStep.Code).message)
    }

    @Test fun existingEmailIsNotAGenericError() {
        val e = authErrorFor(FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x"))
        assertEquals(AuthError(R.string.error_email_in_use, ErrorField.Email), e)
    }

    // These are configuration problems; the code is shown so a user report pins down the cause
    @Test fun configurationProblemsCarryTheirCode() {
        val notAuthorized = authErrorFor(FirebaseAuthException("ERROR_APP_NOT_AUTHORIZED", "x"), PhoneStep.Number)
        assertEquals(R.string.error_app_not_verified, notAuthorized.message)
        assertEquals("ERROR_APP_NOT_AUTHORIZED", notAuthorized.code)
        assertEquals(R.string.error_signin_method_disabled, authErrorFor(FirebaseAuthException("ERROR_OPERATION_NOT_ALLOWED", "x")).message)
        assertEquals(R.string.error_signin_method_disabled, authErrorFor(FirebaseAuthException("ERROR_INTERNAL_ERROR", "BILLING_NOT_ENABLED")).message)
        val unknown = authErrorFor(FirebaseAuthException("ERROR_SOMETHING_NEW", "x"))
        assertEquals(AuthError(R.string.error_with_code, null, "ERROR_SOMETHING_NEW"), unknown)
    }

    @Test fun networkRateLimitAndTimeout() {
        assertEquals(R.string.error_offline, authErrorFor(FirebaseNetworkException("x")).message)
        assertEquals(R.string.error_too_many_attempts, authErrorFor(FirebaseTooManyRequestsException("x")).message)
        assertEquals("TIMEOUT", authErrorFor(AuthTimeoutException(), PhoneStep.Number).code)
    }
}
