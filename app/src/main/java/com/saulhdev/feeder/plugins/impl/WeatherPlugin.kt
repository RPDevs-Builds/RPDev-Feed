/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins.impl

import android.content.Context
import com.saulhdev.feeder.manager.weather.OpenMeteoClient
import com.saulhdev.feeder.plugins.ConfigFieldType
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.PluginCategory
import com.saulhdev.feeder.plugins.PluginConfigField
import com.saulhdev.feeder.plugins.models.HubAction
import com.saulhdev.feeder.plugins.models.HubCardData
import com.saulhdev.feeder.plugins.models.HubChip
import com.saulhdev.feeder.plugins.models.HubTimelineItem

class WeatherPlugin : HubPlugin {

    override val id: String = "plugin_weather"
    override val name: String = "Privacy Weather"
    override val description: String = "Zero-telemetry live weather and hourly forecast from Open-Meteo."
    override val category: PluginCategory = PluginCategory.WEATHER
    override val iconName: String = "sun"
    override val defaultRefreshMinutes: Int = 30

    private val client by lazy { OpenMeteoClient() }

    override fun getConfigFields(): List<PluginConfigField> = listOf(
        PluginConfigField(
            key = "latitude",
            label = "Latitude",
            description = "Latitude coordinate for weather location",
            type = ConfigFieldType.NUMBER,
            defaultValue = "40.7128"
        ),
        PluginConfigField(
            key = "longitude",
            label = "Longitude",
            description = "Longitude coordinate for weather location",
            type = ConfigFieldType.NUMBER,
            defaultValue = "-74.0060"
        ),
        PluginConfigField(
            key = "use_fahrenheit",
            label = "Use Fahrenheit (°F)",
            description = "Toggle between Celsius and Fahrenheit units",
            type = ConfigFieldType.BOOLEAN,
            defaultValue = "false"
        )
    )

    override suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData> {
        val lat = config["latitude"]?.toDoubleOrNull() ?: 40.7128
        val lon = config["longitude"]?.toDoubleOrNull() ?: -74.0060
        val isFahrenheit = config["use_fahrenheit"]?.toBooleanStrictOrNull() ?: false

        return client.fetchWeather(lat, lon, isFahrenheit).map { weather ->
            val unit = if (isFahrenheit) "°F" else "°C"
            val chips = listOf(
                HubChip(label = "🌡️ Feels: ${weather.apparentTemperature.toInt()}$unit", colorHex = "#00bcd4"),
                HubChip(label = "💧 Humidity: ${weather.relativeHumidity}%", colorHex = "#2196f3"),
                HubChip(label = "💨 Wind: ${weather.windSpeed.toInt()} km/h", colorHex = "#607d8b")
            )

            val timelineItems = weather.hourlyForecast.take(4).map { h ->
                val (cond, icon) = OpenMeteoClient.mapWeatherCode(h.weatherCode, true)
                HubTimelineItem(
                    title = "${h.time}: ${h.temperature.toInt()}$unit",
                    subtitle = "$icon $cond",
                    tag = "Forecast",
                    iconName = "cloud",
                    statusSuccess = true
                )
            }

            HubCardData.Composite(
                pluginId = id,
                title = "🌦️ Weather ${weather.conditionIcon}",
                subtitle = "${weather.temperature.toInt()}$unit — ${weather.conditionDescription}",
                badge = "${weather.temperature.toInt()}$unit",
                chips = chips,
                timelineItems = timelineItems,
                actions = listOf(
                    HubAction(label = "Radar / Forecast", url = "https://open-meteo.com", isPrimary = true)
                )
            )
        }
    }
}
