/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.calendar

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.Pair
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.Utils.HOURS_MILLIS
import com.google.android.apps.dashclock.Utils.MINUTES_MILLIS
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import net.nurik.roman.dashclock.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Calendar "upcoming appointment" extension.
 */
class CalendarExtension : DashClockExtension() {
    private var mLookAheadHours = DEFAULT_LOOK_AHEAD_HOURS

    private val selectedCalendars: Set<String>
        get() {
            val sp = PreferenceManager.getDefaultSharedPreferences(this)
            val customVisibility = sp.getBoolean(PREF_CUSTOM_VISIBILITY, false)
            var selectedCalendars = sp.getStringSet(PREF_SELECTED_CALENDARS, null)
            if (!customVisibility || selectedCalendars == null) {
                val allCalendars = getAllCalendars(this)

                // Build a set of all visible calendars in case we don't have a selection set in
                // the preferences.
                selectedCalendars = HashSet()
                if (allCalendars != null) {
                    for (pair in allCalendars) {
                        if (pair.second) {
                            selectedCalendars.add(pair.first)
                        }
                    }
                }
            }
            return selectedCalendars
        }

    override fun onInitialize(isReconnect: Boolean) {
        super.onInitialize(isReconnect)
        if (!isReconnect) {
            addWatchContentUris(
                arrayOf(
                    CalendarContract.Events.CONTENT_URI.toString()
                )
            )
        }

        setUpdateWhenScreenOn(true)
    }

