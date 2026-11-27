package app.lawnchair.override

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.apppairs.AppPairIconDrawingParams
import com.android.launcher3.apppairs.AppPairIconGraphic
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.model.data.AppPairInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.google.accompanist.drawablepainter.rememberDrawablePainter

/**
 * Displays a Material 3 Expressive bottom sheet to customize a home-screen or dock folder
 * (renaming the folder and toggling Cover Mode).
 */
fun showCustomizeFolderSheet(
    launcher: LawnchairLauncher,
    folderInfo: FolderInfo,
    folderIcon: FolderIcon,
) {
    ComposeBottomSheet.show(launcher) {
        val initialTitle = remember(folderInfo) {
            folderInfo.title?.toString().orEmpty()
        }
        var title by remember(folderInfo) {
            mutableStateOf(initialTitle)
        }
        var coverMode by remember(folderInfo) {
            mutableStateOf(folderInfo.isCoverMode())
        }
        val previewData = remember(folderInfo) {
            resolveFolderPreviewData(launcher, folderInfo)
        }

        CustomizeFolderContent(
            title = title,
            initialTitle = initialTitle,
            coverMode = coverMode,
            previewData = previewData,
            onTitleChange = { title = it },
            onCoverModeChange = { coverMode = it },
            onCancel = { close(true) },
            onSave = {
                val trimmedTitle = title.trim()
                if (trimmedTitle != initialTitle) {
                    folderInfo.setTitle(trimmedTitle, launcher.modelWriter)
                    folderIcon.onTitleChanged(folderInfo.title)
                    folderIcon.folder?.reapplyItemInfo()
                }
                if (folderInfo.isCoverMode() != coverMode) {
                    folderInfo.setCoverMode(coverMode, launcher.modelWriter)
                    folderIcon.onCoverModeChanged()
                }
                close(true)
            },
        )
    }
}

data class FolderPreviewData(
    val previewDrawables: List<Drawable>,
    val coverDrawable: Drawable?,
    val coverAppLabel: String?,
)

private fun resolveFolderPreviewData(
    context: Context,
    folderInfo: FolderInfo,
): FolderPreviewData {
    val prefs = PreferenceManager2.getInstance(context)
    val shouldTheme = prefs.themedIcons.firstCached()
    val flags = if (shouldTheme) BitmapInfo.FLAG_THEMED else 0

    val contents = folderInfo.getContents()
    val previewDrawables = contents.take(4).mapNotNull { item ->
        createItemPreviewDrawable(context, item, flags)
    }

    val coverItem = folderInfo.coverInfo as? ItemInfoWithIcon
    val coverDrawable = coverItem?.let { createItemPreviewDrawable(context, it, flags) }
    val coverAppLabel = coverItem?.title?.toString()?.takeIf { it.isNotBlank() }

    return FolderPreviewData(
        previewDrawables = previewDrawables,
        coverDrawable = coverDrawable,
        coverAppLabel = coverAppLabel,
    )
}

private fun createItemPreviewDrawable(
    context: Context,
    item: ItemInfo,
    creationFlags: Int,
): Drawable? = when (item) {
    is ItemInfoWithIcon -> item.newIcon(context, creationFlags)

    is AppPairInfo -> {
        val params = AppPairIconDrawingParams(context, BubbleTextView.DISPLAY_FOLDER)
        AppPairIconGraphic.composeDrawable(item, params)
    }

    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomizeFolderContent(
    title: String,
    initialTitle: String,
    coverMode: Boolean,
    previewData: FolderPreviewData,
    onTitleChange: (String) -> Unit,
    onCoverModeChange: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEnableCoverMode = previewData.coverDrawable != null
    val isCoverPreview = coverMode && canEnableCoverMode

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetDefaults.DragHandle()

        ModalBottomSheetContent(
            title = {
                Text(text = stringResource(R.string.customize_folder_title))
            },
            content = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    FolderPreviewCard(
                        title = title,
                        isCoverPreview = isCoverPreview,
                        previewData = previewData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = onTitleChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        trailingIcon = {
                            if (title != initialTitle) {
                                ClickableIcon(
                                    painter = painterResource(id = R.drawable.ic_undo),
                                    onClick = { onTitleChange(initialTitle) },
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = MaterialTheme.shapes.large,
                        label = { Text(text = stringResource(id = R.string.label)) },
                        placeholder = { Text(text = stringResource(id = R.string.folder_hint_text)) },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PreferenceGroup(
                        description = stringResource(R.string.folder_cover_mode_reorder_hint),
                        showDescription = isCoverPreview,
                    ) {
                        SwitchPreference(
                            checked = coverMode,
                            onCheckedChange = onCoverModeChange,
                            label = stringResource(R.string.folder_cover_mode_label),
                            description = stringResource(R.string.folder_cover_mode_description),
                            enabled = canEnableCoverMode,
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
                    Text(text = stringResource(R.string.apply_label))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderPreviewCard(
    title: String,
    isCoverPreview: Boolean,
    previewData: FolderPreviewData,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AnimatedContent(
                targetState = isCoverPreview,
                transitionSpec = {
                    (fadeIn(animationSpec = effectsSpec) + scaleIn(initialScale = 0.86f, animationSpec = spatialSpec))
                        .togetherWith(
                            fadeOut(animationSpec = effectsSpec) + scaleOut(targetScale = 0.86f, animationSpec = effectsSpec),
                        )
                },
                label = "folder_cover_preview",
            ) { showCover ->
                if (showCover && previewData.coverDrawable != null) {
                    Image(
                        painter = rememberDrawablePainter(drawable = previewData.coverDrawable),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                    )
                } else {
                    FolderGridPreviewIcon(
                        drawables = previewData.previewDrawables,
                    )
                }
            }

            val displayTitle = title.trim().ifEmpty {
                stringResource(R.string.folder_hint_text)
            }
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleSmall,
                color = if (title.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Surface(
                shape = CircleShape,
                color = if (isCoverPreview) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
                val hintText = if (isCoverPreview) {
                    val appLabel = previewData.coverAppLabel
                    if (appLabel != null) {
                        stringResource(R.string.folder_cover_mode_preview_hint, appLabel)
                    } else {
                        stringResource(R.string.folder_cover_mode_preview_hint_generic)
                    }
                } else {
                    stringResource(R.string.folder_normal_preview_hint)
                }
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCoverPreview) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderGridPreviewIcon(
    drawables: List<Drawable>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniPreviewIcon(drawable = drawables.getOrNull(0))
                MiniPreviewIcon(drawable = drawables.getOrNull(1))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniPreviewIcon(drawable = drawables.getOrNull(2))
                MiniPreviewIcon(drawable = drawables.getOrNull(3))
            }
        }
    }
}

@Composable
private fun MiniPreviewIcon(
    drawable: Drawable?,
    modifier: Modifier = Modifier,
) {
    if (drawable != null) {
        Image(
            painter = rememberDrawablePainter(drawable = drawable),
            contentDescription = null,
            modifier = modifier.size(20.dp),
        )
    } else {
        Spacer(modifier = modifier.size(20.dp))
    }
}
