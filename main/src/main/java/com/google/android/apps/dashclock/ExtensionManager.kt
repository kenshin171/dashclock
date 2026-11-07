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

import android.app.backup.BackupManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.text.TextUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.LogUtils.LOGE
import com.google.android.apps.dashclock.LogUtils.LOGW
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData
import com.google.android.apps.dashclock.api.host.ExtensionListing
import com.google.android.apps.dashclock.gmail.GmailExtension
import com.google.android.apps.dashclock.nextalarm.NextAlarmExtension
import com.google.android.apps.dashclock.weather.WeatherExtension
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Arrays

/**
 * A singleton class in charge of extension registration, activation (change in user-specified
 * 'active' extensions), and data caching.
 */
class ExtensionManager private constructor(context: Context) {
    private val mApplicationContext: Context = context.applicationContext

    private val mInternalActiveExtensions: MutableList<ComponentName> = ArrayList()
    private val mActiveExtensions: MutableSet<ExtensionWithData> = HashSet()

    private val mExtensionInfoMap: MutableMap<ComponentName, ExtensionWithData> = HashMap()
    private val mOnChangeListeners: MutableList<OnChangeListener> = ArrayList()

    private val mValuesPreferences: SharedPreferences =
        mApplicationContext.getSharedPreferences("extension_data", 0)
    private val mMainThreadHandler = Handler(Looper.getMainLooper())

    private val mDefaultPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(mApplicationContext)

    init {
        loadInternalActiveExtensionList()
    }

    /**
     * De-activates active extensions that are unsupported or are no longer installed.
     */
    fun cleanupExtensions(): Boolean {
        val availableExtensions = HashSet<ComponentName>()
        for (info in getAvailableExtensions()) {
            // Ensure the extension protocol version is supported. If it isn't, don't allow its use.
            if (!info.compatible()) {
                LOGW(
                    TAG,
                    "Extension '${info.title()}' using unsupported protocol version ${info.protocolVersion()}."
                )
                continue
            }
            info.componentName()?.let { availableExtensions.add(it) }
        }

        var cleanupRequired = false
        val newActiveExtensions = HashSet<ComponentName>()

        synchronized(mActiveExtensions) {
            for (ewd in mActiveExtensions) {
                val cn = ewd.listing?.componentName()
                if (cn != null && availableExtensions.contains(cn)) {
                    newActiveExtensions.add(cn)
                } else {
                    cleanupRequired = true
                }
            }
        }

        if (cleanupRequired) {
            setActiveExtensions(newActiveExtensions)
            return true
        }

        return false
    }

    /**
     * Replaces the set of active extensions with the given list.
     */
    fun setActiveExtensions(extensions: Set<ComponentName>) {
        // Join external and internal extensions
        val allExtensions = HashSet(getInternalActiveExtensionNames())
        for (cn in extensions) {
            if (!allExtensions.contains(cn)) {
                allExtensions.add(cn)
            }
        }

        val infos = HashMap<ComponentName, ExtensionListing>()
        for (info in getAvailableExtensions()) {
            info.componentName()?.let { infos[it] = info }
        }

        val activeExtensionNames = getActiveExtensionNames()
        if (activeExtensionNames == allExtensions) {
            LOGD(TAG, "No change to list of active extensions.")
            return
        }

        // Clear cached data for any no-longer-active extensions.
        for (cn in activeExtensionNames) {
            if (!allExtensions.contains(cn)) {
                destroyExtensionData(cn)
            }
        }

        // Set the new list of active extensions, loading cached data if necessary.
        val newActiveExtensions = ArrayList<ExtensionWithData>()

        for (cn in allExtensions) {
            val existing = mExtensionInfoMap[cn]
            if (existing != null) {
                newActiveExtensions.add(existing)
            } else {
                val ewd = ExtensionWithData()
                ewd.listing = infos[cn]
                if (ewd.listing == null) {
                    ewd.listing = ExtensionListing().apply {
                        componentName(cn)
                    }
                }
                ewd.latestData = deserializeExtensionData(ewd.listing!!.componentName()!!)
                newActiveExtensions.add(ewd)
            }
        }

        mExtensionInfoMap.clear()
        for (ewd in newActiveExtensions) {
            mExtensionInfoMap[ewd.listing!!.componentName()!!] = ewd
        }

        synchronized(mActiveExtensions) {
            mActiveExtensions.clear()
            mActiveExtensions.addAll(newActiveExtensions)
        }

        LOGD(TAG, "List of active extensions has changed.")
        notifyOnChangeListeners(null)
    }

