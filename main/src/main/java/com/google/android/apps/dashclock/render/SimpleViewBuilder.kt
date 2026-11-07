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

package com.google.android.apps.dashclock.render

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.LogUtils.LOGE

/**
 * [ViewBuilder] implementation for standard framework views.
 */
class SimpleViewBuilder(
    private val mContext: Context,
    private val mCallbacks: Callbacks?
) : ViewBuilder {
    private var mRootView: View? = null

    override fun useRoot(root: Any?) {
        mRootView = root as? View
    }

    override fun loadRootLayout(container: Any?, layoutResId: Int) {
        mRootView = LayoutInflater.from(mContext).inflate(
            layoutResId,
            container as? ViewGroup,
            false
        )
    }

    override fun inflateChildLayout(layoutResId: Int, containerId: Int): Any {
        return LayoutInflater.from(mContext).inflate(
            layoutResId,
            if (containerId != 0) mRootView?.findViewById<ViewGroup>(containerId) else null,
            false
        )
    }

    override fun setImageViewBitmap(viewId: Int, bitmap: Bitmap?) {
        try {
            (mRootView!!.findViewById<View>(viewId) as ImageView).setImageBitmap(bitmap)
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setViewContentDescription(viewId: Int, contentDescription: String?) {
        try {
            mRootView!!.findViewById<View>(viewId).contentDescription = contentDescription
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setTextViewText(viewId: Int, text: CharSequence?) {
        try {
            (mRootView!!.findViewById<View>(viewId) as TextView).text = text
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setTextViewColor(viewId: Int, color: Int) {
        try {
            (mRootView!!.findViewById<View>(viewId) as TextView).setTextColor(color)
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setTextViewMaxLines(viewId: Int, maxLines: Int) {
        try {
            (mRootView!!.findViewById<View>(viewId) as TextView).maxLines = maxLines
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setTextViewSingleLine(viewId: Int, singleLine: Boolean) {
        try {
            (mRootView!!.findViewById<View>(viewId) as TextView).isSingleLine = singleLine
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setTextViewTextSize(viewId: Int, unit: Int, size: Float) {
        try {
            (mRootView!!.findViewById<View>(viewId) as TextView).setTextSize(unit, size)
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setTextClockFormat(viewId: Int, format: CharSequence?) {
        try {
            (mRootView!!.findViewById<View>(viewId) as TextClock).format12Hour = format
            (mRootView!!.findViewById<View>(viewId) as TextClock).format24Hour = format
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setLinearLayoutGravity(viewId: Int, gravity: Int) {
        try {
            (mRootView!!.findViewById<View>(viewId) as LinearLayout).gravity = gravity
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setViewPadding(viewId: Int, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            mRootView!!.findViewById<View>(viewId).setPadding(left, top, right, bottom)
        } catch (ignored: NullPointerException) {
        }
    }

    override fun addView(viewId: Int, child: Any?) {
        (mRootView!!.findViewById<View>(viewId) as ViewGroup).addView(child as View)
    }

    override fun removeAllViews(viewId: Int) {
        (mRootView!!.findViewById<View>(viewId) as ViewGroup).removeAllViews()
    }

    override fun setViewVisibility(viewId: Int, visibility: Int) {
        try {
            mRootView!!.findViewById<View>(viewId).visibility = visibility
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setViewBackgroundColor(viewId: Int, color: Int) {
        try {
            mRootView!!.findViewById<View>(viewId).setBackgroundColor(color)
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setViewClickIntent(viewId: Int, clickIntent: Intent) {
        try {
            mCallbacks?.onModifyClickIntent(clickIntent)

            mRootView!!.findViewById<View>(viewId).setOnClickListener {
                mContext.startActivity(clickIntent)
                mCallbacks!!.onClickIntentCalled(viewId)
            }
        } catch (ignored: NullPointerException) {
        }
    }

    override fun setViewClickFillInIntent(viewId: Int, fillIntent: Intent) {
        try {
            val targetView = mRootView!!.findViewById<View>(viewId)
            targetView.setOnClickListener {
                if (mCallbacks == null) {
                    return@setOnClickListener
                }

                try {
                    val intent = mCallbacks.onGetClickIntentTemplate()
                    intent!!.fillIn(fillIntent, 0)
                    mContext.startActivity(intent)
                    mCallbacks.onClickIntentCalled(viewId)
                } catch (e: SecurityException) {
                    LOGE(TAG, "Can't click extension.", e)
                } catch (e: ActivityNotFoundException) {
                    LOGE(TAG, "Can't click extension.", e)
                }
            }
        } catch (ignored: NullPointerException) {
        }
    }

    override fun getRoot(): Any? {
        return mRootView
    }

    interface Callbacks {
        fun onGetClickIntentTemplate(): Intent?
        fun onModifyClickIntent(clickIntent: Intent)
        fun onClickIntentCalled(viewId: Int)
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(SimpleViewBuilder::class.java)
    }
}
