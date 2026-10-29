package com.ojashealing.family.ui.onboarding

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.R
import com.ojashealing.family.ui.theme.SacredGold
import com.ojashealing.family.ui.theme.SaffronPrimary
import com.ojashealing.family.ui.theme.Spacing

@Composable
private fun SignInUiState.message(): String? = error?.let { stringResource(it) } ?: errorText

@Composable
fun SignInScreen(vm: SignInViewModel, onPhone: () -> Unit, onEmail: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as Activity
    OnboardingLayout(
        title = stringResource(R.string.signin_title),
        subtitle = stringResource(R.string.signin_subtitle),
        bottom = {
            ErrorText(state.message())
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
        OnboardingLayout(
            title = stringResource(R.string.signin_phone),
            subtitle = null,
            onBack = onBack,
            bottom = { PrimaryButton(stringResource(R.string.phone_send_code), onClick = { vm.sendCode(activity) }, busy = state.busy) }
        ) {
            OutlinedTextField(
                value = state.phone,
                onValueChange = vm::onPhoneChange,
                label = { Text(stringResource(R.string.field_phone)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = state.message()?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag("phone_input")
            )
        }
    } else {
        OnboardingLayout(
            title = stringResource(R.string.phone_code_title),
            subtitle = stringResource(R.string.phone_code_sent, state.phone),
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
                isError = state.error != null,
                supportingText = state.message()?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag("otp_input")
            )
        }
    }
}

@Composable
fun EmailSignInScreen(vm: SignInViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
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
            TextButton(onClick = vm::toggleCreateAccount, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.createAccount) R.string.signin_have_account else R.string.signin_create_account))
            }
        }
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            label = { Text(stringResource(R.string.field_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            label = { Text(stringResource(R.string.field_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.message() != null,
            supportingText = state.message()?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.createAccount) {
            TextButton(onClick = vm::sendPasswordReset, enabled = !state.busy) {
                Text(stringResource(R.string.signin_forgot_password))
            }
        }
    }
}
