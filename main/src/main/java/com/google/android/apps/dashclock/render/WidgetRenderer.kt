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

package com.google.android.apps.dashclock.render

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.google.android.apps.dashclock.WidgetProvider
import com.google.android.apps.dashclock.configuration.AppearanceConfig
import net.nurik.roman.dashclock.R

/**
 * Class in charge of rendering DashClock to [android.widget.RemoteViews],
 * along with [WidgetRemoteViewsFactoryService].
 */
open class WidgetRenderer internal constructor(context: Context) : DashClockRenderer(context) {
    override fun onCreateViewBuilder(): ViewBuilder {
        return WidgetViewBuilder(mContext)
    }

    override fun builderSetExpandedExtensionsAdapter(
        vb: ViewBuilder,
        viewId: Int,
        mini: Boolean,
        clickTemplateIntent: Intent
    ) {
        val remoteAdapterIntent = Intent(mContext, WidgetRemoteViewsFactoryService::class.java)
        if (mOptions.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            remoteAdapterIntent.putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                mOptions.appWidgetId
            )
            remoteAdapterIntent.putExtra(
                WidgetRemoteViewsFactoryService.EXTRA_TARGET,
                mOptions.target
            )
            remoteAdapterIntent.putExtra(
                WidgetRemoteViewsFactoryService.EXTRA_IS_MINI,
                mini
            )
        }

        // TODO: is this setData call really necessary?
        remoteAdapterIntent.data = Uri.parse(remoteAdapterIntent.toUri(Intent.URI_INTENT_SCHEME))
        val root = vb.getRoot() as RemoteViews
        root.setRemoteAdapter(viewId, remoteAdapterIntent)
        root.setPendingIntentTemplate(
            viewId,
            PendingIntent.getActivity(
                mContext,
                0,
                clickTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )
    }

    companion object {
        /**
         * Renders the DashClock UI to the given app widget IDs.
         */
        @JvmStatic
        fun renderWidgets(context: Context, appWidgetIds: IntArray) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val renderer = WidgetRenderer(context)
            for (appWidgetId in appWidgetIds) {
                val options = Options()
                options.appWidgetId = appWidgetId
                options.target = Options.TARGET_HOME_SCREEN
                options.minWidthDp = Int.MAX_VALUE
                options.minHeightDp = Int.MAX_VALUE
                val widgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
                if (widgetOptions != null) {
                    options.minWidthDp = widgetOptions
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    options.minHeightDp = widgetOptions
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    options.target = if (AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD ==
                        widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY)
                    ) {
                        Options.TARGET_LOCK_SCREEN
                    } else {
                        Options.TARGET_HOME_SCREEN
                    }
                }
                options.foregroundColor = AppearanceConfig.getForegroundColor(context, options.target)

                renderer.setOptions(options)
                appWidgetManager.updateAppWidget(
                    appWidgetId,
                    renderer.renderWidget(null) as RemoteViews
                )

                // During an update to an existing expanded widget, setRemoteAdapter does nothing,
                // so we need to explicitly call notifyAppWidgetViewDataChanged to update data.
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.expanded_extensions
                )
            }
        }

        @JvmStatic
        fun notifyDataSetChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, WidgetProvider::class.java)
            )
            appWidgetManager.notifyAppWidgetViewDataChanged(
                appWidgetIds,
                R.id.expanded_extensions
            )
        }
    }
}