    /**
     * Updates and caches the user-visible data for a given extension.
     */
    fun updateExtensionData(cn: ComponentName, data: ExtensionData): Boolean {
        data.clean()

        val ewd = mExtensionInfoMap[cn]
        if (ewd != null && !ExtensionData.equals(ewd.latestData, data)) {
            ewd.latestData = data
            val componentName = ewd.listing!!.componentName()!!
            serializeExtensionData(componentName, data)
            notifyOnChangeListeners(componentName)
            return true
        }
        return false
    }

    private fun deserializeExtensionData(componentName: ComponentName): ExtensionData {
        val extensionData = ExtensionData()
        val valStr = mValuesPreferences.getString(componentName.flattenToString(), "")
        if (!TextUtils.isEmpty(valStr)) {
            try {
                extensionData.deserialize(JSONTokener(valStr).nextValue() as JSONObject)
            } catch (e: JSONException) {
                LOGE(TAG, "Error loading extension data cache for $componentName.", e)
            }
        }
        return extensionData
    }

    private fun serializeExtensionData(
        componentName: ComponentName,
        extensionData: ExtensionData
    ) {
        try {
            mValuesPreferences.edit()
                .putString(
                    componentName.flattenToString(),
                    extensionData.serialize().toString()
                )
                .apply()
        } catch (e: JSONException) {
            LOGE(TAG, "Error storing extension data cache for $componentName.", e)
        }
    }

    private fun destroyExtensionData(componentName: ComponentName) {
        mValuesPreferences.edit()
            .remove(componentName.flattenToString())
            .apply()
    }

    fun getExtensionWithData(extension: ComponentName): ExtensionWithData? {
        return mExtensionInfoMap[extension]
    }

    fun getActiveExtensionsWithData(): List<ExtensionWithData> {
        val activeExtensions: ArrayList<ExtensionWithData>
        synchronized(mActiveExtensions) {
            activeExtensions = ArrayList(mActiveExtensions)
        }
        return activeExtensions
    }

    fun getInternalActiveExtensionsWithData(): List<ExtensionWithData> {
        // Extract the data from the all active extension cache
        val internalActiveExtensions = getInternalActiveExtensionNames()
        val activeExtensions = ArrayList<ExtensionWithData?>(
            Arrays.asList(*arrayOfNulls<ExtensionWithData>(internalActiveExtensions.size))
        )
        synchronized(mActiveExtensions) {
            for (ewd in mActiveExtensions) {
                val pos = internalActiveExtensions.indexOf(ewd.listing?.componentName())
                if (pos >= 0) {
                    activeExtensions[pos] = ewd
                }
            }
        }

        // Clean any null/unset data
        return activeExtensions.filterNotNull()
    }

    fun getActiveExtensionNames(): Set<ComponentName> {
        val list = HashSet<ComponentName>()
        for (ci in mActiveExtensions) {
            ci.listing?.componentName()?.let { list.add(it) }
        }
        return list
    }

    fun getInternalActiveExtensionNames(): List<ComponentName> {
        return ArrayList(mInternalActiveExtensions)
    }

    /**
     * Returns a listing of all available (installed) extensions, including those that aren't
     * world-readable.
     */
    fun getAvailableExtensions(): List<ExtensionListing> {
        val availableExtensions = ArrayList<ExtensionListing>()
        val pm = mApplicationContext.packageManager
        @Suppress("DEPRECATION")
        val resolveInfos = pm.queryIntentServices(
            Intent(DashClockExtension.ACTION_EXTENSION),
            PackageManager.GET_META_DATA
        )
        for (resolveInfo in resolveInfos) {
            val info = ExtensionListing()
            info.componentName(
                ComponentName(
                    resolveInfo.serviceInfo.packageName,
                    resolveInfo.serviceInfo.name
                )
            )
            info.title(resolveInfo.loadLabel(pm).toString())
            val metaData = resolveInfo.serviceInfo.metaData
            if (metaData != null) {
                info.compatible(
                    ExtensionHost.supportsProtocolVersion(metaData.getInt("protocolVersion"))
                )
                info.worldReadable(metaData.getBoolean("worldReadable", false))
                info.description(metaData.getString("description"))
                val settingsActivity = metaData.getString("settingsActivity")
                if (!TextUtils.isEmpty(settingsActivity)) {
                    info.settingsActivity(
                        ComponentName.unflattenFromString(
                            "${resolveInfo.serviceInfo.packageName}/$settingsActivity"
                        )
                    )
                }
            }

            info.icon(resolveInfo.iconResource)
            availableExtensions.add(info)
        }

        return availableExtensions
    }

