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

/** Most members are in India, so a number typed without a country code is taken as Indian. */
const val DEFAULT_COUNTRY_CODE = "91"

/** Keeps what a phone field can hold: digits and one leading '+'. */
fun sanitizePhoneInput(value: String): String {
    val plus = value.trimStart().startsWith("+")
    val digits = value.filter(Char::isDigit).take(15)
    return if (plus) "+$digits" else digits
}

/**
 * Turns what people actually type into E.164. Handles "98765 43210", "098765 43210", "0091 98765…",
 * "91 98765…", and the field's "+91" prefix followed by a pasted "+91…" or "91…".
 */
fun normalizePhone(input: String, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): String {
    // A pasted "+91…" after the prefilled "+91": the last '+' starts the real number
    val raw = input.substring(input.lastIndexOf('+').coerceAtLeast(0)).trim()
    val digits = raw.filter(Char::isDigit)
    val e164 = when {
        raw.startsWith("+") -> "+$digits"
        digits.startsWith("00") -> "+" + digits.drop(2)
        digits.length == 11 && digits.startsWith("0") -> "+$defaultCountryCode" + digits.drop(1)
        digits.length == 10 -> "+$defaultCountryCode$digits"
        else -> "+$digits"
    }
    if (defaultCountryCode != "91" || !e164.startsWith("+91")) return e164
    val national = e164.removePrefix("+91")
    return when {
        // "+91 0 98765 43210": trunk zero kept after the country code
        national.length == 11 && national.startsWith("0") -> "+91" + national.drop(1)
        // "+91" prefix and then "91 98765 43210" typed again
        national.length == 12 && national.startsWith("91") -> "+" + national
        else -> e164
    }
}

/** E.164 check, plus India's own rule: ten digits starting 6–9 after +91. */
fun isValidPhone(e164: String): Boolean {
    if (!isValidE164(e164)) return false
    if (!e164.startsWith("+91")) return true
    return Regex("""^\+91[6-9]\d{9}$""").matches(e164)
}

// Same shape as android.util.Patterns.EMAIL_ADDRESS, kept here so it runs in plain JVM tests
private val EMAIL = Regex("""^[A-Za-z0-9+._%\-]{1,256}@[A-Za-z0-9][A-Za-z0-9\-]{0,64}(\.[A-Za-z0-9][A-Za-z0-9\-]{0,25})+$""")

fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())
