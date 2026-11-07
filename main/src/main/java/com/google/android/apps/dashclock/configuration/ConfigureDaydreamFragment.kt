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

import android.app.backup.BackupManager
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.apps.dashclock.DaydreamService
import net.nurik.roman.dashclock.R

/**
 * Fragment for allowing the user to configure daydream settings, shown within a [ConfigurationActivity].
 */
class ConfigureDaydreamFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Add 'advanced' preferences.
        addPreferencesFromResource(R.xml.pref_daydream)

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        BaseSettingsActivity.bindPreferenceSummaryToValue(
            findPreference(DaydreamService.PREF_DAYDREAM_ANIMATION)
        )

        // Hide Daydream settings if the activity doesn't exist.
        hidePreferenceIfTroublesomeIntent(PREF_DAYDREAM_SYSTEM_SETTINGS)
    }

    private fun hidePreferenceIfTroublesomeIntent(key: String) {
        val pref = findPreference(key) ?: return

        val intent = pref.intent
        if (intent != null && activity.packageManager.resolveActivity(intent, 0) == null) {
            preferenceScreen.removePreference(pref)
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_configure_simple_prefs, container, false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String?) {
        BackupManager(activity).dataChanged()
    }

    companion object {
        private const val PREF_DAYDREAM_SYSTEM_SETTINGS = "pref_daydream_system_settings"
    }
}
