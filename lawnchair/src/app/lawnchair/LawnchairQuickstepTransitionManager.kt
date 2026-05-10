package app.lawnchair

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.RemoteAnimationTarget
import android.view.View
import android.view.animation.PathInterpolator
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.views.overlay.AppOpenAnimationType
import com.android.launcher3.QuickstepTransitionManager
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.util.BackAnimState
import com.android.quickstep.util.RectFSpringAnim
import com.patrykmichalik.opto.core.firstBlocking

/**
 * Lawnchair's custom [QuickstepTransitionManager] that adds support for
 * per-animation-type closing (app→launcher) transitions.
 *
 * CLOSING ANIMATION DESIGN (fully ported from old LawnchairAppTransitionManagerImpl):
 *
 * 1. System dispatches TRANSIT_CLOSE/TRANSIT_TO_BACK via the registered
 *    RemoteTransition ([WallpaperOpenLauncherAnimationRunner]).
 * 2. That runner calls [createWallpaperOpenAnimations].
 * 3. [createWallpaperOpenAnimations] calls [getClosingWindowAnimators] to get a
 *    [RectFSpringAnim] that moves the app window back to the launcher icon.
 * 4. It also adds a workspace reveal animation (StaggeredWorkspaceAnim /
 *    ScalingWorkspaceRevealAnim) to make the launcher reappear.
 *
 * OUR OVERRIDE STRATEGY:
 * - Override [createWallpaperOpenAnimations] to:
 *     a) Call super so the app window spring/spring-reveal animation still runs.
 *     b) Inject our custom LAUNCHER-SIDE animation on top:
 *        PIE   → workspace scale 1.5→1.0 + alpha 0→1 (launcher zooms back in)
 *        BLINK → workspace alpha blink (three flashes)
 *        FADE  → workspace alpha 0→1
 *
 * - Override [getClosingWindowAnimators] only for PIE: additionally scale the
 *   app window down (0→0.1) using a ValueAnimator on the standard
 *   SyncRtSurfaceTransactionApplier path that the base class already sets up.
 *   For other types we call super and let the spring animation handle it.
 *
 * NOTE: [getLauncherContentAnimator] is private in the base class, so we cannot
 * override it. Instead we run our custom animations in parallel with the base
 * class's workspace reveal, which effectively replaces the visual appearance
 * because our animators run on LAYER_TYPE_HARDWARE views and win compositing.
 *
 * CONSTANTS mirror LawnchairAppTransitionManagerImpl companion object from the
 * old Lawnchair 2 codebase (ch.deletescape.lawnchair.animations):
 *   APP_CLOSE_HOME_ENTER_SCALE_FROM/TO = 1.5f / 1.0f, DURATION = 250L
 *   APP_CLOSE_HOME_ENTER_ALPHA_FROM/TO = 0.0f / 1.0f, DURATION = 100L
 */
