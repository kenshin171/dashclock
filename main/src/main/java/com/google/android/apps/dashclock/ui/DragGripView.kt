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
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import net.nurik.roman.dashclock.R

class DragGripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private var mGravity = Gravity.START
    private var mColor = 0x33333333

    private val mRidgePaint: Paint
    private val mTempRectF = RectF()

    private val mRidgeSize: Float
    private val mRidgeGap: Float

    private var mWidth = 0
    private var mHeight = 0

    init {
        val a = context.obtainStyledAttributes(attrs, ATTRS)
        mGravity = a.getInteger(0, mGravity)
        mColor = a.getColor(1, mColor)
        a.recycle()

        val res = resources
        mRidgeSize = res.getDimensionPixelSize(R.dimen.drag_grip_ridge_size).toFloat()
        mRidgeGap = res.getDimensionPixelSize(R.dimen.drag_grip_ridge_gap).toFloat()

        mRidgePaint = Paint()
        mRidgePaint.color = mColor
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(
                (HORIZ_RIDGES * (mRidgeSize + mRidgeGap) - mRidgeGap).toInt()
                        + paddingLeft + paddingRight,
                widthMeasureSpec
            ),
            resolveSize(
                mRidgeSize.toInt(),
                heightMeasureSpec
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val drawWidth = HORIZ_RIDGES * (mRidgeSize + mRidgeGap) - mRidgeGap
        val drawLeft: Float = when (
            Gravity.getAbsoluteGravity(mGravity, layoutDirection)
                    and Gravity.HORIZONTAL_GRAVITY_MASK
        ) {
            Gravity.CENTER_HORIZONTAL -> paddingLeft +
                    (mWidth - paddingLeft - paddingRight - drawWidth) / 2
            Gravity.RIGHT -> width - paddingRight - drawWidth
            else -> paddingLeft.toFloat()
        }

        val vertRidges = ((mHeight - paddingTop - paddingBottom + mRidgeGap)
                / (mRidgeSize + mRidgeGap)).toInt()
        val drawHeight = vertRidges * (mRidgeSize + mRidgeGap) - mRidgeGap
        val drawTop = paddingTop +
                (mHeight - paddingTop - paddingBottom - drawHeight) / 2

        for (y in 0 until vertRidges) {
            for (x in 0 until HORIZ_RIDGES) {
                mTempRectF.set(
                    drawLeft + x * (mRidgeSize + mRidgeGap),
                    drawTop + y * (mRidgeSize + mRidgeGap),
                    drawLeft + x * (mRidgeSize + mRidgeGap) + mRidgeSize,
                    drawTop + y * (mRidgeSize + mRidgeGap) + mRidgeSize
                )
                canvas.drawOval(mTempRectF, mRidgePaint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mHeight = h
        mWidth = w
    }

    companion object {
        private val ATTRS = intArrayOf(
            android.R.attr.gravity,
            android.R.attr.color
        )

        private const val HORIZ_RIDGES = 2
    }
}
