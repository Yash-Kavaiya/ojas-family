package com.ojashealing.family.data.auth

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Google account picker closed by itself. Android reports this as a cancel, but it happens when the
 * APK's signing key or the Google OAuth client does not match Firebase, so it must not be swallowed.
 */
class GoogleSetupException(cause: Throwable) : Exception("Google picker closed without the user choosing", cause)

/** A cancel faster than a person could close the picker is Android refusing, not the person deciding. */
fun isGoogleSetupFailure(e: Throwable, elapsedMs: Long): Boolean =
    e is GetCredentialCancellationException && elapsedMs < INSTANT_CANCEL_MS

private const val INSTANT_CANCEL_MS = 2_000L

/** Firebase never answered (no code sent, no error), e.g. the reCAPTCHA page was closed. */
class AuthTimeoutException : Exception("Phone verification timed out")

/** Result of sending an OTP: either a code to enter, or Android verified the number on its own. */
sealed interface PhoneVerification {
    data class CodeSent(val verificationId: String, val resendToken: PhoneAuthProvider.ForceResendingToken) : PhoneVerification
    data object AutoVerified : PhoneVerification
}

interface AuthRepository {
    val currentUid: StateFlow<String?>
    suspend fun signInWithGoogle(activity: Activity): Result<Unit>
    suspend fun startPhoneSignIn(
        activity: Activity,
        e164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ): Result<PhoneVerification>
    suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String): Result<Unit>
    suspend fun signInWithEmail(email: String, password: String, create: Boolean): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun signOut(context: Context)
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val webClientId: String
) : AuthRepository {

    private val uid = MutableStateFlow(auth.currentUser?.uid)
    override val currentUid: StateFlow<String?> = uid.asStateFlow()

    init {
        auth.addAuthStateListener { uid.value = it.currentUser?.uid }
    }

    override suspend fun signInWithGoogle(activity: Activity): Result<Unit> = runCatching {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val started = SystemClock.elapsedRealtime()
        val response = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            if (isGoogleSetupFailure(e, SystemClock.elapsedRealtime() - started)) throw GoogleSetupException(e) else throw e
        }
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }.onFailure { report("google", it) }

    override suspend fun startPhoneSignIn(
        activity: Activity,
        e164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?
    ): Result<PhoneVerification> = runCatching {
        // SMS text in the language the app is shown in
        auth.useAppLanguage()
        val outcome = withTimeoutOrNull(PHONE_ANSWER_TIMEOUT_MS) {
            suspendCancellableCoroutine<Result<Any>> { cont ->
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        if (cont.isActive) cont.resume(Result.success<Any>(credential))
                        // Android read the SMS after the code screen was shown: sign in without waiting for typing
                        else auth.signInWithCredential(credential).addOnFailureListener { report("phone auto-retrieval", it) }
                    }

                    override fun onVerificationFailed(e: FirebaseException) {
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }

                    override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                        if (cont.isActive) cont.resume(Result.success<Any>(PhoneVerification.CodeSent(id, token)))
                    }
                }
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(e164)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .apply { resendToken?.let { setForceResendingToken(it) } }
                    .build()
                PhoneAuthProvider.verifyPhoneNumber(options)
            }
        } ?: throw AuthTimeoutException()

        when (val value = outcome.getOrThrow()) {
            is PhoneAuthCredential -> {
                auth.signInWithCredential(value).await()
                PhoneVerification.AutoVerified
            }
            else -> value as PhoneVerification.CodeSent
        }
    }.onFailure { report("phone start", it) }

    override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String): Result<Unit> = runCatching {
        auth.signInWithCredential(PhoneAuthProvider.getCredential(verification.verificationId, code)).await()
        Unit
    }.onFailure { report("phone confirm", it) }

    override suspend fun signInWithEmail(email: String, password: String, create: Boolean): Result<Unit> = runCatching {
        if (create) auth.createUserWithEmailAndPassword(email.trim(), password).await()
        else auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }.onFailure { report(if (create) "email create" else "email sign-in", it) }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }.onFailure { report("password reset", it) }

    override suspend fun signOut(context: Context) {
        auth.signOut()
        // Without this, Credential Manager would silently pick the same Google account next time
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private companion object {
        const val TAG = "OjasAuth"
        // Firebase answers within seconds; this only stops the button spinning forever if it never does
        const val PHONE_ANSWER_TIMEOUT_MS = 120_000L

        /**
         * Logs failures that point at configuration (missing SHA keys, provider switched off, quota)
         * to Crashlytics, so production sign-in problems are visible without a user report.
         * Mistakes people make themselves (wrong password, bad OTP, offline, closing the picker) are not sent.
         */
        fun report(step: String, e: Throwable) {
            Log.w(TAG, "Sign-in failed at $step", e)
            val userMistake = e is FirebaseNetworkException || e is GetCredentialCancellationException ||
                e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException ||
                e is FirebaseAuthUserCollisionException || e is FirebaseAuthWeakPasswordException
            if (!userMistake) runCatching {
                FirebaseCrashlytics.getInstance().apply {
                    log("auth step: $step")
                    recordException(e)
                }
            }
        }
    }
}
