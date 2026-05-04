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
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.theme.isSelectedThemeDark

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
                    if (colIdx > 0) {
                        Spacer(modifier = Modifier.width(gutter))
                    }
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

            if (rowNo != rowCount) {
                Spacer(modifier = Modifier.height(gutter))
            }
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

    val baseColorInt = if (isDark) {
        entry.darkColor(context)
    } else {
        entry.lightColor(context)
    }

    val hsv = remember(baseColorInt) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(baseColorInt, it) }
    }

    // In dark mode keep colours fairly dark; in light mode use noticeably
    // lighter shades so the split-circle doesn't look like a black blob.
    val darkFactor = if (isDark) 0.58f else 0.72f
    val accentDarkFactor = if (isDark) 0.54f else 0.68f

    // Top half of the outer circle: darkened primary
    val darkPrimary = Color.hsv(
        hue = hsv[0],
        saturation = hsv[1],
        value = (hsv[2] * darkFactor).coerceIn(0.12f, 0.85f),
    )
    // Bottom half: analogous accent, shifted ~28° in hue, also darkened
    val darkAccent = Color.hsv(
        hue = (hsv[0] + 28f) % 360f,
        saturation = (hsv[1] * 0.85f).coerceAtLeast(0.18f),
        value = (hsv[2] * accentDarkFactor).coerceIn(0.12f, 0.82f),
    )
    // Center circle: bright / light version of the primary
    val lightPrimary = Color.hsv(
        hue = hsv[0],
        saturation = (hsv[1] * 0.70f).coerceAtLeast(0.12f),
        value = (hsv[2] + 0.28f).coerceAtMost(1f),
    )

    // Container: surface with a very light wash of the base colour
    val containerTint = Color(baseColorInt).copy(alpha = 0.13f)

    // Center circle grows on selection with a bouncy spring
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
            .background(MaterialTheme.colorScheme.surface)
            .background(containerTint)
            .clickable(onClick = onClick),
    ) {
        // Split-circle drawn via Canvas
        Canvas(modifier = Modifier.size(54.dp)) {
            val radius = size.minDimension / 2f
            val circlePath = Path().apply {
                addOval(Rect(center, radius))
            }
            clipPath(circlePath) {
                // Top half — dark primary
                drawRect(
                    color = darkPrimary,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height / 2f),
                )
                // Bottom half — dark accent
                drawRect(
                    color = darkAccent,
                    topLeft = Offset(0f, size.height / 2f),
                    size = Size(size.width, size.height / 2f),
                )
            }
        }

        // Animated center circle with Done checkmark when selected
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(centerCircleSize)
                .clip(CircleShape)
                .background(lightPrimary),
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Done,
                    contentDescription = null,
                    tint = darkPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
