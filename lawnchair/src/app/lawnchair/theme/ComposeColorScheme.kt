package app.lawnchair.theme

import androidx.annotation.FloatRange
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.core.math.MathUtils
import app.lawnchair.theme.color.MonetColorSchemeCompat2025
import com.android.systemui.monet.TonalPalette
import kotlin.math.pow
import kotlin.math.roundToInt

// ── Luminance helpers ─────────────────────────────────────────────────────────

/**
 * Set the luminance (L*) of this color, preserving hue and chroma as much as
 * possible. Used for intermediate surface tones that don't land on a shade key.
 */
internal fun Color.setLuminance(
    @FloatRange(from = 0.0, to = 100.0) newLuminance: Float,
): Color {
    if ((newLuminance < 0.0001) or (newLuminance > 99.9999)) {
        val y = 100 * labInvf((newLuminance + 16) / 116)
        val component = delinearized(y)
        return Color(component, component, component)
    }
    val sLAB = this.convert(ColorSpaces.CieLab)
    return Color(
        newLuminance,
        sLAB.component2(),
        sLAB.component3(),
        colorSpace = ColorSpaces.CieLab,
    ).convert(ColorSpaces.Srgb)
}

private fun labInvf(ft: Float): Float {
    val e = 216f / 24389f
    val kappa = 24389f / 27f
    val ft3 = ft * ft * ft
    return if (ft3 > e) ft3 else (116 * ft - 16) / kappa
}

private fun delinearized(rgbComponent: Float): Int {
    val normalized = rgbComponent / 100
    val delinearized =
        if (normalized <= 0.0031308) normalized * 12.92
        else 1.055 * normalized.toDouble().pow(1.0 / 2.4) - 0.055
    return MathUtils.clamp((delinearized * 255.0).roundToInt(), 0, 255)
}

// ── SPEC 2021 ─────────────────────────────────────────────────────────────────

@Composable
fun dev.kdrag0n.monet.theme.ColorScheme.toComposeColorScheme(isDark: Boolean): ColorScheme =
    remember(this, isDark) {
        val neutral4  = neutral(40).setLuminance(4f)
        val neutral6  = neutral(40).setLuminance(6f)
        val neutral12 = neutral(40).setLuminance(12f)
        val neutral17 = neutral(40).setLuminance(17f)
        val neutral22 = neutral(40).setLuminance(22f)
        val neutral24 = neutral(40).setLuminance(24f)
        val neutral87 = neutral(40).setLuminance(87f)
        val neutral92 = neutral(40).setLuminance(92f)
        val neutral94 = neutral(40).setLuminance(94f)
        val neutral96 = neutral(40).setLuminance(96f)
        val neutral98 = neutral(40).setLuminance(98f)

        if (isDark) {
            darkColorScheme(
                primary                  = primary(80),
                onPrimary                = primary(20),
                primaryContainer         = primary(30),
                onPrimaryContainer       = primary(90),
                inversePrimary           = primary(40),
                secondary                = secondary(80),
                onSecondary              = secondary(20),
                secondaryContainer       = secondary(30),
                onSecondaryContainer     = secondary(90),
                tertiary                 = tertiary(80),
                onTertiary               = tertiary(20),
                background               = neutral6,
                onBackground             = neutral(90),
                surface                  = neutral6,
                onSurface                = neutral(90),
                surfaceVariant           = neutralVariant(30),
                onSurfaceVariant         = neutralVariant(80),
                inverseSurface           = neutral(90),
                inverseOnSurface         = neutral(20),
                outline                  = neutralVariant(60),
                outlineVariant           = neutralVariant(30),
                scrim                    = neutral(0),
                surfaceBright            = neutral24,
                surfaceDim               = neutral6,
                surfaceContainerHighest  = neutral22,
                surfaceContainerHigh     = neutral17,
                surfaceContainer         = neutral12,
                surfaceContainerLow      = neutralVariant(10),
                surfaceContainerLowest   = neutral4,
                surfaceTint              = primary(80),
            )
        } else {
            lightColorScheme(
                primary                  = primary(40),
                onPrimary                = primary(100),
                primaryContainer         = primary(90),
                onPrimaryContainer       = primary(10),
                inversePrimary           = primary(80),
                secondary                = secondary(40),
                onSecondary              = secondary(100),
                secondaryContainer       = secondary(90),
                onSecondaryContainer     = secondary(10),
                tertiary                 = tertiary(40),
                onTertiary               = tertiary(100),
                tertiaryContainer        = tertiary(90),
                onTertiaryContainer      = tertiary(10),
                background               = neutral94,
                onBackground             = neutralVariant(10),
                surface                  = neutral94,
                onSurface                = neutralVariant(10),
                surfaceVariant           = neutralVariant(90),
                onSurfaceVariant         = neutralVariant(30),
                inverseSurface           = neutral(20),
                inverseOnSurface         = neutral(95),
                outline                  = neutralVariant(50),
                outlineVariant           = neutralVariant(80),
                scrim                    = neutral(0),
                surfaceBright            = neutral98,
                surfaceDim               = neutral87,
                surfaceContainerHighest  = neutral(90),
                surfaceContainerHigh     = neutral92,
                surfaceContainer         = neutral98,
                surfaceContainerLow      = neutral96,
                surfaceContainerLowest   = neutral(100),
                surfaceTint              = primary(40),
            )
        }
    }

