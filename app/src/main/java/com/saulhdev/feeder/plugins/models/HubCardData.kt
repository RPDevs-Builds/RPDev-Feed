/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins.models

import kotlinx.serialization.Serializable

@Serializable
sealed class HubCardData {
    abstract val pluginId: String
    abstract val title: String
    abstract val subtitle: String?
    abstract val badge: String?
    abstract val actions: List<HubAction>

    @Serializable
    data class Metric(
        override val pluginId: String,
        override val title: String,
        override val subtitle: String? = null,
        override val badge: String? = null,
        val chips: List<HubChip> = emptyList(),
        override val actions: List<HubAction> = emptyList()
    ) : HubCardData()

    @Serializable
    data class Timeline(
        override val pluginId: String,
        override val title: String,
        override val subtitle: String? = null,
        override val badge: String? = null,
        val items: List<HubTimelineItem> = emptyList(),
        override val actions: List<HubAction> = emptyList()
    ) : HubCardData()

    @Serializable
    data class Progress(
        override val pluginId: String,
        override val title: String,
        override val subtitle: String? = null,
        override val badge: String? = null,
        val progressPercent: Float, // 0.0f to 1.0f
        val progressLabel: String,
        val chips: List<HubChip> = emptyList(),
        override val actions: List<HubAction> = emptyList()
    ) : HubCardData()

    @Serializable
    data class Composite(
        override val pluginId: String,
        override val title: String,
        override val subtitle: String? = null,
        override val badge: String? = null,
        val chips: List<HubChip> = emptyList(),
        val timelineItems: List<HubTimelineItem> = emptyList(),
        val progressPercent: Float? = null,
        val progressLabel: String? = null,
        override val actions: List<HubAction> = emptyList()
    ) : HubCardData()
}

@Serializable
data class HubChip(
    val label: String,
    val iconName: String? = null,
    val colorHex: String? = null,
    val clickUrl: String? = null
)

@Serializable
data class HubTimelineItem(
    val title: String,
    val subtitle: String? = null,
    val tag: String? = null,
    val timestamp: String? = null,
    val iconName: String? = null,
    val clickUrl: String? = null,
    val statusSuccess: Boolean? = null
)

@Serializable
data class HubAction(
    val label: String,
    val url: String? = null,
    val isPrimary: Boolean = false,
    val intentAction: String? = null
)
