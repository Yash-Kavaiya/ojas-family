package com.ojashealing.family.ui.onboarding

import androidx.annotation.StringRes
import com.ojashealing.family.R
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.ageOn

enum class StartDestination { Language, SignIn, ProfileSetup, Main }

/** Null while the signed-in user's profile is still loading, so no screen flashes. */
fun startDestination(languageChosen: Boolean, signedIn: Boolean, profileComplete: Boolean?): StartDestination? = when {
    !languageChosen -> StartDestination.Language
    !signedIn -> StartDestination.SignIn
    profileComplete == null -> null
    !profileComplete -> StartDestination.ProfileSetup
    else -> StartDestination.Main
}

// Stored in Firestore by id; labels come from strings.xml
enum class Gender(val id: String, @StringRes val label: Int) {
    Female("female", R.string.gender_female),
    Male("male", R.string.gender_male),
    Other("other", R.string.gender_other),
    Unspecified("unspecified", R.string.gender_unspecified);

    companion object {
        fun fromId(id: String?): Gender? = entries.firstOrNull { it.id == id }
    }
}

enum class ProfileField { Name, Dob, Gender, Country }

data class ProfileForm(
    val name: String = "",
    val dob: LocalDateLike? = null,
    val gender: Gender? = null,
    val country: String = "",
    val state: String = "",
    val city: String = ""
)

const val MIN_AGE = 13 // Play Families policy: the app is not directed at children

/** Errors keyed by field, as string resources. Empty means the form can be saved. */
fun validate(form: ProfileForm, today: LocalDateLike): Map<ProfileField, Int> = buildMap {
    if (form.name.isBlank()) put(ProfileField.Name, R.string.error_required)
    when {
        form.dob == null -> put(ProfileField.Dob, R.string.error_required)
        form.dob > today -> put(ProfileField.Dob, R.string.error_dob_future)
        ageOn(form.dob, today) < MIN_AGE -> put(ProfileField.Dob, R.string.error_age_min)
    }
    if (form.gender == null) put(ProfileField.Gender, R.string.error_required)
    if (form.country.isBlank()) put(ProfileField.Country, R.string.error_required)
}

// Firebase phone auth needs E.164: '+', country code, subscriber number, 8 to 15 digits in total
fun isValidE164(phone: String): Boolean = Regex("""^\+[1-9]\d{7,14}$""").matches(phone)