// ── SPEC 2025 tone utilities ──────────────────────────────────────────────────

/**
 * Returns the Compose [Color] at a shade-key-aligned Material tone (0–100).
 *
 * Only accurate at tones that land exactly on a shade key:
 *   0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99
 *
 * For intermediate tones (4, 6, 12, 17, 22, 24, 87, 92, 94, 96, 98) use
 * [atToneInterpolated] which applies [setLuminance] for accurate L* values.
 *
 * Shade key mapping: shadeKey = (100 - tone) * 10
 *   tone 0  → shadeKey 1000 → lstar 0   (black)
 *   tone 40 → shadeKey 600  → lstar 40
 *   tone 80 → shadeKey 200  → lstar 80
 *   tone 100 → shadeKey 0   (no key; fallback to key 10 → lstar 99)
 */
private fun TonalPalette.atTone(tone: Int): Color {
    val clamped  = tone.coerceIn(0, 100)
    val shadeKey = (100 - clamped) * 10
    val map      = allShadesMapped
    return Color(
        map[shadeKey]
            ?: map.keys.minByOrNull { kotlin.math.abs(it - shadeKey) }
                ?.let { map[it] }
            ?: allShades.first()
    )
}

/**
 * Returns the Compose [Color] at an intermediate [lstar] (0–100) by:
 *   1. Sampling the palette at the nearest available shade key tone as a base hue/chroma.
 *   2. Shifting that color's L* to the exact [lstar] value using CIE Lab interpolation.
 *
 * This is the same technique used by [toComposeColorScheme] (SPEC_2021) via
 * [setLuminance], and is required for surface tones like 4, 6, 12, 17, 22, 24,
 * 87, 92, 94, 96, 98 which don't land on a shade key boundary.
 *
 * Without this, multiple intermediate tones collapse to the same shade key,
 * making card surfaces indistinguishable from the background in dark mode.
 */
private fun TonalPalette.atToneInterpolated(lstar: Float): Color {
    // Use the mid-range shade (tone 40, shadeKey 600) as the hue/chroma anchor.
    // This tone is always present and has the most representative chroma.
    val anchor = atTone(40)
    return anchor.setLuminance(lstar)
}

// ── SPEC 2025 ─────────────────────────────────────────────────────────────────

/**
 * Converts a [MonetColorSchemeCompat2025] to a Compose [ColorScheme].
 *
 * Role assignments use the same fixed tones as SPEC_2021. The 2025 difference
 * is in palette generation (richer chroma, new hue rotations in ColorScheme.kt).
 *
 * Roles at exact shade-key tones (0,10,20,30,40,80,90,95,99) use [atTone].
 * Intermediate surface tones (4,6,12,17,22,24,87,92,94,96,98) use
 * [atToneInterpolated] which applies [setLuminance] for accurate L* values,
 * preventing surface cards from merging with the background in dark mode.
 */
