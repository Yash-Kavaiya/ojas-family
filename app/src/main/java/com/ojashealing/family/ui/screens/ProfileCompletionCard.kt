package com.ojashealing.family.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.ProfileItem
import com.ojashealing.family.domain.profileCompletion
import com.ojashealing.family.ui.components.CelebrationDialog
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.markCelebrated
import com.ojashealing.family.ui.components.newlyReached
import com.ojashealing.family.ui.theme.Durations
import com.ojashealing.family.ui.theme.OjasEasing
import com.ojashealing.family.ui.theme.OjasGradients
import com.ojashealing.family.ui.theme.Spacing

private val Gold = Color(0xFFE0A100)

private fun ProfileItem.icon(): ImageVector = when (this) {
    ProfileItem.Photo -> Icons.Outlined.PhotoCamera
    ProfileItem.Name -> Icons.Outlined.Badge
    ProfileItem.Dob -> Icons.Outlined.Cake
    ProfileItem.Gender -> Icons.Outlined.Person
    ProfileItem.Location -> Icons.Outlined.Place
    ProfileItem.About -> Icons.Outlined.Info
    ProfileItem.Language -> Icons.Outlined.Language
}

private fun ProfileItem.label(): Int = when (this) {
    ProfileItem.Photo -> R.string.profile_item_photo
    ProfileItem.Name -> R.string.profile_item_name
    ProfileItem.Dob -> R.string.profile_item_dob
    ProfileItem.Gender -> R.string.profile_item_gender
    ProfileItem.Location -> R.string.profile_item_location
    ProfileItem.About -> R.string.profile_item_about
    ProfileItem.Language -> R.string.profile_item_language
}

/**
 * How complete the profile is, as a ring and the items still missing, each with the points it
 * earns. Tapping an item opens the right place to fill it in. At 100% the ring turns gold and a
 * one-time celebration plays.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileCompletionCard(
    profile: UserProfile,
    itemPoints: Int,
    completeBonus: Int,
    onFill: (ProfileItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = profileCompletion(profile)
    val ring by animateFloatAsState(c.percent / 100f, tween(Durations.Slow + 200, easing = OjasEasing.Emphasized), label = "completion")
    val context = LocalContext.current
    var celebrate by remember { mutableStateOf(false) }
    LaunchedEffect(c.complete) {
        if (c.complete && newlyReached(context, "profile_complete_${profile.uid}", setOf("done")).isNotEmpty()) celebrate = true
    }
    if (celebrate) {
        CelebrationDialog(
            title = stringResource(R.string.profile_complete_title),
            message = stringResource(R.string.profile_complete_body, completeBonus),
            onDismiss = { celebrate = false; markCelebrated(context, "profile_complete_${profile.uid}", setOf("done")) },
        )
    }

    OjasCard(modifier = modifier.animateContentSize(tween(Durations.Base, easing = OjasEasing.Emphasized))) {
        AnimatedContent(
            targetState = c.complete,
            transitionSpec = { fadeIn(tween(Durations.Base)) togetherWith fadeOut(tween(Durations.Quick)) },
            label = "completeSwap",
        ) { complete ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        val track = MaterialTheme.colorScheme.surfaceContainerHighest
                        val accent = if (complete) Gold else MaterialTheme.colorScheme.primary
                        Canvas(Modifier.size(64.dp)) {
                            val stroke = 7.dp.toPx()
                            val inset = stroke / 2
                            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                            drawArc(track, 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = Stroke(stroke))
                            drawArc(accent, -90f, 360f * ring, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                        if (complete) Icon(Icons.Filled.Verified, null, tint = Gold, modifier = Modifier.size(28.dp))
                        else Text("${c.percent}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (complete) stringResource(R.string.profile_completion_done) else stringResource(R.string.profile_completion_title, c.percent),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (complete) stringResource(R.string.profile_completion_done_body)
                            else stringResource(R.string.profile_completion_waiting, c.missing.size * itemPoints + completeBonus),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!complete) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        c.missing.forEach { item ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable(role = Role.Button) { onFill(item) }
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(item.icon(), null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(item.label()), style = MaterialTheme.typography.labelLarge)
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(Brush.linearGradient(OjasGradients.gold)).padding(horizontal = 8.dp, vertical = 2.dp)
                                ) { Text("+$itemPoints", style = com.ojashealing.family.ui.theme.BrandNumberSmallStyle, color = Color(0xFF3B2300)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
