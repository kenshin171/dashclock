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

package com.google.android.apps.dashclock.phone

import android.content.Intent
import android.database.Cursor
import android.provider.CallLog
import android.text.TextUtils
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import net.nurik.roman.dashclock.R
import java.util.SortedSet
import java.util.TreeSet

/**
 * Number of missed calls extension.
 */
class MissedCallsExtension : DashClockExtension() {
    override fun onInitialize(isReconnect: Boolean) {
        super.onInitialize(isReconnect)
        if (!isReconnect) {
            addWatchContentUris(
                arrayOf(
                    CallLog.Calls.CONTENT_URI.toString()
                )
            )
        }
    }

    override fun onUpdateData(reason: Int) {
        val cursor = tryOpenMissedCallsCursor()
        if (cursor == null) {
            LOGE(TAG, "Null missed calls cursor, short-circuiting.")
            return
        }

        var missedCalls = 0
        val names: SortedSet<String> = TreeSet()
        while (cursor.moveToNext()) {
            ++missedCalls
            var name = cursor.getString(MissedCallsQuery.CACHED_NAME)
            if (TextUtils.isEmpty(name)) {
                name = cursor.getString(MissedCallsQuery.NUMBER)
                var parsedNumber: Long = 0
                try {
                    parsedNumber = name.toLong()
                } catch (ignored: Exception) {
                }
                if (parsedNumber < 0) {
                    // Unknown or private number
                    name = getString(R.string.missed_calls_unknown)
                }
            }
            if (name != null) {
                names.add(name)
            }
        }
        cursor.close()

        publishUpdate(
            ExtensionData()
                .visible(missedCalls > 0)
                .icon(R.drawable.ic_extension_missed_calls)
                .status(missedCalls.toString())
                .expandedTitle(
                    resources.getQuantityString(
                        R.plurals.missed_calls_title_template, missedCalls, missedCalls
                    )
                )
                .expandedBody(
                    getString(
                        R.string.missed_calls_body_template,
                        TextUtils.join(", ", names)
                    )
                )
                .clickIntent(Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI))
        )
    }

    private fun tryOpenMissedCallsCursor(): Cursor? {
        return try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                MissedCallsQuery.PROJECTION,
                "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}!=0",
                null,
                null
            )
        } catch (e: Exception) {
            LOGE(TAG, "Error opening missed calls cursor", e)
            null
        }
    }

    private object MissedCallsQuery {
        val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER
        )

        const val ID = 0
        const val CACHED_NAME = 1
        const val NUMBER = 2
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(MissedCallsExtension::class.java)
    }
}
