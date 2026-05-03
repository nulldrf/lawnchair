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
                    val entryIdx = firstIndex + colIdx
                    val entry = entries.getOrNull(entryIdx)
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
    val baseColorInt = if (isSelectedThemeDark) {
        entry.darkColor(context)
    } else {
        entry.lightColor(context)
    }

    // Derive three visual layers from the base color HSV
    val hsv = remember(baseColorInt) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(baseColorInt, it) }
    }

    // Top half of the outer circle: darkened primary
    val darkPrimary = Color.hsv(
        hue = hsv[0],
        saturation = hsv[1],
        value = (hsv[2] * 0.55f).coerceIn(0.15f, 0.75f),
    )
    // Bottom half of the outer circle: darkened analogous accent (hue shifted ~28°)
    val darkAccent = Color.hsv(
        hue = (hsv[0] + 28f) % 360f,
        saturation = (hsv[1] * 0.85f).coerceAtLeast(0.2f),
        value = (hsv[2] * 0.55f).coerceIn(0.15f, 0.75f),
    )
    // Center circle: lighter/brighter version of the primary
    val lightPrimary = Color.hsv(
        hue = hsv[0],
        saturation = (hsv[1] * 0.75f).coerceAtLeast(0.15f),
        value = (hsv[2] + 0.25f).coerceAtMost(1f),
    )

    // Container tint: surface + a very light wash of the base color
    val containerTint = Color(baseColorInt).copy(alpha = 0.13f)

    // Center circle animates larger when selected
    val centerCircleSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "centerCircleSize",
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
        // Outer split circle drawn via Canvas
        Canvas(modifier = Modifier.size(52.dp)) {
            val radius = size.minDimension / 2f
            val circleCenter = center

            val circlePath = Path().apply {
                addOval(Rect(circleCenter, radius))
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

        // Center circle — animated size, shows checkmark when selected
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
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
