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

import android.app.AlertDialog.Builder
import android.content.AsyncQueryHandler
import android.content.Context
import android.content.res.TypedArray
import android.database.Cursor
import android.preference.MultiSelectListPreference
import android.preference.PreferenceManager
import android.provider.CalendarContract
import android.util.AttributeSet
import android.view.View
import android.widget.CheckBox
import android.widget.ResourceCursorAdapter
import android.widget.TextView
import net.nurik.roman.dashclock.R

class CalendarSelectionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MultiSelectListPreference(context, attrs) {
    private val mAdapter: CalendarListAdapter
    private val mQueryHandler: QueryHandler
    private val mSelectedCalendars: MutableSet<String>

    init {
        val allCalendars = CalendarExtension.getAllCalendars(context)
        val allVisibleCalendarsSet = HashSet<String>()
        if (allCalendars != null) {
            for (pair in allCalendars) {
                if (pair.second) {
                    allVisibleCalendarsSet.add(pair.first)
                }
            }
        }

        mSelectedCalendars = HashSet(
            PreferenceManager
                .getDefaultSharedPreferences(context)
                .getStringSet(CalendarExtension.PREF_SELECTED_CALENDARS, allVisibleCalendarsSet)
                ?: emptySet()
        )

        mAdapter = CalendarListAdapter(context)
        mQueryHandler = QueryHandler(context, mAdapter)

        mQueryHandler.startQuery(
            0,
            null,
            CalendarContract.Calendars.CONTENT_URI,
            CalendarQuery.PROJECTION,
            "${CalendarContract.Calendars.SYNC_EVENTS}=1",
            null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
    }

    override fun onPrepareDialogBuilder(builder: Builder) {
        builder.setAdapter(mAdapter, null)
        builder.setTitle(R.string.pref_calendar_selected_title)
        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().putStringSet(
                CalendarExtension.PREF_SELECTED_CALENDARS,
                mSelectedCalendars
            ).commit()

            // since we have extended the list preference, it is our responsibility to inform the change listener.
            onPreferenceChangeListener?.onPreferenceChange(
                this@CalendarSelectionPreference,
                mSelectedCalendars
            )
        }
    }

    internal class QueryHandler(
        context: Context,
        private val mAdapter: CalendarListAdapter
    ) : AsyncQueryHandler(context.contentResolver) {
        override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
            mAdapter.swapCursor(cursor)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        // No support for default value attribute
        return null
    }

    inner class CalendarListAdapter(context: Context) :
        ResourceCursorAdapter(context, R.layout.list_item_calendar, null, false) {

        override fun bindView(view: View, context: Context, cursor: Cursor) {
            val calendarNameView = view.findViewById<TextView>(android.R.id.text1)
            calendarNameView.text = cursor.getString(CalendarQuery.CALENDAR_DISPLAY_NAME)

            val accountNameView = view.findViewById<TextView>(android.R.id.text2)
            accountNameView.text = cursor.getString(CalendarQuery.ACCOUNT_NAME)

            val calendarId = cursor.getString(CalendarQuery.ID)

            val checkBox = view.findViewById<CheckBox>(R.id.calendar_checkbox)
            checkBox.isChecked = mSelectedCalendars.contains(calendarId)

            view.setOnClickListener {
                if (mSelectedCalendars.contains(calendarId)) {
                    mSelectedCalendars.remove(calendarId)
                    checkBox.isChecked = false
                } else {
                    mSelectedCalendars.add(calendarId)
                    checkBox.isChecked = true
                }
            }
        }
    }

    internal object CalendarQuery {
        @JvmField
        val PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )

        const val ID = 0
        const val CALENDAR_DISPLAY_NAME = 1
        const val ACCOUNT_NAME = 2
    }
}
