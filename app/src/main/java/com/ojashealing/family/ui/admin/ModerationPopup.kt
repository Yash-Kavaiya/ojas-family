package com.ojashealing.family.ui.admin

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.ui.goals.goalLanguage
import com.ojashealing.family.ui.theme.Spacing

/** One thing waiting for a decision, whatever kind it is. */
private class Waiting(
    val key: String,
    val who: String,
    val what: String,
    val imageUrl: String,
    /** Removing a post cannot be undone, so it asks once more. */
    val denyNeedsConfirm: Boolean,
    val approve: () -> Unit,
    val deny: () -> Unit,
)

private const val SEEN_KEY = "moderation_popup_seen"

/**
 * For people who can moderate: when something new is waiting (a post, a goal photo, a face photo), one
 * small dialog offers Approve and Reject right there. It never nags: each item is offered once per
 * phone, one at a time, and "Later" puts away everything waiting now until something new arrives.
 */
@Composable
fun ModerationPopup(vm: ModerationViewModel, onOpenQueue: () -> Unit) {
    val pending by vm.pending.collectAsStateWithLifecycle()
    val goalReviews by vm.goalReviews.collectAsStateWithLifecycle()
    val faces by vm.faceReviews.collectAsStateWithLifecycle()
    val lang = goalLanguage()
    val unknown = stringResource(R.string.moderation_unknown_member)
    val faceLabel = stringResource(R.string.moderation_face_photos)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ojas", Context.MODE_PRIVATE) }
    var seen by remember { mutableStateOf(prefs.getStringSet(SEEN_KEY, emptySet()).orEmpty().toSet()) }
    var confirmDeny by remember { mutableStateOf(false) }

    val waiting = remember(pending, goalReviews, faces, lang) {
        pending.map { p ->
            Waiting("post:" + p.path, p.author, p.text, p.imageUrl, denyNeedsConfirm = true, approve = { vm.approve(p.path) }, deny = { vm.remove(p.path) })
        } + goalReviews.map { r ->
            val s = r.submission
            Waiting(
                "goal:${s.uid}/${s.goalId}", s.memberName.ifEmpty { unknown },
                listOf(r.goal?.titleIn(lang) ?: s.goalId, s.note).filter { it.isNotEmpty() }.joinToString("\n"), s.photoUrl,
                denyNeedsConfirm = false, approve = { vm.reviewGoal(s, approve = true) }, deny = { vm.reviewGoal(s, approve = false) },
            )
        } + faces.map { f ->
            // The photo itself is part of the key, so a new photo from the same member is offered again
            Waiting(
                "face:${f.uid}:${f.photoUrl.hashCode()}", f.memberName.ifEmpty { unknown }, faceLabel, f.photoUrl,
                denyNeedsConfirm = false, approve = { vm.reviewFace(f.uid, approve = true) }, deny = { vm.reviewFace(f.uid, approve = false) },
            )
        }
    }
    val fresh = waiting.filter { it.key !in seen }
    val item = fresh.firstOrNull() ?: return

    fun putAway(keys: List<String>) {
        // Kept small: once it grows, only what is still waiting needs remembering
        val next = (if (seen.size > 400) waiting.map { it.key }.toSet() else seen) + keys
        seen = next
        confirmDeny = false
        prefs.edit().putStringSet(SEEN_KEY, next).apply()
    }

    AlertDialog(
        onDismissRequest = { putAway(fresh.map { it.key }) },
        title = { Text(stringResource(R.string.moderation_pending), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (confirmDeny) {
                    Text(stringResource(R.string.moderation_remove_confirm), style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text(item.who, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (item.what.isNotBlank()) Text(item.what, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    if (item.imageUrl.isNotEmpty()) AsyncImage(
                        model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    if (fresh.size > 1) Text(
                        stringResource(R.string.moderation_more_waiting, fresh.size - 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { putAway(fresh.map { it.key }); onOpenQueue() },
                    )
                }
            }
        },
        confirmButton = {
            if (confirmDeny) TextButton(onClick = { item.deny(); putAway(listOf(item.key)) }) {
                Text(stringResource(R.string.moderation_remove_post), color = MaterialTheme.colorScheme.error)
            } else TextButton(onClick = { item.approve(); putAway(listOf(item.key)) }) { Text(stringResource(R.string.moderation_approve)) }
        },
        dismissButton = {
            if (confirmDeny) TextButton(onClick = { confirmDeny = false }) { Text(stringResource(R.string.action_cancel)) }
            else androidx.compose.foundation.layout.Row {
                TextButton(onClick = { putAway(fresh.map { it.key }) }) { Text(stringResource(R.string.moderation_later)) }
                TextButton(onClick = { if (item.denyNeedsConfirm) confirmDeny = true else { item.deny(); putAway(listOf(item.key)) } }) {
                    Text(stringResource(R.string.moderation_reject), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}
