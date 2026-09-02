/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.HubPluginRegistry
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.GearSix

@Composable
fun PluginsPage() {
    val context = LocalContext.current
    val registry = remember { HubPluginRegistry.getInstance(context) }
    val isRefreshing by registry.isRefreshing.collectAsState()
    var selectedPluginForConfig by remember { mutableStateOf<HubPlugin?>(null) }

    val plugins = remember { registry.getAllPlugins() }
    val enabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            plugins.forEach { put(it.id, registry.isPluginEnabled(it.id)) }
        }
    }

    ViewWithActionBar(
        title = "Hub Plugins & Modules",
        actions = {
            IconButton(
                onClick = { registry.refreshCards() },
                enabled = !isRefreshing
            ) {
                Icon(
                    imageVector = Phosphor.ArrowCounterClockwise,
                    contentDescription = "Refresh Plugins"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Extend your feed screen with live context modules. Configure credentials, tracked repos, and polling intervals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            items(plugins, key = { it.id }) { plugin ->
                PluginItemCard(
                    plugin = plugin,
                    isEnabled = enabledStates[plugin.id] ?: false,
                    onToggle = { enabled ->
                        enabledStates[plugin.id] = enabled
                        registry.setPluginEnabled(plugin.id, enabled)
                        registry.refreshCards()
                    },
                    onConfigure = {
                        selectedPluginForConfig = plugin
                    }
                )
            }
        }

        selectedPluginForConfig?.let { plugin ->
            PluginConfigDialog(
                plugin = plugin,
                initialConfig = registry.getPluginConfig(plugin.id),
                onDismiss = { selectedPluginForConfig = null },
                onSave = { updatedConfig ->
                    registry.savePluginConfig(plugin.id, updatedConfig)
                    registry.refreshCards()
                    selectedPluginForConfig = null
                }
            )
        }
    }
}

@Composable
fun PluginItemCard(
    plugin: HubPlugin,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = plugin.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            if (plugin.getConfigFields().isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onConfigure,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Phosphor.GearSix,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("Configure")
                    }
                }
            }
        }
    }
}

@Composable
fun PluginConfigDialog(
    plugin: HubPlugin,
    initialConfig: Map<String, String>,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val configState = remember {
        mutableStateMapOf<String, String>().apply {
            plugin.getConfigFields().forEach { field ->
                put(field.key, initialConfig[field.key] ?: field.defaultValue)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "⚙️ ${plugin.name} Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                plugin.getConfigFields().forEach { field ->
                    OutlinedTextField(
                        value = configState[field.key] ?: "",
                        onValueChange = { configState[field.key] = it },
                        label = { Text(field.label) },
                        placeholder = { Text(field.defaultValue) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        text = field.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(configState.toMap()) }) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}
