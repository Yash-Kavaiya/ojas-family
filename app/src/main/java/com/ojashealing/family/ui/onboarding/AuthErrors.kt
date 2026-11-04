package com.ojashealing.family.ui.onboarding

import androidx.annotation.StringRes
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
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

enum class AuthStage { Google, PhoneSend, PhoneVerify, EmailSignIn, EmailCreate, PasswordReset }

/**
 * What to tell the person, and whether the failure is the app's configuration rather than their input
 * (unregistered signing key, provider switched off, App Check). [configProblem] lets debug builds show
 * the signing fingerprint that needs registering in Firebase.
 */
data class AuthError(@StringRes val message: Int, val configProblem: Boolean = false)

private val CONFIG_MARKERS = listOf(
    "28444", "developer console", "app_not_authorized", "not authorized to use firebase",
    "invalid_app_credential", "missing_client_identifier", "app-check", "appcheck", "play integrity",
    "invalid-cert-hash", "sha-1", "sha1", "api key not valid", "billing_not_enabled"
)

private fun Throwable.mentionsConfig(): Boolean {
    val text = listOfNotNull(message, (this as? FirebaseAuthException)?.errorCode, cause?.message)
        .joinToString(" ").lowercase()
    return CONFIG_MARKERS.any { it in text }
}

fun authErrorFor(e: Throwable, stage: AuthStage): AuthError {
    val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
    return when {
        e is FirebaseNetworkException -> AuthError(R.string.error_offline)
        e is FirebaseTooManyRequestsException -> AuthError(R.string.error_too_many_attempts)

        // Credential Manager (Google)
        e is NoCredentialException -> AuthError(R.string.error_google_no_account, configProblem = true)
        e is GetCredentialProviderConfigurationException || e is GetCredentialUnsupportedException ->
            AuthError(R.string.error_signin_not_configured, configProblem = true)
        e is GetCredentialException ->
            if (e.mentionsConfig()) AuthError(R.string.error_signin_not_configured, configProblem = true) else AuthError(R.string.error_generic)

        // Order matters: the weak-password exception is a subclass of invalid-credentials
        e is FirebaseAuthWeakPasswordException -> AuthError(R.string.error_password_weak)
        e is FirebaseAuthUserCollisionException -> AuthError(
            if (code == "ERROR_EMAIL_ALREADY_IN_USE") R.string.error_email_in_use else R.string.error_account_other_method
        )
        e is FirebaseAuthInvalidUserException -> AuthError(
            if (code == "ERROR_USER_DISABLED") R.string.error_account_disabled
            else if (stage == AuthStage.PasswordReset) R.string.error_email_invalid
            else R.string.error_email_wrong_password
        )
        e is FirebaseAuthInvalidCredentialsException -> when {
            code == "ERROR_INVALID_PHONE_NUMBER" -> AuthError(R.string.error_phone_invalid)
            code == "ERROR_SESSION_EXPIRED" -> AuthError(R.string.error_otp_expired)
            code == "ERROR_INVALID_EMAIL" -> AuthError(R.string.error_email_invalid)
            e.mentionsConfig() -> AuthError(R.string.error_signin_not_configured, configProblem = true)
            stage == AuthStage.PhoneVerify -> AuthError(R.string.error_otp_invalid)
            stage == AuthStage.PhoneSend -> AuthError(R.string.error_phone_rejected)
            stage == AuthStage.EmailSignIn -> AuthError(R.string.error_email_wrong_password)
            else -> AuthError(R.string.error_generic)
        }
        code == "ERROR_OPERATION_NOT_ALLOWED" -> AuthError(R.string.error_signin_method_off, configProblem = true)
        e is FirebaseException && e.mentionsConfig() -> AuthError(R.string.error_signin_not_configured, configProblem = true)
        else -> AuthError(R.string.error_generic)
    }
}
