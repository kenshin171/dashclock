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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import com.google.android.apps.dashclock.LogUtils.LOGD
import net.nurik.roman.dashclock.BuildConfig

/**
 * The DashClock [AppWidgetProvider], which just forwards commands to [DashClockService].
 */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        // Update extensions and ensure the periodic refresh is set up.
        PeriodicExtensionRefreshReceiver.updateExtensionsAndEnsurePeriodicRefresh(context)

        // Update widgets
        for (appWidgetId in appWidgetIds) {
            val widgetUpdateIntent = Intent(context, DashClockService::class.java)
            widgetUpdateIntent.action = DashClockService.ACTION_UPDATE_WIDGETS
            widgetUpdateIntent.putExtra(DashClockService.EXTRA_APPWIDGET_ID, appWidgetId)
            context.startService(widgetUpdateIntent)
        }

        val sb = StringBuilder()
        for (appWidgetId in appWidgetIds) {
            sb.append(appWidgetId).append(" ")
        }
        LOGD(TAG, "onUpdate for appWidgetId(s): $sb")
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)

        LOGD(TAG, "onAppWidgetOptionsChanged for appWidgetId: $appWidgetId")

        val widgetUpdateIntent = Intent(context, DashClockService::class.java)
        widgetUpdateIntent.action = DashClockService.ACTION_UPDATE_WIDGETS
        widgetUpdateIntent.putExtra(DashClockService.EXTRA_APPWIDGET_ID, appWidgetId)
        context.startService(widgetUpdateIntent)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        LOGD(TAG, "onDisabled; stopping DashClockService.")
        context.stopService(Intent(context, DashClockService::class.java))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val remainingIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, WidgetProvider::class.java)
        )
        if (remainingIds == null || remainingIds.isEmpty()) {
            LOGD(TAG, "Widget deleted, none remaining; stopping DashClockService.")
            context.stopService(Intent(context, DashClockService::class.java))
        } else {
            LOGD(TAG, "Widget deleted, ${remainingIds.size} remaining.")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.DEBUG) {
            if ("demomode" == intent.action) {
                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putBoolean("demomode", intent.getBooleanExtra("on", false))
                    .commit()
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(WidgetProvider::class.java)
    }
}
