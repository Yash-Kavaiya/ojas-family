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

const val DEFAULT_DIAL_CODE = "+91" // Most members are in India; anyone else types their own '+' code

/**
 * Turns what people actually type into E.164: "98765 43210", "098765-43210", "0091 98765 43210",
 * "+91 (0) 98765 43210" and "+91 98765 43210" all become "+919876543210".
 * Returns the cleaned input unchanged when it cannot be made valid, so [isValidE164] reports it.
 */
fun normalizePhone(raw: String, defaultDialCode: String = DEFAULT_DIAL_CODE): String {
    var s = raw.trim().replace("(0)", "").filter { it.isDigit() || it == '+' }
    if (s.startsWith("00")) s = "+" + s.drop(2)
    if (!s.startsWith("+")) {
        val national = s.trimStart('0')
        s = when {
            national.length == 10 -> defaultDialCode + national
            // Typed the country code without '+', e.g. "919876543210"
            national.length > 10 && defaultDialCode.drop(1).let { national.startsWith(it) && national.length - it.length == 10 } -> "+$national"
            national.isEmpty() -> s
            else -> defaultDialCode + national
        }
    }
    // Indian trunk zero after the country code: "+91 09876543210"
    if (s.startsWith("+910") && s.length == 14) s = "+91" + s.drop(4)
    return s.filterIndexed { i, c -> c.isDigit() || (c == '+' && i == 0) }
}
