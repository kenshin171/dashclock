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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.api.DashClockExtension
import java.net.URISyntaxException

/**
 * A basic proxy activity for handling widget clicks.
 */
class WidgetClickProxyActivity : Activity() {
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            if (intent.hasExtra(EXTRA_UPDATE_EXTENSION)) {
                // Update extensions
                val extensionUpdateIntent = Intent(this, DashClockService::class.java)
                extensionUpdateIntent.action = DashClockService.ACTION_UPDATE_EXTENSIONS
                extensionUpdateIntent.putExtra(
                    DashClockService.EXTRA_COMPONENT_NAME,
                    intent.getStringExtra(EXTRA_UPDATE_EXTENSION)
                )
                extensionUpdateIntent.putExtra(
                    DashClockService.EXTRA_UPDATE_REASON,
                    DashClockExtension.UPDATE_REASON_MANUAL
                )
                startService(extensionUpdateIntent)
            }

            try {
                startActivity(
                    Intent.parseUri(intent.data.toString(), Intent.URI_INTENT_SCHEME)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_TASK_ON_HOME or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_FORWARD_RESULT
                        )
                )
            } catch (e: URISyntaxException) {
                LOGE(TAG, "Error parsing URI.", e)
            } catch (e: ActivityNotFoundException) {
                LOGE(TAG, "Proxy'd activity not found.", e)
            } catch (e: SecurityException) {
                LOGE(TAG, "Don't have permission to launch proxy'd activity.", e)
            }
            finish()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(WidgetClickProxyActivity::class.java)

        /**
         * If present, the component name of the extension to update upon being clicked.
         */
        private const val EXTRA_UPDATE_EXTENSION =
            "com.google.android.apps.dashclock.extra.UPDATE_EXTENSION"

        @JvmStatic
        @JvmOverloads
        fun wrap(
            context: Context,
            intent: Intent,
            alsoUpdateExtension: ComponentName? = null
        ): Intent {
            return Intent(context, WidgetClickProxyActivity::class.java)
                .setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)))
                .putExtra(EXTRA_UPDATE_EXTENSION, alsoUpdateExtension?.flattenToString())
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
        }

        @JvmStatic
        fun getTemplate(context: Context): Intent {
            return Intent(context, WidgetClickProxyActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
        }

        @JvmStatic
        @JvmOverloads
        fun getFillIntent(
            clickIntent: Intent,
            alsoUpdateExtension: ComponentName? = null
        ): Intent {
            return Intent()
                .setData(Uri.parse(clickIntent.toUri(Intent.URI_INTENT_SCHEME)))
                .putExtra(EXTRA_UPDATE_EXTENSION, alsoUpdateExtension?.flattenToString())
        }
    }
}
