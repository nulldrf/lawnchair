package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.util.OnResult
import app.lawnchair.util.requireSystemService
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SelectIconPreference(componentKey: ComponentKey) {
    val context = LocalContext.current
    val label = remember(componentKey) {
        val launcherApps: LauncherApps = context.requireSystemService()
        val intent = Intent().setComponent(componentKey.componentName)
        val activity = launcherApps.resolveActivity(intent, componentKey.user)
        activity.label.toString()
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model
    val iconPackProvider = remember { IconPackProvider.INSTANCE.get(context) }

    val repo = IconOverrideRepository.INSTANCE.get(context)

    // Tracks which strip item the user has highlighted (not yet committed)
    var pendingStripItem by remember { mutableStateOf<IconPickerItem?>(null) }

    OnResult<IconPickerItem> { item ->
        scope.launch {
            repo.setOverride(componentKey, item)
            (context as Activity).let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
                model.forceReload()
            }
        }
    }

    // Commit the pending strip selection on back — we wrap the back logic by
    // applying the pending item before the column's own back handling fires.
    // The simplest approach: a dedicated "Apply" surface at the top only when
    // a strip item is pending, consistent with Lawnchair's existing patterns.
    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle(initialValue = null)
    val hasOverride = overrideItem != null

    PreferenceLayoutLazyColumn(label = label) {

        // ── Reset to default (only when an override exists) ──────────────────
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = true) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = {
                        scope.launch {
                            repo.deleteOverride(componentKey)
                            (context as Activity).let {
                                it.setResult(Activity.RESULT_OK)
                                it.finish()
                                model.onAppIconChanged(
                                    componentKey.componentName.packageName,
                                    componentKey.user,
                                )
                                model.forceReload()
                            }
                        }
                    },
                )
            }
        }

        // ── Apply strip selection (only when user has tapped a strip icon) ───
        if (pendingStripItem != null) {
            preferenceGroupItems(1, isFirstChild = false) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_apply_icon),
                    onClick = {
                        pendingStripItem?.let { item ->
                            scope.launch {
                                repo.setOverride(componentKey, item)
                                (context as Activity).let {
                                    it.setResult(Activity.RESULT_OK)
                                    it.finish()
                                    model.onAppIconChanged(
                                        componentKey.componentName.packageName,
                                        componentKey.user,
                                    )
                                    model.forceReload()
                                }
                            }
                        }
                    },
                )
            }
        }

        // ── Quick-pick strip ─────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.pick_icon_quick_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(iconPacks) { iconPack ->
                        val iconEntry = remember(iconPack, componentKey) {
                            if (iconPack.packageName.isEmpty()) null
                            else IconEntry(
                                packPackageName = iconPack.packageName,
                                name = componentKey.componentName.className
                                    .substringAfterLast('.'),
                                type = IconType.Normal,
                            )
                        }

                        // Resolve the icon for this app from this pack
                        val packDrawable by produceState<Drawable?>(
                            initialValue = null,
                            iconPack,
                            componentKey,
                        ) {
                            launch(Dispatchers.IO) {
                                value = if (iconPack.packageName.isEmpty()) {
                                    // System icons: use the app's actual adaptive icon
                                    context.packageManager.getApplicationIcon(
                                        componentKey.componentName.packageName,
                                    )
                                } else {
                                    val pack = iconPackProvider.getIconPack(iconPack.packageName)
                                    pack?.loadBlocking()
                                    val component = android.content.ComponentName(
                                        componentKey.componentName.packageName,
                                        componentKey.componentName.className,
                                    )
                                    val entry = pack?.getIcon(component)
                                    if (entry != null) {
                                        pack.getIcon(entry, 0)
                                    } else {
                                        // Pack has no mapping for this app — show system icon
                                        context.packageManager.getApplicationIcon(
                                            componentKey.componentName.packageName,
                                        )
                                    }
                                }
                            }
                        }

                        val isSelected = pendingStripItem?.packPackageName == iconPack.packageName

                        QuickPickStripItem(
                            label = iconPack.name,
                            drawable = packDrawable,
                            isSelected = isSelected,
                            onClick = {
                                if (iconEntry != null) {
                                    pendingStripItem = IconPickerItem(
                                        packPackageName = iconPack.packageName,
                                        drawableName = iconEntry.name,
                                        label = iconPack.name,
                                        type = IconType.Normal,
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Full pack list ───────────────────────────────────────────────────
        preferenceGroupItems(
            items = iconPacks,
            isFirstChild = false,
            heading = { stringResource(id = R.string.pick_icon_from_label) },
        ) { _, iconPack ->
            AppItem(
                label = iconPack.name,
                icon = remember(iconPack) { iconPack.icon.toBitmap() },
                onClick = {
                    if (iconPack.packageName.isEmpty()) {
                        navController.navigate(IconPicker())
                    } else {
                        navController.navigate(IconPicker(iconPack.packageName))
                    }
                },
            )
        }
    }
}

@Composable
private fun QuickPickStripItem(
    label: String,
    drawable: Drawable?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = shape,
            border = if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .clickable(onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = rememberDrawablePainter(drawable),
                    contentDescription = label,
                    modifier = Modifier
                        .padding(6.dp)
                        .aspectRatio(1f),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
