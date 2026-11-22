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

import android.Manifest
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.firstCached
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.TonalSpot
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.ThemePreference
import app.lawnchair.ui.preferences.components.WallpaperAccessPermissionDialog
import app.lawnchair.ui.preferences.components.colorpreference.ColorContrastWarning
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.controls.DropdownPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.ScrollAnchor
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.rememberPreferenceScrollState
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.destinations.icons.ThemedIconsState
import app.lawnchair.ui.preferences.navigation.GeneralAdvancedIcons
import app.lawnchair.ui.preferences.navigation.GeneralColorStyle
import app.lawnchair.ui.preferences.navigation.GeneralIconPack
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import app.lawnchair.ui.preferences.navigation.GeneralThemedIcons
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.systemui.monet.SpecVersion
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeneralPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val liveInfoManager = liveInformationManager()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val themedIconsAdapter = prefs2.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs2.drawerThemedIcons.getAdapter()
    val iconShapeAdapter = prefs2.iconShape.getAdapter()

    val currentIconPackName = iconPacks
        .find { it.packageName == prefs2.iconPackPackage.firstCached() }
        ?.name
    val themedIconsState = ThemedIconsState.getForSettings(
        themedIcons = themedIconsAdapter.state.value,
        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
    )
    val themedIconsSubtitle = stringResource(id = themedIconsState.labelResourceId)
    val iconShapeSubtitle = iconShapeEntries(context)
        .firstOrNull { it.value == iconShapeAdapter.state.value }
        ?.label?.invoke()
        ?: stringResource(id = R.string.custom)
    val scrollState = rememberPreferenceScrollState()

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.general_label),
        modifier = modifier,
    ) {
        ScrollAnchor(ScrollKeys.THEME, scrollState) {
            ThemePreference()
        }

        PreferenceGroup {
            ScrollAnchor(ScrollKeys.HOME_ROTATION, scrollState) {
                SwitchPreference(
                    adapter = prefs2.allowRotation.getAdapter(),
                    label = stringResource(id = R.string.home_screen_rotation_label),
                    description = stringResource(id = R.string.home_screen_rotation_description),
                )
            }
        }

        PreferenceGroup(heading = stringResource(id = R.string.updater)) {
            if (BuildConfig.APPLICATION_ID.contains("nightly")) {
                ScrollAnchor(ScrollKeys.AUTO_UPDATER, scrollState) {
                    SwitchPreference(
                        adapter = prefs2.autoUpdaterNightly.getAdapter(),
                        label = stringResource(id = R.string.auto_updater_label),
                        description = stringResource(id = R.string.auto_updater_description),
                    )
                }
            }
            ScrollAnchor(ScrollKeys.LIVE_INFORMATION, scrollState) {
                SwitchPreference(
                    adapter = liveInfoManager.enabled.getAdapter(),
                    label = stringResource(id = R.string.live_information_label),
                    description = stringResource(id = R.string.live_information_description),
                )
            }
        }

        ExpandAndShrink(visible = prefs2.enableFontSelection.asState().value) {
            PreferenceGroup(heading = stringResource(id = R.string.font_label)) {
                ScrollAnchor(ScrollKeys.FONT_WORKSPACE, scrollState) {
                    FontPreference(
                        fontPref = prefs2.fontWorkspace,
                        label = stringResource(R.string.fontWorkspace),
                    )
                }
                ScrollAnchor(ScrollKeys.FONT_HEADING, scrollState) {
                    FontPreference(
                        fontPref = prefs2.fontHeading,
                        label = stringResource(R.string.fontHeading),
                    )
                }
                ScrollAnchor(ScrollKeys.FONT_HEADING_MEDIUM, scrollState) {
                    FontPreference(
                        fontPref = prefs2.fontHeadingMedium,
                        label = stringResource(R.string.fontHeadingMedium),
                    )
                }
                ScrollAnchor(ScrollKeys.FONT_BODY, scrollState) {
                    FontPreference(
                        fontPref = prefs2.fontBody,
                        label = stringResource(R.string.fontBody),
                    )
                }
                ScrollAnchor(ScrollKeys.FONT_BODY_MEDIUM, scrollState) {
                    FontPreference(
                        fontPref = prefs2.fontBodyMedium,
                        label = stringResource(R.string.fontBodyMedium),
                    )
                }
            }
        }
        PreferenceGroup(
            modifier = Modifier,
            heading = stringResource(id = R.string.icons),
        ) {
            ScrollAnchor(ScrollKeys.ICON_PACK, scrollState) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.icon_pack),
                    destination = GeneralIconPack,
                    subtitle = currentIconPackName,
                )
            }
            ScrollAnchor(ScrollKeys.THEMED_ICONS, scrollState) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.themed_icon_title),
                    destination = GeneralThemedIcons,
                    subtitle = themedIconsSubtitle,
                )
            }
            ScrollAnchor(ScrollKeys.ICON_SHAPE, scrollState) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.icon_shape_label),
                    destination = GeneralIconShape(ShapeRoute.APP_SHAPE),
                    subtitle = iconShapeSubtitle,
                    endWidget = {
                        IconShapePreview(iconShape = iconShapeAdapter.state.value)
                    },
                )
            }
            ScrollAnchor(ScrollKeys.ADVANCED_ICONS, scrollState) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.advanced_icon_settings_title),
                    destination = GeneralAdvancedIcons,
                )
            }
        }

        val accentColorAdapter = prefs2.accentColor.getAdapter()
        val accentColorValue = accentColorAdapter.state.value
        val showColorStyle = !(Utilities.ATLEAST_S && accentColorValue == ColorOption.SystemAccent) ||
            !Utilities.ATLEAST_S

        val isWallpaperAccent = accentColorValue is ColorOption.WallpaperPrimary ||
            accentColorValue is ColorOption.WallpaperDerived
        val isCustomAccent = accentColorValue is ColorOption.CustomColor

        val colorStyleAdapter = prefs2.colorStyle.getAdapter()
        val currentColorStyle = colorStyleAdapter.state.value
        val colorSpecValue by prefs2.colorSpec.asState()
        val colorSpecAdapter = prefs2.colorSpec.getAdapter()
        val colorSpecFallbackAdapter = prefs2.colorSpecFallback.getAdapter()

        val effectiveColorStyle = when {
            !isWallpaperAccent && currentColorStyle is LegacyKdrag -> TonalSpot
            colorSpecValue != SpecVersion.SPEC_2026 && currentColorStyle is app.lawnchair.theme.color.Cmf -> TonalSpot
            else -> currentColorStyle
        }
        val colorStyleSubtitle = stringResource(id = effectiveColorStyle.nameResourceId)

        // Show color spec only for wallpaper or custom accents when not using LegacyKdrag.
        val showColorSpec = (isWallpaperAccent || isCustomAccent) &&
            effectiveColorStyle !is LegacyKdrag

        val colorSpecEntries = listOf(
            SpecVersion.SPEC_2021 to stringResource(id = R.string.color_spec_2021),
            SpecVersion.SPEC_2025 to stringResource(id = R.string.color_spec_2025),
            SpecVersion.SPEC_2026 to stringResource(id = R.string.color_spec_2026),
        )

        PreferenceGroup(heading = stringResource(id = R.string.colors)) {
            ScrollAnchor(ScrollKeys.ACCENT_COLOR, scrollState) {
                ColorPreference(preference = prefs2.accentColor)
            }
            ExpandAndShrink(visible = showColorStyle) {
                ScrollAnchor(ScrollKeys.COLOR_STYLE, scrollState) {
                    NavigationActionPreference(
                        label = stringResource(id = R.string.color_style_label),
                        destination = GeneralColorStyle(showLegacyKdrag = isWallpaperAccent),
                        subtitle = colorStyleSubtitle,
                    )
                }
            }
            ExpandAndShrink(visible = showColorSpec) {
                ScrollAnchor(ScrollKeys.COLOR_SPEC, scrollState) {
                    DropdownPreference(
                        label = stringResource(id = R.string.color_spec_label),
                        description = stringResource(id = R.string.color_spec_description),
                        entries = colorSpecEntries,
                        currentValue = colorSpecValue,
                        onValueChange = { newSpec ->
                            if (newSpec == SpecVersion.SPEC_2026 && colorSpecValue != SpecVersion.SPEC_2026) {
                                // Save the previous non-2026 spec as fallback for non-CMF styles.
                                colorSpecFallbackAdapter.onChange(colorSpecValue)
                            } else if (newSpec != SpecVersion.SPEC_2026) {
                                colorSpecFallbackAdapter.onChange(newSpec)
                                // Reset CMF to Tonal Spot when leaving Spec 2026.
                                if (currentColorStyle is app.lawnchair.theme.color.Cmf) {
                                    colorStyleAdapter.onChange(TonalSpot)
                                }
                            }
                            colorSpecAdapter.onChange(newSpec)
                        },
                    )
                }
            }
        }

        val notificationEnabled by remember { notificationDotsEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val serviceEnabled = notificationServiceEnabled()
        val showNotificationCountAdapter = prefs2.showNotificationCount.getAdapter()
        val showNotificationCount = showNotificationCountAdapter.state.value
        val dotColor = prefs2.notificationDotColor.asState().value
        val dotTextColor = prefs2.notificationDotTextColor.asState().value

        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            ScrollAnchor(ScrollKeys.NOTIFICATION_DOTS, scrollState) {
                NotificationDotsPreference(enabled = notificationEnabled, serviceEnabled = serviceEnabled)
            }
            val canDisplayNotificationDot = notificationEnabled && serviceEnabled
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                ScrollAnchor(ScrollKeys.NOTIFICATION_DOT_COLOR, scrollState) {
                    ColorPreference(preference = prefs2.notificationDotColor)
                }
            }
            ExpandAndShrink(visible = canDisplayNotificationDot) {
                ScrollAnchor(ScrollKeys.NOTIFICATION_COUNT, scrollState) {
                    SwitchPreference(
                        adapter = showNotificationCountAdapter,
                        label = stringResource(id = R.string.show_notification_count),
                    )
                }
            }
            ExpandAndShrink(visible = canDisplayNotificationDot && showNotificationCount) {
                ScrollAnchor(ScrollKeys.NOTIFICATION_DOT_TEXT_COLOR, scrollState) {
                    ColorPreference(preference = prefs2.notificationDotTextColor)
                }
            }
            ExpandAndShrink(visible = canDisplayNotificationDot && showNotificationCount) {
                NotificationDotColorContrastWarnings(
                    dotColor = dotColor,
                    dotTextColor = dotTextColor,
                )
            }
        }

        // Settings wallpaper blur requires storage permissions on Android 10+.
        val settingsBlurAdapter = prefs2.settingsBlurBackground.getAdapter()
        var showSettingsBlurPermissionDialog by rememberSaveable { mutableStateOf(false) }
        var settingsBlurManagedFilesChecked by rememberSaveable {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    false
                },
            )
        }
        val settingsBlurMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberMultiplePermissionsState(
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
            )
        } else {
            null
        }
        val settingsBlurPermissionsGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                settingsBlurManagedFilesChecked && (settingsBlurMediaPermission?.allPermissionsGranted == true)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> settingsBlurManagedFilesChecked

            else -> true
        }
        LaunchedEffect(settingsBlurManagedFilesChecked, settingsBlurMediaPermission?.allPermissionsGranted) {
            if (showSettingsBlurPermissionDialog && settingsBlurPermissionsGranted) {
                showSettingsBlurPermissionDialog = false
                settingsBlurAdapter.onChange(true)
            }
        }

        PreferenceGroup(heading = stringResource(id = R.string.settings_background_label)) {
            ScrollAnchor(ScrollKeys.SETTINGS_BLUR, scrollState) {
                SwitchPreference(
                    checked = settingsBlurAdapter.state.value,
                    onCheckedChange = { checked ->
                        if (checked && !settingsBlurPermissionsGranted) {
                            showSettingsBlurPermissionDialog = true
                        } else {
                            settingsBlurAdapter.onChange(checked)
                        }
                    },
                    label = stringResource(id = R.string.settings_blur_label),
                    description = stringResource(id = R.string.settings_blur_description),
                )
            }
            ExpandAndShrink(visible = settingsBlurAdapter.state.value) {
                ScrollAnchor(ScrollKeys.SETTINGS_BLUR_INTENSITY, scrollState) {
                    SliderPreference(
                        label = stringResource(id = R.string.settings_blur_intensity_label),
                        adapter = prefs2.settingsBlurIntensity.getAdapter(),
                        valueRange = 10f..150f,
                        step = 5f,
                        showUnit = "%",
                    )
                }
            }
        }

        if (showSettingsBlurPermissionDialog) {
            WallpaperAccessPermissionDialog(
                managedFilesChecked = settingsBlurManagedFilesChecked,
                onDismiss = { showSettingsBlurPermissionDialog = false },
                onPermissionRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        settingsBlurManagedFilesChecked = Environment.isExternalStorageManager()
                    }
                },
            )
        }
    }
}

@Composable
private fun NotificationDotColorContrastWarnings(
    dotColor: ColorOption,
    dotTextColor: ColorOption,
) {
    val dotColorIsDynamic = when (dotColor) {
        is ColorOption.SystemAccent,
        is ColorOption.WallpaperPrimary,
        is ColorOption.WallpaperDerived,
        is ColorOption.Default,
        -> true

        else -> false
    }

    if (dotColorIsDynamic && dotTextColor !is ColorOption.Default) {
        WarningPreference(
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_sometimes),
        )
    } else {
        ColorContrastWarning(
            foregroundColor = dotTextColor,
            backgroundColor = dotColor,
            text = stringResource(id = R.string.notification_dots_color_contrast_warning_always),
        )
    }
}
