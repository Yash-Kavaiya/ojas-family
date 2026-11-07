package com.ojashealing.family.data.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.ojashealing.family.R
import com.ojashealing.family.ui.onboarding.authErrorFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePickerTest {

    // Android closes the picker by itself, as a "cancel", when the APK's key or OAuth client is wrong
    @Test fun instantCancelIsASetupFailure() =
        assertTrue(isGoogleSetupFailure(GetCredentialCancellationException("activity is cancelled by the user."), elapsedMs = 300))

    @Test fun cancelAfterLookingAtThePickerIsThePersonsChoice() =
        assertFalse(isGoogleSetupFailure(GetCredentialCancellationException("cancelled"), elapsedMs = 6_000))

    @Test fun otherErrorsAreNotRelabelled() =
        assertFalse(isGoogleSetupFailure(NoCredentialException(), elapsedMs = 100))

    @Test fun setupFailureShowsAppNotVerifiedWithCode() {
        val error = authErrorFor(GoogleSetupException(GetCredentialCancellationException("x")))
        assertEquals(R.string.error_google_setup, error.message)
        assertEquals("GOOGLE_PICKER_CLOSED", error.code)
    }
}
