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

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import androidx.legacy.content.WakefulBroadcastReceiver
import com.google.android.apps.dashclock.LogUtils.LOGD

/**
 * Broadcast receiver used to watch for changes to installed packages on the device. This triggers
 * a cleanup of extensions (in case one was uninstalled), or a data update request to an extension
 * if it was updated (its package was replaced).
 */
@Suppress("DEPRECATION")
class ExtensionPackageChangeReceiver : WakefulBroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extensionManager = ExtensionManager.getInstance(context)
        if (extensionManager.cleanupExtensions()) {
            LOGD(TAG, "Extension cleanup performed and action taken.")

            val widgetUpdateIntent = Intent(context, DashClockService::class.java)
            widgetUpdateIntent.action = DashClockService.ACTION_UPDATE_WIDGETS
            startWakefulService(context, widgetUpdateIntent)
        }

        // If this is a replacement or change in the package, update all active extensions from
        // this package.
        val action = intent.action
        if (Intent.ACTION_PACKAGE_CHANGED == action ||
            Intent.ACTION_PACKAGE_REPLACED == action
        ) {
            val packageName = intent.data?.schemeSpecificPart
            if (TextUtils.isEmpty(packageName)) {
                return
            }

            val activeExtensions = extensionManager.getActiveExtensionNames()
            for (cn in activeExtensions) {
                if (packageName == cn.packageName) {
                    LOGD(TAG, "Package for extension $cn changed; asking it for an update.")
                    val extensionUpdateIntent = Intent(context, DashClockService::class.java)
                    extensionUpdateIntent.action = DashClockService.ACTION_UPDATE_EXTENSIONS
                    // TODO: UPDATE_REASON_PACKAGE_CHANGED
                    extensionUpdateIntent.putExtra(
                        DashClockService.EXTRA_COMPONENT_NAME,
                        cn.flattenToShortString()
                    )
                    startWakefulService(context, extensionUpdateIntent)
                }
            }
        }
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(ExtensionPackageChangeReceiver::class.java)
    }
}
