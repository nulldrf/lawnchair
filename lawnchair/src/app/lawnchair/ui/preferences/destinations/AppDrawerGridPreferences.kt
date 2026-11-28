package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.asPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.util.getAllAppsBackgroundColor
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

private const val MIN_DRAWER_GRID_SIZE = 3
private const val MAX_DRAWER_GRID_SIZE = 10

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDrawerGridPreferences(
    modifier: Modifier = Modifier,
) {
    val isExpandedScreen = LocalIsExpandedScreen.current
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    PreferenceLayout(
        label = stringResource(id = R.string.app_drawer_grid),
        modifier = modifier,
        isExpandedScreen = true,
    ) {
        val prefs = preferenceManager2()
        val columnsAdapter = prefs.drawerColumns.getAdapter()
        val columnsUnfoldedAdapter = prefs.drawerColumnsUnfolded.getAdapter()
        val rowsAdapter = prefs.drawerRows.getAdapter()
        val searchBarAtBottom = prefs.appDrawerSearchBarAtBottom.getAdapter().state.value
        val hideSearchBar = prefs.hideAppDrawerSearchBar.getAdapter().state.value
        val showLabels = prefs.showIconLabelsInDrawer.getAdapter().state.value
        val cellHeightFactor = prefs.drawerCellHeightFactor.getAdapter().state.value
        val horizontalPaddingFactor = prefs.drawerLeftRightMarginFactor.getAdapter().state.value
        val topPaddingFactor = prefs.drawerPaddingTopFactor.getAdapter().state.value
        val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

        val originalColumns = remember {
            columnsAdapter.state.value.coerceIn(MIN_DRAWER_GRID_SIZE, MAX_DRAWER_GRID_SIZE)
        }
        val originalRows = remember {
            rowsAdapter.state.value.coerceIn(MIN_DRAWER_GRID_SIZE, MAX_DRAWER_GRID_SIZE)
        }
        val originalColumnsUnfolded = remember {
            columnsUnfoldedAdapter.state.value
                .coerceIn(originalColumns, MAX_DRAWER_GRID_SIZE)
        }

        val columns = rememberSaveable { mutableIntStateOf(originalColumns) }
        val rows = rememberSaveable { mutableIntStateOf(originalRows) }
        val columnsUnfolded = rememberSaveable {
            mutableIntStateOf(originalColumnsUnfolded.coerceAtLeast(originalColumns))
        }

        LaunchedEffect(columns.intValue) {
            if (columnsUnfolded.intValue < columns.intValue) {
                columnsUnfolded.intValue = columns.intValue
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            AppDrawerGridMockup(
                isPortrait = isPortrait,
                columns = columns.intValue,
                rows = rows.intValue,
                searchBarAtBottom = searchBarAtBottom,
                hideSearchBar = hideSearchBar,
                showLabels = showLabels,
                cellHeightFactor = cellHeightFactor,
                horizontalPaddingFactor = horizontalPaddingFactor,
                topPaddingFactor = topPaddingFactor,
            )

            if (isFoldable) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (isExpandedScreen) Int.MAX_VALUE else 1,
                ) {
                    PreferenceGroup(heading = stringResource(id = R.string.when_folded_label)) {
                        SliderPreference(
                            label = stringResource(id = R.string.columns),
                            adapter = columns.asPreferenceAdapter(),
                            step = 1,
                            valueRange = MIN_DRAWER_GRID_SIZE..MAX_DRAWER_GRID_SIZE,
                            liveUpdate = true,
                        )
                        SliderPreference(
                            label = stringResource(id = R.string.rows),
                            adapter = rows.asPreferenceAdapter(),
                            step = 1,
                            valueRange = MIN_DRAWER_GRID_SIZE..MAX_DRAWER_GRID_SIZE,
                            liveUpdate = true,
                        )
                    }

                    PreferenceGroup(
                        heading = stringResource(id = R.string.when_unfolded_label),
                    ) {
                        SliderPreference(
                            label = stringResource(id = R.string.columns),
                            adapter = columnsUnfolded.asPreferenceAdapter(),
                            step = 1,
                            valueRange = columns.intValue..MAX_DRAWER_GRID_SIZE,
                            liveUpdate = true,
                        )
                        DrawerExpandedGridSummary(
                            columns = columnsUnfolded.intValue,
                            rows = rows.intValue,
                        )
                    }
                }
            } else {
                PreferenceGroup {
                    SliderPreference(
                        label = stringResource(id = R.string.columns),
                        adapter = columns.asPreferenceAdapter(),
                        step = 1,
                        valueRange = MIN_DRAWER_GRID_SIZE..MAX_DRAWER_GRID_SIZE,
                        liveUpdate = true,
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.rows),
                        adapter = rows.asPreferenceAdapter(),
                        step = 1,
                        valueRange = MIN_DRAWER_GRID_SIZE..MAX_DRAWER_GRID_SIZE,
                        liveUpdate = true,
                    )
                }
            }

            val navController = LocalNavController.current
            val context = LocalContext.current
            val applyOverrides = {
                prefs.batchEdit {
                    this[prefs.drawerColumns.key] = columns.intValue
                    this[prefs.drawerRows.key] = rows.intValue
                    if (isFoldable) {
                        this[prefs.drawerColumnsUnfolded.key] = columnsUnfolded.intValue
                    }
                }
                LauncherAppState.getIDP(context).onPreferencesChanged(context)
                navController.popBackStack()
            }

            val isChanged = columns.intValue != originalColumns ||
                rows.intValue != originalRows ||
                (isFoldable && columnsUnfolded.intValue != originalColumnsUnfolded)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Button(
                    onClick = { applyOverrides() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(),
                    enabled = isChanged,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(id = R.string.action_apply))
                }
            }
        }
    }
}

