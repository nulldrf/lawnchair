/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.DummyLauncherBox
import app.lawnchair.ui.preferences.components.DummyLauncherLayout
import app.lawnchair.ui.preferences.components.WallpaperPreview
import app.lawnchair.ui.preferences.components.WithWallpaper
import app.lawnchair.ui.preferences.components.clipToBottomPercentage
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.createPreviewIdp
import app.lawnchair.ui.preferences.components.invariantDeviceProfile
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceScrollState
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

@Composable
fun DockPreferences(modifier: Modifier = Modifier) {
    val prefs2 = preferenceManager2()

    val scrollState = rememberPreferenceScrollState()
    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val hotseatBgAdapter = prefs2.hotseatBG.getAdapter()

        MainSwitchPreference(adapter = prefs2.isHotseatEnabled.getAdapter(), label = stringResource(id = R.string.show_hotseat_title)) {
            DockPreferencesPreview()
            PreferenceGroup(heading = stringResource(id = R.string.style)) {
                ScrollAnchor(ScrollKeys.DOCK_BG, scrollState) {
                    SwitchPreference(
                        adapter = hotseatBgAdapter,
                        label = stringResource(id = R.string.hotseat_background),
                    )
                }
                ExpandAndShrink(
                    visible = hotseatBgAdapter.state.value,
                ) {
                    HotseatBackgroundSettings(prefs2, scrollState = scrollState)
                }
            }
            ScrollAnchor(ScrollKeys.DOCK_SEARCH, scrollState) {
                SearchBarPreference(SearchRoute.DOCK_SEARCH)
            }
            GridSettings(prefs2, scrollState)
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                val enableLabelInDockAdapter = prefs2.enableLabelInDock.getAdapter()
                ScrollAnchor(ScrollKeys.DOCK_SHOW_LABELS, scrollState) {
                    SwitchPreference(
                        adapter = enableLabelInDockAdapter,
                        label = stringResource(id = R.string.show_labels),
                    )
                }
                ExpandAndShrink(visible = enableLabelInDockAdapter.state.value) {
                    ScrollAnchor(ScrollKeys.DOCK_TWO_LINE, scrollState) {
                        SwitchPreference(
                            adapter = prefs2.twoLineDock.getAdapter(),
                            label = stringResource(id = R.string.dock_two_line_label),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HotseatBackgroundSettings(
    prefs2: PreferenceManager2,
    scrollState: PreferenceScrollState = rememberPreferenceScrollState(),
) {
    DividerColumn(thickness = 0.dp) {
        ScrollAnchor(ScrollKeys.DOCK_BG_COLOR, scrollState) {
            ColorPreference(preference = prefs2.hotseatBackgroundColor)
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_CORNER_RADIUS, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.hotseat_bg_corner_radius),
                adapter = prefs2.hotseatBackgroundCornerRadius.getAdapter(),
                step = 1f,
                valueRange = 0f..100f,
                showUnit = "dp",
                liveUpdate = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_OPACITY, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.hotseat_bg_alpha),
                adapter = prefs2.hotseatBGAlpha.getAdapter(),
                step = 5,
                valueRange = 5..100,
                showUnit = "%",
                liveUpdate = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_LEFT_MARGIN, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
                adapter = prefs2.hotseatBGHorizontalInsetLeft.getAdapter(),
                step = 5,
                valueRange = 0..100,
                showUnit = "px",
                liveUpdate = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_RIGHT_MARGIN, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
                adapter = prefs2.hotseatBGHorizontalInsetRight.getAdapter(),
                step = 5,
                valueRange = 0..100,
                showUnit = "px",
                liveUpdate = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_TOP_MARGIN, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
                adapter = prefs2.hotseatBGVerticalInsetTop.getAdapter(),
                step = 5,
                valueRange = 0..100,
                showUnit = "px",
                liveUpdate = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_BOTTOM_MARGIN, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
                adapter = prefs2.hotseatBGVerticalInsetBottom.getAdapter(),
                step = 5,
                valueRange = 0..100,
                showUnit = "px",
                liveUpdate = true,
            )
        }
    }
}

@Composable
fun GridSettings(prefs2: PreferenceManager2, scrollState: PreferenceScrollState) {
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
    val hotseatColumnsAdapter = prefs2.hotseatColumns.getAdapter()
    val hotseatColumnsUnfoldedAdapter = prefs2.hotseatColumnsUnfolded.getAdapter()
    val hotseatRowsAdapter = prefs2.hotseatRows.getAdapter()
    val dockPagesAdapter = prefs2.dockPages.getAdapter()

    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        if (isFoldable) {
            ScrollAnchor(ScrollKeys.DOCK_ICONS, scrollState) {
                SliderPreference(
                    label = stringResource(id = R.string.state_folded, stringResource(id = R.string.dock_icons)),
                    adapter = hotseatColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
            }
            SliderPreference(
                label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.dock_icons)),
                adapter = hotseatColumnsUnfoldedAdapter,
                step = 1,
                valueRange = 3..10,
            )
            ExpandAndShrink(
                visible = hotseatColumnsAdapter.state.value > hotseatColumnsUnfoldedAdapter.state.value,
            ) {
                WarningPreference(
                    text = stringResource(id = R.string.foldable_columns_error),
                )
            }
        } else {
            ScrollAnchor(ScrollKeys.DOCK_ICONS, scrollState) {
                SliderPreference(
                    label = stringResource(id = R.string.dock_icons),
                    adapter = hotseatColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
            }
        }
        ScrollAnchor(ScrollKeys.DOCK_BOTTOM_SPACE, scrollState) {
            SliderPreference(
                adapter = prefs2.hotseatBottomFactor.getAdapter(),
                label = stringResource(id = R.string.hotseat_bottom_space_label),
                valueRange = 0.0F..1.7F,
                step = 0.1F,
                showAsPercentage = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_PAGE_INDICATOR, scrollState) {
            SliderPreference(
                adapter = prefs2.pageIndicatorHeightFactor.getAdapter(),
                label = stringResource(id = R.string.page_indicator_height),
                valueRange = 0.0F..1.0F,
                step = 0.1F,
                showAsPercentage = true,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_ROWS, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.dock_rows),
                adapter = hotseatRowsAdapter,
                step = 1,
                valueRange = 1..2,
            )
        }
        ScrollAnchor(ScrollKeys.DOCK_PAGES, scrollState) {
            SliderPreference(
                label = stringResource(id = R.string.dock_pages),
                adapter = dockPagesAdapter,
                step = 1,
                valueRange = 1..5,
            )
        }
    }
}

@Composable
fun ColumnScope.DockPreferencesPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val idp = invariantDeviceProfile()
    // On phones, landscape switches to a vertical side hotseat with no QSB.
    // Force the portrait profile in landscape so the horizontal bottom dock,
    // multi-row layout, and QSB remain visible and editable.
    val forcePortrait = remember(idp, configuration) {
        isLandscape && idp.getDeviceProfile(context).isVerticalBarLayout()
    }

    val prefs2 = preferenceManager2()
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(28.dp)

    // Structural dock settings that affect DeviceProfile / Hotseat layout and require
    // rebuilding the preview IDP & renderer (swapped atomically without blanking).
    val hotseatMode by prefs2.hotseatMode.asState()
    val hotseatColumns by prefs2.hotseatColumns.asState()
    val hotseatColumnsUnfolded by prefs2.hotseatColumnsUnfolded.asState()
    val hotseatRows by prefs2.hotseatRows.asState()
    val dockPages by prefs2.dockPages.asState()
    val hotseatBottomFactor by prefs2.hotseatBottomFactor.asState()
    val pageIndicatorHeightFactor by prefs2.pageIndicatorHeightFactor.asState()
    val enableLabelInDock by prefs2.enableLabelInDock.asState()
    val twoLineDock by prefs2.twoLineDock.asState()

    val previewIdp = createPreviewIdp(
        updateGridOptions = {
            copy(numHotseatColumns = hotseatColumns)
        },
        keys = listOf(
            hotseatMode,
            hotseatColumns,
            hotseatColumnsUnfolded,
            hotseatRows,
            dockPages,
            hotseatBottomFactor,
            pageIndicatorHeightFactor,
            enableLabelInDock,
            twoLineDock,
        ),
    )

    // Background styling is reapplied directly on the live preview Hotseat without
    // tearing down or rebinding the preview renderer. QSB styling is already observed
    // reactively inside LawnQsbLayout via Compose state.
    val hotseatBG by prefs2.hotseatBG.asState()
    val hotseatBackgroundColor by prefs2.hotseatBackgroundColor.asState()
    val hotseatBackgroundCornerRadius by prefs2.hotseatBackgroundCornerRadius.asState()
    val hotseatBGAlpha by prefs2.hotseatBGAlpha.asState()
    val hotseatBGHorizontalInsetLeft by prefs2.hotseatBGHorizontalInsetLeft.asState()
    val hotseatBGVerticalInsetTop by prefs2.hotseatBGVerticalInsetTop.asState()
    val hotseatBGHorizontalInsetRight by prefs2.hotseatBGHorizontalInsetRight.asState()
    val hotseatBGVerticalInsetBottom by prefs2.hotseatBGVerticalInsetBottom.asState()

    val hotseatBackgroundKey = listOf(
        hotseatBG,
        hotseatBackgroundColor,
        hotseatBackgroundCornerRadius,
        hotseatBGAlpha,
        hotseatBGHorizontalInsetLeft,
        hotseatBGVerticalInsetTop,
        hotseatBGHorizontalInsetRight,
        hotseatBGVerticalInsetBottom,
    )

    PreferenceGroupHeading(heading = stringResource(id = R.string.preview_label))

    // Keep the wallpaper permission button outside the preview border.
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WithWallpaper { wallpaper ->
            val previewWidthFraction = if (isLandscape) 0.45f else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth(previewWidthFraction)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(width = 1.dp, color = primary.copy(alpha = 0.25f), shape = shape)
                    .clip(shape),
            ) {
                DummyLauncherBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBottomPercentage(if (hotseatRows >= 2) 0.4f else 0.3f),
                    forcePortrait = forcePortrait,
                ) {
                    WallpaperPreview(
                        wallpaper = wallpaper,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DummyLauncherLayout(
                        idp = previewIdp,
                        modifier = Modifier.fillMaxSize(),
                        forcePortrait = forcePortrait,
                        hotseatBackgroundKey = hotseatBackgroundKey,
                    )
                }
            }
        }
    }
}