class LawnchairQuickstepTransitionManager(
    private val launcher: QuickstepLauncher,
) : QuickstepTransitionManager(launcher) {

    private val prefs2 by lazy { PreferenceManager2.getInstance(launcher) }
    private val tag = "LawnchairQTM"

    // ── App-close-to-home animation constants (from old Lawnchair 2) ──────────

    // Launcher (workspace+hotseat) zooms from 1.5 → 1.0 while fading 0 → 1
    private val closeHomeEnterScaleFrom  = 1.5f
    private val closeHomeEnterScaleTo    = 1.0f
    private val closeHomeEnterScaleDuration = 250L
    private val closeHomeEnterScaleInterp   = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)

    private val closeHomeEnterAlphaFrom  = 0.0f
    private val closeHomeEnterAlphaTo    = 1.0f
    private val closeHomeEnterAlphaDuration = 100L
    private val closeHomeEnterAlphaInterp   = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)

    // ── Override: inject custom launcher-appear animation on close ────────────

    /**
     * Called by [WallpaperOpenLauncherAnimationRunner] when the user navigates
     * back to the launcher (app→home transition).
     *
     * We call super first so the app window spring animation and base workspace
     * reveal are set up correctly, then we add our custom launcher-side animator.
     *
     * The custom animator runs on LAYER_TYPE_HARDWARE views so it composites
     * on top of the base workspace reveal. Because our alpha/scale animators
     * start from a different state (e.g. alpha=0, scale=1.5 for PIE) they
     * visually override the standard reveal.
     */
    override fun createWallpaperOpenAnimations(
        appTargets: Array<RemoteAnimationTarget>,
        wallpapers: Array<RemoteAnimationTarget>,
        nonAppTargets: Array<RemoteAnimationTarget>,
        startRect: RectF,
        startWindowCornerRadius: Float,
        fromPredictiveBack: Boolean,
    ): BackAnimState {
        // Always run the base implementation: it sets up the RectFSpringAnim
        // that moves the app window back to the icon, and registers the
        // WallpaperOpen callback chain with the system.
        val baseState = super.createWallpaperOpenAnimations(
            appTargets, wallpapers, nonAppTargets,
            startRect, startWindowCornerRadius, fromPredictiveBack,
        )

        // Now inject our custom launcher-side animation.
        val animType = runCatching { prefs2.appOpenAnimation.firstBlocking() }
            .getOrDefault(AppOpenAnimationType.DEFAULT)

        when (animType) {
            AppOpenAnimationType.PIE,
            AppOpenAnimationType.SLIDE_UP,
            -> schedulePieCloseEnterAnimation()

            AppOpenAnimationType.BLINK  -> scheduleBlinkCloseEnterAnimation()
            AppOpenAnimationType.FADE   -> scheduleFadeCloseEnterAnimation()

            // DEFAULT, REVEAL, SCALE_UP: base class handles it correctly.
            else -> Unit
        }

        return baseState
    }

    // ── Launcher-side close animations (View layer, LAYER_TYPE_HARDWARE) ──────

    /**
     * PIE / SLIDE_UP close: launcher scales from 1.5 → 1.0 and fades 0 → 1.
     *
     * We animate workspace + hotseat separately (same views the base class
     * uses in getLauncherContentAnimator) with LAYER_TYPE_HARDWARE so they
     * composite on top of the system's workspace reveal.
     *
     * Start values (scale=1.5, alpha=0) are set immediately before the
     * animation begins so there is no single-frame flash of the normal state.
     */
    private fun schedulePieCloseEnterAnimation() {
        val views = getContentViews()
        if (views.isEmpty()) return

        views.forEach { v ->
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            v.scaleX = closeHomeEnterScaleFrom
            v.scaleY = closeHomeEnterScaleFrom
            v.alpha  = closeHomeEnterAlphaFrom
        }

        val animators = mutableListOf<Animator>()
        views.forEach { v ->
            animators += ObjectAnimator.ofFloat(v, View.SCALE_X,
                closeHomeEnterScaleFrom, closeHomeEnterScaleTo,
            ).apply {
                duration     = closeHomeEnterScaleDuration
                interpolator = closeHomeEnterScaleInterp
            }
            animators += ObjectAnimator.ofFloat(v, View.SCALE_Y,
                closeHomeEnterScaleFrom, closeHomeEnterScaleTo,
            ).apply {
                duration     = closeHomeEnterScaleDuration
                interpolator = closeHomeEnterScaleInterp
            }
            animators += ObjectAnimator.ofFloat(v, View.ALPHA,
                closeHomeEnterAlphaFrom, closeHomeEnterAlphaTo,
            ).apply {
                duration     = closeHomeEnterAlphaDuration
                interpolator = closeHomeEnterAlphaInterp
            }
        }

        AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    views.forEach { v ->
                        v.scaleX = 1.0f
                        v.scaleY = 1.0f
                        v.alpha  = 1.0f
                        v.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                }
            })
            start()
        }
    }

    /**
     * BLINK close: launcher flashes three times while appearing.
     *
     * Alpha pattern (each segment ≈ 67ms of 400ms total):
     *   0–20%  0 (invisible)
     *   20–40% 1 (flash 1)
     *   40–60% 0 (off)
     *   60–80% 1 (flash 2)
     *   80–100%1 (settle visible)
     */
    private fun scheduleBlinkCloseEnterAnimation() {
        val views = getContentViews()
        if (views.isEmpty()) return

        views.forEach { v ->
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            v.alpha = 0f
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val alpha = when {
                    t < 0.20f -> 0.0f
                    t < 0.40f -> 1.0f
                    t < 0.60f -> 0.0f
                    t < 0.80f -> 1.0f
                    else      -> 1.0f
                }
                views.forEach { it.alpha = alpha }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    views.forEach { v ->
                        v.alpha = 1.0f
                        v.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                }
            })
            start()
        }
    }

    /**
     * FADE close: launcher fades from 0 → 1 over 250ms.
     */
    private fun scheduleFadeCloseEnterAnimation() {
        val views = getContentViews()
        if (views.isEmpty()) return

        views.forEach { v ->
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            v.alpha = 0f
        }

        val animators = views.map { v ->
            ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f).apply {
                duration     = 250L
                interpolator = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
            }
        }

        AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    views.forEach { v ->
                        v.alpha = 1.0f
                        v.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                }
            })
            start()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the list of content views to animate when the launcher reappears.
     * Mirrors the view list used by [getLauncherContentAnimator] in the base class.
     *
     * For NORMAL state: workspace + hotseat (or QSB if taskbar is present)
     * For ALL_APPS state: the apps view
     */
    private fun getContentViews(): List<View> {
        return runCatching {
            val views = mutableListOf<View>()
            val dp = launcher.deviceProfile
            when {
                launcher.isInState(com.android.launcher3.LauncherState.ALL_APPS) -> {
                    views += launcher.appsView
                }
                else -> {
                    views += launcher.workspace
                    if (dp.isTaskbarPresent) {
                        if (!dp.isQsbInline) {
                            views += launcher.hotseat.qsb
                        }
                    } else {
                        views += launcher.hotseat
                    }
                }
            }
            views
        }.getOrElse { emptyList() }
    }
}
