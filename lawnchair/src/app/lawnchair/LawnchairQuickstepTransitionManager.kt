package app.lawnchair

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.RemoteAnimationTarget
import android.view.View
import android.view.animation.PathInterpolator
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.views.overlay.AppOpenAnimationType
import com.android.launcher3.QuickstepTransitionManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.util.BackAnimState
import com.patrykmichalik.opto.core.firstBlocking

/**
 * Lawnchair's custom [QuickstepTransitionManager].
 *
 * Overrides app→home (closing) transition for PIE/BLINK/FADE types.
 * Animates mDragLayer directly — same as old createLauncherResumeAnimation().
 *
 * Only active on API 31+ system installs. For gesture-nav on non-system
 * installs, closing is handled by FullScreenOverlayView via GNC path.
 */
class LawnchairQuickstepTransitionManager(
    private val launcher: QuickstepLauncher,
) : QuickstepTransitionManager(launcher) {

    private val prefs2 by lazy { PreferenceManager2.getInstance(launcher) }

    private val closeScaleFrom   = 1.5f
    private val closeScaleTo     = 1.0f
    private val closeScaleDur    = 250L
    private val closeScaleInterp = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
    private val closeAlphaFrom   = 0.0f
    private val closeAlphaTo     = 1.0f
    private val closeAlphaDur    = 100L
    private val closeAlphaInterp = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)

    override fun createWallpaperOpenAnimations(
        appTargets: Array<RemoteAnimationTarget>,
        wallpapers: Array<RemoteAnimationTarget>,
        nonAppTargets: Array<RemoteAnimationTarget>,
        startRect: RectF,
        startWindowCornerRadius: Float,
        fromPredictiveBack: Boolean,
    ): BackAnimState {
        val animType = runCatching { prefs2.appOpenAnimation.firstBlocking() }
            .getOrDefault(AppOpenAnimationType.DEFAULT)

        // Set mDragLayer initial state BEFORE super() to avoid flash
        if (animType == AppOpenAnimationType.PIE ||
            animType == AppOpenAnimationType.SLIDE_UP
        ) {
            mDragLayer.pivotX = mDragLayer.width  / 2f
            mDragLayer.pivotY = mDragLayer.height / 2f
            mDragLayer.scaleX = closeScaleFrom
            mDragLayer.scaleY = closeScaleFrom
            mDragLayer.alpha  = closeAlphaFrom
            mDragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val baseState = super.createWallpaperOpenAnimations(
            appTargets, wallpapers, nonAppTargets,
            startRect, startWindowCornerRadius, fromPredictiveBack,
        )

        when (animType) {
            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP -> playPieClose()
            AppOpenAnimationType.BLINK    -> playBlinkClose()
            AppOpenAnimationType.FADE     -> playFadeClose()
            else -> Unit
        }

        return baseState
    }

    private fun playPieClose() {
        val layer = mDragLayer
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(layer, View.SCALE_X, closeScaleFrom, closeScaleTo).apply {
                    duration = closeScaleDur; interpolator = closeScaleInterp
                },
                ObjectAnimator.ofFloat(layer, View.SCALE_Y, closeScaleFrom, closeScaleTo).apply {
                    duration = closeScaleDur; interpolator = closeScaleInterp
                },
                ObjectAnimator.ofFloat(layer, View.ALPHA, closeAlphaFrom, closeAlphaTo).apply {
                    duration = closeAlphaDur; interpolator = closeAlphaInterp
                },
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.scaleX = 1f; layer.scaleY = 1f; layer.alpha = 1f
                    layer.pivotX = layer.width / 2f; layer.pivotY = layer.height / 2f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    private fun playBlinkClose() {
        val layer = mDragLayer
        layer.alpha = 0f
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                layer.alpha = when {
                    t < 0.20f -> 0f; t < 0.40f -> 1f
                    t < 0.60f -> 0f; else -> 1f
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.alpha = 1f; layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    private fun playFadeClose() {
        val layer = mDragLayer
        layer.alpha = 0f
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        ObjectAnimator.ofFloat(layer, View.ALPHA, 0f, 1f).apply {
            duration = 250L; interpolator = closeAlphaInterp
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.alpha = 1f; layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }
}
