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

package com.google.android.apps.dashclock.nextalarm

import android.annotation.TargetApi
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.Utils
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.configuration.AppChooserPreference
import net.nurik.roman.dashclock.R
import java.util.regex.Pattern

/**
 * Next alarm extension.
 */
class NextAlarmExtension : DashClockExtension() {
    private var mRegistered = false
    private val mNextAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onUpdateData(0)
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun registerNextAlarmBroadcast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val filter = IntentFilter()
            filter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            ContextCompat.registerReceiver(
                this,
                mNextAlarmReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            mRegistered = true
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun unregisterNextAlarmBroadcast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mRegistered) {
                unregisterReceiver(mNextAlarmReceiver)
                mRegistered = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerNextAlarmBroadcast()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNextAlarmBroadcast()
    }

    override fun onInitialize(isReconnect: Boolean) {
        super.onInitialize(isReconnect)
        if (!isReconnect && !mRegistered) {
            addWatchContentUris(
                arrayOf(
                    Settings.System.getUriFor(Settings.System.NEXT_ALARM_FORMATTED).toString()
                )
            )
        }
    }

    override fun onUpdateData(reason: Int) {
        var nextAlarm = Settings.System.getString(
            contentResolver,
            Settings.System.NEXT_ALARM_FORMATTED
        )
        if (!TextUtils.isEmpty(nextAlarm)) {
            val m = sDigitPattern.matcher(nextAlarm)
            if (m.find() && m.start() > 0) {
                nextAlarm = nextAlarm.substring(0, m.start()) + "\n" +
                        nextAlarm.substring(m.start() + 1) // +1 to skip whitespace
            }
        }

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        var alarmIntent = AppChooserPreference.getIntentValue(
            sp.getString(PREF_ALARM_SHORTCUT, null), null
        )
        if (alarmIntent == null) {
            alarmIntent = Utils.getDefaultAlarmsIntent(this)
        }

        publishUpdate(
            ExtensionData()
                .visible(!TextUtils.isEmpty(nextAlarm))
                .icon(R.drawable.ic_extension_next_alarm)
                .status(nextAlarm)
                .clickIntent(alarmIntent)
        )
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(NextAlarmExtension::class.java)

        const val PREF_ALARM_SHORTCUT = "pref_alarm_shortcut"

        private val sDigitPattern = Pattern.compile("\\s[0-9]")
    }
}
