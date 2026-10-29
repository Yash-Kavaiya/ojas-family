package com.ojashealing.family.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ojashealing.family.R
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.ui.theme.Spacing
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(vm: ProfileSetupViewModel, onBack: (() -> Unit)? = null, onSaved: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val form = state.form
    fun errorFor(field: ProfileField) = state.errors[field]?.takeIf { state.showErrors }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    var picker by rememberSaveable { mutableStateOf<String?>(null) } // "dob", "country", "state"
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::pickPhoto)
    }

    OnboardingLayout(
        title = stringResource(R.string.profile_setup_title),
        subtitle = stringResource(R.string.profile_setup_subtitle),
        onBack = onBack,
        bottom = {
            ErrorText(state.saveError?.let { stringResource(it) })
            PrimaryButton(stringResource(R.string.action_save), onClick = vm::save, busy = state.saving)
        }
    ) {
        // Photo
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                val model: Any? = state.pendingPhoto ?: state.photoUrl.takeIf { it.isNotEmpty() }
                if (model != null) {
                    AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp))
                } else {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = stringResource(R.string.field_photo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> vm.edit { it.copy(name = v) } },
            label = { Text(stringResource(R.string.field_name)) },
            singleLine = true,
            isError = errorFor(ProfileField.Name) != null,
            supportingText = errorFor(ProfileField.Name)?.let { { Text(stringResource(it)) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth().testTag("field_name")
        )

        PickerField(
            label = stringResource(R.string.field_dob),
            value = form.dob?.let { "%02d/%02d/%04d".format(it.day, it.month, it.year) }.orEmpty(),
            error = errorFor(ProfileField.Dob)?.let { stringResource(it) },
            onClick = { picker = "dob" }
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.field_gender), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Gender.entries.forEach { g ->
                    FilterChip(
                        selected = form.gender == g,
                        onClick = { vm.edit { it.copy(gender = g) } },
                        label = { Text(stringResource(g.label)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            errorFor(ProfileField.Gender)?.let { ErrorText(stringResource(it)) }
        }

        PickerField(
            label = stringResource(R.string.field_country),
            value = vm.locations.country(form.country)?.name.orEmpty(),
            error = errorFor(ProfileField.Country)?.let { stringResource(it) },
            onClick = { picker = "country" }
        )

        val states = remember(form.country) { vm.locations.states(form.country, "") }
        if (states.isNotEmpty()) {
            PickerField(
                label = stringResource(R.string.field_state),
                value = states.firstOrNull { it.code == form.state }?.name.orEmpty(),
                error = null,
                onClick = { picker = "state" }
            )
        }

        CityField(
            value = form.city,
            suggestions = remember(form.country, form.state, form.city) {
                if (form.state.isEmpty()) emptyList() else vm.locations.cities(form.country, form.state, form.city).take(6)
            },
            onChange = { v -> vm.edit { it.copy(city = v) } }
        )
    }

    when (picker) {
        "dob" -> DobPickerDialog(initial = form.dob, onDismiss = { picker = null }, onPick = { d -> vm.edit { it.copy(dob = d) }; picker = null })
        "country" -> SearchPickerDialog(
            title = stringResource(R.string.field_country),
            search = { q -> vm.locations.countries(q).map { it.code to it.name } },
            onDismiss = { picker = null },
            onPick = { code -> vm.selectCountry(code); picker = null }
        )
        "state" -> SearchPickerDialog(
            title = stringResource(R.string.field_state),
            search = { q -> vm.locations.states(form.country, q).map { it.code to it.name } },
            onDismiss = { picker = null },
            onPick = { code -> vm.selectState(code); picker = null }
        )
    }
}

// A read-only text field that opens a picker, so every field shares the same outline and height
@Composable
private fun PickerField(label: String, value: String, error: String?, onClick: () -> Unit) {
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        // The text field consumes taps, so an overlay catches them
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityField(value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val visible = suggestions.filter { !it.equals(value, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && visible.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text(stringResource(R.string.field_city)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(expanded = expanded && visible.isNotEmpty(), onDismissRequest = { expanded = false }) {
            visible.forEach { city ->
                DropdownMenuItem(text = { Text(city) }, onClick = { onChange(city); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DobPickerDialog(initial: LocalDateLike?, onDismiss: () -> Unit, onPick: (LocalDateLike) -> Unit) {
    val utc = TimeZone.getTimeZone("UTC")
    val initialMillis = initial?.let {
        Calendar.getInstance(utc).apply { clear(); set(it.year, it.month - 1, it.day) }.timeInMillis
    }
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis, yearRange = 1920..thisYear)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val c = Calendar.getInstance(utc).apply { timeInMillis = millis }
                    onPick(LocalDateLike(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)))
                } ?: onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun SearchPickerDialog(
    title: String,
    search: (String) -> List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("picker_search")
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results, key = { it.first }, contentType = { "option" }) { (code, name) ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(code) }
                                .padding(vertical = Spacing.m)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
