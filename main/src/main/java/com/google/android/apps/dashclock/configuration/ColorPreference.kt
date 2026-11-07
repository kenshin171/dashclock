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
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.preference.Preference
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.apps.dashclock.Utils
import net.nurik.roman.dashclock.R

/**
 * A preference that allows the user to choose an application or shortcut.
 */
class ColorPreference : Preference {
    private var mColorChoices = IntArray(0)
    private var mValue = 0
    private var mItemLayoutId = R.layout.grid_item_color
    private var mNumColumns = 5
    private var mPreviewView: View? = null

    constructor(context: Context) : super(context) {
        initAttrs(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(attrs, 0)
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
            attrs, R.styleable.ColorPreference, defStyle, defStyle
        )

        try {
            mItemLayoutId = a.getResourceId(R.styleable.ColorPreference_itemLayout, mItemLayoutId)
            mNumColumns = a.getInteger(R.styleable.ColorPreference_numColumns, mNumColumns)
            val choicesResId = a.getResourceId(
                R.styleable.ColorPreference_choices,
                R.array.default_color_choice_values
            )
            if (choicesResId > 0) {
                val choices = a.resources.getStringArray(choicesResId)
                mColorChoices = IntArray(choices.size)
                for (i in choices.indices) {
                    mColorChoices[i] = Color.parseColor(choices[i])
                }
            }
        } finally {
            a.recycle()
        }

        widgetLayoutResource = mItemLayoutId
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        mPreviewView = view.findViewById(R.id.color_view)
        setColorViewValue(mPreviewView, mValue, false)
    }

    fun setValue(value: Int) {
        if (callChangeListener(value)) {
            mValue = value
            persistInt(value)
            notifyChanged()
        }
    }

    override fun onClick() {
        super.onClick()

        val fragment = ColorDialogFragment.newInstance()
        fragment.setPreference(this)

        val activity = context as Activity
        activity.fragmentManager.beginTransaction()
            .add(fragment, getFragmentTag())
            .commit()
    }

    override fun onAttachedToActivity() {
        super.onAttachedToActivity()

        val activity = context as Activity
        val fragment = activity
            .fragmentManager.findFragmentByTag(getFragmentTag()) as? ColorDialogFragment
        if (fragment != null) {
            // re-bind preference to fragment
            fragment.setPreference(this)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInt(index, 0)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        setValue(if (restoreValue) getPersistedInt(0) else (defaultValue as? Int ?: 0))
    }

    fun getFragmentTag(): String {
        return "color_$key"
    }

    fun getValue(): Int {
        return mValue
    }

    class ColorDialogFragment : DialogFragment() {
        private var mPreference: ColorPreference? = null
        private var mColorGrid: GridLayout? = null

        fun setPreference(preference: ColorPreference) {
            mPreference = preference
            repopulateItems()
        }

        override fun onAttach(activity: Activity) {
            super.onAttach(activity)
            repopulateItems()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val layoutInflater = LayoutInflater.from(activity)
            val rootView = layoutInflater.inflate(R.layout.dialog_colors, null)

            mColorGrid = rootView.findViewById<GridLayout>(R.id.color_grid).apply {
                columnCount = mPreference?.mNumColumns ?: 5
            }
            repopulateItems()

            return AlertDialog.Builder(activity)
                .setView(rootView)
                .create()
        }

        private fun repopulateItems() {
            val preference = mPreference ?: return
            val colorGrid = mColorGrid ?: return

            val context = colorGrid.context
            colorGrid.removeAllViews()
            for (color in preference.mColorChoices) {
                val itemView = LayoutInflater.from(context)
                    .inflate(R.layout.grid_item_color, colorGrid, false)

                setColorViewValue(
                    itemView.findViewById(R.id.color_view), color,
                    color == preference.getValue()
                )
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.setOnClickListener {
                    preference.setValue(color)
                    dismiss()
                }

                colorGrid.addView(itemView)
            }

            sizeDialog()
        }

        override fun onStart() {
            super.onStart()
            sizeDialog()
        }

        private fun sizeDialog() {
            if (mPreference == null || mColorGrid == null) {
                return
            }

            val dialog = dialog ?: return
            val colorGrid = mColorGrid ?: return

            val res = colorGrid.context.resources
            val dm = res.displayMetrics

            // Can't use Integer.MAX_VALUE here (weird issue observed otherwise on 4.2)
            colorGrid.measure(
                View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.AT_MOST)
            )
            var width = colorGrid.measuredWidth
            var height = colorGrid.measuredHeight

            val extraPadding = res.getDimensionPixelSize(R.dimen.color_grid_extra_padding)

            width += extraPadding
            height += extraPadding

            dialog.window?.setLayout(width, height)
        }

        companion object {
            @JvmStatic
            fun newInstance(): ColorDialogFragment {
                return ColorDialogFragment()
            }
        }
    }

    companion object {
        @JvmStatic
        private fun setColorViewValue(view: View?, color: Int, selected: Boolean) {
            if (view is ImageView) {
                val res = view.context.resources

                val currentDrawable = view.drawable
                val colorChoiceDrawable: GradientDrawable =
                    if (currentDrawable is GradientDrawable) {
                        // Reuse drawable
                        currentDrawable
                    } else {
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                        }
                    }

                // Set stroke to dark version of color
                val darkenedColor = Color.rgb(
                    Color.red(color) * 192 / 256,
                    Color.green(color) * 192 / 256,
                    Color.blue(color) * 192 / 256
                )

                colorChoiceDrawable.setColor(color)
                colorChoiceDrawable.setStroke(
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 2f, res.displayMetrics
                    ).toInt(),
                    darkenedColor
                )

                var drawable: Drawable = colorChoiceDrawable
                if (selected) {
                    @Suppress("DEPRECATION")
                    val checkmark = res.getDrawable(
                        if (Utils.isColorDark(color))
                            R.drawable.checkmark_white
                        else
                            R.drawable.checkmark_black
                    ) as BitmapDrawable
                    checkmark.gravity = Gravity.CENTER
                    drawable = LayerDrawable(arrayOf(colorChoiceDrawable, checkmark))
                }

                view.setImageDrawable(drawable)
            } else if (view is TextView) {
                view.setTextColor(color)
            }
        }
    }
}
