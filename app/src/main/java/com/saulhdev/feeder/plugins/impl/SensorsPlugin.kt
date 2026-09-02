/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins.impl

import android.content.Context
import com.saulhdev.feeder.manager.sensors.DeviceSensorsHelper
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.PluginCategory
import com.saulhdev.feeder.plugins.models.HubAction
import com.saulhdev.feeder.plugins.models.HubCardData
import com.saulhdev.feeder.plugins.models.HubChip

class SensorsPlugin : HubPlugin {

    override val id: String = "plugin_sensors"
    override val name: String = "Hardware & Battery"
    override val description: String = "Real-time battery charging wattage, voltage, temperature, internal storage, and RAM memory pressure."
    override val category: PluginCategory = PluginCategory.SYSTEM
    override val iconName: String = "cpu"
    override val defaultRefreshMinutes: Int = 5

    override suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData> {
        val telemetry = DeviceSensorsHelper.getFullDeviceTelemetry(context)
        val bat = telemetry.battery
        val mem = telemetry.memory
        val storage = telemetry.storage

        val freeStorageGb = storage.availableBytes / (1024 * 1024 * 1024)
        val totalStorageGb = storage.totalBytes / (1024 * 1024 * 1024)
        val usedStorageGb = totalStorageGb - freeStorageGb

        val availMemMb = mem.availableBytes / (1024 * 1024)
        val totalMemMb = mem.totalBytes / (1024 * 1024)

        val chips = listOf(
            HubChip(
                label = "⚡ ${bat.levelPercent}% (${if (bat.isCharging) bat.chargePlug else "Battery"})",
                colorHex = if (bat.levelPercent <= 20) "#f44336" else "#4caf50"
            ),
            HubChip(
                label = "🌡️ ${String.format("%.1f", bat.temperatureCelsius)}°C",
                colorHex = if (bat.temperatureCelsius >= 45.0) "#f44336" else "#ff9800"
            ),
            HubChip(
                label = "💾 RAM: ${availMemMb}MB / ${totalMemMb}MB",
                colorHex = "#9c27b0"
            ),
            HubChip(
                label = "📁 Storage: ${freeStorageGb}GB Free (${storage.usedPercent}% Used)",
                colorHex = "#3f51b5"
            )
        )

        val card = HubCardData.Progress(
            pluginId = id,
            title = "🔋 Device Telemetry",
            subtitle = "Battery: ${bat.health} (${bat.voltageMilliVolts}mV)",
            badge = "${bat.levelPercent}%",
            progressPercent = storage.usedPercent / 100f,
            progressLabel = "Storage: ${usedStorageGb}GB / ${totalStorageGb}GB Used",
            chips = chips,
            actions = listOf(
                HubAction(label = "Battery Settings", isPrimary = true, intentAction = android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS),
                HubAction(label = "Storage", intentAction = android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            )
        )

        return Result.success(card)
    }
}
