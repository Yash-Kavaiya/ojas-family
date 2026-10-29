package com.ojashealing.family.ui.onboarding

import com.ojashealing.family.R
import com.ojashealing.family.domain.LocalDateLike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {

    @Test fun startIsLanguageWhenNoLanguageChosen() =
        assertEquals(StartDestination.Language, startDestination(languageChosen = false, signedIn = true, profileComplete = true))

    @Test fun startIsSignInWhenSignedOut() =
        assertEquals(StartDestination.SignIn, startDestination(languageChosen = true, signedIn = false, profileComplete = null))

    @Test fun startIsProfileSetupWhenProfileIncomplete() =
        assertEquals(StartDestination.ProfileSetup, startDestination(languageChosen = true, signedIn = true, profileComplete = false))

    @Test fun startIsMainWhenEverythingDone() =
        assertEquals(StartDestination.Main, startDestination(languageChosen = true, signedIn = true, profileComplete = true))

    // Profile not loaded yet (null) must not flash the setup screen
    @Test fun startIsNullWhileProfileLoading() =
        assertEquals(null, startDestination(languageChosen = true, signedIn = true, profileComplete = null))

    private val today = LocalDateLike(2026, 10, 29)
    private val complete = ProfileForm(
        name = "Asha Patel", dob = LocalDateLike(1990, 1, 1), gender = Gender.Female,
        country = "IN", state = "GJ", city = ""
    )

    @Test fun saveDisabledUntilRequiredFieldsSet() {
        assertTrue(validate(complete, today).isEmpty())
        listOf(
            complete.copy(name = " "), complete.copy(dob = null), complete.copy(gender = null), complete.copy(country = "")
        ).forEach { assertFalse(it.toString(), validate(it, today).isEmpty()) }
    }

    @Test fun cityIsOptional() = assertTrue(validate(complete.copy(city = ""), today).isEmpty())

    @Test fun dobInFutureRejected() =
        assertEquals(R.string.error_dob_future, validate(complete.copy(dob = LocalDateLike(2027, 1, 1)), today)[ProfileField.Dob])

    @Test fun under13Rejected() =
        assertEquals(R.string.error_age_min, validate(complete.copy(dob = LocalDateLike(2014, 1, 1)), today)[ProfileField.Dob])

    @Test fun exactly13Accepted() = assertTrue(validate(complete.copy(dob = LocalDateLike(2013, 10, 29)), today).isEmpty())

    @Test fun phoneMustBeE164() {
        assertTrue(isValidE164("+919876543210"))
        assertFalse(isValidE164("9876543210"))
        assertFalse(isValidE164("+91 98765"))
    }
}
