package com.ojashealing.family.ui.onboarding

import android.app.Activity
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.google.firebase.auth.PhoneAuthProvider
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.auth.PhoneVerification
import com.ojashealing.family.data.location.LocationRepository
import com.ojashealing.family.data.user.ProfileEdit
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.data.user.UserRepository
import com.ojashealing.family.ui.theme.OjasFamilyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the real form a new member lands on after signing in. A Compose version mismatch once made
 * this screen crash on open (NoSuchMethodError in FlowRow), which closed the app right after Google sign-in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class ProfileSetupScreenTest {

    @get:Rule val compose = createComposeRule()

    private object Auth : AuthRepository {
        override val currentUid: StateFlow<String?> = MutableStateFlow("u1")
        override suspend fun signInWithGoogle(activity: Activity) = Result.success(Unit)
        override suspend fun startPhoneSignIn(activity: Activity, e164: String, resendToken: PhoneAuthProvider.ForceResendingToken?) =
            Result.success<PhoneVerification>(PhoneVerification.AutoVerified)
        override suspend fun confirmPhoneCode(verification: PhoneVerification.CodeSent, code: String) = Result.success(Unit)
        override suspend fun signInWithEmail(email: String, password: String, create: Boolean) = Result.success(Unit)
        override suspend fun sendPasswordReset(email: String) = Result.success(Unit)
        override suspend fun signOut(context: android.content.Context) {}
    }

    private object Users : UserRepository {
        override fun observe(uid: String): Flow<UserProfile?> = flowOf(null)
        override suspend fun saveProfile(uid: String, edit: ProfileEdit) = Result.success(Unit)
        override suspend fun uploadAvatar(uid: String, uri: Uri) = Result.success("")
        override suspend fun updateSettings(uid: String, fields: Map<String, Any>) = Result.success(Unit)
    }

    private val locations = LocationRepository { name -> if (name.endsWith("countries.json")) "[]" else "{}" }

    @Test fun formOpensWithMaleAndFemaleOnly() {
        val vm = ProfileSetupViewModel(Auth, Users, locations, "Asha")
        compose.setContent { OjasFamilyTheme { ProfileSetupScreen(vm) } }
        compose.onNodeWithText("Male").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Female").performScrollTo().assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Other").fetchSemanticsNodes().size)
    }
}
