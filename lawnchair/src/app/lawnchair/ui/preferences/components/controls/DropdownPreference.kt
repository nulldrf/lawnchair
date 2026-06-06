package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

@Composable
fun <T> DropdownPreference(
    label: String,
    entries: List<Pair<T, String>>,
    currentValue: T,
    onValueChange: (T) -> Unit,
    description: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var tapOffsetDp by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""
    var resolvedTitleStyle by remember { mutableStateOf<TextStyle?>(null) }

    Box {
        PreferenceTemplate(
            modifier = Modifier
                .fillMaxWidth()
                // Peek at the down event position WITHOUT consuming it so the
                // clickable below still gets the event and shows the ripple.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tapOffsetDp = with(density) {
                            DpOffset(down.position.x.toDp(), 0.dp)
                        }
                    }
                }
                .clickable { expanded = true },
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = tapOffsetDp,
        ) {
            entries.forEach { (value, entryLabel) ->
                DropdownMenuItem(
                    text = { Text(text = entryLabel) },
                    onClick = {
                        expanded = false
                        onValueChange(value)
                    },
                    trailingIcon = if (value == currentValue) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}
