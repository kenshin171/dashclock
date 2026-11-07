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

import android.annotation.TargetApi
import android.app.Activity
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.TypedValue
import net.nurik.roman.dashclock.R

/**
 * Helper class that applies the proper icon, title and background color to recent tasks list.
 */
object RecentTasksStyler {
    private var sIcon: Bitmap? = null

    @JvmStatic
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun styleRecentTasksEntry(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        val resources = activity.resources
        val label = resources.getString(activity.applicationInfo.labelRes)

        val tv = TypedValue()
        if (!activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)) {
            return
        }

        if (sIcon == null) {
            // Cache to avoid decoding the same bitmap on every Activity change
            sIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher_white)
        }

        @Suppress("DEPRECATION")
        activity.setTaskDescription(ActivityManager.TaskDescription(label, sIcon, tv.data))
    }
}
