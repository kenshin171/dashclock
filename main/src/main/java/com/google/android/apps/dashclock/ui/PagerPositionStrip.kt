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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import net.nurik.roman.dashclock.R

class PagerPositionStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private var mPageCount = 0
    private var mPosition = 0f

    private var mIndicatorColor = 0xff000000.toInt()
    private var mBackgroundColor = 0x80000000.toInt()

    private var mIndicatorWidth = 0f
    private val mBackgroundPaint: Paint
    private val mIndicatorPaint: Paint

    private val mTempPath = Path()
    private val mTempRectF = RectF()

    private var mWidth = 0
    private var mHeight = 0

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PagerPositionStrip)
        mBackgroundColor = a.getColor(
            R.styleable.PagerPositionStrip_backgroundColor, mBackgroundColor
        )
        mIndicatorColor = a.getColor(
            R.styleable.PagerPositionStrip_indicatorColor, mIndicatorColor
        )
        a.recycle()

        mBackgroundPaint = Paint()
        mBackgroundPaint.color = mBackgroundColor
        mBackgroundPaint.isAntiAlias = true

        mIndicatorPaint = Paint()
        mIndicatorPaint.color = mIndicatorColor
        mIndicatorPaint.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        mTempRectF.top = 0f
        mTempRectF.bottom = mHeight.toFloat()
        mTempRectF.left = 0f
        mTempRectF.right = mWidth.toFloat()

        drawPill(canvas, mTempRectF, mBackgroundPaint)

        if (mPageCount == 0) {
            return
        }

        mTempRectF.top = 0f
        mTempRectF.bottom = mHeight.toFloat()
        mTempRectF.left = mPosition / mPageCount * mWidth
        mTempRectF.right = mTempRectF.left + mIndicatorWidth

        drawPill(canvas, mTempRectF, mIndicatorPaint)
    }

    private fun drawPill(canvas: Canvas, rectF: RectF, paint: Paint) {
        val radius = rectF.height() / 2
        var temp: Float

        mTempPath.reset()
        mTempPath.moveTo(rectF.left + radius, rectF.top)
        mTempPath.lineTo(rectF.right - radius, rectF.top)

        temp = rectF.left
        rectF.left = rectF.right - 2 * radius
        mTempPath.arcTo(rectF, 270f, 180f)
        rectF.left = temp

        mTempPath.lineTo(rectF.left + radius, rectF.bottom)

        temp = rectF.right
        rectF.right = rectF.left + rectF.height()
        mTempPath.arcTo(rectF, 90f, 180f)
        rectF.right = temp

        mTempPath.close()
        canvas.drawPath(mTempPath, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(0, widthMeasureSpec),
            resolveSize(0, heightMeasureSpec)
        )
    }

    fun setPosition(currentPosition: Float) {
        mPosition = currentPosition
        postInvalidateOnAnimation()
    }

    fun setPageCount(count: Int) {
        mPageCount = count
        recalculateIndicatorWidth()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mHeight = h
        mWidth = w
        recalculateIndicatorWidth()
    }

    private fun recalculateIndicatorWidth() {
        mIndicatorWidth = if (mPageCount > 0) mWidth * 1f / mPageCount else 0f
    }
}
