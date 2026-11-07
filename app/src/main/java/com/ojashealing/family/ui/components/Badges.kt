package com.ojashealing.family.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.domain.Role
import com.ojashealing.family.ui.theme.AdminPurple
import com.ojashealing.family.ui.theme.LevelBronze
import com.ojashealing.family.ui.theme.LevelGold
import com.ojashealing.family.ui.theme.LevelLotus
import com.ojashealing.family.ui.theme.LevelSilver
import com.ojashealing.family.ui.theme.SeniorTeal
import com.ojashealing.family.ui.theme.SwamijiGold
import com.ojashealing.family.ui.theme.VerifiedBlue

/** What a role's badge looks like; null for ordinary members, who get no role badge. */
data class RoleBadgeStyle(val icon: ImageVector, val color: Color, val label: Int)

fun roleBadgeStyle(role: Role): RoleBadgeStyle? = when (role) {
    Role.Swamiji -> RoleBadgeStyle(Icons.Filled.Verified, SwamijiGold, R.string.badge_swamiji)
    Role.Admin -> RoleBadgeStyle(Icons.Filled.Verified, VerifiedBlue, R.string.badge_admin)
    Role.SeniorMember -> RoleBadgeStyle(Icons.Filled.Shield, SeniorTeal, R.string.badge_senior)
    Role.Member -> null
}

/** Paid member badge: Member Level 1 lotus, 2 premium, 3 diamond. */
fun memberBadgeColor(memberLevel: Int): Color = when (memberLevel) {
    3 -> Color(0xFF6D28D9)
    2 -> LevelGold
    else -> LevelLotus
}

fun memberBadgeIcon(memberLevel: Int): ImageVector = when (memberLevel) {
    3 -> Icons.Filled.Diamond
    2 -> Icons.Filled.WorkspacePremium
    else -> Icons.Filled.LocalFlorist
}

/** Free user level colour: 1 plain, 2 bronze, 3 silver, 4 gold. */
fun userLevelColor(level: Int): Color = when (level) {
    4 -> LevelGold
    3 -> LevelSilver
    2 -> LevelBronze
    else -> Color(0xFF9A8FA8)
}

/** The tick beside a name: gold for Swamiji, blue for admins, teal shield for senior members. */
@Composable
fun RoleTick(role: Role, size: Dp = 16.dp) {
    val style = roleBadgeStyle(role) ?: return
    val label = stringResource(style.label)
    Icon(
        style.icon,
        contentDescription = label,
        tint = style.color,
        modifier = Modifier.size(size)
    )
}

/** Small round level coin: "L3" for user levels, a gem for paid member levels. */
@Composable
fun LevelCoin(userLevel: Int, memberLevel: Int, size: Dp = 18.dp) {
    if (memberLevel >= 1) {
        val c = memberBadgeColor(memberLevel)
        val label = stringResource(R.string.badge_member_level, memberLevel)
        Box(
            Modifier.size(size).clip(CircleShape).background(c.copy(alpha = 0.16f)).semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) { Icon(memberBadgeIcon(memberLevel), contentDescription = null, tint = c, modifier = Modifier.size(size - 6.dp)) }
    } else if (userLevel >= 2) {
        val c = userLevelColor(userLevel)
        val label = stringResource(R.string.badge_user_level, userLevel)
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.7f))))
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            if (userLevel >= 4) Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(size - 6.dp))
            else Text("$userLevel", color = Color.White, style = com.ojashealing.family.ui.theme.CoinDigitStyle)
        }
    }
}

/** Every badge for a person, for a name line: role tick then level coin. */
@Composable
fun NameBadges(role: Role, userLevel: Int, memberLevel: Int, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        RoleTick(role, size)
        LevelCoin(userLevel, memberLevel, size + 2.dp)
    }
}

/** A labelled pill for profile headers, e.g. "✓ Swamiji", "Level 3", "Member Level 2". */
@Composable
fun BadgePill(icon: ImageVector, text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The row of pills on a profile: role, user level and member level. */
@Composable
fun ProfileBadges(role: Role, userLevel: Int, memberLevel: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        roleBadgeStyle(role)?.let { BadgePill(it.icon, stringResource(it.label), it.color) }
        BadgePill(
            Icons.Filled.Star,
            stringResource(R.string.badge_user_level, userLevel),
            userLevelColor(userLevel)
        )
        if (memberLevel >= 1) BadgePill(memberBadgeIcon(memberLevel), stringResource(R.string.badge_member_level, memberLevel), memberBadgeColor(memberLevel))
    }
}
