package com.ojashealing.family.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ojashealing.family.di.AppContainer
import com.ojashealing.family.ui.components.OjasTopBar
import com.ojashealing.family.ui.components.PaytmCoinOverlay
import com.ojashealing.family.ui.components.RewardAnchors
import com.ojashealing.family.ui.navigation.MainTab
import com.ojashealing.family.ui.navigation.OjasBottomBar
import com.ojashealing.family.ui.onboarding.ProfileSetupScreen
import com.ojashealing.family.ui.onboarding.ProfileSetupViewModel
import com.ojashealing.family.ui.screens.AskTab
import com.ojashealing.family.ui.screens.CommunityTab
import com.ojashealing.family.ui.screens.HomeTab
import com.ojashealing.family.ui.screens.ProfileTab
import com.ojashealing.family.ui.screens.SpiritualTab
import kotlinx.serialization.Serializable

@Serializable object TabsRoute
@Serializable object EditProfileRoute
@Serializable object SettingsRoute

@Composable
fun MainShell(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory {
        initializer { MainViewModel(container.auth, container.users, container.points) }
    })
    val profile by vm.profile.collectAsStateWithLifecycle()
    val reward by vm.reward.collectAsStateWithLifecycle()
    val anchors = remember { RewardAnchors() }
    val nav = rememberNavController()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onAppForeground() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Watches taps without consuming them, so the coin can start where the user tapped
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull()?.let { anchors.lastTap = it.position }
                    }
                }
            }
    ) {
        NavHost(navController = nav, startDestination = TabsRoute) {
            composable<TabsRoute> {
                var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { OjasTopBar(profile, onCounterPositioned = { anchors.counterCenter = it }) },
                    bottomBar = { OjasBottomBar(current = tab, onSelect = { tab = it }) }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                    ) {
                        when (tab) {
                            MainTab.Community -> CommunityTab()
                            MainTab.Spiritual -> SpiritualTab()
                            MainTab.Home -> HomeTab()
                            MainTab.Ask -> AskTab()
                            MainTab.Profile -> ProfileTab(
                                profile = profile,
                                locations = container.locations,
                                onEditProfile = { nav.navigate(EditProfileRoute) },
                                onOpenSettings = { nav.navigate(SettingsRoute) }
                            )
                        }
                    }
                }
            }
            composable<EditProfileRoute> {
                val editVm: ProfileSetupViewModel = viewModel(factory = viewModelFactory {
                    initializer { ProfileSetupViewModel(container.auth, container.users, container.locations, profile?.displayName.orEmpty()) }
                })
                ProfileSetupScreen(editVm, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
            }
            composable<SettingsRoute> { } // filled in by the settings screen (Task 9)
        }

        PaytmCoinOverlay(rewardEvent = reward, anchors = anchors, onDismiss = vm::clearReward)
    }
}
