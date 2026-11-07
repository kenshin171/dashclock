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

package com.google.android.apps.dashclock.api

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.google.android.apps.dashclock.api.internal.IExtension
import com.google.android.apps.dashclock.api.internal.IExtensionHost

/**
 * Base class for a DashClock extension. Extensions are a way for other apps to show additional
 * status information within DashClock widgets that the user may add to the lockscreen or home
 * screen. A limited amount of status information is supported. See the [ExtensionData] class
 * for the types of information that can be displayed.
 */
abstract class DashClockExtension protected constructor() : Service() {
    private var mInitialized = false
    private var mIsWorldReadable = false
    private var mHost: IExtensionHost? = null

    @Volatile
    private lateinit var mServiceLooper: Looper

    @Volatile
    private lateinit var mServiceHandler: Handler

    override fun onCreate() {
        super.onCreate()
        loadMetaData()

        val thread = HandlerThread("DashClockExtension:${javaClass.simpleName}")
        thread.start()

        mServiceLooper = thread.looper
        mServiceHandler = Handler(mServiceLooper)
    }

    override fun onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null)
        mServiceLooper.quit()
    }

    private fun loadMetaData() {
        val pm = packageManager
        try {
            val si = pm.getServiceInfo(
                ComponentName(this, javaClass),
                PackageManager.GET_META_DATA
            )
            val metaData = si.metaData
            if (metaData != null) {
                val protocolVersion = metaData.getInt("protocolVersion")
                mIsWorldReadable = protocolVersion >= PROTOCOL_VERSION_WORLD_READABILITY &&
                    metaData.getBoolean("worldReadable")
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Could not load metadata (e.g. world readable) for extension.")
        }
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    private val mBinder: IExtension.Stub = object : IExtension.Stub() {
        @Throws(RemoteException::class)
        override fun onInitialize(host: IExtensionHost, isReconnect: Boolean) {
            if (!mIsWorldReadable) {
                var verified = false
                val pm = packageManager
                val packages = pm.getPackagesForUid(getCallingUid())
                if (!packages.isNullOrEmpty()) {
                    try {
                        @Suppress("DEPRECATION")
                        val pi = pm.getPackageInfo(packages[0], PackageManager.GET_SIGNATURES)
                        @Suppress("DEPRECATION")
                        val signatures = pi.signatures
                        if (signatures != null &&
                            signatures.size == 1 &&
                            DashClockSignature.SIGNATURE == signatures[0]
                        ) {
                            verified = true
                        }
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }
                }

                if (!verified) {
                    Log.e(
                        TAG,
                        "Caller is not official DashClock app and this extension is not world-readable."
                    )
                    throw SecurityException(
                        "Caller is not official DashClock app and this extension is not world-readable."
                    )
                }
            }

            mHost = host

            if (!mInitialized) {
                this@DashClockExtension.onInitialize(isReconnect)
                mInitialized = true
            }
        }

        @Throws(RemoteException::class)
        override fun onUpdate(reason: Int) {
            if (!mInitialized) {
                return
            }

            mServiceHandler.post {
                this@DashClockExtension.onUpdateData(reason)
            }
        }
    }

    /**
     * Called when a connection with the main DashClock app has been established or re-established
     * after a previous one was lost.
     */
    protected open fun onInitialize(isReconnect: Boolean) {
    }

    /**
     * Called when the DashClock app process is requesting that the extension provide updated
     * information to show to the user.
     */
    protected abstract fun onUpdateData(reason: Int)

    /**
     * Notifies the main DashClock app that new data is available for the extension and should
     * potentially be shown to the user.
     */
    protected fun publishUpdate(data: ExtensionData?) {
        try {
            mHost?.publishUpdate(data)
        } catch (e: RemoteException) {
            Log.e(TAG, "Couldn't publish updated extension data.", e)
        }
    }

    /**
     * Requests that the main DashClock app watch the given content URIs and call this extension's
     * [onUpdateData] method when changes are observed.
     */
    protected fun addWatchContentUris(uris: Array<String>?) {
        try {
            mHost?.addWatchContentUris(uris)
        } catch (e: RemoteException) {
            Log.e(TAG, "Couldn't watch content URIs.", e)
        }
    }

    /**
     * Requests that the main DashClock app stop watching all content URIs previously registered
     * with [addWatchContentUris] for this extension.
     */
    protected fun removeAllWatchContentUris() {
        try {
            mHost?.removeAllWatchContentUris()
        } catch (e: RemoteException) {
            Log.e(TAG, "Couldn't stop watching content URIs.", e)
        }
    }

    /**
     * Requests that the main DashClock app call (or not call) this extension's
     * [onUpdateData] method when the screen turns on.
     */
    protected fun setUpdateWhenScreenOn(updateWhenScreenOn: Boolean) {
        try {
            mHost?.setUpdateWhenScreenOn(updateWhenScreenOn)
        } catch (e: RemoteException) {
            Log.e(TAG, "Couldn't set the extension to update upon ACTION_SCREEN_ON.", e)
        }
    }

    companion object {
        private const val TAG = "DashClockExtension"

        const val UPDATE_REASON_UNKNOWN = 0
        const val UPDATE_REASON_INITIAL = 1
        const val UPDATE_REASON_PERIODIC = 2
        const val UPDATE_REASON_SETTINGS_CHANGED = 3
        const val UPDATE_REASON_CONTENT_CHANGED = 4
        const val UPDATE_REASON_SCREEN_ON = 5
        const val UPDATE_REASON_MANUAL = 6

        const val ACTION_EXTENSION = "com.google.android.apps.dashclock.Extension"

        const val EXTRA_FROM_DASHCLOCK_SETTINGS =
            "com.google.android.apps.dashclock.extra.FROM_DASHCLOCK_SETTINGS"

        const val PERMISSION_READ_EXTENSION_DATA =
            "com.google.android.apps.dashclock.permission.READ_EXTENSION_DATA"

        private const val PROTOCOL_VERSION_WORLD_READABILITY = 2
    }
}
