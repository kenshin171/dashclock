/*
 * Copyright 2015 Google Inc.
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
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ListView
import net.nurik.roman.dashclock.R

/**
 * Fragment for allowing the user to configure advanced appearance settings.
 */
class ConfigureAppearanceMoreFragment : PreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mListBackgroundView: View
    private lateinit var mListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Add 'advanced' preferences.
        addPreferencesFromResource(R.xml.pref_appearance)

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        BaseSettingsActivity.bindPreferenceSummaryToValue(
            findPreference(AppearanceConfig.PREF_HOMESCREEN_BACKGROUND_OPACITY)
        )
        BaseSettingsActivity.bindPreferenceSummaryToValue(
            findPreference(AppearanceConfig.PREF_LOCKSCREEN_BACKGROUND_OPACITY)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val p = findPreference("pref_category_lockscreen")
            if (p != null) {
                preferenceScreen.removePreference(p)
            }
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
        val rootView = inflater.inflate(
            R.layout.fragment_configure_appearance_more,
            container,
            false
        )
        mListBackgroundView = rootView.findViewById(R.id.listview_background)
        mListView = rootView.findViewById<View>(android.R.id.list) as ListView
        mListView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {
            }

            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                updateListBackgroundPosition()
            }
        })
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateListBackgroundPosition()
    }

    private fun updateListBackgroundPosition() {
        val topPadding = mListView.paddingTop
        var top = topPadding
        if (mListView.childCount > 0) {
            top = if (mListView.firstVisiblePosition == 0) {
                mListView.getChildAt(0).top
            } else {
                0
            }
        }

        top = Math.max(0, top)
        mListBackgroundView.translationY = top.toFloat()
        (parentFragment as ConfigureAppearanceFragment)
            .setAppearanceContainerTranslationY(top - topPadding)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        BackupManager(activity).dataChanged()
    }
}
