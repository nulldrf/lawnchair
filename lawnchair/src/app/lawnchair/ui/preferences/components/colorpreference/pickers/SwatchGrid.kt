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
import app.lawnchair.theme.color.AndroidColor
import app.lawnchair.theme.color.MonetColorSchemeCompat
import app.lawnchair.theme.toAndroidColor
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.systemui.monet.Style

object SwatchGridDefaults {
    val GutterSize = 12.dp
    val SwatchMaxWidth = 72.dp
    const val COLUMN_COUNT = 4
}

@Composable
fun <T> SwatchGrid(
    entries: List<ColorPreferenceEntry<T>>,
    onSwatchClick: (T) -> Unit,
    isSwatchSelected: (T) -> Boolean,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    val columnCount = SwatchGridDefaults.COLUMN_COUNT
    val rowCount = if (entries.isEmpty()) 0 else (entries.size - 1) / columnCount + 1
    val gutter = SwatchGridDefaults.GutterSize

    Column(modifier = modifier.then(contentModifier)) {
        for (rowNo in 1..rowCount) {
            val firstIndex = (rowNo - 1) * columnCount

            Row(modifier = Modifier.fillMaxWidth()) {
                for (colIdx in 0 until columnCount) {
                    if (colIdx > 0) Spacer(modifier = Modifier.width(gutter))
                    val entry = entries.getOrNull(firstIndex + colIdx)
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (entry != null) {
                            ColorSwatch(
                                entry = entry,
                                onClick = { onSwatchClick(entry.value) },
                                selected = isSwatchSelected(entry.value),
                                modifier = Modifier.widthIn(0.dp, SwatchGridDefaults.SwatchMaxWidth),
                            )
                        }
                    }
                }
            }

            if (rowNo != rowCount) Spacer(modifier = Modifier.height(gutter))
        }
    }
}

@Composable
fun <T> ColorSwatch(
    entry: ColorPreferenceEntry<T>,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSelectedThemeDark
    val baseColorInt = if (isDark) entry.darkColor(context) else entry.lightColor(context)

    // Build a full Monet palette seeded from this swatch's own colour so
    // every swatch is visually consistent with the M3 token system, regardless
    // of the global theme accent.
    val scheme = remember(baseColorInt) {
        MonetColorSchemeCompat(baseColorInt, Style.TONAL_SPOT)
    }

    // Outer-circle top half  → accent1 shade 100 (light primary pastel)
    // Outer-circle bottom half → accent3 shade 100 (light tertiary pastel)
    // Inner circle            → accent1 shade 600 (bold/vibrant primary)
    // Container background    → accent1 shade 50  (barely-there tint)
    val topHalf = remember(scheme) {
        Color(scheme.accent1[100]?.toAndroidColor() ?: baseColorInt)
    }
    val bottomHalf = remember(scheme) {
        Color(scheme.accent3[100]?.toAndroidColor() ?: baseColorInt)
    }
    val innerCircle = remember(scheme) {
        Color(scheme.accent1[600]?.toAndroidColor() ?: baseColorInt)
    }
    val containerBg = remember(scheme) {
        // accent1 shade 50 is the lightest tinted surface
        Color(scheme.accent1[50]?.toAndroidColor() ?: baseColorInt)
    }

    // Checkmark tint sits on the inner circle — use accent1[100] so it
    // always contrasts against the vibrant inner circle fill.
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
        // Split-circle: top = accent1[100], bottom = accent3[100]
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

        // Animated inner circle: accent1[600], grows + shows checkmark on select
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
