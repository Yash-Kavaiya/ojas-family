package com.ojashealing.family.ui.thought

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.social.todayIst
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.ui.onboarding.OnboardingLayout
import com.ojashealing.family.ui.onboarding.PrimaryButton
import java.util.Calendar
import java.util.TimeZone

private const val MAX_DAYS_AHEAD = 60L * 24 * 3600 * 1000

/** Admin: write a thought for any day from today up to 60 days ahead; it appears at 00:00 IST that day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleThoughtScreen(vm: ThoughtViewModel, onDone: () -> Unit, editDate: String = "") {
    var date by rememberSaveable { mutableStateOf(editDate.ifEmpty { todayIst().iso() }) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    // Saved across leaving the app; the picker copies the picture into our cache so it can still be read on return
    var image by rememberSaveable { mutableStateOf<Uri?>(null) }
    var failed by remember { mutableStateOf(false) }
    val pickImage = com.ojashealing.family.ui.components.rememberImagePicker { image = it }
    // Editing a scheduled thought: fill the form once from it
    var prefilled by rememberSaveable { mutableStateOf(editDate.isEmpty()) }
    val scheduled by vm.scheduled.collectAsStateWithLifecycle()
    var existingImage by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(scheduled) {
        if (!prefilled) scheduled?.firstOrNull { it.id == editDate }?.let { t ->
            title = t.title; text = t.text; existingImage = t.imageUrl; prefilled = true
        }
    }

    OnboardingLayout(
        title = stringResource(R.string.home_schedule),
        subtitle = null,
        onBack = onDone,
        bottom = {
            PrimaryButton(
                stringResource(R.string.action_save),
                onClick = {
                    val d = LocalDateLike.parse(date) ?: return@PrimaryButton
                    saving = true
                    failed = false
                    vm.schedule(d, title, text, image) { ok -> saving = false; if (ok) onDone() else failed = true }
                },
                busy = saving,
                enabled = title.isNotBlank() && text.isNotBlank()
            )
        }
    ) {
        Box {
            OutlinedTextField(value = date, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.field_date)) }, modifier = Modifier.fillMaxWidth())
            Box(Modifier.matchParentSize().clickable { picking = true })
        }
        OutlinedTextField(value = title, onValueChange = { title = it.take(150) }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = text, onValueChange = { text = it.take(5000) }, label = { Text(stringResource(R.string.field_message)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp))
        (image ?: existingImage.takeIf { it.isNotEmpty() })?.let {
            com.ojashealing.family.ui.components.ResponsiveImage(it)
        }
        OutlinedButton(onClick = pickImage) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Text(stringResource(R.string.thought_add_image))
        }
        if (failed) com.ojashealing.family.ui.onboarding.ErrorText(stringResource(R.string.error_generic))
    }

    if (picking) {
        val utc = TimeZone.getTimeZone("UTC")
        val todayMillis = todayIst().let { Calendar.getInstance(utc).apply { clear(); set(it.year, it.month - 1, it.day) }.timeInMillis }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = todayMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in todayMillis..(todayMillis + MAX_DAYS_AHEAD)
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { m ->
                        val c = Calendar.getInstance(utc).apply { timeInMillis = m }
                        date = LocalDateLike(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)).iso()
                    }
                    picking = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) { DatePicker(state = state) }
    }
}
