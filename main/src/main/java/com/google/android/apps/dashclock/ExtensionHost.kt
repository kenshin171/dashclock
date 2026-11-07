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
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.os.SystemClock
import android.text.TextUtils
import android.util.Pair
import android.util.SparseArray
import androidx.core.content.ContextCompat
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.api.internal.IExtension
import com.google.android.apps.dashclock.api.internal.IExtensionHost
import java.util.LinkedList
import java.util.Queue

/**
 * The primary local-process endpoint that deals with extensions. Instances of this class are in
 * charge of maintaining a [ServiceConnection] with connected extensions. There should
 * only be one instance of this class in the app.
 *
 * This class is intended to be used as part of a containing service. Make sure to call
 * [destroy] in the service's [android.app.Service.onDestroy].
 */
class ExtensionHost(context: Service) {
    // TODO: this class badly needs inline docs

    private val mContext: Context = context
    private val mClientThreadHandler = Handler(Looper.getMainLooper())

    private val mExtensionManager: ExtensionManager = ExtensionManager.getInstance(context)

    private val mExtensionConnections: MutableMap<ComponentName, Connection> = HashMap()

    private val mExtensionsToUpdateWhenScreenOn: MutableSet<ComponentName> = HashSet()
    private var mScreenOnReceiverRegistered = false

    @Volatile
    private var mAsyncLooper: Looper

    @Volatile
    private var mAsyncHandler: Handler

    private val mChangeListener = object : ExtensionManager.OnChangeListener {
        override fun onExtensionsChanged(sourceExtension: ComponentName?) {
            if (sourceExtension != null) {
                // If the extension change is a result of a single extension, don't do anything,
                // since we're only interested in events triggered by the system overall (e.g.
                // extensions added or removed).
                return
            }
            LOGD(TAG, "onExtensionsChanged; calling establishAndDestroyConnections.")
            establishAndDestroyConnections(mExtensionManager.getActiveExtensionNames())
        }
    }

    init {
        mExtensionManager.addOnChangeListener(mChangeListener)

        val thread = HandlerThread("ExtensionHost")
        thread.start()
        mAsyncLooper = thread.looper
        mAsyncHandler = Handler(mAsyncLooper)

        mChangeListener.onExtensionsChanged(null)
        mExtensionManager.cleanupExtensions()

        LOGD(TAG, "ExtensionHost initialized.")
    }

    fun destroy() {
        mExtensionManager.removeOnChangeListener(mChangeListener)
        if (mScreenOnReceiverRegistered) {
            mContext.unregisterReceiver(mScreenOnReceiver)
            mScreenOnReceiverRegistered = false
        }
        establishAndDestroyConnections(HashSet())
        mAsyncLooper.quit()
    }

    private fun establishAndDestroyConnections(newExtensionNames: Set<ComponentName>) {
        // Get the list of active extensions
        val activeSet = HashSet(newExtensionNames)

        // Get the list of connected extensions
        val connectedSet = HashSet(mExtensionConnections.keys)

        for (cn in activeSet) {
            if (connectedSet.contains(cn)) {
                continue
            }

            // Bind anything not currently connected (this is the initial connection
            // to the now-added extension)
            val conn = createConnection(cn, false)
            if (conn != null) {
                mExtensionConnections[cn] = conn
            }
        }

        // Remove active items from the connected set, leaving only newly-inactive items
        // to be disconnected below.
        connectedSet.removeAll(activeSet)

        for (cn in connectedSet) {
            val conn = mExtensionConnections[cn]

            // Unbind the now-disconnected extension
            if (conn != null) {
                destroyConnection(conn)
            }
            mExtensionConnections.remove(cn)
        }
    }

