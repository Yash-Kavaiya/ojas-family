package com.ojashealing.family.ui.onboarding

import android.app.Activity
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.FirebaseNetworkException
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.AuthRepository
import com.ojashealing.family.data.auth.PhoneVerification
import com.ojashealing.family.data.location.LocationRepository
import com.ojashealing.family.data.settings.currentAppLanguage
import com.ojashealing.family.data.settings.setAppLanguage
import com.ojashealing.family.data.user.ProfileEdit
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.data.user.UserRepository
import com.ojashealing.family.domain.LocalDateLike
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import com.ojashealing.family.data.retryBriefly
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Decides which top-level screen to show: language, sign-in, profile setup or the main app. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModel(auth: AuthRepository, users: UserRepository) : ViewModel() {

    private val language = MutableStateFlow(currentAppLanguage())

    private val profileComplete = auth.currentUid.flatMapLatest { uid ->
        if (uid == null) flowOf(SignedOut)
        else users.observe(uid)
            // A passing listener error must not send an existing member to profile setup
            .retryBriefly()
            .map<UserProfile?, Any?> { it?.profileComplete ?: false }
            .onStart { emit(null) }
            .catch { emit(false) }
    }

    val start: StateFlow<StartDestination?> = combine(language, profileComplete) { lang, complete ->
        startDestination(
            languageChosen = lang.isNotEmpty(),
            signedIn = complete != SignedOut,
            profileComplete = complete as? Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun chooseLanguage(tag: String) {
        // AppCompat recreates the activity in the new locale; this ViewModel survives that
        language.value = tag
        setAppLanguage(tag)
    }

    private object SignedOut
}

data class SignInUiState(
    val busy: Boolean = false,
    val error: AuthError? = null,
    val phone: String = "+$DEFAULT_COUNTRY_CODE",
    /** The normalized E.164 number the code was sent to, shown on the OTP screen. */
    val sentTo: String = "",
    val verification: PhoneVerification.CodeSent? = null,
    val code: String = "",
    val email: String = "",
    val password: String = "",
    val createAccount: Boolean = false,
    val resetSentTo: String? = null
) {
    /** The error to show under [field], if it belongs there. Errors without a field show under the last input. */
    fun errorFor(field: ErrorField, fallback: Boolean = false): AuthError? =
        error?.takeIf { it.field == field || (fallback && it.field == null) }
}

class SignInViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = sanitizePhoneInput(value), error = null) }
    fun onCodeChange(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value.trim(), error = null, resetSentTo = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun toggleCreateAccount() = _state.update { it.copy(createAccount = !it.createAccount, error = null, resetSentTo = null) }
    fun backToPhoneEntry() = _state.update { it.copy(verification = null, code = "", error = null) }

    /** An error from one sign-in screen must not greet the person on the next one. */
    fun clearError() = _state.update { it.copy(error = null, resetSentTo = null) }

    fun signInWithGoogle(activity: Activity) = run(step = null) { auth.signInWithGoogle(activity) }

    fun sendCode(activity: Activity) {
        val phone = normalizePhone(_state.value.phone)
        if (!isValidPhone(phone)) {
            _state.update { it.copy(error = AuthError(R.string.error_phone_invalid, ErrorField.Phone)) }
            return
        }
        val resending = _state.value.verification != null
        _state.update { it.copy(phone = phone) }
        run(
            step = if (resending) PhoneStep.Code else PhoneStep.Number,
            onSuccess = { result ->
                (result as? PhoneVerification.CodeSent)?.let { v -> _state.update { it.copy(verification = v, sentTo = phone) } }
            }
        ) {
            auth.startPhoneSignIn(activity, phone, _state.value.verification?.resendToken)
        }
    }

    fun verifyCode() {
        val s = _state.value
        val v = s.verification ?: return
        if (s.code.length != 6) {
            _state.update { it.copy(error = AuthError(R.string.error_otp_invalid, ErrorField.Code)) }
            return
        }
        run(step = PhoneStep.Code) { auth.confirmPhoneCode(v, s.code) }
    }

    fun submitEmail() {
        val s = _state.value
        val email = s.email.trim()
        when {
            !isValidEmail(email) -> _state.update { it.copy(error = AuthError(R.string.error_email_invalid, ErrorField.Email)) }
            s.password.length < MIN_PASSWORD -> _state.update { it.copy(error = AuthError(R.string.error_password_short, ErrorField.Password)) }
            else -> run(step = null) { auth.signInWithEmail(email, s.password, s.createAccount) }
        }
    }

    fun sendPasswordReset() {
        val email = _state.value.email.trim()
        if (!isValidEmail(email)) {
            _state.update { it.copy(error = AuthError(R.string.error_email_invalid, ErrorField.Email)) }
            return
        }
        run(step = null, onSuccess = { _state.update { it.copy(resetSentTo = email) } }) { auth.sendPasswordReset(email) }
    }

    // Runs an auth call with a busy flag and maps failures to a message; success navigates via auth state
    private fun <T> run(step: PhoneStep?, onSuccess: (T) -> Unit = {}, block: suspend () -> Result<T>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            block()
                .onSuccess { value ->
                    // This ViewModel outlives the session: never leave a password or OTP behind for the next person
                    _state.update { it.copy(busy = false, password = "", code = "") }
                    onSuccess(value)
                }
                .onFailure { e ->
                    // Closing the Google account picker is a choice, not an error
                    if (e is GetCredentialCancellationException) _state.update { it.copy(busy = false) }
                    else _state.update { it.copy(busy = false, error = authErrorFor(e, step)) }
                }
        }
    }

    private companion object {
        // Firebase itself accepts 6; 8 is the app's own floor
        const val MIN_PASSWORD = 8
    }
}

