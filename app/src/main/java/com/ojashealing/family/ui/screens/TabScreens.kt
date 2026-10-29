package com.ojashealing.family.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.HelpCenter
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ojashealing.family.R
import com.ojashealing.family.ui.components.EmptyState
import com.ojashealing.family.ui.components.SectionHeader

// Community, Practice, Home and Ask get their real content in phases 2 and 3; until then each says what will come
@Composable
private fun UpcomingTab(@StringRes title: Int, icon: ImageVector, @StringRes emptyTitle: Int, @StringRes emptyBody: Int) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(title = stringResource(title))
        EmptyState(icon = icon, title = stringResource(emptyTitle), body = stringResource(emptyBody), modifier = Modifier.weight(1f))
    }
}

@Composable
fun CommunityTab() = UpcomingTab(R.string.tab_community, Icons.Outlined.Forum, R.string.empty_community_title, R.string.empty_community_body)

@Composable
fun SpiritualTab() = UpcomingTab(R.string.tab_spiritual, Icons.Outlined.SelfImprovement, R.string.empty_spiritual_title, R.string.empty_spiritual_body)

@Composable
fun HomeTab() = UpcomingTab(R.string.notif_thought, Icons.Outlined.WbSunny, R.string.empty_home_title, R.string.empty_home_body)

@Composable
fun AskTab() = UpcomingTab(R.string.tab_ask, Icons.Outlined.HelpCenter, R.string.empty_ask_title, R.string.empty_ask_body)
