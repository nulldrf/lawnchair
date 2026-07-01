package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.asPreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.createPreviewIdp
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreenGridPreferences(
    modifier: Modifier = Modifier,
) {
    val isExpandedScreen = LocalIsExpandedScreen.current
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_grid),
        modifier = modifier,
        isExpandedScreen = true,
        // No outer scroll — the screen manages its own fixed-height preview
        // area plus an independently scrollable controls area below it.
        scrollState = null,
    ) {
        val prefs = preferenceManager()
        val columnsAdapter = prefs.workspaceColumns.getAdapter()
        val rowsAdapter = prefs.workspaceRows.getAdapter()
        val hotseatColumnsAdapter = prefs.hotseatColumns.getAdapter()
        val hotseatColumnsUnfoldedAdapter = prefs.hotseatColumnsUnfolded.getAdapter()
        val increaseMaxGridSize = prefs.workspaceIncreaseMaxGridSize.getAdapter()
        val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY

        val originalColumns = remember { columnsAdapter.state.value }
        val originalRows = remember { rowsAdapter.state.value }
        val originalHotseatColumns = remember { hotseatColumnsAdapter.state.value }
        val originalHotseatColumnsUnfolded = remember { hotseatColumnsUnfoldedAdapter.state.value }

        val columns = rememberSaveable { mutableIntStateOf(originalColumns) }
        val rows = rememberSaveable { mutableIntStateOf(originalRows) }
        val hotseatColumns = rememberSaveable { mutableIntStateOf(originalHotseatColumns) }
        val hotseatColumnsUnfolded = rememberSaveable {
            mutableIntStateOf(originalHotseatColumnsUnfolded.coerceAtLeast(originalHotseatColumns))
        }

        // Keep the unfolded dock count from ever dropping below the folded
        // count — unfolded is always a superset of the folded layout.
        LaunchedEffect(hotseatColumns.intValue) {
            if (hotseatColumnsUnfolded.intValue < hotseatColumns.intValue) {
                hotseatColumnsUnfolded.intValue = hotseatColumns.intValue
            }
        }

        val maxGridSize = if (increaseMaxGridSize.state.value) 20 else 10

        // NOTE: PreferenceLayout always renders inside StretchNestedScrollView,
        // which measures its Compose content with UNSPECIFIED (unbounded) height
        // regardless of the `scrollState` parameter passed in — PreferenceColumn
        // never actually applies verticalScroll() itself. That means
        // BoxWithConstraints + weight(1f) (upstream's split-height approach)
        // receives infinite maxHeight here and weight(1f) silently collapses to
        // zero, producing a blank screen below the mockup.
        //
        // Fix: drop the bounded-height split entirely and let the screen flow
        // naturally inside the outer NestedScrollView, same as every other
        // screen in this fork. The mockup gets a sensible fixed-fraction size
        // instead of a computed previewMaxHeight, and the controls + apply
        // button are just regular content — no inner weight()/heightIn(min=).
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Phone-frame mockup ───────────────────────────────────────────
            GridMockup(
                isPortrait = isPortrait,
                columns = columns.intValue,
                rows = rows.intValue,
                hotseatColumns = hotseatColumns.intValue,
            )

            if (isFoldable) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (isExpandedScreen) Int.MAX_VALUE else 1,
                ) {
                    PreferenceGroup(heading = stringResource(id = R.string.when_folded_label)) {
                        Item {
                            SliderPreference(
                                label = stringResource(id = R.string.columns),
                                adapter = columns.asPreferenceAdapter(),
                                step = 1,
                                valueRange = 3..maxGridSize,
                            )
                        }
                        Item {
                            SliderPreference(
                                label = stringResource(id = R.string.rows),
                                adapter = rows.asPreferenceAdapter(),
                                step = 1,
                                valueRange = 3..maxGridSize,
                            )
                        }
                        Item {
                            SliderPreference(
                                label = stringResource(id = R.string.dock_icons),
                                adapter = hotseatColumns.asPreferenceAdapter(),
                                step = 1,
                                valueRange = 3..maxGridSize,
                            )
                        }
                    }

                    PreferenceGroup(
                        heading = stringResource(id = R.string.when_unfolded_label),
                    ) {
                        Item {
                            SliderPreference(
                                label = stringResource(id = R.string.dock_icons),
                                adapter = hotseatColumnsUnfolded.asPreferenceAdapter(),
                                step = 1,
                                valueRange = hotseatColumns.intValue..maxGridSize,
                            )
                        }
                        Item {
                            FakeExpandedGridPreference(
                                columns = columns.intValue * 2,
                                rows = rows.intValue,
                                description = stringResource(id = R.string.unfolded_grid_description),
                            )
                        }
                    }
                }
            } else {
                PreferenceGroup {
                    Item {
                        SliderPreference(
                            label = stringResource(id = R.string.columns),
                            adapter = columns.asPreferenceAdapter(),
                            step = 1,
                            valueRange = 3..maxGridSize,
                        )
                    }
                    Item {
                        SliderPreference(
                            label = stringResource(id = R.string.rows),
                            adapter = rows.asPreferenceAdapter(),
                            step = 1,
                            valueRange = 3..maxGridSize,
                        )
                    }
                    Item {
                        SliderPreference(
                            label = stringResource(id = R.string.dock_icons),
                            adapter = hotseatColumns.asPreferenceAdapter(),
                            step = 1,
                            valueRange = 3..maxGridSize,
                        )
                    }
                }
            }

            val navController = LocalNavController.current
            val context = LocalContext.current
            val applyOverrides = {
                prefs.batchEdit {
                    columnsAdapter.onChange(columns.intValue)
                    rowsAdapter.onChange(rows.intValue)
                    hotseatColumnsAdapter.onChange(hotseatColumns.intValue)
                    hotseatColumnsUnfoldedAdapter.onChange(hotseatColumnsUnfolded.intValue)
                }
                LauncherAppState.getIDP(context).onPreferencesChanged(context)
                navController.popBackStack()
            }

            val isChanged = columns.intValue != originalColumns ||
                rows.intValue != originalRows ||
                hotseatColumns.intValue != originalHotseatColumns ||
                hotseatColumnsUnfolded.intValue != originalHotseatColumnsUnfolded

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

