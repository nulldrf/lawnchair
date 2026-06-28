package app.lawnchair.theme.color

import androidx.annotation.ColorInt
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import com.android.systemui.monet.Style

/**
 * Holds a materialkolor [DynamicScheme] for the SPEC_2025 path.
 *
 * Uses the canonical HCT-based palette generation from materialkolor/material-color-utilities,
 * which gives exact [TonalPalette.tone(t)] lookups at any integer tone 0–100 with no sparse
 * grid, no nearest-key fallback, and no CIE Lab luminance workarounds.
 *
 * [DynamicScheme] also pre-computes every Material role as an ARGB [Int] property via
 * [MaterialDynamicColors] and [ColorSpec2025], so [toComposeColorScheme2025] simply reads
 * those values directly — no tone arithmetic in Lawnchair at all.
 *
 * Intentionally does NOT extend [dev.kdrag0n.monet.theme.ColorScheme]. This is a plain
 * data holder. The SPEC_2021 / LegacyKdrag path continues to use the existing
 * [MonetColorSchemeCompat] which extends the kdrag0n class.
 *
 * [isDark] is part of construction because [DynamicScheme] / [ColorSpec2025] bakes dark/light
 * mode into palette generation (surface tones differ, chroma multipliers differ per mode).
 * The cache in [ThemeProvider] therefore keys on (seedColor, Style, isDark).
 *
 * Styles that have no SPEC_2025 variant ([Style.RAINBOW], [Style.FRUIT_SALAD],
 * [Style.MONOCHROMATIC], [Style.CONTENT]) are handled by [maybeFallbackSpecVersion] inside
 * [DynamicScheme] which silently falls back to SPEC_2021 for those variants.
 */
class MonetColorSchemeCompat2025(
    @ColorInt val seedColor: Int,
    val style: Style = Style.TONAL_SPOT,
    val isDark: Boolean = false,
) {
    val scheme: DynamicScheme = buildScheme(seedColor, style, isDark)

    /**
     * Builds a [SparseIntArray] mapping Android system color resource IDs to ARGB ints
     * from this SPEC_2025 [DynamicScheme].  Called by [AccentColorExtractor] to apply
     * the 2025 palette to widget contexts without Java needing to touch [TonalPalette].
     *
     * Shade keys (0–1000) from the legacy Monet system map to Material tones (100–0):
     *   materialTone = (1000 - shade) / 10
     */
    fun toColorOverrides(
        accent1Res: android.util.SparseIntArray,
        accent2Res: android.util.SparseIntArray,
        accent3Res: android.util.SparseIntArray,
        neutral1Res: android.util.SparseIntArray,
        neutral2Res: android.util.SparseIntArray,
    ): android.util.SparseIntArray {
        val result = android.util.SparseIntArray(5 * 13)

        fun addPalette(palette: com.materialkolor.palettes.TonalPalette, resMap: android.util.SparseIntArray) {
            for (i in 0 until resMap.size()) {
                val shade = resMap.keyAt(i)
                val resId = resMap.valueAt(i)
                val tone = (1000 - shade) / 10
                result.put(resId, palette.tone(tone))
            }
        }

        addPalette(scheme.primaryPalette,        accent1Res)
        addPalette(scheme.secondaryPalette,       accent2Res)
        addPalette(scheme.tertiaryPalette,        accent3Res)
        addPalette(scheme.neutralPalette,         neutral1Res)
        addPalette(scheme.neutralVariantPalette,  neutral2Res)

        return result
    }

    companion object {
        private fun buildScheme(
            @ColorInt seedColor: Int,
            style: Style,
            isDark: Boolean,
        ): DynamicScheme {
            val sourceHct = Hct.fromInt(seedColor)
			android.util.Log.d("HCT_DEBUG", "hue=${sourceHct.hue} chroma=${sourceHct.chroma} tone=${sourceHct.tone}")
            val spec = ColorSpec.SpecVersion.SPEC_2025
            val contrast = 0.0

            return when (style) {
                Style.VIBRANT       -> SchemeVibrant(sourceHct, isDark, contrast, spec)
                Style.EXPRESSIVE    -> SchemeExpressive(sourceHct, isDark, contrast, spec)
                Style.SPRITZ        -> SchemeNeutral(sourceHct, isDark, contrast, spec)
                Style.RAINBOW       -> SchemeRainbow(sourceHct, isDark, contrast, spec)
                Style.FRUIT_SALAD   -> SchemeFruitSalad(sourceHct, isDark, contrast, spec)
                Style.CONTENT       -> SchemeContent(sourceHct, isDark, contrast, spec)
                Style.MONOCHROMATIC -> SchemeMonochrome(sourceHct, isDark, contrast, spec)
                // TONAL_SPOT and anything else (including LegacyKdrag placeholder TONAL_SPOT)
                else                -> SchemeTonalSpot(sourceHct, isDark, contrast, spec)
            }
        }
    }
}
