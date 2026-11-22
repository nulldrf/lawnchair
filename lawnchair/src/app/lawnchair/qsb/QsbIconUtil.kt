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
import androidx.compose.material3.MaterialTheme
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
        // LC-Note: unthemed G icon still gets the brand-color sweep gradient.
        if (resId == R.drawable.ic_super_g_color && drawable is LayerDrawable) {
            return createSuperGSweepDrawable(
                drawable = drawable,
                primary = 0xFF4285F4.toInt(),
                secondary = 0xFF34A853.toInt(),
                tertiary = 0xFFFBBC05.toInt(),
                quaternary = 0xFFEA4335.toInt(),
                blendHalfWidth = 0.03f,
            )
        }
        return drawable
    }

    if (method == ThemingMethod.THEME_BY_LAYER_ID) {
        if (drawable is LayerDrawable) {
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

                return createSuperGSweepDrawable(
                    drawable = drawable,
                    primary = primary,
                    secondary = secondary,
                    tertiary = tertiary,
                    quaternary = quaternary,
                    blendHalfWidth = 0.05f,
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
        }
    } else {
        drawable.setTint(ColorTokens.ColorAccent.resolveColor(context))
    }

    return drawable
}

// Midpoint junction angles from ic_super_g_color_1..4 (center=12,12 in 24x24):
//   blue→green  : inner 0.1562, outer 0.1358 → midpoint 0.1460
//   green→yellow: inner 0.4478, outer 0.4259 → midpoint 0.4369
//   yellow→red  : inner 0.5522, outer 0.5741 → midpoint 0.5631
//   red endcap  : 0.8616..0.8670; blue bar top: 0.9721 (open gap 0.8670..0.9721)
//
// The red→blue wrap is placed strictly inside the open gap (0.900..0.945) so blue
// never leaks into the red tip. The horizontal bar (x=12..23.3, y=10..14.51) is
// excluded with anti-aliasing padding (left=0.44, top=0.35, right=1.0, bottom=0.64)
// so adjacent sweep colors cannot bleed onto the bar's edges.
private fun createSuperGSweepDrawable(
    drawable: Drawable,
    primary: Int,
    secondary: Int,
    tertiary: Int,
    quaternary: Int,
    blendHalfWidth: Float,
): SweepGradientDrawable {
    val sweepColors = intArrayOf(
        primary,
        primary,
        secondary,
        secondary,
        tertiary,
        tertiary,
        quaternary,
        quaternary,
        primary,
        primary,
    )
    val sweepPositions = floatArrayOf(
        0.000f,
        0.1460f - blendHalfWidth,
        0.1460f + blendHalfWidth,
        0.4369f - blendHalfWidth,
        0.4369f + blendHalfWidth,
        0.5631f - blendHalfWidth,
        0.5631f + blendHalfWidth,
        0.9000f,
        0.9450f,
        1.000f,
    )
    return SweepGradientDrawable(
        inner = drawable,
        colors = sweepColors,
        positions = sweepPositions,
        barExcludeLeft = 0.440f,
        barExcludeTop = 0.350f,
        barExcludeRight = 1.000f,
        barExcludeBottom = 0.640f,
    )
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
    val colorScheme = MaterialTheme.colorScheme
    val drawable = remember(context, resId, themed, method, colorScheme) {
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
            b.left.toFloat(),
            b.top.toFloat(),
            b.right.toFloat(),
            b.bottom.toFloat(),
            Path.Direction.CW,
        )
        if (barExcludeLeft >= 0f) {
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val barRect = RectF(
                b.left + barExcludeLeft * w,
                b.top + barExcludeTop * h,
                b.left + barExcludeRight * w,
                b.top + barExcludeBottom * h,
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
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
            null,
        )

        // Draw all layers with their solid tints (bar stays solid blue here)
        inner.draw(canvas)

        // Apply sweep gradient only to the arc region (bar is clipped out)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(
            bounds.left.toFloat(),
            bounds.top.toFloat(),
            bounds.right.toFloat(),
            bounds.bottom.toFloat(),
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