@Composable
fun MonetColorSchemeCompat2025.toComposeColorScheme2025(isDark: Boolean): ColorScheme =
    remember(this, isDark) {
        val p1 = accent1
        val p2 = accent2
        val p3 = accent3
        val n1 = neutral1
        val n2 = neutral2

        if (isDark) {
            darkColorScheme(
                primary                  = p1.atTone(80),
                onPrimary                = p1.atTone(20),
                primaryContainer         = p1.atTone(30),
                onPrimaryContainer       = p1.atTone(90),
                inversePrimary           = p1.atTone(40),
                secondary                = p2.atTone(80),
                onSecondary              = p2.atTone(20),
                secondaryContainer       = p2.atTone(30),
                onSecondaryContainer     = p2.atTone(90),
                tertiary                 = p3.atTone(80),
                onTertiary               = p3.atTone(20),
                tertiaryContainer        = p3.atTone(30),
                onTertiaryContainer      = p3.atTone(90),
                // Surface roles: use atToneInterpolated for intermediate L* values
                // so each level is visually distinct (avoids card/background collapse)
                background               = n1.atToneInterpolated(6f),
                onBackground             = n1.atTone(90),
                surface                  = n1.atToneInterpolated(6f),
                onSurface                = n1.atTone(90),
                surfaceVariant           = n2.atTone(30),
                onSurfaceVariant         = n2.atTone(80),
                inverseSurface           = n1.atTone(90),
                inverseOnSurface         = n1.atTone(20),
                outline                  = n2.atTone(60),
                outlineVariant           = n2.atTone(30),
                scrim                    = n1.atTone(0),
                surfaceBright            = n1.atToneInterpolated(24f),
                surfaceDim               = n1.atToneInterpolated(6f),
                surfaceContainerHighest  = n1.atToneInterpolated(22f),
                surfaceContainerHigh     = n1.atToneInterpolated(17f),
                surfaceContainer         = n1.atToneInterpolated(12f),
                surfaceContainerLow      = n2.atTone(10),
                surfaceContainerLowest   = n1.atToneInterpolated(4f),
                surfaceTint              = p1.atTone(80),
            )
        } else {
            lightColorScheme(
                primary                  = p1.atTone(40),
                onPrimary                = p1.atTone(100),
                primaryContainer         = p1.atTone(90),
                onPrimaryContainer       = p1.atTone(10),
                inversePrimary           = p1.atTone(80),
                secondary                = p2.atTone(40),
                onSecondary              = p2.atTone(100),
                secondaryContainer       = p2.atTone(90),
                onSecondaryContainer     = p2.atTone(10),
                tertiary                 = p3.atTone(40),
                onTertiary               = p3.atTone(100),
                tertiaryContainer        = p3.atTone(90),
                onTertiaryContainer      = p3.atTone(10),
                // Surface roles: use atToneInterpolated for intermediate L* values
                background               = n1.atToneInterpolated(94f),
                onBackground             = n2.atTone(10),
                surface                  = n1.atToneInterpolated(94f),
                onSurface                = n2.atTone(10),
                surfaceVariant           = n2.atTone(90),
                onSurfaceVariant         = n2.atTone(30),
                inverseSurface           = n1.atTone(20),
                inverseOnSurface         = n1.atTone(95),
                outline                  = n2.atTone(50),
                outlineVariant           = n2.atTone(80),
                scrim                    = n1.atTone(0),
                surfaceBright            = n1.atToneInterpolated(98f),
                surfaceDim               = n1.atToneInterpolated(87f),
                surfaceContainerHighest  = n1.atTone(90),
                surfaceContainerHigh     = n1.atToneInterpolated(92f),
                surfaceContainer         = n1.atToneInterpolated(98f),
                surfaceContainerLow      = n1.atToneInterpolated(96f),
                surfaceContainerLowest   = n1.atTone(100),
                surfaceTint              = p1.atTone(40),
            )
        }
    }
