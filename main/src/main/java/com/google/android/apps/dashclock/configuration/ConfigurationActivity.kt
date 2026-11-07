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

import android.app.Fragment
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.apps.dashclock.DashClockService
import com.google.android.apps.dashclock.HelpUtils
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGD
import com.google.android.apps.dashclock.RecentTasksStyler
import com.google.android.apps.dashclock.Utils
import com.google.android.apps.dashclock.api.DashClockExtension
import net.nurik.roman.dashclock.BuildConfig
import net.nurik.roman.dashclock.R

/**
 * The primary widget configuration activity. Serves as an interstitial when adding the widget, and
 * shows when pressing the settings button in the widget.
 */
class ConfigurationActivity : AppCompatActivity() {
    // only used when adding a new widget
    private var mNewWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var mStartSection = START_SECTION_EXTENSIONS

    private var mBackgroundCleared = false

    private lateinit var mAppBar: Toolbar

    public override fun onCreate(savedInstanceState: Bundle?) {
        setupFauxDialog()
        RecentTasksStyler.styleRecentTasksEntry(this)
        super.onCreate(savedInstanceState)

        Utils.enableDisablePhoneOnlyExtensions(this)

        val intent = intent
        if (intent != null) {
            if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
                @Suppress("DEPRECATION")
                val icon = Intent.ShortcutIconResource().apply {
                    packageName = this@ConfigurationActivity.packageName
                    resourceName = resources.getResourceName(R.mipmap.ic_launcher)
                }
                @Suppress("DEPRECATION")
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.title_configure))
                        .putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon)
                        .putExtra(
                            Intent.EXTRA_SHORTCUT_INTENT,
                            Intent.makeMainActivity(
                                ComponentName(this, ConfigurationActivity::class.java)
                            )
                        )
                )
                finish()
            }

            mStartSection = intent.getIntExtra(EXTRA_START_SECTION, 0)
        }

        setContentView(R.layout.activity_configure)

        if (intent != null && AppWidgetManager.ACTION_APPWIDGET_CONFIGURE == intent.action) {
            mNewWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId)
            // See http://code.google.com/p/android/issues/detail?id=2539
            setResult(
                RESULT_CANCELED,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId)
            )
        }

        // Set up UI widgets
        setupActionBar()
    }

    private fun setupFauxDialog() {
        // Check if this should be a dialog
        val tv = TypedValue()
        if (!theme.resolveAttribute(R.attr.isDialog, tv, true) || tv.data == 0) {
            return
        }

        // Should be a dialog; set up the window parameters.
        val dm = resources.displayMetrics

        val params = window.attributes
        params.width = resources.getDimensionPixelSize(R.dimen.configure_dialog_width)
        params.height = Math.min(
            resources.getDimensionPixelSize(R.dimen.configure_dialog_max_height),
            dm.heightPixels * 3 / 4
        )
        params.alpha = 1.0f
        params.dimAmount = 0.5f
        window.attributes = params
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        showWallpaper()
    }

    fun showWallpaper() {
        if (!mBackgroundCleared) {
            // We initially show a background so that the activity transition (zoom-up animation
            // in Android 4.1) doesn't show the system wallpaper (which is needed in the
            // appearance configuration fragment). Upon user interaction (i.e. once we know the
            // activity transition has finished), clear the background so that the system wallpaper
            // can be seen when the appearance configuration fragment is shown.
            findViewById<View>(R.id.content_container).background = null
            mBackgroundCleared = true
        }
    }

    private fun setupActionBar() {
        mAppBar = findViewById<View>(R.id.app_bar) as Toolbar

        // Done button
        mAppBar.setNavigationIcon(R.drawable.ic_action_done)
        mAppBar.setNavigationContentDescription(R.string.done)
        mAppBar.setNavigationOnClickListener {
            // "Done"
            if (mNewWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mNewWidgetId)
                )
            }

            finish()
        }

        // Spinner
        val inflater = LayoutInflater.from(this)
        val sectionSpinner = findViewById<View>(R.id.app_bar_section_spinner) as Spinner
        sectionSpinner.adapter = object : BaseAdapter() {
            override fun getCount(): Int {
                return SECTION_LABELS.size
            }

            override fun getItem(position: Int): Any {
                return SECTION_LABELS[position]
            }

            override fun getItemId(position: Int): Long {
                return (position + 1).toLong()
            }

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: inflater.inflate(
                    R.layout.list_item_configure_ab_spinner,
                    parent,
                    false
                )
                (view.findViewById<View>(android.R.id.text1) as TextView).text =
                    getString(SECTION_LABELS[position])
                return view
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val view = convertView ?: inflater.inflate(
                    R.layout.list_item_configure_ab_spinner_dropdown,
                    parent,
                    false
                )
                (view.findViewById<View>(android.R.id.text1) as TextView).text =
                    getString(SECTION_LABELS[position])
                return view
            }
        }

        sectionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                spinner: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val fragmentClass = SECTION_FRAGMENTS[position]
                val currentFragment = fragmentManager.findFragmentById(R.id.content_container)
                if (currentFragment != null && fragmentClass == currentFragment.javaClass) {
                    return
                }

                try {
                    val newFragment = fragmentClass.newInstance()
                    fragmentManager.beginTransaction()
                        .replace(R.id.content_container, newFragment)
                        .commitAllowingStateLoss()
                } catch (e: InstantiationException) {
                    throw RuntimeException(e)
                } catch (e: IllegalAccessException) {
                    throw RuntimeException(e)
                }
            }

            override fun onNothingSelected(spinner: AdapterView<*>?) {
            }
        }

        sectionSpinner.setSelection(mStartSection)

        // Actions
        mAppBar.inflateMenu(R.menu.configure_overflow)
        val menu = mAppBar.menu
        if (!BuildConfig.DEBUG) {
            menu.findItem(R.id.action_send_logs)?.isVisible = false
        }

        mAppBar.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_get_more_extensions -> {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(
                                "http://play.google.com/store/search?q=DashClock+Extension&c=apps"
                            )
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    return@OnMenuItemClickListener true
                }

                R.id.action_send_logs -> {
                    LogUtils.sendDebugLog(this@ConfigurationActivity)
                    return@OnMenuItemClickListener true
                }

                R.id.action_about -> {
                    HelpUtils.showAboutDialog(this@ConfigurationActivity)
                    return@OnMenuItemClickListener true
                }
            }
            false
        })
    }

    override fun onStop() {
        super.onStop()

        // Update extensions (settings may have changed)
        // TODO: update only those extensions whose settings have changed
        val updateExtensionsIntent = Intent(this, DashClockService::class.java).apply {
            action = DashClockService.ACTION_UPDATE_EXTENSIONS
            putExtra(
                DashClockService.EXTRA_UPDATE_REASON,
                DashClockExtension.UPDATE_REASON_SETTINGS_CHANGED
            )
        }
        startService(updateExtensionsIntent)

        // Update all widgets, including a new one if it was just added
        // We can't only update the new one because settings affecting all widgets may have
        // been changed.
        LOGD(TAG, "Updating all widgets")

        val widgetUpdateIntent = Intent(this, DashClockService::class.java).apply {
            action = DashClockService.ACTION_UPDATE_WIDGETS
        }
        startService(widgetUpdateIntent)
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(ConfigurationActivity::class.java)

        const val LAUNCHER_ACTIVITY_NAME =
            "com.google.android.apps.dashclock.configuration.ConfigurationLauncherActivity"

        const val EXTRA_START_SECTION =
            "com.google.android.apps.dashclock.configuration.extra.START_SECTION"

        const val START_SECTION_EXTENSIONS = 0
        const val START_SECTION_APPEARANCE = 1
        const val START_SECTION_DAYDREAM = 2
        const val START_SECTION_ADVANCED = 3

        private val SECTION_LABELS = intArrayOf(
            R.string.section_extensions,
            R.string.section_appearance,
            R.string.section_daydream,
            R.string.section_advanced
        )

        private val SECTION_FRAGMENTS = arrayOf<Class<out Fragment>>(
            ConfigureExtensionsFragment::class.java,
            ConfigureAppearanceFragment::class.java,
            ConfigureDaydreamFragment::class.java,
            ConfigureAdvancedFragment::class.java
        )
    }
}
