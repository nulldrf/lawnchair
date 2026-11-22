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
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import app.lawnchair.views.overlay.AppOpenAnimationType
import com.android.app.animation.Interpolators.AGGRESSIVE_EASE
import com.android.app.animation.Interpolators.LINEAR
import com.android.app.animation.Interpolators.clampToProgress
import com.android.launcher3.BaseActivity.EVENT_RESUMED
import com.android.launcher3.BaseActivity.INVISIBLE_BY_APP_TRANSITIONS
import com.android.launcher3.BubbleTextView
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.quickstep.util.MultiValueUpdateListener
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 * 1:1 port of Lawnchair 9's `ch.deletescape.lawnchair.animations.AnimationType`.
 */
abstract class AnimationType {

    open fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
        return null
    }

    open fun playLaunchAnimation(
        launcher: Launcher,
        v: View?,
        intent: Intent?,
        controller: LawnchairAppTransitionController,
    ) {
    }

    open fun overrideResumeAnimation(launcher: Launcher) {
    }

    fun getBounds(v: View?): Rect? {
        if (v == null) return null
        var left = 0
        var top = 0
        var width = v.measuredWidth
        var height = v.measuredHeight
        if (v is BubbleTextView) {
            // Launch from center of icon, not entire view
            val icon = v.icon
            if (icon != null) {
                val bounds = icon.bounds
                left = (width - bounds.width()) / 2
                top = v.paddingTop
                width = bounds.width()
                height = bounds.height()
            }
        }
        return Rect(left, top, left + width, top + height)
    }

    class DefaultAnimation : AnimationType()

    class FadeAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions {
            return ActivityOptions.makeCustomAnimation(
                launcher,
                R.anim.fade_in_short,
                R.anim.no_anim_short,
            )
        }

        @Suppress("DEPRECATION")
        override fun overrideResumeAnimation(launcher: Launcher) {
            launcher.overridePendingTransition(R.anim.no_anim_short, R.anim.fade_out_short)
        }
    }

    class BlinkAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions {
            return ActivityOptions.makeCustomAnimation(
                launcher,
                R.anim.blink_open_enter,
                R.anim.blink_open_exit,
            )
        }

        @Suppress("DEPRECATION")
        override fun overrideResumeAnimation(launcher: Launcher) {
            launcher.overridePendingTransition(R.anim.blink_close_enter, R.anim.blink_close_exit)
        }
    }

    class ScaleUpAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            val bounds = getBounds(v) ?: return super.getActivityLaunchOptions(launcher, v)
            return ActivityOptions.makeScaleUpAnimation(
                v,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
            )
        }
    }

    class SlideUpAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions {
            return ActivityOptions.makeCustomAnimation(
                launcher,
                R.anim.task_open_enter,
                R.anim.no_anim,
            )
        }
    }

    class RevealAnimation : AnimationType() {

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions? {
            val bounds = getBounds(v) ?: return super.getActivityLaunchOptions(launcher, v)
            return ActivityOptions.makeClipRevealAnimation(
                v,
                bounds.left,
                bounds.top,
                bounds.width(),
                bounds.height(),
            )
        }
    }

    class PieAnimation : AnimationType() {

        private var lastView: WeakReference<View>? = null

        override fun getActivityLaunchOptions(launcher: Launcher, v: View?): ActivityOptions {
            lastView = v?.let { WeakReference(it) }
            return ActivityOptions.makeCustomAnimation(
                launcher,
                R.anim.dummy_anim_enter,
                R.anim.dummy_anim_exit,
            )
        }

        override fun playLaunchAnimation(
            launcher: Launcher,
            v: View?,
            intent: Intent?,
            controller: LawnchairAppTransitionController,
        ) {
            val view = v ?: lastView?.get() ?: return
            lastView = null

            val rootView = launcher.dragLayer.parent as? ViewGroup ?: return
            val dp = launcher.deviceProfile
            val widthPx = dp.deviceProperties.widthPx
            val heightPx = dp.deviceProperties.heightPx
            if (widthPx <= 0 || heightPx <= 0) return

            val anim = AnimatorSet()

            val splashData = SplashResolver.INSTANCE.get(launcher).loadSplash(intent)
            val splashView = SplashLayout(launcher).apply {
                alpha = 0f
                applySplash(splashData)
            }
            val splashLp = InsettableFrameLayout.LayoutParams(widthPx, heightPx).apply {
                ignoreInsets = true
            }
            // Add splashView BEFORE playIconAnimators adds floatingView so the floating icon
            // renders on top of the expanding splash during the initial crossfade (1:1 LC9).
            rootView.addView(splashView, splashLp)

            // Set the state animation first so that any state listeners are called
            // before our internal listeners.
            launcher.stateManager.setCurrentAnimation(anim)

            val windowTargetBounds = getWindowTargetBounds(launcher)
            val floatingView = controller.playIconAnimators(anim, view, windowTargetBounds)
            if (floatingView == null) {
                rootView.removeView(splashView)
                return
            }

            val launcherContentAnimator = controller.getLauncherContentAnimator(isAppOpening = true)
            anim.play(launcherContentAnimator.first)
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    launcher.addForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS)
                }

                override fun onAnimationEnd(animation: Animator) {
                    launcher.clearForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS)
                }
            })
            anim.play(
                getOpeningWindowAnimators(
                    launcher = launcher,
                    v2 = view,
                    splashView = splashView,
                    floatingView = floatingView,
                    rootView = rootView,
                    windowTargetBounds = windowTargetBounds,
                ),
            )

            // Keep splashView & faded launcher content in place until Launcher actually resumes
            // so there is never a gap/flash back to the home screen while the app draws (1:1 LC9).
            launcher.addEventCallback(EVENT_RESUMED) {
                if (anim.isRunning) {
                    anim.cancel()
                }
                rootView.removeView(splashView)
                launcherContentAnimator.second.run()
            }

            anim.start()
        }

        @Suppress("DEPRECATION")
        override fun overrideResumeAnimation(launcher: Launcher) {
            launcher.overridePendingTransition(
                R.anim.pie_like_close_enter,
                R.anim.pie_like_close_exit,
            )
        }

        private fun getWindowTargetBounds(launcher: Launcher): Rect {
            val props = launcher.deviceProfile.deviceProperties
            return Rect(0, 0, props.widthPx, props.heightPx)
        }

        private fun getOpeningWindowAnimators(
            launcher: Launcher,
            v2: View,
            splashView: SplashLayout,
            floatingView: View,
            rootView: ViewGroup,
            windowTargetBounds: Rect,
        ): ValueAnimator {
            val v = if (v2 is FolderIcon) v2.folderName else v2

            val bounds = Rect()
            when {
                v.parent is DeepShortcutView -> {
                    // Deep shortcut views have their icon drawn in a separate view.
                    val view = v.parent as DeepShortcutView
                    launcher.dragLayer.getDescendantRectRelativeToSelf(view.iconView, bounds)
                }

                v is BubbleTextView -> v.getIconBounds(bounds)

                else -> launcher.dragLayer.getDescendantRectRelativeToSelf(v, bounds)
            }

            val floatingViewBounds = IntArray(2)
            val rootViewBounds = IntArray(2)
            val crop = Rect()

            // Leave ValueAnimator's default interpolator (AccelerateDecelerateInterpolator)
            // untouched, matching Lawnchair 9 / AOSP Pie's LauncherAppTransitionManagerImpl.
            val appAnimator = ValueAnimator.ofFloat(0f, 1f)
            appAnimator.duration = LawnchairAppTransitionController.APP_LAUNCH_DURATION
            appAnimator.addUpdateListener(object : MultiValueUpdateListener() {
                // Fade alpha for the app window: 0 -> 1 over first 60ms of 500ms, LINEAR.
                val mAlpha = FloatProp(
                    0f,
                    1f,
                    clampToProgress(LINEAR, 0f, 60f / LawnchairAppTransitionController.APP_LAUNCH_DURATION),
                )

                override fun onUpdate(percent: Float, initOnly: Boolean) {
                    val easePercent = AGGRESSIVE_EASE.getInterpolation(percent)

                    // Calculate app icon size.
                    val iconWidth = bounds.width() * floatingView.scaleX
                    val iconHeight = bounds.height() * floatingView.scaleY

                    // Scale the app window to match the icon size.
                    val windowWidth = windowTargetBounds.width()
                    val windowHeight = windowTargetBounds.height()
                    val scaleX = iconWidth / windowWidth
                    val scaleY = iconHeight / windowHeight
                    val scale = min(1f, min(scaleX, scaleY))

                    // Position the scaled window on top of the icon.
                    val scaledWindowWidth = windowWidth * scale
                    val scaledWindowHeight = windowHeight * scale

                    val offsetX = (scaledWindowWidth - iconWidth) / 2f
                    val offsetY = (scaledWindowHeight - iconHeight) / 2f
                    floatingView.getLocationOnScreen(floatingViewBounds)
                    rootView.getLocationOnScreen(rootViewBounds)

                    val transX0 = floatingViewBounds[0] - rootViewBounds[0] - offsetX
                    val transY0 = floatingViewBounds[1] - rootViewBounds[1] - offsetY

                    // Animate the window crop so that it starts off as a square, and then reveals
                    // to the full window aspect ratio.
                    if (windowHeight >= windowWidth) {
                        val cropHeight = windowHeight * easePercent + windowWidth * (1f - easePercent)
                        val initialTop = (windowHeight - windowWidth) / 2f
                        crop.left = 0
                        crop.top = (initialTop * (1f - easePercent)).toInt()
                        crop.right = windowWidth
                        crop.bottom = (crop.top + cropHeight).toInt()
                    } else {
                        val cropWidth = windowWidth * easePercent + windowHeight * (1f - easePercent)
                        val initialLeft = (windowWidth - windowHeight) / 2f
                        crop.left = (initialLeft * (1f - easePercent)).toInt()
                        crop.top = 0
                        crop.right = (crop.left + cropWidth).toInt()
                        crop.bottom = windowHeight
                    }

                    splashView.pivotX = 0f
                    splashView.pivotY = 0f
                    splashView.scaleX = scale
                    splashView.scaleY = scale
                    splashView.translationX = transX0
                    splashView.translationY = transY0
                    splashView.alpha = mAlpha.value
                    splashView.setCrop(crop)
                }
            })
            return appAnimator
        }
    }

    companion object {
        fun from(type: AppOpenAnimationType): AnimationType {
            return when (type) {
                AppOpenAnimationType.PIE -> PieAnimation()
                AppOpenAnimationType.REVEAL -> RevealAnimation()
                AppOpenAnimationType.SLIDE_UP -> SlideUpAnimation()
                AppOpenAnimationType.SCALE_UP -> ScaleUpAnimation()
                AppOpenAnimationType.BLINK -> BlinkAnimation()
                AppOpenAnimationType.FADE -> FadeAnimation()
                AppOpenAnimationType.DEFAULT -> DefaultAnimation()
            }
        }
    }
}
