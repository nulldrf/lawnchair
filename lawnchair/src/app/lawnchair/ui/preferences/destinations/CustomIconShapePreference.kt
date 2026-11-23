package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.icons.shape.IconCornerShape
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.getSteps
import app.lawnchair.ui.preferences.components.controls.snapSliderValue
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.ConnectedButtonGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.copyToClipboard
import app.lawnchair.util.getClipboardContent
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlin.math.roundToInt

private enum class CornerTarget {
    ALL,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

@Composable
fun CustomIconShapePreference(
    modifier: Modifier = Modifier,
    currentTab: ShapeRoute = ShapeRoute.APP_SHAPE,
) {
    val context = LocalContext.current
    val preferenceManager2 = preferenceManager2()
    val msdlPlayer = remember(context) { MSDLPlayerWrapper.INSTANCE.get(context) }

    val customIconShapeAdapter = when (currentTab) {
        ShapeRoute.APP_SHAPE -> preferenceManager2.customIconShape.getAdapter()
        ShapeRoute.FOLDER_SHAPE -> preferenceManager2.customFolderShape.getAdapter()
    }

    val appliedIconShape = customIconShapeAdapter.state.value
    var selectedIconShape by remember(currentTab) {
        mutableStateOf(IconShape.CustomCornerBased(appliedIconShape ?: IconShape.Circle))
    }
    var selectedTarget by remember(currentTab) { mutableStateOf(CornerTarget.ALL) }

    val selectedIconShapeApplied by remember {
        derivedStateOf {
            customIconShapeAdapter.state.value.toString() == selectedIconShape.toString()
        }
    }

    val label = when (currentTab) {
        ShapeRoute.APP_SHAPE -> stringResource(id = R.string.custom_icon_shape)
        ShapeRoute.FOLDER_SHAPE -> stringResource(id = R.string.custom_folder_shape)
    }
    val importErrorMessage = stringResource(id = R.string.icon_shape_clipboard_import_error)

    PreferenceLayout(
        label = label,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Button(
                    enabled = !selectedIconShapeApplied,
                    onClick = {
                        msdlPlayer.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                        customIconShapeAdapter.onChange(newValue = selectedIconShape)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 16.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = if (appliedIconShape != null) {
                            stringResource(id = R.string.action_apply)
                        } else {
                            stringResource(id = R.string.action_create)
                        },
                    )
                }
                BottomSpacer()
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InteractiveShapePreviewCard(
                iconShape = selectedIconShape,
                selectedTarget = selectedTarget,
                onSelectTarget = { corner ->
                    msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                    selectedTarget = if (selectedTarget == corner) CornerTarget.ALL else corner
                },
                onCopy = {
                    msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                    copyToClipboard(context = context, text = selectedIconShape.toString())
                },
                onPaste = {
                    msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                    getClipboardContent(context)?.let {
                        IconShape.CustomCornerBased.fromStringOrNull(it)
                    }?.let {
                        selectedIconShape = it
                    } ?: run {
                        Toast.makeText(context, importErrorMessage, Toast.LENGTH_SHORT).show()
                    }
                },
            )

            CornerTargetSelectorRow(
                selectedTarget = selectedTarget,
                onSelectTarget = { target ->
                    if (selectedTarget != target) {
                        msdlPlayer.playToken(MSDLToken.TAP_LOW_EMPHASIS)
                        selectedTarget = target
                    }
                },
            )

            SharedCornerControlsCard(
                iconShape = selectedIconShape,
                selectedTarget = selectedTarget,
                onCornerStyleChange = { newCornerShape ->
                    selectedIconShape = selectedIconShape.withCornerShape(selectedTarget, newCornerShape)
                },
                onCornerScaleChange = { newScale ->
                    selectedIconShape = selectedIconShape.withCornerScale(selectedTarget, newScale)
                },
            )
        }
    }
}