    private fun createConnection(cn: ComponentName, isReconnect: Boolean): Connection? {
        LOGD(TAG, "createConnection for $cn; isReconnect=$isReconnect")

        val conn = Connection()
        conn.componentName = cn
        conn.contentObserver = object : ContentObserver(mClientThreadHandler) {
            override fun onChange(selfChange: Boolean) {
                execute(
                    conn.componentName!!,
                    UPDATE_OPERATIONS.get(DashClockExtension.UPDATE_REASON_CONTENT_CHANGED),
                    UPDATE_COLLAPSE_TIME_MILLIS,
                    DashClockExtension.UPDATE_REASON_CONTENT_CHANGED
                )
            }
        }
        conn.hostInterface = makeHostInterface(conn)
        conn.serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                conn.ready = true
                conn.binder = IExtension.Stub.asInterface(iBinder)

                // Initialize the service
                execute(
                    conn,
                    object : Operation {
                        @Throws(RemoteException::class)
                        override fun run(extension: IExtension) {
                            // Note that this is protected from ANRs since it runs in the
                            // AsyncHandler thread. Also, since this is a 'oneway' call,
                            // when used with remote extensions, this call does not block.
                            try {
                                extension.onInitialize(conn.hostInterface, isReconnect)
                            } catch (e: SecurityException) {
                                LOGE(TAG, "Error initializing extension $componentName", e)
                            }
                        }
                    },
                    0,
                    null
                )

                if (!isReconnect) {
                    execute(
                        conn.componentName!!,
                        UPDATE_OPERATIONS.get(DashClockExtension.UPDATE_REASON_INITIAL),
                        0,
                        null
                    )
                }

                // Execute operations that were deferred until the service was available.
                // TODO: handle service disruptions that occur here
                synchronized(conn.deferredOps) {
                    if (conn.ready) {
                        val processedCollapsedTokens = HashSet<Any>()
                        val it = conn.deferredOps.iterator()
                        while (it.hasNext()) {
                            val op = it.next()
                            val token = op.first
                            if (token != null) {
                                if (processedCollapsedTokens.contains(token)) {
                                    // An operation with this collapse token has already been
                                    // processed; skip this one.
                                    continue
                                }

                                processedCollapsedTokens.add(token)
                            }
                            execute(conn, op.second, 0, null)
                            it.remove()
                        }
                    }
                }
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                conn.serviceConnection = null
                conn.binder = null
                conn.ready = false
                mClientThreadHandler.post { mExtensionConnections.remove(componentName) }
            }
        }

        try {
            if (!mContext.bindService(
                    Intent().setComponent(cn),
                    conn.serviceConnection!!,
                    Context.BIND_AUTO_CREATE
                )
            ) {
                LOGE(TAG, "Error binding to extension " + cn.flattenToShortString())
                return null
            }
        } catch (e: SecurityException) {
            LOGE(TAG, "Error binding to extension " + cn.flattenToShortString(), e)
            return null
        }

        return conn
    }

    private fun makeHostInterface(conn: Connection): IExtensionHost {
        return object : IExtensionHost.Stub() {
            @Throws(RemoteException::class)
            override fun publishUpdate(data: ExtensionData?) {
                val nonNullData = data ?: ExtensionData()

                // TODO: this needs to be thread-safe
                LOGD(TAG, "publishUpdate received for extension ${conn.componentName}")
                mExtensionManager.updateExtensionData(conn.componentName!!, nonNullData)
            }

            @Throws(RemoteException::class)
            override fun addWatchContentUris(contentUris: Array<String>?) {
                if (!contentUris.isNullOrEmpty() && conn.contentObserver != null) {
                    val resolver = mContext.contentResolver
                    for (uri in contentUris) {
                        if (TextUtils.isEmpty(uri)) {
                            continue
                        }

                        resolver.registerContentObserver(
                            Uri.parse(uri),
                            true,
                            conn.contentObserver!!
                        )
                    }
                }
            }

            @Throws(RemoteException::class)
            override fun removeAllWatchContentUris() {
                val resolver = mContext.contentResolver
                conn.contentObserver?.let { resolver.unregisterContentObserver(it) }
            }

            @Throws(RemoteException::class)
            override fun setUpdateWhenScreenOn(updateWhenScreenOn: Boolean) {
                synchronized(mExtensionsToUpdateWhenScreenOn) {
                    if (updateWhenScreenOn) {
                        if (mExtensionsToUpdateWhenScreenOn.isEmpty()) {
                            val filter = IntentFilter()
                            filter.addAction(Intent.ACTION_SCREEN_ON)
                            ContextCompat.registerReceiver(
                                mContext,
                                mScreenOnReceiver,
                                filter,
                                ContextCompat.RECEIVER_EXPORTED
                            )
                            mScreenOnReceiverRegistered = true
                        }

                        mExtensionsToUpdateWhenScreenOn.add(conn.componentName!!)
                    } else {
                        mExtensionsToUpdateWhenScreenOn.remove(conn.componentName)

                        if (mExtensionsToUpdateWhenScreenOn.isEmpty()) {
                            mContext.unregisterReceiver(mScreenOnReceiver)
                            mScreenOnReceiverRegistered = false
                        }
                    }
                }
            }
        }
    }

    private fun destroyConnection(conn: Connection) {
        if (conn.contentObserver != null) {
            mContext.contentResolver.unregisterContentObserver(conn.contentObserver!!)
            conn.contentObserver = null
        }

        conn.binder = null
        if (conn.serviceConnection != null) {
            mContext.unbindService(conn.serviceConnection!!)
            conn.serviceConnection = null
        }
    }

    private fun execute(
        conn: Connection,
        operation: Operation,
        collapseDelayMillis: Int,
        collapseToken: Any?
    ) {
        val collapseTokenForConn: Any? = if (collapseDelayMillis > 0 && collapseToken != null) {
            Pair(conn.componentName, collapseToken)
        } else {
            null
        }

        val runnable = Runnable {
            try {
                val binder = conn.binder ?: throw RemoteException("Binder is unavailable.")
                operation.run(binder)
            } catch (e: RemoteException) {
                LOGE(
                    TAG,
                    "Couldn't execute operation; scheduling for retry upon service reconnection.",
                    e
                )
                // TODO: exponential backoff for retrying the same operation, or fail after
                // n attempts (in case the remote service consistently crashes when
                // executing this operation)
                synchronized(conn.deferredOps) {
                    conn.deferredOps.add(Pair(collapseTokenForConn, operation))
                }
            }
        }

        if (conn.ready) {
            if (collapseTokenForConn != null) {
                mAsyncHandler.removeCallbacksAndMessages(collapseTokenForConn)
            }

            if (collapseDelayMillis > 0) {
                mAsyncHandler.postAtTime(
                    runnable,
                    collapseTokenForConn,
                    SystemClock.uptimeMillis() + collapseDelayMillis
                )
            } else {
                mAsyncHandler.post(runnable)
            }
        } else {
            mAsyncHandler.post {
                synchronized(conn.deferredOps) {
                    conn.deferredOps.add(Pair(collapseTokenForConn, operation))
                }
            }
        }
    }

    fun execute(
        cn: ComponentName,
        operation: Operation,
        collapseDelayMillis: Int,
        collapseToken: Any?
    ) {
        var conn = mExtensionConnections[cn]
        if (conn == null) {
            conn = createConnection(cn, true)
            if (conn != null) {
                mExtensionConnections[cn] = conn
            } else {
                LOGE(
                    TAG,
                    "Couldn't connect to extension to perform operation; operation canceled."
                )
                return
            }
        }

        execute(conn, operation, collapseDelayMillis, collapseToken)
    }

    private val mScreenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(mExtensionsToUpdateWhenScreenOn) {
                for (cn in mExtensionsToUpdateWhenScreenOn) {
                    execute(
                        cn,
                        UPDATE_OPERATIONS.get(DashClockExtension.UPDATE_REASON_SCREEN_ON),
                        0,
                        null
                    )
                }
            }
        }
    }

    /**
     * Will be run on a worker thread.
     */
    interface Operation {
        @Throws(RemoteException::class)
        fun run(extension: IExtension)
    }

    private class Connection {
        var ready = false
        var componentName: ComponentName? = null
        var serviceConnection: ServiceConnection? = null
        var binder: IExtension? = null
        var hostInterface: IExtensionHost? = null
        var contentObserver: ContentObserver? = null

        /**
         * Only access on the async thread. The pair is (collapse token, operation)
         */
        val deferredOps: Queue<Pair<Any?, Operation>> = LinkedList()
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(ExtensionHost::class.java)

        private const val CURRENT_EXTENSION_PROTOCOL_VERSION = 2

        /**
         * The amount of time to wait after something has changed before recognizing it as an individual
         * event. Any changes within this time window will be collapsed, and will further delay the
         * handling of the event.
         */
        const val UPDATE_COLLAPSE_TIME_MILLIS = 500

        @JvmField
        val UPDATE_OPERATIONS = SparseArray<Operation>()

        init {
            createUpdateOperation(DashClockExtension.UPDATE_REASON_UNKNOWN)
            createUpdateOperation(DashClockExtension.UPDATE_REASON_INITIAL)
            createUpdateOperation(DashClockExtension.UPDATE_REASON_PERIODIC)
            createUpdateOperation(DashClockExtension.UPDATE_REASON_SETTINGS_CHANGED)
            createUpdateOperation(DashClockExtension.UPDATE_REASON_CONTENT_CHANGED)
            createUpdateOperation(DashClockExtension.UPDATE_REASON_SCREEN_ON)
            createUpdateOperation(DashClockExtension.UPDATE_REASON_MANUAL)
        }

        private fun createUpdateOperation(reason: Int) {
            UPDATE_OPERATIONS.put(
                reason,
                object : Operation {
                    @Throws(RemoteException::class)
                    override fun run(extension: IExtension) {
                        // Note that this is protected from ANRs since it runs in the AsyncHandler thread.
                        // Also, since this is a 'oneway' call, when used with remote extensions, this call
                        // does not block.
                        extension.onUpdate(reason)
                    }
                }
            )
        }

        @JvmStatic
        fun supportsProtocolVersion(protocolVersion: Int): Boolean {
            return protocolVersion in 1..CURRENT_EXTENSION_PROTOCOL_VERSION
        }
    }
}
