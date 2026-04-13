package app.lawnchair.qsb

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R

fun ImageView.setThemedIconResource(
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
) {
    if (themed && method == ThemingMethod.THEME_BY_LAYER_ID) {
        val drawable = ResourcesCompat.getDrawable(resources, resId, null)!!
        if (drawable is LayerDrawable) {
            drawable.mutate()
            val primary = ColorTokens.QsbIconTintPrimary.resolveColor(context)
            val secondary = ColorTokens.QsbIconTintSecondary.resolveColor(context)
            val tertiary = ColorTokens.QsbIconTintTertiary.resolveColor(context)
            val quaternary = ColorTokens.QsbIconTintQuaternary.resolveColor(context)

            if (resId == R.drawable.ic_mic_color || resId == R.drawable.ic_lens_color) {
                // Mic and lens always use a single uniform color across all layers,
                // matching Google's latest update where these icons are monochrome.
                val accent = ColorTokens.QsbIconTintPrimary.resolveColor(context)
                for (i in 0 until drawable.numberOfLayers) {
                    drawable.getDrawable(i).setTint(accent)
                }
                setImageDrawable(drawable)
                return
            } else if (resId == R.drawable.ic_super_g_color) {
                for (i in 0 until drawable.numberOfLayers) {
                    val layerId = drawable.getId(i)
                    val color = when (layerId) {
                        R.id.qsbIconTintPrimary -> primary
                        R.id.qsbIconTintSecondary -> secondary
                        R.id.qsbIconTintTertiary -> tertiary
                        R.id.qsbIconTintQuaternary -> quaternary
                        else -> continue
                    }
                    drawable.getDrawable(i).setTint(color)
                }

                // Junction angles from actual SVG path coordinates (center=12,12 in 24x24):
                //   blue→green  : 0.1358
                //   green→yellow: 0.4259
                //   yellow→red  : 0.5741
                //   red→blue    : 0.8670
                //
                // The horizontal bar in the blue segment occupies x=12..24, y=10..14.51
                // in the 24x24 viewport. We exclude this rect from the sweep gradient
                // so the bar stays solid blue (from layer tint), and only the arc gets
                // the color blending treatment.
                //
                // Bar bounds as fractions of drawable size (viewport 24x24):
                //   left=0.500, top=0.417, right=1.000, bottom=0.605

                val d = 0.05f

                val sweepColors = intArrayOf(
                    primary,    // 0.000 - inside blue (seam, safe)
                    primary,    // blue solid
                    secondary,  // blue→green blend
                    secondary,  // green solid
                    tertiary,   // green→yellow blend
                    tertiary,   // yellow solid
                    quaternary, // yellow→red blend
                    quaternary, // red solid
                    primary,    // red→blue blend
                    primary,    // 1.000 - inside blue (seam, safe)
                )
                val sweepPositions = floatArrayOf(
                    0.000f,
                    0.1358f - d,
                    0.1358f + d,
                    0.4259f - d,
                    0.4259f + d,
                    0.5741f - d,
                    0.5741f + d,
                    0.8670f - d,
                    0.8670f + d,
                    1.000f,
                )

                setImageDrawable(
                    SweepGradientDrawable(
                        drawable,
                        sweepColors,
                        sweepPositions,
                        // Bar rect to exclude from gradient (in 0..1 fractions of bounds)
                        barExcludeLeft   = 0.500f,
                        barExcludeTop    = 0.417f,
                        barExcludeRight  = 1.000f,
                        barExcludeBottom = 0.605f,
                    )
                )
                return
            } else {
                for (i in 0 until drawable.numberOfLayers) {
                    val layerId = drawable.getId(i)
                    val color = when (layerId) {
                        R.id.qsbIconTintPrimary -> primary
                        R.id.qsbIconTintSecondary -> secondary
                        R.id.qsbIconTintTertiary -> tertiary
                        R.id.qsbIconTintQuaternary -> quaternary
                        else -> continue
                    }
                    drawable.getDrawable(i).setTint(color)
                }
            }
        }
        setImageDrawable(drawable)
    } else {
        // TINT method or themed=false
        if (!themed && (resId == R.drawable.ic_mic_color || resId == R.drawable.ic_lens_color)) {
            // Mic and lens: uniform single color even when unthemed
            val drawable = ResourcesCompat.getDrawable(resources, resId, null)!!
            if (drawable is LayerDrawable) {
                val accent = ColorTokens.QsbIconTintPrimary.resolveColor(context)
                for (i in 0 until drawable.numberOfLayers) {
                    drawable.getDrawable(i).setTint(accent)
                }
                setImageDrawable(drawable)
                return
            }
        } else if (!themed && resId == R.drawable.ic_super_g_color) {
            // Unthemed: apply sweep gradient with original Google brand colors
            val drawable = ResourcesCompat.getDrawable(resources, resId, null)!!
            if (drawable is LayerDrawable) {
                val blue   = 0xFF4285F4.toInt()
                val green  = 0xFF34A853.toInt()
                val yellow = 0xFFFBBC05.toInt()
                val red    = 0xFFEA4335.toInt()

                val d = 0.03f
                val sweepColors = intArrayOf(
                    blue, blue, green, green, yellow, yellow, red, red, blue, blue,
                )
                val sweepPositions = floatArrayOf(
                    0.000f,
                    0.1358f - d, 0.1358f + d,
                    0.4259f - d, 0.4259f + d,
                    0.5741f - d, 0.5741f + d,
                    0.9000f - d, 0.9000f + d,
                    1.000f,
                )
                setImageDrawable(
                    SweepGradientDrawable(
                        drawable,
                        sweepColors,
                        sweepPositions,
                        barExcludeLeft   = 0.500f,
                        barExcludeTop    = 0.417f,
                        barExcludeRight  = 1.000f,
                        barExcludeBottom = 0.605f,
                    )
                )
                return
            }
        }
        setImageResource(resId)
        if (themed) setColorFilter(ColorTokens.ColorAccent.resolveColor(context))
    }
}

