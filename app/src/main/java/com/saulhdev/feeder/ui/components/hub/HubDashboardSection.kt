/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.ui.components.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.plugins.HubPluginRegistry

@Composable
fun HubDashboardSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val registry = remember { HubPluginRegistry.getInstance(context) }
    val cards by registry.cardsFlow.collectAsState()

    LaunchedEffect(Unit) {
        registry.refreshCards()
    }

    if (cards.isNotEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            cards.forEach { cardData ->
                GenericHubCard(cardData = cardData)
            }
        }
    }
}
