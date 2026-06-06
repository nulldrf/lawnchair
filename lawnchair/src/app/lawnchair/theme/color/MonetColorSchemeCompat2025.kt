package app.lawnchair.theme.color

import androidx.annotation.ColorInt
import com.android.systemui.monet.ColorScheme as MonetColorScheme
import com.android.systemui.monet.SpecVersion
import com.android.systemui.monet.Style
import com.android.systemui.monet.TonalPalette

/**
 * Holds the SPEC_2025 tonal palettes for a given seed color and style.
 *
 * Intentionally does NOT extend [dev.kdrag0n.monet.theme.ColorScheme].
 * The kdrag0n abstract class carries its own engine abstraction (primary(),
 * neutral(), ColorSwatch maps, etc.) which has nothing to do with the 2025
 * palette path. Extending it would force unused overrides and risk the kdrag0n
 * interpolation logic interfering with direct tone lookups.
 *
 * Instead this class is a plain data holder. [toComposeColorScheme2025] in
 * ComposeColorScheme.kt reads [accent1], [accent2], [accent3], [neutral1],
 * [neutral2] directly via [TonalPalette.atTone].
 *
 * Cached in [ThemeProvider.colorSchemeMap2025] as its own type, separate from
 * the kdrag0n [dev.kdrag0n.monet.theme.ColorScheme] cache.
 */
class MonetColorSchemeCompat2025(
    @ColorInt val seedColor: Int,
    val style: Style = Style.TONAL_SPOT,
) {
    private val raw: MonetColorScheme = MonetColorScheme(
        seedColor,
        style,
        SpecVersion.SPEC_2025,
    )

    val accent1: TonalPalette = raw.accent1
    val accent2: TonalPalette = raw.accent2
    val accent3: TonalPalette = raw.accent3
    val neutral1: TonalPalette = raw.neutral1
    val neutral2: TonalPalette = raw.neutral2
}
