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

package com.google.android.apps.dashclock.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import net.nurik.roman.dashclock.R

/**
 * A helper class for creating swipeable tabs without the use of {@link android.app.ActionBar} APIs.
 */
class SimplePagedTabsHelper(
    private val mContext: Context,
    private val mTabContainer: ViewGroup,
    private val mPager: ViewPager
) {
    private val mTabPositions = HashMap<View, Int>()
    private val mTabContentIds = ArrayList<Int>()

    private val mTabClickListener = View.OnClickListener { view ->
        mTabPositions[view]?.let { mPager.currentItem = it }
    }

    private val mAdapter = object : PagerAdapter() {
        override fun getCount(): Int {
            return mTabContentIds.size
        }

        override fun isViewFromObject(view: View, o: Any): Boolean {
            return view === o
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            return mPager.findViewById(mTabContentIds[position])
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            // No-op
        }
    }

    init {
        mPager.adapter = mAdapter
        mPager.setOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until mTabContainer.childCount) {
                    mTabContainer.getChildAt(i).isSelected = i == position
                }
            }
        })
    }

    fun addTab(labelResId: Int, contentViewId: Int) {
        addTab(mContext.getString(labelResId), contentViewId)
    }

    fun addTab(label: CharSequence, contentViewId: Int) {
        val tabView = LayoutInflater.from(mContext).inflate(R.layout.tab, mTabContainer, false)
        tabView.findViewById<TextView>(R.id.tab).text = label
        tabView.setOnClickListener(mTabClickListener)
        val position = mTabContentIds.size
        tabView.isSelected = mPager.currentItem == position
        mTabPositions[tabView] = position
        mTabContainer.addView(tabView)
        mTabContentIds.add(contentViewId)
        mAdapter.notifyDataSetChanged()
    }
}
