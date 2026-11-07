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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.mentionedUids
import com.ojashealing.family.ui.theme.Spacing

/** What a composer sends: text, an optional picked photo, and the people @mentioned. */
data class Draft(val text: String, val photo: Uri?, val mentions: List<String>)

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
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var mentioning by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        photo?.let { uri ->
            Box {
                AsyncImage(
                    model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                )
                IconButton(
                    onClick = { photo = null },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))
                ) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.post_remove_photo), tint = Color.White) }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(1000) },
            placeholder = { Text(hint) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else TextButton(
                    onClick = {
                        onSend(Draft(text, photo, mentionedUids(text, mentionable))) { text = ""; photo = null }
                    },
                    enabled = text.isNotBlank()
                ) { Text(stringResource(R.string.action_send)) }
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canAttachImage) {
                IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Outlined.Image, contentDescription = stringResource(R.string.post_add_photo))
                }
            }
            if (mentionable.isNotEmpty()) {
                Box {
                    IconButton(onClick = { mentioning = true }) {
                        Icon(Icons.Outlined.AlternateEmail, contentDescription = stringResource(R.string.mention_title))
                    }
                    DropdownMenu(expanded = mentioning, onDismissRequest = { mentioning = false }) {
                        mentionable.distinctBy { it.uid }.take(20).forEach { a ->
                            DropdownMenuItem(text = { Text(a.name) }, onClick = {
                                text = (text.trimEnd() + " @" + a.name + " ").trimStart()
                                mentioning = false
                            })
                        }
                    }
                }
            }
            if (photo != null) {
                Text(stringResource(R.string.post_photo_review), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.s))
            }
        }
    }
}

/** A post's picture; tapping opens it full screen. */
@Composable
fun PostImage(url: String, modifier: Modifier = Modifier, aspect: Float = 4f / 3f) {
    var open by remember { mutableStateOf(false) }
    AsyncImage(
        model = url,
        contentDescription = stringResource(R.string.thought_open_image),
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { open = true }
    )
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
