package com.ojashealing.family.ui.main

import android.app.Activity
import android.net.Uri
import com.google.firebase.auth.PhoneAuthProvider
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.auth.PhoneVerification
import com.ojashealing.family.data.points.PointEvent
import com.ojashealing.family.data.points.PointsRepository
import com.ojashealing.family.data.user.ProfileEdit
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.data.user.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: android.net.Uri
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoTest {

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

    private class FakeUsers(private val upload: Result<String>) : UserRepository {
        val uploads = mutableListOf<Pair<String, Uri>>()
        override fun observe(uid: String): Flow<UserProfile?> = emptyFlow()
        override suspend fun saveProfile(uid: String, edit: ProfileEdit) = Result.success(Unit)
        override suspend fun uploadAvatar(uid: String, uri: Uri) = upload.also { uploads += uid to uri }
        override suspend fun updateSettings(uid: String, fields: Map<String, Any>) = Result.success(Unit)
    }

    private class NoPoints : PointsRepository {
        override suspend fun claimDailyOpen() = Result.success(true)
        override fun observeNewEvents(uid: String): Flow<PointEvent> = emptyFlow()
        override suspend fun deleteAccount() = Result.success(Unit)
    }

    private val photo = Uri.parse("content://media/7")

    @Test fun changingPhotoUploadsItForTheSignedInMember() = runTest(dispatcher) {
        val users = FakeUsers(Result.success("https://p/a.jpg"))
        val vm = MainViewModel(FakeAuth(), users, NoPoints(), retryDelaysMs = emptyList())
        vm.changePhoto(photo)
        advanceUntilIdle()
        assertEquals(listOf("u1" to photo), users.uploads)
        assertFalse(vm.photoUploading.value)
        assertNull(vm.photoError.value)
    }

    @Test fun failedPhotoUploadSaysSo() = runTest(dispatcher) {
        val vm = MainViewModel(FakeAuth(), FakeUsers(Result.failure(RuntimeException("too big"))), NoPoints(), retryDelaysMs = emptyList())
        vm.changePhoto(photo)
        advanceUntilIdle()
        assertFalse(vm.photoUploading.value)
        assertEquals(R.string.error_photo_upload, vm.photoError.value)
    }
}
