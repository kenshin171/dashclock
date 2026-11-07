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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.AnalogClock
import android.widget.ImageView
import android.widget.TextView
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.phone.MissedCallsExtension
import com.google.android.apps.dashclock.phone.SmsExtension
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Because every project needs a Utils class.
 */
object Utils {
    private val TAG = LogUtils.makeLogTag(Utils::class.java)

    private const val USER_AGENT = "DashClock/0.0"

    const val MILLIS_NANOS = 1000000L // 1 ms = 1,000,000 nanos
    const val SECONDS_MILLIS = 1000 // 1 second is 1000 ms
    const val MINUTES_MILLIS = 60 * SECONDS_MILLIS // 1 minute = 60 sec
    const val HOURS_MILLIS = 60 * MINUTES_MILLIS // 1 hour = 60 min

    const val EXTENSION_ICON_SIZE = 128

    private val CLOCK_PACKAGES = arrayOf(
        "com.google.android.deskclock",
        "com.android.deskclock"
    )

    private const val BRIGHTNESS_THRESHOLD = 150

    private val sPhoneOnlyExtensions = arrayOf<Class<*>>(
        SmsExtension::class.java,
        MissedCallsExtension::class.java
    )

    // TODO: Let's use a *real* HTTP library, eh?
    @JvmStatic
    @Throws(IOException::class)
    fun openUrlConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.useCaches = false
        conn.setChunkedStreamingMode(0)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connect()
        return conn
    }

    @JvmStatic
    fun isColorDark(color: Int): Boolean {
        return (30 * Color.red(color) +
            59 * Color.green(color) +
            11 * Color.blue(color)) / 100 <= BRIGHTNESS_THRESHOLD
    }

    @JvmStatic
    fun recolorBitmap(drawable: Drawable?, color: Int): Bitmap? {
        if (drawable == null) {
            return null
        }

        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBitmap)
        drawable.setBounds(0, 0, outBitmap.width, outBitmap.height)
        @Suppress("DEPRECATION")
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        drawable.draw(canvas)
        drawable.colorFilter = null
        drawable.callback = null // free up any references
        return outBitmap
    }

    @JvmStatic
    fun makeRecoloredDrawable(
        context: Context,
        drawable: BitmapDrawable,
        color: Int,
        withStates: Boolean
    ): Drawable {
        val recoloredBitmap = recolorBitmap(drawable, color)
        val recoloredDrawable = BitmapDrawable(context.resources, recoloredBitmap)

        if (!withStates) {
            return recoloredDrawable
        }

        val stateDrawable = StateListDrawable()
        stateDrawable.addState(intArrayOf(android.R.attr.state_pressed), drawable)
        stateDrawable.addState(intArrayOf(android.R.attr.state_focused), drawable)
        stateDrawable.addState(intArrayOf(), recoloredDrawable)
        return stateDrawable
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun traverseAndRecolor(
        root: View,
        color: Int,
        withStates: Boolean,
        setClickableItemBackgrounds: Boolean
    ) {
        val context = root.context

        if (setClickableItemBackgrounds && root.isClickable) {
            val selectableItemBackground = StateListDrawable()
            selectableItemBackground.addState(
                intArrayOf(android.R.attr.state_pressed),
                ColorDrawable(color and 0xffffff or 0x33000000)
            )
            selectableItemBackground.addState(
                intArrayOf(android.R.attr.state_focused),
                ColorDrawable(color and 0xffffff or 0x44000000)
            )
            selectableItemBackground.addState(intArrayOf(), null)
            root.background = selectableItemBackground
        }

        when (root) {
            is ViewGroup -> {
                for (i in 0 until root.childCount) {
                    traverseAndRecolor(
                        root.getChildAt(i),
                        color,
                        withStates,
                        setClickableItemBackgrounds
                    )
                }
            }
            is ImageView -> {
                val sourceDrawable = root.drawable
                if (withStates && sourceDrawable is BitmapDrawable) {
                    root.setImageDrawable(
                        makeRecoloredDrawable(context, sourceDrawable, color, true)
                    )
                } else {
                    root.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
                }
            }
            is TextView -> {
                if (withStates) {
                    val sourceColor = root.currentTextColor
                    val colorStateList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_pressed),
                            intArrayOf(android.R.attr.state_focused),
                            intArrayOf()
                        ),
                        intArrayOf(
                            sourceColor,
                            sourceColor,
                            color
                        )
                    )
                    root.setTextColor(colorStateList)
                } else {
                    root.setTextColor(color)
                }
            }
            is AnalogClock -> {
                try {
                    val hourHandField = AnalogClock::class.java.getDeclaredField("mHourHand")
                    hourHandField.isAccessible = true
                    val minuteHandField = AnalogClock::class.java.getDeclaredField("mMinuteHand")
                    minuteHandField.isAccessible = true
                    val dialField = AnalogClock::class.java.getDeclaredField("mDial")
                    dialField.isAccessible = true
                    val hourHand = hourHandField.get(root) as? BitmapDrawable
                    if (hourHand != null) {
                        val d = makeRecoloredDrawable(context, hourHand, color, withStates)
                        d.callback = root
                        hourHandField.set(root, d)
                    }
                    val minuteHand = minuteHandField.get(root) as? BitmapDrawable
                    if (minuteHand != null) {
                        val d = makeRecoloredDrawable(context, minuteHand, color, withStates)
                        d.callback = root
                        minuteHandField.set(root, d)
                    }
                    val dial = dialField.get(root) as? BitmapDrawable
                    if (dial != null) {
                        val d = makeRecoloredDrawable(context, dial, color, withStates)
                        d.callback = root
                        dialField.set(root, d)
                    }
                } catch (ignored: NoSuchFieldException) {
                } catch (ignored: IllegalAccessException) {
                } catch (ignored: ClassCastException) {
                } // TODO: catch all exceptions?
            }
        }
    }

    @JvmStatic
    fun flattenExtensionIcon(baseIcon: Drawable?, color: Int): Bitmap? {
        if (baseIcon == null) {
            return null
        }

        val outBitmap = Bitmap.createBitmap(
            EXTENSION_ICON_SIZE,
            EXTENSION_ICON_SIZE,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(outBitmap)
        baseIcon.setBounds(0, 0, EXTENSION_ICON_SIZE, EXTENSION_ICON_SIZE)
        @Suppress("DEPRECATION")
        baseIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        baseIcon.draw(canvas)
        baseIcon.colorFilter = null
        baseIcon.callback = null // free up any references
        return outBitmap
    }

    @JvmStatic
    fun flattenExtensionIcon(context: Context, baseIcon: Bitmap?, color: Int): Bitmap? {
        if (baseIcon == null) {
            return null
        }
        return flattenExtensionIcon(BitmapDrawable(context.resources, baseIcon), color)
    }

    @JvmStatic
    fun loadExtensionIcon(
        context: Context,
        extension: ComponentName,
        icon: Int,
        iconUri: Uri?,
        color: Int
    ): Bitmap? {
        if (iconUri != null) {
            return loadExtensionIconFromUri(context, iconUri)
        }

        if (icon <= 0) {
            return null
        }

        val packageName = extension.packageName
        try {
            val packageContext = context.createPackageContext(packageName, 0)
            val packageRes = packageContext.resources

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeResource(packageRes, icon, options)

            // Cut down the icon to a smaller size.
            var sampleSize = 1
            while (true) {
                if (options.outHeight / (sampleSize * 2) > EXTENSION_ICON_SIZE / 2) {
                    sampleSize *= 2
                } else {
                    break
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize

            return flattenExtensionIcon(
                context,
                BitmapFactory.decodeResource(packageRes, icon, options),
                color
            )
        } catch (e: PackageManager.NameNotFoundException) {
            LOGE(TAG, "Couldn't access extension's package while loading icon data.")
        }

        return null
    }

    @JvmStatic
    fun loadExtensionIconFromUri(context: Context, iconUri: Uri): Bitmap? {
        try {
            val pfd = context.contentResolver.openFileDescriptor(iconUri, "r") ?: return null

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options)

            // Cut down the icon to a smaller size.
            var sampleSize = 1
            while (true) {
                if (options.outHeight / (sampleSize * 2) > EXTENSION_ICON_SIZE / 2) {
                    sampleSize *= 2
                } else {
                    break
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize

            return flattenExtensionIcon(
                context,
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, options),
                0xffffffff.toInt()
            )
        } catch (e: IOException) {
            LOGE(TAG, "Couldn't read icon from content URI.", e)
        } catch (e: SecurityException) {
            LOGE(TAG, "Couldn't read icon from content URI.", e)
        }

        return null
    }

    @JvmStatic
    fun getDefaultClockIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (packageName in CLOCK_PACKAGES) {
            try {
                pm.getPackageInfo(packageName, 0)
                return pm.getLaunchIntentForPackage(packageName)
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return null
    }

    @JvmStatic
    fun getDefaultAlarmsIntent(context: Context): Intent? {
        // TODO: consider using AlarmClock.ACTION_SET_ALARM, although it requires a permission
        val pm = context.packageManager
        for (packageName in CLOCK_PACKAGES) {
            try {
                val cn = ComponentName(packageName, "com.android.deskclock.AlarmClock")
                pm.getActivityInfo(cn, 0)
                return Intent.makeMainActivity(cn)
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }
        return getDefaultClockIntent(context)
    }

    @JvmStatic
    fun expandedTitleOrStatus(data: ExtensionData): String? {
        var expandedTitle = data.expandedTitle()
        if (TextUtils.isEmpty(expandedTitle)) {
            expandedTitle = data.status()
            if (!TextUtils.isEmpty(expandedTitle)) {
                expandedTitle = expandedTitle!!.replace("\n", " ")
            }
        }
        return expandedTitle
    }

    @JvmStatic
    fun enableDisablePhoneOnlyExtensions(context: Context) {
        val pm = context.packageManager
        val hasTelephony = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

        for (ext in sPhoneOnlyExtensions) {
            pm.setComponentEnabledSetting(
                ComponentName(context, ext),
                if (hasTelephony) {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
