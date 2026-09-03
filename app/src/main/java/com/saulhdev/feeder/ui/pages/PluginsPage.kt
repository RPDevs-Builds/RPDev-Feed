/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.ui.pages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.HubPluginRegistry
import com.saulhdev.feeder.plugins.PluginCategory
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.hub.GenericHubCard
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.CaretDown
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Plus
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun PluginsPage() {
    val context = LocalContext.current
    val registry = remember { HubPluginRegistry.getInstance(context) }
    val isRefreshing by registry.isRefreshing.collectAsState()
    val previewCards by registry.cardsFlow.collectAsState()
    var selectedPluginForConfig by remember { mutableStateOf<HubPlugin?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    var pluginsList by remember { mutableStateOf(registry.getAllPlugins()) }
    val enabledStates = remember {
        mutableStateMapOf<String, Boolean>().apply {
            pluginsList.forEach { put(it.id, registry.isPluginEnabled(it.id)) }
        }
    }

    val refreshPluginList = {
        pluginsList = registry.getAllPlugins()
        pluginsList.forEach { enabledStates[it.id] = registry.isPluginEnabled(it.id) }
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
                    text = "Reorder, enable, and customize live context modules in your feed. Use Up/Down arrows to adjust position and display priority.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            item {
                Button(
                    onClick = { showAddCustomDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Phosphor.Plus,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("Add Custom REST / JSON Module")
                }
            }

            itemsIndexed(pluginsList, key = { _, it -> it.id }) { index, plugin ->
                PluginItemCard(
                    plugin = plugin,
                    position = index + 1,
                    isFirst = index == 0,
                    isLast = index == pluginsList.size - 1,
                    isEnabled = enabledStates[plugin.id] ?: false,
                    isCustom = plugin.id.startsWith("plugin_custom_rest_"),
                    onMoveUp = {
                        registry.movePluginUp(plugin.id)
                        refreshPluginList()
                    },
                    onMoveDown = {
                        registry.movePluginDown(plugin.id)
                        refreshPluginList()
                    },
                    onToggle = { enabled ->
                        enabledStates[plugin.id] = enabled
                        registry.setPluginEnabled(plugin.id, enabled)
                    },
                    onConfigure = {
                        selectedPluginForConfig = plugin
                    },
                    onDelete = {
                        registry.deleteCustomPlugin(plugin.id)
                        refreshPluginList()
                    }
                )
            }

            if (previewCards.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Live Cards Preview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Active cards as rendered on your feed screen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                itemsIndexed(previewCards, key = { index, it -> "${it.pluginId}_$index" }) { _, card ->
                    GenericHubCard(cardData = card)
                }
            }
        }

        selectedPluginForConfig?.let { plugin ->
            PluginConfigDialog(
                plugin = plugin,
                initialConfig = registry.getPluginConfig(plugin.id),
                onDismiss = { selectedPluginForConfig = null },
                onSave = { updatedConfig ->
                    registry.savePluginConfig(plugin.id, updatedConfig)
                    selectedPluginForConfig = null
                }
            )
        }

        if (showAddCustomDialog) {
            AddCustomModuleDialog(
                onDismiss = { showAddCustomDialog = false },
                onAdd = { name, url, headers ->
                    registry.addCustomPlugin(name, url, headers)
                    refreshPluginList()
                    showAddCustomDialog = false
                }
            )
        }
    }
}

