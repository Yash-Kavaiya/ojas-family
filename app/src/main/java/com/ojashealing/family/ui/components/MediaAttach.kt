package com.ojashealing.family.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.social.MediaKind
import com.ojashealing.family.ui.theme.Spacing

/**
 * Three tiles to add one photo, audio clip or video to a post, and the picked file shown
 * (picture, player) with a button to remove it. The caller keeps [uri] and [kind], e.g. in
 * rememberSaveable, so the choice survives turning the phone.
 */
@Composable
fun MediaAttachPicker(
    uri: String?,
    kind: MediaKind?,
    onChange: (uri: String?, kind: MediaKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun pick(k: MediaKind) = { picked: android.net.Uri? -> if (picked != null) onChange(picked.toString(), k) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), pick(MediaKind.Image))
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), pick(MediaKind.Video))
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), pick(MediaKind.Audio))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(stringResource(R.string.announce_attach), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            AttachTile(Icons.Outlined.Image, stringResource(R.string.announce_photo), kind == MediaKind.Image,
                { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f))
            AttachTile(Icons.Outlined.Headphones, stringResource(R.string.announce_audio), kind == MediaKind.Audio,
                { audioPicker.launch("audio/*") }, Modifier.weight(1f))
            AttachTile(Icons.Outlined.Videocam, stringResource(R.string.announce_video), kind == MediaKind.Video,
                { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }, Modifier.weight(1f))
        }
        if (uri != null && kind != null) {
            Box {
                when (kind) {
                    MediaKind.Image -> PostImage(uri)
                    MediaKind.Audio -> AudioPlayer(uri, Modifier.padding(top = Spacing.xl))
                    MediaKind.Video -> InlineVideoPlayer(uri, Modifier.padding(top = Spacing.xl))
                }
                IconButton(
                    onClick = { onChange(null, null) },
                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                ) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.announce_remove), tint = Color.White) }
            }
            Text(stringResource(R.string.announce_size_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A post's photo, audio or video, as it shows in the post. */
@Composable
fun PostMedia(kind: MediaKind?, url: String, modifier: Modifier = Modifier) {
    if (url.isEmpty() || kind == null) return
    when (kind) {
        MediaKind.Image -> PostImage(url, modifier)
        MediaKind.Audio -> AudioPlayer(url, modifier)
        MediaKind.Video -> InlineVideoPlayer(url, modifier)
    }
}

@Composable
private fun AttachTile(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
