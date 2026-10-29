package com.ojashealing.family

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojashealing.family.data.repository.OjasRepository
import com.ojashealing.family.ui.components.OjasTopBar
import com.ojashealing.family.ui.components.PaytmCoinOverlay
import com.ojashealing.family.ui.components.RewardAnchors
import com.ojashealing.family.ui.screens.ask.AskScreen
import com.ojashealing.family.ui.screens.community.CommunityScreen
import com.ojashealing.family.ui.screens.profile.ProfileScreen
import com.ojashealing.family.ui.screens.spiritual.SpiritualActivitiesScreen
import com.ojashealing.family.ui.screens.thought.ThoughtOfDayScreen
import com.ojashealing.family.ui.theme.OjasFamilyTheme
import com.ojashealing.family.ui.theme.SacredGold
import com.ojashealing.family.ui.theme.SaffronPrimary

enum class OjasTab(val title: String, val index: Int) {
    COMMUNITY("Community", 0),
    SPIRITUAL("Spiritual", 1),
    THOUGHT_OF_DAY("Thought", 2),
    ASK("Ask", 3),
    PROFILE("Profile", 4)
}

class MainActivity : ComponentActivity() {

    private val repository = OjasRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check daily login reward (+1 Moksha Point for opening free app today)
        repository.checkDailyLoginReward()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            val userProfile by repository.userProfile.collectAsStateWithLifecycle()
            val thoughts by repository.thoughts.collectAsStateWithLifecycle()
            val questions by repository.questions.collectAsStateWithLifecycle()
            val dailyActivities by repository.dailyActivities.collectAsStateWithLifecycle()
            val announcements by repository.announcements.collectAsStateWithLifecycle()
            val goals by repository.goals.collectAsStateWithLifecycle()
            val activities by repository.activities.collectAsStateWithLifecycle()
            val understandingTopics by repository.understandingTopics.collectAsStateWithLifecycle()
            val chatMessages by repository.chatMessages.collectAsStateWithLifecycle()
            val scheduledCalls by repository.scheduledCalls.collectAsStateWithLifecycle()
            val rewardEvent by repository.rewardEvent.collectAsStateWithLifecycle()

            // Default to Tab 3 (Thought of the day - Home) or Tab 1
            var currentTab by remember { mutableStateOf(OjasTab.THOUGHT_OF_DAY) }
            val rewardAnchors = remember { RewardAnchors() }

