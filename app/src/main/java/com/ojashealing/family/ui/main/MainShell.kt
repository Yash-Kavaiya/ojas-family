package com.ojashealing.family.ui.main

import com.ojashealing.family.data.retryBriefly
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import com.ojashealing.family.data.call.isOver
import androidx.navigation.compose.currentBackStackEntryAsState
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import coil.compose.AsyncImage
import com.ojashealing.family.ui.adaptive.DetailMode
import com.ojashealing.family.ui.adaptive.PrimaryNav
import com.ojashealing.family.ui.adaptive.rememberWindowChrome
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.filled.Stars
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.calmSpring
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
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
import com.ojashealing.family.data.call.RequestStatus
import com.ojashealing.family.push.CallReminderPlan
import com.ojashealing.family.push.CallReminders
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
import com.ojashealing.family.data.call.CallStart
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
import kotlinx.coroutines.flow.combine
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable object TabsRoute
@Serializable object EditProfileRoute
@Serializable object SettingsRoute
@Serializable object AdminContentRoute
/** [date] empty = a new thought; otherwise edit the thought for that IST day. */
@Serializable data class ScheduleThoughtRoute(val date: String = "")
@Serializable object ScheduledThoughtsRoute
@Serializable data class QuestionRoute(val id: String)
@Serializable object AskRoute
@Serializable object AnnounceRoute
@Serializable object ModerationRoute
@Serializable data class GoalSubmitRoute(val goalId: String)
@Serializable data class VideoRoute(val videoId: String, val title: String, val section: String = "", val startSec: Int = 0)
/** A guided practice session (Blue Healing Light, Quick Protection, Morning, Night). */
@Serializable data class PracticeSessionRoute(val section: String)
@Serializable data class DailyPostRoute(val id: String)
@Serializable object ComposeDailyRoute
@Serializable data class PlansRoute(val access: String = "")
@Serializable data class ChatRoute(val memberUid: String = "", val channel: String = "swamiji")
@Serializable object InboxRoute
@Serializable object FindMemberRoute
@Serializable data class CallRoute(
    val callId: String = "",
    val calleeUid: String = "",
    val memberUid: String = "",
    val staffUid: String = "",
    val peerName: String = "",
    val video: Boolean = false,
    val channel: String = "swamiji",
    val requestId: String = "",
    /** From the ringing notification's Answer button. */
    val autoAnswer: Boolean = false,
    /** Back to the call already on, from the ongoing-call bar. */
    val resume: Boolean = false
)
@Serializable object CallHistoryRoute
@Serializable object MyCallsRoute
@Serializable object StaffRequestsRoute
@Serializable data class ScheduleCallRoute(val channel: String)
@Serializable data class CallRequestRoute(val id: String)
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
    val scope = rememberCoroutineScope()
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
    val practiceStreak by practiceVm.streak.collectAsStateWithLifecycle()
    val pointsConfigForProfile by remember { container.points.observePointsConfig().catch { emit(com.ojashealing.family.data.points.PointsConfig()) } }
        .collectAsStateWithLifecycle(com.ojashealing.family.data.points.PointsConfig())
    val practiceMessage by practiceVm.message.collectAsStateWithLifecycle()
    val progressStore = remember { com.ojashealing.family.ui.content.PracticeProgressStore(context.applicationContext) }
    val guidedVm: com.ojashealing.family.ui.content.GuidedPracticeViewModel = viewModel(factory = viewModelFactory {
        initializer { com.ojashealing.family.ui.content.GuidedPracticeViewModel(container.practiceMedia, vm.profile, progressStore) }
    })
    val guided by guidedVm.cards.collectAsStateWithLifecycle()
    val thoughtVm: ThoughtViewModel = viewModel(factory = viewModelFactory {
        initializer {
            ThoughtViewModel(
                container.thoughts, vm.profile, uploadPostImage, container.images::uploadThought,
                blocked = container.auth.currentUid.flatMapLatest { uid -> uid?.let(container.community::observeBlocked) ?: flowOf(emptySet()) }
            )
        }
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
    // Unread messages for the Ask tab badge: my own chats as a member, the inbox as staff
    val askUnread by remember(profile?.uid, profile?.viewer) {
        val p0 = profile
        when {
            p0 == null -> flowOf(0)
            Permissions.isChatStaff(p0.viewer) -> {
                val swamiji = container.chat.observeInbox(p0.viewer, p0.uid).map { l -> l.sumOf { com.ojashealing.family.data.chat.unreadFor(it, iAmStaff = true) } }
                val admin = if (p0.viewer.role == com.ojashealing.family.domain.Role.Admin)
                    container.chat.on(com.ojashealing.family.data.chat.ChatChannel.Admin).observeInbox(p0.viewer, p0.uid)
                        .map { l -> l.sumOf { com.ojashealing.family.data.chat.unreadFor(it, iAmStaff = true) } }
                else flowOf(0)
                combine(swamiji, admin) { a, b -> a + b }
            }
            else -> combine(
                container.chat.observeThread(p0.uid),
                container.chat.on(com.ojashealing.family.data.chat.ChatChannel.Admin).observeThread(p0.uid)
            ) { a, b -> (a?.unreadCountMember ?: 0) + (b?.unreadCountMember ?: 0) }
        }.catch { emit(0) }
    }.collectAsStateWithLifecycle(0)

    // Online / last seen: a heartbeat while the app is in front
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(profile?.uid) {
        val uid = profile?.uid ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                container.presence.heartbeat(uid)
                kotlinx.coroutines.delay(com.ojashealing.family.data.presence.HEARTBEAT_MS)
            }
        }
    }
    val play: (ContentItem) -> Unit = { nav.navigate(VideoRoute(it.videoId, it.title, it.section.id)) }
    val anchors = remember { RewardAnchors() }
    // Held above the NavHost so sub-screens keep the tab, but not saved: every fresh open starts on Home
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    // Q&A, Daily or Announcements inside Community; held here so notifications can pick it
    var communityTab by rememberSaveable { mutableStateOf(0) }
    var communityJump by rememberSaveable { mutableStateOf<String?>(null) }
    var askJump by rememberSaveable { mutableStateOf<String?>(null) }
    val appLanguage = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val about by remember(appLanguage) {
        container.about.observe(appLanguage)
            .catch { emit(com.ojashealing.family.data.about.AboutInfo()) }
    }.collectAsStateWithLifecycle(com.ojashealing.family.data.about.AboutInfo())
    val photoUploading by vm.photoUploading.collectAsStateWithLifecycle()
    val photoError by vm.photoError.collectAsStateWithLifecycle()
    val noSwamiji = stringResource(R.string.call_no_swamiji)
    val swamijiName = stringResource(R.string.role_swamiji)

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.onAppForeground()
        thoughtVm.refreshDay()
    }

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

    // Celebrate each goal the moment it is approved, and each new level, once per phone
    var celebration by remember { mutableStateOf<Pair<String, String>?>(null) }
    val goalLang = com.ojashealing.family.ui.goals.goalLanguage()
    val goalDoneTitle = stringResource(R.string.celebrate_goal_title)
    val goalDoneBody = stringResource(R.string.celebrate_goal_body)
    val levelTitle = stringResource(R.string.celebrate_level_title)
    val levelBody = stringResource(R.string.celebrate_level_body)
    val memberTitle = stringResource(R.string.celebrate_member_title)
    val achievedIds = goals.achieved.map { it.id }.toSet()
    LaunchedEffect(goals.loaded, achievedIds, p?.uid) {
        val uid = p?.uid ?: return@LaunchedEffect
        if (!goals.loaded) return@LaunchedEffect
        val fresh = com.ojashealing.family.ui.components.newlyReached(context, "goals_$uid", achievedIds)
        val g = goals.achieved.firstOrNull { it.id in fresh } ?: return@LaunchedEffect
        com.ojashealing.family.ui.components.markCelebrated(context, "goals_$uid", fresh)
        celebration = goalDoneTitle to goalDoneBody.format(g.titleIn(goalLang), g.reward)
    }
    val userLevel = p?.viewer?.userLevel?.rank ?: 0
    val memberLevel = p?.viewer?.memberLevel?.rank ?: 0
    LaunchedEffect(userLevel, memberLevel, p?.uid) {
        val uid = p?.uid ?: return@LaunchedEffect
        val reached = (2..userLevel).map { "u$it" }.toSet() + (1..memberLevel).map { "m$it" }.toSet()
        val fresh = com.ojashealing.family.ui.components.newlyReached(context, "levels_$uid", reached)
        if (fresh.isEmpty()) return@LaunchedEffect
        com.ojashealing.family.ui.components.markCelebrated(context, "levels_$uid", fresh)
        celebration = if (fresh.any { it.startsWith("m") }) memberTitle.format(memberLevel) to levelBody
        else levelTitle.format(userLevel) to levelBody
    }
    celebration?.let { (title, body) ->
        com.ojashealing.family.ui.components.CelebrationDialog(title, body, onDismiss = { celebration = null })
    }

    // A call ringing for me opens the call screen, even if its push never arrived
    LaunchedEffect(p?.uid) {
        val uid = p?.uid ?: return@LaunchedEffect
        container.calls.observeIncoming(uid).retryBriefly().catch { }.collect { calls ->
            val call = calls.firstOrNull() ?: return@collect
            val onCall = nav.currentBackStackEntry?.destination?.route?.contains("CallRoute") == true
            if (!onCall) nav.navigate(CallRoute(callId = call.id, video = call.video))
        }
    }

    // Android 14+: calls ring full screen only if the user allows it; ask once
    var askFullScreen by remember { mutableStateOf(false) }
    LaunchedEffect(p?.uid) {
        val me = p ?: return@LaunchedEffect
        val prefs = context.getSharedPreferences("ojas", android.content.Context.MODE_PRIVATE)
        if (!com.ojashealing.family.push.IncomingCallNotifier.canRingFullScreen(context) && !prefs.getBoolean("askedFullScreen", false)) {
            prefs.edit().putBoolean("askedFullScreen", true).apply()
            askFullScreen = true
        }
    }
    if (askFullScreen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askFullScreen = false },
            title = { Text(stringResource(R.string.call_fullscreen_title)) },
            text = { Text(stringResource(R.string.call_fullscreen_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    askFullScreen = false
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + context.packageName))
                        )
                    }
                }) { Text(stringResource(R.string.action_allow)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askFullScreen = false }) { Text(stringResource(R.string.action_not_now)) } }
        )
    }

    // Notification taps
    val target by DeepLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(target, p?.uid) {
        val t = target ?: return@LaunchedEffect
        if (p == null) return@LaunchedEffect
        DeepLinks.consume()
        openTarget(
            nav, t, p.uid,
            selectCommunityTab = { communityTab = it },
            selectTab = { tab = it },
            setCommunityJump = { communityJump = it },
            setAskJump = { askJump = it },
        )
    }

    LaunchedEffect(practiceMessage) {
        practiceMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); practiceVm.clearMessage() }
    }

    val adminTeamName = stringResource(R.string.chat_tab_admin)
    /** A member calls Swamiji (or the assigned senior member) or the Admin team's call receiver. */
    val callStaff: (com.ojashealing.family.data.chat.ChatChannel, Boolean, String) -> Unit = callStaff@{ channel, video, requestId ->
        val me = p ?: return@callStaff
        val admin = channel == com.ojashealing.family.data.chat.ChatChannel.Admin
        val staff = if (admin) staffContact?.supportUid?.takeIf { it.isNotEmpty() }
        else myThread?.assigneeUid?.takeIf { it.isNotEmpty() } ?: staffContact?.swamijiUid?.takeIf { it.isNotEmpty() }
        val name = if (admin) staffContact?.supportName?.ifEmpty { null } ?: adminTeamName
        else myThread?.assigneeName?.ifEmpty { null } ?: swamijiName
        if (staff == null) Toast.makeText(context, noSwamiji, Toast.LENGTH_LONG).show()
        else nav.navigate(CallRoute(calleeUid = staff, memberUid = me.uid, staffUid = staff, peerName = name, video = video, channel = channel.id, requestId = requestId))
    }
    /** Staff call a member back (from a chat, the history or a booked call). */
    val callMember: (String, String, Boolean, String, String) -> Unit = callMember@{ memberUid, name, video, channel, requestId ->
        val me = p ?: return@callMember
        nav.navigate(CallRoute(calleeUid = memberUid, memberUid = memberUid, staffUid = me.uid, peerName = name, video = video, channel = channel, requestId = requestId))
    }
    val callsVm: com.ojashealing.family.ui.ask.CallsViewModel = viewModel(factory = viewModelFactory {
        initializer { com.ojashealing.family.ui.ask.CallsViewModel(container.callRequests, container.calls, vm.profile) }
    })
    val myCallRequests by callsVm.mine.collectAsStateWithLifecycle()
    val staffCallRequests by callsVm.forStaff.collectAsStateWithLifecycle()
    // Confirmed audio and video calls keep a reminder on this phone until the call.
    LaunchedEffect(myCallRequests, staffCallRequests, p?.uid) {
        val uid = p?.uid ?: return@LaunchedEffect
        val mine = myCallRequests ?: return@LaunchedEffect
        val staff = staffCallRequests ?: return@LaunchedEffect
        val plans = buildList {
            for (r in mine) {
                if (r.status != RequestStatus.Confirmed) continue
                val peer = r.staffName.ifBlank { if (r.channel == "admin") adminTeamName else swamijiName }
                add(CallReminderPlan(r.id, r.at, r.video, peer))
            }
            for (r in staff) {
                if (r.status != RequestStatus.Confirmed || r.memberUid == uid) continue
                add(CallReminderPlan(r.id, r.at, r.video, r.memberName.ifBlank { swamijiName }))
            }
        }
        CallReminders.sync(context, plans)
    }
    val openPlans: (PaidAccess?) -> Unit = { nav.navigate(PlansRoute(it?.id.orEmpty())) }
    val openProfile: ((String) -> Unit)? = p?.takeIf { Permissions.canViewOtherProfiles(it.viewer) }?.let { { uid: String -> nav.navigate(MemberProfileRoute(uid)) } }

    val commentLikes = remember(profile?.uid) { profile?.uid?.let { com.ojashealing.family.ui.components.CommentLikes(container.commentLikes, it) } }
    // Report and Block on every member's post, comment and reply (Q&A, Daily Activities, thoughts, announcements)
    val reportedText = stringResource(R.string.community_reported)
    val blockedText = stringResource(R.string.community_blocked)
    val postActions = remember(profile?.uid) {
        profile?.uid?.let { uid ->
            com.ojashealing.family.ui.community.PostActions(
                myUid = uid,
                report = { path, reason -> communityVm.report(path, reason) { Toast.makeText(context, reportedText, Toast.LENGTH_SHORT).show() } },
                block = { other -> communityVm.block(other); Toast.makeText(context, blockedText, Toast.LENGTH_SHORT).show() }
            )
        }
    }
    CompositionLocalProvider(
        LocalOpenProfile provides openProfile,
        com.ojashealing.family.ui.components.LocalCommentLikes provides commentLikes,
        com.ojashealing.family.ui.community.LocalPostActions provides postActions
    ) {
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
        // A call left running while chatting or browsing: a green bar back to it, like WhatsApp
        val call by container.callManager.ui.collectAsStateWithLifecycle()
        val onCallScreen = nav.currentBackStackEntryAsState().value?.destination?.route?.contains("CallRoute") == true
        val ongoing = call?.takeIf { it.phase.let { ph -> ph != com.ojashealing.family.data.call.CallPhase.Incoming && !ph.isOver } }
        NavHost(
            navController = nav,
            startDestination = TabsRoute,
            // New screens glide in from the side and fade; Back plays it in reverse
            // New screens glide in from the side with the emphasized curve; leaving is quicker than arriving
            enterTransition = { slideInHorizontally(tween(NAV_MS, easing = OjasEasing.Emphasized)) { it / 5 } + fadeIn(tween(NAV_MS, easing = OjasEasing.Emphasized)) },
            exitTransition = { slideOutHorizontally(tween(NAV_MS, easing = OjasEasing.Emphasized)) { -it / 12 } + fadeOut(tween(Durations.Quick)) },
            popEnterTransition = { slideInHorizontally(tween(NAV_MS, easing = OjasEasing.Emphasized)) { -it / 12 } + fadeIn(tween(NAV_MS, easing = OjasEasing.Emphasized)) },
            popExitTransition = { slideOutHorizontally(tween(Durations.Base - 60, easing = OjasEasing.Exit)) { it / 5 } + fadeOut(tween(Durations.Quick)) }
        ) {
            composable<TabsRoute> {
                val chrome = rememberWindowChrome()
                val beside = chrome.detail == DetailMode.BesideList
                NavigationSuiteScaffold(
                    layoutType = if (chrome.primaryNav == PrimaryNav.BottomBar) NavigationSuiteType.NavigationBar else NavigationSuiteType.NavigationRail,
                    navigationSuiteItems = {
                        MainTab.entries.forEach { item ->
                            item(
                                selected = tab == item,
                                onClick = { tab = item },
                                modifier = Modifier.testTag("nav_${item.name}"),
                                icon = { SuiteIcon(item, tab == item, profile?.photoUrl.orEmpty(), askUnread) },
                                label = {
                                    Text(
                                        stringResource(item.label),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        OjasTopBar(profile, onCounterPositioned = { anchors.counterCenter = it }, onOpenPoints = { nav.navigate(PointsHistoryRoute) })
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(targetState = tab, transitionSpec = { tabTransition(initialState, targetState) }, label = "tab") { shown ->
                                when (shown) {
                                    MainTab.Community -> CommunityPanes(
                                        beside = beside,
                                        jump = communityJump,
                                        onJumpConsumed = { communityJump = null },
                                        list = { onOpen ->
                                            CommunityScreen(
                                                communityVm,
                                                onOpenQuestion = { onOpen("question", it) },
                                                onAsk = { nav.navigate(AskRoute) },
                                                onAnnounce = { nav.navigate(AnnounceRoute) },
                                                daily = { canPost ->
                                                    DailyFeed(
                                                        dailyVm, canPost,
                                                        onOpen = { onOpen("daily", it) },
                                                        onCompose = { nav.navigate(ComposeDailyRoute) },
                                                        onJoin = { openPlans(PaidAccess.Membership) },
                                                    )
                                                },
                                                onOpenAnnouncement = { onOpen("announcement", it) },
                                                tab = communityTab,
                                                onTab = { communityTab = it },
                                            )
                                        },
                                        detail = { kind, id, onBack, showBack ->
                                            when (kind) {
                                                "daily" -> DailyPostScreen(dailyVm, id, onBack, showBack, embedded = true)
                                                "announcement" -> AnnouncementDetailScreen(communityVm, id, onBack, showBack, embedded = true)
                                                else -> QuestionDetailScreen(communityVm, id, onBack, showBack, embedded = true)
                                            }
                                        },
                                    )
                                    MainTab.Spiritual -> PracticeTab(
                                        practice, onPlay = play, practice = practiceStatus, practiceBusy = practiceBusy, onMarkPractice = practiceVm::mark,
                                        guided = guided,
                                        streak = practiceStreak,
                                        onOpenGuided = { nav.navigate(PracticeSessionRoute(it.id)) },
                                    )
                                    MainTab.Home -> HomeScreen(
                                        thoughtVm,
                                        firstName = profile?.displayName.orEmpty().trim().substringBefore(' '),
                                        streak = practiceStreak,
                                        onOpenPractice = { tab = MainTab.Spiritual },
                                        onSchedule = { nav.navigate(ScheduleThoughtRoute()) },
                                        onOpenThought = { nav.navigate(ThoughtRoute(it)) },
                                        about = about,
                                        onOpenAbout = { nav.navigate(AboutRoute(it)) },
                                        onOpenScheduled = { nav.navigate(ScheduledThoughtsRoute) },
                                        notificationPrefs = profile?.notificationPrefs.orEmpty(),
                                        onNotificationPrefs = { next ->
                                            val uid = profile?.uid ?: return@HomeScreen
                                            scope.launch { container.users.updateSettings(uid, mapOf("notificationPrefs" to next)) }
                                        },
                                    )
                                    MainTab.Ask -> AskPanes(
                                        beside = beside,
                                        jump = askJump,
                                        onJumpConsumed = { askJump = null },
                                        hub = { openChat, openInbox ->
                                            AskTab(
                                                profile = profile,
                                                entitlements = entitlements,
                                                understanding = understanding,
                                                onPlay = play,
                                                onOpenChat = { openChat(it.id) },
                                                onCall = { channel, video -> callStaff(channel, video, "") },
                                                onPlans = { openPlans(it) },
                                                onInbox = openInbox,
                                                onOpenAi = { nav.navigate(SwamijiAiRoute) },
                                                onSchedule = { nav.navigate(ScheduleCallRoute(it.id)) },
                                                onHistory = { nav.navigate(CallHistoryRoute) },
                                                onMyCalls = { nav.navigate(MyCallsRoute) },
                                                adminCallsReady = staffContact?.supportUid?.isNotEmpty() == true,
                                                scheduled = (if (p != null && Permissions.isChatStaff(p.viewer)) staffCallRequests else myCallRequests).orEmpty(),
                                                approving = p != null && Permissions.isChatStaff(p.viewer),
                                                onOpenRequest = { nav.navigate(CallRequestRoute(it)) },
                                            )
                                        },
                                        inbox = { onOpen, showBack, onBack ->
                                            val inboxVm: InboxViewModel = viewModel(factory = viewModelFactory { initializer { InboxViewModel(container.chat, vm.profile) } })
                                            InboxScreen(
                                                inboxVm,
                                                onBack = onBack,
                                                onOpen = { member, channel -> onOpen(member, channel.id) },
                                                onRequests = { nav.navigate(StaffRequestsRoute) },
                                                onHistory = { nav.navigate(CallHistoryRoute) },
                                                onFind = { nav.navigate(FindMemberRoute) },
                                                showBack = showBack,
                                                embedded = true,
                                            )
                                        },
                                        chat = { memberUid, channel, onBack, showBack ->
                                            ChatDestination(
                                                container, vm, profile, entitlements, entitlementsFlow, staffContact,
                                                memberUid, channel, showBack, embedded = true, onBack,
                                                onRenew = { openPlans(PaidAccess.Membership) },
                                                onSchedule = { nav.navigate(ScheduleCallRoute(channel)) },
                                                callStaff, callMember,
                                            )
                                        },
                                    )
                                    MainTab.Profile -> ProfileTab(
                                        pointsConfig = pointsConfigForProfile,
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
                                        onOpenMembers = { nav.navigate(MembersRoute) },
                                    )
                                }
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
                    onSubmitWithPhoto = { t, b, photo, done -> communityVm.ask(t, b, photo, done) },
                    sending = communityVm.sending.collectAsStateWithLifecycle().value,
                    failed = communityVm.postFailed.collectAsStateWithLifecycle().value
                )
            }
            composable<AnnounceRoute> { ComposeAnnouncementScreen(communityVm, onBack = { nav.popBackStack() }) }
            composable<DailyPostRoute> { entry -> DailyPostScreen(dailyVm, entry.toRoute<DailyPostRoute>().id, onBack = { nav.popBackStack() }) }
            composable<ComposeDailyRoute> { ComposeDailyScreen(dailyVm, onBack = { nav.popBackStack() }) }
            composable<ScheduleThoughtRoute> { entry ->
                ScheduleThoughtScreen(thoughtVm, editDate = entry.toRoute<ScheduleThoughtRoute>().date, onDone = { nav.popBackStack() })
            }
            composable<ScheduledThoughtsRoute> {
                com.ojashealing.family.ui.thought.ScheduledThoughtsScreen(
                    thoughtVm,
                    onEdit = { nav.navigate(ScheduleThoughtRoute(it)) },
                    onBack = { nav.popBackStack() }
                )
            }
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
            composable<PracticeSessionRoute> { entry ->
                val section = ContentSection.fromId(entry.toRoute<PracticeSessionRoute>().section)
                if (section == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                val sessionVm: com.ojashealing.family.ui.content.PracticeSessionViewModel = viewModel(
                    key = "practice-${section.id}",
                    factory = viewModelFactory {
                        initializer {
                            com.ojashealing.family.ui.content.PracticeSessionViewModel(
                                context.applicationContext, section, container.practiceMedia, progressStore, vm.profile.map { it?.uid }
                            )
                        }
                    }
                )
                com.ojashealing.family.ui.content.PracticeSessionScreen(sessionVm, onClose = { guidedVm.refresh(); nav.popBackStack() })
            }
            composable<VideoRoute> { entry ->
                val route = entry.toRoute<VideoRoute>()
                val kind = ContentSection.fromId(route.section)?.practiceKind()
                val status = practiceStatus
                VideoPlayerScreen(
                    route.videoId, route.title, onBack = { nav.popBackStack() }, startSec = route.startSec,
                    practice = if (kind != null && status != null) { { PracticeBar(kind, status, practiceBusy, { practiceVm.mark(kind) }) } } else null
                )
            }
            composable<PlansRoute> { entry ->
                val only = PaidAccess.entries.firstOrNull { it.id == entry.toRoute<PlansRoute>().access }
                PlansScreen(paidVm, onBack = { nav.popBackStack() }, only = only)
            }
            composable<ChatRoute> { entry ->
                val route = entry.toRoute<ChatRoute>()
                ChatDestination(
                    container, vm, profile, entitlements, entitlementsFlow, staffContact,
                    route.memberUid, route.channel, showBack = true, embedded = false,
                    onBack = { nav.popBackStack() },
                    onRenew = { openPlans(PaidAccess.Membership) },
                    onSchedule = { nav.navigate(ScheduleCallRoute(route.channel)) },
                    callStaff, callMember,
                )
            }
            composable<InboxRoute> {
                val inboxVm: InboxViewModel = viewModel(factory = viewModelFactory { initializer { InboxViewModel(container.chat, vm.profile) } })
                InboxScreen(
                    inboxVm, onBack = { nav.popBackStack() },
                    onOpen = { member, channel -> nav.navigate(ChatRoute(member, channel.id)) },
                    onRequests = { nav.navigate(StaffRequestsRoute) },
                    onHistory = { nav.navigate(CallHistoryRoute) },
                    onFind = { nav.navigate(FindMemberRoute) }
                )
            }
            composable<FindMemberRoute> {
                val me = p ?: return@composable
                // Only Swamiji and admins reach out first; senior members and members never see this
                if (me.viewer.role != com.ojashealing.family.domain.Role.Admin && me.viewer.role != com.ojashealing.family.domain.Role.Swamiji) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                val findVm: com.ojashealing.family.ui.ask.FindMemberViewModel = viewModel(factory = viewModelFactory {
                    initializer { com.ojashealing.family.ui.ask.FindMemberViewModel(container.publicProfiles::searchMembers, me.uid) }
                })
                // Admins write as the Admin team, Swamiji in the Swamiji conversation
                val channel = if (me.viewer.role == com.ojashealing.family.domain.Role.Admin) com.ojashealing.family.data.chat.ChatChannel.Admin else com.ojashealing.family.data.chat.ChatChannel.Swamiji
                com.ojashealing.family.ui.ask.FindMemberScreen(
                    findVm, onBack = { nav.popBackStack() },
                    onChat = { m -> nav.navigate(ChatRoute(m.uid, channel.id)) },
                    onCall = { m, video -> callMember(m.uid, m.name, video, channel.id, "") },
                )
            }
            composable<CallHistoryRoute> {
                com.ojashealing.family.ui.ask.CallHistoryScreen(callsVm, onBack = { nav.popBackStack() }, onCallBack = { c ->
                    val me = p ?: return@CallHistoryScreen
                    if (Permissions.isChatStaff(me.viewer) && c.memberUid != me.uid) {
                        callMember(c.memberUid, if (c.callerUid == me.uid) c.calleeName else c.callerName, c.video, c.channel, "")
                    } else {
                        val ch = com.ojashealing.family.data.chat.ChatChannel.fromId(c.channel)
                        val ok = if (c.video) Permissions.canVideoCall(me.viewer) else Permissions.canAudioCall(me.viewer, entitlements[PaidAccess.Call]?.canCall() == true)
                        if (ok) callStaff(ch, c.video, "") else openPlans(PaidAccess.Membership)
                    }
                })
            }
            composable<MyCallsRoute> {
                com.ojashealing.family.ui.ask.MyCallsScreen(callsVm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(CallRequestRoute(it)) })
            }
            composable<StaffRequestsRoute> {
                com.ojashealing.family.ui.ask.MyCallsScreen(callsVm, onBack = { nav.popBackStack() }, onOpen = { nav.navigate(CallRequestRoute(it)) }, staffView = true)
            }
            composable<ScheduleCallRoute> { entry ->
                val me = p ?: return@composable
                com.ojashealing.family.ui.ask.ScheduleCallScreen(
                    callsVm,
                    channel = entry.toRoute<ScheduleCallRoute>().channel,
                    canAudio = Permissions.canAudioCall(me.viewer, entitlements[PaidAccess.Call]?.canCall() == true),
                    canVideo = Permissions.canVideoCall(me.viewer),
                    onBack = { nav.popBackStack() },
                    onMyCalls = { nav.popBackStack(); nav.navigate(MyCallsRoute) }
                )
            }
            composable<CallRequestRoute> { entry ->
                com.ojashealing.family.ui.ask.CallRequestScreen(callsVm, entry.toRoute<CallRequestRoute>().id, onBack = { nav.popBackStack() }) { r, iAmStaff ->
                    if (iAmStaff) callMember(r.memberUid, r.memberName, r.video, r.channel, r.id)
                    else callStaff(com.ojashealing.family.data.chat.ChatChannel.fromId(r.channel), r.video, r.id)
                }
            }
            composable<CallRoute> { entry ->
                val r = entry.toRoute<CallRoute>()
                val me = p ?: return@composable
                val start = if (r.resume) null else if (r.callId.isNotEmpty()) CallStart.Incoming(r.callId, r.video)
                else CallStart.Outgoing(
                    r.calleeUid, r.memberUid, r.staffUid, r.peerName,
                    com.ojashealing.family.data.call.CallOptions(
                        video = r.video, channel = r.channel, requestId = r.requestId,
                        callerName = me.displayName, calleeName = r.peerName
                    )
                )
                val app = context.applicationContext
                val callVm: CallViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        CallViewModel(
                            container.callManager, start, me.uid,
                            // Staff may read profiles, so they see who is calling; members see "Swamiji"
                            nameOf = { uid -> runCatching { container.users.observe(uid).first()?.displayName }.getOrNull() },
                            fallbackName = if (Permissions.isChatStaff(me.viewer)) "" else swamijiName,
                            autoAnswer = r.autoAnswer
                        )
                    }
                })
                CallScreen(callVm, onClose = { nav.popBackStack() }, onMinimise = { nav.popBackStack() })
            }
            composable<MemberProfileRoute> { entry ->
                val uid = entry.toRoute<MemberProfileRoute>().uid
                val staffRole = profile?.viewer?.role
                val pointsStaff = staffRole == com.ojashealing.family.domain.Role.Admin || staffRole == com.ojashealing.family.domain.Role.Swamiji
                var managing by rememberSaveable { mutableStateOf(false) }
                val managePointsVm: com.ojashealing.family.ui.screens.PointsActionsViewModel = viewModel(factory = viewModelFactory {
                    initializer { com.ojashealing.family.ui.screens.PointsActionsViewModel(container.points) }
                })
                PublicProfileScreen(
                    remember(uid) { container.publicProfiles.observe(uid) }, goals.goals, container.locations, onBack = { nav.popBackStack() },
                    // Swamiji and admins have full control over a member's Moksha Points
                    staffActions = if (pointsStaff && uid != profile?.uid) { name ->
                        androidx.compose.material3.OutlinedButton(onClick = { managing = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Stars, null)
                            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.points_manage))
                        }
                        if (managing) com.ojashealing.family.ui.screens.ManagePointsSheet(managePointsVm, uid, name, onDismiss = { managing = false })
                    } else null,
                )
            }
            composable<ThoughtRoute> { entry -> ThoughtDetailScreen(thoughtVm, entry.toRoute<ThoughtRoute>().id, onBack = { nav.popBackStack() }) }
            composable<AnnouncementRoute> { entry -> AnnouncementDetailScreen(communityVm, entry.toRoute<AnnouncementRoute>().id, onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { entry -> AboutScreen(entry.toRoute<AboutRoute>().kind, about, onBack = { nav.popBackStack() }) }
            composable<PointsHistoryRoute> {
                val uid = profile?.uid
                val history by remember(uid) { uid?.let { container.points.observeHistory(it).catch { emit(emptyList()) } } ?: flowOf(null) }
                    .collectAsStateWithLifecycle(null)
                val targets by remember { container.points.observeLevelTargets().catch { emit(LevelTargets()) } }.collectAsStateWithLifecycle(LevelTargets())
                val historyMore by remember(uid) { uid?.let { container.points.historyHasMore(it) } ?: flowOf(false) }.collectAsStateWithLifecycle(false)
                val scope = rememberCoroutineScope()
                val pointsVm: com.ojashealing.family.ui.screens.PointsActionsViewModel = viewModel(factory = viewModelFactory {
                    initializer { com.ojashealing.family.ui.screens.PointsActionsViewModel(container.points) }
                })
                val pointsConfig by pointsVm.config.collectAsStateWithLifecycle()
                // Explain how points work before anything else, the first time this screen opens
                val explained by container.settings.pointsExplained.collectAsStateWithLifecycle(initialValue = true)
                var explain by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(explained) { if (!explained) explain = true }
                var sending by rememberSaveable { mutableStateOf(false) }
                var editing by rememberSaveable { mutableStateOf(false) }
                val role = profile?.viewer?.role
                val canEditAmounts = role == com.ojashealing.family.domain.Role.Admin || role == com.ojashealing.family.domain.Role.Swamiji
                PointsHistoryScreen(
                    profile, history, targets,
                    onBack = { nav.popBackStack() },
                    hasMore = historyMore,
                    onLoadOlder = { uid?.let { id -> scope.launch { container.points.loadOlderHistory(id) } } },
                    onHowItWorks = { explain = true },
                    onSend = { sending = true },
                )
                if (explain) com.ojashealing.family.ui.screens.PointsExplainerSheet(
                    pointsConfig, targets,
                    onDismiss = { explain = false; scope.launch { container.settings.markPointsExplained() } },
                    onEditAmounts = if (canEditAmounts) ({ explain = false; editing = true }) else null,
                )
                if (sending) profile?.let { p ->
                    com.ojashealing.family.ui.screens.SendPointsSheet(
                        pointsVm, balance = p.mokshaPoints, myLevel = p.viewer.userLevel.rank, frozen = p.transfersFrozen,
                        onDismiss = { sending = false },
                    )
                }
                if (editing) com.ojashealing.family.ui.screens.PointsAmountsSheet(pointsVm, onDismiss = { editing = false })
            }
            composable<SwamijiAiRoute> {
                val aiVm: com.ojashealing.family.ui.ask.SwamijiAiViewModel = viewModel(factory = viewModelFactory {
                    initializer { com.ojashealing.family.ui.ask.SwamijiAiViewModel(container.ai, vm.profile.map { it?.uid }) }
                })
                SwamijiAiScreen(
                    aiVm,
                    onBack = { nav.popBackStack() },
                    onAskSwamiji = { nav.popBackStack(); tab = MainTab.Ask },
                    onOpenVideo = { id, title, start -> nav.navigate(VideoRoute(id, title, startSec = start)) },
                )
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
                                CallReminders.clear(app)
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

        if (ongoing != null && !onCallScreen) {
            OngoingCallBar(ongoing, Modifier.align(Alignment.TopCenter)) { nav.navigate(CallRoute(resume = true)) { launchSingleTop = true } }
        }
        PaytmCoinOverlay(rewardEvent = reward, anchors = anchors, onDismiss = vm::clearReward)
    }
    }
}

private const val NAV_MS = Durations.Nav

/**
 * Switching tabs is a frequent action, so it stays short: the new tab fades in and drifts
 * 8dp from the side you tapped while the old one fades out quickly.
 */
private fun tabTransition(from: MainTab, to: MainTab): ContentTransform {
    val dir = if (to.ordinal > from.ordinal) 1 else -1
    val enter = fadeIn(tween(Durations.Base - 80, delayMillis = 40, easing = OjasEasing.Emphasized)) +
        slideInHorizontally(tween(Durations.Base, easing = OjasEasing.Emphasized)) { dir * (it / 40).coerceAtMost(48) }
    val exit = fadeOut(tween(Durations.Quick - 30, easing = OjasEasing.Exit))
    return enter togetherWith exit
}

private fun revealTabs(nav: NavHostController) {
    val route = nav.currentBackStackEntry?.destination?.route.orEmpty()
    if (!route.contains("CallRoute")) nav.popBackStack(TabsRoute, inclusive = false)
}

/** Opens what a notification points at. */
private fun openTarget(
    nav: NavHostController,
    target: String,
    myUid: String,
    selectCommunityTab: (Int) -> Unit = {},
    selectTab: (MainTab) -> Unit,
    setCommunityJump: (String) -> Unit = {},
    setAskJump: (String) -> Unit = {},
) {
    val (kind, id) = target.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    // Put the right tab underneath, then open the item itself, so Back lands where it belongs
    when (kind) {
        "questions" -> {
            selectTab(MainTab.Community)
            selectCommunityTab(com.ojashealing.family.ui.community.CommunityTab.QA)
            setCommunityJump("question/$id")
            revealTabs(nav)
        }
        "dailyPosts" -> {
            selectTab(MainTab.Community)
            selectCommunityTab(com.ojashealing.family.ui.community.CommunityTab.DAILY)
            setCommunityJump("daily/$id")
            revealTabs(nav)
        }
        "chats" -> {
            selectTab(MainTab.Ask)
            val uid = if (id == myUid) "" else id
            setAskJump(if (uid.isEmpty()) "chat:swamiji:" else "inbox:swamiji:$uid")
            revealTabs(nav)
        }
        "adminChats" -> {
            selectTab(MainTab.Ask)
            val uid = if (id == myUid) "" else id
            setAskJump(if (uid.isEmpty()) "chat:admin:" else "inbox:admin:$uid")
            revealTabs(nav)
        }
        "callRequests" -> if (id.isNotEmpty()) nav.navigate(CallRequestRoute(id))
        // The ringing-call listener may already have opened it
        // "calls/<id>" shows the ringing call; "calls/<id>/answer" (the notification's Answer) answers it too
        "calls" -> {
            val callId = id.substringBefore('/')
            val answer = id.endsWith("/answer")
            if (callId.isNotEmpty() && (answer || nav.currentBackStackEntry?.destination?.route?.contains("CallRoute") != true)) {
                nav.navigate(CallRoute(callId = callId, autoAnswer = answer)) { launchSingleTop = true }
            }
        }
        "thoughts" -> { selectTab(MainTab.Home); if (id.isNotEmpty()) nav.navigate(ThoughtRoute(id)) }
        "announcements" -> {
            selectTab(MainTab.Community)
            selectCommunityTab(com.ojashealing.family.ui.community.CommunityTab.NOTICES)
            if (id.isNotEmpty()) setCommunityJump("announcement/$id")
            revealTabs(nav)
        }
        "practice" -> selectTab(MainTab.Spiritual)
    }
}

@Composable
private fun ChatDestination(
    container: AppContainer,
    mainVm: MainViewModel,
    profile: com.ojashealing.family.data.user.UserProfile?,
    entitlements: Entitlements,
    entitlementsFlow: kotlinx.coroutines.flow.Flow<Entitlements>,
    staffContact: com.ojashealing.family.data.chat.StaffContact?,
    memberUid: String,
    channelId: String,
    showBack: Boolean,
    embedded: Boolean,
    onBack: () -> Unit,
    onRenew: () -> Unit,
    onSchedule: () -> Unit,
    callStaff: (com.ojashealing.family.data.chat.ChatChannel, Boolean, String) -> Unit,
    callMember: (String, String, Boolean, String, String) -> Unit,
) {
    val other = memberUid.ifEmpty { null }
    val channel = com.ojashealing.family.data.chat.ChatChannel.fromId(channelId)
    val chatVm: ChatViewModel = viewModel(key = "chat-${channel.id}-${other.orEmpty()}", factory = viewModelFactory {
        initializer {
            ChatViewModel(
                container.chat, mainVm.profile, entitlementsFlow, other, channel, container.images::uploadChatFile,
                presence = container.presence::observe,
                staffContact = container.chat.observeStaffContact().catch { emit(null) },
            )
        }
    })
    val me = profile
    val iAmStaff = me != null && Permissions.isChatStaff(me.viewer) && other != null && other != me.uid
    val calls: ((String, Boolean) -> Unit)? = when {
        me == null -> null
        iAmStaff -> { member, video -> callMember(member, chatVm.ui.value?.thread?.memberName.orEmpty(), video, channel.id, "") }
        else -> {
            val audioOk = Permissions.canAudioCall(me.viewer, entitlements[PaidAccess.Call]?.canCall() == true)
            val videoOk = Permissions.canVideoCall(me.viewer)
            val ready = channel == com.ojashealing.family.data.chat.ChatChannel.Swamiji || staffContact?.supportUid?.isNotEmpty() == true
            if (!ready) null else { _, video -> if (if (video) videoOk else audioOk) callStaff(channel, video, "") else onRenew() }
        }
    }
    ChatScreen(
        chatVm,
        onBack = onBack,
        onRenew = onRenew,
        onCall = calls,
        onSchedule = if (iAmStaff) null else onSchedule,
        showBack = showBack,
        embedded = embedded,
        saveKey = "chat-${channel.id}-${other.orEmpty()}",
    )
}

@Composable
private fun SuiteIcon(tab: MainTab, selected: Boolean, photoUrl: String, askUnread: Int) {
    BadgedBox(badge = {
        val count = if (tab == MainTab.Ask) askUnread else 0
        if (count > 0) Badge { Text(if (count > 99) "99+" else "$count") }
    }) {
        if (tab == MainTab.Profile && photoUrl.isNotEmpty()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(24.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        } else {
            // Outline when idle, filled when selected; the swap pops in from small without bouncing
            androidx.compose.animation.Crossfade(
                targetState = selected,
                animationSpec = tween(Durations.Quick),
                label = "tabIcon",
            ) { on ->
                val pop = remember { androidx.compose.animation.core.Animatable(if (on) 0.25f else 1f) }
                LaunchedEffect(on) { if (on) pop.animateTo(1f, calmSpring()) }
                Icon(
                    if (on) tab.selectedIcon else tab.icon,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
            }
        }
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

/** "Ongoing call · 04:12": tap to go back to the call. */
@Composable
private fun OngoingCallBar(call: com.ojashealing.family.data.call.CallUi, modifier: Modifier = Modifier, onReturn: () -> Unit) {
    val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(1_000) }
    }
    val time = if (call.connectedAt > 0) com.ojashealing.family.data.call.callClock(now - call.connectedAt) else "…"
    androidx.compose.material3.Surface(
        onClick = onReturn,
        color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
        contentColor = androidx.compose.ui.graphics.Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .padding(horizontal = com.ojashealing.family.ui.theme.Spacing.l, vertical = com.ojashealing.family.ui.theme.Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(com.ojashealing.family.ui.theme.Spacing.s)
        ) {
            androidx.compose.material3.Icon(
                if (call.video) androidx.compose.material.icons.Icons.Outlined.Videocam else androidx.compose.material.icons.Icons.Outlined.Call,
                contentDescription = null
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.call_ongoing_bar, call.peerName.ifBlank { time }), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Text(stringResource(R.string.call_ongoing_return), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Text(time, style = MaterialTheme.typography.labelLarge)
        }
    }
}
