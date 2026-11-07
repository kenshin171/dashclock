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

package com.google.android.apps.dashclock.gmail

import android.preference.MultiSelectListPreference
import android.preference.Preference
import android.preference.PreferenceManager
import com.google.android.apps.dashclock.configuration.BaseSettingsActivity
import net.nurik.roman.dashclock.R

class GmailSettingsActivity : BaseSettingsActivity() {
    override fun setupSimplePreferencesScreen() {
        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        mFragment.addPreferencesFromResource(R.xml.pref_gmail)

        addAccountsPreference()

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(mFragment.findPreference(GmailExtension.PREF_LABEL))
    }

    private fun addAccountsPreference() {
        val accounts = GmailExtension.getAllAccountNames(this)
        val allAccountsSet = HashSet<String>(accounts.toList())

        val accountsPreference = MultiSelectListPreference(this)
        accountsPreference.key = GmailExtension.PREF_ACCOUNTS
        accountsPreference.setTitle(R.string.pref_gmail_accounts_title)
        accountsPreference.entries = accounts
        accountsPreference.entryValues = accounts
        accountsPreference.setDefaultValue(allAccountsSet)
        mFragment.preferenceScreen.addPreference(accountsPreference)

        val accountsChangeListener = Preference.OnPreferenceChangeListener { preference, value ->
            var numSelected = 0
            val numTotal = accounts.size

            try {
                @Suppress("UNCHECKED_CAST")
                val selectedAccounts = value as? Set<String>
                if (selectedAccounts != null) {
                    numSelected = selectedAccounts.size
                }
            } catch (ignored: ClassCastException) {
            }

            preference.summary = resources.getQuantityString(
                R.plurals.pref_gmail_accounts_summary_template,
                numTotal, numSelected, numTotal
            )
            true
        }

        accountsPreference.onPreferenceChangeListener = accountsChangeListener
        accountsChangeListener.onPreferenceChange(
            accountsPreference,
            PreferenceManager
                .getDefaultSharedPreferences(this)
                .getStringSet(accountsPreference.key, allAccountsSet)
        )
    }
}
