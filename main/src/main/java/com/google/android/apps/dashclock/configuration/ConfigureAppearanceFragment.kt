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
import android.app.backup.BackupManager
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.google.android.apps.dashclock.Utils
import com.google.android.apps.dashclock.ui.PagerPositionStrip
import net.nurik.roman.dashclock.R
import java.util.HashMap

/**
 * Fragment for allowing the user to configure widget appearance, shown within a [ConfigurationActivity].
 */
class ConfigureAppearanceFragment : Fragment() {
    private val mHandler = Handler()

    private val mHidePositionStripRunnables = HashMap<View, Runnable>()

    private val mCurrentStyleNames = HashMap<String, String>()
    private var mAnimationDuration = 0
    private var mPositionStrips: Array<View>? = null
    private lateinit var mAppearanceContainerView: View

    private val mHidePositionStripsRunnable = Runnable {
        showPositionStrips(false, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mAnimationDuration = resources.getInteger(android.R.integer.config_shortAnimTime)

        val sp = PreferenceManager.getDefaultSharedPreferences(activity)

        val rootView = inflater.inflate(
            R.layout.fragment_configure_appearance, container, false
        ) as ViewGroup

        mCurrentStyleNames[AppearanceConfig.PREF_STYLE_TIME] =
            sp.getString(
                AppearanceConfig.PREF_STYLE_TIME,
                AppearanceConfig.TIME_STYLE_NAMES[0]
            ) ?: AppearanceConfig.TIME_STYLE_NAMES[0]
        configureStylePager(
            rootView.findViewById<View>(R.id.pager_time_style) as ViewPager,
            rootView.findViewById<View>(R.id.pager_time_position_strip) as PagerPositionStrip,
            AppearanceConfig.TIME_STYLE_NAMES,
            AppearanceConfig.COMPONENT_TIME,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
            AppearanceConfig.PREF_STYLE_TIME
        )

        mCurrentStyleNames[AppearanceConfig.PREF_STYLE_DATE] =
            sp.getString(
                AppearanceConfig.PREF_STYLE_DATE,
                AppearanceConfig.DATE_STYLE_NAMES[0]
            ) ?: AppearanceConfig.DATE_STYLE_NAMES[0]
        configureStylePager(
            rootView.findViewById<View>(R.id.pager_date_style) as ViewPager,
            rootView.findViewById<View>(R.id.pager_date_position_strip) as PagerPositionStrip,
            AppearanceConfig.DATE_STYLE_NAMES,
            AppearanceConfig.COMPONENT_DATE,
            Gravity.CENTER_HORIZONTAL or Gravity.TOP,
            AppearanceConfig.PREF_STYLE_DATE
        )
        (activity as ConfigurationActivity).showWallpaper()

        mAppearanceContainerView = rootView.findViewById(R.id.appearance_container)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.appearance_more_container, ConfigureAppearanceMoreFragment())
                .commit()
        }
        return rootView
    }

    override fun onResume() {
        super.onResume()
        val rootView = view
        if (rootView != null) {
            val strips = arrayOf<View>(
                rootView.findViewById(R.id.pager_time_position_strip),
                rootView.findViewById(R.id.pager_date_position_strip)
            )
            mPositionStrips = strips
            for (strip in strips) {
                strip.alpha = 0f
            }
            showPositionStrips(true, AUTO_HIDE_DELAY_MILLIS)
        }
    }

    override fun onPause() {
        super.onPause()

        val sp = PreferenceManager.getDefaultSharedPreferences(activity).edit()
        for ((key, value) in mCurrentStyleNames) {
            sp.putString(key, value)
        }
        sp.commit()

        BackupManager(activity).dataChanged()
    }

    override fun onDestroy() {
        super.onDestroy()

        // remove all scheduled runnables
        mHidePositionStripRunnables.clear()
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun configureStylePager(
        pager: ViewPager,
        positionStrip: PagerPositionStrip,
        styleNames: Array<String>,
        styleComponent: String,
        gravity: Int,
        preference: String
    ) {
        val currentStyleName = mCurrentStyleNames[preference]

        val inflater = activity.layoutInflater
        pager.adapter = object : PagerAdapter() {
            override fun getCount(): Int {
                return styleNames.size
            }

            override fun isViewFromObject(view: View, o: Any): Boolean {
                return view === o
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val wrapper = FrameLayout(activity)
                val wrapperLp = ViewPager.LayoutParams()
                wrapper.layoutParams = wrapperLp
                var styleName = styleNames[position]
                if (styleName.contains("analog")) {
                    styleName += "_white"
                }
                val v = inflater.inflate(
                    AppearanceConfig.getLayoutByStyleName(activity, styleComponent, styleName),
                    container,
                    false
                )
                val lp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.gravity = gravity
                v.layoutParams = lp
                wrapper.addView(v)
                container.addView(wrapper)
                return wrapper
            }

            override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
                container.removeView(`object` as View)
            }
        }

        pager.setOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                mCurrentStyleNames[preference] = styleNames[position]
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                positionStrip.setPosition(position + positionOffset)
            }
        })

        positionStrip.setPageCount(styleNames.size)

        pager.setOnTouchListener { _, motionEvent ->
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN -> showPositionStrips(true, -1)
                MotionEvent.ACTION_UP -> showPositionStrips(false, AUTO_HIDE_DELAY_MILLIS)
            }
            false
        }

        for (i in styleNames.indices) {
            if (currentStyleName == styleNames[i]) {
                pager.currentItem = i
                positionStrip.setPosition(i.toFloat())
                break
            }
        }
    }

    private fun showPositionStrips(show: Boolean, hideDelay: Int) {
        // remove any currently scheduled runnables
        mHandler.removeCallbacks(mHidePositionStripsRunnable)

        // if show or hide immediately, take action now
        if (show || hideDelay <= 0) {
            mPositionStrips?.forEach { strip ->
                strip.animate().cancel()
                strip.animate().alpha(if (show) 1f else 0f).duration = mAnimationDuration.toLong()
            }
        }

        // schedule a hide if hideDelay > 0
        if (hideDelay > 0) {
            mHandler.postDelayed(mHidePositionStripsRunnable, hideDelay.toLong())
        }
    }

    override fun onDetach() {
        super.onDetach()
    }

    fun setAppearanceContainerTranslationY(translationY: Int) {
        mAppearanceContainerView.translationY = translationY.toFloat()
    }

    companion object {
        private const val AUTO_HIDE_DELAY_MILLIS = 1 * Utils.SECONDS_MILLIS
    }
}
