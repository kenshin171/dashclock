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

package com.google.android.apps.dashclock

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.service.dreams.DreamService
import android.util.AttributeSet
import android.util.Property
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import com.google.android.apps.dashclock.Utils.SECONDS_MILLIS
import com.google.android.apps.dashclock.configuration.AppearanceConfig
import com.google.android.apps.dashclock.render.DashClockRenderer
import com.google.android.apps.dashclock.render.SimpleRenderer
import net.nurik.roman.dashclock.R

/**
 * Daydream for DashClock.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
class DaydreamService : DreamService(),
    ExtensionManager.OnChangeListener,
    DashClockRenderer.OnClickListener {

    private val mHandler = Handler(Looper.getMainLooper())
    private var mExtensionManager: ExtensionManager? = null
    private var mTravelDistance = 0
    private var mForegroundColor = 0
    private var mAnimation = 0

    private lateinit var mDaydreamContainer: ViewGroup
    private lateinit var mExtensionsContainer: ViewGroup
    private var mSingleCycleAnimator: AnimatorSet? = null

    private var mAttached = false
    private var mNeedsRelayout = false
    private var mMovingLeft = false
    private var mManuallyAwoken = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mExtensionManager = ExtensionManager.getInstance(this).also {
            it.addOnChangeListener(this)
        }

        // Update extensions and ensure the periodic refresh is set up.
        PeriodicExtensionRefreshReceiver.updateExtensionsAndEnsurePeriodicRefresh(this)

        mAttached = true
        isInteractive = true
        isFullscreen = true

        // Read preferences
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        mForegroundColor = sp.getInt(
            PREF_DAYDREAM_COLOR,
            AppearanceConfig.DEFAULT_WIDGET_FOREGROUND_COLOR
        )

        val animation = sp.getString(PREF_DAYDREAM_ANIMATION, "")
        mAnimation = when (animation) {
            "none" -> ANIMATION_NONE
            "slide" -> ANIMATION_SLIDE
            "fade" -> ANIMATION_FADE
            else -> ANIMATION_PENDULUM
        }

        isScreenBright = !sp.getBoolean(PREF_DAYDREAM_NIGHT_MODE, true)

        // Begin daydream
        layoutDream()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mExtensionManager?.removeOnChangeListener(this)
        mExtensionManager = null
        mHandler.removeCallbacksAndMessages(null)
        mAttached = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mHandler.removeCallbacks(mCycleRunnable)
        layoutDream()
    }

    override fun onExtensionsChanged(sourceExtension: ComponentName?) {
        mHandler.removeCallbacks(mHandleExtensionsChanged)
        mHandler.postDelayed(
            mHandleExtensionsChanged,
            DashClockService.UPDATE_COLLAPSE_TIME_MILLIS.toLong()
        )
    }

    private val mHandleExtensionsChanged = Runnable {
        renderDaydream(false)
    }

    private fun layoutDream() {
        setContentView(R.layout.daydream)
        mNeedsRelayout = true
        renderDaydream(true)

        mHandler.removeCallbacks(mCycleRunnable)
        mHandler.postDelayed(mCycleRunnable, (CYCLE_INTERVAL_MILLIS - FADE_MILLIS).toLong())
    }

    private fun renderDaydream(restartAnimation: Boolean) {
        val extensionManager = mExtensionManager
        if (!mAttached || extensionManager == null) {
            return
        }

        if (restartAnimation) {
            // Only modify fullscreen state if this render will restart an animation (enter a new
            // cycle)
            isFullscreen = true
        }

        val res = resources

        mDaydreamContainer = findViewById<View>(R.id.daydream_container) as ViewGroup
        val rootContainer = findViewById<View>(R.id.daydream_root) as RootLayout
        if (mTravelDistance == 0) {
            mTravelDistance = rootContainer.width / 4
        }
        rootContainer.setRootLayoutListener(object : RootLayout.RootLayoutListener {
            override fun onAwake() {
                mManuallyAwoken = true
                isFullscreen = false
                mHandler.removeCallbacks(mCycleRunnable)
                mHandler.postDelayed(mCycleRunnable, CYCLE_INTERVAL_MILLIS.toLong())
                mDaydreamContainer.animate()
                    .alpha(1f)
                    .rotation(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(res.getInteger(android.R.integer.config_shortAnimTime).toLong())
                mSingleCycleAnimator?.cancel()
            }

            override fun isAwake(): Boolean {
                return mManuallyAwoken
            }

            override fun onSizeChanged(width: Int, height: Int) {
                mTravelDistance = width / 4
            }
        })

        val displayMetrics = res.displayMetrics

        val screenWidthDp = (displayMetrics.widthPixels * 1f / displayMetrics.density).toInt()
        val screenHeightDp = (displayMetrics.heightPixels * 1f / displayMetrics.density).toInt()

        // Set up rendering
        val renderer = SimpleRenderer(this)
        val options = DashClockRenderer.Options()
        options.target = DashClockRenderer.Options.TARGET_DAYDREAM
        options.foregroundColor = Color.WHITE
        options.minWidthDp = screenWidthDp
        options.minHeightDp = screenHeightDp
        options.newTaskOnClick = true
        options.onClickListener = this
        options.clickIntentTemplate = WidgetClickProxyActivity.getTemplate(this)
        renderer.setOptions(options)

        // Render the clock face
        val vb = renderer.createSimpleViewBuilder()
        vb.useRoot(mDaydreamContainer)
        renderer.renderClockFace(vb, options.foregroundColor)
        vb.setLinearLayoutGravity(R.id.clock_target, Gravity.CENTER_HORIZONTAL)

        // Render extensions
        mExtensionsContainer = findViewById<View>(R.id.extensions_container) as ViewGroup
        mExtensionsContainer.removeAllViews()
        val visibleExtensions = extensionManager.getVisibleExtensionsWithData()
        for (ewd in visibleExtensions) {
            mExtensionsContainer.addView(
                renderer.renderExpandedExtension(
                    mExtensionsContainer,
                    null,
                    false,
                    ewd
                ) as View
            )
        }

        if (mDaydreamContainer.height == 0 || mNeedsRelayout) {
            val vto = mDaydreamContainer.viewTreeObserver
            if (vto.isAlive) {
                vto.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        val currentVto = mDaydreamContainer.viewTreeObserver
                        if (currentVto.isAlive) {
                            currentVto.removeOnGlobalLayoutListener(this)
                        }

                        postLayoutRender(restartAnimation)
                    }
                })
            }
            mDaydreamContainer.requestLayout()
            mNeedsRelayout = false
        } else {
            postLayoutRender(restartAnimation)
        }
    }

    /**
     * Post-layout render code.
     */
    fun postLayoutRender(restartAnimation: Boolean) {
        // Adjust the ScrollView
        val scrollView = findViewById<View>(R.id.extensions_scroller) as ExposedScrollView
        val maxScroll = scrollView.computeVerticalScrollRange() - scrollView.height
        if (maxScroll < 0) {
            val lp = scrollView.layoutParams
            lp.height = mExtensionsContainer.height
            scrollView.layoutParams = lp
            mDaydreamContainer.requestLayout()
        }

        // Recolor widget
        Utils.traverseAndRecolor(mDaydreamContainer, mForegroundColor, true, true)

        if (restartAnimation) {
            var x = 0
            var deg = 0
            if (mAnimation and ANIMATION_HAS_SLIDE != 0) {
                x = (if (mMovingLeft) 1 else -1) * mTravelDistance
            }
            if (mAnimation and ANIMATION_HAS_ROTATE != 0) {
                deg = (if (mMovingLeft) 1 else -1) * TRAVEL_ROTATE_DEGREES
            }
            mMovingLeft = !mMovingLeft
            mDaydreamContainer.animate().cancel()
            if (mAnimation and ANIMATION_HAS_SLIDE != 0) {
                // Only use small size when moving
                mDaydreamContainer.scaleX = SCALE_WHEN_MOVING
                mDaydreamContainer.scaleY = SCALE_WHEN_MOVING
            }
            mSingleCycleAnimator?.cancel()

            val scrollDownAnimator = ObjectAnimator.ofInt(
                scrollView,
                ExposedScrollView.SCROLL_POS,
                0,
                maxScroll
            )
            scrollDownAnimator.duration = (CYCLE_INTERVAL_MILLIS / 5).toLong()
            scrollDownAnimator.startDelay = (CYCLE_INTERVAL_MILLIS / 5).toLong()

            val scrollUpAnimator = ObjectAnimator.ofInt(
                scrollView,
                ExposedScrollView.SCROLL_POS,
                0
            )
            scrollUpAnimator.duration = (CYCLE_INTERVAL_MILLIS / 5).toLong()
            scrollUpAnimator.startDelay = (CYCLE_INTERVAL_MILLIS / 5).toLong()

            val scrollAnimator = AnimatorSet()
            scrollAnimator.playSequentially(scrollDownAnimator, scrollUpAnimator)

            val moveAnimator = ObjectAnimator.ofFloat(
                mDaydreamContainer,
                View.TRANSLATION_X,
                x.toFloat(),
                (-x).toFloat()
            ).setDuration(CYCLE_INTERVAL_MILLIS.toLong())
            moveAnimator.interpolator = LinearInterpolator()

            val rotateAnimator = ObjectAnimator.ofFloat(
                mDaydreamContainer,
                View.ROTATION,
                deg.toFloat(),
                (-deg).toFloat()
            ).setDuration(CYCLE_INTERVAL_MILLIS.toLong())
            moveAnimator.interpolator = LinearInterpolator()

            mSingleCycleAnimator = AnimatorSet().apply {
                playTogether(scrollAnimator, moveAnimator, rotateAnimator)
                start()
            }
        }
    }

    @JvmField
    var mCycleRunnable: Runnable = object : Runnable {
        override fun run() {
            mManuallyAwoken = false
            var outAlpha = 1f
            if (mAnimation and ANIMATION_HAS_FADE != 0) {
                outAlpha = 0f
            }
            mDaydreamContainer.animate().alpha(outAlpha).setDuration(FADE_MILLIS.toLong())
                .withEndAction {
                    renderDaydream(true)
                    mHandler.removeCallbacks(this)
                    mHandler.postDelayed(
                        this,
                        (CYCLE_INTERVAL_MILLIS - FADE_MILLIS).toLong()
                    )
                    mDaydreamContainer.animate().alpha(1f).setDuration(FADE_MILLIS.toLong())
                }
        }
    }

    override fun onClick() {
        // Any time anything in DashClock is clicked
        finish()
    }

    /**
     * FrameLayout that can notify listeners of ACTION_DOWN events.
     */
    class RootLayout : FrameLayout {
        private var mRootLayoutListener: RootLayoutListener? = null
        private var mCancelCurrentEvent = false

        constructor(context: Context) : super(context)

        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
            context,
            attrs,
            defStyle
        )

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                // ACTION_UP doesn't seem to reliably get called. Otherwise
                // should postDelayed on ACTION_UP instead of ACTION_DOWN.
                MotionEvent.ACTION_DOWN -> {
                    val listener = mRootLayoutListener
                    if (listener != null && !listener.isAwake()) {
                        mCancelCurrentEvent = true
                        listener.onAwake()
                    } else {
                        mCancelCurrentEvent = false
                    }
                }
            }
            return mCancelCurrentEvent
        }

        fun setRootLayoutListener(rootLayoutListener: RootLayoutListener?) {
            mRootLayoutListener = rootLayoutListener
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            mRootLayoutListener?.onSizeChanged(w, h)
        }

        interface RootLayoutListener {
            fun onAwake()
            fun onSizeChanged(width: Int, height: Int)
            fun isAwake(): Boolean
        }
    }

    /**
     * ScrollView that exposes its scroll range.
     */
    class ExposedScrollView : ScrollView {
        constructor(context: Context) : super(context)

        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
            context,
            attrs,
            defStyle
        )

        public override fun computeVerticalScrollRange(): Int {
            return super.computeVerticalScrollRange()
        }

        companion object {
            @JvmField
            val SCROLL_POS: Property<ScrollView, Int> =
                object : Property<ScrollView, Int>(Int::class.javaObjectType, "scrollPos") {
                    override fun set(`object`: ScrollView, value: Int) {
                        `object`.scrollTo(0, value)
                    }

                    override fun get(`object`: ScrollView): Int {
                        return `object`.scrollY
                    }
                }
        }
    }

    companion object {
        const val PREF_DAYDREAM_COLOR = "pref_daydream_color"
        const val PREF_DAYDREAM_NIGHT_MODE = "pref_daydream_night_mode"
        const val PREF_DAYDREAM_ANIMATION = "pref_daydream_animation"

        private const val ANIMATION_HAS_ROTATE = 0x1
        private const val ANIMATION_HAS_SLIDE = 0x2
        private const val ANIMATION_HAS_FADE = 0x4

        private const val ANIMATION_NONE = 0
        private const val ANIMATION_FADE = ANIMATION_HAS_FADE
        private const val ANIMATION_SLIDE = ANIMATION_FADE or ANIMATION_HAS_SLIDE
        private const val ANIMATION_PENDULUM = ANIMATION_SLIDE or ANIMATION_HAS_ROTATE

        private const val CYCLE_INTERVAL_MILLIS = 20 * SECONDS_MILLIS
        private const val FADE_MILLIS = 5 * SECONDS_MILLIS
        private const val TRAVEL_ROTATE_DEGREES = 3
        private const val SCALE_WHEN_MOVING = 0.85f
    }
}
