package com.ojashealing.family.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.location.LocationRepository
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.ageOn
import com.ojashealing.family.ui.components.LoadingState
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.components.SectionHeader
import com.ojashealing.family.ui.theme.Spacing

@Composable
fun ProfileTab(
    profile: UserProfile?,
    locations: LocationRepository,
    onEditProfile: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (profile == null) {
        LoadingState()
        return
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(title = stringResource(R.string.tab_profile)) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            IdentityCard(profile, locations)
            StatsRow(profile)
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_edit_profile), modifier = Modifier.padding(start = Spacing.s))
            }
            OjasCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.profile_goals_title), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.profile_goals_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IdentityCard(profile: UserProfile, locations: LocationRepository) {
    OjasCard {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (profile.photoUrl.isNotEmpty()) {
                    AsyncImage(model = profile.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp))
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(profile.displayName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

            val age = LocalDateLike.parse(profile.dob)?.let { ageOn(it, LocalDateLike.today()) }
            val place = listOfNotNull(
                profile.city.takeIf { it.isNotEmpty() },
                locations.states(profile.country, "").firstOrNull { it.code == profile.state }?.name,
                locations.country(profile.country)?.name
            ).joinToString(", ").ifEmpty { stringResource(R.string.profile_location_unset) }
            Text(
                listOfNotNull(age?.let { stringResource(R.string.profile_age, it) }, place).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.profile_level, profile.viewer.userLevel.rank)) })
                if (profile.viewer.memberLevel != MemberLevel.None) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.profile_member_level, profile.viewer.memberLevel.rank)) })
                }
            }
        }
    }
}

// Values carry the weight, labels sit underneath in caption size
@Composable
private fun StatsRow(profile: UserProfile) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        StatCard(value = profile.mokshaPoints.toString(), label = stringResource(R.string.profile_points), modifier = Modifier.weight(1f))
        StatCard(
            value = profile.activeDays.toString(),
            label = stringResource(R.string.profile_days_label),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    OjasCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
