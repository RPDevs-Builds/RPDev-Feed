/*
 * This file is part of RPDev Feed
 * Copyright (c) 2026 RPDevs
 *
 * Licensed under the GNU General Public License v3.0
 */

package com.saulhdev.feeder.manager.calendar

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val color: Int?
)

object CalendarProviderHelper {
    private const val TAG = "CalendarProviderHelper"

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR
    )

    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getUpcomingEvents(context: Context, hoursAhead: Int = 24): List<CalendarEvent> {
        if (!hasCalendarPermission(context)) {
            return emptyList()
        }

        val events = mutableListOf<CalendarEvent>()
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, hoursAhead)
        }

        val builder: Uri.Builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startCal.timeInMillis)
        ContentUris.appendId(builder, endCal.timeInMillis)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                builder.build(),
                PROJECTION,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIdx = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locIdx = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val colorIdx = it.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                    val title = if (titleIdx >= 0) it.getString(titleIdx) ?: "Untitled Event" else "Untitled Event"
                    val desc = if (descIdx >= 0) it.getString(descIdx) else null
                    val loc = if (locIdx >= 0) it.getString(locIdx) else null
                    val begin = if (beginIdx >= 0) it.getLong(beginIdx) else 0L
                    val end = if (endIdx >= 0) it.getLong(endIdx) else 0L
                    val allDay = if (allDayIdx >= 0) it.getInt(allDayIdx) == 1 else false
                    val color = if (colorIdx >= 0) it.getInt(colorIdx) else null

                    events.add(
                        CalendarEvent(
                            id = id,
                            title = title,
                            description = desc,
                            location = loc,
                            startMillis = begin,
                            endMillis = end,
                            allDay = allDay,
                            color = color
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendar events", e)
        } finally {
            cursor?.close()
        }

        return events
    }
}
