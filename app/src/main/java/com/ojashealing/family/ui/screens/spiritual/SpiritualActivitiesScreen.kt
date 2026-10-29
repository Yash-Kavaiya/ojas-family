package com.ojashealing.family.ui.screens.spiritual

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ojashealing.family.data.model.*
import com.ojashealing.family.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiritualActivitiesScreen(
    userProfile: UserProfile,
    activities: List<SpiritualActivityItem>,
    onCompleteActivity: (Int, String) -> Unit,
    onUpdateReminderTime: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeSession by remember { mutableStateOf<SpiritualActivityItem?>(null) }
    var settingReminderForActivity by remember { mutableStateOf<SpiritualActivityItem?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header Card with Chakra Alignment
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF1E1738),
                                    Color(0xFF2C134C)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SelfImprovement,
                                contentDescription = null,
                                tint = SacredGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Spiritual Activities & Sadhana",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Align body chakra energies through Swami Anand Swaroop's guided meditations, box breathing, and protection techniques.",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Chakra Rainbow Dots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chakras = listOf(
                                "Root" to Color(0xFFE53935),
                                "Sacral" to Color(0xFFFB8C00),
                                "Solar" to Color(0xFFFDD835),
                                "Heart" to Color(0xFF43A047),
                                "Throat" to Color(0xFF039BE5),
                                "3rd Eye" to Color(0xFF3949AB),
                                "Crown" to Color(0xFF8E24AA)
                            )
                            chakras.forEach { (name, color) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        items(activities, key = { it.id }) { item ->
            val isUnlocked = when (item.accessLevel) {
                ActivityAccessLevel.OPEN_FOR_ALL -> true
                ActivityAccessLevel.LEVEL_3_OR_PAID ->
                    userProfile.userLevel >= UserLevel.LEVEL_3 || userProfile.paidLevel != PaidMemberLevel.NONE || userProfile.isAdmin
                ActivityAccessLevel.LEVEL_4_OR_PAID ->
                    userProfile.userLevel >= UserLevel.LEVEL_4 || userProfile.paidLevel != PaidMemberLevel.NONE || userProfile.isAdmin
            }

            ActivityCard(
                item = item,
                isUnlocked = isUnlocked,
                userProfile = userProfile,
                onStart = { activeSession = item },
                onSetReminder = { settingReminderForActivity = item }
            )
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Active Meditation / Box Breathing Player Modal
    activeSession?.let { activity ->
        MeditationPlayerModal(
            activity = activity,
            onDismiss = { activeSession = null },
            onCompleted = {
                onCompleteActivity(5, "+5 Moksha Points for completing ${activity.title}!")
                activeSession = null
            }
        )
    }

    // Reminder Time Picker Dialog
    settingReminderForActivity?.let { activity ->
        ReminderTimeDialog(
            activity = activity,
            currentReminderTime = activity.reminderTime ?: "06:00 AM",
            onDismiss = { settingReminderForActivity = null },
            onSave = { time ->
                onUpdateReminderTime(activity.id, time)
                settingReminderForActivity = null
            }
        )
    }
}

@Composable
private fun ActivityCard(
    item: SpiritualActivityItem,
    isUnlocked: Boolean,
    userProfile: UserProfile,
    onStart: () -> Unit,
    onSetReminder: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = when (item.id) {
                        "act_blue_light" -> HealingCyan.copy(alpha = 0.15f)
                        "act_morning_protection" -> SaffronPrimary.copy(alpha = 0.15f)
                        "act_night_protection" -> Color(0xFF673AB7).copy(alpha = 0.15f)
                        else -> SacredGold.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.id) {
                            "act_blue_light" -> HealingCyan
                            "act_morning_protection" -> SaffronPrimary
                            "act_night_protection" -> Color(0xFF7E57C2)
                            else -> GoldDark
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${item.durationMinutes} mins",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.subtitle,
                fontSize = 12.sp,
                color = SaffronPrimary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterVintage,
                    contentDescription = null,
                    tint = ChakraGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Chakra: ${item.chakraFocus}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (item.allowsReminderNotification) {
                    OutlinedButton(
                        onClick = onSetReminder,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.reminderTime ?: "Set Alarm",
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (isUnlocked) {
                    Button(
                        onClick = onStart,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.id == "act_blue_light") HealingCyan else SaffronPrimary
                        ),
                        modifier = Modifier.testTag("start_sadhana_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.hasBoxBreathing) Icons.Default.Air else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (item.hasBoxBreathing) "Start Box Breathing" else "Begin Meditation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Locked", tint = SaffronPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Requires Level 3 or Paid Member",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeditationPlayerModal(
    activity: SpiritualActivityItem,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var progressSeconds by remember { mutableStateOf(0) }
    val totalSeconds = activity.durationMinutes * 60

    // Box Breathing cycle state: Inhale (4s) -> Hold (4s) -> Exhale (4s) -> Hold (4s)
    var breathPhase by remember { mutableStateOf("Inhale (4s)") }
    var phaseSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(isPlaying) {
        while (isPlaying && progressSeconds < totalSeconds) {
            delay(1000)
            progressSeconds += 1
            phaseSeconds = (phaseSeconds + 1) % 16
            breathPhase = when (phaseSeconds) {
                in 0..3 -> "Inhale Prana (4s)"
                in 4..7 -> "Retain & Energize (4s)"
                in 8..11 -> "Exhale & Release (4s)"
                else -> "Hold in Stillness (4s)"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140D26)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sadhana Player",
                        color = SacredGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = activity.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Swami Anand Swaroop's Divine Guidance",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive Meditative Breathing Animation Circle
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.85f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(
                    modifier = Modifier
                        .size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer aura ring
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .graphicsLayer {
                                val s = if (isPlaying) pulseScale else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        if (activity.id == "act_blue_light") HealingCyan.copy(alpha = 0.4f) else SaffronPrimary.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Inner glowing orb
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        if (activity.id == "act_blue_light") Color(0xFF00E5FF) else SacredGold,
                                        if (activity.id == "act_blue_light") Color(0xFF0D47A1) else SaffronPrimary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "ॐ",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (activity.hasBoxBreathing) {
                                Text(
                                    text = breathPhase.take(6),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (activity.hasBoxBreathing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFF261944),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = breathPhase,
                            color = SacredGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { (progressSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (activity.id == "act_blue_light") HealingCyan else SaffronPrimary,
                    trackColor = Color(0xFF332356)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val currentMin = progressSeconds / 60
                    val currentSec = progressSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", currentMin, currentSec),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${activity.durationMinutes}:00",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { progressSeconds = (progressSeconds - 15).coerceAtLeast(0) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "Rewind", tint = Color.White)
                    }

                    FloatingActionButton(
                        onClick = { isPlaying = !isPlaying },
                        containerColor = if (activity.id == "act_blue_light") HealingCyan else SaffronPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(
                        onClick = { progressSeconds = (progressSeconds + 30).coerceAtMost(totalSeconds) },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Default.Forward30, contentDescription = "Fast Forward", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCompleted,
                    colors = ButtonDefaults.buttonColors(containerColor = SacredGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Complete & Claim +5 Moksha Pts",
                        color = Color(0xFF3E1F00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderTimeDialog(
    activity: SpiritualActivityItem,
    currentReminderTime: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val times = listOf("05:30 AM", "06:00 AM", "06:30 AM", "07:00 AM", "08:30 PM", "09:00 PM", "09:30 PM", "10:00 PM")
    var selectedTime by remember { mutableStateOf(currentReminderTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Reminder: ${activity.category}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Select your daily reminder time for ${activity.title}:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                times.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { t ->
                            FilterChip(
                                selected = selectedTime == t,
                                onClick = { selectedTime = t },
                                label = { Text(t, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTime) },
                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
            ) {
                Text("Save Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