@Composable
private fun InteractiveShapePreviewCard(
    iconShape: IconShape.CustomCornerBased,
    selectedTarget: CornerTarget,
    onSelectTarget: (CornerTarget) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            // Active target pill badge (top-start)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    text = selectedTarget.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Compact Copy / Paste actions (top-end)
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(id = R.string.export_to_clipboard),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onPaste,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = stringResource(id = R.string.import_from_clipboard),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Large centered live preview with 4 tappable corner handles
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 34.dp, bottom = 8.dp)
                    .size(144.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconShapePreview(
                    iconShape = iconShape,
                    modifier = Modifier.size(116.dp),
                    strokeColor = MaterialTheme.colorScheme.primary,
                    fillColor = MaterialTheme.colorScheme.primaryContainer,
                )

                CornerHandleDot(
                    corner = CornerTarget.TOP_LEFT,
                    selectedTarget = selectedTarget,
                    onClick = { onSelectTarget(CornerTarget.TOP_LEFT) },
                    modifier = Modifier.align(Alignment.TopStart),
                )
                CornerHandleDot(
                    corner = CornerTarget.TOP_RIGHT,
                    selectedTarget = selectedTarget,
                    onClick = { onSelectTarget(CornerTarget.TOP_RIGHT) },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
                CornerHandleDot(
                    corner = CornerTarget.BOTTOM_LEFT,
                    selectedTarget = selectedTarget,
                    onClick = { onSelectTarget(CornerTarget.BOTTOM_LEFT) },
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                CornerHandleDot(
                    corner = CornerTarget.BOTTOM_RIGHT,
                    selectedTarget = selectedTarget,
                    onClick = { onSelectTarget(CornerTarget.BOTTOM_RIGHT) },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun CornerHandleDot(
    corner: CornerTarget,
    selectedTarget: CornerTarget,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDirectlySelected = selectedTarget == corner
    val isActive = selectedTarget == CornerTarget.ALL || isDirectlySelected
    val description = corner.label()

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.RadioButton
                selected = isDirectlySelected
            },
        contentAlignment = Alignment.Center,
    ) {
        RadioButton(
            selected = isActive,
            onClick = null,
            modifier = Modifier.scale(0.8f),
        )
    }
}

@Composable
private fun CornerTargetSelectorRow(
    selectedTarget: CornerTarget,
    onSelectTarget: (CornerTarget) -> Unit,
) {
    val targets = listOf(
        CornerTarget.ALL,
        CornerTarget.TOP_LEFT,
        CornerTarget.TOP_RIGHT,
        CornerTarget.BOTTOM_LEFT,
        CornerTarget.BOTTOM_RIGHT,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        targets.forEach { target ->
            val isSelected = selectedTarget == target
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                label = "chip_bg",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "chip_fg",
            )
            val description = target.label()

            Surface(
                onClick = { onSelectTarget(target) },
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .weight(if (target == CornerTarget.ALL) 1.35f else 1f)
                    .height(40.dp)
                    .semantics {
                        contentDescription = description
                        role = Role.RadioButton
                        selected = isSelected
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (target == CornerTarget.ALL) {
                        Text(
                            text = stringResource(id = R.string.custom_icon_shape_all),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    } else {
                        CornerBracketIcon(
                            corner = target,
                            color = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CornerBracketIcon(
    corner: CornerTarget,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.2.dp.toPx()
        val inset = strokeWidth / 2f
        val span = size.minDimension - strokeWidth
        val arcDiameter = span * 1.15f

        val startAngle = when (corner) {
            CornerTarget.TOP_LEFT -> 180f
            CornerTarget.TOP_RIGHT -> 270f
            CornerTarget.BOTTOM_RIGHT -> 0f
            CornerTarget.BOTTOM_LEFT -> 90f
            CornerTarget.ALL -> 0f
        }
        val topLeft = when (corner) {
            CornerTarget.TOP_LEFT -> Offset(inset, inset)
            CornerTarget.TOP_RIGHT -> Offset(size.width - inset - arcDiameter, inset)
            CornerTarget.BOTTOM_RIGHT -> Offset(size.width - inset - arcDiameter, size.height - inset - arcDiameter)
            CornerTarget.BOTTOM_LEFT -> Offset(inset, size.height - inset - arcDiameter)
            CornerTarget.ALL -> Offset.Zero
        }

        // Subtle square reference frame
        drawRect(
            color = color.copy(alpha = 0.22f),
            topLeft = Offset(inset, inset),
            size = Size(span, span),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        // Highlighted active corner arc
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcDiameter, arcDiameter),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedCornerControlsCard(
    iconShape: IconShape.CustomCornerBased,
    selectedTarget: CornerTarget,
    onCornerStyleChange: (IconCornerShape) -> Unit,
    onCornerScaleChange: (Float) -> Unit,
) {
    val context = LocalContext.current
    val msdlPlayer = remember(context) { MSDLPlayerWrapper.INSTANCE.get(context) }

    val styleOptions = remember {
        listOf<IconCornerShape>(
            IconCornerShape.arc,
            IconCornerShape.Squircle,
            IconCornerShape.Cut,
        )
    }
    val styleLabels = listOf(
        stringResource(id = R.string.custom_icon_shape_corner_round),
        stringResource(id = R.string.custom_icon_shape_corner_squircle),
        stringResource(id = R.string.custom_icon_shape_corner_cut),
    )

    val activeCornerShape = iconShape.resolvedCornerShape(selectedTarget)
    val selectedStyleIndex = activeCornerShape?.let { active ->
        styleOptions.indexOfFirst { it.isSameCornerStyle(active) }
    } ?: -1

    val step = 0.1f
    val valueRange = 0f..1f
    val activeScale = iconShape.resolvedCornerScale(selectedTarget)
    val isMixedScale = selectedTarget == CornerTarget.ALL && !iconShape.hasUniformScale()
    val scaleReadout = if (isMixedScale) {
        stringResource(id = R.string.custom_icon_shape_mixed)
    } else {
        stringResource(
            id = R.string.n_percent,
            (snapSliderValue(valueRange.start, activeScale, step) * 100).roundToInt(),
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.custom_icon_shape_corner),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = activeCornerShape?.getLabel()
                            ?: stringResource(id = R.string.custom_icon_shape_mixed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ConnectedButtonGroup(
                    options = styleLabels,
                    selectedIndex = selectedStyleIndex,
                    onSelect = { index -> onCornerStyleChange(styleOptions[index]) },
                    minHeight = 44.dp,
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.custom_icon_shape_corner_roundness),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = scaleReadout,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Slider(
                    value = activeScale,
                    onValueChange = { newValue ->
                        msdlPlayer.playToken(MSDLToken.DRAG_INDICATOR_DISCRETE)
                        onCornerScaleChange(newValue)
                    },
                    valueRange = valueRange,
                    steps = getSteps(valueRange, step),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun IconCornerShape.isSameCornerStyle(other: IconCornerShape): Boolean = this === other || this.toString() == other.toString()

private fun IconShape.CustomCornerBased.resolvedCornerShape(target: CornerTarget): IconCornerShape? = when (target) {
    CornerTarget.TOP_LEFT -> topLeft.shape

    CornerTarget.TOP_RIGHT -> topRight.shape

    CornerTarget.BOTTOM_LEFT -> bottomLeft.shape

    CornerTarget.BOTTOM_RIGHT -> bottomRight.shape

    CornerTarget.ALL -> {
        val first = topLeft.shape
        val uniform = topRight.shape.isSameCornerStyle(first) &&
            bottomLeft.shape.isSameCornerStyle(first) &&
            bottomRight.shape.isSameCornerStyle(first)
        if (uniform) first else null
    }
}

private fun IconShape.CustomCornerBased.resolvedCornerScale(target: CornerTarget): Float = when (target) {
    CornerTarget.TOP_LEFT -> topLeft.scale.x
    CornerTarget.TOP_RIGHT -> topRight.scale.x
    CornerTarget.BOTTOM_LEFT -> bottomLeft.scale.x
    CornerTarget.BOTTOM_RIGHT -> bottomRight.scale.x
    CornerTarget.ALL -> topLeft.scale.x
}

private fun IconShape.CustomCornerBased.hasUniformScale(): Boolean {
    val base = (topLeft.scale.x * 100).roundToInt()
    return (topRight.scale.x * 100).roundToInt() == base &&
        (bottomLeft.scale.x * 100).roundToInt() == base &&
        (bottomRight.scale.x * 100).roundToInt() == base
}

private fun IconShape.CustomCornerBased.withCornerShape(
    target: CornerTarget,
    shape: IconCornerShape,
): IconShape.CustomCornerBased = when (target) {
    CornerTarget.ALL -> copy(
        topLeftShape = shape,
        topRightShape = shape,
        bottomLeftShape = shape,
        bottomRightShape = shape,
    )

    CornerTarget.TOP_LEFT -> copy(topLeftShape = shape)

    CornerTarget.TOP_RIGHT -> copy(topRightShape = shape)

    CornerTarget.BOTTOM_LEFT -> copy(bottomLeftShape = shape)

    CornerTarget.BOTTOM_RIGHT -> copy(bottomRightShape = shape)
}

private fun IconShape.CustomCornerBased.withCornerScale(
    target: CornerTarget,
    scale: Float,
): IconShape.CustomCornerBased = when (target) {
    CornerTarget.ALL -> copy(
        topLeftScale = scale,
        topRightScale = scale,
        bottomLeftScale = scale,
        bottomRightScale = scale,
    )

    CornerTarget.TOP_LEFT -> copy(topLeftScale = scale)

    CornerTarget.TOP_RIGHT -> copy(topRightScale = scale)

    CornerTarget.BOTTOM_LEFT -> copy(bottomLeftScale = scale)

    CornerTarget.BOTTOM_RIGHT -> copy(bottomRightScale = scale)
}

@Composable
private fun CornerTarget.label(): String = when (this) {
    CornerTarget.ALL -> stringResource(id = R.string.custom_icon_shape_all_corners)
    CornerTarget.TOP_LEFT -> stringResource(id = R.string.custom_icon_shape_top_left)
    CornerTarget.TOP_RIGHT -> stringResource(id = R.string.custom_icon_shape_top_right)
    CornerTarget.BOTTOM_LEFT -> stringResource(id = R.string.custom_icon_shape_bottom_left)
    CornerTarget.BOTTOM_RIGHT -> stringResource(id = R.string.custom_icon_shape_bottom_right)
}

@Composable
private fun IconCornerShape.getLabel() = when (this) {
    IconCornerShape.Squircle -> stringResource(id = R.string.custom_icon_shape_corner_squircle)
    IconCornerShape.Cut -> stringResource(id = R.string.custom_icon_shape_corner_cut)
    else -> stringResource(id = R.string.custom_icon_shape_corner_round)
}
