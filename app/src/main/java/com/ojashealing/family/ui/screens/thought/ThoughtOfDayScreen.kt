package com.ojashealing.family.ui.screens.thought

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ojashealing.family.data.model.ThoughtComment
import com.ojashealing.family.data.model.ThoughtOfDay
import com.ojashealing.family.data.model.UserProfile
import com.ojashealing.family.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtOfDayScreen(
    userProfile: UserProfile,
    thoughts: List<ThoughtOfDay>,
    onLikeThought: (String) -> Unit,
    onAddComment: (String, String, String?, String?) -> Unit,
    onScheduleThought: (String, String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current selected day offset: 0 is Today, 1 is Yesterday, up to 6 (7 days total)
    var selectedDayOffset by remember { mutableStateOf(0) }
    var showFullImageDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var commentImageLink by remember { mutableStateOf("") }
    var showLinkField by remember { mutableStateOf(false) }

    // Filter available 7-day archive
    val availableThoughts = thoughts.filter { it.dayOffset in 0..6 }
    val currentThought = availableThoughts.find { it.dayOffset == selectedDayOffset }
        ?: availableThoughts.firstOrNull() ?: return

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                // 7-Day Navigation Carousel (User can visit older posts up to 7 days only)
                Text(
                    text = "Daily Spiritual Reflection (7-Day Archive)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableThoughts) { t ->
                        FilterChip(
                            selected = t.dayOffset == selectedDayOffset,
                            onClick = { selectedDayOffset = t.dayOffset },
                            label = {
                                Text(
                                    text = t.dateLabel,
                                    fontSize = 12.sp,
                                    fontWeight = if (t.dayOffset == selectedDayOffset) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                if (t.dayOffset == 0) {
                                    Icon(
                                        Icons.Default.Stars,
                                        contentDescription = "Today",
                                        tint = SacredGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (userProfile.isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { showScheduleDialog = true },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    text = { Text("Schedule Post") },
                    containerColor = SacredGold,
                    contentColor = Color(0xFF3E1F00),
                    modifier = Modifier.testTag("admin_schedule_fab")
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Main Thought Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Header image / visual banner with tap to view full
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF241442),
                                            Color(0xFF130A24)
                                        )
                                    )
                                )
                                .clickable { showFullImageDialog = true }
                                .testTag("thought_image_banner"),
                            contentAlignment = Alignment.Center
                        ) {
                            // Spiritual geometry art
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(SacredGold, SaffronPrimary)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ॐ",
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2C1400)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FilterVintage,
                                            contentDescription = null,
                                            tint = SacredGold,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = currentThought.chakraAlignment,
                                            fontSize = 11.sp,
                                            color = SacredGold,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Tap to view sacred art in full screen",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }

                            // Day badge in corner
                            Surface(
                                color = SaffronPrimary,
                                shape = RoundedCornerShape(bottomStart = 14.dp),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = currentThought.dateLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Content Body
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = currentThought.title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Sacred Quote in Card
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "“${currentThought.quote}”",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        lineHeight = 22.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "— Swami Anand Swaroop",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SaffronPrimary,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Swamiji's Practical Insight:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentThought.swamijiCommentary,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 19.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Daily Mantra
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = SaffronPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Day Sadhana Mantra:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = currentThought.mantra,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Like & Interaction row (With Paytm like animation)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onLikeThought(currentThought.id) },
                                        modifier = Modifier
                                            .size(46.dp)
                                            .testTag("like_thought_button")
                                    ) {
                                        Icon(
                                            imageVector = if (currentThought.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Like",
                                            tint = if (currentThought.isLiked) LotusRose else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Column {
                                        Text(
                                            text = "${currentThought.likesCount} Devotees Liked",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (currentThought.dayOffset == 0)
                                                "+1 Moksha Point on today's like! 🎉"
                                            else
                                                "Archive post (points on today only)",
                                            fontSize = 11.sp,
                                            color = if (currentThought.dayOffset == 0) SaffronPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${currentThought.comments.size} Comments",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Comments Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Devotee Satsang Comments (${currentThought.comments.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (userProfile.canCommentPosts()) "Level 3: +1 pt per comment" else "Comment locked (Needs L3)",
                        fontSize = 11.sp,
                        color = if (userProfile.canCommentPosts()) SaffronPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Comment Box
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = {
                                Text(
                                    if (userProfile.canCommentPosts())
                                        "Share your spiritual reflection or gratitude with Swamiji..."
                                    else
                                        "Commenting is unlocked at User Level 3 (500 pts) or Paid Level 2."
                                )
                            },
                            enabled = userProfile.canCommentPosts(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("comment_input"),
                            minLines = 2
                        )

                        AnimatedVisibility(visible = showLinkField) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = commentImageLink,
                                    onValueChange = { commentImageLink = it },
                                    label = { Text("Spiritual Image / Article URL") },
                                    placeholder = { Text("https://...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { showLinkField = !showLinkField },
                                enabled = userProfile.canCommentPosts()
                            ) {
                                Icon(
                                    Icons.Outlined.InsertLink,
                                    contentDescription = "Attach Link",
                                    tint = if (showLinkField) SaffronPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = {
                                    if (commentText.isNotBlank()) {
                                        onAddComment(
                                            currentThought.id,
                                            commentText.trim(),
                                            null,
                                            if (commentImageLink.isNotBlank()) commentImageLink.trim() else null
                                        )
                                        commentText = ""
                                        commentImageLink = ""
                                        showLinkField = false
                                    }
                                },
                                enabled = commentText.isNotBlank() && userProfile.canCommentPosts(),
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                                modifier = Modifier.testTag("submit_comment_button")
                            ) {
                                Text("Post Comment (+1 Pt)")
                            }
                        }
                    }
                }
            }

            // Comments List
            items(currentThought.comments, key = { it.id }) { comment ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(SaffronPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = comment.authorName.take(1),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = SaffronDark
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = comment.authorName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = comment.authorLevel,
                                        fontSize = 11.sp,
                                        color = SaffronPrimary
                                    )
                                }
                            }

                            Text(
                                text = comment.date,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = comment.text,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )

                        if (comment.linkUrl != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = comment.linkUrl,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }

    // Full Image / Post View Modal
    if (showFullImageDialog) {
        Dialog(onDismissRequest = { showFullImageDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF160F2C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sacred Darshan & Insight",
                            color = SacredGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { showFullImageDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(SacredGold, SaffronPrimary, Color(0xFF3B154D))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ॐ",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C1400)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = currentThought.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "“${currentThought.quote}”",
                        color = SacredGold,
                        fontSize = 14.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentThought.swamijiCommentary,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showFullImageDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close View")
                    }
                }
            }
        }
    }

    // Admin Schedule Post Dialog (Admin can schedule a post for entire month or more)
    if (showScheduleDialog) {
        AdminSchedulePostDialog(
            onDismiss = { showScheduleDialog = false },
            onSchedule = { title, quote, commentary, chakra, mantra ->
                onScheduleThought(title, quote, commentary, chakra, mantra)
                showScheduleDialog = false
            }
        )
    }
}

@Composable
private fun AdminSchedulePostDialog(
    onDismiss: () -> Unit,
    onSchedule: (String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf("") }
    var commentary by remember { mutableStateOf("") }
    var chakra by remember { mutableStateOf("Anahata (Heart Chakra)") }
    var mantra by remember { mutableStateOf("Om Namah Shivaya") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = SacredGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule Thought of the Day", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "As Admin, you can schedule Swamiji's thoughts for the entire month ahead.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Awakening Divine Peace") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = quote,
                    onValueChange = { quote = it },
                    label = { Text("Swamiji's Quote") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = commentary,
                    onValueChange = { commentary = it },
                    label = { Text("Commentary & Guidance") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                OutlinedTextField(
                    value = chakra,
                    onValueChange = { chakra = it },
                    label = { Text("Chakra Focus") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mantra,
                    onValueChange = { mantra = it },
                    label = { Text("Mantra") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && quote.isNotBlank()) {
                        onSchedule(title, quote, commentary, chakra, mantra)
                    }
                },
                enabled = title.isNotBlank() && quote.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SacredGold)
            ) {
                Text("Schedule for Month", color = Color(0xFF3E1F00), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
