package app.lawnchair.ui.preferences.components.colorpreference.pickers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.colorpreference.staticColors
import com.android.launcher3.R

/**
 * Custom page:
 *  - Top: static preset swatch grid (4 columns, split-circle style).
 *  - Bottom: a "Custom" text link that opens [CanvasColorPickerDialog].
 *
 * No HSB/RGB sliders. The [CanvasColorPickerDialog] handles all freeform
 * color picking.
 *
 * @param selectedColor  Currently applied/selected color as an ARGB int.
 * @param onSelect       Called whenever the user picks a new color, either
 *                       from the static grid or from the canvas dialog.
 */
@Composable
fun CustomColorPicker(
    selectedColor: Int,
    onSelect: (Int) -> Unit,
    simple: Boolean = false,
) {
    var showCanvasDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Static color swatch grid (4 columns, split-circle visual)
        SwatchGrid(
            entries = staticColors,
            simple = simple,
            onSwatchClick = { option ->
                when (option) {
                    is ColorOption.CustomColor -> onSelect(option.color)
                    else -> Unit
                }
            },
            isSwatchSelected = { option ->
                option is ColorOption.CustomColor && option.color == selectedColor
            },
        )

        // "Custom" label — tapping opens the canvas picker dialog
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { showCanvasDialog = true },
                )
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = R.string.custom),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showCanvasDialog) {
        CanvasColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showCanvasDialog = false },
            onColorSelected = { newColor ->
                onSelect(newColor)
            },
        )
    }
}
