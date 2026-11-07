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

package com.google.android.apps.dashclock.configuration

import android.content.Context
import android.graphics.Color
import android.preference.PreferenceManager
import com.google.android.apps.dashclock.render.DashClockRenderer

/**
 * Helper class for working with DashClock appearance settings.
 */
class AppearanceConfig {
    companion object {
        const val COMPONENT_TIME = "time"
        const val COMPONENT_DATE = "date"

        const val PREF_STYLE_TIME = "pref_style_time"
        const val PREF_STYLE_DATE = "pref_style_date"

        const val PREF_HIDE_SETTINGS = "pref_hide_settings" // deprecated
        const val PREF_SETTINGS_BUTTON = "pref_settings_button"
        const val PREF_AGGRESSIVE_CENTERING = "pref_aggressive_centering"

        const val PREF_SETTINGS_BUTTON_HIDDEN = "hidden"
        const val PREF_SETTINGS_BUTTON_IN_WIDGET = "inwidget"
        const val PREF_SETTINGS_BUTTON_IN_LAUNCHER = "inlauncher"

        const val PREF_HOMESCREEN_FOREGROUND_COLOR = "pref_homescreen_foreground_color"
        const val PREF_HOMESCREEN_BACKGROUND_OPACITY = "pref_homescreen_background_opacity"
        const val PREF_HOMESCREEN_HIDE_CLOCK = "pref_homescreen_hide_clock"

        const val PREF_LOCKSCREEN_FOREGROUND_COLOR = "pref_lockscreen_foreground_color"
        const val PREF_LOCKSCREEN_BACKGROUND_OPACITY = "pref_lockscreen_background_opacity"
        const val PREF_LOCKSCREEN_HIDE_CLOCK = "pref_lockscreen_hide_clock"

        const val DEFAULT_WIDGET_FOREGROUND_COLOR = Color.WHITE

        @JvmField
        var TIME_STYLE_NAMES = arrayOf(
            "default",
            "light",
            "alpha",
            "stock",
            "condensed",
            "big_small",
            "analog1",
            "analog2"
        )

        @JvmField
        var DATE_STYLE_NAMES = arrayOf(
            "default",
            "simple",
            "condensed_bold"
        )

        @JvmStatic
        fun getCurrentTimeLayout(context: Context, foregroundColor: Int): Int {
            var currentTimeStyleName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_STYLE_TIME, TIME_STYLE_NAMES[0]) ?: TIME_STYLE_NAMES[0]
            if (currentTimeStyleName.contains("analog")) {
                currentTimeStyleName += if (foregroundColor == Color.BLACK) {
                    "_black"
                } else {
                    "_white"
                }
            }
            return getLayoutByStyleName(context, COMPONENT_TIME, currentTimeStyleName)
        }

        @JvmStatic
        fun getCurrentDateLayout(context: Context): Int {
            val currentDateStyleName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_STYLE_DATE, DATE_STYLE_NAMES[0]) ?: DATE_STYLE_NAMES[0]
            return getLayoutByStyleName(context, COMPONENT_DATE, currentDateStyleName)
        }

        @JvmStatic
        fun getLayoutByStyleName(context: Context, component: String, name: String): Int {
            return context.resources.getIdentifier(
                "widget_include_${component}_style_$name",
                "layout", context.packageName
            )
        }

        @JvmStatic
        fun isSettingsButtonHidden(context: Context): Boolean {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val pref = sp.getString(PREF_SETTINGS_BUTTON, null)
                ?: // Check older preference
                return sp.getBoolean(PREF_HIDE_SETTINGS, false)

            return PREF_SETTINGS_BUTTON_IN_WIDGET != pref
        }

        @JvmStatic
        fun shouldLauncherSettingsBeShown(context: Context): Boolean {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val pref = sp.getString(PREF_SETTINGS_BUTTON, null)
            return PREF_SETTINGS_BUTTON_IN_LAUNCHER == pref
        }

        @JvmStatic
        fun isClockHiddenOnHomeScreen(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_HOMESCREEN_HIDE_CLOCK, false)
        }

        @JvmStatic
        fun isAggressiveCenteringEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_AGGRESSIVE_CENTERING, true)
        }

        @JvmStatic
        fun isClockHiddenOnLockScreen(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_LOCKSCREEN_HIDE_CLOCK, false)
        }

        @JvmStatic
        fun getForegroundColor(context: Context, target: Int): Int {
            if (target == DashClockRenderer.Options.TARGET_HOME_SCREEN) {
                return PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PREF_HOMESCREEN_FOREGROUND_COLOR, Color.WHITE)
            } else if (target == DashClockRenderer.Options.TARGET_LOCK_SCREEN) {
                return PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(PREF_LOCKSCREEN_FOREGROUND_COLOR, Color.WHITE)
            }
            return DEFAULT_WIDGET_FOREGROUND_COLOR
        }

        @JvmStatic
        fun getBackgroundColor(context: Context, target: Int): Int {
            val foregroundColor = getForegroundColor(context, target)
            var opacity = 0
            try {
                if (target == DashClockRenderer.Options.TARGET_HOME_SCREEN) {
                    opacity = Integer.parseInt(
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(PREF_HOMESCREEN_BACKGROUND_OPACITY, "50") ?: "50"
                    )
                } else if (target == DashClockRenderer.Options.TARGET_LOCK_SCREEN) {
                    opacity = Integer.parseInt(
                        PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(PREF_LOCKSCREEN_BACKGROUND_OPACITY, "0") ?: "0"
                    )
                }
            } catch (ignored: NumberFormatException) {
            }

            val backgroundColor = if (foregroundColor == Color.WHITE) Color.BLACK else Color.WHITE
            return (backgroundColor and 0xffffff) or ((opacity * 255 / 100) shl 24)
        }
    }
}
