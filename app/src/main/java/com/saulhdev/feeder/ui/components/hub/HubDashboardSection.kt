/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.ui.components.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.manager.calendar.CalendarEvent
import com.saulhdev.feeder.manager.calendar.CalendarProviderHelper
import com.saulhdev.feeder.manager.sensors.DeviceSensorsHelper
import com.saulhdev.feeder.manager.sensors.DeviceTelemetry
import com.saulhdev.feeder.manager.weather.OpenMeteoClient
import com.saulhdev.feeder.manager.weather.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HubDashboardSection(
    modifier: Modifier = Modifier,
    showWeather: Boolean = true,
    showCalendar: Boolean = true,
    showSensors: Boolean = true,
    defaultLatitude: Double = 40.7128,
    defaultLongitude: Double = -74.0060,
    isFahrenheit: Boolean = false
) {
    val context = LocalContext.current
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var calendarEvents by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var deviceTelemetry by remember { mutableStateOf<DeviceTelemetry?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (showSensors) {
                deviceTelemetry = DeviceSensorsHelper.getFullDeviceTelemetry(context)
            }
            if (showCalendar) {
                calendarEvents = CalendarProviderHelper.getUpcomingEvents(context, 24)
            }
            if (showWeather) {
                val client = OpenMeteoClient()
                client.fetchWeather(defaultLatitude, defaultLongitude, isFahrenheit).onSuccess {
                    weatherInfo = it
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (showWeather && weatherInfo != null) {
            WeatherHubCard(weather = weatherInfo!!, isFahrenheit = isFahrenheit)
        }

        if (showCalendar && calendarEvents.isNotEmpty()) {
            CalendarHubCard(events = calendarEvents)
        }

        if (showSensors && deviceTelemetry != null) {
            HardwareSensorsCard(telemetry = deviceTelemetry!!)
        }
    }
}
