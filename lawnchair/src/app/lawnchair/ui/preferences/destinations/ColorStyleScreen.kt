package app.lawnchair.ui.preferences.destinations

import android.app.WallpaperManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.theme.color.KdragMonetColorScheme
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.MonetColorSchemeCompat
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import com.android.launcher3.R
import dev.kdrag0n.colorkt.Color as KdragColor
import dev.kdrag0n.monet.theme.ColorScheme as KdragColorScheme
import dev.kdrag0n.monet.theme.toComposeColor

@Composable
fun ColorStyleScreen(
    showLegacyKdrag: Boolean = false,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val adapter = prefs2.colorStyle.getAdapter()
    val currentStyle = adapter.state.value
    val isDark = isSystemInDarkTheme()

    // Raw wallpaper primary — fed into every Monet engine as seed so that
    // preview colours are driven by the actual wallpaper colour, not by the
    // AOSP-processed primary that shifts whenever the active style changes.
    // WallpaperManager.getWallpaperColors returns colours extracted directly
    // from the wallpaper bitmap, before any Monet processing.
    // Fallback if WallpaperManager returns null (e.g. solid-color wallpaper).
    // Must be read outside the remember lambda — composable reads are not
    // allowed inside remember { }.
    val fallbackSeed = MaterialTheme.colorScheme.primary.toArgb()
    val rawWallpaperSeed: Int = remember {
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
            ?: fallbackSeed
    }

    val styles = remember(showLegacyKdrag) {
        ColorStyle.values().filter { it !is LegacyKdrag || showLegacyKdrag }
    }

    // Pre-build all preview palettes in one block so card composition is cheap.
    val allPreviewColors = remember(rawWallpaperSeed, isDark, showLegacyKdrag) {
        styles.associateWith { style ->
            buildStylePreviewColors(
                style = style,
                rawWallpaperSeed = rawWallpaperSeed,
                isDark = isDark,
            )
        }
    }

    PreferenceLayoutLazyColumn(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.color_style_label),
    ) {
        items(
            items = styles,
            key = { it.toString() },
        ) { style ->
            ColorStyleCard(
                style = style,
                isSelected = style == currentStyle,
                previewColors = allPreviewColors.getValue(style),
                onClick = { adapter.onChange(style) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Card
// ---------------------------------------------------------------------------

@Composable
private fun ColorStyleCard(
    style: ColorStyle,
    isSelected: Boolean,
    previewColors: StylePreviewColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = MaterialTheme.shapes.extraLarge

    // Card surface comes from this style's OWN neutral palette, not the active
    // theme — so Monochromatic looks gray, Vibrant looks more colourful, etc.
    val backgroundColor = if (isSelected) {
        // Tint the card's own surface toward its primary colour.
        lerp(previewColors.cardBackground, previewColors.primary, 0.18f)
    } else {
        previewColors.cardBackground
    }

    // Border also uses the style's own primary when selected.
    val borderColor = if (isSelected) previewColors.primary else previewColors.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Surface(
        color = backgroundColor,
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .border(width = borderWidth, color = borderColor, shape = cardShape)
            .clip(cardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            StylePreviewIcon(
                colors = previewColors,
                modifier = Modifier.size(56.dp),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(id = style.nameResourceId),
                    style = MaterialTheme.typography.titleSmall,
                    color = previewColors.onCardBackground,
                )
                Text(
                    text = stringResource(id = style.descriptionResourceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = previewColors.onCardBackground.copy(alpha = 0.72f),
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = previewColors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Style preview icon
// ---------------------------------------------------------------------------

/**
 * Rounded-rectangle container holding a circle split into three arcs:
 *
 * ```
 *  ╭────────────────────╮
 *  │  ╭──────────────╮  │  container  = neutral1 mid-dark tone
 *  │  │▓▓▓▓▓▓▓▓▓▓▓▓▓│  │  upper half = accent1  (primary)
 *  │  │░░░░░░│▒▒▒▒▒▒│  │  lower-left = accent3  (tertiary / hue-shifted)
 *  │  ╰──────────────╯  │  lower-right= accent2  (secondary)
 *  ╰────────────────────╯
 * ```
 *
 * Compose Canvas angles (clockwise from 3 o'clock):
 *   upper half   → startAngle=180, sweep=180  (left→top→right)
 *   lower-left   → startAngle=90,  sweep=90   (bottom→left)   ← accent3/tertiary
 *   lower-right  → startAngle=0,   sweep=90   (right→bottom)  ← accent2/secondary
 */
@Composable
private fun StylePreviewIcon(
    colors: StylePreviewColors,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Round-rect background (filled by the clip above, drawn explicitly
            // here so the container colour is the style's own neutral).
            drawRect(color = colors.container)

            val diameter = size.minDimension * 0.78f
            val radius = diameter / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arcTopLeft = Offset(cx - radius, cy - radius)
            val arcSize = Size(diameter, diameter)

            // Upper half — accent1 (primary)
            drawArc(
                color = colors.primary,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = arcTopLeft,
                size = arcSize,
            )

            // Lower-left quarter — accent3 (tertiary, hue-shifted colour)
            drawArc(
                color = colors.tertiary,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = arcTopLeft,
                size = arcSize,
            )

            // Lower-right quarter — accent2 (secondary)
            drawArc(
                color = colors.secondary,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = arcTopLeft,
                size = arcSize,
            )
        }
    }
}

// ---------------------------------------------------------------------------
//  Per-style colour generation
// ---------------------------------------------------------------------------

private data class StylePreviewColors(
    val primary: Color,          // accent1 — upper arc
    val secondary: Color,        // accent2 — lower-right arc
    val tertiary: Color,         // accent3 — lower-left arc  (hue-shifted)
    val container: Color,        // neutral1 mid-dark — icon round-rect background
    val cardBackground: Color,   // neutral1 very light/dark — card surface
    val onCardBackground: Color, // neutral1 contrasting — text colour
    val outlineVariant: Color,   // neutral2 mid — unselected border
)

/**
 * Builds [StylePreviewColors] for [style] using the appropriate Monet engine.
 *
 * - [LegacyKdrag] → [KdragMonetColorScheme] fed with [rawWallpaperSeed] (the
 *   colour extracted directly from the wallpaper bitmap, before any AOSP monet
 *   processing). Feeding the AOSP-transformed primary into ZCAM produces
 *   tonal-spot-like results, which is why the circle was wrong at runtime.
 *
 * - All other styles → [MonetColorSchemeCompat] wrapping the AOSP engine.
 *
 * Both engines expose swatches as `Map<Int, dev.kdrag0n.colorkt.Color>` with
 * the same tone keys (0, 10, 50, 100, 200 … 1000).  [toComposeColor] converts
 * any kdrag0n colour to a Compose [Color] via [toAndroidColor].
 *
 * Tone key guide (higher key = darker):
 *   100 = very light   800 = medium-dark
 *   200 = light        900 = dark
 *   500 = saturated mid
 */
private fun buildStylePreviewColors(
    style: ColorStyle,
    rawWallpaperSeed: Int,
    isDark: Boolean,
): StylePreviewColors {
    val scheme: KdragColorScheme = when (style) {
        is LegacyKdrag -> KdragMonetColorScheme(rawWallpaperSeed)
        else -> MonetColorSchemeCompat(rawWallpaperSeed, style.style)
    }

    // Tone keys for light/dark theme variants.
    val containerKey = if (isDark) 700 else 100
    val cardBgKey = if (isDark) 900 else 50
    val onCardKey = if (isDark) 100 else 900
    val outlineKey = if (isDark) 700 else 300

    return StylePreviewColors(
        primary = scheme.accent1.composeColor(500),
        secondary = scheme.accent2.composeColor(400),
        tertiary = scheme.accent3.composeColor(300),
        container = scheme.neutral1.composeColor(containerKey),
        cardBackground = scheme.neutral2.composeColor(cardBgKey),
        onCardBackground = scheme.neutral1.composeColor(onCardKey),
        outlineVariant = scheme.neutral2.composeColor(outlineKey),
    )
}

// ---------------------------------------------------------------------------
//  Colour conversion helper
// ---------------------------------------------------------------------------

/**
 * Looks up [key] in this kdrag0n ColorSwatch and converts the result to a
 * Compose [Color] via [toComposeColor] (which calls through [toAndroidColor]).
 *
 * Falls back to [Color.Gray] if the key is absent — this should never happen
 * with a well-formed palette.
 */
private fun Map<Int, KdragColor>.composeColor(key: Int): Color =
    this[key]?.toComposeColor() ?: Color.Gray
