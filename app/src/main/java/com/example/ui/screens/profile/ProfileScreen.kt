package com.example.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    goals: List<Goal>,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onUpdateProfile: (String, String, String, String, String, String) -> Unit,
    onToggleProfileHidden: (Boolean) -> Unit,
    onToggleGoalsHidden: (Boolean) -> Unit,
    onUpdateNotifications: (NotificationSettings) -> Unit,
    onCompleteGoal: (String, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var selectedGoalForProof by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Avatar and Identity
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(SaffronPrimary, SacredGold)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userProfile.name.take(1),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = userProfile.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${userProfile.gender} • Age ${userProfile.age}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${userProfile.city}, ${userProfile.state}, ${userProfile.country}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Edit and Settings Buttons
                        Row {
                            IconButton(onClick = { showEditProfileDialog = true }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit Profile", tint = SaffronPrimary)
                            }
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Moksha Points Big Counter Pill
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(SacredGold),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("ॐ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E1F00))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Moksha Points Earned",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${userProfile.mokshaPoints}",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SaffronPrimary
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SaffronPrimary
                                ) {
                                    Text(
                                        text = userProfile.userLevel.title.take(15),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = userProfile.paidLevel.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SacredGold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Streak and Days Active
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔥", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Sadhana Streak", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${userProfile.streakDays} Days", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📿", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text("Days Active", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${userProfile.daysActive} Days", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Suggested & Achieved Goals Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "App Suggested Goals & Sadhana",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Realize practical goals to accumulate high Moksha Points",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ChakraGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${goals.count { it.isAchieved }}/${goals.size} Done",
                        color = ChakraGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Goals List
        items(goals, key = { it.id }) { goal ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (goal.isAchieved)
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(if (goal.isAchieved) 0.dp else 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (goal.isAchieved) ChakraGreen.copy(alpha = 0.15f) else SaffronPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = goal.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (goal.isAchieved) ChakraGreen else SaffronPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = SacredGold, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+${goal.mokshaReward} pts",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldDark
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = goal.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = goal.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    if (goal.targetDays > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LinearProgressIndicator(
                                progress = { (goal.progressDays.toFloat() / goal.targetDays.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = SaffronPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${goal.progressDays}/${goal.targetDays} days",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (goal.isAchieved) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Achieved", tint = ChakraGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Achieved! ${goal.proofNote ?: ""}",
                                fontSize = 11.sp,
                                color = ChakraGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Button(
                            onClick = { selectedGoalForProof = goal },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Post Proof & Claim", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Quick Settings Shortcuts
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Dark Theme", fontSize = 13.sp)
                        }
                        Switch(checked = isDarkTheme, onCheckedChange = onToggleTheme)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNotificationsDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Detailed Notification Settings", fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Edit Profile Modal (With Incremental Search for Country, State, City)
    if (showEditProfileDialog) {
        EditProfileModal(
            currentProfile = userProfile,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, dob, gender, country, state, city ->
                onUpdateProfile(name, dob, gender, country, state, city)
                showEditProfileDialog = false
            }
        )
    }

    // App Settings Modal
    if (showSettingsDialog) {
        SettingsDialog(
            userProfile = userProfile,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onToggleProfileHidden = onToggleProfileHidden,
            onToggleGoalsHidden = onToggleGoalsHidden,
            onOpenNotifications = {
                showSettingsDialog = false
                showNotificationsDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Notifications Dialog
    if (showNotificationsDialog) {
        NotificationsSettingsDialog(
            current = userProfile.notifications,
            onDismiss = { showNotificationsDialog = false },
            onSave = { newSettings ->
                onUpdateNotifications(newSettings)
                showNotificationsDialog = false
            }
        )
    }

    // Complete Goal Proof Dialog
    selectedGoalForProof?.let { goal ->
        GoalProofDialog(
            goal = goal,
            onDismiss = { selectedGoalForProof = null },
            onSubmit = { note ->
                onCompleteGoal(goal.id, note, "photo_uploaded_proof")
                selectedGoalForProof = null
            }
        )
    }
}

// Edit Profile Modal with Incremental Search for Country, State, and City/Village
@Composable
private fun EditProfileModal(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentProfile.name) }
    var dob by remember { mutableStateOf(currentProfile.dob) }
    var gender by remember { mutableStateOf(currentProfile.gender) }
    var country by remember { mutableStateOf(currentProfile.country) }
    var state by remember { mutableStateOf(currentProfile.state) }
    var city by remember { mutableStateOf(currentProfile.city) }

    var showCountryPicker by remember { mutableStateOf(false) }
    var showStatePicker by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit Profile", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = { Text("DOB (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gender") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "*Only your calculated age is visible on public profile.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Incremental Search Country Field
                OutlinedCard(
                    onClick = { showCountryPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Country (Incremental Search)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(country, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.Search, contentDescription = null, tint = SaffronPrimary)
                    }
                }

                // Incremental Search State Field (Auto fill according to Country)
                OutlinedCard(
                    onClick = { showStatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("State (Filtered by $country)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(state, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.Search, contentDescription = null, tint = SaffronPrimary)
                    }
                }

                // Incremental Search City or Village Field (Auto fill according to State)
                OutlinedCard(
                    onClick = { showCityPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("City or Village (Filtered by $state)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(city, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.Search, contentDescription = null, tint = SaffronPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSave(name.trim(), dob.trim(), gender.trim(), country, state, city)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Profile Changes")
                }
            }
        }
    }

    // Country Search Picker Dialog
    if (showCountryPicker) {
        SearchableListDialog(
            title = "Select Country",
            items = LocationDirectory.countries,
            onSelect = { selected ->
                country = selected
                state = LocationDirectory.getStatesForCountry(selected).firstOrNull() ?: ""
                city = LocationDirectory.getCitiesForState(state).firstOrNull() ?: ""
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }

    // State Search Picker Dialog
    if (showStatePicker) {
        SearchableListDialog(
            title = "Select State ($country)",
            items = LocationDirectory.getStatesForCountry(country),
            onSelect = { selected ->
                state = selected
                city = LocationDirectory.getCitiesForState(selected).firstOrNull() ?: ""
                showStatePicker = false
            },
            onDismiss = { showStatePicker = false }
        )
    }

    // City Search Picker Dialog
    if (showCityPicker) {
        SearchableListDialog(
            title = "Select City or Village ($state)",
            items = LocationDirectory.getCitiesForState(state),
            onSelect = { selected ->
                city = selected
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false }
        )
    }
}

@Composable
private fun SearchableListDialog(
    title: String,
    items: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = items.filter { it.contains(searchQuery, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Type to search incrementally...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(item) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(text = item, fontSize = 14.sp)
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    userProfile: UserProfile,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onToggleProfileHidden: (Boolean) -> Unit,
    onToggleGoalsHidden: (Boolean) -> Unit,
    onOpenNotifications: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("App & Privacy Settings", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Dark and Light Theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Theme")
                    Switch(checked = isDarkTheme, onCheckedChange = onToggleTheme)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Other user profile is visible only to admin and member level 3 rule
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "🔒 Privacy Policy: Other user profiles are visible only to Admins and Paid Member Level 3.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Hide my profile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hide My Profile", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Invisible to other seekers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = userProfile.isProfileHidden,
                        onCheckedChange = onToggleProfileHidden
                    )
                }

                // Hide my goal achieved
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hide Achieved Goals", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Keep sadhana badges private", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = userProfile.areGoalsHidden,
                        onCheckedChange = onToggleGoalsHidden
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Notifications")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun NotificationsSettingsDialog(
    current: NotificationSettings,
    onDismiss: () -> Unit,
    onSave: (NotificationSettings) -> Unit
) {
    var all by remember { mutableStateOf(current.allNotifications) }
    var ann by remember { mutableStateOf(current.announcements) }
    var thought by remember { mutableStateOf(current.thoughtOfDay) }
    var commPost by remember { mutableStateOf(current.communityPosts) }
    var commInteract by remember { mutableStateOf(current.communityInteractions) }
    var commMention by remember { mutableStateOf(current.communityMentions) }
    var dailyPost by remember { mutableStateOf(current.dailyActivitiesPosts) }
    var dailyReply by remember { mutableStateOf(current.dailyActivitiesReplies) }
    var dailyMention by remember { mutableStateOf(current.dailyActivitiesMentions) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .padding(4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Notification Settings", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("All Notifications Master Switch", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Switch(checked = all, onCheckedChange = { all = it })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    }

                    item {
                        NotificationRow("Announcements", ann, enabled = all) { ann = it }
                        NotificationRow("Thought of the Day", thought, enabled = all) { thought = it }
                        NotificationRow("Community Posts", commPost, enabled = all) { commPost = it }
                        NotificationRow("Community User Interactions", commInteract, enabled = all) { commInteract = it }
                        NotificationRow("Community Mentions", commMention, enabled = all) { commMention = it }
                        NotificationRow("Daily Activities Section Posts", dailyPost, enabled = all) { dailyPost = it }
                        NotificationRow("Daily Activities User Replies", dailyReply, enabled = all) { dailyReply = it }
                        NotificationRow("Daily Activities Mentions", dailyMention, enabled = all) { dailyMention = it }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                current.copy(
                                    allNotifications = all,
                                    announcements = ann,
                                    thoughtOfDay = thought,
                                    communityPosts = commPost,
                                    communityInteractions = commInteract,
                                    communityMentions = commMention,
                                    dailyActivitiesPosts = dailyPost,
                                    dailyActivitiesReplies = dailyReply,
                                    dailyActivitiesMentions = dailyMention
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                    ) {
                        Text("Save Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun GoalProofDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Complete Goal: ${goal.title}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(goal.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SaffronPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Attached Seva Photo Proof [Mock Uploaded]", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Sacred Feeling") },
                    placeholder = { Text("e.g. Prepared with Gayatri mantra chanting") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(note.ifBlank { "Completed with joy" }) },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
            ) {
                Text("Claim +${goal.mokshaReward} Pts")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
