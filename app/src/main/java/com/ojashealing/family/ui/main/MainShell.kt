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
import androidx.compose.ui.platform.LocalContext
import com.ojashealing.family.data.settings.currentAppLanguage
import com.ojashealing.family.data.settings.setAppLanguage
import androidx.navigation.toRoute
import com.ojashealing.family.di.AppContainer
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.ui.admin.AdminContentScreen
import com.ojashealing.family.ui.admin.AdminContentViewModel
import com.ojashealing.family.ui.admin.ModerationScreen
import com.ojashealing.family.ui.admin.ModerationViewModel
import com.ojashealing.family.ui.admin.asActions
import com.ojashealing.family.ui.content.ContentViewModel
import com.ojashealing.family.ui.content.PracticeTab
import com.ojashealing.family.ui.content.UnderstandingTab
import com.ojashealing.family.ui.content.VideoPlayerScreen
import com.ojashealing.family.ui.settings.SettingsProfile
import com.ojashealing.family.ui.settings.SettingsScreen
import com.ojashealing.family.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.ojashealing.family.ui.goals.GoalSubmitScreen
import com.ojashealing.family.ui.goals.GoalSubmitViewModel
import com.ojashealing.family.ui.goals.ProfileGoalsViewModel
import com.ojashealing.family.ui.components.OjasTopBar
import com.ojashealing.family.ui.components.PaytmCoinOverlay
import com.ojashealing.family.ui.components.RewardAnchors
import com.ojashealing.family.ui.navigation.MainTab
import com.ojashealing.family.ui.navigation.OjasBottomBar
import com.ojashealing.family.ui.onboarding.ProfileSetupScreen
import com.ojashealing.family.ui.onboarding.ProfileSetupViewModel
import androidx.compose.ui.res.stringResource
import com.ojashealing.family.R
import com.ojashealing.family.ui.community.CommunityScreen
import com.ojashealing.family.ui.community.CommunityViewModel
import com.ojashealing.family.ui.community.ComposePostScreen
import com.ojashealing.family.ui.community.QuestionDetailScreen
import com.ojashealing.family.ui.thought.HomeScreen
import com.ojashealing.family.ui.thought.ScheduleThoughtScreen
import com.ojashealing.family.ui.thought.ThoughtViewModel
import com.ojashealing.family.ui.screens.ProfileTab
import kotlinx.serialization.Serializable

