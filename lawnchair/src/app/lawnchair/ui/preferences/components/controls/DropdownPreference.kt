package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
    // Tap position in dp relative to the row — used to position the menu
    var tapOffsetDp by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""
    var resolvedTitleStyle by remember { mutableStateOf<TextStyle?>(null) }

    // The anchor Box wraps the entire row so DropdownMenu positions
    // relative to the row's top-left corner, then we shift by tap offset.
    Box {
        PreferenceTemplate(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        tapOffsetDp = with(density) {
                            DpOffset(offset.x.toDp(), 0.dp)
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

        // DropdownMenu anchors to the parent Box (the full row).
        // offset shifts it to exactly where the finger tapped.
        // containerColor = transparent + tonalElevation = 0 so our
        // custom Surface draws the rounded shape without interference.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = tapOffsetDp,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            // Custom Surface inside DropdownMenu for rounded shape + shadow.
            // DropdownMenu's built-in content area has no padding/clip of its
            // own when containerColor is transparent, so we control it fully.
            androidx.compose.material3.Surface(
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
                                    detectTapGestures {
                                        expanded = false
                                        onValueChange(value)
                                    }
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
                                // resolvedTitleStyle has GoogleSansFlex embedded —
                                // DropdownMenu shares the same composition tree as
                                // the parent so MaterialTheme.typography also works,
                                // but we use resolvedStyle to be explicit.
                                style = (resolvedTitleStyle ?: MaterialTheme.typography.bodyLarge)
                                    .copy(
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
}
