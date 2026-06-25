package app.lawnchair.theme.color

import app.lawnchair.theme.color.tokens.Shade
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.ColorSwatch

/**
 * Wraps a [MonetColorSchemeCompat2025] materialkolor [DynamicScheme] as a kdrag0n
 * [ColorScheme] so all existing [app.lawnchair.theme.color.tokens.ColorToken]
 * implementations can consume SPEC_2025 colours without modification.
 *
 * Shade keys (0–1000) are converted to Material tones (100–0) via:
 *   materialTone = (1000 - shade) / 10
 *
 * The [dev.kdrag0n.monet.theme.ColorSwatch] alias is [Map<Int, Color>]; every
 * supported shade key is pre-computed at construction time so lookups are O(1).
 */
class Spec2025CompatColorScheme(
    private val scheme2025: MonetColorSchemeCompat2025,
) : ColorScheme() {

    private val allShades: List<Int> = listOf(
        Shade.S0.lightness,
        Shade.S10.lightness,
        Shade.S20.lightness,
        Shade.S50.lightness,
        Shade.S100.lightness,
        Shade.S200.lightness,
        Shade.S300.lightness,
        Shade.S400.lightness,
        Shade.S500.lightness,
        Shade.S600.lightness,
        Shade.S650.lightness,
        Shade.S700.lightness,
        Shade.S800.lightness,
        Shade.S900.lightness,
        Shade.S950.lightness,
        Shade.S1000.lightness,
    )

    private fun buildSwatch(palette: com.materialkolor.palettes.TonalPalette): ColorSwatch =
        allShades.associateWith { shade ->
            val tone = (1000 - shade) / 10
            AndroidColor(palette.tone(tone))
        }

    // Map materialkolor palette roles to kdrag0n swatch names
    override val neutral1: ColorSwatch = buildSwatch(scheme2025.scheme.neutralPalette)
    override val neutral2: ColorSwatch = buildSwatch(scheme2025.scheme.neutralVariantPalette)
    override val accent1: ColorSwatch  = buildSwatch(scheme2025.scheme.primaryPalette)
    override val accent2: ColorSwatch  = buildSwatch(scheme2025.scheme.secondaryPalette)
    override val accent3: ColorSwatch  = buildSwatch(scheme2025.scheme.tertiaryPalette)
}
