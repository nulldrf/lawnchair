package app.lawnchair.theme.color

import androidx.annotation.ColorInt
import dev.kdrag0n.colorkt.cam.Zcam
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.colorkt.tristimulus.CieXyz.Companion.toXyz
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs
import dev.kdrag0n.colorkt.tristimulus.CieXyzAbs.Companion.toAbs
import dev.kdrag0n.colorkt.ucs.lab.CieLab
import dev.kdrag0n.monet.theme.ColorScheme
import dev.kdrag0n.monet.theme.ColorSwatch
import dev.kdrag0n.monet.theme.DynamicColorScheme
import dev.kdrag0n.monet.theme.MaterialYouTargets

/**
 * A [ColorScheme] that uses the kdrag0n ZCAM-based Monet engine instead of the
 * Android system engine.  This produces richer, more perceptually-accurate palettes
 * than the stock Google implementation, especially for wallpaper-derived seed colors.
 *
 * This class is intentionally stateless: callers should cache instances by seed color
 * (see [app.lawnchair.theme.ThemeProvider]).
 */
class KdragMonetColorScheme(
    @ColorInt seedColor: Int,
) : ColorScheme() {

    // Strip the alpha byte so Srgb(Int) receives a packed 0xRRGGBB value.
    private val seedSrgb: Srgb = Srgb(seedColor and 0x00FFFFFF)

    private val dynamicScheme = DynamicColorScheme(
        targets = DEFAULT_TARGETS,
        seedColor = seedSrgb,
        chromaFactor = 1.0,
        cond = DEFAULT_COND,
        accurateShades = true,
    )

    override val neutral1: ColorSwatch = dynamicScheme.neutral1
    override val neutral2: ColorSwatch = dynamicScheme.neutral2

    override val accent1: ColorSwatch = dynamicScheme.accent1
    override val accent2: ColorSwatch = dynamicScheme.accent2
    override val accent3: ColorSwatch = dynamicScheme.accent3

    companion object {
        // Standard Material You viewing conditions used by the original kdrag0n engine.
        // Stateless across all seeds, so compute once and reuse.
        private val DEFAULT_COND: Zcam.ViewingConditions = run {
            val white = CieXyzAbs.DEFAULT_SDR_WHITE_LUMINANCE
            // backgroundLuminance = relative luminance of a 50 % CIELab gray at the SDR peak
            val bgLuminance = CieLab(50.0, 0.0, 0.0).toXyz().y * white
            Zcam.ViewingConditions(
                surroundFactor = Zcam.ViewingConditions.SURROUND_AVERAGE,
                adaptingLuminance = 0.4 * white,
                backgroundLuminance = bgLuminance,
                referenceWhite = CieXyzAbs(white, white, white),
            )
        }

        // Target palette — the reference chroma/lightness map derived from Pixel defaults.
        private val DEFAULT_TARGETS = MaterialYouTargets(
            chromaFactor = 1.0,
            useLinearLightness = false,
            cond = DEFAULT_COND,
        )
    }
}
