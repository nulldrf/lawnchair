package app.lawnchair.qsb

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Loads a drawable resource and applies theming based on the specified [ThemingMethod].
 *
 * If [themed] is true, the function will apply colors based on the chosen method:
 * - [ThemingMethod.THEME_BY_LAYER_ID]: If the drawable is a [LayerDrawable], it searches for specific
 *   layer IDs (e.g., `R.id.qsbIconTintPrimary`) and applies corresponding QSB color tokens.
 * - [ThemingMethod.TINT]: Applies accent color tinting to the entire drawable.
 */
fun setThemedIconResource(
    context: Context,
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
): Drawable {
    val drawable = requireNotNull(ResourcesCompat.getDrawable(context.resources, resId, context.theme)) {
        "Unable to resolve icon drawable for resId=$resId"
    }.mutate()

    if (!themed) {
        return drawable
    }

    if (method == ThemingMethod.THEME_BY_LAYER_ID && drawable is LayerDrawable) {
        val primary = ColorTokens.QsbIconTintPrimary.resolveColor(context)
        val secondary = ColorTokens.QsbIconTintSecondary.resolveColor(context)
        val tertiary = ColorTokens.QsbIconTintTertiary.resolveColor(context)
        val quaternary = ColorTokens.QsbIconTintQuaternary.resolveColor(context)

        if (resId == R.drawable.ic_mic_color || resId == R.drawable.ic_lens_color) {
            // Mic and lens always use a single uniform color across all layers,
            // matching Google's latest update where these icons are monochrome.
            val accent = ColorTokens.QsbIconTintMonochrome.resolveColor(context)
            for (i in 0 until drawable.numberOfLayers) {
                drawable.getDrawable(i).setTint(accent)
            }
            return drawable
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
            return SweepGradientDrawable(
                drawable,
                sweepColors,
                sweepPositions,
                // Bar rect to exclude from gradient (in 0..1 fractions of bounds)
                barExcludeLeft   = 0.500f,
                barExcludeTop    = 0.417f,
                barExcludeRight  = 1.000f,
                barExcludeBottom = 0.605f,
            )
        } else {
            for (i in (0 until drawable.numberOfLayers)) {
                val color = when (drawable.getId(i)) {
                    R.id.qsbIconTintPrimary -> primary
                    R.id.qsbIconTintSecondary -> secondary
                    R.id.qsbIconTintTertiary -> tertiary
                    R.id.qsbIconTintQuaternary -> quaternary
                    else -> 0
                }
                if (color == 0) continue
                drawable.getDrawable(i).setTint(color)
            }
        }
    } else {
        drawable.setTint(ColorTokens.ColorAccent.resolveColor(context))
    }

    return drawable
}

/**
 * Remembers a [Painter] for a themed icon resource.
 *
 * @param resId The drawable resource ID to load.
 * @param themed Whether the icon should have theming applied.
 * @param method The strategy to use for applying theme colors.
 * @return A [Painter] that draws the (potentially themed) drawable resource.
 */
@Composable
fun rememberThemedIconPainter(
    @DrawableRes resId: Int,
    themed: Boolean,
    method: ThemingMethod = ThemingMethod.THEME_BY_LAYER_ID,
): Painter {
    val context = LocalContext.current
    val drawable = remember(context, resId, themed, method) {
        setThemedIconResource(context, resId, themed, method)
    }
    return rememberDrawablePainter(drawable)
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
