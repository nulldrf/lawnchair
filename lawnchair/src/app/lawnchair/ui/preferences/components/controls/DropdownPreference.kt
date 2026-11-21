package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@Composable
fun <T> DropdownPreference(
    label: String,
    entries: List<Pair<T, String>>,
    currentValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val context = LocalContext.current
    val msdlPlayer = remember(context) { MSDLPlayerWrapper.INSTANCE.get(context) }
    var expanded by remember { mutableStateOf(false) }
    var anchorOffset by remember { mutableStateOf(IntOffset.Zero) }
    val currentLabel = entries.firstOrNull { it.first == currentValue }?.second ?: ""
    var resolvedTitleStyle by remember { mutableStateOf<TextStyle?>(null) }

    Box(modifier = modifier.trackPressPosition { anchorOffset = it.round() }) {
        PreferenceTemplate(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    msdlPlayer.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                    expanded = true
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

        OffsetAnchoredExpressiveMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorOffset = anchorOffset,
        ) {
            entries.forEachIndexed { index, (value, entryLabel) ->
                SelectableDropdownMenuItem(
                    selected = value == currentValue,
                    onClick = {
                        msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        expanded = false
                        onValueChange(value)
                    },
                    text = { Text(text = entryLabel) },
                    shapes = MenuDefaults.itemShape(index, entries.size),
                    colors = MenuDefaults.selectableItemColors(),
                    selectedLeadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun OffsetAnchoredExpressiveMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorOffset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Place a zero-height anchor at the tap coordinates so the M3E popup
    // opens from and closes back into the exact press position.
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            layout(width, 0) {
                placeable.place(anchorOffset.x, anchorOffset.y)
            }
        },
    ) {
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                containerColor = MenuDefaults.groupStandardContainerColor,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                content = content,
            )
        }
    }
}

private fun Modifier.trackPressPosition(onPress: (Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPress(down.position)
    }
}
