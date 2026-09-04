/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */
package com.saulhdev.feeder.ui.components.hub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.plugins.HubPluginRegistry
import com.saulhdev.feeder.plugins.models.HubCardData
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.CaretDown
import com.saulhdev.feeder.ui.icons.phosphor.CaretUp
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.TrashSimple
import com.saulhdev.feeder.ui.pages.PluginConfigDialog

@Composable
fun HubDashboardSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val registry = remember { HubPluginRegistry.getInstance(context) }
    val cards by registry.cardsFlow.collectAsState()
    val dismissedIds by registry.dismissedCardIds.collectAsState()

    LaunchedEffect(Unit) {
        registry.refreshCards(clearDismissed = false)
    }

    val visibleCards = remember(cards, dismissedIds) {
        cards.filter { it.pluginId !in dismissedIds }
    }

    if (visibleCards.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visibleCards.forEach { cardData ->
                SwipeableHubCard(
                    key = cardData.pluginId,
                    cardData = cardData,
                    onDismiss = {
                        registry.dismissCard(cardData.pluginId)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SwipeableHubCard(
    key: String,
    cardData: HubCardData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val registry = remember { HubPluginRegistry.getInstance(context) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.StartToEnd || dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )

    var showMenu by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    val plugin = remember(cardData.pluginId) {
        registry.getAllPlugins().firstOrNull { it.id == cardData.pluginId }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                )
        ) {
            GenericHubCard(cardData = cardData)

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (plugin != null && plugin.getConfigFields().isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Configure ${plugin.name}") },
                        leadingIcon = {
                            Icon(
                                imageVector = Phosphor.GearSix,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            showConfigDialog = true
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Refresh Module") },
                    leadingIcon = {
                        Icon(
                            imageVector = Phosphor.ArrowCounterClockwise,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        registry.refreshCards(clearDismissed = false)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Move Up") },
                    leadingIcon = {
                        Icon(
                            imageVector = Phosphor.CaretUp,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        registry.movePluginUp(cardData.pluginId)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Move Down") },
                    leadingIcon = {
                        Icon(
                            imageVector = Phosphor.CaretDown,
                            contentDescription = null
                        )
                    },
                    onClick = {
                        showMenu = false
                        registry.movePluginDown(cardData.pluginId)
                    }
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Dismiss for Now") },
                    leadingIcon = {
                        Icon(
                            imageVector = Phosphor.TrashSimple,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDismiss()
                    }
                )
            }
        }
    }

    if (showConfigDialog && plugin != null) {
        val initialConfig = remember(plugin.id) { registry.getPluginConfig(plugin.id) }
        PluginConfigDialog(
            plugin = plugin,
            initialConfig = initialConfig,
            onDismiss = { showConfigDialog = false },
            onSave = { newConfig ->
                registry.savePluginConfig(plugin.id, newConfig)
                showConfigDialog = false
            }
        )
    }
}
