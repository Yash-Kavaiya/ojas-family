package com.ojashealing.family.ui.main

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.ojashealing.family.R
import com.ojashealing.family.data.paid.Entitlements
import com.ojashealing.family.data.paid.PaidAccess
import com.ojashealing.family.data.settings.currentAppLanguage
import com.ojashealing.family.data.settings.setAppLanguage
import com.ojashealing.family.di.AppContainer
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.push.DeepLinks
import com.ojashealing.family.push.PushTopics
import com.ojashealing.family.push.Reminders
import com.ojashealing.family.push.canNotify
import com.ojashealing.family.ui.admin.AdminContentScreen
import com.ojashealing.family.ui.admin.AdminContentViewModel
import com.ojashealing.family.ui.admin.ModerationScreen
import com.ojashealing.family.ui.admin.ModerationViewModel
import com.ojashealing.family.ui.admin.asActions
import com.ojashealing.family.ui.ask.AskTab
import com.ojashealing.family.ui.ask.CallScreen
import com.ojashealing.family.ui.ask.CallStart
import com.ojashealing.family.ui.ask.CallViewModel
import com.ojashealing.family.ui.ask.ChatScreen
import com.ojashealing.family.ui.ask.ChatViewModel
import com.ojashealing.family.ui.ask.InboxScreen
import com.ojashealing.family.ui.ask.InboxViewModel
import com.ojashealing.family.ui.community.CommunityScreen
import com.ojashealing.family.ui.community.CommunityViewModel
import com.ojashealing.family.ui.community.ComposeAnnouncementScreen
import com.ojashealing.family.ui.community.AnnouncementDetailScreen
import com.ojashealing.family.ui.thought.ThoughtDetailScreen
import com.ojashealing.family.ui.thought.AboutScreen
import com.ojashealing.family.data.points.LevelTargets
import com.ojashealing.family.ui.screens.PointsHistoryScreen
import com.ojashealing.family.ui.ask.SwamijiAiScreen
import com.ojashealing.family.ui.community.ComposeDailyScreen
import com.ojashealing.family.ui.community.ComposePostScreen
import com.ojashealing.family.ui.community.DailyFeed
import com.ojashealing.family.ui.community.DailyPostScreen
import com.ojashealing.family.ui.community.DailyViewModel
import com.ojashealing.family.ui.community.QuestionDetailScreen
import com.ojashealing.family.ui.components.OjasTopBar
import com.ojashealing.family.ui.components.PaytmCoinOverlay
import com.ojashealing.family.ui.components.RewardAnchors
import com.ojashealing.family.ui.content.ContentViewModel
import com.ojashealing.family.ui.content.PracticeBar
import com.ojashealing.family.ui.content.PracticeTab
import com.ojashealing.family.ui.content.PracticeViewModel
import com.ojashealing.family.ui.content.VideoPlayerScreen
import com.ojashealing.family.ui.content.practiceKind
import com.ojashealing.family.ui.goals.GoalSubmitScreen
import com.ojashealing.family.ui.goals.GoalSubmitViewModel
import com.ojashealing.family.ui.goals.ProfileGoalsViewModel
import com.ojashealing.family.ui.navigation.MainTab
import com.ojashealing.family.ui.navigation.OjasBottomBar
import com.ojashealing.family.ui.onboarding.ProfileSetupScreen
import com.ojashealing.family.ui.onboarding.ProfileSetupViewModel
import com.ojashealing.family.ui.paid.PaidViewModel
import com.ojashealing.family.ui.paid.PlansScreen
import com.ojashealing.family.ui.screens.MembersScreen
import com.ojashealing.family.ui.screens.MembersViewModel
import com.ojashealing.family.ui.screens.ProfileTab
import com.ojashealing.family.ui.screens.PublicProfileScreen
import com.ojashealing.family.ui.settings.SettingsProfile
import com.ojashealing.family.ui.settings.SettingsScreen
import com.ojashealing.family.ui.settings.SettingsViewModel
import com.ojashealing.family.ui.thought.HomeScreen
import com.ojashealing.family.ui.thought.LocalOpenProfile
import com.ojashealing.family.ui.thought.ScheduleThoughtScreen
import com.ojashealing.family.ui.thought.ThoughtViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
@Serializable data class VideoRoute(val videoId: String, val title: String, val section: String = "")
@Serializable data class DailyPostRoute(val id: String)
@Serializable object ComposeDailyRoute
@Serializable data class PlansRoute(val access: String = "")
@Serializable data class ChatRoute(val memberUid: String = "")
@Serializable object InboxRoute
@Serializable data class CallRoute(
    val callId: String = "",
    val calleeUid: String = "",
    val memberUid: String = "",
    val staffUid: String = "",
    val peerName: String = ""
)
@Serializable data class MemberProfileRoute(val uid: String)
@Serializable object MembersRoute
@Serializable object PointsHistoryRoute
@Serializable data class ThoughtRoute(val id: String)
@Serializable data class AnnouncementRoute(val id: String)
@Serializable data class AboutRoute(val kind: String)
@Serializable object SwamijiAiRoute

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun MainShell(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = viewModelFactory {
        initializer { MainViewModel(container.auth, container.users, container.points) }
    })
    val profile by vm.profile.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    val reward by vm.reward.collectAsStateWithLifecycle()
    val uploadPostImage: suspend (String, android.net.Uri) -> String = container.images::upload
    val entitlementsFlow = remember {
        container.auth.currentUid.flatMapLatest { uid -> uid?.let { container.paid.observe(it).catch { emit(Entitlements()) } } ?: flowOf(Entitlements()) }
    }
    val entitlements by entitlementsFlow.collectAsStateWithLifecycle(Entitlements())
    val contentVm: ContentViewModel = viewModel(factory = viewModelFactory {
        initializer { ContentViewModel(container.content.observe(), vm.profile.map { it?.viewer }) }
    })
    val practice by contentVm.practice.collectAsStateWithLifecycle()
    val understanding by contentVm.understanding.collectAsStateWithLifecycle()
    val practiceVm: PracticeViewModel = viewModel(factory = viewModelFactory { initializer { PracticeViewModel(container.practice, vm.profile) } })
    val practiceStatus by practiceVm.status.collectAsStateWithLifecycle()
    val practiceBusy by practiceVm.busy.collectAsStateWithLifecycle()
    val practiceMessage by practiceVm.message.collectAsStateWithLifecycle()
    val thoughtVm: ThoughtViewModel = viewModel(factory = viewModelFactory {
        initializer { ThoughtViewModel(container.thoughts, vm.profile, uploadPostImage, container.images::uploadThought) }
    })
    val communityVm: CommunityViewModel = viewModel(factory = viewModelFactory {
        initializer { CommunityViewModel(container.community, vm.profile, uploadPostImage, container.images::uploadAnnouncement) }
    })
    val dailyVm: DailyViewModel = viewModel(factory = viewModelFactory {
        initializer {
            DailyViewModel(
                container.daily, vm.profile,
                container.auth.currentUid.flatMapLatest { uid -> uid?.let(container.community::observeBlocked) ?: flowOf(emptySet()) },
                uploadPostImage
            )
        }
    })
    val paidVm: PaidViewModel = viewModel(factory = viewModelFactory { initializer { PaidViewModel(vm.profile, entitlementsFlow, container.billing) } })
    val goalsVm: ProfileGoalsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            ProfileGoalsViewModel(
                container.goals.observeGoals(),
                container.auth.currentUid.flatMapLatest { uid -> uid?.let(container.goals::observeProgress) ?: flowOf(emptyMap()) }
            )
        }
    })
    val goals by goalsVm.state.collectAsStateWithLifecycle()
    val staffContact by remember { container.chat.observeStaffContact().catch { emit(null) } }.collectAsStateWithLifecycle(null)
    val myThread by remember {
        container.auth.currentUid.flatMapLatest { uid -> uid?.let { container.chat.observeThread(it).catch { emit(null) } } ?: flowOf(null) }
    }.collectAsStateWithLifecycle(null)
    val play: (ContentItem) -> Unit = { nav.navigate(VideoRoute(it.videoId, it.title, it.section.id)) }
    val anchors = remember { RewardAnchors() }
    // Held above the NavHost so sub-screens keep the tab, but not saved: every fresh open starts on Home
    var tab by remember { mutableStateOf(MainTab.Home) }
    // Q&A, Daily or Announcements inside Community; held here so notifications can pick it
    var communityTab by rememberSaveable { mutableStateOf(0) }
    val appLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val about by remember(appLanguage) {
        container.about.observe(appLanguage)
            .catch { emit(com.ojashealing.family.data.about.AboutInfo()) }
    }.collectAsStateWithLifecycle(com.ojashealing.family.data.about.AboutInfo())
    val photoUploading by vm.photoUploading.collectAsStateWithLifecycle()
    val photoError by vm.photoError.collectAsStateWithLifecycle()
    val noSwamiji = stringResource(R.string.call_no_swamiji)
    val swamijiName = stringResource(R.string.role_swamiji)

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onAppForeground() }

    // Keep push topics, the FCM token and practice reminders in step with the profile and Settings
    val p = profile
    LaunchedEffect(p?.uid, p?.notificationPrefs, p?.viewer?.memberLevel, p?.viewer?.role) {
        if (p == null) return@LaunchedEffect
        PushTopics.registerToken()
        PushTopics.sync(p.notificationPrefs, paid = Permissions.canViewDailyActivities(p.viewer))
    }
    LaunchedEffect(p?.morningReminder, p?.nightReminder, p?.viewer, p?.notificationPrefs) {
        if (p == null) return@LaunchedEffect
        val on = Permissions.canDoDailyPractices(p.viewer) && (p.notificationPrefs["all"] ?: true)
        Reminders.schedule(context, p.morningReminder, p.nightReminder, on)
    }
    AskNotificationPermission()

    // A call ringing for me opens the call screen, even if its push never arrived
    LaunchedEffect(p?.uid) {
        val uid = p?.uid ?: return@LaunchedEffect
        container.calls.observeIncoming(uid).catch { }.collect { calls ->
            val call = calls.firstOrNull() ?: return@collect
            val onCall = nav.currentBackStackEntry?.destination?.route?.contains("CallRoute") == true
            if (!onCall) nav.navigate(CallRoute(callId = call.id))
        }
    }

    // Notification taps
    val target by DeepLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(target, p?.uid) {
        val t = target ?: return@LaunchedEffect
        if (p == null) return@LaunchedEffect
        DeepLinks.consume()
        openTarget(nav, t, p.uid, selectCommunityTab = { communityTab = it }) { tab = it }
    }

    LaunchedEffect(practiceMessage) {
        practiceMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); practiceVm.clearMessage() }
    }

    val callSwamiji: () -> Unit = callSwamiji@{
        val me = p ?: return@callSwamiji
        val staff = myThread?.assigneeUid?.takeIf { it.isNotEmpty() } ?: staffContact?.swamijiUid
        if (staff == null) Toast.makeText(context, noSwamiji, Toast.LENGTH_LONG).show()
        else nav.navigate(CallRoute(calleeUid = staff, memberUid = me.uid, staffUid = staff, peerName = myThread?.assigneeName?.ifEmpty { null } ?: swamijiName))
    }
    val openPlans: (PaidAccess?) -> Unit = { nav.navigate(PlansRoute(it?.id.orEmpty())) }
    val openProfile: ((String) -> Unit)? = p?.takeIf { Permissions.canViewOtherProfiles(it.viewer) }?.let { { uid: String -> nav.navigate(MemberProfileRoute(uid)) } }

    CompositionLocalProvider(LocalOpenProfile provides openProfile) {
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
        NavHost(
            navController = nav,
            startDestination = TabsRoute,
            // New screens glide in from the side and fade; Back plays it in reverse
            enterTransition = { slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(NAV_MS)) },
            exitTransition = { slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { -it / 10 } + fadeOut(tween(NAV_MS / 2)) },
            popEnterTransition = { slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { -it / 10 } + fadeIn(tween(NAV_MS)) },
            popExitTransition = { slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(NAV_MS / 2)) }
        ) {
            composable<TabsRoute> {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { OjasTopBar(profile, onCounterPositioned = { anchors.counterCenter = it }, onOpenPoints = { nav.navigate(PointsHistoryRoute) }) },
                    bottomBar = { OjasBottomBar(current = tab, onSelect = { tab = it }) }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                    ) {
                        AnimatedContent(targetState = tab, transitionSpec = { tabTransition(initialState, targetState) }, label = "tab") { shown ->
                        when (shown) {
                            MainTab.Community -> CommunityScreen(
                                communityVm,
                                onOpenQuestion = { nav.navigate(QuestionRoute(it)) },
                                onAsk = { nav.navigate(AskRoute) },
                                onAnnounce = { nav.navigate(AnnounceRoute) },
                                daily = { canPost ->
                                    DailyFeed(
                                        dailyVm, canPost,
                                        onOpen = { nav.navigate(DailyPostRoute(it)) },
                                        onCompose = { nav.navigate(ComposeDailyRoute) },
                                        onJoin = { openPlans(PaidAccess.Membership) }
                                    )
                                },
                                onOpenAnnouncement = { nav.navigate(AnnouncementRoute(it)) },
                                tab = communityTab,
                                onTab = { communityTab = it }
                            )
                            MainTab.Spiritual -> PracticeTab(practice, onPlay = play, practice = practiceStatus, practiceBusy = practiceBusy, onMarkPractice = practiceVm::mark)
                            MainTab.Home -> HomeScreen(
                                thoughtVm,
                                onSchedule = { nav.navigate(ScheduleThoughtRoute) },
                                onOpenThought = { nav.navigate(ThoughtRoute(it)) },
                                about = about,
                                onOpenAbout = { nav.navigate(AboutRoute(it)) }
                            )
                            MainTab.Ask -> AskTab(
                                profile = profile,
                                entitlements = entitlements,
                                understanding = understanding,
                                onPlay = play,
                                onOpenChat = { nav.navigate(ChatRoute()) },
                                onCallSwamiji = callSwamiji,
                                onPlans = { openPlans(it) },
                                onInbox = { nav.navigate(InboxRoute) },
                                onOpenAi = { nav.navigate(SwamijiAiRoute) }
                            )
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
                                photoError = photoError,
                                entitlements = entitlements,
                                onPlans = { openPlans(null) },
                                onOpenMembers = { nav.navigate(MembersRoute) }
                            )
                        }
                        }
                    }
                }
            }
            composable<QuestionRoute> { entry ->
                QuestionDetailScreen(communityVm, entry.toRoute<QuestionRoute>().id, onBack = { nav.popBackStack() })
            }
            composable<AskRoute> {
                val perms by communityVm.permissions.collectAsStateWithLifecycle()
                ComposePostScreen(
                    title = stringResource(R.string.community_ask),
                    firstLabel = stringResource(R.string.community_question_title),
                    secondLabel = stringResource(R.string.community_question_body),
                    onBack = { nav.popBackStack() },
                    onSubmit = { t, b, done -> communityVm.ask(t, b, done) },
                    canAttachImage = perms?.canAttachImage == true,
                    onSubmitWithPhoto = { t, b, photo, done -> communityVm.ask(t, b, photo, done) }
                )
            }
            composable<AnnounceRoute> { ComposeAnnouncementScreen(communityVm, onBack = { nav.popBackStack() }) }
            composable<DailyPostRoute> { entry -> DailyPostScreen(dailyVm, entry.toRoute<DailyPostRoute>().id, onBack = { nav.popBackStack() }) }
            composable<ComposeDailyRoute> { ComposeDailyScreen(dailyVm, onBack = { nav.popBackStack() }) }
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
                val kind = ContentSection.fromId(route.section)?.practiceKind()
                val status = practiceStatus
                VideoPlayerScreen(
                    route.videoId, route.title, onBack = { nav.popBackStack() },
                    practice = if (kind != null && status != null) { { PracticeBar(kind, status, practiceBusy, { practiceVm.mark(kind) }) } } else null
                )
            }
            composable<PlansRoute> { entry ->
                val only = PaidAccess.entries.firstOrNull { it.id == entry.toRoute<PlansRoute>().access }
                PlansScreen(paidVm, onBack = { nav.popBackStack() }, only = only)
            }
            composable<ChatRoute> { entry ->
                val memberUid = entry.toRoute<ChatRoute>().memberUid.ifEmpty { null }
                val chatVm: ChatViewModel = viewModel(key = "chat-${memberUid.orEmpty()}", factory = viewModelFactory {
                    initializer { ChatViewModel(container.chat, vm.profile, entitlementsFlow, memberUid) }
                })
                val staffCall: ((String) -> Unit)? = p?.takeIf { Permissions.isChatStaff(it.viewer) }?.let { me ->
                    { member: String ->
                        val name = chatVm.ui.value?.thread?.memberName.orEmpty()
                        nav.navigate(CallRoute(calleeUid = member, memberUid = member, staffUid = me.uid, peerName = name))
                    }
                }
                ChatScreen(chatVm, onBack = { nav.popBackStack() }, onRenew = { openPlans(PaidAccess.Chat) }, onCall = staffCall)
            }
            composable<InboxRoute> {
                val inboxVm: InboxViewModel = viewModel(factory = viewModelFactory { initializer { InboxViewModel(container.chat, vm.profile) } })
                InboxScreen(inboxVm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(ChatRoute(it)) })
            }
            composable<CallRoute> { entry ->
                val r = entry.toRoute<CallRoute>()
                val me = p ?: return@composable
                val start = if (r.callId.isNotEmpty()) CallStart.Incoming(r.callId)
                else CallStart.Outgoing(r.calleeUid, r.memberUid, r.staffUid, r.peerName)
                val app = context.applicationContext
                val callVm: CallViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        CallViewModel(
                            app, container.calls, me.uid, start,
                            // Staff may read profiles, so they see who is calling; members see "Swamiji"
                            nameOf = { uid -> runCatching { container.users.observe(uid).first()?.displayName }.getOrNull() },
                            fallbackName = if (Permissions.isChatStaff(me.viewer)) "" else swamijiName
                        )
                    }
                })
                CallScreen(callVm, onClose = { nav.popBackStack() })
            }
            composable<MemberProfileRoute> { entry ->
                val uid = entry.toRoute<MemberProfileRoute>().uid
                PublicProfileScreen(remember(uid) { container.publicProfiles.observe(uid) }, goals.goals, container.locations, onBack = { nav.popBackStack() })
            }
            composable<ThoughtRoute> { entry -> ThoughtDetailScreen(thoughtVm, entry.toRoute<ThoughtRoute>().id, onBack = { nav.popBackStack() }) }
            composable<AnnouncementRoute> { entry -> AnnouncementDetailScreen(communityVm, entry.toRoute<AnnouncementRoute>().id, onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { entry -> AboutScreen(entry.toRoute<AboutRoute>().kind, about, onBack = { nav.popBackStack() }) }
            composable<PointsHistoryRoute> {
                val uid = profile?.uid
                val history by remember(uid) { uid?.let { container.points.observeHistory(it).catch { emit(emptyList()) } } ?: flowOf(null) }
                    .collectAsStateWithLifecycle(null)
                val targets by remember { container.points.observeLevelTargets().catch { emit(LevelTargets()) } }.collectAsStateWithLifecycle(LevelTargets())
                PointsHistoryScreen(profile, history, targets, onBack = { nav.popBackStack() })
            }
            composable<SwamijiAiRoute> {
                SwamijiAiScreen(onBack = { nav.popBackStack() }, onAskSwamiji = { nav.popBackStack(); tab = MainTab.Ask })
            }
            composable<MembersRoute> {
                val membersVm: MembersViewModel = viewModel(factory = viewModelFactory { initializer { MembersViewModel(container.publicProfiles, container.paid) } })
                MembersScreen(membersVm, onBack = { nav.popBackStack() })
            }
            composable<EditProfileRoute> {
                val editVm: ProfileSetupViewModel = viewModel(factory = viewModelFactory {
                    initializer { ProfileSetupViewModel(container.auth, container.users, container.locations, profile?.displayName.orEmpty()) }
                })
                ProfileSetupScreen(editVm, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
            }
            composable<SettingsRoute> {
                val app = context.applicationContext
                val settingsVm: SettingsViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            profile = vm.profile.map { p ->
                                p?.let { SettingsProfile(it.hideProfile, it.hideGoals, it.notificationPrefs, it.morningReminder, it.nightReminder, Permissions.canHideProfile(it.viewer)) }
                            },
                            theme = container.settings,
                            writeSettings = { fields ->
                                container.auth.currentUid.value?.let { container.users.updateSettings(it, fields) }
                                    ?: Result.failure(IllegalStateException("signed out"))
                            },
                            deleteAccount = container.points::deleteAccount,
                            // Signing out flips the auth state, and OjasRoot swaps to the sign-in screen.
                            // This phone stops getting the account's pushes and reminders first.
                            signOut = {
                                container.auth.currentUid.value?.let { PushTopics.forget(it) }
                                Reminders.schedule(app, "06:00", "21:30", enabled = false)
                                container.auth.signOut(app)
                            },
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
}

private const val NAV_MS = 320

/** Tabs slide toward the side of the tab you tapped, with a soft fade and a slight zoom. */
private fun tabTransition(from: MainTab, to: MainTab): ContentTransform {
    val dir = if (to.ordinal > from.ordinal) 1 else -1
    val enter = slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { dir * it / 6 } +
        fadeIn(tween(280)) + scaleIn(tween(280), initialScale = 0.98f)
    val exit = slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -dir * it / 8 } + fadeOut(tween(180))
    return enter togetherWith exit
}

/** Opens what a notification points at. */
private fun openTarget(
    nav: NavHostController,
    target: String,
    myUid: String,
    selectCommunityTab: (Int) -> Unit = {},
    selectTab: (MainTab) -> Unit
) {
    val (kind, id) = target.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    // Put the right tab underneath, then open the item itself, so Back lands where it belongs
    when (kind) {
        "questions" -> { selectTab(MainTab.Community); selectCommunityTab(0); nav.navigate(QuestionRoute(id)) }
        "dailyPosts" -> { selectTab(MainTab.Community); selectCommunityTab(1); nav.navigate(DailyPostRoute(id)) }
        "chats" -> nav.navigate(ChatRoute(if (id == myUid) "" else id))
        // The ringing-call listener may already have opened it
        "calls" -> if (nav.currentBackStackEntry?.destination?.route?.contains("CallRoute") != true) nav.navigate(CallRoute(callId = id))
        "thoughts" -> { selectTab(MainTab.Home); if (id.isNotEmpty()) nav.navigate(ThoughtRoute(id)) }
        "announcements" -> {
            selectTab(MainTab.Community); selectCommunityTab(2)
            if (id.isNotEmpty()) nav.navigate(AnnouncementRoute(id))
        }
        "practice" -> selectTab(MainTab.Spiritual)
    }
}

/** Android 13+: asks once for notification permission, explaining why first. */
@Composable
private fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("ojas", android.content.Context.MODE_PRIVATE) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (asked || canNotify(context) || prefs.getBoolean("notifAsked", false)) return
    AlertDialog(
        onDismissRequest = { asked = true; prefs.edit().putBoolean("notifAsked", true).apply() },
        title = { Text(stringResource(R.string.notif_permission_title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.notif_permission_body), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = {
                asked = true
                prefs.edit().putBoolean("notifAsked", true).apply()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.action_allow)) }
        },
        dismissButton = {
            TextButton(onClick = { asked = true; prefs.edit().putBoolean("notifAsked", true).apply() }) { Text(stringResource(R.string.action_not_now)) }
        }
    )
}
