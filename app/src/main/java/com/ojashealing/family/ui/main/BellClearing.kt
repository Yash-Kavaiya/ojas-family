package com.ojashealing.family.ui.main

import com.ojashealing.family.data.notify.NotifGroup
import com.ojashealing.family.ui.community.CommunityTab
import com.ojashealing.family.ui.navigation.MainTab

/** The group whose new posts the member is looking at on the tabs screen, or null for a tab without any. */
fun groupOnScreen(tab: MainTab, communityTab: Int): NotifGroup? = when (tab) {
    MainTab.Home -> NotifGroup.Thought
    MainTab.Community -> when (communityTab) {
        CommunityTab.DAILY -> NotifGroup.Activity
        CommunityTab.NOTICES -> NotifGroup.Announcement
        else -> NotifGroup.Qa
    }
    else -> null
}

/** The bell target a tab shows by itself (goal results, the face photo decision), or null. */
fun targetOnScreen(tab: MainTab, practicePart: Int): String? = when {
    tab == MainTab.Spiritual && practicePart == com.ojashealing.family.ui.content.PracticePart.FREEDOM -> "goals"
    tab == MainTab.Profile -> "profile"
    else -> null
}
