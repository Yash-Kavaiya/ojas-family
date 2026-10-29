package com.ojashealing.family.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.BuildConfig
import com.ojashealing.family.R
import com.ojashealing.family.data.settings.ThemeMode
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.theme.Spacing

private val languageNames = listOf("hi" to "हिन्दी", "gu" to "ગુજરાતી", "en" to "English")

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editingReminder by rememberSaveable { mutableStateOf<Boolean?>(null) } // true = morning
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.s)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.l)
            ) {
                Section(stringResource(R.string.settings_appearance)) { ThemeSelector(state.themeMode, vm::setTheme) }

                Section(stringResource(R.string.settings_language)) {
                    languageNames.forEach { (tag, name) ->
                        RadioRow(name, selected = state.language == tag) { vm.setLanguage(tag) }
                    }
                }

                Section(stringResource(R.string.settings_privacy)) {
                    SwitchRow(stringResource(R.string.settings_hide_profile), state.hideProfile, onChange = vm::setHideProfile)
                    SwitchRow(stringResource(R.string.settings_hide_goals), state.hideGoals, onChange = vm::setHideGoals)
                }

                Section(stringResource(R.string.settings_notifications)) {
                    SwitchRow(stringResource(R.string.notif_all), state.categoriesEnabled) { vm.setNotification(NotificationKeys.ALL, it) }
                    NotificationKeys.categories.forEach { (key, label) ->
                        SwitchRow(stringResource(label), state.isOn(key), enabled = state.categoriesEnabled) { vm.setNotification(key, it) }
                    }
                }

                Section(stringResource(R.string.settings_reminders)) {
                    ValueRow(stringResource(R.string.settings_morning_reminder), state.morningReminder) { editingReminder = true }
                    ValueRow(stringResource(R.string.settings_night_reminder), state.nightReminder) { editingReminder = false }
                }

                Section(stringResource(R.string.settings_account)) {
                    OutlinedButton(onClick = vm::signOutNow, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_delete_account), color = MaterialTheme.colorScheme.error)
                    }
                }

                Section(stringResource(R.string.settings_about)) {
                    Text("Ojas Family ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.settings_attribution), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editingReminder?.let { morning ->
        ReminderDialog(
            initial = if (morning) state.morningReminder else state.nightReminder,
            onDismiss = { editingReminder = null },
            onPick = { vm.setReminder(morning, it); editingReminder = null }
        )
    }
    if (confirmDelete) {
        DeleteAccountDialog(
            deleting = state.deleting,
            error = state.deleteError?.let { stringResource(it) },
            onConfirm = vm::deleteAccount,
            onDismiss = { if (!state.deleting) confirmDelete = false }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    OjasCard {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.System to R.string.settings_theme_system,
        ThemeMode.Light to R.string.settings_theme_light,
        ThemeMode.Dark to R.string.settings_theme_dark
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size)
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

// All rows share a 48dp minimum height so labels in every language line up with their controls
@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked && enabled, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDialog(initial: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val (h, m) = initial.split(":").mapNotNull { it.toIntOrNull() }.let { (it.getOrNull(0) ?: 6) to (it.getOrNull(1) ?: 0) }
    val picker = rememberTimePickerState(initialHour = h, initialMinute = m)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = picker) },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(picker.hour, picker.minute)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// Deleting is permanent, so the user types the confirmation word before the button unlocks
@Composable
private fun DeleteAccountDialog(deleting: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val word = stringResource(R.string.settings_delete_word)
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_delete_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.settings_delete_body, word), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == word && !deleting) {
                Text(stringResource(R.string.settings_delete_account), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) } }
    )
}
