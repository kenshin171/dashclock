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

package com.google.android.apps.dashclock.weather

import net.nurik.roman.dashclock.R

/**
 * A helper class representing weather data, for use with [WeatherExtension].
 */
class WeatherData {
    @JvmField
    var temperature: Int = INVALID_TEMPERATURE
    @JvmField
    var low: Int = INVALID_TEMPERATURE
    @JvmField
    var high: Int = INVALID_TEMPERATURE
    @JvmField
    var conditionCode: Int = INVALID_CONDITION
    @JvmField
    var todayForecastConditionCode: Int = INVALID_CONDITION
    @JvmField
    var conditionText: String? = null
    @JvmField
    var forecastText: String? = null
    @JvmField
    var location: String? = null

    companion object {
        const val INVALID_TEMPERATURE: Int = Int.MIN_VALUE
        const val INVALID_CONDITION: Int = -1

        @JvmStatic
        fun getConditionIconId(conditionCode: Int): Int {
            // http://developer.yahoo.com/weather/
            return when (conditionCode) {
                // dust or sand, foggy, haze, smoky
                19, 20, 21, 22 -> R.drawable.ic_weather_foggy
                // blustery, windy
                23, 24 -> R.drawable.ic_weather_windy
                // cold, cloudy, mostly cloudy (night), mostly cloudy (day)
                25, 26, 27, 28 -> R.drawable.ic_weather_cloudy
                // partly cloudy (night), partly cloudy (day), partly cloudy
                29, 30, 44 -> R.drawable.ic_weather_partly_cloudy
                // clear (night), fair (night), fair (day)
                31, 33, 34 -> R.drawable.ic_weather_clear
                // sunny, hot
                32, 36 -> R.drawable.ic_weather_sunny
                // tornado, tropical storm, hurricane, severe thunderstorms, thunderstorms,
                // mixed rain and snow, mixed rain and sleet, mixed snow and sleet,
                // freezing drizzle, drizzle, freezing rain, showers, hail, sleet,
                // mixed rain and hail, isolated thunderstorms, scattered thunderstorms,
                // scattered showers, thundershowers, isolated thundershowers
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 17, 18, 35, 37, 38, 39, 40, 45, 47 ->
                    R.drawable.ic_weather_raining
                // snow flurries, light snow showers, blowing snow, snow, heavy snow,
                // scattered snow showers, snow showers
                13, 14, 15, 16, 41, 42, 43, 46 -> R.drawable.ic_weather_snow
                else -> R.drawable.ic_weather_clear
            }
        }
    }
}
