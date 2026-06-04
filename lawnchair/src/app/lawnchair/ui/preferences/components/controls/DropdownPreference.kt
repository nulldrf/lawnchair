package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
    tapFractionX: Float = 0.5f,
    onSelect: (T) -> Unit,
) {
    var animVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animVisible = true }

    AnimatedVisibility(
        visible = animVisible,
        enter = fadeIn(tween(100)) + scaleIn(
            animationSpec = tween(150),
            transformOrigin = TransformOrigin(tapFractionX, 0f),
            initialScale = 0.92f,
        ),
        exit = fadeOut(tween(80)) + scaleOut(
            animationSpec = tween(120),
            transformOrigin = TransformOrigin(tapFractionX, 0f),
            targetScale = 0.92f,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 160.dp, max = 240.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), clip = false),
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

    // Absolute window coordinates of the row bottom-left, updated on layout
    var rowBottomLeft by remember { mutableStateOf(IntOffset.Zero) }
    var rowWidth by remember { mutableStateOf(1) }
    var tapRelativeX by remember { mutableStateOf(0) }

    PreferenceTemplate(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                rowBottomLeft = IntOffset(
                    x = pos.x.toInt(),
                    y = pos.y.toInt() + coords.size.height,
                )
                rowWidth = coords.size.width.coerceAtLeast(1)
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
        // PopupPositionProvider gives full control over where the popup
        // window is placed in absolute screen coordinates.
        // We ignore the anchor bounds (those are the parent View bounds)
        // and instead place directly at the row's bottom edge + tap X.
        val positionProvider = remember(rowBottomLeft, tapRelativeX) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val x = (rowBottomLeft.x + tapRelativeX)
                        .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    val y = rowBottomLeft.y
                        .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
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
                tapFractionX = (tapRelativeX.toFloat() / rowWidth).coerceIn(0f, 1f),
                onSelect = { value ->
                    expanded = false
                    onValueChange(value)
                },
            )
        }
    }
}
