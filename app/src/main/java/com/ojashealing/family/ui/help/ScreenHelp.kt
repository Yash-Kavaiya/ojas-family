package com.ojashealing.family.ui.help

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ojashealing.family.R
import com.ojashealing.family.ui.community.CommunityTab
import com.ojashealing.family.ui.content.PracticePart
import com.ojashealing.family.ui.navigation.MainTab
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.launch

/** One line of "who can do what": a plan or feature, and its rule. */
data class HelpRow(@StringRes val label: Int, @StringRes val text: Int)

private fun plans(free: Int, silver: Int, gold: Int) =
    listOf(HelpRow(R.string.help_free, free), HelpRow(R.string.help_silver, silver), HelpRow(R.string.help_gold, gold))

/** The help for one screen. [key] is saved on the phone once its help has been shown; don't rename it. */
enum class HelpTopic(val key: String, @StringRes val title: Int, @StringRes val body: Int, val rows: List<HelpRow> = emptyList()) {
    Thought("thought", R.string.notif_group_thought, R.string.help_thought_body, listOf(
        HelpRow(R.string.help_free, R.string.help_thought_free), HelpRow(R.string.help_silver_gold, R.string.help_thought_paid),
    )),
    Qa("qa", R.string.community_qa, R.string.help_qa_body, plans(R.string.help_qa_free, R.string.help_qa_silver, R.string.help_qa_gold)),
    Daily("daily", R.string.community_daily, R.string.help_daily_body, plans(R.string.help_daily_free, R.string.help_daily_silver, R.string.help_daily_gold)),
    Notices("notices", R.string.community_notices, R.string.help_notices_body, plans(R.string.help_notices_free, R.string.help_notices_silver, R.string.help_notices_gold)),
    Read("read", R.string.read_title, R.string.help_read_body),
    Practice("practice", R.string.practice_tab_daily, R.string.help_practice_body, plans(R.string.help_practice_free, R.string.help_practice_silver, R.string.help_practice_gold)),
    Freedom("freedom", R.string.practice_tab_freedom, R.string.help_freedom_body),
    Ask("ask", R.string.ask_part_ask, R.string.help_ask_body, plans(R.string.help_ask_free, R.string.help_ask_silver, R.string.help_ask_gold)),
    Video("video", R.string.ask_part_video, R.string.help_video_body),
    Books("books", R.string.ask_part_books, R.string.help_books_body),
    Profile("profile", R.string.tab_profile, R.string.help_profile_body, listOf(
        HelpRow(R.string.help_row_photo, R.string.help_profile_photo), HelpRow(R.string.help_row_hide, R.string.help_profile_hide),
    )),
}

/** The help for what is on screen: the bottom tab and the tab inside it. */
fun helpTopicFor(tab: MainTab, communityTab: Int, practicePart: Int, askPart: Int): HelpTopic = when (tab) {
    MainTab.Home -> HelpTopic.Thought
    MainTab.Profile -> HelpTopic.Profile
    MainTab.Community -> when (communityTab) {
        CommunityTab.DAILY -> HelpTopic.Daily
        CommunityTab.NOTICES -> HelpTopic.Notices
        else -> HelpTopic.Qa
    }
    MainTab.Spiritual -> when (practicePart) {
        PracticePart.DAILY -> HelpTopic.Practice
        PracticePart.FREEDOM -> HelpTopic.Freedom
        else -> HelpTopic.Read
    }
    MainTab.Ask -> when (askPart) {
        1 -> HelpTopic.Video
        2 -> HelpTopic.Books
        else -> HelpTopic.Ask
    }
}

/** A screen's help opens by itself once, only on the main tabs, after the member's profile has loaded. */
fun shouldAutoOpenHelp(topic: HelpTopic, seen: Set<String>, onTabs: Boolean, profileLoaded: Boolean, sheetOpen: Boolean): Boolean =
    onTabs && profileLoaded && !sheetOpen && topic.key !in seen

/** Which screens' help has been shown. A file of its own, outside Android backup, so a new install starts fresh. */
class HelpSeen(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("screen_help", Context.MODE_PRIVATE)
    val seen: Set<String> get() = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun mark(topic: HelpTopic) {
        prefs.edit().putStringSet(KEY, seen + topic.key).apply()
    }

    private companion object {
        const val KEY = "seen"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHelpSheet(topic: HelpTopic, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            Text(stringResource(topic.title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(topic.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (topic.rows.isNotEmpty()) {
                Text(stringResource(R.string.help_access), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                topic.rows.forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(row.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(row.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.help_got_it)) }
        }
    }
}
