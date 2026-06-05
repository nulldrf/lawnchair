package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        tapOffsetDp = with(density) {
                            DpOffset(offset.x.toDp(), offset.y.toDp())
                        }
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
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else null,
                )
            }
        }
    }
}
