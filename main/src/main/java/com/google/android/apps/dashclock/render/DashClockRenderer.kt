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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import com.google.android.apps.dashclock.ExtensionManager
import com.google.android.apps.dashclock.ExtensionManager.ExtensionWithData
import com.google.android.apps.dashclock.LogUtils
import com.google.android.apps.dashclock.Utils
import com.google.android.apps.dashclock.WidgetClickProxyActivity
import com.google.android.apps.dashclock.configuration.AppChooserPreference
import com.google.android.apps.dashclock.configuration.AppearanceConfig
import com.google.android.apps.dashclock.configuration.ConfigurationActivity
import net.nurik.roman.dashclock.BuildConfig
import net.nurik.roman.dashclock.R
import java.util.Locale

/**
 * Abstract helper class in charge of core rendering logic for DashClock widgets.
 */
abstract class DashClockRenderer protected constructor(
    @JvmField
    protected var mContext: Context
) {
    @JvmField
    protected var mExtensionManager: ExtensionManager = ExtensionManager.getInstance(mContext)

    @JvmField
    protected var mOptions: Options = Options()

    fun setOptions(options: Options) {
        mOptions = options
    }

    fun renderWidget(container: Any?): Any? {
        val vb = onCreateViewBuilder()
        val res = mContext.resources

        // Load data from extensions
        val extensions = mExtensionManager.getInternalActiveExtensionsWithData()
        val activeExtensions = extensions.size

        // Determine if we're on a tablet or not (lock screen widgets can't be collapsed on
        // tablets).
        val isTablet = res.configuration.smallestScreenWidthDp >= 600

        val shadeColor = AppearanceConfig.getBackgroundColor(mContext, mOptions.target)

        val aggressiveCentering = AppearanceConfig.isAggressiveCenteringEnabled(mContext)

        val minExpandedHeight = res.getDimensionPixelSize(
            if (mOptions.target == Options.TARGET_LOCK_SCREEN) {
                R.dimen.min_expanded_height_lock_screen
            } else {
                R.dimen.min_expanded_height
            }
        )
        val isExpanded = mOptions.minHeightDp >= minExpandedHeight / res.displayMetrics.density

        // Step 1. Load the root layout
        // TODO: merge
        vb.loadRootLayout(
            container,
            if (isExpanded) {
                if (aggressiveCentering) {
                    R.layout.widget_main_expanded_forced_center
                } else {
                    R.layout.widget_main_expanded
                }
            } else {
                if (aggressiveCentering) {
                    if (mOptions.target == Options.TARGET_LOCK_SCREEN) {
                        R.layout.widget_main_collapsed_forced_center_lockscreen
                    } else {
                        R.layout.widget_main_collapsed_forced_center
                    }
                } else {
                    R.layout.widget_main_collapsed
                }
            }
        )

        // Step 2. Configure the shade, if it should exist
        vb.setViewBackgroundColor(R.id.shade, shadeColor)
        vb.setViewVisibility(R.id.shade, if (shadeColor == 0) View.GONE else View.VISIBLE)

        // Step 3. Draw the basic clock face
        val hideSettings: Boolean
        val hideClock = (mOptions.target == Options.TARGET_HOME_SCREEN
                && AppearanceConfig.isClockHiddenOnHomeScreen(mContext))
                || (mOptions.target == Options.TARGET_LOCK_SCREEN
                && AppearanceConfig.isClockHiddenOnLockScreen(mContext))
        vb.setViewVisibility(R.id.clock_target, if (hideClock) View.GONE else View.VISIBLE)
        if (hideClock) {
            hideSettings = true
        } else {
            renderClockFace(vb, mOptions.foregroundColor)
            hideSettings = AppearanceConfig.isSettingsButtonHidden(mContext)
        }

        // Step 4. Align the clock face and settings button (if shown)
        val isPortrait = res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        if (aggressiveCentering) {
            // Forced/aggressive centering rules
            vb.setViewVisibility(
                R.id.settings_button_center_displacement,
                if (hideSettings) View.GONE else View.VISIBLE
            )
            vb.setViewPadding(R.id.clock_row, 0, 0, 0, 0)
            vb.setLinearLayoutGravity(R.id.clock_target, Gravity.CENTER_HORIZONTAL)
        } else {
            // Normal centering rules
            val forceCentered = isTablet && isPortrait
                    && mOptions.target != Options.TARGET_HOME_SCREEN

            var clockInnerGravity = Gravity.CENTER_HORIZONTAL
            if (activeExtensions > 0 && !forceCentered) {
                // Extensions are visible, don't center clock
                clockInnerGravity = if (mOptions.target == Options.TARGET_LOCK_SCREEN) {
                    // lock screen doesn't look at expanded state; the UI should
                    // not jitter across expanded/collapsed states for lock screen
                    if (isTablet) Gravity.LEFT else Gravity.RIGHT
                } else {
                    // home screen
                    if (isExpanded && isTablet) Gravity.LEFT else Gravity.RIGHT
                }
            }
            vb.setLinearLayoutGravity(R.id.clock_target, clockInnerGravity)

            val clockCentered = activeExtensions == 0 || forceCentered // left otherwise
            vb.setLinearLayoutGravity(
                R.id.clock_row,
                if (clockCentered) Gravity.CENTER_HORIZONTAL else Gravity.LEFT
            )
            vb.setViewVisibility(
                R.id.settings_button_center_displacement,
                if (hideSettings) {
                    View.GONE
                } else {
                    if (clockCentered) View.INVISIBLE else View.GONE
                }
            )

            var clockLeftMargin = res.getDimensionPixelSize(R.dimen.clock_left_margin)
            if (!isExpanded && mOptions.target == Options.TARGET_HOME_SCREEN) {
                clockLeftMargin = 0
            }
            vb.setViewPadding(
                R.id.clock_row,
                if (clockCentered) 0 else clockLeftMargin,
                0,
                0,
                0
            )
        }

        // Settings button
        if (isExpanded) {
            vb.setViewVisibility(R.id.settings_button, if (hideSettings) View.GONE else View.VISIBLE)
            vb.setViewClickIntent(
                R.id.settings_button,
                Intent(mContext, ConfigurationActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
            )
            vb.setImageViewBitmap(
                R.id.settings_button,
                Utils.recolorBitmap(
                    res.getDrawable(R.drawable.ic_widget_action_settings) as BitmapDrawable,
                    mOptions.foregroundColor
                )
            )
        }

        // Step 6. Render the extensions (collapsed or expanded)
        if (isExpanded) {
            // Expanded style
            val onClickTemplateIntent = WidgetClickProxyActivity.getTemplate(mContext)
            builderSetExpandedExtensionsAdapter(
                vb,
                R.id.expanded_extensions,
                false,
                onClickTemplateIntent
            )
        } else {
            // Collapsed style
            vb.setViewVisibility(
                R.id.collapsed_extensions_container,
                if (activeExtensions > 0) View.VISIBLE else View.GONE
            )
            vb.removeAllViews(R.id.collapsed_extensions_container)

            // On the lock screen, no vertical scrolling.
            var ellipsisVisible = false
            var slotIndex = 0
            for (ewd in extensions) {
                if (ewd.latestData?.visible() != true) {
                    continue
                }

                if (slotIndex >= MAX_COLLAPSED_EXTENSIONS) {
                    ellipsisVisible = true
                    break
                }

                vb.addView(
                    R.id.collapsed_extensions_container,
                    renderCollapsedExtension(null, null, false, ewd)
                )

                ++slotIndex
            }

            if (ellipsisVisible) {
                vb.addView(
                    R.id.collapsed_extensions_container,
                    vb.inflateChildLayout(
                        R.layout.widget_include_collapsed_ellipsis,
                        R.id.collapsed_extensions_container
                    )
                )
                vb.setImageViewBitmap(
                    R.id.collapsed_extension_ellipsis,
                    Utils.recolorBitmap(
                        res.getDrawable(R.drawable.collapsed_extension_ellipsis) as BitmapDrawable,
                        mOptions.foregroundColor
                    )
                )
            }
        }

        return vb.getRoot()
    }

    fun renderClockFace(vb: ViewBuilder, foregroundColor: Int) {
        val sp = PreferenceManager.getDefaultSharedPreferences(mContext)
        vb.removeAllViews(R.id.time_container)
        vb.addView(
            R.id.time_container,
            vb.inflateChildLayout(
                AppearanceConfig.getCurrentTimeLayout(mContext, foregroundColor),
                R.id.time_container
            )
        )
        vb.removeAllViews(R.id.date_container)
        vb.addView(
            R.id.date_container,
            vb.inflateChildLayout(
                AppearanceConfig.getCurrentDateLayout(mContext),
                R.id.date_container
            )
        )
        if (BuildConfig.DEBUG) {
            if (sp.getBoolean("demomode", false)) {
                vb.setTextClockFormat(R.id.large_time_component_1, "10:08")
                vb.setTextClockFormat(R.id.date_component_1, "FRI, OCT 05")
            }
        }

        if (mOptions.minWidthDp < MIN_NORMAL_FONTSIZE_WIDTH_DP) {
            val res = mContext.resources
            val miniTextSizeLargePx = res.getDimensionPixelSize(R.dimen.mini_clock_text_size_large)
            val miniTextSizeSmallPx = res.getDimensionPixelSize(R.dimen.mini_clock_text_size_small)
            val miniDateTextSizePx = res.getDimensionPixelSize(R.dimen.mini_clock_date_text_size)
            for (id in LARGE_TIME_COMPONENT_IDS) {
                vb.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, miniTextSizeLargePx.toFloat())
            }
            for (id in SMALL_TIME_COMPONENT_IDS) {
                vb.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, miniTextSizeSmallPx.toFloat())
            }
            for (id in DATE_COMPONENT_IDS) {
                vb.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, miniDateTextSizePx.toFloat())
            }
            val miniDatePaddingPx = res.getDimensionPixelSize(R.dimen.mini_clock_date_top_padding)
            vb.setViewPadding(R.id.date_container, 0, miniDatePaddingPx, 0, 0)
        }

        for (id in LARGE_TIME_COMPONENT_IDS) {
            vb.setTextViewColor(id, mOptions.foregroundColor)
        }
        for (id in SMALL_TIME_COMPONENT_IDS) {
            vb.setTextViewColor(id, mOptions.foregroundColor)
        }
        for (id in DATE_COMPONENT_IDS) {
            vb.setTextViewColor(id, mOptions.foregroundColor)
        }

        val clockIntent = AppChooserPreference.getIntentValue(
            sp.getString(PREF_CLOCK_SHORTCUT, null),
            Utils.getDefaultClockIntent(mContext)
        )
        if (clockIntent != null) {
            vb.setViewClickIntent(R.id.clock_target, clockIntent)
        }
    }

    fun renderCollapsedExtension(
        container: Any?,
        convertRoot: Any?,
        inList: Boolean,
        ewd: ExtensionWithData
    ): Any? {
        val vb = onCreateViewBuilder()
        if (convertRoot != null) {
            vb.useRoot(convertRoot)
        } else {
            vb.loadRootLayout(
                container,
                if (inList) {
                    R.layout.widget_include_collapsed_extension
                } else {
                    R.layout.widget_include_collapsed_extension_interactive
                }
            )
        }

        val res = mContext.resources
        val extensionCollapsedTextSizeSingleLine = res
            .getDimensionPixelSize(R.dimen.extension_collapsed_text_size_single_line)
        val extensionCollapsedTextSizeTwoLine = res
            .getDimensionPixelSize(R.dimen.extension_collapsed_text_size_two_line)

        val latestData = ewd.latestData ?: return vb.getRoot()
        val listing = ewd.listing ?: return vb.getRoot()
        val componentName = listing.componentName() ?: return vb.getRoot()

        val status = latestData.status() ?: ""

        if (status.indexOf("\n") > 0) {
            vb.setTextViewSingleLine(R.id.collapsed_extension_text, false)
            vb.setTextViewMaxLines(R.id.collapsed_extension_text, 2)
            vb.setTextViewTextSize(
                R.id.collapsed_extension_text,
                TypedValue.COMPLEX_UNIT_PX,
                extensionCollapsedTextSizeTwoLine.toFloat()
            )
        } else {
            vb.setTextViewSingleLine(R.id.collapsed_extension_text, true)
            vb.setTextViewMaxLines(R.id.collapsed_extension_text, 1)
            vb.setTextViewTextSize(
                R.id.collapsed_extension_text,
                TypedValue.COMPLEX_UNIT_PX,
                extensionCollapsedTextSizeSingleLine.toFloat()
            )
        }

        vb.setTextViewText(R.id.collapsed_extension_text, status.uppercase(Locale.getDefault()))
        vb.setTextViewColor(R.id.collapsed_extension_text, mOptions.foregroundColor)

        var statusContentDescription = latestData.contentDescription()
        if (TextUtils.isEmpty(statusContentDescription)) {
            val builder = StringBuilder()
            val expandedTitle = Utils.expandedTitleOrStatus(latestData)
            if (!TextUtils.isEmpty(expandedTitle)) {
                builder.append(expandedTitle)
            }
            val expandedBody = latestData.expandedBody()
            if (!TextUtils.isEmpty(expandedBody)) {
                builder.append(" ").append(expandedBody)
            }
            statusContentDescription = builder.toString()
        }
        vb.setViewContentDescription(R.id.collapsed_extension_text, statusContentDescription)

        vb.setImageViewBitmap(
            R.id.collapsed_extension_icon,
            Utils.loadExtensionIcon(
                mContext,
                componentName,
                latestData.icon(),
                latestData.iconUri(),
                mOptions.foregroundColor
            )
        )
        vb.setViewContentDescription(R.id.collapsed_extension_icon, listing.title())

        val clickIntent = latestData.clickIntent()
        if (clickIntent != null && componentName != null) {
            if (inList) {
                vb.setViewClickFillInIntent(
                    R.id.collapsed_extension_target,
                    WidgetClickProxyActivity.getFillIntent(
                        clickIntent,
                        componentName
                    )
                )
            } else {
                vb.setViewClickIntent(
                    R.id.collapsed_extension_target,
                    WidgetClickProxyActivity.wrap(
                        mContext,
                        clickIntent,
                        componentName
                    )
                )
            }
        }

        return vb.getRoot()
    }

    fun renderExpandedExtension(
        container: Any?,
        convertRoot: Any?,
        inList: Boolean,
        ewd: ExtensionWithData?
    ): Any? {
        val vb = onCreateViewBuilder()
        if (convertRoot != null) {
            vb.useRoot(convertRoot)
        } else {
            vb.loadRootLayout(container, R.layout.widget_list_item_expanded_extension)
        }

        val latestData = ewd?.latestData
        val listing = ewd?.listing
        if (ewd == null || latestData == null || listing == null) {
            vb.setTextViewText(R.id.text1, mContext.resources.getText(R.string.status_none))
            vb.setViewVisibility(R.id.text2, View.GONE)
            return vb.getRoot()
        }

        val componentName = listing.componentName() ?: return vb.getRoot()

        vb.setTextViewText(R.id.text1, Utils.expandedTitleOrStatus(latestData))
        vb.setTextViewColor(R.id.text1, mOptions.foregroundColor)

        val expandedBody = latestData.expandedBody()
        vb.setViewVisibility(
            R.id.text2,
            if (TextUtils.isEmpty(expandedBody)) View.GONE else View.VISIBLE
        )
        vb.setTextViewText(R.id.text2, latestData.expandedBody())
        vb.setTextViewColor(R.id.text2, mOptions.foregroundColor)

        vb.setImageViewBitmap(
            R.id.icon,
            Utils.loadExtensionIcon(
                mContext,
                componentName,
                latestData.icon(),
                latestData.iconUri(),
                mOptions.foregroundColor
            )
        )
        val contentDescription = latestData.contentDescription()
        if (TextUtils.isEmpty(contentDescription)) {
            // No specific content description provided. Just set the minimal extra content
            // description for the icon.
            vb.setViewContentDescription(R.id.icon, listing.title())
        } else {
            // Content description for the entire row provided. Use it!
            vb.setViewContentDescription(
                R.id.list_item,
                "${listing.title()}. $contentDescription"
            )
            vb.setViewContentDescription(R.id.text1, ".") // silence title
            vb.setViewContentDescription(R.id.text2, ".") // silence body
        }

        val clickIntent = latestData.clickIntent()
        if (clickIntent != null && componentName != null) {
            if (inList) {
                vb.setViewClickFillInIntent(
                    R.id.list_item,
                    WidgetClickProxyActivity.getFillIntent(
                        clickIntent,
                        componentName
                    )
                )
            } else {
                vb.setViewClickIntent(
                    R.id.list_item,
                    WidgetClickProxyActivity.wrap(
                        mContext,
                        clickIntent,
                        componentName
                    )
                )
            }
        }

        return vb.getRoot()
    }

    protected abstract fun onCreateViewBuilder(): ViewBuilder

    protected abstract fun builderSetExpandedExtensionsAdapter(
        builder: ViewBuilder,
        viewId: Int,
        mini: Boolean,
        onClickTemplateIntent: Intent
    )

    class Options {
        @JvmField
        var target: Int = 0
        @JvmField
        var minWidthDp: Int = 0
        @JvmField
        var minHeightDp: Int = 0
        @JvmField
        var foregroundColor: Int = AppearanceConfig.DEFAULT_WIDGET_FOREGROUND_COLOR

        // Only used by WidgetRenderer
        @JvmField
        var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID // optional

        // Only used by SimpleRenderer
        @JvmField
        var newTaskOnClick: Boolean = false
        @JvmField
        var onClickListener: OnClickListener? = null
        @JvmField
        var clickIntentTemplate: Intent? = null

        companion object {
            const val TARGET_HOME_SCREEN: Int = 0
            const val TARGET_LOCK_SCREEN: Int = 1
            const val TARGET_DAYDREAM: Int = 2
        }
    }

    fun interface OnClickListener {
        fun onClick()
    }

    companion object {
        private val TAG = LogUtils.makeLogTag(DashClockRenderer::class.java)

        private const val MAX_COLLAPSED_EXTENSIONS = 3
        private const val MIN_NORMAL_FONTSIZE_WIDTH_DP = 300

        const val PREF_CLOCK_SHORTCUT: String = "pref_clock_shortcut"

        private val LARGE_TIME_COMPONENT_IDS = intArrayOf(
            R.id.large_time_component_1,
            R.id.large_time_component_2,
            R.id.large_time_component_3
        )

        private val SMALL_TIME_COMPONENT_IDS = intArrayOf(
            R.id.small_time_component_1,
            R.id.small_time_component_2,
            R.id.small_time_component_3
        )

        private val DATE_COMPONENT_IDS = intArrayOf(
            R.id.date_component_1,
            R.id.date_component_2,
            R.id.date_component_3
        )
    }
}
