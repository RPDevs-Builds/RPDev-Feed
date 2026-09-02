/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.plugins.impl

import android.content.Context
import com.saulhdev.feeder.manager.calendar.CalendarProviderHelper
import com.saulhdev.feeder.plugins.ConfigFieldType
import com.saulhdev.feeder.plugins.HubPlugin
import com.saulhdev.feeder.plugins.PluginCategory
import com.saulhdev.feeder.plugins.PluginConfigField
import com.saulhdev.feeder.plugins.models.HubAction
import com.saulhdev.feeder.plugins.models.HubCardData
import com.saulhdev.feeder.plugins.models.HubTimelineItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarPlugin : HubPlugin {

    override val id: String = "plugin_calendar"
    override val name: String = "Calendar Agenda"
    override val description: String = "On-device agenda engine querying the upcoming 24-hour schedule."
    override val category: PluginCategory = PluginCategory.PRODUCTIVITY
    override val iconName: String = "calendar"
    override val defaultRefreshMinutes: Int = 15

    override fun getConfigFields(): List<PluginConfigField> = listOf(
        PluginConfigField(
            key = "lookahead_hours",
            label = "Lookahead Hours",
            description = "Number of hours to scan ahead for events (default 24)",
            type = ConfigFieldType.NUMBER,
            defaultValue = "24"
        )
    )

    override suspend fun fetchCardData(
        context: Context,
        config: Map<String, String>
    ): Result<HubCardData> {
        val hours = config["lookahead_hours"]?.toIntOrNull() ?: 24
        val events = CalendarProviderHelper.getUpcomingEvents(context, hours)

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timelineItems = events.map { e ->
            val timeRange = "${timeFormat.format(Date(e.startMillis))} - ${timeFormat.format(Date(e.endMillis))}"
            HubTimelineItem(
                title = e.title,
                subtitle = if (!e.location.isNullOrBlank()) "${e.location} • $timeRange" else timeRange,
                tag = if (e.allDay) "All Day" else null,
                iconName = "calendar_event",
                clickUrl = "content://com.android.calendar/events/${e.id}",
                statusSuccess = true
            )
        }

        val card = HubCardData.Timeline(
            pluginId = id,
            title = "📅 Agenda",
            subtitle = if (events.isEmpty()) "No upcoming events in next ${hours}h" else "${events.size} upcoming events",
            badge = if (events.isNotEmpty()) "${events.size} Events" else "Clear",
            items = timelineItems,
            actions = listOf(
                HubAction(label = "Open Calendar", isPrimary = true, intentAction = "android.intent.action.MAIN")
            )
        )

        return Result.success(card)
    }
}
