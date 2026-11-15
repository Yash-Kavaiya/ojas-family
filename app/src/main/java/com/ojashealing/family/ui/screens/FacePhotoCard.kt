package com.ojashealing.family.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.data.user.FacePhoto
import com.ojashealing.family.data.user.FacePhotoStatus
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Profile, from Member Level 3: upload a photo of your face for the opening animation. It appears
 * under the logo, with the blue light passing over it, once an admin has approved it.
 */
@Composable
fun FacePhotoCard(photo: FacePhoto?, onUpload: suspend (Uri) -> Result<Unit>) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && !busy) {
            busy = true
            failed = false
            scope.launch {
                failed = onUpload(uri).isFailure
                busy = false
            }
        }
    }
    OjasCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.face_photo_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            if (photo != null && photo.photoUrl.isNotEmpty()) AsyncImage(
                model = photo.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(
                        when (photo?.status) {
                            null -> R.string.face_photo_body
                            FacePhotoStatus.Submitted -> R.string.face_photo_waiting
                            FacePhotoStatus.Approved -> R.string.face_photo_approved
                            FacePhotoStatus.Rejected -> R.string.face_photo_rejected
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (photo?.status == FacePhotoStatus.Rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (failed) Text(stringResource(R.string.error_generic), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (photo == null) R.string.face_photo_upload else R.string.face_photo_change), modifier = Modifier.padding(start = Spacing.s))
        }
    }
}
