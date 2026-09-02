/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins

import android.content.Context
import com.saulhdev.feeder.plugins.models.HubCardData

enum class PluginCategory(val displayName: String) {
    DEVELOPER("Developer & Code"),
    PRODUCTIVITY("Productivity & Calendar"),
    SYSTEM("System & Hardware"),
    WEATHER("Weather & Environment"),
    IOT("IoT & Smart Home"),
    CUSTOM("Custom REST / Webhooks")
}

enum class ConfigFieldType {
    TEXT,
    PASSWORD,
    NUMBER,
    BOOLEAN
}

data class PluginConfigField(
    val key: String,
    val label: String,
    val description: String,
    val type: ConfigFieldType = ConfigFieldType.TEXT,
    val defaultValue: String = "",
    val isRequired: Boolean = false
)

interface HubPlugin {
    val id: String
    val name: String
    val description: String
    val category: PluginCategory
    val iconName: String
    val defaultRefreshMinutes: Int

    /**
     * Declarative list of settings and API keys required by the plugin.
     */
    fun getConfigFields(): List<PluginConfigField> = emptyList()

    /**
     * Executes the plugin data fetch on IO thread and transforms it to a standard HubCardData.
     */
    suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData>
}
