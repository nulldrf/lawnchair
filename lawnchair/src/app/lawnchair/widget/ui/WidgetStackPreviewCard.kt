package app.lawnchair.widget.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.launcher3.R
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.widget.WidgetCell

/**
 * UI representation of a single widget member in the widget stack editor.
 */
data class WidgetStackMemberUi(
    val appWidgetId: Int,
    val label: String,
    val widgetItem: WidgetItem?,
)

/**
 * Renders a single widget preview card inside the One UI-style horizontal stack strip.
 */
@Composable
fun WidgetStackPreviewCard(
    member: WidgetStackMemberUi,
    canRemove: Boolean,
    isDragging: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(24.dp),
        color = if (isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = if (isDragging) 8.dp else 2.dp,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        border = if (isDragging) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (member.widgetItem != null) {
                WidgetCellPreview(
                    widgetItem = member.widgetItem,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = member.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (canRemove) {
                FilledTonalIconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(34.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.remove_widget_from_stack),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Trailing "+" card that opens the widget picker to append another widget to the stack.
 */
@Composable
fun WidgetStackAddCard(
    cardWidth: Dp,
    cardHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_widget_to_stack),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.add_widget_to_stack),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WidgetCellPreview(
    widgetItem: WidgetItem,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                val cell = LayoutInflater.from(context)
                    .inflate(R.layout.widget_cell, this, false) as WidgetCell
                cell.isClickable = false
                cell.isLongClickable = false
                cell.isFocusable = false
                cell.applyFromCellItem(widgetItem)
                addView(
                    cell,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                tag = widgetItem
            }
        },
        update = { container ->
            if (container.tag != widgetItem) {
                val cell = container.getChildAt(0) as? WidgetCell
                cell?.applyFromCellItem(widgetItem)
                container.tag = widgetItem
            }
        },
    )
}
