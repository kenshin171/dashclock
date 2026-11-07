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

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.google.android.apps.dashclock.ExtensionManager
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.configuration.AppearanceConfig
import net.nurik.roman.dashclock.R

/**
 * This is the service that provides the factory to be bound to the collection. Basically the
 * [android.widget.Adapter] for expanded DashClock extensions.
 */
class WidgetRemoteViewsFactoryService : RemoteViewsService() {
    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        LOGD(TAG, "Instantiating a remote views factory.")
        val target = intent.getIntExtra(EXTRA_TARGET, DashClockRenderer.Options.TARGET_HOME_SCREEN)
        val isMini = intent.getBooleanExtra(EXTRA_IS_MINI, false)
        return WidgetRemoveViewsFactory(this, target, isMini)
    }

    /**
     * This is the factory that will provide data to the collection widget. Behaves pretty much like
     * an [android.widget.Adapter].
     */
    internal inner class WidgetRemoveViewsFactory(
        private val mContext: Context,
        private val mTarget: Int,
        private val mIsMini: Boolean
    ) : RemoteViewsService.RemoteViewsFactory {
        private val mExtensionManager: ExtensionManager = ExtensionManager.getInstance(mContext)
        private var mVisibleExtensions: List<ExtensionManager.ExtensionWithData> = ArrayList()

        override fun onCreate() {
        }

        override fun onDestroy() {
        }

        override fun onDataSetChanged() {
            mVisibleExtensions = mExtensionManager.getVisibleExtensionsWithData()
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            val ewd = getItemAtProtected(position)
            return ewd?.listing?.componentName()?.hashCode()?.toLong() ?: 0L
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        override fun getCount(): Int {
            return mVisibleExtensions.size
        }

        private fun getItemAtProtected(position: Int): ExtensionManager.ExtensionWithData? {
            return if (position < mVisibleExtensions.size) mVisibleExtensions[position] else null
        }

        override fun getViewAt(position: Int): RemoteViews? {
            if (position >= mVisibleExtensions.size) {
                // TODO: trap this better
                // See note on synchronization below.
                return null
            }

            val renderer = WidgetRenderer(mContext)
            val options = DashClockRenderer.Options()
            options.target = mTarget
            options.foregroundColor = AppearanceConfig.getForegroundColor(mContext, mTarget)
            renderer.setOptions(options)
            val ewd = getItemAtProtected(position) ?: return null
            return (if (mIsMini) {
                renderer.renderCollapsedExtension(null, null, true, ewd)
            } else {
                renderer.renderExpandedExtension(null, null, true, ewd)
            }) as? RemoteViews
        }

        override fun getLoadingView(): RemoteViews {
            return RemoteViews(
                mContext.packageName,
                R.layout.widget_list_item_expanded_extension_loading
            )
        }
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(WidgetRemoteViewsFactoryService::class.java)

        @JvmField
        var EXTRA_TARGET: String = "com.google.android.apps.dashclock.extra.TARGET"

        @JvmField
        var EXTRA_IS_MINI: String = "com.google.android.apps.dashclock.extra.IS_MINI"
    }
}
