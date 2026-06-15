package app.lawnchair.allapps.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import app.lawnchair.launcher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.android.systemui.util.dpToPx
import com.patrykmichalik.opto.core.firstBlocking

class SearchItemBackground(
    context: Context,
    showBackground: Boolean,
    roundTop: Boolean,
    roundBottom: Boolean,
) {
    private val resources = context.resources

    private val searchDecorationPadding = resources.getDimensionPixelSize(R.dimen.search_decoration_padding)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmpPath = Path()
    private val tmpRect = RectF()

    // LC-Note: System cross-window blur replaced by HokoBlur. The focus/group
    // highlight colors are now driven by the drawerBlurBackground HokoBlur
    // preference instead of BlurUtils.supportsBlursOnWindows().
    private val drawerBlurEnabled = PreferenceManager2.getInstance(context.launcher).drawerBlurBackground.firstBlocking()

    // Retained for any external callers that previously checked supportBlur.
    val supportBlur = drawerBlurEnabled

    val focusHighlight = if (drawerBlurEnabled) {
        ColorTokens.FocusHighlightBlur.resolveColor(context)
    } else {
        ColorTokens.FocusHighlight.resolveColor(context)
    }
    val groupHighlight = if (showBackground) {
        if (drawerBlurEnabled) {
            ColorTokens.GroupHighlightBlur.resolveColor(context)
        } else {
            ColorTokens.GroupHighlight.resolveColor(context)
        }
    } else {
        0
    }

    val cornerRadii: FloatArray

    init {
        val searchGroupRadius = resources.getDimensionPixelSize(R.dimen.search_group_radius).toFloat()
        val searchResultRadius = resources.getDimensionPixelSize(R.dimen.search_result_radius).toFloat()

        val topRadius = if (roundTop) searchGroupRadius else searchResultRadius
        val bottomRadius = if (roundBottom) searchGroupRadius else searchResultRadius

        cornerRadii = floatArrayOf(
            topRadius,
            topRadius,
            topRadius,
            topRadius,
            bottomRadius,
            bottomRadius,
            bottomRadius,
            bottomRadius,
        )
    }

    fun draw(c: Canvas, child: View, isFocused: Boolean) {
        val color = if (isFocused) focusHighlight else groupHighlight
        if (color == 0) return

        paint.color = color

        var left = child.left.toFloat() + searchDecorationPadding
        var top = child.top.toFloat() + searchDecorationPadding
        var right = child.right.toFloat() - searchDecorationPadding
        var bottom = child.bottom.toFloat() - searchDecorationPadding

        if (child is SearchResultIcon) {
            val iconSize = child.iconSize.toFloat()
            val desiredWidth = iconSize + 48.dpToPx(resources)
            val cellWidth = child.width.toFloat()
            if (desiredWidth < cellWidth) {
                val inset = (cellWidth - desiredWidth) / 2
                left += inset
                right -= inset
            }
            val isTwoLine = child.lineCount > 1
            val insetTop = 6.dpToPx(resources)
            val insetBottom = if (isTwoLine) 6.dpToPx(resources) else 0.dpToPx(resources)
            top += insetTop
            bottom -= insetBottom
        }

        tmpRect.set(left, top, right, bottom)

        tmpPath.reset()
        tmpPath.addRoundRect(tmpRect, cornerRadii, Path.Direction.CW)

        c.drawPath(tmpPath, paint)
    }
}
