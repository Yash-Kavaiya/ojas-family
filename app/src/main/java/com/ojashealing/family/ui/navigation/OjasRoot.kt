package com.ojashealing.family.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.ojashealing.family.OjasApp
import com.ojashealing.family.di.AppContainer
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.main.MainShell
import com.ojashealing.family.ui.onboarding.EmailSignInScreen
import com.ojashealing.family.ui.onboarding.LanguageScreen
import com.ojashealing.family.ui.onboarding.OnboardingViewModel
import com.ojashealing.family.ui.onboarding.PhoneSignInScreen
import com.ojashealing.family.ui.onboarding.ProfileSetupScreen
import com.ojashealing.family.ui.onboarding.ProfileSetupViewModel
import com.ojashealing.family.ui.onboarding.SignInScreen
import com.ojashealing.family.ui.onboarding.SignInViewModel
import com.ojashealing.family.ui.onboarding.StartDestination
import kotlinx.serialization.Serializable

@Serializable object SignInRoute
@Serializable object PhoneRoute
@Serializable object EmailRoute

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as OjasApp).container

/**
 * Top-level gate. The start destination follows auth and profile state, so signing in, finishing the
 * profile or signing out swaps the whole screen without any back stack to undo.
 */
@Composable
fun OjasRoot() {
    val container = appContainer()
    val onboarding: OnboardingViewModel = viewModel(factory = viewModelFactory {
        initializer { OnboardingViewModel(container.auth, container.users) }
    })
    val start by onboarding.start.collectAsStateWithLifecycle()

    when (start) {
        null -> LoadingState()
        StartDestination.Language -> LanguageScreen(onChoose = onboarding::chooseLanguage)
        StartDestination.SignIn -> AuthFlow(container)
        StartDestination.ProfileSetup -> {
            val vm: ProfileSetupViewModel = viewModel(factory = viewModelFactory {
                initializer {
                    ProfileSetupViewModel(container.auth, container.users, container.locations, Firebase.auth.currentUser?.displayName.orEmpty())
                }
            })
            ProfileSetupScreen(vm)
        }
        StartDestination.Main -> MainShell()
    }
}

@Composable
private fun AuthFlow(container: AppContainer) {
    val nav = rememberNavController()
    // One ViewModel for the three sign-in screens so the typed phone/email survives going back
    val vm: SignInViewModel = viewModel(factory = viewModelFactory { initializer { SignInViewModel(container.auth) } })
    NavHost(navController = nav, startDestination = SignInRoute) {
        composable<SignInRoute> { SignInScreen(vm, onPhone = { nav.navigate(PhoneRoute) }, onEmail = { nav.navigate(EmailRoute) }) }
        composable<PhoneRoute> { PhoneSignInScreen(vm, onBack = { nav.popBackStack() }) }
        composable<EmailRoute> { EmailSignInScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
