package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

// ── Popup content — top-level, no scope inheritance ───────────────────────────
@Composable
private fun <T> DropdownPopupContent(
    entries: List<Pair<T, String>>,
    currentValue: T,
    resolvedStyle: TextStyle,
    onSelect: (T) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scaleY by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "dropdownScaleY",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "dropdownAlpha",
    )

    Surface(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 240.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), clip = false)
            // Scale vertically from the top edge — exactly how Android's
            // native PopupMenu animates. scaleY goes 0→1 so the menu
            // appears to unfold downward from the row.
            .graphicsLayer {
                this.scaleY = scaleY
                this.alpha = alpha
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(vertical = 4.dp, horizontal = 4.dp),
        ) {
            entries.forEach { (value, entryLabel) ->
                val isSelected = value == currentValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent,
                        )
                        .pointerInput(value) {
                            detectTapGestures { onSelect(value) }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = entryLabel,
                        style = resolvedStyle.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                            letterSpacing = MaterialTheme.typography.bodyLarge.letterSpacing,
                        ),
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ── Public preference row ─────────────────────────────────────────────────────
@Composable
fun <T> DropdownPreference(
    label: String,
    entries: List<Pair<T, String>>,
    currentValue: T,
    onValueChange: (T) -> Unit,
    description: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""
    var resolvedTitleStyle by remember { mutableStateOf<TextStyle?>(null) }

    // Absolute window coordinates of the row — updated on layout
    var rowTopLeft by remember { mutableStateOf(IntOffset.Zero) }
    var rowBottomLeft by remember { mutableStateOf(IntOffset.Zero) }
    var tapRelativeX by remember { mutableStateOf(0) }

    PreferenceTemplate(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                rowTopLeft = IntOffset(pos.x.toInt(), pos.y.toInt())
                rowBottomLeft = IntOffset(
                    x = pos.x.toInt(),
                    y = pos.y.toInt() + coords.size.height,
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    tapRelativeX = offset.x.toInt()
                    expanded = true
                }
            },
        title = {
            resolvedTitleStyle = LocalTextStyle.current
            Text(text = label)
        },
        description = { if (description != null) Text(text = description) },
        endWidget = {
            Text(
                text = currentLabel,
                color = MaterialTheme.colorScheme.primary,
                style = (resolvedTitleStyle ?: LocalTextStyle.current).copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                    letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing,
                ),
            )
        },
    )

    if (expanded) {
        val positionProvider = remember(rowTopLeft, rowBottomLeft, tapRelativeX) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val x = (rowBottomLeft.x + tapRelativeX)
                        .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    // Show below row if there's enough space, otherwise flip above
                    val yBelow = rowBottomLeft.y
                    val yAbove = rowTopLeft.y - popupContentSize.height
                    val y = if (yBelow + popupContentSize.height <= windowSize.height) {
                        yBelow
                    } else {
                        yAbove.coerceAtLeast(0)
                    }
                    return IntOffset(x, y)
                }
            }
        }

        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            DropdownPopupContent(
                entries = entries,
                currentValue = currentValue,
                resolvedStyle = resolvedTitleStyle ?: MaterialTheme.typography.bodyLarge,
                onSelect = { value ->
                    expanded = false
                    onValueChange(value)
                },
            )
        }
    }
}
