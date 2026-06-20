package app.lawnchair.ui.preferences.destinations

import android.app.WallpaperManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.theme.color.KdragMonetColorScheme
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.MonetColorSchemeCompat
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import com.android.launcher3.R
import com.android.systemui.monet.SpecVersion
import com.android.systemui.monet.Style
import dev.kdrag0n.colorkt.Color as KdragColor
import dev.kdrag0n.monet.theme.ColorScheme as KdragColorScheme
import dev.kdrag0n.monet.theme.toComposeColor

@Composable
fun ColorStyleScreen(
    showLegacyKdrag: Boolean = false,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val styleAdapter = prefs2.colorStyle.getAdapter()

    val currentStyle = styleAdapter.state.value
    val currentSpec = prefs2.colorSpec.asState().value

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val accentColorValue = prefs2.accentColor.asState().value
    val fallbackSeed = MaterialTheme.colorScheme.primary.toArgb()
    val rawWallpaperSeed: Int = remember(accentColorValue) {
        when (accentColorValue) {
            is ColorOption.CustomColor -> accentColorValue.color
            is ColorOption.WallpaperPrimary ->
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    ?.primaryColor
                    ?.toArgb()
                    ?: fallbackSeed
            is ColorOption.WallpaperDerived -> accentColorValue.color
            else -> fallbackSeed
        }
    }

    val styles = remember(showLegacyKdrag) {
        ColorStyle.values().filter { it !is LegacyKdrag || showLegacyKdrag }
    }

    val allPreviewColors = remember(rawWallpaperSeed, isDark, showLegacyKdrag, currentSpec) {
        styles.associateWith { style ->
            buildStylePreviewColors(
                style = style,
                rawWallpaperSeed = rawWallpaperSeed,
                isDark = isDark,
                specVersion = currentSpec,
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
            // Show the current spec as a badge on styles that support both specs
            // (Spritz, TonalSpot, Vibrant, Expressive). Others always use 2021.
            // LegacyKdrag uses TonalSpot as a placeholder style value — exclude it
            // from spec badges since it uses its own ZCAM engine regardless of spec.
            val specBadgeStyles = setOf(Style.SPRITZ, Style.TONAL_SPOT, Style.VIBRANT, Style.EXPRESSIVE)
            val showSpecBadge = specBadgeStyles.contains(style.style) && style !is LegacyKdrag

            ColorStyleCard(
                style = style,
                isSelected = style == currentStyle,
                previewColors = allPreviewColors.getValue(style),
                onClick = { styleAdapter.onChange(style) },
                specBadge = if (showSpecBadge) currentSpec else null,
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
    specBadge: SpecVersion? = null,
) {
    val cardShape = MaterialTheme.shapes.extraLarge

    val backgroundColor = if (isSelected) {
        lerp(previewColors.cardBackground, previewColors.primary, 0.18f)
    } else {
        previewColors.cardBackground
    }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(id = style.nameResourceId),
                        style = MaterialTheme.typography.titleSmall,
                        color = previewColors.onCardBackground,
                    )
                    // Spec badge — shows the currently active spec version
                    if (specBadge != null) {
                        val badgeLabel = when (specBadge) {
                            SpecVersion.SPEC_2025 -> "2025"
                            else -> "2021"
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = previewColors.primary.copy(alpha = 0.18f),
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = previewColors.onCardBackground.copy(alpha = 0.75f),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(id = style.descriptionResourceId),
                    style = MaterialTheme.typography.bodySmall,
                    color = previewColors.onCardBackground.copy(alpha = 0.72f),
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .drawBehind { drawCircle(color = previewColors.primary) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = backgroundColor,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
//  Style preview icon — three-arc circle in a rounded rect
// ---------------------------------------------------------------------------

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
            drawRect(color = colors.container)

            val diameter = size.minDimension * 0.78f
            val radius = diameter / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val arcTopLeft = Offset(cx - radius, cy - radius)
            val arcSize = Size(diameter, diameter)

            drawArc(color = colors.primary, startAngle = 180f, sweepAngle = 180f,
                useCenter = true, topLeft = arcTopLeft, size = arcSize)
            drawArc(color = colors.tertiary, startAngle = 90f, sweepAngle = 90f,
                useCenter = true, topLeft = arcTopLeft, size = arcSize)
            drawArc(color = colors.secondary, startAngle = 0f, sweepAngle = 90f,
                useCenter = true, topLeft = arcTopLeft, size = arcSize)
        }
    }
}

// ---------------------------------------------------------------------------
//  Per-style colour generation
// ---------------------------------------------------------------------------

private data class StylePreviewColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val container: Color,
    val cardBackground: Color,
    val onCardBackground: Color,
    val outlineVariant: Color,
)

private fun buildStylePreviewColors(
    style: ColorStyle,
    rawWallpaperSeed: Int,
    isDark: Boolean,
    specVersion: SpecVersion = SpecVersion.SPEC_2021,
): StylePreviewColors {
    val scheme: KdragColorScheme = when (style) {
        is LegacyKdrag -> KdragMonetColorScheme(rawWallpaperSeed)
        else -> MonetColorSchemeCompat(rawWallpaperSeed, style.style, specVersion)
    }

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

private fun Map<Int, KdragColor>.composeColor(key: Int): Color =
    this[key]?.toComposeColor() ?: Color.Gray