// ---------------------------------------------------------------------------
// Phone-frame mockup (bordered/clipped DummyLauncherBox pattern)
// ---------------------------------------------------------------------------

/**
 * Live grid preview using the same border+clip pattern as IconPackPreferences,
 * but height-capped at [previewMaxHeight] so it shares space fairly with the
 * controls area below it — important on foldables and landscape where vertical
 * space is scarce. Width-only constraint lets DummyLauncherBox's internal
 * aspectRatio() own the height (gap-free), then heightIn(max=...) caps the
 * result from above if the natural aspect-ratio height would be too tall.
 */
@Composable
private fun GridMockup(
    isPortrait: Boolean,
    columns: Int,
    rows: Int,
    hotseatColumns: Int,
) {
    val primary = MaterialTheme.colorScheme.primary
    val phoneShape = RoundedCornerShape(28.dp)
    val borderColor = primary.copy(alpha = 0.25f)
    // Fixed width fraction — same pattern as every other mockup in this fork.
    // No previewMaxHeight needed: DummyLauncherBox's internal aspectRatio()
    // derives height from width alone, so this stays gap-free without relying
    // on a bounded parent height (which StretchNestedScrollView never provides).
    val widthFraction = if (isPortrait) 0.65f else 0.45f

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
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // createPreviewIdp reflects current slider values live,
                    // including the dock column count added in this merge.
                    DummyLauncherLayout(
                        idp = createPreviewIdp {
                            copy(
                                numColumns = columns,
                                numRows = rows,
                                numHotseatColumns = hotseatColumns,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Foldable "when unfolded" fake preview row (unchanged from upstream)
// ---------------------------------------------------------------------------

@Composable
private fun FakeExpandedGridPreference(
    columns: Int,
    rows: Int,
    description: String,
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
        description = {
            Text(description)
        },
        modifier = modifier,
        applyPaddings = false,
        enabled = false,
    )
}
