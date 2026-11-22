/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.lawnchair.animations

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.views.overlay.AppOpenAnimationType
import com.android.app.animation.Interpolators.AGGRESSIVE_EASE
import com.android.app.animation.Interpolators.EXAGGERATED_EASE
import com.android.app.animation.Interpolators.LINEAR
import com.android.launcher3.BubbleTextView
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE
import com.android.quickstep.views.TaskView
import kotlin.math.abs
import kotlin.math.max

/**
 * Manages custom app launch and resume transitions ported 1:1 from Lawnchair 9's
 * `LawnchairAppTransitionManagerImpl` and AOSP Pie's `LauncherAppTransitionManagerImpl`.
 */
class LawnchairAppTransitionController(
    private val launcher: LawnchairLauncher,
) {
    private val prefs2 by lazy { PreferenceManager2.getInstance(launcher) }
    private val dragLayer: DragLayer get() = launcher.dragLayer
    private val dragLayerAlpha get() = dragLayer.getAlphaProperty(DragLayer.ALPHA_INDEX_TRANSITIONS)
    private val isRtl: Boolean get() = Utilities.isRtl(launcher.resources)
    private val contentTransY: Float
        get() = launcher.resources.getDimensionPixelSize(R.dimen.content_trans_y).toFloat()

    private var cachedTypeKey: AppOpenAnimationType? = null
    private var cachedAnimationType: AnimationType = AnimationType.DefaultAnimation()
    private var prevScrimMultiplier: Float? = null

    var floatingView: View? = null
        private set

    private fun currentAnimationType(): AnimationType {
        val selected = prefs2.appOpenAnimation.firstCached()
        if (selected != cachedTypeKey) {
            cachedTypeKey = selected
            cachedAnimationType = AnimationType.from(selected)
        }
        return cachedAnimationType
    }

    private fun isLaunchingFromRecents(v: View?): Boolean {
        return launcher.stateManager.state.isRecentsViewVisible && v is TaskView
    }

    fun getActivityLaunchOptions(v: View?): ActivityOptions? {
        if (isLaunchingFromRecents(v)) {
            return null
        }
        return currentAnimationType().getActivityLaunchOptions(launcher, v)
    }

    fun playLaunchAnimation(v: View?, intent: Intent?) {
        if (!isLaunchingFromRecents(v)) {
            currentAnimationType().playLaunchAnimation(launcher, v, intent, this)
        }
    }

    fun overrideResumeAnimation() {
        currentAnimationType().overrideResumeAnimation(launcher)
    }

    /**
     * Content is everything on screen except the background and the floating view (if any).
     * 1:1 port of Lawnchair 9's `LauncherAppTransitionManagerImpl.getLauncherContentAnimator`.
     */
    fun getLauncherContentAnimator(isAppOpening: Boolean): Pair<AnimatorSet, Runnable> {
        val launcherAnimator = AnimatorSet()
        val endListener: Runnable

        val alphas = if (isAppOpening) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f)
        val trans = if (isAppOpening) floatArrayOf(0f, contentTransY) else floatArrayOf(-contentTransY, 0f)

        if (launcher.isInState(LauncherState.ALL_APPS)) {
            val appsView = launcher.appsView
            val startAlpha = appsView.alpha
            val startY = appsView.translationY
            appsView.alpha = alphas[0]
            appsView.translationY = trans[0]

            val alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, *alphas)
            alpha.duration = CONTENT_ALPHA_DURATION
            alpha.interpolator = LINEAR
            appsView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            alpha.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    appsView.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            launcherAnimator.play(alpha)

            val transY = ObjectAnimator.ofFloat(appsView, View.TRANSLATION_Y, *trans)
            transY.interpolator = AGGRESSIVE_EASE
            transY.duration = CONTENT_TRANS_DURATION
            launcherAnimator.play(transY)

            endListener = Runnable {
                if (launcher.isInState(LauncherState.ALL_APPS)) {
                    appsView.alpha = startAlpha
                    appsView.translationY = startY
                    appsView.scaleX = 1f
                    appsView.scaleY = 1f
                } else {
                    launcher.stateManager.reapplyState()
                }
                appsView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        } else {
            dragLayerAlpha.value = alphas[0]
            val alpha = ObjectAnimator.ofFloat(dragLayerAlpha, MULTI_PROPERTY_VALUE, *alphas)
            alpha.duration = CONTENT_ALPHA_DURATION
            alpha.interpolator = LINEAR
            launcherAnimator.play(alpha)

            dragLayer.translationY = trans[0]
            val transY = ObjectAnimator.ofFloat(dragLayer, View.TRANSLATION_Y, *trans)
            transY.interpolator = AGGRESSIVE_EASE
            transY.duration = CONTENT_TRANS_DURATION
            launcherAnimator.play(transY)

            val scrimMultiplier = launcher.rootView.sysUiScrim.sysUIMultiplier
            if (prevScrimMultiplier == null) {
                prevScrimMultiplier = scrimMultiplier.value
            }
            scrimMultiplier.updateValue(0f)

            // Pause page indicator animations as they lead to layer trashing.
            launcher.workspace.pageIndicator.pauseAnimations()
            dragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

            endListener = Runnable { resetContentView() }
        }
        return Pair(launcherAnimator, endListener)
    }

    /**
     * Animators for the "floating view" of the view used to launch the target.
     * 1:1 port of Lawnchair 9's `LauncherAppTransitionManagerImpl.playIconAnimators`.
     */
    fun playIconAnimators(
        appOpenAnimator: AnimatorSet,
        v2: View,
        windowTargetBounds: Rect,
    ): View? {
        val v: View = if (v2 is FolderIcon) v2.folderName else v2
        val isBubbleTextView = v is BubbleTextView
        val fromDeepShortcutView = v.parent is DeepShortcutView

        val floating = View(launcher)
        if (isBubbleTextView && v.tag is ItemInfoWithIcon) {
            val info = v.tag as ItemInfoWithIcon
            val shouldTheme = v.shouldUseTheme()
            val flags = if (shouldTheme) BitmapInfo.FLAG_THEMED else 0
            floating.background = info.newIcon(launcher, flags, shouldTheme)
        } else if (fromDeepShortcutView) {
            val shortcutView = v.parent as DeepShortcutView
            floating.background = shortcutView.iconView.background?.constantState?.newDrawable()
        } else if (v.tag is ItemInfoWithIcon) {
            floating.background = (v.tag as ItemInfoWithIcon).newIcon(launcher)
        }

        // Position the floating view exactly on top of the original.
        val rect = Rect()
        if (fromDeepShortcutView) {
            // Deep shortcut views have their icon drawn in a separate view.
            val view = v.parent as DeepShortcutView
            dragLayer.getDescendantRectRelativeToSelf(view.iconView, rect)
        } else {
            dragLayer.getDescendantRectRelativeToSelf(v, rect)
        }
        var viewLocationLeft = rect.left + dragLayer.left
        var viewLocationTop = rect.top + dragLayer.top

        var startScale = 1f
        if (isBubbleTextView && !fromDeepShortcutView) {
            v.getIconBounds(rect)
            val dr = v.icon
            if (dr is FastBitmapDrawable) {
                startScale = dr.getAnimatedScale()
            }
        } else {
            rect.set(0, 0, rect.width(), rect.height())
        }
        if (rect.width() <= 0 || rect.height() <= 0) {
            floatingView = null
            return null
        }

        viewLocationLeft += rect.left
        viewLocationTop += rect.top
        val viewLocationStart = if (isRtl) {
            windowTargetBounds.width() - (viewLocationLeft + rect.width())
        } else {
            viewLocationLeft
        }
        val lp = InsettableFrameLayout.LayoutParams(rect.width(), rect.height()).apply {
            ignoreInsets = true
            marginStart = viewLocationStart
            topMargin = viewLocationTop
        }
        floating.layoutParams = lp

        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        floating.left = viewLocationLeft
        floating.top = viewLocationTop
        floating.right = viewLocationLeft + rect.width()
        floating.bottom = viewLocationTop + rect.height()

        // Swap the two views in place.
        val rootView = dragLayer.parent as ViewGroup
        rootView.addView(floating)
        floatingView = floating

        if (v is BubbleTextView) {
            v.setIconVisible(false)
            v.setForceHideDot(true)
        } else {
            v.visibility = View.INVISIBLE
        }

        val dragLayerBounds = IntArray(2)
        dragLayer.getLocationOnScreen(dragLayerBounds)

        // Animate the app icon to the center of the window bounds in screen coordinates.
        val centerX = windowTargetBounds.centerX() - dragLayerBounds[0]
        val centerY = windowTargetBounds.centerY() - dragLayerBounds[1]

        val xPosition = if (isRtl) {
            windowTargetBounds.width() - lp.marginStart - rect.width()
        } else {
            lp.marginStart
        }
        val dX = centerX - xPosition - (lp.width / 2f)
        val dY = centerY - lp.topMargin - (lp.height / 2f)

        val x = ObjectAnimator.ofFloat(floating, View.TRANSLATION_X, 0f, dX)
        val y = ObjectAnimator.ofFloat(floating, View.TRANSLATION_Y, 0f, dY)

        // Use upward animation for apps that are either on the bottom half of the screen, or are
        // relatively close to the center.
        val useUpwardAnimation = lp.topMargin > centerY ||
            abs(dY) < launcher.deviceProfile.cellHeightPx
        if (useUpwardAnimation) {
            x.duration = APP_LAUNCH_CURVED_DURATION
            y.duration = APP_LAUNCH_DURATION
        } else {
            x.duration = (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_DURATION).toLong()
            y.duration = (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_CURVED_DURATION).toLong()
        }
        x.interpolator = AGGRESSIVE_EASE
        y.interpolator = AGGRESSIVE_EASE
        appOpenAnimator.play(x)
        appOpenAnimator.play(y)

        // Scale the app icon to take up the entire screen. This simplifies the math when
        // animating the app window position / scale.
        val maxScaleX = windowTargetBounds.width() / rect.width().toFloat()
        val maxScaleY = windowTargetBounds.height() / rect.height().toFloat()
        val scale = max(maxScaleX, maxScaleY)
        val scaleAnim = ObjectAnimator.ofFloat(floating, SCALE_PROPERTY, startScale, scale)
        scaleAnim.duration = APP_LAUNCH_DURATION
        scaleAnim.interpolator = EXAGGERATED_EASE
        appOpenAnimator.play(scaleAnim)

        // Fade out the app icon.
        val alpha = ObjectAnimator.ofFloat(floating, View.ALPHA, 1f, 0f)
        if (useUpwardAnimation) {
            alpha.startDelay = APP_LAUNCH_ALPHA_START_DELAY
            alpha.duration = APP_LAUNCH_ALPHA_DURATION
        } else {
            alpha.startDelay =
                (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_ALPHA_START_DELAY).toLong()
            alpha.duration =
                (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_ALPHA_DURATION).toLong()
        }
        alpha.interpolator = LINEAR
        appOpenAnimator.play(alpha)

        if (v is BubbleTextView) {
            v.setTextVisibility(v.shouldTextBeVisible())
            val textAlpha = v.createTextAlphaAnimator(false)
            textAlpha.duration = APP_LAUNCH_ALPHA_DURATION
            appOpenAnimator.play(textAlpha)
        }

        appOpenAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Reset launcher to normal state
                if (v is BubbleTextView) {
                    v.setIconVisible(true)
                    v.setTextVisibility(v.shouldTextBeVisible())
                    v.setForceHideDot(false)
                } else {
                    v.visibility = View.VISIBLE
                }
                rootView.removeView(floating)
                if (floatingView === floating) {
                    floatingView = null
                }
            }
        })

        return floating
    }

    private fun resetContentView() {
        launcher.workspace.pageIndicator.skipAnimationsToEnd()
        dragLayerAlpha.value = 1f
        dragLayer.setLayerType(View.LAYER_TYPE_NONE, null)
        dragLayer.translationY = 0f
        dragLayer.scaleX = 1f
        dragLayer.scaleY = 1f
        prevScrimMultiplier?.let { prev ->
            launcher.rootView.sysUiScrim.sysUIMultiplier.updateValue(prev)
            prevScrimMultiplier = null
        }
    }

    companion object {
        const val APP_LAUNCH_DURATION = 500L
        private const val APP_LAUNCH_CURVED_DURATION = APP_LAUNCH_DURATION / 2
        private const val APP_LAUNCH_DOWN_DUR_SCALE_FACTOR = 0.8f
        private const val APP_LAUNCH_ALPHA_START_DELAY = 32L
        private const val APP_LAUNCH_ALPHA_DURATION = 50L
        private const val CONTENT_ALPHA_DURATION = 217L
        private const val CONTENT_TRANS_DURATION = 350L
    }
}
