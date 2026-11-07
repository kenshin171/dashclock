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

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.DialogFragment
import android.app.LoaderManager
import android.content.AsyncTaskLoader
import android.content.Context
import android.content.Loader
import android.content.res.TypedArray
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.Preference
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.android.apps.dashclock.weather.YahooWeatherApiClient.LocationSearchResult
import net.nurik.roman.dashclock.R

/**
 * A preference that allows the user to choose a location, using the Yahoo! GeoPlanet API.
 */
class WeatherLocationPreference : Preference {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    fun setValue(value: String?) {
        val nonNullValue = value ?: ""

        if (callChangeListener(nonNullValue)) {
            persistString(nonNullValue)
            notifyChanged()
        }
    }

    override fun onClick() {
        super.onClick()

        val fragment = LocationChooserDialogFragment.newInstance()
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
            .findFragmentByTag(getFragmentTag()) as? LocationChooserDialogFragment
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
        return "location_chooser_$key"
    }

    /**
     * Dialog fragment that pops up when touching the preference.
     */
    class LocationChooserDialogFragment : DialogFragment(),
        TextWatcher,
        LoaderManager.LoaderCallbacks<List<LocationSearchResult>> {

        private var mPreference: WeatherLocationPreference? = null

        private var mSearchResultsAdapter: SearchResultsListAdapter? = null
        private var mSearchResultsList: ListView? = null

        fun setPreference(preference: WeatherLocationPreference?) {
            mPreference = preference
            tryBindList()
        }

        override fun onAttach(activity: Activity) {
            super.onAttach(activity)
            tryBindList()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Force Holo Light since ?android:actionBarXX would use dark action bar
            val layoutContext = ContextThemeWrapper(activity, R.style.Theme_Dialog)

            val layoutInflater = LayoutInflater.from(layoutContext)
            val rootView = layoutInflater.inflate(R.layout.dialog_weather_location_chooser, null)
            val searchView = rootView.findViewById<TextView>(R.id.location_query)
            searchView.addTextChangedListener(this)

            // Set up apps
            val list = rootView.findViewById<ListView>(android.R.id.list)
            mSearchResultsList = list
            list.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    val value = mSearchResultsAdapter?.getPrefValueAt(position)
                    mPreference?.setValue(value)
                    dismiss()
                }

            tryBindList()

            val dialog = AlertDialog.Builder(activity)
                .setView(rootView)
                .create()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            return dialog
        }

        private fun tryBindList() {
            if (mPreference == null) {
                return
            }

            if (isAdded && mSearchResultsAdapter == null) {
                mSearchResultsAdapter = SearchResultsListAdapter()
            }

            if (mSearchResultsAdapter != null && mSearchResultsList != null) {
                mSearchResultsList?.adapter = mSearchResultsAdapter
            }
        }

        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
            mQuery = charSequence.toString()
            if (mRestartLoaderHandler.hasMessages(0)) {
                return
            }

            mRestartLoaderHandler.sendMessageDelayed(
                mRestartLoaderHandler.obtainMessage(0),
                QUERY_DELAY_MILLIS.toLong()
            )
        }

        override fun afterTextChanged(editable: Editable) {
        }

        private var mQuery: String? = null

        private val mRestartLoaderHandler: Handler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                val args = Bundle()
                args.putString("query", mQuery)
                loaderManager.restartLoader(0, args, this@LocationChooserDialogFragment)
            }
        }

        override fun onCreateLoader(id: Int, args: Bundle): Loader<List<LocationSearchResult>> {
            val query = args.getString("query")
            return ResultsLoader(query, activity)
        }

        override fun onLoadFinished(
            loader: Loader<List<LocationSearchResult>>,
            results: List<LocationSearchResult>?
        ) {
            mSearchResultsAdapter?.changeArray(results)
        }

        override fun onLoaderReset(loader: Loader<List<LocationSearchResult>>) {
            mSearchResultsAdapter?.changeArray(null)
        }

        private inner class SearchResultsListAdapter : BaseAdapter() {
            private var mResults: List<LocationSearchResult> = ArrayList()

            fun changeArray(results: List<LocationSearchResult>?) {
                mResults = results ?: ArrayList()
                notifyDataSetChanged()
            }

            override fun getCount(): Int {
                return Math.max(1, mResults.size)
            }

            override fun getItem(position: Int): Any? {
                if (position == 0 && mResults.isEmpty()) {
                    return null
                }

                return mResults[position]
            }

            fun getPrefValueAt(position: Int): String {
                if (position == 0 && mResults.isEmpty()) {
                    return ""
                }

                val result = mResults[position]
                return "${result.woeid},${result.displayName}"
            }

            override fun getItemId(position: Int): Long {
                if (position == 0 && mResults.isEmpty()) {
                    return -1
                }

                return mResults[position].woeid.hashCode().toLong()
            }

            override fun getView(position: Int, convertView: View?, container: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(activity)
                    .inflate(R.layout.list_item_weather_location_result, container, false)

                if (position == 0 && mResults.isEmpty()) {
                    view.findViewById<TextView>(android.R.id.text1)
                        .setText(R.string.pref_weather_location_automatic)
                    view.findViewById<TextView>(android.R.id.text2)
                        .setText(R.string.pref_weather_location_automatic_description)
                } else {
                    val result = mResults[position]
                    view.findViewById<TextView>(android.R.id.text1).text = result.displayName
                    view.findViewById<TextView>(android.R.id.text2).text = result.country
                }

                return view
            }
        }

        companion object {
            /**
             * Time between search queries while typing.
             */
            private const val QUERY_DELAY_MILLIS = 500

            @JvmStatic
            fun newInstance(): LocationChooserDialogFragment {
                return LocationChooserDialogFragment()
            }
        }
    }

    /**
     * Loader that fetches location search results from [YahooWeatherApiClient].
     */
    private class ResultsLoader(
        private val mQuery: String?,
        context: Context
    ) : AsyncTaskLoader<List<LocationSearchResult>>(context) {
        private var mResults: List<LocationSearchResult>? = null

        override fun loadInBackground(): List<LocationSearchResult> {
            return YahooWeatherApiClient.findLocationsAutocomplete(mQuery)
        }

        override fun deliverResult(apps: List<LocationSearchResult>?) {
            mResults = apps

            if (isStarted) {
                // If the Loader is currently started, we can immediately
                // deliver its results.
                super.deliverResult(apps)
            }
        }

        override fun onStartLoading() {
            if (mResults != null) {
                deliverResult(mResults)
            }

            if (takeContentChanged() || mResults == null) {
                // If the data has changed since the last time it was loaded
                // or is not currently available, start a load.
                forceLoad()
            }
        }

        override fun onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad()
        }

        override fun onReset() {
            super.onReset()
            onStopLoading()
        }
    }

    companion object {
        @JvmStatic
        fun getDisplayValue(context: Context, value: String?): CharSequence {
            if (value.isNullOrEmpty() || value.indexOf(',') < 0) {
                return context.getString(R.string.pref_weather_location_automatic)
            }

            val woeidAndDisplayName = value.split(",".toRegex(), limit = 2)
            return woeidAndDisplayName[1]
        }

        @JvmStatic
        fun getWoeidFromValue(value: String?): String? {
            if (value.isNullOrEmpty() || value.indexOf(',') < 0) {
                return null
            }

            val woeidAndDisplayName = value.split(",".toRegex(), limit = 2)
            return woeidAndDisplayName[0]
        }
    }
}
