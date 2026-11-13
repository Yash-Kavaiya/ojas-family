package com.ojashealing.family.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.ojashealing.family.R

// Order is the spec's tab order; Home (Thought of the Day) sits in the middle and is the start tab
enum class MainTab(@StringRes val label: Int, val selectedIcon: ImageVector, val icon: ImageVector) {
    Community(R.string.tab_community, Icons.Filled.Groups, Icons.Outlined.Groups),
    Spiritual(R.string.tab_spiritual, Icons.Filled.SelfImprovement, Icons.Outlined.SelfImprovement),
    Home(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Ask(R.string.tab_ask, Icons.Filled.QuestionAnswer, Icons.Outlined.QuestionAnswer),
    Profile(R.string.tab_profile, Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle)
}

@Composable
fun OjasBottomBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier, photoUrl: String = "", badges: Map<MainTab, Int> = emptyMap()) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        modifier = modifier.shadow(8.dp).testTag("bottom_nav_bar")
    ) {
        MainTab.entries.forEach { tab ->
            val selected = tab == current
            val label = stringResource(tab.label)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                  // Unread messages show as a count on the tab, like WhatsApp
                  val count = badges[tab] ?: 0
                  androidx.compose.material3.BadgedBox(badge = {
                      if (count > 0) androidx.compose.material3.Badge { Text(if (count > 99) "99+" else "$count") }
                  }) {
                    // The member's own photo stands in for the Profile icon
                    if (tab == MainTab.Profile && photoUrl.isNotEmpty()) {
                        coil.compose.AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                                .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    } else Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                  }
                },
                label = {
                    // Hindi and Gujarati labels can be wider than English; never wrap to a second line
                    com.ojashealing.family.ui.components.FitText(label, MaterialTheme.typography.labelMedium, Modifier.testTag("nav_label"))
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_${tab.name}")
            )
        }
    }
}
