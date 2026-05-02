package app.lawnchair

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RectF
import android.view.RemoteAnimationTarget
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.views.overlay.AppOpenAnimationType
import com.android.launcher3.QuickstepTransitionManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.util.BackAnimState
import com.patrykmichalik.opto.core.firstBlocking

/**
 * Lawnchair custom [QuickstepTransitionManager].
 *
 * CLOSING ANIMATION DESIGN (fully ported from old LawnchairAppTransitionManagerImpl):
 *
 * Old code used createLauncherResumeAnimation() which animated mDragLayer directly:
 *   - mDragLayer.scaleX/Y: APP_CLOSE_HOME_ENTER_SCALE_FROM(1.5) → TO(1.0), 250ms
 *   - dragLayerAlpha:       APP_CLOSE_HOME_ENTER_ALPHA_FROM(0.0) → TO(1.0), 100ms
 *   - mDragLayer.setLayerType(LAYER_TYPE_HARDWARE)
 *
 * QuickstepTransitionManager has no createLauncherResumeAnimation().
 * Its getLauncherContentAnimator(false) animates workspace + hotseat separately.
 * We cannot call it (private), so we animate mDragLayer directly instead —
 * which is what the old code did and what the user sees as correct.
 *
 * We override createWallpaperOpenAnimations, call super for the app-window spring,
 * then immediately set mDragLayer start values and schedule our animator.
 * mDragLayer is protected in the base class, accessible here.
 */
class LawnchairQuickstepTransitionManager(
    private val launcher: QuickstepLauncher,
) : QuickstepTransitionManager(launcher) {

    private val prefs2 by lazy { PreferenceManager2.getInstance(launcher) }

    // ── Constants from old LawnchairAppTransitionManagerImpl companion object ──

    // App close: home (dragLayer) enter
    private val closeHomeEnterScaleFrom   = 1.5f
    private val closeHomeEnterScaleTo     = 1.0f
    private val closeHomeEnterScaleDur    = 250L
    private val closeHomeEnterScaleInterp = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)

    private val closeHomeEnterAlphaFrom   = 0.0f
    private val closeHomeEnterAlphaTo     = 1.0f
    private val closeHomeEnterAlphaDur    = 100L
    private val closeHomeEnterAlphaInterp = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)

    // ── createWallpaperOpenAnimations override ────────────────────────────────

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

        // For all custom types: set mDragLayer initial state BEFORE calling
        // super so there is no single-frame flash of the normal state.
        if (animType == AppOpenAnimationType.PIE ||
            animType == AppOpenAnimationType.SLIDE_UP
        ) {
            // PIE close: dragLayer zooms in from enlarged state.
            // Reset pivot to centre — it may have been moved during the opening
            // animation's icon-pivot logic.
            mDragLayer.pivotX = mDragLayer.width  / 2f
            mDragLayer.pivotY = mDragLayer.height / 2f
            mDragLayer.scaleX = closeHomeEnterScaleFrom
            mDragLayer.scaleY = closeHomeEnterScaleFrom
            mDragLayer.alpha  = closeHomeEnterAlphaFrom
            mDragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        // Run base class — handles the app-window spring (RectFSpringAnim) and
        // workspace reveal. We do NOT interfere with those.
        val baseState = super.createWallpaperOpenAnimations(
            appTargets, wallpapers, nonAppTargets,
            startRect, startWindowCornerRadius, fromPredictiveBack,
        )

        // Now schedule our custom launcher-side (mDragLayer) animation.
        when (animType) {
            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP,
            -> playPieCloseDragLayerAnimation()

            AppOpenAnimationType.BLINK -> playBlinkCloseDragLayerAnimation()
            AppOpenAnimationType.FADE  -> playFadeCloseDragLayerAnimation()

            // DEFAULT, REVEAL, SCALE_UP — base class handles.
            else -> Unit
        }

        return baseState
    }

    // ── dragLayer close animations — ported 1:1 from old createLauncherResumeAnimation ──

    /**
     * PIE / SLIDE_UP close: dragLayer scales 1.5→1.0 (zoom-in) and alpha 0→1.
     * Exactly mirrors old LawnchairAppTransitionManagerImpl.createLauncherResumeAnimation()
     * for the useScaleAnim=true path.
     */
    private fun playPieCloseDragLayerAnimation() {
        val layer = mDragLayer

        val scaleAnim = ObjectAnimator.ofFloat(layer, View.SCALE_X, closeHomeEnterScaleFrom, closeHomeEnterScaleTo).apply {
            duration     = closeHomeEnterScaleDur
            interpolator = closeHomeEnterScaleInterp
        }
        val scaleAnimY = ObjectAnimator.ofFloat(layer, View.SCALE_Y, closeHomeEnterScaleFrom, closeHomeEnterScaleTo).apply {
            duration     = closeHomeEnterScaleDur
            interpolator = closeHomeEnterScaleInterp
        }
        val alphaAnim = ObjectAnimator.ofFloat(layer, View.ALPHA, closeHomeEnterAlphaFrom, closeHomeEnterAlphaTo).apply {
            duration     = closeHomeEnterAlphaDur
            interpolator = closeHomeEnterAlphaInterp
        }

        AnimatorSet().apply {
            playTogether(scaleAnim, scaleAnimY, alphaAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.scaleX = 1f
                    layer.scaleY = 1f
                    layer.alpha  = 1f
                    layer.pivotX = layer.width  / 2f
                    layer.pivotY = layer.height / 2f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * BLINK close: dragLayer flashes three times while fading in.
     * Mirrors BlinkAnimation.overrideResumeAnimation — blink_close_enter plays on
     * the launcher side, but since that's a window-level anim we replicate it here
     * on the dragLayer View so it works with Quickstep.
     */
    private fun playBlinkCloseDragLayerAnimation() {
        val layer = mDragLayer
        layer.alpha = 0f
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                layer.alpha = when {
                    t < 0.20f -> 0.0f  // invisible start
                    t < 0.40f -> 1.0f  // flash 1 on
                    t < 0.60f -> 0.0f  // flash 1 off
                    t < 0.80f -> 1.0f  // flash 2 on
                    else      -> 1.0f  // settle visible
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.alpha = 1f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    /**
     * FADE close: dragLayer fades from 0→1 as the launcher appears.
     * Mirrors FadeAnimation.overrideResumeAnimation but on dragLayer View.
     */
    private fun playFadeCloseDragLayerAnimation() {
        val layer = mDragLayer
        layer.alpha = 0f
        layer.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        ObjectAnimator.ofFloat(layer, View.ALPHA, 0f, 1f).apply {
            duration     = 250L
            interpolator = closeHomeEnterAlphaInterp
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    layer.alpha = 1f
                    layer.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }
}