@Composable
fun PluginItemCard(
    plugin: HubPlugin,
    position: Int,
    isFirst: Boolean,
    isLast: Boolean,
    isEnabled: Boolean,
    isCustom: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp)
                    ) {
                        Text(
                            text = "#$position",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Column {
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

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reorder buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = !isFirst
                    ) {
                        Icon(
                            imageVector = Phosphor.CaretUp,
                            contentDescription = "Move Up",
                            tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = !isLast
                    ) {
                        Icon(
                            imageVector = Phosphor.CaretDown,
                            contentDescription = "Move Down",
                            tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCustom) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Phosphor.TrashSimple,
                                contentDescription = "Delete Module",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (plugin.getConfigFields().isNotEmpty()) {
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
}

@Composable
fun AddCustomModuleDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String, headers: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }

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
                    text = "➕ Add Custom Module",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Poll any JSON API and render live stats cards in your feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Module Name") },
                    placeholder = { Text("e.g. Server Status / Home Assistant") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("JSON Endpoint URL") },
                    placeholder = { Text("https://api.example.com/status") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                OutlinedTextField(
                    value = headers,
                    onValueChange = { headers = it },
                    label = { Text("Headers (JSON Optional)") },
                    placeholder = { Text("{\"Authorization\": \"Bearer token\"}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAdd(name.ifBlank { "Custom REST" }, url, headers) },
                        enabled = url.isNotBlank()
                    ) {
                        Text("Add Module")
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configState = remember {
        mutableStateMapOf<String, String>().apply {
            plugin.getConfigFields().forEach { field ->
                put(field.key, initialConfig[field.key] ?: field.defaultValue)
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<com.saulhdev.feeder.manager.weather.GeoLocationResult>>(emptyList()) }
    var locationStatusMessage by remember { mutableStateOf<String?>(null) }

    val retrieveGpsLocation = {
        try {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    val providers: List<String> = lm.getProviders(true)
                    var foundLoc: Location? = null
                    for (p in providers) {
                        try {
                            @Suppress("MissingPermission")
                            val loc = lm.getLastKnownLocation(p)
                            if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                                foundLoc = loc
                                break
                            }
                        } catch (_: SecurityException) {
                        }
                    }
                    if (foundLoc != null) {
                        configState["latitude"] = String.format(Locale.US, "%.4f", foundLoc.latitude)
                        configState["longitude"] = String.format(Locale.US, "%.4f", foundLoc.longitude)

                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(foundLoc.latitude, foundLoc.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Current Location"
                                val stateOrCountry = addr.adminArea ?: addr.countryName ?: ""
                                val locStr = if (stateOrCountry.isNotBlank() && stateOrCountry != city) "$city, $stateOrCountry" else city
                                configState["location_name"] = locStr
                                locationStatusMessage = "📍 Location detected: $locStr"
                            } else {
                                configState["location_name"] = "Current Location"
                                locationStatusMessage = "📍 GPS location acquired (${configState["latitude"]}, ${configState["longitude"]})"
                            }
                        } catch (_: Exception) {
                            configState["location_name"] = "Current Location"
                            locationStatusMessage = "📍 GPS location acquired (${configState["latitude"]}, ${configState["longitude"]})"
                        }
                    } else {
                        locationStatusMessage = "⚠️ No GPS fix found yet. Please enter Address / Zip or try again."
                    }
                }
            }
        } catch (e: Exception) {
            locationStatusMessage = "Location error: ${e.message}"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms: Map<String, Boolean> ->
        val granted = perms.values.any { it }
        if (granted) {
            retrieveGpsLocation()
        } else {
            locationStatusMessage = "⚠️ Location permission denied. You can search by Address / Zip code below."
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

                if (plugin.id == "plugin_weather") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📍 Weather Location Lookup",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Find Lat/Long coordinates by city name, address, or zip code, or tap to request GPS access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text("Address, City, or Zip") },
                                    placeholder = { Text("e.g. 90210, Miami, London") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            isSearching = true
                                            coroutineScope.launch {
                                                val client = com.saulhdev.feeder.manager.weather.OpenMeteoClient()
                                                val res = client.searchLocation(searchQuery)
                                                searchResults = res.getOrDefault(emptyList())
                                                isSearching = false
                                                if (searchResults.isEmpty()) {
                                                    locationStatusMessage = "No results found for '$searchQuery'"
                                                }
                                            }
                                        }
                                    },
                                    enabled = searchQuery.isNotBlank() && !isSearching
                                ) {
                                    Text(if (isSearching) "..." else "Search")
                                }
                            }

                            if (searchResults.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select matching location:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    searchResults.forEach { loc ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 2.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    configState["location_name"] = loc.displayLabel
                                                    configState["latitude"] = String.format(Locale.US, "%.4f", loc.latitude)
                                                    configState["longitude"] = String.format(Locale.US, "%.4f", loc.longitude)
                                                    locationStatusMessage = "✅ Selected: ${loc.displayLabel}"
                                                    searchResults = emptyList()
                                                    searchQuery = ""
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = loc.displayLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "Lat: ${loc.latitude}, Lon: ${loc.longitude}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Text(
                                                    text = "Select",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (hasFine || hasCoarse) {
                                        retrieveGpsLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📍 Use Device Location (GPS)")
                            }

                            locationStatusMessage?.let { msg ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

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

