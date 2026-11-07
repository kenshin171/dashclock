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

package com.google.android.apps.dashclock

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.google.android.apps.dashclock.configuration.AppearanceConfig
import com.google.android.apps.dashclock.configuration.ConfigurationActivity

/**
 * A private broadcast receiver that enables/disables "multiplexer-only" mode, which disables
 * most of DashClock's UI surface, leaving it to only function as a carrier of data between
 * extensions and other host apps that want to show data from those extensions.
 */
class MultiplexerOnlyModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || ACTION_MULTIPLEXER_ONLY_MODE != intent.action) {
            return
        }

        val multiplexerOnlyMode = intent.getBooleanExtra(EXTRA_ENABLE, false)
        enableDisableMultiplexerOnlyMode(context, multiplexerOnlyMode)
    }

    private fun enableDisableMultiplexerOnlyMode(context: Context, multiplexerOnlyMode: Boolean) {
        val components = arrayOf(
            ComponentName(context, DaydreamService::class.java),
            ComponentName(context, ConfigurationActivity::class.java),
            ComponentName(context, WidgetProvider::class.java)
        )

        val pm = context.packageManager
        for (componentName in components) {
            pm.setComponentEnabledSetting(
                componentName,
                if (multiplexerOnlyMode) {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                },
                PackageManager.DONT_KILL_APP
            )
        }

        pm.setComponentEnabledSetting(
            ComponentName(
                context.packageName,
                ConfigurationActivity.LAUNCHER_ACTIVITY_NAME
            ),
            if (!multiplexerOnlyMode && AppearanceConfig.shouldLauncherSettingsBeShown(context)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }

    companion object {
        private const val ACTION_MULTIPLEXER_ONLY_MODE =
            "com.google.android.apps.dashclock.action.MULTIPLEXER_ONLY_MODE"
        private const val EXTRA_ENABLE =
            "com.google.android.apps.dashclock.extra.ENABLE"
    }
}
