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

import android.content.Intent
import android.graphics.Bitmap

/**
 * Abstraction for building up view hierarchies, using either standard framework views or
 * RemoteViews.
 */
interface ViewBuilder {
    fun loadRootLayout(container: Any?, layoutResId: Int)
    fun useRoot(root: Any?)
    fun inflateChildLayout(layoutResId: Int, containerId: Int): Any?
    fun setViewClickIntent(viewId: Int, clickIntent: Intent)
    fun setViewClickFillInIntent(viewId: Int, fillIntent: Intent)
    fun setViewVisibility(viewId: Int, visibility: Int)
    fun setViewPadding(viewId: Int, left: Int, top: Int, right: Int, bottom: Int)
    fun setViewContentDescription(viewId: Int, contentDescription: String?)
    fun setViewBackgroundColor(viewId: Int, color: Int)
    fun setTextViewText(viewId: Int, text: CharSequence?)
    fun setTextViewTextSize(viewId: Int, unit: Int, size: Float)
    fun setTextViewColor(viewId: Int, color: Int)
    fun setTextViewSingleLine(viewId: Int, singleLine: Boolean)
    fun setTextViewMaxLines(viewId: Int, maxLines: Int)
    fun setTextClockFormat(viewId: Int, format: CharSequence?)
    fun setImageViewBitmap(viewId: Int, bitmap: Bitmap?)
    fun setLinearLayoutGravity(viewId: Int, gravity: Int)
    fun addView(viewId: Int, child: Any?)
    fun removeAllViews(viewId: Int)
    fun getRoot(): Any?
}