            OjasFamilyTheme(darkTheme = isDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Watches taps without consuming them, so the coin can start where the user tapped
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.firstOrNull()?.let { rewardAnchors.lastTap = it.position }
                                }
                            }
                        }
                ) {
                    Scaffold(
                        topBar = {
                            OjasTopBar(
                                profile = userProfile,
                                onUserLevelChange = { repository.setUserLevel(it) },
                                onPaidLevelChange = { repository.setPaidLevel(it) },
                                onAdminToggle = { repository.setAdmin(it) },
                                onCounterPositioned = { rewardAnchors.counterCenter = it }
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                modifier = Modifier
                                    .testTag("bottom_nav_bar")
                            ) {
                                // 1st Tab: Community
                                NavigationBarItem(
                                    selected = currentTab == OjasTab.COMMUNITY,
                                    onClick = { currentTab = OjasTab.COMMUNITY },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == OjasTab.COMMUNITY) Icons.Default.Groups else Icons.Outlined.Groups,
                                            contentDescription = "Community"
                                        )
                                    },
                                    label = { Text("Community", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SaffronPrimary,
                                        selectedTextColor = SaffronPrimary,
                                        indicatorColor = SaffronPrimary.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag("nav_community")
                                )

                                // 2nd Tab: Spiritual Activities
                                NavigationBarItem(
                                    selected = currentTab == OjasTab.SPIRITUAL,
                                    onClick = { currentTab = OjasTab.SPIRITUAL },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == OjasTab.SPIRITUAL) Icons.Default.SelfImprovement else Icons.Outlined.SelfImprovement,
                                            contentDescription = "Spiritual"
                                        )
                                    },
                                    label = { Text("Spiritual", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SaffronPrimary,
                                        selectedTextColor = SaffronPrimary,
                                        indicatorColor = SaffronPrimary.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag("nav_spiritual")
                                )

                                // 3rd Tab: Thought of the Day (Home)
                                NavigationBarItem(
                                    selected = currentTab == OjasTab.THOUGHT_OF_DAY,
                                    onClick = { currentTab = OjasTab.THOUGHT_OF_DAY },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == OjasTab.THOUGHT_OF_DAY) Icons.Default.WbSunny else Icons.Outlined.WbSunny,
                                            contentDescription = "Thought"
                                        )
                                    },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SacredGold,
                                        selectedTextColor = SaffronPrimary,
                                        indicatorColor = SacredGold.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.testTag("nav_thought")
                                )

                                // 4th Tab: Ask
                                NavigationBarItem(
                                    selected = currentTab == OjasTab.ASK,
                                    onClick = { currentTab = OjasTab.ASK },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == OjasTab.ASK) Icons.Default.HelpCenter else Icons.Outlined.HelpCenter,
                                            contentDescription = "Ask"
                                        )
                                    },
                                    label = { Text("Ask", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SaffronPrimary,
                                        selectedTextColor = SaffronPrimary,
                                        indicatorColor = SaffronPrimary.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag("nav_ask")
                                )

                                // 5th Tab: Profile
                                NavigationBarItem(
                                    selected = currentTab == OjasTab.PROFILE,
                                    onClick = { currentTab = OjasTab.PROFILE },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentTab == OjasTab.PROFILE) Icons.Default.AccountCircle else Icons.Outlined.AccountCircle,
                                            contentDescription = "Profile"
                                        )
                                    },
                                    label = { Text("Profile", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SaffronPrimary,
                                        selectedTextColor = SaffronPrimary,
                                        indicatorColor = SaffronPrimary.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag("nav_profile")
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                // Screens with their own Scaffold would otherwise add system-bar padding twice
                                .consumeWindowInsets(innerPadding)
                        ) {
                            when (currentTab) {
                                OjasTab.COMMUNITY -> CommunityScreen(
                                    userProfile = userProfile,
                                    questions = questions,
                                    dailyActivities = dailyActivities,
                                    announcements = announcements,
                                    onLikeQuestion = { repository.toggleLikeQuestion(it) },
                                    onAskQuestion = { title, body, tags -> repository.askQuestion(title, body, tags) },
                                    onAnswerQuestion = { qId, ans -> repository.answerQuestion(qId, ans) },
                                    onLikeDailyActivity = { repository.toggleLikeDailyActivity(it) },
                                    onAddDailyActivity = { title, desc, cat -> repository.addDailyActivityPost(title, desc, cat) }
                                )

                                OjasTab.SPIRITUAL -> SpiritualActivitiesScreen(
                                    userProfile = userProfile,
                                    activities = activities,
                                    onCompleteActivity = { pts, reason -> repository.triggerReward(pts, reason) },
                                    onUpdateReminderTime = { id, time ->
                                        if (id == "act_morning_protection") {
                                            repository.updateNotifications(userProfile.notifications.copy(morningPracticeTime = time))
                                        } else {
                                            repository.updateNotifications(userProfile.notifications.copy(nightPracticeTime = time))
                                        }
                                    }
                                )

                                OjasTab.THOUGHT_OF_DAY -> ThoughtOfDayScreen(
                                    userProfile = userProfile,
                                    thoughts = thoughts,
                                    onLikeThought = { repository.toggleLikeThought(it) },
                                    onAddComment = { id, text, img, link -> repository.addCommentToThought(id, text, img, link) },
                                    onScheduleThought = { title, quote, comm, chakra, mantra ->
                                        repository.scheduleThought(title, quote, comm, chakra, mantra)
                                    }
                                )

                                OjasTab.ASK -> AskScreen(
                                    userProfile = userProfile,
                                    understandingTopics = understandingTopics,
                                    chatMessages = chatMessages,
                                    scheduledCalls = scheduledCalls,
                                    onSendMessage = { repository.sendChatMessage(it) },
                                    onBookCall = { plan, time -> repository.scheduleCall(plan, time) }
                                )

                                OjasTab.PROFILE -> ProfileScreen(
                                    userProfile = userProfile,
                                    goals = goals,
                                    isDarkTheme = isDarkTheme,
                                    onToggleTheme = { isDarkTheme = it },
                                    onUpdateProfile = { name, dob, gender, country, state, city ->
                                        repository.updateProfile(name, dob, gender, country, state, city)
                                    },
                                    onToggleProfileHidden = { repository.toggleProfileHidden(it) },
                                    onToggleGoalsHidden = { repository.toggleGoalsHidden(it) },
                                    onUpdateNotifications = { repository.updateNotifications(it) },
                                    onCompleteGoal = { id, note, img -> repository.completeGoal(id, note, img) }
                                )
                            }
                        }
                    }

                    // Small coin for likes/comments, full Paytm burst for milestones
                    PaytmCoinOverlay(
                        rewardEvent = rewardEvent,
                        anchors = rewardAnchors,
                        onDismiss = { repository.clearRewardEvent() }
                    )
                }
            }
        }
    }
}
