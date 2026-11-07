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

package com.google.android.apps.dashclock.weather

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.legacy.content.WakefulBroadcastReceiver
import com.google.android.apps.dashclock.DashClockService
import com.google.android.apps.dashclock.api.DashClockExtension

/**
 * Broadcast receiver that's a target of the retry-update alarm from [WeatherExtension].
 */
class WeatherRetryReceiver : WakefulBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Update weather
        // TODO: there should be a way for an extension to request an update through the API
        // See issue 292.
        startWakefulService(
            context,
            Intent(context, DashClockService::class.java)
                .setAction(DashClockService.ACTION_UPDATE_EXTENSIONS)
                .putExtra(
                    DashClockService.EXTRA_UPDATE_REASON,
                    DashClockExtension.UPDATE_REASON_UNKNOWN // TODO: UPDATE_REASON_RETRY
                )
                .putExtra(
                    DashClockService.EXTRA_COMPONENT_NAME,
                    ComponentName(context, WeatherExtension::class.java).flattenToShortString()
                )
        )
    }

    companion object {
        @JvmStatic
        fun getPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, WeatherRetryReceiver::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
