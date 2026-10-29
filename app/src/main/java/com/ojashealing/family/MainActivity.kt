package com.ojashealing.family

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
import com.ojashealing.family.ui.navigation.MainTab
import com.ojashealing.family.ui.navigation.OjasBottomBar
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

class MainActivity : AppCompatActivity() {

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
            var currentTab by remember { mutableStateOf(MainTab.Home) }
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
                        bottomBar = { OjasBottomBar(current = currentTab, onSelect = { currentTab = it }) },
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
                                MainTab.Community -> CommunityScreen(
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

                                MainTab.Spiritual -> SpiritualActivitiesScreen(
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

                                MainTab.Home -> ThoughtOfDayScreen(
                                    userProfile = userProfile,
                                    thoughts = thoughts,
                                    onLikeThought = { repository.toggleLikeThought(it) },
                                    onAddComment = { id, text, img, link -> repository.addCommentToThought(id, text, img, link) },
                                    onScheduleThought = { title, quote, comm, chakra, mantra ->
                                        repository.scheduleThought(title, quote, comm, chakra, mantra)
                                    }
                                )

                                MainTab.Ask -> AskScreen(
                                    userProfile = userProfile,
                                    understandingTopics = understandingTopics,
                                    chatMessages = chatMessages,
                                    scheduledCalls = scheduledCalls,
                                    onSendMessage = { repository.sendChatMessage(it) },
                                    onBookCall = { plan, time -> repository.scheduleCall(plan, time) }
                                )

                                MainTab.Profile -> ProfileScreen(
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
