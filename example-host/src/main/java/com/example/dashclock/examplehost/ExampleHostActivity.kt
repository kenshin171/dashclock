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

package com.example.dashclock.examplehost

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.apps.dashclock.api.host.DashClockHost
import com.google.android.apps.dashclock.api.host.ExtensionListing

class ExampleHostActivity : Activity(), View.OnClickListener, AdapterView.OnItemClickListener {
    private var mAdapter: ExtensionAdapter? = null
    private lateinit var mOnlyShowingWorldReadableExtensionsView: View
    private var mHost: Host? = null

    private var mMultiplexerDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main)

        val listView = findViewById<ListView>(R.id.extensions)
        listView.onItemClickListener = this

        try {
            val host = Host()
            mHost = host
            val adapter = ExtensionAdapter(this, host, this)
            mAdapter = adapter
            listView.adapter = adapter
            adapter.notifyDataSetChanged()
        } catch (ex: SecurityException) {
            Toast.makeText(this, "Not enough permissions", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Not enough permissions", ex)
        }

        // Listen for new multiplexer package changes
        val filter = IntentFilter()
        filter.addAction(ACTION_MULTIPLEXER_PACKAGE_CHANGED)
        LocalBroadcastManager.getInstance(this).registerReceiver(
            mMultiplexerEventsReceiver, filter
        )

        // Set up force world-readability button
        mOnlyShowingWorldReadableExtensionsView =
            findViewById(R.id.only_showing_world_readable_view)
        mOnlyShowingWorldReadableExtensionsView.setOnClickListener {
            try {
                mHost?.let { startActivity(it.enableForceWorldReadabilityIntent) }
            } catch (e: ActivityNotFoundException) {
            }
        }
    }

    private val mMultiplexerEventsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mHost?.handleMultiplexerPackageChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMultiplexerEventsReceiver)
        mHost?.destroy()
    }

    override fun onClick(v: View) {
        val tag = v.tag
        if (tag is ExtensionListing) {
            mHost?.startSettingsActivityForExtension(tag)
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val info = mAdapter?.getItem(position) ?: return
        if (info.worldReadable()) {
            val comp = info.componentName() ?: return
            val updatableExtensions = arrayListOf(comp)
            mHost?.requestExtensionUpdate(updatableExtensions)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh_all -> {
                mHost?.requestExtensionUpdate(null)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun displayMultiplexerDownloadDialog() {
        if (mMultiplexerDialog?.isShowing == true) {
            mMultiplexerDialog?.dismiss()
        }

        val host = mHost ?: return

        val positiveListener = DialogInterface.OnClickListener { dialog, _ ->
            dialog.dismiss()

            try {
                host.multiplexerDownloadIntent?.let { startActivity(it) }
            } catch (ex: ActivityNotFoundException) {
                // wtf (there isn't a browser)
                Toast.makeText(
                    this@ExampleHostActivity,
                    R.string.multiplexer_dialog_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val builder = AlertDialog.Builder(
            this,
            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        )
            .setIcon(R.drawable.ic_mux_dialog_icon)
            .setNegativeButton(android.R.string.cancel, null)

        val muxDownloadIntent = host.multiplexerDownloadIntent
        if (muxDownloadIntent != null) {
            if (!DashClockHost.isDashClockPresent(this)) {
                // Install Multiplexer app
                builder.setTitle(R.string.multiplexer_dialog_install_title)
                builder.setMessage(R.string.multiplexer_dialog_install_message)
                builder.setPositiveButton(
                    R.string.multiplexer_dialog_install_positive_button,
                    positiveListener
                )
            } else {
                // Update DashClock
                builder.setTitle(R.string.multiplexer_dialog_upgrade_title)
                builder.setMessage(R.string.multiplexer_dialog_upgrade_message)
                builder.setPositiveButton(
                    R.string.multiplexer_dialog_upgrade_positive_button,
                    positiveListener
                )
            }
        } else {
            // Not available update
            val pkgs = DashClockHost.getOtherAppsWithReadDataExtensionsPermission(this)
            val appNames = packagesNameListToAppNameString(this, pkgs)
            builder.setTitle(R.string.multiplexer_dialog_other_title)
            builder.setMessage(getString(R.string.multiplexer_dialog_other_message, appNames))
        }
        mMultiplexerDialog = builder.create().also { it.show() }
    }

    class MultiplexerPackageChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val notifyIntent = Intent(ACTION_MULTIPLEXER_PACKAGE_CHANGED)
            LocalBroadcastManager.getInstance(context).sendBroadcast(notifyIntent)
        }
    }

    private inner class Host : DashClockHost(this@ExampleHostActivity) {
        override fun onAvailableExtensionsChanged() {
            super.onAvailableExtensionsChanged()

            val availableExtensions = getAvailableExtensions(
                !areNonWorldReadableExtensionsVisible()
            )
            val worldReadableExtensions = HashSet<ComponentName>()
            for (info in availableExtensions) {
                info.componentName()?.let { worldReadableExtensions.add(it) }
            }

            mAdapter?.clear()
            mAdapter?.addAll(availableExtensions)
            mHost?.listenTo(worldReadableExtensions)
            mOnlyShowingWorldReadableExtensionsView.visibility =
                if (areNonWorldReadableExtensionsVisible()) View.GONE else View.VISIBLE
        }

        override fun onExtensionDataChanged(extension: ComponentName) {
            super.onExtensionDataChanged(extension)
            Log.d(TAG, "Extension data changed for " + extension.flattenToString())
            mAdapter?.notifyDataSetChanged()
        }

        override fun onMultiplexerChangedDetected(available: Boolean) {
            // Before to notify the implementation the multiplexer change, hide
            // the multiplexer dialog if is showing, so the implementation can
            // decide again what is the better method to advise the user
            if (mMultiplexerDialog?.isShowing == true) {
                mMultiplexerDialog?.dismiss()
            }

            if (!available) {
                // Not available clear the dataset
                mAdapter?.clear()

                // Use the provided implementation
                displayMultiplexerDownloadDialog()
            }
        }
    }

    private class ExtensionAdapter(
        context: Context,
        private val mHost: DashClockHost,
        private val mListener: View.OnClickListener
    ) : ArrayAdapter<ExtensionListing>(context, 0) {
        private val mInflater: LayoutInflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val holder: ViewHolder

            if (convertView == null) {
                view = mInflater.inflate(R.layout.extension_item, parent, false)
                holder = ViewHolder(
                    icon = view.findViewById(R.id.icon),
                    title = view.findViewById(R.id.title),
                    description = view.findViewById(R.id.description),
                    status = view.findViewById(R.id.status),
                    expandedTitle = view.findViewById(R.id.expandedTitle),
                    expandedBody = view.findViewById(R.id.expandedBody),
                    worldReadable = view.findViewById(R.id.world_readable),
                    settings = view.findViewById(R.id.settings)
                )
                holder.settings.setOnClickListener(mListener)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val info = getItem(position)!!
            holder.title.text = info.title()
            holder.description.text = info.description()

            try {
                if (info.icon() != 0 && info.componentName() != null) {
                    val res = context.createPackageContext(
                        info.componentName()!!.packageName, 0
                    ).resources
                    @Suppress("DEPRECATION")
                    val dw = res.getDrawable(info.icon())
                    @Suppress("DEPRECATION")
                    dw.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
                    holder.icon.setImageDrawable(dw)
                } else {
                    holder.icon.setImageDrawable(null)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                holder.icon.setImageDrawable(null)
            }

            val hasSettings = info.settingsActivity() != null
            holder.settings.visibility = if (hasSettings) View.VISIBLE else View.GONE
            holder.settings.tag = info

            holder.worldReadable.visibility = if (info.worldReadable()) View.VISIBLE else View.GONE

            val data = info.componentName()?.let { mHost.getExtensionData(it) }
            if (data != null) {
                if (!data.visible()) {
                    val dataText = context.getString(R.string.data_invalid)
                    holder.status.text = dataText
                    holder.status.visibility =
                        if (TextUtils.isEmpty(dataText)) View.GONE else View.VISIBLE
                    holder.expandedTitle.visibility = View.GONE
                    holder.expandedBody.visibility = View.GONE
                } else {
                    holder.status.text = data.status()
                    holder.status.visibility =
                        if (TextUtils.isEmpty(data.status())) View.GONE else View.VISIBLE
                    holder.expandedTitle.text = data.expandedTitle()
                    holder.expandedTitle.visibility =
                        if (TextUtils.isEmpty(data.expandedTitle())) View.GONE else View.VISIBLE
                    holder.expandedBody.text = data.expandedBody()
                    holder.expandedBody.visibility =
                        if (TextUtils.isEmpty(data.expandedBody())) View.GONE else View.VISIBLE
                }
            }

            return view
        }

        private class ViewHolder(
            val icon: ImageView,
            val title: TextView,
            val description: TextView,
            val status: TextView,
            val expandedTitle: TextView,
            val expandedBody: TextView,
            val worldReadable: ImageView,
            val settings: ImageView
        )
    }

    companion object {
        private const val TAG = "ExampleHost"

        private const val ACTION_MULTIPLEXER_PACKAGE_CHANGED =
            "com.example.dashclock.examplehost.MULTIPLEXER_PACKAGE_CHANGED"

        private fun packagesNameListToAppNameString(context: Context, pkgs: List<String>): String {
            val sb = StringBuilder()
            val pm = context.packageManager
            for (pkg in pkgs) {
                sb.append("\n\n\t")
                try {
                    val pi = pm.getApplicationInfo(pkg, 0)
                    val name = pi.loadLabel(pm)
                    sb.append(name ?: pkg)
                } catch (ex: PackageManager.NameNotFoundException) {
                    sb.append(pkg)
                }
            }
            return sb.toString()
        }
    }
}
