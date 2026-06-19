package app.lawnchair.ui.theme

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

@JvmOverloads
fun Context.getAccentColor(darkTheme: Boolean = Themes.getAttrBoolean(this, R.attr.isMainColorDark)): Int {
    return ColorTokens.ColorAccent.resolveColor(this, if (darkTheme) UiColorMode.Dark else UiColorMode.Light)
}

@ColorInt
fun lightenColor(@ColorInt color: Int): Int {
    var newColor = color
    val outHsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, outHsl)

    while (ColorUtils.calculateContrast(newColor, 0xFF000000.toInt()) < 6.5) {
        outHsl[2] += 0.05F
        newColor = ColorUtils.HSLToColor(outHsl)
    }

    return newColor
}

@Suppress("DEPRECATION")
fun Context.getSystemAccent(darkTheme: Boolean): Int {
    val res = resources
    return if (Utilities.ATLEAST_S) {
        val colorId = if (darkTheme) R.color.system_accent1_100 else R.color.system_accent1_600
        res.getColor(colorId)
    } else {
        var propertyValue = Utilities.getSystemProperty("persist.sys.theme.accentcolor", "")
        if (!propertyValue.isNullOrEmpty()) {
            if (!propertyValue.startsWith('#')) {
                propertyValue = "#$propertyValue"
            }
            try {
                return Color.parseColor(propertyValue)
            } catch (_: IllegalArgumentException) {
            }
        }

        val typedValue = TypedValue()
        val theme = if (darkTheme) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light
        val contextWrapper = ContextThemeWrapper(this, theme)
        contextWrapper.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        typedValue.data
    }
}

@Composable
fun preferenceGroupColor(): androidx.compose.ui.graphics.Color {
    // surfaceColorAtElevation always blends surfaceTint (= primary/accent hue) over
    // surface, regardless of style. For styles with neutral surfaces by design
    // (Rainbow, Monochromatic, Content with a low-chroma seed) this incorrectly
    // introduces an accent tint into cards that should stay grey. Reading the
    // surface-family roles directly avoids this — they stay within the neutral
    // palette (n1/n2), which is already correctly neutral or tinted per style.
    //
    // surfaceBright is not used in light mode because SPEC_2025's Tonal Spot/
    // Vibrant/Expressive/Spritz styles collapse surfaceBright == surface == tone 98,
    // making cards invisible. surfaceContainerLowest (tone 100, still pure n1) stays
    // visually distinct from the tone-98 background in every style and spec.
    return if (isSelectedThemeDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
}

@Composable
fun dividerColor() = MaterialTheme.colorScheme.outlineVariant
