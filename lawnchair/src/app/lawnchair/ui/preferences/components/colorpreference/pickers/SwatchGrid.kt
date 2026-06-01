package app.lawnchair.ui.preferences.components.colorpreference.pickers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.theme.color.KdragMonetColorScheme
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.MonetColorSchemeCompat
import app.lawnchair.theme.color.TonalSpot
import app.lawnchair.theme.toAndroidColor
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.theme.isSelectedThemeDark
import dev.kdrag0n.monet.theme.ColorScheme

object SwatchGridDefaults {
    val GutterSize = 12.dp
    val SwatchMaxWidth = 72.dp
    const val COLUMN_COUNT = 4
}

/**
 * @param colorStyle  When non-null each swatch is rendered using this Monet style
 *   (Presets page only). Pass null to always render with TONAL_SPOT (Custom page).
 * @param simple      When true, renders [SimpleColorSwatch] (solid circle + ring
 *   selection) instead of the full split-circle Monet swatch design.
 */
@Composable
fun <T> SwatchGrid(
    entries: List<ColorPreferenceEntry<T>>,
    onSwatchClick: (T) -> Unit,
    isSwatchSelected: (T) -> Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    colorStyle: ColorStyle? = null,
    simple: Boolean = false,
) {
    val columnCount = SwatchGridDefaults.COLUMN_COUNT
    val rowCount = if (entries.isEmpty()) 0 else (entries.size - 1) / columnCount + 1
    val gutter = SwatchGridDefaults.GutterSize

    Column(
        modifier = modifier.then(contentModifier),
        verticalArrangement = Arrangement.spacedBy(gutter),
    ) {
        for (rowNo in 1..rowCount) {
            val firstIndex = (rowNo - 1) * columnCount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gutter),
            ) {
                for (colIdx in 0 until columnCount) {
                    val entry = entries.getOrNull(firstIndex + colIdx)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (entry != null) {
                            if (simple) {
                                SimpleColorSwatch(
                                    entry = entry,
                                    onClick = { onSwatchClick(entry.value) },
                                    selected = isSwatchSelected(entry.value),
                                    modifier = Modifier.widthIn(0.dp, SwatchGridDefaults.SwatchMaxWidth),
                                )
                            } else {
                                ColorSwatch(
                                    entry = entry,
                                    onClick = { onSwatchClick(entry.value) },
                                    selected = isSwatchSelected(entry.value),
                                    colorStyle = colorStyle,
                                    forceStyle = if (entry.value is ColorOption.SystemAccent) {
                                        TonalSpot
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.widthIn(0.dp, SwatchGridDefaults.SwatchMaxWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Simple swatch — solid circle, ring selection indicator
// ---------------------------------------------------------------------------

/**
 * A plain filled circle swatch used for non-accent color preferences.
 * Selected state is shown as a ring drawn via Canvas — no padding modifier
 * so spring overshoot can never cause a negative-padding crash.
 */
@Composable
fun <T> SimpleColorSwatch(
    entry: ColorPreferenceEntry<T>,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSelectedThemeDark
    // Check lightColor == 0 to detect Default — darkColor calls lightenColor(0)
    // which returns a non-zero value, so checking colorInt == 0 in dark mode fails.
    val lightColorInt = entry.lightColor(context)
    val isDefault = lightColorInt == 0
    val colorInt = if (isDark) entry.darkColor(context) else lightColorInt
    // Default/Managed-by-Lawnchair: hardcoded gray outer circle, near-white inner
    // badge with near-black letter — always legible on light and dark backgrounds.
    val color = if (!isDefault) Color(colorInt) else Color(0xFF9E9E9E)
    val defaultBadgeBg = Color(0xFFEEEEEE)   // Near-white — high contrast on gray
    val defaultBadgeFg = Color(0xFF212121)    // Near-black on near-white

    // Ring stroke width — animate with no-bounce spring to avoid negative values
    val ringStroke by animateDpAsState(
        targetValue = if (selected) 2.5.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ringStroke_$selected",
    )
    // Gap between circle edge and ring
    val ringGap by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ringGap_$selected",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(onClick = onClick),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                val circleRadius = (size.minDimension / 2f) - (ringStroke.toPx() + ringGap.toPx())
                // Solid filled circle
                drawCircle(
                    color = color,
                    radius = circleRadius.coerceAtLeast(1f),
                    center = center,
                )
                // Ring drawn outside the circle
                if (ringStroke.value > 0f) {
                    drawCircle(
                        color = color,
                        radius = (circleRadius + ringGap.toPx() + ringStroke.toPx() / 2f)
                            .coerceAtLeast(1f),
                        center = center,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = ringStroke.toPx().coerceAtLeast(0f),
                        ),
                    )
                }
            }
            // "Managed by Lawnchair" Default option: fixed-size badge with "A" letter.
            // Hardcoded gray palette so it is always visible on any theme background.
            if (isDefault) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(defaultBadgeBg),
                ) {
                    Text(
                        text = "A",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                        color = defaultBadgeFg,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Full Monet swatch — split-circle, used for accent color only
// ---------------------------------------------------------------------------

@Composable
fun <T> ColorSwatch(
    entry: ColorPreferenceEntry<T>,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    colorStyle: ColorStyle? = null,
    forceStyle: ColorStyle? = null,
) {
    val context = LocalContext.current
    val isDark = isSelectedThemeDark
    val baseColorInt = if (isDark) entry.darkColor(context) else entry.lightColor(context)

    val effectiveStyle = forceStyle ?: colorStyle
    val scheme: ColorScheme = remember(baseColorInt, effectiveStyle) {
        buildScheme(baseColorInt, effectiveStyle)
    }

    val topHalf = remember(scheme) {
        Color(scheme.accent1[100]?.toAndroidColor() ?: baseColorInt)
    }
    val bottomHalf = remember(scheme) {
        Color(scheme.accent3[100]?.toAndroidColor() ?: baseColorInt)
    }
    val innerCircle = remember(scheme) {
        Color(scheme.accent1[600]?.toAndroidColor() ?: baseColorInt)
    }
    val containerBg = remember(scheme, isDark) {
        if (isDark) {
            Color(scheme.accent1[900]?.toAndroidColor() ?: baseColorInt)
        } else {
            Color(scheme.accent1[50]?.toAndroidColor() ?: baseColorInt)
        }
    }
    val checkTint = topHalf

    val centerCircleSize by animateDpAsState(
        targetValue = if (selected) 32.dp else 24.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "centerCircle_$selected",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(containerBg)
            .clickable(onClick = onClick),
    ) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val radius = size.minDimension / 2f
            val circlePath = Path().apply { addOval(Rect(center, radius)) }
            clipPath(circlePath) {
                drawRect(
                    color = topHalf,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height / 2f),
                )
                drawRect(
                    color = bottomHalf,
                    topLeft = Offset(0f, size.height / 2f),
                    size = Size(size.width, size.height / 2f),
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(centerCircleSize)
                .clip(CircleShape)
                .background(innerCircle),
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun buildScheme(seedColor: Int, colorStyle: ColorStyle?): ColorScheme =
    when (colorStyle) {
        is LegacyKdrag -> KdragMonetColorScheme(seedColor)
        null -> MonetColorSchemeCompat(seedColor, com.android.systemui.monet.Style.TONAL_SPOT)
        else -> MonetColorSchemeCompat(seedColor, colorStyle.style)
    }
