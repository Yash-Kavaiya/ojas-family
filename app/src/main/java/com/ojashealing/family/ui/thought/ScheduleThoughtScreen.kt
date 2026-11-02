package com.ojashealing.family.ui.thought

import androidx.compose.foundation.clickable
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
fun ScheduleThoughtScreen(vm: ThoughtViewModel, onDone: () -> Unit) {
    var date by rememberSaveable { mutableStateOf(todayIst().iso()) }
    var title by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }

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
                    vm.schedule(d, title, text) { saving = false; onDone() }
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