    override fun onUpdateData(reason: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val showAllDay = sp.getBoolean(PREF_SHOW_ALL_DAY, false)

        try {
            mLookAheadHours = sp.getString(
                PREF_LOOK_AHEAD_HOURS,
                mLookAheadHours.toString()
            )!!.toInt()
        } catch (e: NumberFormatException) {
            mLookAheadHours = DEFAULT_LOOK_AHEAD_HOURS
        }

        val cursor = tryOpenEventsCursor(showAllDay)
        if (cursor == null) {
            LOGE(TAG, "Null events cursor, short-circuiting.")
            return
        }

        val currentTimestamp = currentTimestamp
        var nextTimestamp: Long = 0
        var endTimestamp: Long = 0
        var timeUntilNextAppointent: Long = 0
        var allDay = false
        var allDayPosition = -1
        var allDayTimestampLocalMidnight: Long = 0
        while (cursor.moveToNext()) {
            nextTimestamp = cursor.getLong(EventsQuery.BEGIN)
            val tzOffset = TimeZone.getDefault().getOffset(nextTimestamp)
            allDay = cursor.getInt(EventsQuery.ALL_DAY) != 0
            if (allDay) {
                endTimestamp = cursor.getLong(EventsQuery.END) - tzOffset

                if (showAllDay && allDayPosition < 0 && endTimestamp > currentTimestamp) {
                    // Store the position of this all day event. If no regular events are found
                    // and the user wanted to see all day events, then show this all day event.
                    allDayPosition = cursor.position

                    // For all day events (if the user wants to see them), convert the begin
                    // timestamp, which is the midnight UTC time, to local time. That is,
                    // allDayTimestampLocalMidnight will be midnight in local time since that's a
                    // more relevant representation of that day to the user.
                    allDayTimestampLocalMidnight = nextTimestamp - tzOffset
                }
                continue
            }

            timeUntilNextAppointent = nextTimestamp - currentTimestamp

            if (timeUntilNextAppointent >= -NOW_BUFFER_TIME_MILLIS) {
                // Use this event even if it's already started, a few minutes in
                break
            }

            // Skip over events that are not ALL_DAY but span multiple days, including
            // the next 6 hours. An example is an event that starts at 4pm yesterday
            // day and ends 6pm tomorrow.
            LOGD(
                TAG,
                "Skipping over event with start timestamp $nextTimestamp. Current timestamp $currentTimestamp"
            )
        }

        if (allDayPosition >= 0) {
            // Only show all day events if there's no regular event (cursor is after last)
            // or if the all day event is tomorrow or later and the all day event is earlier than
            // the regular event.
            if (cursor.isAfterLast
                || (allDayTimestampLocalMidnight - currentTimestamp > 0
                        && allDayTimestampLocalMidnight < nextTimestamp)
            ) {
                cursor.moveToPosition(allDayPosition)
                allDay = true

                nextTimestamp = allDayTimestampLocalMidnight
                timeUntilNextAppointent = nextTimestamp - currentTimestamp
                LOGD(
                    TAG,
                    "Showing an all day event because either no regular event was found or it's a full day later than the all-day event."
                )
            }
        }

        if (cursor.isAfterLast) {
            LOGD(TAG, "No upcoming appointments found.")
            cursor.close()
            publishUpdate(ExtensionData())
            return
        }

        val nextEventCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        nextEventCalendar.timeInMillis = nextTimestamp

        val minutesUntilNextAppointment = (timeUntilNextAppointent / MINUTES_MILLIS).toInt()

        val untilString: String = if (allDay) {
            if (timeUntilNextAppointent <= 0) {
                // All day event happening today (its start time is past today's midnight
                // offset.
                getString(R.string.today)
            } else {
                SimpleDateFormat("E").format(nextEventCalendar.time)
            }
        } else if (minutesUntilNextAppointment < 2) {
            resources.getString(R.string.now)
        } else if (minutesUntilNextAppointment < 60) {
            resources.getQuantityString(
                R.plurals.calendar_template_mins,
                minutesUntilNextAppointment,
                minutesUntilNextAppointment
            )
        } else {
            val hours = (minutesUntilNextAppointment / 60f).roundToInt()
            if (hours < 24) {
                resources.getQuantityString(
                    R.plurals.calendar_template_hours, hours, hours
                )
            } else {
                SimpleDateFormat("E").format(nextEventCalendar.time)
            }
        }
        val eventTitle = cursor.getString(EventsQuery.TITLE)
        val eventLocation = cursor.getString(EventsQuery.EVENT_LOCATION)

        val eventId = cursor.getLong(EventsQuery.EVENT_ID)
        val eventBegin = cursor.getLong(EventsQuery.BEGIN)
        val eventEnd = cursor.getLong(EventsQuery.END)
        cursor.close()

        var expandedTime: String? = null
        val expandedTimeFormat = StringBuilder()
        if (allDay) {
            if (timeUntilNextAppointent <= 0) {
                // All day event happening today (its start time is past today's midnight
                // offset.
                expandedTimeFormat.setLength(0)
                expandedTime = getString(R.string.today)
            } else {
                expandedTimeFormat.append("EEEE, MMM dd")
            }
        } else if (minutesUntilNextAppointment < 2) {
            // Event happening right now!
            expandedTimeFormat.setLength(0)
            expandedTime = getString(R.string.now)
        } else {
            if (timeUntilNextAppointent > 24 * HOURS_MILLIS) {
                expandedTimeFormat.append("EEEE, ")
            }

            if (DateFormat.is24HourFormat(this)) {
                expandedTimeFormat.append("HH:mm")
            } else {
                expandedTimeFormat.append("h:mm a")
            }
        }

        if (expandedTimeFormat.isNotEmpty()) {
            expandedTime = SimpleDateFormat(expandedTimeFormat.toString())
                .format(nextEventCalendar.time)
        }

        var expandedBody = expandedTime
        if (!TextUtils.isEmpty(eventLocation)) {
            expandedBody = getString(
                R.string.calendar_with_location_template,
                expandedTime, eventLocation
            )
        }

        publishUpdate(
            ExtensionData()
                .visible(
                    allDay || (timeUntilNextAppointent >= -NOW_BUFFER_TIME_MILLIS
                            && timeUntilNextAppointent <= mLookAheadHours * HOURS_MILLIS)
                )
                .icon(R.drawable.ic_extension_calendar)
                .status(untilString)
                .expandedTitle(eventTitle)
                .expandedBody(expandedBody)
                .clickIntent(
                    Intent(Intent.ACTION_VIEW)
                        .setData(
                            Uri.withAppendedPath(
                                CalendarContract.Events.CONTENT_URI,
                                eventId.toString()
                            )
                        )
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, eventBegin)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, eventEnd)
                )
        )
    }

    private fun tryOpenEventsCursor(showAllDay: Boolean): Cursor? {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val customVisibility = sp.getBoolean(PREF_CUSTOM_VISIBILITY, false)

        // Filter out all day events unless the user expressly requested to show all day events
        var allDaySelection = SQL_TAUTOLOGY
        if (!showAllDay) {
            allDaySelection = "${CalendarContract.Instances.ALL_DAY}=0"
        }

        // Only filter on visible calendars if there isn't custom visibility
        val visibleCalendarsSelection = SQL_TAUTOLOGY
        if (!customVisibility) {
            allDaySelection = "${CalendarContract.Instances.VISIBLE}!=0"
        }

        val calendarSelection = generateCalendarSelection()
        val calendarSet = selectedCalendars
        val calendarsSelectionArgs = calendarSet.toTypedArray()

        val now = currentTimestamp

        return try {
            contentResolver.query(
                CalendarContract.Instances.CONTENT_URI.buildUpon()
                    .appendPath((now - NOW_BUFFER_TIME_MILLIS).toString())
                    .appendPath((now + mLookAheadHours * HOURS_MILLIS).toString())
                    .build(),
                EventsQuery.PROJECTION,
                allDaySelection + " AND "
                        + CalendarContract.Instances.SELF_ATTENDEE_STATUS + "!="
                        + CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED + " AND "
                        + "IFNULL(" + CalendarContract.Instances.STATUS + ",0)!="
                        + CalendarContract.Instances.STATUS_CANCELED + " AND "
                        + visibleCalendarsSelection + " AND ("
                        + calendarSelection + ")",
                calendarsSelectionArgs,
                CalendarContract.Instances.BEGIN
            )
        } catch (e: Exception) {
            LOGE(TAG, "Error querying calendar API", e)
            null
        }
    }

    private fun generateCalendarSelection(): String {
        val calendars = selectedCalendars
        val count = calendars.size

        val sb = StringBuilder()

        for (i in 0 until count) {
            if (i != 0) {
                sb.append(" OR ")
            }

            sb.append(CalendarContract.Events.CALENDAR_ID)
            sb.append(" = ?")
        }

        if (sb.isEmpty()) {
            sb.append(SQL_TAUTOLOGY) // constant expression to prevent returning null
        }

        return sb.toString()
    }

    private object EventsQuery {
        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.ALL_DAY
        )

        const val EVENT_ID = 0
        const val BEGIN = 1
        const val END = 2
        const val TITLE = 3
        const val EVENT_LOCATION = 4
        const val ALL_DAY = 5
    }

    private object CalendarsQuery {
        val PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.VISIBLE
        )

        const val ID = 0
        const val VISIBLE = 1
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(CalendarExtension::class.java)

        const val PREF_CUSTOM_VISIBILITY = "pref_calendar_custom_visibility"
        const val PREF_SELECTED_CALENDARS = "pref_calendar_selected"
        const val PREF_LOOK_AHEAD_HOURS = "pref_calendar_look_ahead_hours"
        const val PREF_SHOW_ALL_DAY = "pref_calendar_show_all_day"

        private const val SQL_TAUTOLOGY = "1=1"

        // Show events happening "now" if they started under 5 minutes ago
        const val NOW_BUFFER_TIME_MILLIS = 5 * MINUTES_MILLIS

        private const val DEFAULT_LOOK_AHEAD_HOURS = 6

        @JvmStatic
        internal fun getAllCalendars(context: Context): List<Pair<String, Boolean>>? {
            // Only return calendars that are marked as synced to device.
            // (This is different from the display flag)
            val calendars = ArrayList<Pair<String, Boolean>>()

            try {
                val cursor = context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    CalendarsQuery.PROJECTION,
                    "${CalendarContract.Calendars.SYNC_EVENTS}=1",
                    null,
                    null
                )

                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        calendars.add(
                            Pair(
                                cursor.getString(CalendarsQuery.ID),
                                cursor.getInt(CalendarsQuery.VISIBLE) == 1
                            )
                        )
                    }

                    cursor.close()
                }
            } catch (e: SecurityException) {
                LOGE(TAG, "Error querying calendar API", e)
                return null
            }

            return calendars
        }

        private val currentTimestamp: Long
            get() = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis
    }
}
