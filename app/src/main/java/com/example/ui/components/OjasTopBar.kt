package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PaidMemberLevel
import com.example.data.model.UserLevel
import com.example.data.model.UserProfile
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OjasTopBar(
    profile: UserProfile,
    onUserLevelChange: (UserLevel) -> Unit,
    onPaidLevelChange: (PaidMemberLevel) -> Unit,
    onAdminToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onCounterPositioned: (Offset) -> Unit = {}
) {
    var showLevelSimulator by remember { mutableStateOf(false) }

    // Count up just as the flying coin lands, then bump the pill
    val shownPoints by animateIntAsState(
        targetValue = profile.mokshaPoints,
        animationSpec = tween(durationMillis = 450, delayMillis = 550),
        label = "mokshaCount"
    )
    val counterBump = remember { Animatable(1f) }
    var lastPoints by remember { mutableIntStateOf(profile.mokshaPoints) }
    LaunchedEffect(profile.mokshaPoints) {
        if (profile.mokshaPoints > lastPoints) {
            delay(550)
            counterBump.animateTo(1.25f, tween(120))
            counterBump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        lastPoints = profile.mokshaPoints
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // App Title & Tagline
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(SaffronPrimary, SacredGold)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ॐ",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Ojas Family",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Swami Anand Swaroop Spiritual Guidance",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Moksha Points & Level Badge Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Moksha Points Counter Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .testTag("moksha_points_badge")
                            .scale(counterBump.value)
                            .onGloballyPositioned { onCounterPositioned(it.boundsInRoot().center) }
                            .clickable { showLevelSimulator = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = "Moksha Points",
                                tint = SacredGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$shownPoints",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "pts",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Level simulator launcher icon
                    IconButton(
                        onClick = { showLevelSimulator = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("level_simulator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Permission Level Simulator",
                            tint = SaffronPrimary
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet / Dialog for Level Simulator so the user can test all permission levels!
    if (showLevelSimulator) {
        AlertDialog(
            onDismissRequest = { showLevelSimulator = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = SaffronPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Permission & Member Level",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Switch levels below to test app rules (like, comments, asking questions, daily activities & practices):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Free User Level Selector
                    Text(
                        text = "Free User Level:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    UserLevel.values().forEach { level ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onUserLevelChange(level) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = profile.userLevel == level,
                                onClick = { onUserLevelChange(level) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = level.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (profile.userLevel == level) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = when (level) {
                                        UserLevel.LEVEL_1 -> "Read only • Quick Protection • +1 pt daily"
                                        UserLevel.LEVEL_2 -> "Can Like posts (+1 pt per current date like)"
                                        UserLevel.LEVEL_3 -> "Can Comment & Ask Questions (+1 pt each)"
                                        UserLevel.LEVEL_4 -> "1+ Year • All Daily Practices Unlocked"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Paid Member Level Selector
                    Text(
                        text = "Paid Member Level (Rs. 100/mo):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SacredGold
                    )
                    PaidMemberLevel.values().forEach { paid ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPaidLevelChange(paid) }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = profile.paidLevel == paid,
                                onClick = { onPaidLevelChange(paid) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = paid.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (profile.paidLevel == paid) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = when (paid) {
                                        PaidMemberLevel.NONE -> "Free seeker"
                                        PaidMemberLevel.LEVEL_1 -> "Starts from Level 2 + Daily Practices unlocked"
                                        PaidMemberLevel.LEVEL_2 -> "Paid 3 Mos: Comments & 3-4 personal calls"
                                        PaidMemberLevel.LEVEL_3 -> "Paid 6 Mos: Post in Daily Activities & Anytime calls"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Admin Role Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Admin Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Can schedule posts & moderate content",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = profile.isAdmin,
                            onCheckedChange = { onAdminToggle(it) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLevelSimulator = false }) {
                    Text("Apply & Close")
                }
            }
        )
    }
}
