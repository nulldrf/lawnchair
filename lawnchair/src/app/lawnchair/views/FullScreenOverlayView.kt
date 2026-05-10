package app.lawnchair.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.views.overlay.AppOpenAnimationType
import app.lawnchair.views.overlay.FullScreenOverlayMode
import com.patrykmichalik.opto.core.firstBlocking

class FullScreenOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var endX = 0f
    private var endY = 0f
    private val startColor = ColorTokens.ColorBackground.resolveColor(context)
    private val endColor = Color.TRANSPARENT

    private var cornerRadius = 0f

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(startColor)
        clipToOutline = true
    }

    fun pointyEndView(view: View) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        endX = location[0] + view.width / 2f
        endY = location[1] + view.height / 2f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }

    fun suckAnimation(duration: Long = 600, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post

            if (endX == 0f || endY == 0f) {
                endX = width / 2f
                endY = height / 2f
            }

            val moveX = ObjectAnimator.ofFloat(this, TRANSLATION_X, 0f, endX - width / 2)
            val moveY = ObjectAnimator.ofFloat(this, TRANSLATION_Y, 0f, endY - height / 2)

            val scaleAnimator = ValueAnimator.ofFloat(1f, 0.85f, 0.6f, 0.3f, 0.0f).apply {
                this.duration = duration
                addUpdateListener { animator ->
                    val progress = animator.animatedFraction
                    val scaleFactor = animator.animatedValue as Float
                    scaleX = scaleFactor
                    scaleY = scaleFactor

                    val changeScale = progress < 0.8f
                    val minSize = width.coerceAtMost(height) * scaleFactor
                    cornerRadius = (width / 2f) * progress
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(
                                0, 0,
                                if (!changeScale) minSize.toInt() * 6 else view.width,
                                if (!changeScale) minSize.toInt() * 6 else view.height,
                                cornerRadius,
                            )
                        }
                    }
                    invalidate()
                }
            }

            val fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0.0f)

            val fadeTriggerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                addUpdateListener { animator ->
                    val progress = animator.animatedFraction
                    if (progress > 0.5f) {
                        if (!fadeAnimator.isRunning) fadeAnimator.start()
                    }
                }
            }

            AnimatorSet().apply {
                playTogether(scaleAnimator, moveX, moveY, fadeTriggerAnimator)
                play(fadeAnimator).after(fadeTriggerAnimator)
                this.duration = duration
                interpolator = PathInterpolator(0.22f, 1f, 0.36f, 1f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        (parent as? ViewGroup)?.removeView(this@FullScreenOverlayView)
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }

    fun animateIn(duration: Long = 400, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post

            val colorAnimator =
                ValueAnimator.ofObject(ArgbEvaluator(), startColor, startColor).apply {
                    this.duration = duration
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        setBackgroundColor(animation.animatedValue as Int)
                    }
                }

            val fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator()
            }

            AnimatorSet().apply {
                playTogether(colorAnimator, fadeAnimator)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }

    fun animateOut(duration: Long = 1200, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post

            val colorAnimator =
                ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor).apply {
                    this.duration = duration
                    interpolator = AccelerateInterpolator()
                    addUpdateListener { animation ->
                        setBackgroundColor(animation.animatedValue as Int)
                    }
                }

            val fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0f).apply {
                this.duration = duration
                interpolator = AccelerateInterpolator()
            }

            AnimatorSet().apply {
                playTogether(colorAnimator, fadeAnimator)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        visibility = INVISIBLE
                        (parent as? ViewGroup)?.removeView(this@FullScreenOverlayView)
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }

    // ── Custom close animations for AppOpenAnimationType ──────────────────────
    // These are used by the GNC (GestureNavContract) close path when the user
    // swipes back from an app using gesture navigation.
    // GNC is available to non-system launchers on Android 10+ — this is how
    // other custom launchers (Niagara, etc.) implement close animations without
    // needing CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS permission.

    /**
     * PIE close: overlay scales from 1.5→1.0 while fading in, revealing the launcher.
     * Mirrors old LawnchairAppTransitionManagerImpl.createLauncherResumeAnimation()
     * scale path: APP_CLOSE_HOME_ENTER_SCALE_FROM=1.5, TO=1.0, DUR=250ms.
     */
    fun pieCloseAnimation(duration: Long = 250, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            scaleX = 1.5f
            scaleY = 1.5f
            alpha  = 0f
            pivotX = width  / 2f
            pivotY = height / 2f
            setLayerType(LAYER_TYPE_HARDWARE, null)

            val scaleInterp = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
            val alphaInterp  = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(this@FullScreenOverlayView, SCALE_X, 1.5f, 1.0f).apply {
                        this.duration = duration; interpolator = scaleInterp
                    },
                    ObjectAnimator.ofFloat(this@FullScreenOverlayView, SCALE_Y, 1.5f, 1.0f).apply {
                        this.duration = duration; interpolator = scaleInterp
                    },
                    ObjectAnimator.ofFloat(this@FullScreenOverlayView, ALPHA, 0f, 1f).apply {
                        this.duration = minOf(duration, 100L); interpolator = alphaInterp
                    },
                )
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        setLayerType(LAYER_TYPE_NONE, null)
                        (parent as? ViewGroup)?.removeView(this@FullScreenOverlayView)
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }

    /**
     * BLINK close: launcher flashes three times while appearing.
     * Mirrors BlinkAnimation.overrideResumeAnimation() from old Lawnchair 2.
     */
    fun blinkCloseAnimation(onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            alpha = 0f
            setLayerType(LAYER_TYPE_HARDWARE, null)

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 400L
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    this@FullScreenOverlayView.alpha = when {
                        t < 0.20f -> 0.0f  // invisible
                        t < 0.40f -> 1.0f  // flash 1 on
                        t < 0.60f -> 0.0f  // flash 1 off
                        t < 0.80f -> 1.0f  // flash 2 on
                        else      -> 1.0f  // settle visible
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        this@FullScreenOverlayView.alpha = 1f
                        setLayerType(LAYER_TYPE_NONE, null)
                        (parent as? ViewGroup)?.removeView(this@FullScreenOverlayView)
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }

    /**
     * FADE close: launcher fades from 0→1 as it appears.
     * Mirrors FadeAnimation.overrideResumeAnimation() from old Lawnchair 2.
     */
    fun fadeCloseAnimation(duration: Long = 250, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            alpha = 0f
            setLayerType(LAYER_TYPE_HARDWARE, null)

            ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f).apply {
                this.duration = duration
                interpolator  = PathInterpolator(0.33f, 0.0f, 0.3f, 1.0f)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        setLayerType(LAYER_TYPE_NONE, null)
                        (parent as? ViewGroup)?.removeView(this@FullScreenOverlayView)
                        onEnd?.invoke()
                    }
                })
                start()
            }
        }
    }
}

