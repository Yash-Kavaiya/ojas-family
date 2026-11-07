package com.ojashealing.family.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import com.ojashealing.family.data.paid.PaidAccess
import com.ojashealing.family.ui.paid.formatDate
import com.ojashealing.family.ui.admin.showAdminEntry
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.goals.Goal
import com.ojashealing.family.ui.goals.ProfileGoalsCard
import com.ojashealing.family.ui.goals.ProfileGoalsState
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
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit = {},
    onOpenModeration: () -> Unit = {},
    goals: ProfileGoalsState = ProfileGoalsState(),
    onSubmitGoal: (Goal) -> Unit = {},
    onPickPhoto: (Uri) -> Unit = {},
    photoUploading: Boolean = false,
    @StringRes photoError: Int? = null,
    entitlements: com.ojashealing.family.data.paid.Entitlements = com.ojashealing.family.data.paid.Entitlements(),
    onPlans: () -> Unit = {},
    onOpenMembers: () -> Unit = {}
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
            IdentityCard(profile, locations, onPickPhoto, photoUploading, photoError)
            StatsRow(profile)
            MembershipCard(profile, entitlements, onPlans)
            OutlinedButton(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_edit_profile), modifier = Modifier.padding(start = Spacing.s))
            }
            if (showAdminEntry(profile.viewer)) {
                OutlinedButton(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.admin_title), modifier = Modifier.padding(start = Spacing.s))
                }
                OutlinedButton(onClick = onOpenModeration, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.moderation_title), modifier = Modifier.padding(start = Spacing.s))
                }
                OutlinedButton(onClick = onOpenMembers, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.members_title), modifier = Modifier.padding(start = Spacing.s))
                }
            }
            ProfileGoalsCard(goals, onSubmit = onSubmitGoal)
        }
    }
}

@Composable
private fun IdentityCard(
    profile: UserProfile,
    locations: LocationRepository,
    onPickPhoto: (Uri) -> Unit,
    uploading: Boolean,
    @StringRes photoError: Int?
) {
    // Pick, then crop and zoom in a circle before uploading
    var cropping by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) cropping = uri }
    cropping?.let { src ->
        com.ojashealing.family.ui.components.PhotoCropDialog(src, onDismiss = { cropping = null }, onCropped = { cropping = null; onPickPhoto(it) })
    }
    val changePhoto = stringResource(R.string.profile_change_photo)
    OjasCard {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // Tapping the picture changes it; the camera badge says so
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clickable(enabled = !uploading, onClickLabel = changePhoto, role = Role.Button) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
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
                if (uploading) CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            }
            if (profile.photoUrl.isEmpty() && !uploading) {
                Text(changePhoto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            photoError?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(profile.displayName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                com.ojashealing.family.ui.components.RoleTick(profile.viewer.role, 22.dp)
            }

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
            com.ojashealing.family.ui.components.ProfileBadges(profile.viewer.role, profile.viewer.userLevel.rank, profile.viewer.memberLevel.rank)
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

/** Spec: paid membership (monthly) with member levels after 3 and 6 months. */
@Composable
private fun MembershipCard(profile: UserProfile, entitlements: com.ojashealing.family.data.paid.Entitlements, onPlans: () -> Unit) {
    val m = entitlements[PaidAccess.Membership]?.takeIf { it.isActive() }
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.membership_title), style = MaterialTheme.typography.titleLarge)
        }
        if (m != null) {
            Text(stringResource(R.string.membership_active_until, formatDate(m.expiresAt)), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.profile_member_level, profile.viewer.memberLevel.rank), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onPlans) { Text(stringResource(R.string.membership_plans)) }
        } else {
            Text(stringResource(R.string.membership_benefits), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onPlans) { Text(stringResource(R.string.membership_join)) }
        }
    }
}
