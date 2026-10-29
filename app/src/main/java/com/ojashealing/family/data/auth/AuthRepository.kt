package com.ojashealing.family.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

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
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        Unit
    }

    override suspend fun startPhoneSignIn(
        activity: Activity,
        e164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?
    ): Result<PhoneVerification> = runCatching {
        val outcome = suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (cont.isActive) cont.resume(Result.success<Any>(credential))
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
        }.getOrThrow()

        when (outcome) {
            is PhoneAuthCredential -> {
                auth.signInWithCredential(outcome).await()
                PhoneVerification.AutoVerified
            }
            else -> outcome as PhoneVerification.CodeSent
        }
    }

    override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String): Result<Unit> = runCatching {
        auth.signInWithCredential(PhoneAuthProvider.getCredential(verification.verificationId, code)).await()
        Unit
    }

    override suspend fun signInWithEmail(email: String, password: String, create: Boolean): Result<Unit> = runCatching {
        if (create) auth.createUserWithEmailAndPassword(email.trim(), password).await()
        else auth.signInWithEmailAndPassword(email.trim(), password).await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }

    override suspend fun signOut(context: Context) {
        auth.signOut()
        // Without this, Credential Manager would silently pick the same Google account next time
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