/**
 * Shows and animates the full-screen overlay when returning from an app via
 * GestureNavContract (gesture swipe-back).
 *
 * HOW GNC CLOSE WORKS (why this works without system permissions):
 *
 * GestureNavContract is a public API added in Android 10 that allows any
 * launcher — system or not — to receive the app's SurfaceControl when the
 * user swipes back using gesture navigation. The launcher calls
 * GestureNavContract.sendEndPosition() with the icon's bounds and the
 * SurfaceControl, and the system animates the app window surface back to
 * the icon. The launcher controls its own side of the animation (how it
 * appears) separately.
 *
 * This is different from RemoteTransition (which needs
 * CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS) — GNC is specifically designed
 * for third-party launchers.
 *
 * NOTE: GNC only fires for GESTURE navigation (swipe back), NOT for
 * navigation button back-press. Button back uses the system's default
 * transition which cannot be overridden without system privileges.
 *
 * The overlay animation type is chosen based on [AppOpenAnimationType]:
 *   PIE / SLIDE_UP → pieCloseAnimation (scale 1.5→1.0 + fade in)
 *   BLINK          → blinkCloseAnimation (three flashes)
 *   FADE           → fadeCloseAnimation (fade in)
 *   SUCK_IN / FADE_IN (FullScreenOverlayMode) → original suck/fade behavior
 *   DEFAULT / REVEAL / SCALE_UP → standard FullScreenOverlayMode behavior
 */
fun Activity.showFullScreenOverlay(
    durationIn: Long = 200,
    durationOut: Long = 500,
    rootView: ViewGroup? = null,
    endView: View,
    onOverlayReady: () -> Unit,
) {
    val pref2 = PreferenceManager2.getInstance(this)
    val overlayMode = pref2.closingAppOverlay.firstBlocking()
    val appOpenAnim = pref2.appOpenAnimation.firstBlocking()
    val overlayView = FullScreenOverlayView(this)
    val targetRootView = rootView ?: window.decorView.findViewById<ViewGroup>(android.R.id.content)

    overlayView.pointyEndView(endView)
    targetRootView?.addView(overlayView)

    // Choose close animation based on AppOpenAnimationType first.
    // This is the GNC path (gesture swipe back) — available to non-system launchers.
    // FullScreenOverlayMode is a secondary control for SUCK_IN/FADE_IN/NONE.
    when (appOpenAnim) {
        AppOpenAnimationType.PIE,
        AppOpenAnimationType.SLIDE_UP,
        -> {
            // Pie close: overlay (launcher) zooms in from 1.5x scale while fading in.
            // The GNC system already animates the APP window back to the icon position.
            // We just need the launcher to appear with the Pie zoom-in effect.
            overlayView.pieCloseAnimation(durationOut) {
                onOverlayReady()
            }
        }

        AppOpenAnimationType.BLINK -> {
            overlayView.blinkCloseAnimation {
                onOverlayReady()
            }
        }

        AppOpenAnimationType.FADE -> {
            overlayView.fadeCloseAnimation(durationOut) {
                onOverlayReady()
            }
        }

        // DEFAULT, REVEAL, SCALE_UP: fall back to FullScreenOverlayMode setting.
        else -> {
            when (overlayMode) {
                FullScreenOverlayMode.FADE_IN -> {
                    overlayView.animateIn(durationIn) {
                        overlayView.animateOut(durationOut, onOverlayReady)
                    }
                }
                FullScreenOverlayMode.SUCK_IN -> {
                    overlayView.suckAnimation(durationOut) {
                        onOverlayReady()
                    }
                }
                FullScreenOverlayMode.NONE -> {
                    targetRootView?.removeView(overlayView)
                    onOverlayReady()
                }
            }
        }
    }
}
