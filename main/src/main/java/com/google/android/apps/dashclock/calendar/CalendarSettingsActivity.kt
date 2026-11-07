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

import android.preference.Preference
import android.preference.PreferenceManager
import com.google.android.apps.dashclock.configuration.BaseSettingsActivity
import net.nurik.roman.dashclock.R

class CalendarSettingsActivity : BaseSettingsActivity() {
    override fun setupSimplePreferencesScreen() {
        // Add 'general' preferences.
        mFragment.addPreferencesFromResource(R.xml.pref_calendar)
        bindSelectedCalendarsPreference()

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(mFragment.findPreference(CalendarExtension.PREF_LOOK_AHEAD_HOURS))
    }

    private fun bindSelectedCalendarsPreference() {
        val preference = mFragment.findPreference(
            CalendarExtension.PREF_SELECTED_CALENDARS
        ) as CalendarSelectionPreference
        val allCalendars = CalendarExtension.getAllCalendars(this)
        val allVisibleCalendarsSet = HashSet<String>()
        if (allCalendars != null) {
            for (pair in allCalendars) {
                if (pair.second) {
                    allVisibleCalendarsSet.add(pair.first)
                }
            }
        }

        val calendarsChangeListener = Preference.OnPreferenceChangeListener { pref, value ->
            var numSelected = 0
            val numTotal = allCalendars?.size ?: 0

            try {
                @Suppress("UNCHECKED_CAST")
                val selectedCalendars = value as? Set<String>
                if (selectedCalendars != null) {
                    numSelected = selectedCalendars.size
                }
            } catch (ignored: ClassCastException) {
            }

            pref.summary = resources.getQuantityString(
                R.plurals.pref_calendar_selected_summary_template,
                numTotal, numSelected, numTotal
            )
            true
        }

        preference.onPreferenceChangeListener = calendarsChangeListener
        calendarsChangeListener.onPreferenceChange(
            preference,
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .getStringSet(preference.key, allVisibleCalendarsSet)
        )
    }
}