    private fun loadInternalActiveExtensionList() {
        val activeExtensions = ArrayList<ComponentName>()
        val extensions = if (mDefaultPreferences.contains(PREF_ACTIVE_EXTENSIONS)) {
            mDefaultPreferences.getString(PREF_ACTIVE_EXTENSIONS, "") ?: ""
        } else {
            createDefaultExtensionList()
        }
        val componentNameStrings = extensions.split(",".toRegex()).dropLastWhile { it.isEmpty() }
        for (componentNameString in componentNameStrings) {
            if (TextUtils.isEmpty(componentNameString)) {
                continue
            }
            val cn = ComponentName.unflattenFromString(componentNameString)
            if (cn != null) {
                activeExtensions.add(cn)
            }
        }
        setInternalActiveExtensions(activeExtensions)
    }

    private fun createDefaultExtensionList(): String {
        val sb = StringBuilder()

        for (cls in DEFAULT_EXTENSIONS) {
            if (sb.isNotEmpty()) {
                sb.append(",")
            }
            sb.append(ComponentName(mApplicationContext, cls).flattenToString())
        }

        return sb.toString()
    }

    /**
     * Replaces the set of active extensions with the given list.
     */
    fun setInternalActiveExtensions(extensions: List<ComponentName>) {
        val sb = StringBuilder()

        for (extension in extensions) {
            if (sb.isNotEmpty()) {
                sb.append(",")
            }
            sb.append(extension.flattenToString())
        }

        mDefaultPreferences.edit()
            .putString(PREF_ACTIVE_EXTENSIONS, sb.toString())
            .apply()
        BackupManager(mApplicationContext).dataChanged()

        mInternalActiveExtensions.clear()
        mInternalActiveExtensions.addAll(extensions)
        setActiveExtensions(getActiveExtensionNames())
    }

    fun getVisibleExtensionsWithData(): List<ExtensionWithData> {
        val visibleExtensions = ArrayList<ExtensionWithData>()
        for (ewd in getInternalActiveExtensionsWithData()) {
            if (ewd.latestData?.visible() == true) {
                visibleExtensions.add(ewd)
            }
        }
        return visibleExtensions
    }

    /**
     * Registers a listener to be triggered when either the list of active extensions changes or an
     * extension's data changes.
     */
    fun addOnChangeListener(onChangeListener: OnChangeListener) {
        mOnChangeListeners.add(onChangeListener)
    }

    /**
     * Removes a listener previously registered with [addOnChangeListener].
     */
    fun removeOnChangeListener(onChangeListener: OnChangeListener) {
        mOnChangeListeners.remove(onChangeListener)
    }

    private fun notifyOnChangeListeners(sourceExtension: ComponentName?) {
        mMainThreadHandler.post {
            for (listener in mOnChangeListeners) {
                listener.onExtensionsChanged(sourceExtension)
            }
        }
    }

    interface OnChangeListener {
        /**
         * @param sourceExtension null if not related to any specific extension (e.g. list of
         * extensions has changed).
         */
        fun onExtensionsChanged(sourceExtension: ComponentName?)
    }

    class ExtensionWithData {
        @JvmField
        var listing: ExtensionListing? = null

        @JvmField
        var latestData: ExtensionData? = null
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(ExtensionManager::class.java)

        private var sInstance: ExtensionManager? = null
        private const val PREF_ACTIVE_EXTENSIONS = "active_extensions"

        private val DEFAULT_EXTENSIONS = arrayOf<Class<*>>(
            WeatherExtension::class.java,
            GmailExtension::class.java,
            NextAlarmExtension::class.java
        )

        @JvmStatic
        fun getInstance(context: Context): ExtensionManager {
            if (sInstance == null) {
                sInstance = ExtensionManager(context)
            }
            return sInstance!!
        }
    }
}
