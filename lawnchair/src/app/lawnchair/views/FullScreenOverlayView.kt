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
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.views.overlay.AppOpenAnimationType
import app.lawnchair.views.overlay.FullScreenOverlayMode

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
            if (endX == 0f || endY == 0f) { endX = width / 2f; endY = height / 2f }
            val moveX = ObjectAnimator.ofFloat(this, TRANSLATION_X, 0f, endX - width / 2)
            val moveY = ObjectAnimator.ofFloat(this, TRANSLATION_Y, 0f, endY - height / 2)
            val scaleAnimator = ValueAnimator.ofFloat(1f, 0.85f, 0.6f, 0.3f, 0.0f).apply {
                this.duration = duration
                addUpdateListener { animator ->
                    val progress = animator.animatedFraction
                    val scaleFactor = animator.animatedValue as Float
                    scaleX = scaleFactor; scaleY = scaleFactor
                    val changeScale = progress < 0.8f
                    val minSize = width.coerceAtMost(height) * scaleFactor
                    cornerRadius = (width / 2f) * progress
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setRoundRect(0, 0,
                                if (!changeScale) minSize.toInt() * 6 else view.width,
                                if (!changeScale) minSize.toInt() * 6 else view.height,
                                cornerRadius)
                        }
                    }
                    invalidate()
                }
            }
            val fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0.0f)
            val fadeTriggerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                addUpdateListener { animator ->
                    if (animator.animatedFraction > 0.5f && !fadeAnimator.isRunning)
                        fadeAnimator.start()
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
            val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, startColor).apply {
                this.duration = duration; interpolator = DecelerateInterpolator()
                addUpdateListener { setBackgroundColor(it.animatedValue as Int) }
            }
            val fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f).apply {
                this.duration = duration; interpolator = DecelerateInterpolator()
            }
            AnimatorSet().apply {
                playTogether(colorAnimator, fadeAnimator)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
                })
                start()
            }
        }
    }

    fun animateOut(duration: Long = 1200, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor).apply {
                this.duration = duration; interpolator = AccelerateInterpolator()
                addUpdateListener { setBackgroundColor(it.animatedValue as Int) }
            }
            val fadeAnimator = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0f).apply {
                this.duration = duration; interpolator = AccelerateInterpolator()
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

    // ── GNC close animations (gesture swipe-back, non-system launcher) ────────

    fun pieCloseAnimation(duration: Long = 250, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            scaleX = 1.5f; scaleY = 1.5f; alpha = 0f
            pivotX = width / 2f; pivotY = height / 2f
            setLayerType(LAYER_TYPE_HARDWARE, null)
            val interp = PathInterpolator(0.33f, 0.0f, 0.2f, 1.0f)
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(this@FullScreenOverlayView, SCALE_X, 1.5f, 1.0f).apply {
                        this.duration = duration; interpolator = interp
                    },
                    ObjectAnimator.ofFloat(this@FullScreenOverlayView, SCALE_Y, 1.5f, 1.0f).apply {
                        this.duration = duration; interpolator = interp
                    },
                    ObjectAnimator.ofFloat(this@FullScreenOverlayView, ALPHA, 0f, 1f).apply {
                        this.duration = minOf(duration, 100L); interpolator = interp
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

    fun blinkCloseAnimation(onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            alpha = 0f; setLayerType(LAYER_TYPE_HARDWARE, null)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 400L
                addUpdateListener { anim ->
                    val t = anim.animatedFraction
                    this@FullScreenOverlayView.alpha = when {
                        t < 0.20f -> 0f; t < 0.40f -> 1f
                        t < 0.60f -> 0f; else -> 1f
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

    fun fadeCloseAnimation(duration: Long = 250, onEnd: (() -> Unit)? = null) {
        post {
            if (!isAttachedToWindow) return@post
            alpha = 0f; setLayerType(LAYER_TYPE_HARDWARE, null)
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

fun Activity.showFullScreenOverlay(
    durationIn: Long = 200,
    durationOut: Long = 500,
    rootView: ViewGroup? = null,
    endView: View,
    onOverlayReady: () -> Unit,
) {
    val pref2 = PreferenceManager2.getInstance(this)
    val overlayMode  = pref2.closingAppOverlay.firstCached()
    val appOpenAnim  = pref2.appOpenAnimation.firstCached()
    val overlayView  = FullScreenOverlayView(this)
    val targetRoot   = rootView ?: window.decorView.findViewById<ViewGroup>(android.R.id.content)

    overlayView.pointyEndView(endView)
    targetRoot?.addView(overlayView)

    when (appOpenAnim) {
        AppOpenAnimationType.PIE,
        AppOpenAnimationType.SLIDE_UP,
        -> overlayView.pieCloseAnimation(durationOut) { onOverlayReady() }

        AppOpenAnimationType.BLINK -> overlayView.blinkCloseAnimation { onOverlayReady() }
        AppOpenAnimationType.FADE  -> overlayView.fadeCloseAnimation(durationOut) { onOverlayReady() }

        else -> when (overlayMode) {
            FullScreenOverlayMode.FADE_IN -> overlayView.animateIn(durationIn) {
                overlayView.animateOut(durationOut, onOverlayReady)
            }
            FullScreenOverlayMode.SUCK_IN -> overlayView.suckAnimation(durationOut) { onOverlayReady() }
            FullScreenOverlayMode.NONE    -> { targetRoot?.removeView(overlayView); onOverlayReady() }
        }
    }
}