@Serializable object TabsRoute
@Serializable object EditProfileRoute
@Serializable object SettingsRoute
@Serializable object AdminContentRoute
@Serializable object ScheduleThoughtRoute
@Serializable data class QuestionRoute(val id: String)
@Serializable object AskRoute
@Serializable object AnnounceRoute
@Serializable object ModerationRoute
@Serializable data class GoalSubmitRoute(val goalId: String)
@Serializable data class VideoRoute(val videoId: String, val title: String)

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun MainShell(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory {
        initializer { MainViewModel(container.auth, container.users, container.points) }
    })
    val profile by vm.profile.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val reward by vm.reward.collectAsStateWithLifecycle()
    val contentVm: ContentViewModel = viewModel(factory = viewModelFactory {
        initializer { ContentViewModel(container.content.observe(), vm.profile.map { it?.viewer }) }
    })
    val practice by contentVm.practice.collectAsStateWithLifecycle()
    val understanding by contentVm.understanding.collectAsStateWithLifecycle()
    val thoughtVm: ThoughtViewModel = viewModel(factory = viewModelFactory {
        initializer { ThoughtViewModel(container.thoughts, vm.profile) }
    })
    val communityVm: CommunityViewModel = viewModel(factory = viewModelFactory {
        initializer { CommunityViewModel(container.community, vm.profile) }
    })
    val goalsVm: ProfileGoalsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            ProfileGoalsViewModel(
                container.goals.observeGoals(),
                container.auth.currentUid.flatMapLatest { uid -> uid?.let(container.goals::observeProgress) ?: flowOf(emptyMap()) }
            )
        }
    })
    val goals by goalsVm.state.collectAsStateWithLifecycle()
    val play: (ContentItem) -> Unit = { nav.navigate(VideoRoute(it.videoId, it.title)) }
    val anchors = remember { RewardAnchors() }
    // Held above the NavHost so sub-screens keep the tab, but not saved: every fresh open starts on Home
    var tab by remember { mutableStateOf(MainTab.Home) }
    val photoUploading by vm.photoUploading.collectAsStateWithLifecycle()
    val photoError by vm.photoError.collectAsStateWithLifecycle()

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
                            MainTab.Community -> CommunityScreen(
                                communityVm,
                                onOpenQuestion = { nav.navigate(QuestionRoute(it)) },
                                onAsk = { nav.navigate(AskRoute) },
                                onAnnounce = { nav.navigate(AnnounceRoute) }
                            )
                            MainTab.Spiritual -> PracticeTab(practice, onPlay = play)
                            MainTab.Home -> HomeScreen(thoughtVm, onSchedule = { nav.navigate(ScheduleThoughtRoute) })
                            MainTab.Ask -> UnderstandingTab(understanding, onPlay = play)
                            MainTab.Profile -> ProfileTab(
                                profile = profile,
                                locations = container.locations,
                                onEditProfile = { nav.navigate(EditProfileRoute) },
                                onOpenSettings = { nav.navigate(SettingsRoute) },
                                onOpenAdmin = { nav.navigate(AdminContentRoute) },
                                onOpenModeration = { nav.navigate(ModerationRoute) },
                                goals = goals,
                                onSubmitGoal = { nav.navigate(GoalSubmitRoute(it.id)) },
                                onPickPhoto = vm::changePhoto,
                                photoUploading = photoUploading,
                                photoError = photoError
                            )
                        }
                    }
                }
            }
            composable<QuestionRoute> { entry ->
                QuestionDetailScreen(communityVm, entry.toRoute<QuestionRoute>().id, onBack = { nav.popBackStack() })
            }
            composable<AskRoute> {
                ComposePostScreen(
                    title = stringResource(R.string.community_ask),
                    firstLabel = stringResource(R.string.community_question_title),
                    secondLabel = stringResource(R.string.community_question_body),
                    onBack = { nav.popBackStack() },
                    onSubmit = { t, b, done -> communityVm.ask(t, b, done) }
                )
            }
            composable<AnnounceRoute> {
                ComposePostScreen(
                    title = stringResource(R.string.community_post_notice),
                    firstLabel = stringResource(R.string.field_title),
                    secondLabel = stringResource(R.string.field_message),
                    onBack = { nav.popBackStack() },
                    onSubmit = { t, b, done -> communityVm.announce(t, b, done) }
                )
            }
            composable<ScheduleThoughtRoute> { ScheduleThoughtScreen(thoughtVm, onDone = { nav.popBackStack() }) }
            composable<ModerationRoute> {
                val moderationVm: ModerationViewModel = viewModel(factory = viewModelFactory {
                    initializer { ModerationViewModel(container.moderation.asActions(container.goals)) }
                })
                ModerationScreen(moderationVm, onBack = { nav.popBackStack() })
            }
            composable<GoalSubmitRoute> { entry ->
                val goalId = entry.toRoute<GoalSubmitRoute>().goalId
                val submitVm: GoalSubmitViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        GoalSubmitViewModel { photo, note ->
                            container.auth.currentUid.value?.let { container.goals.submit(it, goalId, photo, note) }
                                ?: Result.failure(IllegalStateException("signed out"))
                        }
                    }
                })
                GoalSubmitScreen(goals.goals.firstOrNull { it.id == goalId }, submitVm, onBack = { nav.popBackStack() })
            }
            composable<AdminContentRoute> {
                val adminVm: AdminContentViewModel = viewModel(factory = viewModelFactory {
                    initializer { AdminContentViewModel(container.content) }
                })
                AdminContentScreen(adminVm, onBack = { nav.popBackStack() })
            }
            composable<VideoRoute> { entry ->
                val route = entry.toRoute<VideoRoute>()
                VideoPlayerScreen(route.videoId, route.title, onBack = { nav.popBackStack() })
            }
            composable<EditProfileRoute> {
                val editVm: ProfileSetupViewModel = viewModel(factory = viewModelFactory {
                    initializer { ProfileSetupViewModel(container.auth, container.users, container.locations, profile?.displayName.orEmpty()) }
                })
                ProfileSetupScreen(editVm, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
            }
            composable<SettingsRoute> {
                val context = LocalContext.current.applicationContext
                val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            profile = vm.profile.map { p ->
                                p?.let { SettingsProfile(it.hideProfile, it.hideGoals, it.notificationPrefs, it.morningReminder, it.nightReminder) }
                            },
                            theme = container.settings,
                            writeSettings = { fields ->
                                container.auth.currentUid.value?.let { container.users.updateSettings(it, fields) }
                                    ?: Result.failure(IllegalStateException("signed out"))
                            },
                            deleteAccount = container.points::deleteAccount,
                            // Signing out flips the auth state, and OjasRoot swaps to the sign-in screen
                            signOut = { container.auth.signOut(context) },
                            setLanguage = ::setAppLanguage,
                            currentLanguage = ::currentAppLanguage
                        )
                    }
                })
                SettingsScreen(settingsVm, onBack = { nav.popBackStack() })
            }
        }

        PaytmCoinOverlay(rewardEvent = reward, anchors = anchors, onDismiss = vm::clearReward)
    }
}
