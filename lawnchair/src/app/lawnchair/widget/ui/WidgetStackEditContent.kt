package app.lawnchair.widget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import com.android.launcher3.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Pure stateless Jetpack Compose UI for creating or editing a widget stack.
 *
 * Uses Lawnchair's [ModalBottomSheetContent] with a Material 3 drag handle, a One UI-inspired
 * horizontal peeking carousel powered by `sh.calvin.reorderable`, a Material 3 [SwitchPreference]
 * for auto-rotate, and M3E expressive morphing buttons (`ButtonDefaults.shapes()`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetStackEditContent(
    isEditing: Boolean,
    spanX: Int,
    spanY: Int,
    members: List<WidgetStackMemberUi>,
    autoRotate: Boolean,
    onMoveMember: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveMember: (appWidgetId: Int) -> Unit,
    onAddWidgetClick: () -> Unit,
    onAutoRotateChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Guard against dragging onto or past the trailing non-reorderable "+" card.
        if (from.index in members.indices && to.index in members.indices) {
            onMoveMember(from.index, to.index)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetDefaults.DragHandle()

        ModalBottomSheetContent(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (isEditing) R.string.edit_stack else R.string.create_stack,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.widget_stack_cell_size, spanX, spanY),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            text = {
                Text(
                    text = stringResource(R.string.widget_stack_reorder_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            content = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        state = lazyListState,
                        contentPadding = PaddingValues(horizontal = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(
                            items = members,
                            key = { _, member -> member.appWidgetId },
                        ) { _, member ->
                            ReorderableItem(
                                state = reorderableState,
                                key = member.appWidgetId,
                            ) { isDragging ->
                                WidgetStackPreviewCard(
                                    member = member,
                                    canRemove = members.size > 1,
                                    isDragging = isDragging,
                                    cardWidth = 220.dp,
                                    cardHeight = 200.dp,
                                    onRemove = { onRemoveMember(member.appWidgetId) },
                                    modifier = Modifier.longPressDraggableHandle(),
                                )
                            }
                        }

                        item(key = ADD_CARD_KEY) {
                            WidgetStackAddCard(
                                cardWidth = 220.dp,
                                cardHeight = 200.dp,
                                onClick = onAddWidgetClick,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    PreferenceGroup {
                        SwitchPreference(
                            checked = autoRotate,
                            onCheckedChange = onAutoRotateChange,
                            label = stringResource(R.string.auto_rotate_widgets),
                            description = stringResource(R.string.auto_rotate_widgets_description),
                        )
                    }
                }
            },
            buttons = {
                OutlinedButton(
                    onClick = onCancel,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.requiredWidth(8.dp))
                Button(
                    onClick = onSave,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.widget_stack_save))
                }
            },
        )
    }
}

private const val ADD_CARD_KEY = "widget_stack_add_card"