data class ProfileSetupUiState(
    val form: ProfileForm = ProfileForm(),
    val errors: Map<ProfileField, Int> = emptyMap(),
    val showErrors: Boolean = false,
    val photoUrl: String = "",
    val pendingPhoto: Uri? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    @StringRes val saveError: Int? = null
)

class ProfileSetupViewModel(
    private val auth: AuthRepository,
    private val users: UserRepository,
    val locations: LocationRepository,
    private val suggestedName: String
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    init {
        // Pre-fill from an existing profile (editing) or the sign-in provider's display name
        viewModelScope.launch {
            val uid = auth.currentUid.value ?: return@launch
            val existing = runCatching { users.observe(uid).first() }.getOrNull()
            val form = if (existing != null && existing.displayName.isNotEmpty()) {
                ProfileForm(
                    name = existing.displayName,
                    dob = LocalDateLike.parse(existing.dob),
                    gender = Gender.fromId(existing.gender),
                    country = existing.country,
                    state = existing.state,
                    city = existing.city,
                    village = existing.village,
                    about = existing.about
                )
            } else ProfileForm(name = suggestedName, country = "IN")
            _state.update { it.copy(form = form, photoUrl = existing?.photoUrl.orEmpty(), errors = validate(form, LocalDateLike.today())) }
        }
    }

    fun edit(transform: (ProfileForm) -> ProfileForm) = _state.update {
        val form = transform(it.form)
        it.copy(form = form, errors = validate(form, LocalDateLike.today()), saveError = null)
    }

    fun selectCountry(code: String) = edit { if (it.country == code) it else it.copy(country = code, state = "", city = "") }
    fun selectState(code: String) = edit { if (it.state == code) it else it.copy(state = code, city = "") }
    fun pickPhoto(uri: Uri) = _state.update { it.copy(pendingPhoto = uri) }

    fun save() {
        val s = _state.value
        if (s.errors.isNotEmpty()) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val uid = auth.currentUid.value ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            s.pendingPhoto?.let { users.uploadAvatar(uid, it) }
            val f = s.form
            users.saveProfile(
                uid,
                ProfileEdit(
                    displayName = f.name,
                    dob = f.dob!!.iso(),
                    gender = f.gender!!.id,
                    country = f.country,
                    state = f.state,
                    city = f.city,
                    village = f.village,
                    about = f.about,
                    language = currentAppLanguage().ifEmpty { "en" }
                )
            ).onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(saving = false, saveError = if (e is FirebaseNetworkException) R.string.error_offline else R.string.error_generic)
                    }
                }
        }
    }
}
