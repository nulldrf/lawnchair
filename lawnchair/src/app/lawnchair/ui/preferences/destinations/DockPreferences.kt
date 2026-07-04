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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import app.lawnchair.ui.preferences.components.layout.PreferenceScrollState
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
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
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R

@Composable
fun DockPreferences(modifier: Modifier = Modifier) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val scrollState = rememberPreferenceScrollState()
    PreferenceLayout(
        label = stringResource(id = R.string.dock_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val hotseatBgAdapter = prefs.hotseatBG.getAdapter()

        ScrollAnchor(ScrollKeys.SHOW_DOCK, scrollState) {
        MainSwitchPreference(adapter = prefs2.isHotseatEnabled.getAdapter(), label = stringResource(id = R.string.show_hotseat_title)) {
            DockPreferencesPreview()
            PreferenceGroup(heading = stringResource(id = R.string.style)) {
                Item {
                    ScrollAnchor(ScrollKeys.DOCK_BG, scrollState) {
                    SwitchPreference(
                        adapter = hotseatBgAdapter,
                        label = stringResource(id = R.string.hotseat_background),
                    )
                    }
                }
                Item(
                    key = "feed_pref",
                    visible = hotseatBgAdapter.state.value,
                ) {
                    HotseatBackgroundSettings(prefs, prefs2, scrollState = scrollState)
                }
            }
            SearchBarPreference(SearchRoute.DOCK_SEARCH)
            GridSettings(prefs, prefs2, scrollState)
            PreferenceGroup(heading = stringResource(id = R.string.icons)) {
                val enableLabelInDockAdapter = prefs2.enableLabelInDock.getAdapter()
                Item {
                    ScrollAnchor(ScrollKeys.DOCK_SHOW_LABELS, scrollState) {
                    SwitchPreference(
                        adapter = enableLabelInDockAdapter,
                        label = stringResource(id = R.string.show_labels),
                    )
                    }
                }
                Item(
                    key = "dock_two_line_pref",
                    visible = enableLabelInDockAdapter.state.value,
                ) {
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
}

@Composable
fun HotseatBackgroundSettings(
    prefs: PreferenceManager,
    prefs2: PreferenceManager2,
    scrollState: PreferenceScrollState = rememberPreferenceScrollState(),
) {
    DividerColumn(thickness = 0.dp) {
        ScrollAnchor(ScrollKeys.DOCK_BG_COLOR, scrollState) {
            ColorPreference(preference = prefs2.hotseatBackgroundColor)
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_OPACITY, scrollState) {
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_alpha),
            adapter = prefs.hotseatBGAlpha.getAdapter(),
            step = 5,
            valueRange = 5..100,
            showUnit = "%",
        )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_LEFT_MARGIN, scrollState) {
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_left),
            adapter = prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_RIGHT_MARGIN, scrollState) {
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_horizontal_inset_right),
            adapter = prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_TOP_MARGIN, scrollState) {
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_top),
            adapter = prefs.hotseatBGVerticalInsetTop.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        }
        ScrollAnchor(ScrollKeys.DOCK_BG_BOTTOM_MARGIN, scrollState) {
        SliderPreference(
            label = stringResource(id = R.string.hotseat_bg_vertical_inset_bottom),
            adapter = prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            step = 5,
            valueRange = 0..100,
            showUnit = "px",
        )
        }
    }
}

