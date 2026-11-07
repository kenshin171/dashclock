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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.preference.Preference
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.viewpager.widget.ViewPager
import com.google.android.apps.dashclock.ui.SimplePagedTabsHelper
import net.nurik.roman.dashclock.R
import java.net.URISyntaxException
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator

/**
 * A preference that allows the user to choose an application or shortcut.
 */
class AppChooserPreference : Preference {
    private var mAllowUseDefault = false

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(attrs, 0)
    }

    constructor(context: Context) : super(context) {
        initAttrs(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initAttrs(attrs, defStyle)
    }

    private fun initAttrs(attrs: AttributeSet?, defStyle: Int) {
        val a = context.theme.obtainStyledAttributes(
            attrs, R.styleable.AppChooserPreference, defStyle, defStyle
        )

        try {
            mAllowUseDefault = a.getBoolean(R.styleable.AppChooserPreference_allowUseDefault, true)
        } finally {
            a.recycle()
        }
    }

    fun setIntentValue(intent: Intent?) {
        setValue(intent?.toUri(Intent.URI_INTENT_SCHEME) ?: "")
    }

    fun setValue(value: String?) {
        val nonNullValue = value ?: ""
        if (callChangeListener(nonNullValue)) {
            persistString(nonNullValue)
            notifyChanged()
        }
    }

    override fun onClick() {
        super.onClick()

        val fragment = AppChooserDialogFragment.newInstance()
        fragment.setPreference(this)

        val activity = context as Activity
        activity.fragmentManager.beginTransaction()
            .add(fragment, getFragmentTag())
            .commit()
    }

    override fun onAttachedToActivity() {
        super.onAttachedToActivity()

        val activity = context as Activity
        val fragment = activity.fragmentManager
            .findFragmentByTag(getFragmentTag()) as? AppChooserDialogFragment
        if (fragment != null) {
            // re-bind preference to fragment
            fragment.setPreference(this)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        setValue(if (restoreValue) getPersistedString("") else defaultValue as? String)
    }

    fun getFragmentTag(): String {
        return "app_chooser_$key"
    }

    class AppChooserDialogFragment : DialogFragment() {
        private var mPreference: AppChooserPreference? = null

        private var mAppsAdapter: ActivityListAdapter? = null
        private var mShortcutsAdapter: ActivityListAdapter? = null

        private var mAppsList: ListView? = null
        private var mShortcutsList: ListView? = null

        fun setPreference(preference: AppChooserPreference) {
            mPreference = preference
            tryBindLists()
        }

        override fun onAttach(activity: Activity) {
            super.onAttach(activity)
            tryBindLists()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Force Holo Light since ?android:actionBarXX would use dark action bar
            val layoutContext = ContextThemeWrapper(activity, R.style.Theme_Dialog)

            val layoutInflater = LayoutInflater.from(layoutContext)
            val rootView = layoutInflater.inflate(R.layout.dialog_app_chooser, null)
            val tabWidget = rootView.findViewById<ViewGroup>(android.R.id.tabs)
            val pager = rootView.findViewById<ViewPager>(R.id.pager)
            pager.pageMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f,
                resources.displayMetrics
            ).toInt()

            val helper = SimplePagedTabsHelper(layoutContext, tabWidget, pager)
            helper.addTab(R.string.title_apps, R.id.apps_list)
            helper.addTab(R.string.title_shortcuts, R.id.shortcuts_list)

            // Set up apps
            mAppsList = rootView.findViewById<ListView>(R.id.apps_list).apply {
                onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    var intent = mAppsAdapter?.getIntent(position)
                    if (intent != null) {
                        intent = Intent.makeMainActivity(intent.component)
                    }
                    mPreference?.setIntentValue(intent)
                    dismiss()
                }
            }

            // Set up shortcuts
            mShortcutsList = rootView.findViewById<ListView>(R.id.shortcuts_list).apply {
                onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    startActivityForResult(
                        mShortcutsAdapter?.getIntent(position),
                        REQUEST_CREATE_SHORTCUT
                    )
                }
            }

            tryBindLists()

            return AlertDialog.Builder(activity)
                .setView(rootView)
                .create()
        }

        private fun tryBindLists() {
            val preference = mPreference ?: return

            if (isAdded && mAppsAdapter == null && mShortcutsAdapter == null) {
                mAppsAdapter = ActivityListAdapter(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    preference.mAllowUseDefault
                )
                mShortcutsAdapter = ActivityListAdapter(
                    Intent(Intent.ACTION_CREATE_SHORTCUT).addCategory(Intent.CATEGORY_DEFAULT),
                    false
                )
            }

            if (mAppsAdapter != null && mAppsList != null
                && mShortcutsAdapter != null && mShortcutsList != null
            ) {
                mAppsList?.adapter = mAppsAdapter
                mShortcutsList?.adapter = mShortcutsAdapter
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_CREATE_SHORTCUT && resultCode == Activity.RESULT_OK && data != null) {
                @Suppress("DEPRECATION")
                mPreference?.setIntentValue(
                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                )
                dismiss()
            }
        }

        internal class ActivityInfo {
            var label: CharSequence? = null
            var icon: Drawable? = null
            lateinit var componentName: ComponentName
        }

        private inner class ActivityListAdapter(
            private val mQueryIntent: Intent,
            private val mAllowUseDefault: Boolean
        ) : BaseAdapter() {
            private val mPackageManager: PackageManager = activity.packageManager
            private val mInfos: MutableList<ActivityInfo> = ArrayList()

            init {
                val resolveInfos = mPackageManager.queryIntentActivities(mQueryIntent, 0)
                for (ri in resolveInfos) {
                    val ai = ActivityInfo().apply {
                        icon = ri.loadIcon(mPackageManager)
                        label = ri.loadLabel(mPackageManager)
                        componentName = ComponentName(
                            ri.activityInfo.packageName,
                            ri.activityInfo.name
                        )
                    }
                    mInfos.add(ai)
                }

                Collections.sort(mInfos, Comparator { activityInfo, activityInfo2 ->
                    activityInfo.label.toString().compareTo(activityInfo2.label.toString())
                })
            }

            override fun getCount(): Int {
                return mInfos.size + if (mAllowUseDefault) 1 else 0
            }

            override fun getItem(position: Int): Any? {
                if (mAllowUseDefault && position == 0) {
                    return null
                }

                return mInfos[position - if (mAllowUseDefault) 1 else 0]
            }

            fun getIntent(position: Int): Intent? {
                if (mAllowUseDefault && position == 0) {
                    return null
                }

                return Intent(mQueryIntent)
                    .setComponent(mInfos[position - if (mAllowUseDefault) 1 else 0].componentName)
            }

            override fun getItemId(position: Int): Long {
                if (mAllowUseDefault && position == 0) {
                    return -1
                }

                return mInfos[position - if (mAllowUseDefault) 1 else 0].componentName.hashCode()
                    .toLong()
            }

            override fun getView(position: Int, convertView: View?, container: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(activity)
                    .inflate(R.layout.list_item_intent, container, false)

                if (mAllowUseDefault && position == 0) {
                    view.findViewById<TextView>(android.R.id.text1).text =
                        getString(R.string.pref_shortcut_default)
                    view.findViewById<ImageView>(android.R.id.icon).setImageDrawable(null)
                } else {
                    val ai = mInfos[position - if (mAllowUseDefault) 1 else 0]
                    view.findViewById<TextView>(android.R.id.text1).text = ai.label
                    view.findViewById<ImageView>(android.R.id.icon).setImageDrawable(ai.icon)
                }

                return view
            }
        }

        companion object {
            @JvmField
            var REQUEST_CREATE_SHORTCUT = 1

            @JvmStatic
            fun newInstance(): AppChooserDialogFragment {
                return AppChooserDialogFragment()
            }
        }
    }

    companion object {
        @JvmStatic
        fun getIntentValue(value: String?, defaultIntent: Intent?): Intent? {
            return try {
                if (TextUtils.isEmpty(value)) {
                    defaultIntent
                } else {
                    Intent.parseUri(value, Intent.URI_INTENT_SCHEME)
                }
            } catch (e: URISyntaxException) {
                defaultIntent
            }
        }

        @JvmStatic
        fun getDisplayValue(context: Context, value: String?): CharSequence? {
            if (TextUtils.isEmpty(value)) {
                return context.getString(R.string.pref_shortcut_default)
            }

            val intent = try {
                Intent.parseUri(value, Intent.URI_INTENT_SCHEME)
            } catch (e: URISyntaxException) {
                return context.getString(R.string.pref_shortcut_default)
            }

            val pm = context.packageManager
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            if (resolveInfos.isEmpty()) {
                return null
            }

            val label = StringBuilder()
            label.append(resolveInfos[0].loadLabel(pm))
            if (intent.data != null && intent.data?.scheme != null &&
                intent.data!!.scheme!!.startsWith("http")
            ) {
                label.append(": ").append(intent.dataString)
            }
            return label
        }
    }
}
