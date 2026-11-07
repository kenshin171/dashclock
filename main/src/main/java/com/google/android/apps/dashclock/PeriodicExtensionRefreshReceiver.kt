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

package com.google.android.apps.dashclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.legacy.content.WakefulBroadcastReceiver
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.Utils.MINUTES_MILLIS
import com.google.android.apps.dashclock.api.DashClockExtension

/**
 * A broadcast receiver in charge of scheduling DashClock extension refreshes. This was
 * originally handled by updatePeriodMillis but custom refresh behavior and a separation of
 * extension refreshes from widget refreshes was desirable so was moved here.
 */
@Suppress("DEPRECATION")
class PeriodicExtensionRefreshReceiver : WakefulBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || ACTION_PERIODIC_ALARM != intent.action) {
            return
        }

        // Periodic alarm has triggered. Update all extensions.
        startWakefulService(
            context,
            getUpdateAllExtensionsIntent(context, DashClockExtension.UPDATE_REASON_PERIODIC)
        )
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(PeriodicExtensionRefreshReceiver::class.java)

        private const val ACTION_PERIODIC_ALARM =
            "com.google.android.apps.dashclock.action.PERIODIC_ALARM"

        private fun getUpdateAllExtensionsIntent(context: Context, reason: Int): Intent {
            LOGD(TAG, "getUpdateAllExtensionsIntent, reason=$reason")
            return Intent(context, DashClockService::class.java)
                .setAction(DashClockService.ACTION_UPDATE_EXTENSIONS)
                .putExtra(DashClockService.EXTRA_UPDATE_REASON, reason)
        }

        /**
         * Sets the refresh schedule if one isn't set already.
         */
        @JvmStatic
        fun updateExtensionsAndEnsurePeriodicRefresh(context: Context) {
            LOGD(TAG, "updateExtensionsAndEnsurePeriodicRefresh")
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Update all extensions now.
            context.startService(
                getUpdateAllExtensionsIntent(
                    context,
                    DashClockExtension.UPDATE_REASON_MANUAL
                )
            )

            // Schedule an alarm for every 30 minutes; it will not wake up the device;
            // it will be handled only once the device is awake. The first time that this
            // alarm can go off is in 15 minutes, and the latest time it will go off is
            // 45 minutes from now.
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PeriodicExtensionRefreshReceiver::class.java)
                    .setAction(ACTION_PERIODIC_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 15 * MINUTES_MILLIS,
                AlarmManager.INTERVAL_HALF_HOUR,
                pi
            )
        }

        /**
         * Cancels the refresh schedule if one isn't set already.
         */
        @JvmStatic
        fun cancelPeriodicRefresh(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, PeriodicExtensionRefreshReceiver::class.java)
                    .setAction(ACTION_PERIODIC_ALARM),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)
        }
    }
}
