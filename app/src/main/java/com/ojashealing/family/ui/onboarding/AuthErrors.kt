package com.ojashealing.family.ui.onboarding

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.AuthTimeoutException
import com.ojashealing.family.data.auth.GoogleSetupException

/** Which input a sign-in error belongs under. */
enum class ErrorField { Email, Password, Phone, Code }

/**
 * A sign-in failure as the person should see it. [code] is the Firebase error code, shown only when
 * there is no specific message, so a report from the field still says what actually failed.
 */
data class AuthError(@StringRes val message: Int, val field: ErrorField? = null, val code: String? = null)

/** Maps Firebase Auth failures to messages people can act on, instead of "Something went wrong". */
fun authErrorFor(e: Throwable, phoneStep: PhoneStep? = null): AuthError {
    val codeField = if (phoneStep == PhoneStep.Code) ErrorField.Code else ErrorField.Phone
    return when (e) {
        is FirebaseNetworkException -> AuthError(R.string.error_offline)
        is FirebaseTooManyRequestsException -> AuthError(R.string.error_too_many_attempts)
        is GoogleSetupException -> AuthError(R.string.error_google_setup, null, "GOOGLE_PICKER_CLOSED")
        is AuthTimeoutException -> AuthError(R.string.error_with_code, phoneStep?.let { codeField }, "TIMEOUT")
        is FirebaseAuthWeakPasswordException -> AuthError(R.string.error_password_weak, ErrorField.Password)
        is FirebaseAuthUserCollisionException -> AuthError(R.string.error_email_in_use, ErrorField.Email)
        is FirebaseAuthException -> byCode(e.errorCode, e.message.orEmpty(), phoneStep, codeField)
        else -> AuthError(R.string.error_with_code, null, e.javaClass.simpleName)
    }
}

enum class PhoneStep { Number, Code }

private fun byCode(code: String, message: String, phoneStep: PhoneStep?, codeField: ErrorField): AuthError = when (code) {
    "ERROR_INVALID_EMAIL" -> AuthError(R.string.error_email_invalid, ErrorField.Email)
    // With email enumeration protection (on by default) a missing account also comes back as INVALID_CREDENTIAL
    "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> AuthError(R.string.error_wrong_credentials, ErrorField.Password)
    "ERROR_INVALID_CREDENTIAL" ->
        if (phoneStep != null) AuthError(R.string.error_otp_invalid, codeField)
        else AuthError(R.string.error_wrong_credentials, ErrorField.Password)
    "ERROR_USER_DISABLED" -> AuthError(R.string.error_account_disabled)
    "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
        AuthError(R.string.error_email_in_use, ErrorField.Email)
    "ERROR_WEAK_PASSWORD" -> AuthError(R.string.error_password_weak, ErrorField.Password)
    "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" -> AuthError(R.string.error_too_many_attempts, phoneStep?.let { codeField })
    "ERROR_INVALID_PHONE_NUMBER", "ERROR_MISSING_PHONE_NUMBER" -> AuthError(R.string.error_phone_invalid, ErrorField.Phone)
    "ERROR_INVALID_VERIFICATION_CODE", "ERROR_MISSING_VERIFICATION_CODE" -> AuthError(R.string.error_otp_invalid, ErrorField.Code)
    "ERROR_SESSION_EXPIRED", "ERROR_INVALID_VERIFICATION_ID" -> AuthError(R.string.error_otp_expired, ErrorField.Code)
    // Provider switched off in the Firebase console, or SMS needs the Blaze plan
    "ERROR_OPERATION_NOT_ALLOWED" -> AuthError(R.string.error_signin_method_disabled, null, code)
    // Release signing key's SHA-1/SHA-256 missing in Firebase, or Play Integrity / reCAPTCHA failed
    "ERROR_APP_NOT_AUTHORIZED", "ERROR_MISSING_CLIENT_IDENTIFIER", "ERROR_INVALID_APP_CREDENTIAL",
    "ERROR_MISSING_APP_CREDENTIAL", "ERROR_INVALID_CERT_HASH", "ERROR_WEB_CONTEXT_CANCELED" ->
        AuthError(R.string.error_app_not_verified, phoneStep?.let { codeField }, code)
    else ->
        if (message.contains("BILLING_NOT_ENABLED")) AuthError(R.string.error_signin_method_disabled, null, "BILLING_NOT_ENABLED")
        else AuthError(R.string.error_with_code, phoneStep?.let { codeField }, code)
}
