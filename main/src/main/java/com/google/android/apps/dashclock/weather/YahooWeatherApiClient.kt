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

import android.location.Location
import android.text.TextUtils
import android.util.Pair
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.LogUtils.LOGW
import com.google.android.apps.dashclock.Utils
import net.nurik.roman.dashclock.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection

/**
 * Client code for the Yahoo! Weather RSS feeds and GeoPlanet API.
 */
object YahooWeatherApiClient {
    private val TAG = LogUtils.makeLogTag(YahooWeatherApiClient::class.java)

    private var sWeatherUnits: String? = null

    private var sXmlPullParserFactory: XmlPullParserFactory? = null

    private const val MAX_SEARCH_RESULTS = 10

    init {
        try {
            sXmlPullParserFactory = XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
            }
        } catch (e: XmlPullParserException) {
            LOGE(TAG, "Could not instantiate XmlPullParserFactory", e)
        }
    }

    @JvmStatic
    fun setWeatherUnits(weatherUnits: String?) {
        sWeatherUnits = weatherUnits
    }

    @JvmStatic
    @Throws(CantGetWeatherException::class)
    fun getWeatherForLocationInfo(locationInfo: LocationInfo): WeatherData {
        // Loop through the woeids (they're in descending precision order) until weather data
        // is found.
        for (woeid in locationInfo.woeids) {
            LOGD(TAG, "Trying WOEID: $woeid")
            val data = getWeatherForWoeid(woeid, locationInfo.town)
            if (data != null
                && data.conditionCode != WeatherData.INVALID_CONDITION
                && data.temperature != WeatherData.INVALID_TEMPERATURE
            ) {
                return data
            }
        }

        // No weather could be found :(
        throw CantGetWeatherException(true, R.string.no_weather_data)
    }

    @JvmStatic
    @Throws(CantGetWeatherException::class)
    fun getWeatherForWoeid(woeid: String?, town: String?): WeatherData? {
        var connection: HttpURLConnection? = null
        try {
            // TODO: proper http library
            connection = Utils.openUrlConnection(buildWeatherQueryUrl(woeid))
            val xpp = sXmlPullParserFactory!!.newPullParser()
            xpp.setInput(InputStreamReader(connection.inputStream))

            val data = WeatherData()
            var hasTodayForecast = false
            var eventType = xpp.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "condition" == xpp.name) {
                    for (i in xpp.attributeCount - 1 downTo 0) {
                        when (xpp.getAttributeName(i)) {
                            "temp" -> data.temperature = xpp.getAttributeValue(i).toInt()
                            "code" -> data.conditionCode = xpp.getAttributeValue(i).toInt()
                            "text" -> data.conditionText = xpp.getAttributeValue(i)
                        }
                    }
                } else if (eventType == XmlPullParser.START_TAG
                    && "forecast" == xpp.name
                    && !hasTodayForecast
                ) {
                    // TODO: verify this is the forecast for today (this currently assumes the
                    // first forecast is today's forecast)
                    hasTodayForecast = true
                    for (i in xpp.attributeCount - 1 downTo 0) {
                        when (xpp.getAttributeName(i)) {
                            "code" -> data.todayForecastConditionCode = xpp.getAttributeValue(i).toInt()
                            "low" -> data.low = xpp.getAttributeValue(i).toInt()
                            "high" -> data.high = xpp.getAttributeValue(i).toInt()
                            "text" -> data.forecastText = xpp.getAttributeValue(i)
                        }
                    }
                } else if (eventType == XmlPullParser.START_TAG && "location" == xpp.name) {
                    var cityOrVillage = "--"
                    var region: String? = null
                    var country = "--"
                    for (i in xpp.attributeCount - 1 downTo 0) {
                        when (xpp.getAttributeName(i)) {
                            "city" -> cityOrVillage = xpp.getAttributeValue(i)
                            "region" -> region = xpp.getAttributeValue(i)
                            "country" -> country = xpp.getAttributeValue(i)
                        }
                    }

                    if (TextUtils.isEmpty(region)) {
                        // If no region is available, show the country. Otherwise, don't
                        // show country information.
                        region = country
                    }

                    if (!TextUtils.isEmpty(town) && town != cityOrVillage) {
                        // If a town is available and it's not equivalent to the city name,
                        // show it.
                        cityOrVillage = "$cityOrVillage, $town"
                    }

                    data.location = "$cityOrVillage, $region"
                }
                eventType = xpp.next()
            }

            if (TextUtils.isEmpty(data.location)) {
                data.location = town
            }

            return data
        } catch (e: IOException) {
            throw CantGetWeatherException(
                true,
                R.string.no_weather_data,
                "Error parsing weather feed XML.",
                e
            )
        } catch (e: NumberFormatException) {
            throw CantGetWeatherException(
                true,
                R.string.no_weather_data,
                "Error parsing weather feed XML.",
                e
            )
        } catch (e: XmlPullParserException) {
            throw CantGetWeatherException(
                true,
                R.string.no_weather_data,
                "Error parsing weather feed XML.",
                e
            )
        } finally {
            connection?.disconnect()
        }
    }

    @JvmStatic
    @Throws(CantGetWeatherException::class)
    fun getLocationInfo(location: Location): LocationInfo {
        val li = LocationInfo()

        // first=tagname (admin1, locality3) second=woeid
        var primaryWoeid: String? = null
        val alternateWoeids = ArrayList<Pair<String, String>>()

        var connection: HttpURLConnection? = null
        try {
            connection = Utils.openUrlConnection(buildPlaceSearchUrl(location))
            val xpp = sXmlPullParserFactory!!.newPullParser()
            xpp.setInput(InputStreamReader(connection.inputStream))

            var inWoe = false
            var inTown = false
            var eventType = xpp.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = xpp.name

                if (eventType == XmlPullParser.START_TAG && "woeid" == tagName) {
                    inWoe = true
                } else if (eventType == XmlPullParser.TEXT && inWoe) {
                    primaryWoeid = xpp.text
                }

                if (eventType == XmlPullParser.START_TAG
                    && tagName != null
                    && (tagName.startsWith("locality") || tagName.startsWith("admin"))
                ) {
                    for (i in xpp.attributeCount - 1 downTo 0) {
                        val attrName = xpp.getAttributeName(i)
                        if ("type" == attrName && "Town" == xpp.getAttributeValue(i)) {
                            inTown = true
                        } else if ("woeid" == attrName) {
                            val woeid = xpp.getAttributeValue(i)
                            if (!TextUtils.isEmpty(woeid)) {
                                alternateWoeids.add(Pair(tagName, woeid))
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT && inTown) {
                    li.town = xpp.text
                }

                if (eventType == XmlPullParser.END_TAG) {
                    inWoe = false
                    inTown = false
                }

                eventType = xpp.next()
            }

            // Add the primary woeid if it was found.
            if (!TextUtils.isEmpty(primaryWoeid) && primaryWoeid != null) {
                li.woeids.add(primaryWoeid)
            }

            // Sort by descending tag name to order by decreasing precision
            // (locality3, locality2, locality1, admin3, admin2, admin1, etc.)
            alternateWoeids.sortWith { pair1, pair2 ->
                pair1.first.compareTo(pair2.first)
            }

            for (pair in alternateWoeids) {
                li.woeids.add(pair.second)
            }

            if (li.woeids.isNotEmpty()) {
                return li
            }

            throw CantGetWeatherException(
                true,
                R.string.no_weather_data,
                "No WOEIDs found nearby."
            )
        } catch (e: IOException) {
            throw CantGetWeatherException(
                true,
                R.string.no_weather_data,
                "Error parsing place search XML",
                e
            )
        } catch (e: XmlPullParserException) {
            throw CantGetWeatherException(
                true,
                R.string.no_weather_data,
                "Error parsing place search XML",
                e
            )
        } finally {
            connection?.disconnect()
        }
    }

    private const val PARSE_STATE_NONE = 0
    private const val PARSE_STATE_PLACE = 1
    private const val PARSE_STATE_WOEID = 2
    private const val PARSE_STATE_NAME = 3
    private const val PARSE_STATE_COUNTRY = 4
    private const val PARSE_STATE_ADMIN1 = 5

    @JvmStatic
    fun findLocationsAutocomplete(startsWith: String?): List<LocationSearchResult> {
        LOGD(TAG, "Autocompleting locations starting with '$startsWith'")

        val results = ArrayList<LocationSearchResult>()

        var connection: HttpURLConnection? = null
        try {
            connection = Utils.openUrlConnection(buildPlaceSearchStartsWithUrl(startsWith ?: ""))
            val xpp = sXmlPullParserFactory!!.newPullParser()
            xpp.setInput(InputStreamReader(connection.inputStream))

            var result: LocationSearchResult? = null
            var name: String? = null
            var country: String? = null
            var admin1: String? = null
            val sb = StringBuilder()

            var state = PARSE_STATE_NONE
            var eventType = xpp.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = xpp.name

                if (eventType == XmlPullParser.START_TAG) {
                    when (state) {
                        PARSE_STATE_NONE -> {
                            if ("place" == tagName) {
                                state = PARSE_STATE_PLACE
                                result = LocationSearchResult()
                                name = null
                                country = null
                                admin1 = null
                            }
                        }

                        PARSE_STATE_PLACE -> {
                            when (tagName) {
                                "name" -> state = PARSE_STATE_NAME
                                "woeid" -> state = PARSE_STATE_WOEID
                                "country" -> state = PARSE_STATE_COUNTRY
                                "admin1" -> state = PARSE_STATE_ADMIN1
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    when (state) {
                        PARSE_STATE_WOEID -> result?.woeid = xpp.text
                        PARSE_STATE_NAME -> name = xpp.text
                        PARSE_STATE_COUNTRY -> country = xpp.text
                        PARSE_STATE_ADMIN1 -> admin1 = xpp.text
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("place" == tagName) {
                        sb.setLength(0)
                        if (!TextUtils.isEmpty(name)) {
                            sb.append(name)
                        }
                        if (!TextUtils.isEmpty(admin1)) {
                            if (sb.isNotEmpty()) {
                                sb.append(", ")
                            }
                            sb.append(admin1)
                        }
                        if (result != null) {
                            result.displayName = sb.toString()
                            result.country = country
                            results.add(result)
                        }
                        state = PARSE_STATE_NONE
                    } else if (state != PARSE_STATE_NONE) {
                        state = PARSE_STATE_PLACE
                    }
                }

                eventType = xpp.next()
            }
        } catch (e: IOException) {
            LOGW(TAG, "Error parsing place search XML")
        } catch (e: XmlPullParserException) {
            LOGW(TAG, "Error parsing place search XML")
        } finally {
            connection?.disconnect()
        }

        return results
    }

    private fun buildWeatherQueryUrl(woeid: String?): String {
        // http://developer.yahoo.com/weather/
        val query = "https://query.yahooapis.com/v1/public/yql?q=" +
                "SELECT * FROM weather.forecast WHERE woeid=$woeid" +
                " and u='$sWeatherUnits'"
        return query.replace(" ".toRegex(), "%20")
    }

    private fun buildPlaceSearchUrl(l: Location): String {
        // GeoPlanet API
        return "http://where.yahooapis.com/v1/places.q('" +
                "${l.latitude},${l.longitude}')" +
                "?appid=${YahooWeatherApiConfig.APP_ID}"
    }

    private fun buildPlaceSearchStartsWithUrl(startsWith: String): String {
        // GeoPlanet API
        val sanitized = startsWith.replace("[^\\w ]+".toRegex(), "").replace(" ".toRegex(), "%20")
        return "http://where.yahooapis.com/v1/places.q('$sanitized%2A');" +
                "count=$MAX_SEARCH_RESULTS" +
                "?appid=${YahooWeatherApiConfig.APP_ID}"
    }

    class LocationInfo {
        // Sorted by decreasing precision
        // (point of interest, locality3, locality2, locality1, admin3, admin2, admin1, etc.)
        @JvmField
        var woeids: MutableList<String> = ArrayList()
        @JvmField
        var town: String? = null
    }

    class LocationSearchResult {
        @JvmField
        var woeid: String? = null
        @JvmField
        var displayName: String? = null
        @JvmField
        var country: String? = null
    }
}
