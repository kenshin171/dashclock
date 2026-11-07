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

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.preference.PreferenceManager
import android.text.TextUtils
import androidx.legacy.content.WakefulBroadcastReceiver
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.LogUtils.LOGW
import com.google.android.apps.dashclock.Utils.MILLIS_NANOS
import com.google.android.apps.dashclock.Utils.MINUTES_MILLIS
import com.google.android.apps.dashclock.Utils.SECONDS_MILLIS
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.configuration.AppChooserPreference
import com.google.android.apps.dashclock.weather.YahooWeatherApiClient.LocationInfo
import com.google.android.apps.dashclock.weather.YahooWeatherApiClient.getLocationInfo
import com.google.android.apps.dashclock.weather.YahooWeatherApiClient.getWeatherForLocationInfo
import com.google.android.apps.dashclock.weather.YahooWeatherApiClient.setWeatherUnits
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import net.nurik.roman.dashclock.BuildConfig
import net.nurik.roman.dashclock.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A local weather and forecast extension.
 */
class WeatherExtension : DashClockExtension() {
    private var mServiceThreadHandler: Handler? = null
    private var mOneTimeLocationListenerActive = false
    private val mTimeoutHandler = Handler(Looper.getMainLooper())

    private fun resetAndCancelRetries() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit().remove(STATE_WEATHER_LAST_BACKOFF_MILLIS).apply()
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        am.cancel(
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, WeatherRetryReceiver::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun scheduleRetry() {
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        val lastBackoffMillis = sp.getInt(STATE_WEATHER_LAST_BACKOFF_MILLIS, 0)
        val backoffMillis = if (lastBackoffMillis > 0) {
            lastBackoffMillis * 2
        } else {
            INITIAL_BACKOFF_MILLIS
        }
        sp.edit().putInt(STATE_WEATHER_LAST_BACKOFF_MILLIS, backoffMillis).apply()
        LOGD(TAG, "Scheduling weather retry in ${backoffMillis / SECONDS_MILLIS} second(s)")
        val am = getSystemService(ALARM_SERVICE) as AlarmManager
        am.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + backoffMillis,
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, WeatherRetryReceiver::class.java),
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun onInitialize(isReconnect: Boolean) {
        super.onInitialize(isReconnect)
    }

    override fun onUpdateData(reason: Int) {
        if (mServiceThreadHandler == null) {
            // Get handle to background thread
            val myLooper = Looper.myLooper()
            if (myLooper != null) {
                mServiceThreadHandler = Handler(myLooper)
            }
        }

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sWeatherUnits = sp.getString(PREF_WEATHER_UNITS, sWeatherUnits) ?: "f"
        sWeatherIntent = AppChooserPreference.getIntentValue(
            sp.getString(PREF_WEATHER_SHORTCUT, null),
            DEFAULT_WEATHER_INTENT
        )

        setWeatherUnits(sWeatherUnits)

        val lastUpdateElapsedMillis = sp.getLong(
            STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS,
            -UPDATE_THROTTLE_MILLIS.toLong()
        )
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        if (reason != UPDATE_REASON_INITIAL && reason != UPDATE_REASON_MANUAL &&
            nowElapsedMillis < lastUpdateElapsedMillis + UPDATE_THROTTLE_MILLIS
        ) {
            LOGD(TAG, "Throttling weather update attempt.")
            return
        }

        LOGD(TAG, "Attempting weather update; reason=$reason")

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val ni = cm.activeNetworkInfo
        if (ni == null || !ni.isConnected) {
            LOGD(TAG, "No network connection; not attempting to update weather.")
            return
        }

        val manualLocationWoeid = WeatherLocationPreference.getWoeidFromValue(
            sp.getString(PREF_WEATHER_LOCATION, null)
        )
        if (!TextUtils.isEmpty(manualLocationWoeid) && manualLocationWoeid != null) {
            // WOEIDs
            // Honolulu = 2423945
            // Paris = 615702
            // London = 44418
            // New York = 2459115
            // San Francisco = 2487956
            val locationInfo = LocationInfo()
            locationInfo.woeids = mutableListOf(manualLocationWoeid)
            tryPublishWeatherUpdateFromLocationInfo(locationInfo)
            return
        }

        // Get the user's location, then get the weather for that location.
        tryGooglePlayServicesGetLocationAndPublishWeatherUpdate(Runnable {
            // If there was an error with Play Services, try LocationManager
            tryLocationManagerGetLocationAndPublishWeatherUpdate()
        })
    }

    @SuppressLint("MissingPermission")
    private fun tryGooglePlayServicesGetLocationAndPublishWeatherUpdate(
        errorRunnable: Runnable?
    ) {
        val playServicesResult =
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (playServicesResult != ConnectionResult.SUCCESS) {
            LOGW(TAG, "Google Play Services was unavailable (code $playServicesResult).")
            errorRunnable?.run()
            return
        }

        LOGD(TAG, "Getting location using Google Play Services.")
        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            var location: Location? =
                Tasks.await(fusedClient.lastLocation, 10, TimeUnit.SECONDS)
            if (location == null ||
                (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) >= STALE_LOCATION_NANOS
            ) {
                LOGW(
                    TAG,
                    "Stale or missing last-known location; requesting single location update."
                )
                location = Tasks.await(
                    fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null),
                    30,
                    TimeUnit.SECONDS
                )
            }

            if (location != null) {
                tryPublishWeatherUpdateFromGeolocation(location)
            } else {
                LOGW(TAG, "Play Services returned null location; falling back to LocationManager.")
                errorRunnable?.run()
            }
        } catch (e: Exception) {
            LOGW(TAG, "Error getting location from Play Services; falling back to LocationManager.", e)
            errorRunnable?.run()
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryLocationManagerGetLocationAndPublishWeatherUpdate() {
        LOGD(TAG, "Getting location using LocationManager")
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = lm.getBestProvider(sLocationCriteria, true)
        if (provider.isNullOrEmpty()) {
            publishErrorUpdate(
                CantGetWeatherException(
                    false,
                    R.string.no_location_data,
                    "No available location providers matching criteria."
                )
            )
            return
        }

        val lastLocation = lm.getLastKnownLocation(provider)
        if (lastLocation == null ||
            (SystemClock.elapsedRealtimeNanos() - lastLocation.elapsedRealtimeNanos) >= STALE_LOCATION_NANOS
        ) {
            LOGW(
                TAG,
                "Stale or missing last-known location; requesting single coarse location update."
            )
            disableOneTimeLocationListener()
            mOneTimeLocationListenerActive = true
            lm.requestSingleUpdate(provider, mOneTimeLocationListener, null)

            // Time-out single location update request
            mTimeoutHandler.removeCallbacksAndMessages(null)
            mTimeoutHandler.postDelayed({
                LOGE(TAG, "LocationManager location request timed out.")
                disableOneTimeLocationListener()
                scheduleRetry()
            }, LOCATION_TIMEOUT_MILLIS.toLong())
        } else {
            tryPublishWeatherUpdateFromGeolocation(lastLocation)
        }
    }

    private fun disableOneTimeLocationListener() {
        if (mOneTimeLocationListenerActive) {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(mOneTimeLocationListener)
            mOneTimeLocationListenerActive = false
        }
    }

    private val mOneTimeLocationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            LOGD(TAG, "Got network location update")
            mTimeoutHandler.removeCallbacksAndMessages(null)
            tryPublishWeatherUpdateFromGeolocation(location)
            disableOneTimeLocationListener()
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
            LOGD(TAG, "Network location provider status change: $status")
            if (status == LocationProvider.TEMPORARILY_UNAVAILABLE) {
                scheduleRetry()
                disableOneTimeLocationListener()
            }
        }

        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_RECEIVED_LOCATION == intent.action) {
            val location = intent.getParcelableExtra<Location>(
                LocationManager.KEY_LOCATION_CHANGED
            )
            if (mServiceThreadHandler == null) {
                LOGW(
                    TAG,
                    "Can't process location update because onUpdateData hasn't been called on this service instance."
                )
            } else if (location != null) {
                // A location update request succeeded; try publishing weather from here.
                LOGD(TAG, "Got a Play Services location update; trying weather update.")
                mTimeoutHandler.removeCallbacksAndMessages(null)
                mServiceThreadHandler?.post {
                    tryPublishWeatherUpdateFromGeolocation(location)
                }
            }

            WakefulBroadcastReceiver.completeWakefulIntent(intent)
            stopSelf(startId)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        disableOneTimeLocationListener()
    }

    private fun tryPublishWeatherUpdateFromGeolocation(location: Location) {
        try {
            LOGD(TAG, "Using location: ${location.latitude},${location.longitude}")
            tryPublishWeatherUpdateFromLocationInfo(getLocationInfo(location))
        } catch (e: CantGetWeatherException) {
            publishErrorUpdate(e)
            if (e.isRetryable) {
                scheduleRetry()
            }
        }
    }

    private fun tryPublishWeatherUpdateFromLocationInfo(locationInfo: LocationInfo) {
        try {
            publishWeatherUpdate(getWeatherForLocationInfo(locationInfo))
        } catch (e: CantGetWeatherException) {
            publishErrorUpdate(e)
            if (e.isRetryable) {
                scheduleRetry()
            }
        }
    }

    private fun publishErrorUpdate(e: CantGetWeatherException) {
        LOGE(TAG, "Showing a weather extension error", e)
        publishUpdate(
            ExtensionData()
                .visible(true)
                .clickIntent(sWeatherIntent)
                .icon(R.drawable.ic_weather_clear)
                .status(getString(R.string.status_none))
                .expandedBody(getString(e.userFacingErrorStringId))
        )
    }

    private fun publishWeatherUpdate(weatherData: WeatherData) {
        val temperature = if (weatherData.temperature != WeatherData.INVALID_TEMPERATURE) {
            getString(R.string.temperature_template, weatherData.temperature)
        } else {
            getString(R.string.status_none)
        }
        val expandedBody = StringBuilder()

        if (weatherData.low != WeatherData.INVALID_TEMPERATURE &&
            weatherData.high != WeatherData.INVALID_TEMPERATURE
        ) {
            expandedBody.append(
                getString(
                    R.string.weather_low_high_template,
                    getString(R.string.temperature_template, weatherData.low),
                    getString(R.string.temperature_template, weatherData.high)
                )
            )
        }

        var conditionIconId = WeatherData.getConditionIconId(weatherData.conditionCode)
        if (WeatherData.getConditionIconId(weatherData.todayForecastConditionCode)
            == R.drawable.ic_weather_raining
        ) {
            // Show rain if it will rain today.
            conditionIconId = R.drawable.ic_weather_raining

            if (expandedBody.isNotEmpty()) {
                expandedBody.append(", ")
            }
            expandedBody.append(
                getString(R.string.later_forecast_template, weatherData.forecastText)
            )
        }

        if (expandedBody.isNotEmpty()) {
            expandedBody.append("\n")
        }
        expandedBody.append(weatherData.location)

        if (BuildConfig.DEBUG) {
            expandedBody.append("\n")
                .append(SimpleDateFormat.getDateTimeInstance().format(Date()))
        }

        publishUpdate(
            ExtensionData()
                .visible(true)
                .clickIntent(sWeatherIntent)
                .status(temperature)
                .expandedTitle(
                    getString(
                        R.string.weather_expanded_title_template,
                        temperature + sWeatherUnits.uppercase(Locale.US),
                        weatherData.conditionText
                    )
                )
                .icon(conditionIconId)
                .expandedBody(expandedBody.toString())
        )

        // Mark that a successful weather update has been pushed
        resetAndCancelRetries()
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit().putLong(
            STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS,
            SystemClock.elapsedRealtime()
        ).commit()
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(WeatherExtension::class.java)

        const val ACTION_RECEIVED_LOCATION: String =
            "com.google.android.apps.dashclock.action.RECEIVED_LOCATION"

        const val PREF_WEATHER_UNITS: String = "pref_weather_units"
        const val PREF_WEATHER_SHORTCUT: String = "pref_weather_shortcut"
        const val PREF_WEATHER_LOCATION: String = "pref_weather_location"

        @JvmField
        val DEFAULT_WEATHER_INTENT: Intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=weather")
        )

        const val STATE_WEATHER_LAST_BACKOFF_MILLIS: String =
            "state_weather_last_backoff_millis"
        const val STATE_WEATHER_LAST_UPDATE_ELAPSED_MILLIS: String =
            "state_weather_last_update_elapsed_millis"

        // At least 10 min b/w updates
        private const val UPDATE_THROTTLE_MILLIS = 10 * MINUTES_MILLIS

        private const val STALE_LOCATION_NANOS = 10L * MINUTES_MILLIS * MILLIS_NANOS

        // 30 seconds for first error retry
        private const val INITIAL_BACKOFF_MILLIS = 30 * SECONDS_MILLIS

        // 60 sec timeout for location attempt
        private const val LOCATION_TIMEOUT_MILLIS = 60 * SECONDS_MILLIS

        private val sLocationCriteria: Criteria = Criteria().apply {
            powerRequirement = Criteria.POWER_LOW
            accuracy = Criteria.ACCURACY_COARSE
            isCostAllowed = false
        }

        private var sWeatherUnits = "f"
        private var sWeatherIntent: Intent? = null
    }
}
