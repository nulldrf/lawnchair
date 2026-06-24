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
import kotlin.math.pow
import kotlin.math.roundToInt

// ── Luminance helpers (SPEC_2021 path only) ───────────────────────────────────

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

/**
 * Converts a kdrag0n [dev.kdrag0n.monet.theme.ColorScheme] to a Compose [ColorScheme]
 * using SPEC_2021 fixed-tone role assignments.
 *
 * Used for [MonetColorSchemeCompat] (SPEC_2021), [SystemColorScheme], and LegacyKdrag.
 */
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

// ── SPEC 2025 ─────────────────────────────────────────────────────────────────

/**
 * Converts a [MonetColorSchemeCompat2025] to a Compose [ColorScheme].
 *
 * Reads role values directly from [MonetColorSchemeCompat2025.scheme] — a
 * materialkolor [DynamicScheme] whose ARGB properties are pre-computed by
 * [MaterialDynamicColors] + [ColorSpec2025].
 *
 * No tone arithmetic, no sparse grid lookups, no [setLuminance] workarounds.
 * Every value is the canonical HCT result matching what MaterialKolor produces.
 *
 * Surface roles (background, surface, surfaceContainer* etc.) use [ColorSpec2025]
 * tone logic which accounts for yellow hue special cases, variant-specific tones,
 * and chroma multipliers — all handled inside the library, not in Lawnchair.
 *
 * [isDark] is already baked into [MonetColorSchemeCompat2025.scheme] at construction,
 * so the correct dark/light variant is always returned regardless of this parameter.
 * We still accept [isDark] here to select the right Compose role set (dark vs light
 * color scheme), which is separate from palette generation.
 */
@Composable
fun MonetColorSchemeCompat2025.toComposeColorScheme2025(isDark: Boolean): ColorScheme =
    remember(this, isDark) {
        val s = scheme

        if (isDark) {
            darkColorScheme(
                primary                  = Color(s.primary),
                onPrimary                = Color(s.onPrimary),
                primaryContainer         = Color(s.primaryContainer),
                onPrimaryContainer       = Color(s.onPrimaryContainer),
                inversePrimary           = Color(s.inversePrimary),
                secondary                = Color(s.secondary),
                onSecondary              = Color(s.onSecondary),
                secondaryContainer       = Color(s.secondaryContainer),
                onSecondaryContainer     = Color(s.onSecondaryContainer),
                tertiary                 = Color(s.tertiary),
                onTertiary               = Color(s.onTertiary),
                tertiaryContainer        = Color(s.tertiaryContainer),
                onTertiaryContainer      = Color(s.onTertiaryContainer),
                background               = Color(s.background),
                onBackground             = Color(s.onBackground),
                surface                  = Color(s.surface),
                onSurface                = Color(s.onSurface),
                surfaceVariant           = Color(s.surfaceVariant),
                onSurfaceVariant         = Color(s.onSurfaceVariant),
                inverseSurface           = Color(s.inverseSurface),
                inverseOnSurface         = Color(s.inverseOnSurface),
                outline                  = Color(s.outline),
                outlineVariant           = Color(s.outlineVariant),
                scrim                    = Color(s.scrim),
                surfaceBright            = Color(s.surfaceBright),
                surfaceDim               = Color(s.surfaceDim),
                surfaceContainerHighest  = Color(s.surfaceContainerHighest),
                surfaceContainerHigh     = Color(s.surfaceContainerHigh),
                surfaceContainer         = Color(s.surfaceContainer),
                surfaceContainerLow      = Color(s.surfaceContainerLow),
                surfaceContainerLowest   = Color(s.surfaceContainerLowest),
                surfaceTint              = Color(s.surfaceTint),
                error                    = Color(s.error),
                onError                  = Color(s.onError),
                errorContainer           = Color(s.errorContainer),
                onErrorContainer         = Color(s.onErrorContainer),
            )
        } else {
            lightColorScheme(
                primary                  = Color(s.primary),
                onPrimary                = Color(s.onPrimary),
                primaryContainer         = Color(s.primaryContainer),
                onPrimaryContainer       = Color(s.onPrimaryContainer),
                inversePrimary           = Color(s.inversePrimary),
                secondary                = Color(s.secondary),
                onSecondary              = Color(s.onSecondary),
                secondaryContainer       = Color(s.secondaryContainer),
                onSecondaryContainer     = Color(s.onSecondaryContainer),
                tertiary                 = Color(s.tertiary),
                onTertiary               = Color(s.onTertiary),
                tertiaryContainer        = Color(s.tertiaryContainer),
                onTertiaryContainer      = Color(s.onTertiaryContainer),
                background               = Color(s.background),
                onBackground             = Color(s.onBackground),
                surface                  = Color(s.surface),
                onSurface                = Color(s.onSurface),
                surfaceVariant           = Color(s.surfaceVariant),
                onSurfaceVariant         = Color(s.onSurfaceVariant),
                inverseSurface           = Color(s.inverseSurface),
                inverseOnSurface         = Color(s.inverseOnSurface),
                outline                  = Color(s.outline),
                outlineVariant           = Color(s.outlineVariant),
                scrim                    = Color(s.scrim),
                surfaceBright            = Color(s.surfaceBright),
                surfaceDim               = Color(s.surfaceDim),
                surfaceContainerHighest  = Color(s.surfaceContainerHighest),
                surfaceContainerHigh     = Color(s.surfaceContainerHigh),
                surfaceContainer         = Color(s.surfaceContainer),
                surfaceContainerLow      = Color(s.surfaceContainerLow),
                surfaceContainerLowest   = Color(s.surfaceContainerLowest),
                surfaceTint              = Color(s.surfaceTint),
                error                    = Color(s.error),
                onError                  = Color(s.onError),
                errorContainer           = Color(s.errorContainer),
                onErrorContainer         = Color(s.onErrorContainer),
            )
        }
    }
