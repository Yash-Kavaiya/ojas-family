package com.ojashealing.family.ui.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.BuildConfig
import com.ojashealing.family.R
import com.ojashealing.family.data.auth.signingSha1
import com.ojashealing.family.ui.theme.SacredGold
import com.ojashealing.family.ui.theme.SaffronPrimary
import com.ojashealing.family.ui.theme.Spacing

@Composable
private fun SignInUiState.message(): String? =
    error?.let { stringResource(it) + (errorDetail?.let { d -> " ($d)" } ?: "") } ?: errorText
private fun AuthError?.text(): String? = this?.let { e ->
    if (e.code != null && e.message == R.string.error_with_code) stringResource(e.message, e.code)
    else if (e.code != null) stringResource(e.message) + " (" + e.code + ")"
    else stringResource(e.message)
}

// Codes that mean Firebase did not recognise this APK, not a mistake by the person signing in
private val SIGNING_CODES = setOf(
    "ERROR_APP_NOT_AUTHORIZED", "ERROR_MISSING_CLIENT_IDENTIFIER", "ERROR_INVALID_APP_CREDENTIAL",
    "ERROR_MISSING_APP_CREDENTIAL", "ERROR_INVALID_CERT_HASH"
)

/**
 * Debug builds only. Google Sign-In and phone OTP fail when the APK's signing key is not registered in
 * Firebase, and every CI build without DEBUG_KEYSTORE_B64 uses a throwaway key. Showing the fingerprint
 * here turns "it doesn't work" into "add this SHA-1 in the Firebase console".
 */
@Composable
private fun DebugSigningHint(error: AuthError?) {
    if (!BuildConfig.DEBUG || error == null) return
    val code = error.code.orEmpty()
    // Credential Manager failures (Google) arrive as their exception class name, e.g. NoCredentialException
    if (code !in SIGNING_CODES && "Credential" !in code) return
    val context = LocalContext.current
    val sha1 = remember { signingSha1(context) } ?: return
    Text(
        text = "Debug build signed with SHA-1 $sha1. If Firebase doesn't list this fingerprint " +
            "(Project settings → Your apps), Google and phone sign-in fail.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().testTag("signin_debug_hint")
    )
}

@Composable
fun SignInScreen(vm: SignInViewModel, onPhone: () -> Unit, onEmail: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    OnboardingLayout(
        title = stringResource(R.string.signin_title),
        subtitle = stringResource(R.string.signin_subtitle),
        bottom = {
            ErrorText(state.error.text())
            DebugSigningHint(state.error)
            PrimaryButton(stringResource(R.string.signin_google), onClick = { vm.signInWithGoogle(activity) }, busy = state.busy)
            OutlinedButton(onClick = onPhone, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("signin_phone")) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
                Text(stringResource(R.string.signin_phone), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
            }
            OutlinedButton(onClick = onEmail, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("signin_email")) {
                Icon(Icons.Outlined.Email, contentDescription = null)
                Text(stringResource(R.string.signin_email), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Spacing.s))
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(SaffronPrimary, SacredGold))),
                contentAlignment = Alignment.Center
            ) {
                Text("ॐ", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun PhoneSignInScreen(vm: SignInViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    val verification = state.verification
    if (verification == null) {
        val error = state.errorFor(ErrorField.Phone, fallback = true)
        OnboardingLayout(
            title = stringResource(R.string.signin_phone),
            subtitle = stringResource(R.string.phone_hint),
            onBack = onBack,
            bottom = { PrimaryButton(stringResource(R.string.phone_send_code), onClick = { vm.sendCode(activity) }, busy = state.busy) }
        ) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = vm::onPhoneChange,
                label = { Text(stringResource(R.string.field_phone)) },
                singleLine = true,
                enabled = !state.busy,
                isError = error != null,
                supportingText = error.text()?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.sendCode(activity) }),
                modifier = Modifier.fillMaxWidth().testTag("phone_input")
            )
            DebugSigningHint(error)
        }
    } else {
        // System back on the OTP step returns to the number, like the toolbar arrow
        BackHandler(onBack = vm::backToPhoneEntry)
        val error = state.errorFor(ErrorField.Code, fallback = true)
        OnboardingLayout(
            title = stringResource(R.string.phone_code_title),
            subtitle = stringResource(R.string.phone_code_sent, state.sentTo.ifEmpty { state.phone }),
            onBack = vm::backToPhoneEntry,
            bottom = {
                PrimaryButton(stringResource(R.string.phone_verify), onClick = vm::verifyCode, busy = state.busy, enabled = state.code.length == 6)
                TextButton(onClick = { vm.sendCode(activity) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.phone_resend))
                }
            }
        ) {
            OutlinedTextField(
                value = state.code,
                onValueChange = vm::onCodeChange,
                label = { Text(stringResource(R.string.field_otp)) },
                singleLine = true,
                isError = error != null,
                supportingText = error.text()?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.verifyCode() }),
                modifier = Modifier.fillMaxWidth().testTag("otp_input")
            )
            DebugSigningHint(error)
        }
    }
}

@Composable
fun EmailSignInScreen(vm: SignInViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val emailError = state.errorFor(ErrorField.Email)
    val passwordError = state.errorFor(ErrorField.Password, fallback = true)
    OnboardingLayout(
        title = stringResource(if (state.createAccount) R.string.signin_create_account else R.string.signin_email),
        subtitle = state.resetSentTo?.let { stringResource(R.string.signin_reset_sent, it) },
        onBack = onBack,
        bottom = {
            PrimaryButton(
                stringResource(if (state.createAccount) R.string.signin_create_account else R.string.action_continue),
                onClick = vm::submitEmail,
                busy = state.busy
            )
            TextButton(onClick = vm::toggleCreateAccount, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("email_toggle_mode")) {
                Text(stringResource(if (state.createAccount) R.string.signin_have_account else R.string.signin_create_account))
            }
        }
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = { Text(stringResource(R.string.field_email)) },
            singleLine = true,
            enabled = !state.busy,
            isError = emailError != null,
            supportingText = emailError.text()?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.fillMaxWidth().testTag("email_input")
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = { Text(stringResource(R.string.field_password)) },
            singleLine = true,
            enabled = !state.busy,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(if (showPassword) R.string.password_hide else R.string.password_show)
                    )
                }
            },
            isError = passwordError != null,
            supportingText = (passwordError.text() ?: if (state.createAccount) stringResource(R.string.error_password_short) else null)
                ?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); vm.submitEmail() }),
            modifier = Modifier.fillMaxWidth().testTag("password_input")
        )
        if (!state.createAccount) {
            TextButton(onClick = vm::sendPasswordReset, enabled = !state.busy) {
                Text(stringResource(R.string.signin_forgot_password))
            }
        }
    }
}