@Composable
fun GridSettings(prefs: PreferenceManager, prefs2: PreferenceManager2, scrollState: app.lawnchair.ui.preferences.components.layout.PreferenceScrollState) {
    val isFoldable = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
    val hotseatColumnsAdapter = prefs.hotseatColumns.getAdapter()
    val hotseatColumnsUnfoldedAdapter = prefs.hotseatColumnsUnfolded.getAdapter()

    PreferenceGroup(heading = stringResource(id = R.string.grid)) {
        if (isFoldable) {
            Item {
                ScrollAnchor(ScrollKeys.DOCK_ICONS, scrollState) {
                SliderPreference(
                    label = stringResource(id = R.string.state_folded, stringResource(id = R.string.dock_icons)),
                    adapter = hotseatColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                }
            }
            Item {
                SliderPreference(
                    label = stringResource(id = R.string.state_unfolded, stringResource(id = R.string.dock_icons)),
                    adapter = hotseatColumnsUnfoldedAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
            }
            Item(
                visible = hotseatColumnsAdapter.state.value > hotseatColumnsUnfoldedAdapter.state.value,
            ) {
                WarningPreference(
                    text = stringResource(id = R.string.foldable_columns_error),
                )
            }
        } else {
            Item {
                ScrollAnchor(ScrollKeys.DOCK_ICONS, scrollState) {
                SliderPreference(
                    label = stringResource(id = R.string.dock_icons),
                    adapter = hotseatColumnsAdapter,
                    step = 1,
                    valueRange = 3..10,
                )
                }
            }
        }
        Item {
            ScrollAnchor(ScrollKeys.DOCK_BOTTOM_SPACE, scrollState) {
            SliderPreference(
                adapter = prefs2.hotseatBottomFactor.getAdapter(),
                label = stringResource(id = R.string.hotseat_bottom_space_label),
                valueRange = 0.0F..1.7F,
                step = 0.1F,
                showAsPercentage = true,
            )
            }
        }
        Item {
            ScrollAnchor(ScrollKeys.DOCK_PAGE_INDICATOR, scrollState) {
            SliderPreference(
                adapter = prefs2.pageIndicatorHeightFactor.getAdapter(),
                label = stringResource(id = R.string.page_indicator_height),
                valueRange = 0.0F..1.0F,
                step = 0.1F,
                showAsPercentage = true,
            )
            }
        }
    }
}

@Composable
fun ColumnScope.DockPreferencesPreview(modifier: Modifier = Modifier) {
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) {
        val prefs = preferenceManager()
        val prefs2 = preferenceManager2()
        val primary = MaterialTheme.colorScheme.primary
        val shape = RoundedCornerShape(28.dp)

        val adapters = listOf(
            prefs2.hotseatMode.getAdapter(),
            prefs.hotseatColumns.getAdapter(),
            prefs.hotseatColumnsUnfolded.getAdapter(),
            prefs2.themedHotseatQsb.getAdapter(),
            prefs.hotseatQsbCornerRadius.getAdapter(),
            prefs.hotseatQsbAlpha.getAdapter(),
            prefs.hotseatQsbStrokeWidth.getAdapter(),
            prefs2.hotseatBottomFactor.getAdapter(),
            prefs2.strokeColorStyle.getAdapter(),
            prefs2.enableLabelInDock.getAdapter(),
            prefs.hotseatBG.getAdapter(),
            prefs.hotseatBGHorizontalInsetLeft.getAdapter(),
            prefs.hotseatBGVerticalInsetTop.getAdapter(),
            prefs.hotseatBGHorizontalInsetRight.getAdapter(),
            prefs.hotseatBGVerticalInsetBottom.getAdapter(),
            prefs2.pageIndicatorHeightFactor.getAdapter(),
            prefs2.hotseatBackgroundColor.getAdapter(),
            prefs.hotseatBGAlpha.getAdapter(),
        )

        PreferenceGroupHeading(heading = stringResource(id = R.string.preview_label))

        // WithWallpaper wraps the bordered box so its permission button renders
        // AFTER the box as the next Column child — outside the border.
        // The bordered Box is the content lambda; the button is a sibling appended
        // by WithWallpaper itself after content() returns.
        Column(modifier = modifier.fillMaxWidth()) {
            WithWallpaper { wallpaper ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(width = 1.dp, color = primary.copy(alpha = 0.25f), shape = shape)
                        .clip(shape),
                ) {
                    DummyLauncherBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBottomPercentage(0.3f),
                    ) {
                        WallpaperPreview(
                            wallpaper = wallpaper,
                            modifier = Modifier.fillMaxSize(),
                        )
                        key(adapters.map { it.state.value }.toTypedArray()) {
                            DummyLauncherLayout(
                                idp = createPreviewIdp {
                                    copy(numHotseatColumns = prefs.hotseatColumns.get())
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                // WithWallpaper appends the "Show wallpaper" button here as the
                // next Column child — outside the bordered box.
            }
        }
    }
}