@Composable
private fun AppDrawerGridMockup(
    isPortrait: Boolean,
    columns: Int,
    rows: Int,
    searchBarAtBottom: Boolean,
    hideSearchBar: Boolean,
    showLabels: Boolean,
    cellHeightFactor: Float,
    horizontalPaddingFactor: Float,
    topPaddingFactor: Float,
) {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val phoneShape = RoundedCornerShape(28.dp)
    val borderColor = primary.copy(alpha = 0.25f)
    val widthFraction = if (isPortrait) 0.65f else 0.45f
    val drawerScrimColor = remember(context) {
        val defaultColor = ColorTokens.AllAppsScrimColor.resolveColor(context)
        Color(getAllAppsBackgroundColor(context, defaultColor))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isPortrait) 16.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(widthFraction),
        ) {
            WithWallpaper(displayWallpaperButton = false) { wallpaper ->
                DummyLauncherBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = borderColor, shape = phoneShape)
                        .clip(phoneShape),
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(drawerScrimColor),
                    ) {
                        val sidePadding = (8f + horizontalPaddingFactor.coerceIn(0f, 1.5f) * 14f).dp
                        val topInset = (14f + (topPaddingFactor.coerceIn(1f, 2f) - 1f) * 18f).dp
                        val effectiveHeightFraction = cellHeightFactor.coerceIn(0.3f, 1f)

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = sidePadding,
                                    end = sidePadding,
                                    top = topInset,
                                    bottom = 12.dp,
                                ),
                        ) {
                            if (!hideSearchBar && !searchBarAtBottom) {
                                PreviewSearchPill(surfaceVariant = surfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                val slotW = maxWidth / columns
                                val maxSlotH = maxHeight / rows
                                val slotH = maxSlotH * effectiveHeightFraction
                                val labelReserve = if (showLabels && slotH > 22.dp) 6.dp else 0.dp
                                val iconSize = minOf(
                                    slotW * 0.66f,
                                    (slotH - labelReserve) * 0.78f,
                                ).coerceIn(6.dp, 26.dp)
                                val iconCorner = (iconSize * 0.3f).coerceIn(2.dp, 8.dp)

                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Top,
                                ) {
                                    repeat(rows) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(slotH),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            repeat(columns) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center,
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(iconSize)
                                                            .background(
                                                                primary.copy(alpha = 0.72f),
                                                                RoundedCornerShape(iconCorner),
                                                            ),
                                                    )
                                                    if (labelReserve > 0.dp) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .width((iconSize * 0.9f).coerceAtLeast(6.dp))
                                                                .height(2.dp)
                                                                .background(
                                                                    onSurface.copy(alpha = 0.45f),
                                                                    RoundedCornerShape(1.dp),
                                                                ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            PreviewPageIndicatorDots(primary = primary)

                            if (!hideSearchBar && searchBarAtBottom) {
                                Spacer(modifier = Modifier.height(8.dp))
                                PreviewSearchPill(surfaceVariant = surfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSearchPill(surfaceVariant: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .background(
                surfaceVariant.copy(alpha = 0.85f),
                RoundedCornerShape(10.dp),
            ),
    )
}

@Composable
private fun PreviewPageIndicatorDots(primary: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(12.dp)
                .height(4.dp)
                .background(primary, RoundedCornerShape(2.dp)),
        )
        repeat(2) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(primary.copy(alpha = 0.45f), CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DrawerExpandedGridSummary(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    PreferenceTemplate(
        title = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.grid),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                    LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                ) {
                    Text(
                        text = stringResource(id = R.string.x_by_y, columns, rows),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = modifier,
        enabled = false,
    )
}
