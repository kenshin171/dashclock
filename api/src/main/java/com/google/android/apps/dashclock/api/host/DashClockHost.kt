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

package com.google.android.apps.dashclock.api.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.RemoteException
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.DashClockSignature
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.api.internal.IDataConsumerHost
import com.google.android.apps.dashclock.api.internal.IDataConsumerHostCallback

/**
 * Base class to be used by data consumer host implementations in order to get
 * updates for installed extensions and their data.
 */
abstract class DashClockHost @Throws(SecurityException::class) protected constructor(
    private val mContext: Context
) {
    /**
     * Exception thrown if the host multiplexer service is not available.
     */
    class NoMultiplexerAvailableException internal constructor(message: String) :
        RuntimeException(message)

    private var mService: IDataConsumerHost? = null
    private val mAvailableExtensions: MutableList<ExtensionListing> = ArrayList()
    private var mNonWorldReadableExtensionsVisible = false
    private val mDataCache: MutableMap<ComponentName, ExtensionData?> = HashMap()
    private var mListenedExtensions: Set<ComponentName>? = null
    private var mDestroyed = false

    private var mIsMultiplexerPresent = true

    private val mHandlerCallback = Handler.Callback { msg ->
        @Suppress("UNCHECKED_CAST")
        when (msg.what) {
            MSG_NOTIFY_EXTENSION_LIST_CHANGE -> {
                mAvailableExtensions.clear()
                mAvailableExtensions.addAll(msg.obj as List<ExtensionListing>)
                mNonWorldReadableExtensionsVisible = msg.arg1 == 0
                onAvailableExtensionsChanged()
                true
            }
            MSG_NOTIFY_DATA_CHANGE -> {
                onExtensionDataChanged(msg.obj as ComponentName)
                true
            }
            MSG_RECONNECT -> {
                try {
                    if (!connect()) {
                        mHandler.sendEmptyMessageDelayed(MSG_RECONNECT, AUTO_RECONNECT_DELAY.toLong())
                    }
                } catch (e: NoMultiplexerAvailableException) {
                    // Reconnect will not work, so stop here
                } catch (e: SecurityException) {
                    // Reconnect will not work, so stop here
                }
                true
            }
            MSG_NOTIFY_MUX_NOT_AVAILABLE -> {
                onMultiplexerChangedDetected(false)
                mIsMultiplexerPresent = false
                true
            }
            else -> false
        }
    }

    private val mHandler: Handler = Handler(Looper.getMainLooper(), mHandlerCallback)

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val hostService = IDataConsumerHost.Stub.asInterface(service)
            mService = hostService
            try {
                mListenedExtensions?.let { listenTo(it) }
                val msg = mHandler.obtainMessage(
                    MSG_NOTIFY_EXTENSION_LIST_CHANGE,
                    if (hostService.areNonWorldReadableExtensionsVisible()) 0 else 1,
                    0,
                    hostService.availableExtensions
                )
                msg.sendToTarget()
            } catch (ex: RemoteException) {
                mService = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mNonWorldReadableExtensionsVisible = false
            mAvailableExtensions.clear()
            mService = null
            if (!mDestroyed) {
                mHandler.sendEmptyMessageDelayed(MSG_RECONNECT, AUTO_RECONNECT_DELAY.toLong())
            }
        }
    }

    private val mCallback: IDataConsumerHostCallback.Stub =
        object : IDataConsumerHostCallback.Stub() {
            override fun notifyUpdate(source: ComponentName, data: ExtensionData?) {
                synchronized(mDataCache) {
                    mDataCache[source] = data
                }
                mHandler.obtainMessage(MSG_NOTIFY_DATA_CHANGE, source).sendToTarget()
            }

            override fun notifyAvailableExtensionChanged(
                extensions: List<ExtensionListing>,
                nonWorldReadableExtensionsVisible: Boolean
            ) {
                mHandler.obtainMessage(
                    MSG_NOTIFY_EXTENSION_LIST_CHANGE,
                    if (nonWorldReadableExtensionsVisible) 0 else 1,
                    0,
                    extensions
                ).sendToTarget()
            }
        }

    init {
        try {
            if (!connect()) {
                mHandler.sendEmptyMessageDelayed(MSG_RECONNECT, AUTO_RECONNECT_DELAY.toLong())
            }
        } catch (ex: NoMultiplexerAvailableException) {
            mHandler.obtainMessage(MSG_NOTIFY_MUX_NOT_AVAILABLE).sendToTarget()
        } catch (ex: SecurityException) {
            mHandler.obtainMessage(MSG_NOTIFY_MUX_NOT_AVAILABLE).sendToTarget()
        }
    }

    fun destroy() {
        mDestroyed = true
        mHandler.removeCallbacksAndMessages(null)
        val service = mService
        if (service != null) {
            try {
                service.listenTo(null, mCallback)
                mContext.unbindService(mConnection)
            } catch (e: RemoteException) {
                // ignored
            }
        }
    }

    fun areNonWorldReadableExtensionsVisible(): Boolean = mNonWorldReadableExtensionsVisible

    val enableForceWorldReadabilityIntent: Intent
        get() = Intent(ACTION_ASK_ENABLE_FORCE_WORLD_READABLE)

    fun getAvailableExtensions(onlyWorldReadableExtensions: Boolean): List<ExtensionListing> {
        if (!onlyWorldReadableExtensions) {
            return ArrayList(mAvailableExtensions)
        }
        val eis = ArrayList(mAvailableExtensions)
        for (i in eis.size - 1 downTo 0) {
            if (!eis[i].worldReadable()) {
                eis.removeAt(i)
            }
        }
        return eis
    }

    fun getExtensionData(extension: ComponentName): ExtensionData? {
        synchronized(mDataCache) {
            return mDataCache[extension]
        }
    }

    fun listenTo(extensions: Set<ComponentName>?) {
        mListenedExtensions = extensions
        val extensionList = extensions?.let { ArrayList(it) }
        try {
            mService?.listenTo(extensionList, mCallback)
        } catch (e: RemoteException) {
            // Ignore
        }
    }

    fun startSettingsActivityForExtension(extension: ExtensionListing): Boolean {
        val service = mService
        if (service != null && extension.settingsActivity() != null) {
            try {
                service.showExtensionSettings(extension.componentName(), mCallback)
                return true
            } catch (e: RemoteException) {
                // Ignore
            }
        }
        return false
    }

    fun requestExtensionUpdate(extensions: List<ComponentName>?): Boolean {
        try {
            mService?.requestExtensionUpdate(extensions, mCallback)
            return true
        } catch (ex: RemoteException) {
            // Ignore
        }
        return false
    }

    protected open fun onAvailableExtensionsChanged() {}

    protected open fun onExtensionDataChanged(extension: ComponentName) {}

    protected open fun onMultiplexerChangedDetected(available: Boolean) {}

    val multiplexerDownloadIntent: Intent?
        get() {
            val apps = getOtherAppsWithReadDataExtensionsPermission(mContext)
            if (apps.isNotEmpty()) {
                return null
            }
            val pkgName = MULTIPLEXER_HOST_SERVICE.packageName
            val uri = Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")
            return Intent(Intent.ACTION_VIEW, uri)
        }

    fun handleMultiplexerPackageChanged() {
        val isMultiplexerPresent = isMultiplexerServicePresent(mContext)
        if (mIsMultiplexerPresent != isMultiplexerPresent) {
            if (isMultiplexerPresent && mService == null) {
                mHandler.obtainMessage(MSG_RECONNECT).sendToTarget()
            } else if (!isMultiplexerPresent && mService != null) {
                mContext.unbindService(mConnection)
            }

            onMultiplexerChangedDetected(isMultiplexerPresent)
            mIsMultiplexerPresent = isMultiplexerPresent
        }
    }

    @Throws(SecurityException::class, NoMultiplexerAvailableException::class)
    private fun connect(): Boolean {
        val cn = getMultiplexerService(mContext)
            ?: throw NoMultiplexerAvailableException("Multiplexer service not installed")

        enforcePermission(mContext, BIND_DATA_CONSUMER_PERMISSION)

        val intent = Intent().apply { component = cn }
        return mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
    }

    companion object {
        const val BIND_DATA_CONSUMER_PERMISSION =
            "com.google.android.apps.dashclock.permission.BIND_DATA_CONSUMER"

        private val MULTIPLEXER_HOST_SERVICE = ComponentName(
            "net.nurik.roman.dashclock",
            "com.google.android.apps.dashclock.DashClockService"
        )

        private const val ACTION_ASK_ENABLE_FORCE_WORLD_READABLE =
            "com.google.android.apps.dashclock.action.ASK_ENABLE_FORCE_WORLD_READABLE"

        private const val AUTO_RECONNECT_DELAY = 5000

        private const val MSG_NOTIFY_EXTENSION_LIST_CHANGE = 1
        private const val MSG_NOTIFY_DATA_CHANGE = 2
        private const val MSG_RECONNECT = 3
        private const val MSG_NOTIFY_MUX_NOT_AVAILABLE = 4

        @JvmStatic
        fun isMultiplexerServicePresent(context: Context): Boolean {
            return getMultiplexerService(context) != null
        }

        @JvmStatic
        fun getOtherAppsWithReadDataExtensionsPermission(context: Context): List<String> {
            val installedApps = ArrayList<String>()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return installedApps
            }
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (ai in apps) {
                try {
                    val pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_PERMISSIONS)
                    val perms = pi.permissions
                    if (perms != null) {
                        for (perm in perms) {
                            if (perm.name == DashClockExtension.PERMISSION_READ_EXTENSION_DATA) {
                                if (ai.packageName != MULTIPLEXER_HOST_SERVICE.packageName) {
                                    installedApps.add(ai.packageName)
                                    break
                                }
                            }
                        }
                    }
                } catch (ex: NameNotFoundException) {
                    // Ignore
                }
            }
            return installedApps
        }

        private fun getMultiplexerService(context: Context): ComponentName? {
            val pm = context.packageManager
            val debuggable =
                (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            try {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(
                    MULTIPLEXER_HOST_SERVICE.packageName,
                    PackageManager.GET_SIGNATURES or PackageManager.GET_SERVICES
                )
                if (pi.applicationInfo?.enabled == true) {
                    val services = pi.services ?: return null
                    for (si in services) {
                        if (MULTIPLEXER_HOST_SERVICE.className == si.name && si.enabled) {
                            if (debuggable) {
                                return MULTIPLEXER_HOST_SERVICE
                            }
                            @Suppress("DEPRECATION")
                            val signatures = pi.signatures
                            if (signatures != null &&
                                signatures.size == 1 &&
                                DashClockSignature.SIGNATURE == signatures[0]
                            ) {
                                return MULTIPLEXER_HOST_SERVICE
                            }
                        }
                    }
                }
            } catch (e: NameNotFoundException) {
                // ignored
            }
            return null
        }

        @Throws(SecurityException::class)
        private fun enforcePermission(context: Context, permission: String) {
            val pm = context.packageManager
            val packageName = context.packageName
            try {
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                val requestedPermissions = pi.requestedPermissions
                if (requestedPermissions != null) {
                    for (reqPerm in requestedPermissions) {
                        if (reqPerm == permission) {
                            return
                        }
                    }
                }
            } catch (e: NameNotFoundException) {
                // Ignore
            }
            throw SecurityException("Caller didn't request the permission \"$permission\"")
        }

        @JvmStatic
        fun isDashClockPresent(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(MULTIPLEXER_HOST_SERVICE.packageName, 0)
                true
            } catch (e: NameNotFoundException) {
                false
            }
        }
    }
}
