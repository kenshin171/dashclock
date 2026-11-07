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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.ViewPropertyAnimator
import android.widget.TextView
import net.nurik.roman.dashclock.R

class UndoBarController(
    private val mBarView: View,
    private val mUndoListener: UndoListener
) {
    private val mMessageView: TextView = mBarView.findViewById(R.id.undobar_message)
    private val mBarAnimator: ViewPropertyAnimator = mBarView.animate()

    // State objects
    private var mUndoToken: Bundle? = null
    private var mUndoMessage: CharSequence? = null
    private var mBarShown = true

    interface UndoListener {
        fun onUndo(token: Bundle?)
    }

    init {
        mBarView.setOnClickListener {
            hideUndoBar(false)
        }
        mBarView.findViewById<View>(R.id.undobar_button).setOnClickListener {
            hideUndoBar(false)
            mUndoListener.onUndo(mUndoToken)
        }

        hideUndoBar(true)
    }

    fun showUndoBar(immediate: Boolean, message: CharSequence?, undoToken: Bundle?) {
        mBarShown = true
        mUndoToken = undoToken
        mUndoMessage = message
        mMessageView.text = mUndoMessage

        mBarView.visibility = View.VISIBLE
        if (immediate) {
            mBarView.alpha = 1f
        } else {
            mBarAnimator.cancel()
            mBarAnimator
                .alpha(1f)
                .setDuration(
                    mBarView.resources
                        .getInteger(android.R.integer.config_shortAnimTime).toLong()
                )
                .setListener(null)
        }
    }

    fun hideUndoBar(immediate: Boolean) {
        if (!mBarShown) {
            return
        }

        mBarShown = false
        if (immediate) {
            mBarView.visibility = View.GONE
            mBarView.alpha = 0f
            mUndoMessage = null
            mUndoToken = null
        } else {
            mBarAnimator.cancel()
            mBarAnimator
                .alpha(0f)
                .setDuration(
                    mBarView.resources
                        .getInteger(android.R.integer.config_shortAnimTime).toLong()
                )
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mBarView.visibility = View.GONE
                        mUndoMessage = null
                        mUndoToken = null
                    }
                })
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putCharSequence("undo_message", mUndoMessage)
        outState.putBundle("undo_token", mUndoToken)
    }

    fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            mUndoMessage = savedInstanceState.getCharSequence("undo_message")
            mUndoToken = savedInstanceState.getBundle("undo_token")

            if (mUndoToken != null || !TextUtils.isEmpty(mUndoMessage)) {
                showUndoBar(true, mUndoMessage, mUndoToken)
            }
        }
    }
}
