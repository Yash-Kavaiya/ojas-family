package com.ojashealing.family.ui.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.social.MediaKind
import com.ojashealing.family.ui.theme.Spacing

/** In a list, a small "Audio" / "Video" (or "Photo") line, so people know there is more to open. */
@Composable
internal fun MediaLabel(kind: MediaKind?, modifier: Modifier = Modifier) {
    if (kind == null) return
    val (icon, label) = when (kind) {
        MediaKind.Image -> Icons.Outlined.Image to R.string.announce_photo
        MediaKind.Audio -> Icons.Outlined.Headphones to R.string.announce_audio
        MediaKind.Video -> Icons.Outlined.Videocam to R.string.announce_video
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
