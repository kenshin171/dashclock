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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews

/**
 * [ViewBuilder] implementation for [RemoteViews].
 */
class WidgetViewBuilder(private val mContext: Context) : ViewBuilder {
    private var mRemoteViews: RemoteViews? = null

    override fun loadRootLayout(container: Any?, layoutResId: Int) {
        mRemoteViews = RemoteViews(mContext.packageName, layoutResId)
    }

    override fun useRoot(root: Any?) {
        throw UnsupportedOperationException("Can't reuse RemoteViews for WidgetViewBuilder.")
    }

    override fun inflateChildLayout(layoutResId: Int, containerId: Int): Any {
        return RemoteViews(mContext.packageName, layoutResId)
    }

    override fun setLinearLayoutGravity(viewId: Int, gravity: Int) {
        mRemoteViews!!.setInt(viewId, "setGravity", gravity)
    }

    override fun setViewPadding(viewId: Int, left: Int, top: Int, right: Int, bottom: Int) {
        mRemoteViews!!.setViewPadding(viewId, left, top, right, bottom)
    }

    override fun addView(viewId: Int, child: Any?) {
        mRemoteViews!!.addView(viewId, child as RemoteViews)
    }

    override fun removeAllViews(viewId: Int) {
        mRemoteViews!!.removeAllViews(viewId)
    }

    override fun setViewVisibility(viewId: Int, visibility: Int) {
        mRemoteViews!!.setViewVisibility(viewId, visibility)
    }

    override fun setViewBackgroundColor(viewId: Int, color: Int) {
        mRemoteViews!!.setInt(viewId, "setBackgroundColor", color)
    }

    override fun setImageViewBitmap(viewId: Int, bitmap: Bitmap?) {
        mRemoteViews!!.setImageViewBitmap(viewId, bitmap)
    }

    override fun setViewContentDescription(viewId: Int, contentDescription: String?) {
        mRemoteViews!!.setContentDescription(viewId, contentDescription)
    }

    override fun setTextViewText(viewId: Int, text: CharSequence?) {
        mRemoteViews!!.setTextViewText(viewId, text)
    }

    override fun setTextViewColor(viewId: Int, color: Int) {
        mRemoteViews!!.setTextColor(viewId, color)
    }

    override fun setTextViewMaxLines(viewId: Int, maxLines: Int) {
        mRemoteViews!!.setInt(viewId, "setMaxLines", maxLines)
    }

    override fun setTextViewSingleLine(viewId: Int, singleLine: Boolean) {
        mRemoteViews!!.setBoolean(viewId, "setSingleLine", singleLine)
    }

    override fun setTextViewTextSize(viewId: Int, unit: Int, size: Float) {
        mRemoteViews!!.setTextViewTextSize(viewId, unit, size)
    }

    override fun setTextClockFormat(viewId: Int, format: CharSequence?) {
        mRemoteViews!!.setCharSequence(viewId, "setFormat12Hour", format)
        mRemoteViews!!.setCharSequence(viewId, "setFormat24Hour", format)
    }

    override fun setViewClickIntent(viewId: Int, clickIntent: Intent) {
        mRemoteViews!!.setOnClickPendingIntent(
            viewId,
            PendingIntent.getActivity(
                mContext,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun setViewClickFillInIntent(viewId: Int, fillIntent: Intent) {
        mRemoteViews!!.setOnClickFillInIntent(viewId, fillIntent)
    }

    override fun getRoot(): Any? {
        return mRemoteViews
    }
}
