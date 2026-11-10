package com.ojashealing.family.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ojashealing.family.R
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.ui.components.OjasCard
import com.ojashealing.family.ui.content.label
import com.ojashealing.family.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminContentScreen(vm: AdminContentViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            item(key = "top", contentType = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    Text(stringResource(R.string.admin_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
            }

            state.message?.let { msg ->
                item(key = "message", contentType = "message") {
                    Text(
                        if (msg.count != null) stringResource(msg.text, msg.count) else stringResource(msg.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item(key = "add", contentType = "card") {
                OjasCard {
                    Text(stringResource(R.string.admin_add_title), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = vm::onUrlChange,
                        label = { Text(stringResource(R.string.admin_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.admin_section), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ContentSection.entries.forEach { section ->
                            FilterChip(
                                selected = state.section == section,
                                onClick = { vm.onSectionChange(section) },
                                label = { Text(stringResource(section.label())) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    Button(onClick = vm::addVideo, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        if (state.busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        else Text(stringResource(R.string.admin_add))
                    }
                }
            }

            item(key = "import", contentType = "card") {
                OjasCard {
                    Text(stringResource(R.string.admin_import_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.admin_import_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChannelPlaylists.forEach { playlist ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(playlist.section.label()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { vm.import(playlist) }, enabled = state.importing == null) {
                                if (state.importing == playlist.id) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                else Text(stringResource(R.string.admin_import))
                            }
                        }
                    }
                }
            }

            item(key = "listTitle", contentType = "title") {
                Text(stringResource(R.string.admin_videos_title), style = MaterialTheme.typography.titleLarge)
            }
            items(items, key = { it.id }, contentType = { "video" }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(item.section.label()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = item.published, onCheckedChange = { vm.setPublished(item.id, it) })
                    IconButton(onClick = { confirmDelete = item.id }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.admin_delete))
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.admin_delete_confirm), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { vm.delete(id); confirmDelete = null }) { Text(stringResource(R.string.admin_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}
