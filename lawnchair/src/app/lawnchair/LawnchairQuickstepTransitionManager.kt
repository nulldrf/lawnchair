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
 * Lawnchair's custom [QuickstepTransitionManager] that overrides the
 * app-to-home (closing) transition for custom animation types.
 *
 * WHY PREVIOUS APPROACH FAILED:
 * [createWallpaperOpenAnimations] in the base class adds either a
 * ScalingWorkspaceRevealAnim or StaggeredWorkspaceAnim which animate
 * workspace + hotseat (scale, alpha, translateY). Our previous attempt
 * also animated those same views, causing undefined fighting behaviour —
 * two ObjectAnimators on the same View property where whichever writes
 * last per frame wins.
 *
 * FIX — Full-screen overlay approach:
 * 1. Let super() run completely — workspace reveal, spring animation, all of it.
 *    We do NOT touch workspace or hotseat.
 * 2. Add a full-screen opaque View to mDragLayer.parent (above dragLayer,
 *    not inside it — so it is not affected by any scale on dragLayer).
 * 3. Animate the overlay away according to the animation type:
 *      PIE   → overlay scales 1.5→1.0 + fades 1→0  (zoom-in feel)
 *      BLINK → overlay flashes 3 times then disappears
 *      FADE  → overlay fades 1→0
 * 4. As the overlay peels away, the base class workspace reveal
 *    (running beneath the overlay the whole time) is revealed naturally.
 *
 * Zero interference with the base class. The overlay just covers and
 * uncovers the launcher with our custom animation.
 */
class LawnchairQuickstepTransitionManager(
    private val launcher: QuickstepLauncher,
) : QuickstepTransitionManager(launcher) {

    private val prefs2 by lazy { PreferenceManager2.getInstance(launcher) }

    private val overlayColor: Int
        get() {
            val ta = launcher.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
            val color = ta.getColor(0, Color.BLACK)
            ta.recycle()
            return color
        }

    override fun createWallpaperOpenAnimations(
        appTargets: Array<RemoteAnimationTarget>,
        wallpapers: Array<RemoteAnimationTarget>,
        nonAppTargets: Array<RemoteAnimationTarget>,
        startRect: RectF,
        startWindowCornerRadius: Float,
        fromPredictiveBack: Boolean,
    ): BackAnimState {
        // Always let the base class run fully — RectFSpringAnim, workspace reveal, etc.
        val baseState = super.createWallpaperOpenAnimations(
            appTargets, wallpapers, nonAppTargets,
            startRect, startWindowCornerRadius, fromPredictiveBack,
        )

        val animType = runCatching { prefs2.appOpenAnimation.firstBlocking() }
            .getOrDefault(AppOpenAnimationType.DEFAULT)

        when (animType) {
            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP,
            -> playOverlay(OverlayStyle.PIE)
            AppOpenAnimationType.BLINK  -> playOverlay(OverlayStyle.BLINK)
            AppOpenAnimationType.FADE   -> playOverlay(OverlayStyle.FADE)
            else -> Unit
        }

        return baseState
    }

    private enum class OverlayStyle { PIE, BLINK, FADE }

    /**
     * Adds a full-screen opaque overlay to mDragLayer.parent and animates
     * it away, revealing the launcher beneath.
     *
     * mDragLayer is protected in QuickstepTransitionManager so it is
     * accessible here without reflection.
     */
    private fun playOverlay(style: OverlayStyle) {
        val parent = mDragLayer.parent as? ViewGroup ?: return

        val w = mDragLayer.width.takeIf { it > 0 } ?: return
        val h = mDragLayer.height.takeIf { it > 0 } ?: return

        val overlay = FrameLayout(launcher).apply {
            setBackgroundColor(overlayColor)
            alpha  = 1f
            scaleX = if (style == OverlayStyle.PIE) 1.5f else 1.0f
            scaleY = if (style == OverlayStyle.PIE) 1.5f else 1.0f
            pivotX = w / 2f
            pivotY = h / 2f
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        parent.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val cleanup = {
            overlay.setLayerType(View.LAYER_TYPE_NONE, null)
            parent.removeView(overlay)
        }

        val endListener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = cleanup()
            override fun onAnimationCancel(animation: Animator) = cleanup()
        }

        val interp    = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
        val duration  = 250L

        when (style) {
            OverlayStyle.PIE -> {
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(overlay, View.SCALE_X, 1.5f, 1.0f).apply {
                            this.duration = duration; interpolator = interp
                        },
                        ObjectAnimator.ofFloat(overlay, View.SCALE_Y, 1.5f, 1.0f).apply {
                            this.duration = duration; interpolator = interp
                        },
                        ObjectAnimator.ofFloat(overlay, View.ALPHA, 1.0f, 0.0f).apply {
                            this.duration = duration; interpolator = interp
                        },
                    )
                    addListener(endListener)
                    start()
                }
            }
            OverlayStyle.BLINK -> {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    this.duration = 400L
                    addUpdateListener { anim ->
                        val t = anim.animatedFraction
                        overlay.alpha = when {
                            t < 0.20f -> 1.0f
                            t < 0.40f -> 0.0f
                            t < 0.60f -> 1.0f
                            t < 0.80f -> 0.0f
                            else      -> 0.0f
                        }
                    }
                    addListener(endListener)
                    start()
                }
            }
            OverlayStyle.FADE -> {
                ObjectAnimator.ofFloat(overlay, View.ALPHA, 1.0f, 0.0f).apply {
                    this.duration = duration
                    interpolator  = interp
                    addListener(endListener)
                    start()
                }
            }
        }
    }
}
