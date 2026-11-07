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

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.preference.PreferenceManager
import android.text.TextUtils
import androidx.legacy.content.WakefulBroadcastReceiver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.DashClockSignature
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.api.host.DashClockHost
import com.google.android.apps.dashclock.api.host.ExtensionListing
import com.google.android.apps.dashclock.api.internal.IDataConsumerHost
import com.google.android.apps.dashclock.api.internal.IDataConsumerHostCallback
import com.google.android.apps.dashclock.render.WidgetRenderer

/**
 * The primary service for DashClock. This service is in charge of updating widget UI (see
 * [ACTION_UPDATE_WIDGETS]) and updating extension data (see [ACTION_UPDATE_EXTENSIONS]).
 */
class DashClockService : Service(),
    ExtensionManager.OnChangeListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mExtensionHost: ExtensionHost
    private lateinit var mExtensionManager: ExtensionManager
    private lateinit var mCallbacks: CallbackList
    private lateinit var mRegisteredCallbacks: MutableMap<IBinder, CallbackData>
    private val mHandler = Handler(Looper.getMainLooper())
    private var mForceWorldReadable = false

    override fun onCreate() {
        super.onCreate()
        LOGD(TAG, "onCreate")

        // Initialize the extensions components (host and manager)
        mCallbacks = CallbackList()
        mRegisteredCallbacks = HashMap()
        mExtensionManager = ExtensionManager.getInstance(this)
        mExtensionManager.addOnChangeListener(this)
        mExtensionHost = ExtensionHost(this)

        val filter = IntentFilter(ACTION_EXTENSION_UPDATE_REQUESTED)
        LocalBroadcastManager.getInstance(this).registerReceiver(mExtensionEventsReceiver, filter)

        // Start a periodic refresh of all the extensions
        // FIXME: only do this if there are any active extensions
        PeriodicExtensionRefreshReceiver.updateExtensionsAndEnsurePeriodicRefresh(this)

        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(sp, PREF_FORCE_WORLD_READABLE)
    }

    override fun onDestroy() {
        super.onDestroy()
        LOGD(TAG, "onDestroy")

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mExtensionEventsReceiver)
        PeriodicExtensionRefreshReceiver.cancelPeriodicRefresh(this)

        mExtensionHost.destroy()
        mCallbacks.kill()

        mUpdateHandler.removeCallbacksAndMessages(null)
        mExtensionManager.removeOnChangeListener(this)

        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LOGD(TAG, "onStartCommand: " + (intent?.toString() ?: "no intent"))
        enforceCallingPermission(DashClockExtension.PERMISSION_READ_EXTENSION_DATA)

        if (intent != null) {
            val action = intent.action
            if (ACTION_UPDATE_WIDGETS == action) {
                handleUpdateWidgets(intent)
            } else if (ACTION_UPDATE_EXTENSIONS == action) {
                handleUpdateExtensions(intent)
            }

            // If started by a wakeful broadcast receiver, release the wake lock it acquired.
            WakefulBroadcastReceiver.completeWakefulIntent(intent)
        }

        return START_STICKY
    }

    private val mUpdateHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            LOGD(
                TAG,
                "onExtensionsChanged from " + (if (msg.obj != null) "extension ${msg.obj}" else "DashClock")
            )
            sendBroadcast(Intent(ACTION_EXTENSIONS_CHANGED))
            handleUpdateWidgets(Intent())
            WidgetRenderer.notifyDataSetChanged(this@DashClockService)
        }
    }

    /**
     * Updates a widget's UI.
     */
    private fun handleUpdateWidgets(intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(this)

        // Either update all app widgets, or only those which were requested.
        val appWidgetIds: IntArray = if (intent.hasExtra(EXTRA_APPWIDGET_ID)) {
            intArrayOf(intent.getIntExtra(EXTRA_APPWIDGET_ID, -1))
        } else {
            appWidgetManager.getAppWidgetIds(ComponentName(this, WidgetProvider::class.java))
        }

        val sb = StringBuilder()
        for (appWidgetId in appWidgetIds) {
            sb.append(appWidgetId).append(" ")
        }
        LOGD(TAG, "Rendering widgets with appWidgetId(s): $sb")

        WidgetRenderer.renderWidgets(this, appWidgetIds)
    }

    /**
     * Asks extensions to provide data updates.
     */
    private fun handleUpdateExtensions(intent: Intent) {
        val reason = intent.getIntExtra(
            EXTRA_UPDATE_REASON,
            DashClockExtension.UPDATE_REASON_UNKNOWN
        )
        val updateExtension = intent.getStringExtra(EXTRA_COMPONENT_NAME)

        LOGD(
            TAG,
            String.format(
                "handleUpdateExtensions [action=%s, reason=%d, extension=%s]",
                intent.action,
                reason,
                updateExtension ?: ""
            )
        )

        // Either update all extensions, or only the requested one.
        if (!TextUtils.isEmpty(updateExtension)) {
            val cn = ComponentName.unflattenFromString(updateExtension!!)
            if (cn != null) {
                mExtensionHost.execute(
                    cn,
                    ExtensionHost.UPDATE_OPERATIONS.get(reason),
                    ExtensionHost.UPDATE_COLLAPSE_TIME_MILLIS,
                    reason
                )
            }
        } else {
            for (cn in mExtensionManager.getActiveExtensionNames()) {
                mExtensionHost.execute(
                    cn,
                    ExtensionHost.UPDATE_OPERATIONS.get(reason),
                    ExtensionHost.UPDATE_COLLAPSE_TIME_MILLIS,
                    reason
                )
            }
        }
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        if (PREF_FORCE_WORLD_READABLE == key) {
            mForceWorldReadable = sp.getBoolean(PREF_FORCE_WORLD_READABLE, false)
            onExtensionsChanged(null)
        }
    }

    /*
     * Read API
     */

    private class CallbackData {
        var mUid: Int = 0
        var mPackage: String? = null
        var mHasDashClockSignature: Boolean = false
        var mExtensions: List<ComponentName>? = null
    }

    private val mBinder = object : IDataConsumerHost.Stub() {
        @Throws(RemoteException::class)
        override fun listenTo(
            extensions: List<ComponentName>?,
            cb: IDataConsumerHostCallback?
        ) {
            if (cb == null) {
                throw NullPointerException("Callback must not be null")
            }
            enforceCallingPermission(DashClockHost.BIND_DATA_CONSUMER_PERMISSION)

            val callingUid = Binder.getCallingUid()
            mHandler.post {
                val data = createCallbackData(callingUid)
                data.mExtensions = extensions
                mCallbacks.update(cb, data)
            }
        }

        @Throws(RemoteException::class)
        override fun showExtensionSettings(
            extension: ComponentName,
            cb: IDataConsumerHostCallback?
        ) {
            // Check that callback was registered and that extension was enabled
            enforceEnabledExtensionForCallback(cb, extension)

            // Make sure we know about the passed in extension
            val info = findExtensionInfo(extension)
                ?: throw NullPointerException("ExtensionInfo doesn't exists")
            if (info.settingsActivity() == null) {
                // Nothing to show
                return
            }

            // Start the proxy activity
            val i = Intent(this@DashClockService, ExtensionSettingActivityProxy::class.java)
            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            i.putExtra(EXTRA_COMPONENT_NAME, info.componentName()!!.flattenToString())
            i.putExtra(
                ExtensionSettingActivityProxy.EXTRA_SETTINGS_ACTIVITY,
                info.settingsActivity()!!.flattenToString()
            )
            startActivity(i)
        }

        @Throws(RemoteException::class)
        override fun requestExtensionUpdate(
            extensions: List<ComponentName>?,
            cb: IDataConsumerHostCallback?
        ) {
            enforceRegisteredCallingCallback(cb)
            internalRequestUpdateData(cb!!, extensions)
        }

        @Throws(RemoteException::class)
        override fun getAvailableExtensions(): List<ExtensionListing> {
            return mExtensionManager.getAvailableExtensions()
        }

        @Throws(RemoteException::class)
        override fun areNonWorldReadableExtensionsVisible(): Boolean {
            return mForceWorldReadable
        }
    }

    private inner class CallbackList : RemoteCallbackList<IDataConsumerHostCallback>() {
        fun update(cb: IDataConsumerHostCallback, data: CallbackData) {
            val binder = cb.asBinder()
            val dataExtensions = data.mExtensions
            if (dataExtensions == null) {
                if (mRegisteredCallbacks.containsKey(binder)) {
                    unregister(cb)
                    mRegisteredCallbacks.remove(binder)
                }
            } else {
                var isNewCallback = false
                if (!mRegisteredCallbacks.containsKey(binder)) {
                    isNewCallback = true
                    register(cb)
                }

                // Notify callback of data for extensions that it newly registered
                val prevExtensions = if (isNewCallback) {
                    ArrayList()
                } else {
                    mRegisteredCallbacks[binder]?.mExtensions ?: ArrayList()
                }
                val availableData = determineDataForAlreadyActiveExtensions(
                    dataExtensions,
                    prevExtensions
                )

                try {
                    for (cn in availableData.keys) {
                        val e = availableData[cn]
                        // Do not leak data if extension expressly denied access
                        // to non-dashclock apps
                        if (e?.latestData != null && isExtensionReadableByHost(e, data)) {
                            cb.notifyUpdate(e.listing!!.componentName(), e.latestData)
                        } else {
                            val notData = ExtensionData()
                            cb.notifyUpdate(cn, notData)
                        }
                    }
                } catch (e: RemoteException) {
                    // ignored, cb is dead anyway
                }
                mRegisteredCallbacks[binder] = data
            }

            recalculateActiveExtensions()
        }

        override fun onCallbackDied(cb: IDataConsumerHostCallback) {
            super.onCallbackDied(cb)
            mRegisteredCallbacks.remove(cb.asBinder())
            recalculateActiveExtensions()
        }
    }

    private fun isExtensionReadableByHost(
        e: ExtensionManager.ExtensionWithData,
        data: CallbackData
    ): Boolean {
        val listing = e.listing ?: return false
        return mForceWorldReadable ||
            listing.worldReadable() ||
            (!listing.worldReadable() && data.mHasDashClockSignature)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    override fun onExtensionsChanged(sourceExtension: ComponentName?) {
        LOGD(TAG, "onExtensionsChanged: source = $sourceExtension")

        mUpdateHandler.removeCallbacksAndMessages(null)
        mUpdateHandler.sendMessageDelayed(
            mUpdateHandler.obtainMessage(0, sourceExtension),
            UPDATE_COLLAPSE_TIME_MILLIS.toLong()
        )

        if (sourceExtension == null) {
            broadcastExtensionListChange(mExtensionManager.getAvailableExtensions())
        } else {
            val data = mExtensionManager.getExtensionWithData(sourceExtension)
            if (data?.latestData != null) {
                broadcastDataChange(sourceExtension, data)
            }
        }
    }

    private fun broadcastExtensionListChange(extensions: List<ExtensionListing>) {
        val count = mCallbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                mCallbacks.getBroadcastItem(i).notifyAvailableExtensionChanged(
                    extensions,
                    mForceWorldReadable
                )
            } catch (e: RemoteException) {
                // ignored
            }
        }
        mCallbacks.finishBroadcast()
    }

    private fun broadcastDataChange(
        source: ComponentName,
        ewd: ExtensionManager.ExtensionWithData
    ) {
        val count = mCallbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                val cb = mCallbacks.getBroadcastItem(i)
                val cbData = mRegisteredCallbacks[cb.asBinder()]
                val extension = cbData?.mExtensions
                if (extension != null && extension.contains(source)) {
                    // Do not leak data if extension expressly denied access
                    // to non-dashclock apps
                    if (isExtensionReadableByHost(ewd, cbData)) {
                        cb.notifyUpdate(source, ewd.latestData)
                    }
                }
            } catch (e: RemoteException) {
                // ignored
            }
        }
        mCallbacks.finishBroadcast()
    }

    private fun determineDataForAlreadyActiveExtensions(
        extensions: List<ComponentName>,
        excludedExtensions: List<ComponentName>?
    ): Map<ComponentName, ExtensionManager.ExtensionWithData?> {
        val result = HashMap<ComponentName, ExtensionManager.ExtensionWithData?>()
        val map = HashMap<ComponentName, ExtensionManager.ExtensionWithData>()
        for (e in mExtensionManager.getActiveExtensionsWithData()) {
            val cn = e.listing?.componentName()
            if (e.latestData != null && cn != null) {
                map[cn] = e
            }
        }
        for (extension in extensions) {
            if (excludedExtensions != null && excludedExtensions.contains(extension)) {
                continue
            }
            result[extension] = map[extension]
        }
        return result
    }

    private fun recalculateActiveExtensions() {
        val extensions = HashSet<ComponentName>()
        for (entry in mRegisteredCallbacks.values) {
            val entryExtensions = entry.mExtensions ?: continue
            for (extension in entryExtensions) {
                extensions.add(extension)
            }
        }
        LOGD(TAG, "recalculateActiveExtensions: determined list = $extensions")
        mExtensionManager.setActiveExtensions(extensions)
    }

    private fun findExtensionInfo(extension: ComponentName): ExtensionListing? {
        for (info in mExtensionManager.getAvailableExtensions()) {
            if (extension == info.componentName()) {
                return info
            }
        }
        return null
    }

    private fun enforceRegisteredCallingCallback(cb: IDataConsumerHostCallback?) {
        if (cb == null || !mRegisteredCallbacks.containsKey(cb.asBinder())) {
            throw SecurityException("Caller should provide a registered callback.")
        }
    }

    private fun enforceEnabledExtensionForCallback(
        cb: IDataConsumerHostCallback?,
        extension: ComponentName
    ) {
        enforceRegisteredCallingCallback(cb)
        val extensions = mRegisteredCallbacks[cb!!.asBinder()]?.mExtensions
        if (extensions != null) {
            for (ext in extensions) {
                if (ext == extension) {
                    return
                }
            }
        }
        throw SecurityException("Extension is not enabled for caller.")
    }

    @Throws(SecurityException::class)
    private fun enforceCallingPermission(permission: String) {
        // We need to check that any of the packages of the caller has
        // the request permission
        val pm = packageManager
        try {
            val packages = pm.getPackagesForUid(Binder.getCallingUid())
            if (packages != null) {
                for (pkg in packages) {
                    val pi = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                    val requestedPermissions = pi.requestedPermissions
                    if (requestedPermissions != null) {
                        for (requestedPermission in requestedPermissions) {
                            if (requestedPermission == permission) {
                                // The caller has the request permission
                                return
                            }
                        }
                    }
                }
            }
        } catch (ex: PackageManager.NameNotFoundException) {
            // Ignore. Package wasn't found
        }
        throw SecurityException("Caller doesn't have the request permission \"$permission\"")
    }

    private fun createCallbackData(uid: Int): CallbackData {
        var hasDashClockSignature = false
        var packageName: String? = null
        val pm = packageManager
        val packages = pm.getPackagesForUid(uid)
        if (!packages.isNullOrEmpty()) {
            try {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(packages[0], PackageManager.GET_SIGNATURES)
                packageName = pi.packageName
                @Suppress("DEPRECATION")
                val signatures = pi.signatures
                if (signatures != null &&
                    signatures.size == 1 &&
                    DashClockSignature.SIGNATURE == signatures[0]
                ) {
                    hasDashClockSignature = true
                }
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }

        val data = CallbackData()
        data.mUid = uid
        data.mPackage = packageName
        data.mHasDashClockSignature = hasDashClockSignature
        return data
    }

    private fun internalRequestUpdateData(
        cb: IDataConsumerHostCallback,
        extensions: List<ComponentName>?
    ) {
        // Recover the updatable extensions for this caller
        val updatableExtensions = ArrayList<ComponentName>()
        val registeredExtensions = mRegisteredCallbacks[cb.asBinder()]?.mExtensions ?: emptyList()
        if (extensions == null) {
            updatableExtensions.addAll(registeredExtensions)
        } else {
            for (extension in extensions) {
                if (registeredExtensions.contains(extension)) {
                    updatableExtensions.add(extension)
                }
            }
        }

        // Request an update of all the extensions in the list
        val lbm = LocalBroadcastManager.getInstance(this)
        for (updatableExtension in updatableExtensions) {
            val intent = Intent(ACTION_EXTENSION_UPDATE_REQUESTED)
            intent.putExtra(EXTRA_COMPONENT_NAME, updatableExtension.flattenToString())
            intent.putExtra(EXTRA_UPDATE_REASON, DashClockExtension.UPDATE_REASON_MANUAL)
            lbm.sendBroadcast(intent)
        }
    }

    private val mExtensionEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_EXTENSION_UPDATE_REQUESTED == intent.action) {
                handleUpdateExtensions(intent)
            }
        }
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(DashClockService::class.java)

        /**
         * Intent action for updating widget views. If [EXTRA_APPWIDGET_ID] is provided, updates
         * only that widget. Otherwise, updates all widgets.
         */
        const val ACTION_UPDATE_WIDGETS = "com.google.android.apps.dashclock.action.UPDATE_WIDGETS"
        const val EXTRA_APPWIDGET_ID = "com.google.android.apps.dashclock.extra.APPWIDGET_ID"

        /**
         * Intent action for telling extensions to update their data. If [EXTRA_COMPONENT_NAME]
         * is provided, updates only that extension. Otherwise, updates all active extensions. Also
         * optional is [EXTRA_UPDATE_REASON] (see [DashClockExtension] for update reasons).
         */
        const val ACTION_UPDATE_EXTENSIONS =
            "com.google.android.apps.dashclock.action.UPDATE_EXTENSIONS"
        const val EXTRA_COMPONENT_NAME = "com.google.android.apps.dashclock.extra.COMPONENT_NAME"
        const val EXTRA_UPDATE_REASON = "com.google.android.apps.dashclock.extra.UPDATE_REASON"

        /**
         * Related to the Read API.
         */
        const val ACTION_EXTENSION_UPDATE_REQUESTED =
            "com.google.android.apps.dashclock.action.EXTENSION_UPDATE_REQUESTED"

        /**
         * Broadcast intent action that's triggered when the set of visible extensions or their
         * data change.
         */
        const val ACTION_EXTENSIONS_CHANGED =
            "com.google.android.apps.dashclock.action.EXTENSIONS_CHANGED"

        /**
         * The amount of time to wait after something has changed before recognizing it as an individual
         * event. Any changes within this time window will be collapsed, and will further delay the
         * handling of the event.
         */
        const val UPDATE_COLLAPSE_TIME_MILLIS = 500

        /**
         * Force all extensions to be readable by external apps.
         */
        const val PREF_FORCE_WORLD_READABLE = "pref_force_world_readable"
    }
}
