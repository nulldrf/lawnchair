package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.data.iconoverride.IconOverride
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.icons.iconpack.IconPackProvider
import app.lawnchair.icons.picker.IconEntry
import app.lawnchair.icons.picker.IconPickerItem
import app.lawnchair.icons.picker.IconType
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.destinations.IconPackInfo
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
import kotlinx.coroutines.withContext

private const val TAG = "SelectIconPreference"

@Composable
fun SelectIconPreference(componentKey: ComponentKey) {
    val context = LocalContext.current
    val label = remember(componentKey) {
        resolveAppLabel(context.requireSystemService(), componentKey)
    }
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val launcherAppState = LauncherAppState.getInstance(context)
    val model = launcherAppState.model
    val iconPackProvider = remember { IconPackProvider.INSTANCE.get(context) }

    val repo = IconOverrideRepository.INSTANCE.get(context)
    val overrideItem by repo.observeTarget(componentKey).collectAsStateWithLifecycle<IconOverride?>(initialValue = null)
    val hasOverride = overrideItem != null

    // Apply icon immediately on tap — no pending/apply button needed
    fun applyItem(item: IconPickerItem) {
        scope.launch {
            repo.setOverride(componentKey, item)
            // Refresh package icons while the model is still loaded; forceReload alone can
            // skip PackageUpdatedTask and leave icon-cache entries that look "fresh".
            // onAppIconChanged is @WorkerThread (blocking ShortcutManager query).
            withContext(Dispatchers.IO) {
                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
            }
            (context as Activity).let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
            }
        }
    }

    fun resetOverride() {
        scope.launch {
            repo.deleteOverride(componentKey)
            // Refresh package icons while the model is still loaded; forceReload alone can
            // skip PackageUpdatedTask and leave icon-cache entries that look "fresh".
            // onAppIconChanged is @WorkerThread (blocking ShortcutManager query).
            withContext(Dispatchers.IO) {
                model.onAppIconChanged(componentKey.componentName.packageName, componentKey.user)
            }
            (context as Activity).let {
                it.setResult(Activity.RESULT_OK)
                it.finish()
            }
        }
    }

    OnResult<IconPickerItem> { item -> applyItem(item) }

    PreferenceLayoutLazyColumn(label = label) {

        // ── Quick-pick strip ─────────────────────────────────────────────────
        item {
            // Resolve all packs up front, filter to only those with a real mapping
            // (system pack always included, 3rd-party only if they have the icon)
            // Triple: (IconPackInfo, Drawable?, IconEntry?) — entry is null for system
            val resolvedStrip by produceState<List<Triple<IconPackInfo, Drawable?, IconEntry?>>>(
                initialValue = emptyList(),
                iconPacks,
                componentKey,
            ) {
                launch(Dispatchers.IO) {
                    val component = android.content.ComponentName(
                        componentKey.componentName.packageName,
                        componentKey.componentName.className,
                    )
                    val systemDrawable = context.packageManager
                        .getApplicationIcon(componentKey.componentName.packageName)

                    val result = mutableListOf<Triple<IconPackInfo, Drawable?, IconEntry?>>()

                    iconPacks.forEach { iconPack ->
                        if (iconPack.packageName.isEmpty()) {
                            // System icons — always first, always shown, never duplicated
                            result.add(0, Triple(iconPack, systemDrawable, null))
                        } else {
                            val pack = iconPackProvider.getIconPack(iconPack.packageName)
                            pack?.loadBlocking()
                            val entry = pack?.getIcon(component)
                            if (entry != null) {
                                // Pack has a real mapping — include with its actual IconEntry
                                result.add(Triple(iconPack, pack.getIcon(entry, 0), entry))
                            }
                            // No mapping — skip entirely, no fallback
                        }
                    }
                    value = result
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.pick_icon_quick_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 32.dp)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp),
                    ) {
                        // Current state — mirrors LawnchairIconProvider.resolveIconEntry():
                        // 1. Per-app override (IconOverrideRepository.overridesMap)
                        // 2. Active icon pack (prefs.iconPackPackage — the single selected pack)
                        // 3. System icon fallback
                        val prefs = remember {
                            app.lawnchair.preferences.PreferenceManager.getInstance(context)
                        }
                        val currentDrawable by produceState<Drawable?>(
                            initialValue = null,
                            componentKey,
                            overrideItem,
                        ) {
                            launch(Dispatchers.IO) {
                                val component = android.content.ComponentName(
                                    componentKey.componentName.packageName,
                                    componentKey.componentName.className,
                                )
                                val systemDrawable = context.packageManager
                                    .getApplicationIcon(componentKey.componentName.packageName)

                                value = try {
                                    // 1. Per-app override — same as overrideRepo.overridesMap lookup
                                    val override = overrideItem?.iconPickerItem
                                    if (override != null) {
                                        val entry = override.toIconEntry()
                                        if (entry.packPackageName.isNotEmpty()) {
                                            iconPackProvider
                                                .getIconPack(entry.packPackageName)
                                                ?.loadBlocking()
                                        }
                                        iconPackProvider.getDrawable(entry, 0, componentKey.user)
                                    } else {
                                        // 2. Active icon pack — exactly what LawnchairIconProvider
                                        //    does: read iconPackPackage pref, get that single pack
                                        val activePackName = prefs.iconPackPackage.get()
                                        val activePack = iconPackProvider
                                            .getIconPack(activePackName)
                                            ?.also { it.loadBlocking() }
                                        val entry = activePack?.getCalendar(component)
                                            ?: activePack?.getIcon(component)
                                        entry?.let { iconPackProvider.getDrawable(it, 0, componentKey.user) }
                                    }
                                } catch (_: Exception) {
                                    null
                                } ?: systemDrawable
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 8.dp)
                                .size(64.dp),
                        ) {
                            Image(
                                painter = rememberDrawablePainter(currentDrawable),
                                contentDescription = label,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .aspectRatio(1f),
                            )
                        }

                        // Thin vertical divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )

                        // Scrollable filtered pack variants
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(resolvedStrip) { (iconPack, drawable, entry) ->
                                QuickPickStripItem(
                                    drawable = drawable,
                                    contentDescription = iconPack.name,
                                    onClick = {
                                        applyItem(
                                            IconPickerItem(
                                                packPackageName = iconPack.packageName,
                                                // Use the actual drawable name from the pack's
                                                // appfilter mapping, not the class simple name
                                                drawableName = entry?.name
                                                    ?: componentKey.componentName.className
                                                        .substringAfterLast('.'),
                                                label = iconPack.name,
                                                type = entry?.type ?: IconType.Normal,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Reset to default ─────────────────────────────────────────────────
        if (hasOverride) {
            preferenceGroupItems(1, isFirstChild = false) {
                ClickablePreference(
                    label = stringResource(id = R.string.icon_picker_reset_to_default),
                    onClick = { resetOverride() },
                )
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
    drawable: Drawable?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(64.dp)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Image(
            painter = rememberDrawablePainter(drawable),
            contentDescription = contentDescription,
            modifier = Modifier
                .padding(4.dp)
                .aspectRatio(1f),
        )
    }
}

/**
 * Resolves a display label for [componentKey], including work/private profiles.
 *
 * [LauncherApps.resolveActivity] can return null or throw for non-current users; fall back to
 * [LauncherApps.getActivityList] and finally the activity class name.
 */
private fun resolveAppLabel(launcherApps: LauncherApps, componentKey: ComponentKey): String {
    val componentName = componentKey.componentName
    val user = componentKey.user
    try {
        val intent = Intent().setComponent(componentName)
        launcherApps.resolveActivity(intent, user)?.label?.toString()?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "resolveActivity failed for $componentKey", t)
    }
    try {
        val activities = launcherApps.getActivityList(componentName.packageName, user)
        activities
            .firstOrNull { it.componentName == componentName }
            ?.label
            ?.toString()
            ?.let { return it }
        activities
            .firstOrNull()
            ?.label
            ?.toString()
            ?.let { return it }
    } catch (t: Throwable) {
        Log.w(TAG, "getActivityList failed for $componentKey", t)
    }
    return componentName.shortClassName?.trimStart('.') ?: componentName.packageName
}