class SweepGradientDrawable(
    private val inner: android.graphics.drawable.Drawable,
    private val colors: IntArray,
    private val positions: FloatArray,
    // Fraction (0..1) of drawable bounds to exclude from gradient (the horizontal bar)
    private val barExcludeLeft: Float = -1f,
    private val barExcludeTop: Float = -1f,
    private val barExcludeRight: Float = -1f,
    private val barExcludeBottom: Float = -1f,
) : DrawableWrapper(inner) {

    private val gradientPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    // Clip path that covers everything EXCEPT the bar rectangle
    private val clipPath = Path()

    private fun buildShader(b: Rect) {
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        gradientPaint.shader = SweepGradient(cx, cy, colors, positions)

        // Build clip path: full bounds minus the bar rectangle
        clipPath.reset()
        clipPath.addRect(
            b.left.toFloat(), b.top.toFloat(),
            b.right.toFloat(), b.bottom.toFloat(),
            Path.Direction.CW,
        )
        if (barExcludeLeft >= 0f) {
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val barRect = RectF(
                b.left + barExcludeLeft   * w,
                b.top  + barExcludeTop    * h,
                b.left + barExcludeRight  * w,
                b.top  + barExcludeBottom * h,
            )
            // Cut out the bar by adding it with opposite winding (even-odd fill)
            clipPath.addRect(barRect, Path.Direction.CCW)
        }
        clipPath.fillType = Path.FillType.EVEN_ODD
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        buildShader(bounds)
    }

    override fun draw(canvas: Canvas) {
        if (gradientPaint.shader == null) buildShader(bounds)

        val count = canvas.saveLayer(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(),
            null,
        )

        // Draw all layers with their solid tints (bar stays solid blue here)
        inner.draw(canvas)

        // Apply sweep gradient only to the arc region (bar is clipped out)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(),
            gradientPaint,
        )
        canvas.restore()

        canvas.restoreToCount(count)
    }
}

enum class ThemingMethod {
    TINT,
    THEME_BY_LAYER_ID,
}
