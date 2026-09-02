/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.manager.sensors

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.io.File

data class BatteryTelemetry(
    val levelPercent: Int,
    val isCharging: Boolean,
    val chargePlug: String, // "AC", "USB", "Wireless", "None"
    val temperatureCelsius: Float,
    val voltageMilliVolts: Int,
    val health: String,
    val currentMicroAmperes: Int
)

data class StorageTelemetry(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedPercent: Int
)

data class MemoryTelemetry(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedPercent: Int,
    val isLowMemory: Boolean
)

data class DeviceTelemetry(
    val battery: BatteryTelemetry,
    val storage: StorageTelemetry,
    val memory: MemoryTelemetry,
    val timestamp: Long = System.currentTimeMillis()
)

object DeviceSensorsHelper {

    fun getBatteryTelemetry(context: Context): BatteryTelemetry {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) ((level / scale.toFloat()) * 100).toInt() else 0

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargePlug = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val plugType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "Battery Discharging"
        }

        val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0f
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Normal"
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val currentNow = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0

        return BatteryTelemetry(
            levelPercent = batteryPct,
            isCharging = isCharging,
            chargePlug = plugType,
            temperatureCelsius = temp,
            voltageMilliVolts = voltage,
            health = health,
            currentMicroAmperes = currentNow
        )
    }

    fun getStorageTelemetry(): StorageTelemetry {
        return try {
            val path: File = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes
            val usedPercent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

            StorageTelemetry(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                usedPercent = usedPercent
            )
        } catch (e: Exception) {
            StorageTelemetry(0L, 0L, 0)
        }
    }

    fun getMemoryTelemetry(context: Context): MemoryTelemetry {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)

            val totalBytes = memInfo.totalMem
            val availableBytes = memInfo.availMem
            val usedBytes = totalBytes - availableBytes
            val usedPercent = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt() else 0

            MemoryTelemetry(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                usedPercent = usedPercent,
                isLowMemory = memInfo.lowMemory
            )
        } catch (e: Exception) {
            MemoryTelemetry(0L, 0L, 0, false)
        }
    }

    fun getFullDeviceTelemetry(context: Context): DeviceTelemetry {
        return DeviceTelemetry(
            battery = getBatteryTelemetry(context),
            storage = getStorageTelemetry(),
            memory = getMemoryTelemetry(context)
        )
    }
}
