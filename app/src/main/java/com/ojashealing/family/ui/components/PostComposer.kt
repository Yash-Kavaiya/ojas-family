package com.ojashealing.family.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.social.replyTargetFor
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.mentionedUids
import com.ojashealing.family.ui.theme.Spacing

/**
 * Swamiji and the admin team (from config/staff), offered for @mentions under announcements and
 * thoughts: they wrote the post, so a comment there often talks to them.
 */
val LocalStaffMentions = androidx.compose.runtime.compositionLocalOf<List<Author>> { emptyList() }

/** What a composer sends: text, an optional picked photo, and the people @mentioned. */
/** [reply] is set when the text answers a comment rather than the post itself. */
data class Draft(val text: String, val photo: Uri?, val mentions: List<String>, val reply: com.ojashealing.family.data.social.ReplyTo? = null)

/**
 * Comment / answer / reply box. The photo button shows only for members allowed to attach pictures,
 * and the @ button offers the people already in this thread (Firestore keeps other profiles private).
 */
@Composable
fun PostComposer(
    hint: String,
    canAttachImage: Boolean,
    mentionable: List<Author>,
    busy: Boolean,
    onSend: (Draft, onSent: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    replyingTo: com.ojashealing.family.data.social.Post? = null,
    onCancelReply: () -> Unit = {}
) {
    var field by rememberSaveable(stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(""))
    }
    val text = field.text
    var photo by rememberSaveable { mutableStateOf<Uri?>(null) }
    // The + menu inside the box: 1 = photo / mention, 2 = the people to mention
    var menu by remember { mutableStateOf(0) }
    val pickImage = rememberImagePicker { photo = it }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Choosing "Reply" puts the cursor in the box and opens the keyboard
    androidx.compose.runtime.LaunchedEffect(replyingTo?.id) {
        if (replyingTo != null) { runCatching { focus.requestFocus() }; keyboard?.show() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        replyingTo?.let { target ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.replying_to, target.author.name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = Spacing.s)
                )
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel), modifier = Modifier.size(18.dp))
                }
            }
        }
        photo?.let { uri ->
            Box {
                ResponsiveImage(uri, Modifier.fillMaxWidth(0.4f), maxHeight = 120.dp)
                IconButton(
                    onClick = { photo = null },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.post_remove_photo), tint = Color.White) }
            }
        }
        val people = mentionable.distinctBy { it.uid }.take(20)
        // Typing "@" (and the start of a name) offers the people in this thread right above the box
        val query = if (field.selection.collapsed) com.ojashealing.family.data.social.mentionQuery(text, field.selection.start) else null
        val suggestions = query?.let { com.ojashealing.family.data.social.mentionMatches(it, people) }.orEmpty()
        if (suggestions.isNotEmpty()) MentionSuggestions(suggestions) { a ->
            val (t, c) = com.ojashealing.family.data.social.insertMention(text, field.selection.start, a.name)
            field = androidx.compose.ui.text.input.TextFieldValue(t.take(1000), androidx.compose.ui.text.TextRange(c.coerceAtMost(1000)))
        }
        val mentionColor = MaterialTheme.colorScheme.primary
        OutlinedTextField(
            value = field,
            onValueChange = { field = if (it.text.length > 1000) it.copy(text = it.text.take(1000)) else it },
            visualTransformation = remember(people, mentionColor) { MentionHighlight(people.map { it.name }, mentionColor) },
            placeholder = { Text(hint, style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                    if (canAttachImage || people.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menu = 1 }, enabled = !busy) {
                                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.composer_more))
                            }
                            DropdownMenu(expanded = menu == 1, onDismissRequest = { menu = 0 }) {
                                if (canAttachImage) DropdownMenuItem(
                                    text = { Text(stringResource(R.string.post_add_photo)) },
                                    leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                    onClick = { menu = 0; pickImage() }
                                )
                                if (people.isNotEmpty()) DropdownMenuItem(
                                    text = { Text(stringResource(R.string.mention_title)) },
                                    leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null) },
                                    onClick = { menu = 2 }
                                )
                            }
                            DropdownMenu(expanded = menu == 2, onDismissRequest = { menu = 0 }) {
                                people.forEach { a ->
                                    DropdownMenuItem(text = { Text(a.name) }, onClick = {
                                        val (t, c) = com.ojashealing.family.data.social.insertMention(text, field.selection.start, a.name)
                                        field = androidx.compose.ui.text.input.TextFieldValue(t.take(1000), androidx.compose.ui.text.TextRange(c.coerceAtMost(1000)))
                                        menu = 0
                                    })
                                }
                            }
                        }
                    }
                    SendButton(
                        onClick = {
                            val reply = replyingTo?.let { replyTargetFor(it) }
                            onSend(Draft(text, photo, mentionedUids(text, mentionable), reply)) { field = androidx.compose.ui.text.input.TextFieldValue(""); photo = null; onCancelReply() }
                        },
                        enabled = text.isNotBlank(),
                        busy = busy,
                        size = 40.dp,
                    )
                }
            }
        )
        if (photo != null) {
            Text(stringResource(R.string.post_photo_review), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.s))
        }
    }
}

/** A post's picture; tapping opens it full screen. */
@Composable
fun PostImage(url: String, modifier: Modifier = Modifier, maxHeight: androidx.compose.ui.unit.Dp = 480.dp) {
    var open by remember { mutableStateOf(false) }
    // Shown at its own shape, never cropped, whatever size the member uploaded
    ResponsiveImage(url, modifier, maxHeight, stringResource(R.string.thought_open_image), onClick = { open = true })
    if (open) ImageViewer(url) { open = false }
}

@Composable
fun ImageViewer(url: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose)) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.l)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
            }
        }
    }
}

/** Colours each "@Name" of a known person while it is typed. */
private class MentionHighlight(private val names: List<String>, private val color: Color) :
    androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val ranges = com.ojashealing.family.data.social.mentionRanges(text.text, names)
        if (ranges.isEmpty()) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        val styled = androidx.compose.ui.text.AnnotatedString.Builder(text).apply {
            ranges.forEach { addStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), it.first, it.last + 1) }
        }.toAnnotatedString()
        return androidx.compose.ui.text.input.TransformedText(styled, androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
    override fun equals(other: Any?) = other is MentionHighlight && other.names == names && other.color == color
    override fun hashCode() = names.hashCode() * 31 + color.hashCode()
}

/** The people matching what is typed after "@", as a row of chips; tapping one puts the name in. */
@Composable
private fun MentionSuggestions(people: List<Author>, onPick: (Author) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.xs),
    ) {
        items(people.size, key = { people[it].uid }) { i ->
            val a = people[i]
            androidx.compose.material3.AssistChip(
                onClick = { onPick(a) },
                label = { Text(a.name, maxLines = 1) },
                leadingIcon = { Icon(Icons.Outlined.AlternateEmail, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}
