package app.lawnchair.ui.preferences.components.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A preference row showing [label] + current value on the right.
 * Tapping opens an animated popup anchored to the right-side value text.
 *
 * Popup style:
 *  - Rounded rectangle container (16dp) with scale+fade animation
 *  - Selected option: full-width filled primaryContainer (12dp corners) + checkmark
 *  - Unselected option: transparent, plain text, same width as selected
 */
@Composable
fun <T> DropdownPreference(
    label: String,
    entries: List<Pair<T, String>>,
    currentValue: T,
    onValueChange: (T) -> Unit,
    description: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var animatedExpanded by remember { mutableStateOf(false) }
    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = true
                animatedExpanded = true
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Left: label + optional description ───────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // ── Right: value text — popup anchors to this Box ─────────────────────
        Box(contentAlignment = Alignment.TopEnd) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (animatedExpanded) {
                Popup(
                    alignment = Alignment.TopEnd,
                    onDismissRequest = {
                        expanded = false
                        animatedExpanded = false
                    },
                    properties = PopupProperties(focusable = true),
                ) {
                    // AnimatedVisibility is outside any Row/Column scope here,
                    // so the plain top-level overload is used — no RowScope clash.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expanded,
                        enter = scaleIn(
                            animationSpec = tween(durationMillis = 180),
                            transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0f),
                            initialScale = 0.85f,
                        ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                        exit = scaleOut(
                            animationSpec = tween(durationMillis = 140),
                            transformOrigin = TransformOrigin(pivotFractionX = 1f, pivotFractionY = 0f),
                            targetScale = 0.85f,
                        ) + fadeOut(animationSpec = tween(durationMillis = 120)),
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 160.dp, max = 240.dp)
                                .shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    clip = false,
                                ),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 3.dp,
                        ) {
                            // IntrinsicSize.Max makes every item Row stretch to
                            // the width of the widest entry so the selected pill
                            // always fills the full popup width.
                            Column(
                                modifier = Modifier
                                    .width(IntrinsicSize.Max)
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                            ) {
                                entries.forEach { (value, entryLabel) ->
                                    val isSelected = value == currentValue
                                    val pillBackground = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    }
                                    val textColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(pillBackground)
                                            .clickable {
                                                expanded = false
                                                onValueChange(value)
                                            }
                                            .padding(
                                                horizontal = 16.dp,
                                                vertical = 12.dp,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Fixed-size slot keeps labels aligned
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = entryLabel,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) {
                                                FontWeight.Medium
                                            } else {
                                                FontWeight.Normal
                                            },
                                            color = textColor,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
