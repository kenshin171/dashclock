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

package com.google.android.apps.dashclock.configuration

import android.app.Fragment
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceManager
import android.preference.RingtonePreference
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.apps.dashclock.RecentTasksStyler
import com.google.android.apps.dashclock.weather.WeatherLocationPreference
import net.nurik.roman.dashclock.R

/**
 * A base activity for extension configuration activities.
 */
abstract class BaseSettingsActivity : AppCompatActivity() {
    protected lateinit var mFragment: ExtensionPreferenceFragment

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extension_configuration_simple)
        setSupportActionBar(findViewById<View>(R.id.app_bar) as Toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        RecentTasksStyler.styleRecentTasksEntry(this)

        val existingFragment =
            fragmentManager.findFragmentById(R.id.fragment_container) as? ExtensionPreferenceFragment
        if (existingFragment == null) {
            mFragment = ExtensionPreferenceFragment()
            fragmentManager.beginTransaction()
                .add(R.id.fragment_container, mFragment)
                .commit()
        } else {
            mFragment = existingFragment
        }

        (findViewById<View>(android.R.id.title) as TextView).text = title
    }

    @Deprecated("Deprecated in Java")
    override fun onAttachFragment(fragment: Fragment) {
        if (fragment is ExtensionPreferenceFragment) {
            // instanceof check necessary as not all attached fragments
            // are ExtensionPreferenceFragments (e.g. app chooser dialog fragments)
            mFragment = fragment
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            // TODO: if the previous activity on the stack isn't a ConfigurationActivity,
            // launch it.
            finish()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    protected abstract fun setupSimplePreferencesScreen()

    class ExtensionPreferenceFragment : PreferenceFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return inflater.inflate(
                R.layout.fragment_configure_extension_simple_prefs,
                container,
                false
            )
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            (activity as BaseSettingsActivity).setupSimplePreferencesScreen()
        }
    }

    companion object {
        /**
         * A preference value change listener that updates the preference's summary to reflect its new
         * value.
         */
        private val sBindPreferenceSummaryToValueListener =
            Preference.OnPreferenceChangeListener { preference, value ->
                val stringValue = value.toString()

                when (preference) {
                    is ListPreference -> {
                        // For list preferences, look up the correct display value in
                        // the preference's 'entries' list.
                        val index = preference.findIndexOfValue(stringValue)

                        // Set the summary to reflect the new value.
                        preference.setSummary(
                            if (index >= 0) {
                                preference.entries[index].toString().replace("%".toRegex(), "%%")
                            } else {
                                null
                            }
                        )
                    }
                    is RingtonePreference -> {
                        // For ringtone preferences, look up the correct display value
                        // using RingtoneManager.
                        if (TextUtils.isEmpty(stringValue)) {
                            // Empty values correspond to 'silent' (no ringtone).
                            //preference.setSummary(R.string.pref_ringtone_silent);
                        } else {
                            val ringtone = RingtoneManager.getRingtone(
                                preference.context, Uri.parse(stringValue)
                            )

                            if (ringtone == null) {
                                // Clear the summary if there was a lookup error.
                                preference.setSummary(null)
                            } else {
                                // Set the summary to reflect the new ringtone display
                                // name.
                                val name = ringtone.getTitle(preference.context)
                                preference.setSummary(name)
                            }
                        }
                    }
                    is AppChooserPreference -> {
                        preference.summary = AppChooserPreference.getDisplayValue(
                            preference.context, stringValue
                        )
                    }
                    is WeatherLocationPreference -> {
                        preference.summary = WeatherLocationPreference.getDisplayValue(
                            preference.context, stringValue
                        )
                    }
                    else -> {
                        // For all other preferences, set the summary to the value's
                        // simple string representation.
                        preference.summary = stringValue
                    }
                }
                true
            }

        /**
         * Binds a preference's summary to its value. More specifically, when the preference's value is
         * changed, its summary (line of text below the preference title) is updated to reflect the
         * value. The summary is also immediately updated upon calling this method. The exact display
         * format is dependent on the type of preference.
         */
        @JvmStatic
        fun bindPreferenceSummaryToValue(preference: Preference?) {
            if (preference == null) return
            setAndCallPreferenceChangeListener(preference, sBindPreferenceSummaryToValueListener)
        }

        /**
         * When the preference's value is changed, trigger the given listener. The listener is also
         * immediately called with the preference's current value upon calling this method.
         */
        @JvmStatic
        fun setAndCallPreferenceChangeListener(
            preference: Preference?,
            listener: Preference.OnPreferenceChangeListener
        ) {
            if (preference == null) return
            // Set the listener to watch for value changes.
            preference.onPreferenceChangeListener = listener

            // Trigger the listener immediately with the preference's
            // current value.
            listener.onPreferenceChange(
                preference,
                PreferenceManager
                    .getDefaultSharedPreferences(preference.context)
                    .getString(preference.key, "")
            )
        }
    }
}
