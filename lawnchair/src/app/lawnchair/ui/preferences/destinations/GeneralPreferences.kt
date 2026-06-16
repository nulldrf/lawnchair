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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import android.os.Environment
import app.lawnchair.ui.preferences.components.WallpaperAccessPermissionDialog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.TonalSpot
import com.android.systemui.monet.SpecVersion
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.ThemePreference
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
import app.lawnchair.ui.preferences.navigation.GeneralColorStyle
import app.lawnchair.ui.preferences.navigation.GeneralIconPack
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GeneralPreferences() {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val themedIconsAdapter = prefs.themedIcons.getAdapter()
    val drawerThemedIconsAdapter = prefs.drawerThemedIcons.getAdapter()
    val iconShapeAdapter = prefs2.iconShape.getAdapter()

    val currentIconPackName = iconPacks
        .find { it.packageName == preferenceManager().iconPackPackage.get() }
        ?.name
    val themedIconsEnabled = ThemedIconsState.getForSettings(
        themedIcons = themedIconsAdapter.state.value,
        drawerThemedIcons = drawerThemedIconsAdapter.state.value,
    ) != ThemedIconsState.Off
    val iconStyleSubtitle = if (currentIconPackName != null && themedIconsEnabled) {
        stringResource(
            id = R.string.x_and_y,
            currentIconPackName,
            stringResource(id = R.string.themed_icon_title),
        )
    } else {
        currentIconPackName
    }
    val iconShapeSubtitle = iconShapeEntries(context)
        .firstOrNull { it.value == iconShapeAdapter.state.value }
        ?.label?.invoke()
        ?: stringResource(id = R.string.custom)
    val scrollState = rememberPreferenceScrollState()

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.general_label),
    ) {
        // ── Theme ─────────────────────────────────────────────────────────────
        ThemePreference()

        // ── Rotation ──────────────────────────────────────────────────────────
        PreferenceGroup {
            Item {
                SwitchPreference(
                    adapter = prefs.allowRotation.getAdapter(),
                    label = stringResource(id = R.string.home_screen_rotation_label),
                    description = stringResource(id = R.string.home_screen_rotation_description),
                )
            }
        }

        // ── Haptic feedback ───────────────────────────────────────────────────
        PreferenceGroup {
            Item {
                SwitchPreference(
                    adapter = prefs2.hapticFeedback.getAdapter(),
                    label = stringResource(id = R.string.haptic_feedback_label),
                    description = stringResource(id = R.string.haptic_feedback_description),
                )
            }
        }

        // ── Auto-updater (nightly builds only) ────────────────────────────────
        if (BuildConfig.APPLICATION_ID.contains("nightly")) {
            PreferenceGroup(heading = stringResource(id = R.string.updater)) {
                Item {
                    SwitchPreference(
                        adapter = prefs2.autoUpdaterNightly.getAdapter(),
                        label = stringResource(id = R.string.auto_updater_label),
                        description = stringResource(id = R.string.auto_updater_description),
                    )
                }
            }
        }

        // ── Fonts ─────────────────────────────────────────────────────────────
        ExpandAndShrink(visible = prefs2.enableFontSelection.asState().value) {
            PreferenceGroup(heading = stringResource(id = R.string.font_label)) {
                Item {
                    FontPreference(
                        fontPref = prefs.fontWorkspace,
                        label = stringResource(R.string.fontWorkspace),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontHeading,
                        label = stringResource(R.string.fontHeading),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontHeadingMedium,
                        label = stringResource(R.string.fontHeadingMedium),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontBody,
                        label = stringResource(R.string.fontBody),
                    )
                }
                Item {
                    FontPreference(
                        fontPref = prefs.fontBodyMedium,
                        label = stringResource(R.string.fontBodyMedium),
                    )
                }
            }
        }

        // ── Icons ─────────────────────────────────────────────────────────────
        val wrapAdaptiveIcons = prefs.wrapAdaptiveIcons.getAdapter()
        val colorizedBackgrounds = prefs.colorizedBackgrounds.getAdapter()

        PreferenceGroup(
            heading = stringResource(id = R.string.icons),
            description = stringResource(id = (R.string.adaptive_icon_background_description)),
            showDescription = wrapAdaptiveIcons.state.value,
        ) {
            Item {
                ScrollAnchor(ScrollKeys.ICON_STYLE, scrollState) {
                    NavigationActionPreference(
                        label = stringResource(id = R.string.icon_style_label),
                        destination = GeneralIconPack,
                        subtitle = iconStyleSubtitle,
                    )
                }
            }
            Item(
                "themed_icon",
                themedIconsEnabled,
            ) {
                SwitchPreference(
                    adapter = prefs.transparentIconBackground.getAdapter(),
                    label = stringResource(id = R.string.transparent_background_icons_label),
                    description = stringResource(id = R.string.transparent_background_icons_description),
                )
            }
            Item {
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
            }
            Item {
                ScrollAnchor(ScrollKeys.AUTO_ADAPTIVE, scrollState) {
                    SwitchPreference(
                        adapter = wrapAdaptiveIcons,
                        label = stringResource(id = R.string.auto_adaptive_icons_label),
                        description = stringResource(id = R.string.auto_adaptive_icons_description),
                    )
                }
            }
            Item {
                ScrollAnchor(ScrollKeys.SHADOW_ICONS, scrollState) {
                    SwitchPreference(
                        adapter = prefs.shadowBGIcons.getAdapter(),
                        label = stringResource(id = R.string.shadow_bg_icons_label),
                    )
                }
            }
            Item(
                "wrap_adaptive_icons",
                wrapAdaptiveIcons.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.background_lightness_label),
                    adapter = prefs.coloredBackgroundLightness.getAdapter(),
                    valueRange = 0F..1F,
                    step = 0.1f,
                    showAsPercentage = true,
                )
            }
            Item(
                "colorized_backgrounds",
                wrapAdaptiveIcons.state.value,
            ) {
                SwitchPreference(
                    adapter = colorizedBackgrounds,
                    label = stringResource(id = R.string.colorized_backgrounds_label),
                    description = stringResource(id = R.string.colorized_backgrounds_description),
                )
            }
            Item(
                "treat_white_adaptive_icons",
                wrapAdaptiveIcons.state.value && colorizedBackgrounds.state.value,
            ) {
                SwitchPreference(
                    adapter = prefs.treatWhiteAdaptiveIcons.getAdapter(),
                    label = stringResource(id = R.string.treat_white_adaptive_icons_label),
                    description = stringResource(id = R.string.treat_white_adaptive_icons_description),
                )
            }
            Item(
                "colorize_icon_pack_background",
                colorizedBackgrounds.state.value,
            ) {
                SwitchPreference(
                    adapter = prefs.colorizeIconPackBackground.getAdapter(),
                    label = stringResource(id = R.string.colorize_icon_pack_background_label),
                    description = stringResource(id = R.string.colorize_icon_pack_background_description),
                )
            }
        }

        // ── Colors ────────────────────────────────────────────────────────────
        val accentColorAdapter = prefs2.accentColor.getAdapter()
        val accentColorValue = accentColorAdapter.state.value
        val showColorStyle = !(Utilities.ATLEAST_S && accentColorValue == ColorOption.SystemAccent) ||
            !Utilities.ATLEAST_S

        val isWallpaperAccent = accentColorValue is ColorOption.WallpaperPrimary ||
            accentColorValue is ColorOption.WallpaperDerived
        val isCustomAccent = accentColorValue is ColorOption.CustomColor

        val currentColorStyle = prefs2.colorStyle.asState().value
        val effectiveColorStyle = if (!isWallpaperAccent && currentColorStyle is LegacyKdrag) {
            TonalSpot
        } else {
            currentColorStyle
        }
        val colorStyleSubtitle = stringResource(id = effectiveColorStyle.nameResourceId)

        // Color spec is only meaningful when the accent comes from a wallpaper or a
        // custom colour — system accent always uses the system Monet pipeline.
        // Also hide when LegacyKdrag (ZCAM engine) is active — it has its own
        // algorithm and is unaffected by Material spec versions.
        val showColorSpec = (isWallpaperAccent || isCustomAccent) &&
            effectiveColorStyle !is LegacyKdrag

        // Use asState() directly so Compose tracks the preference as reactive
        // state — prevents stale reads when the user changes the value quickly.
        val colorSpecValue by prefs2.colorSpec.asState()
        val colorSpecAdapter = prefs2.colorSpec.getAdapter()
        val colorSpecEntries = listOf(
            SpecVersion.SPEC_2021 to stringResource(id = R.string.color_spec_2021),
            SpecVersion.SPEC_2025 to stringResource(id = R.string.color_spec_2025),
        )

        PreferenceGroup(heading = stringResource(id = R.string.colors)) {
            Item { ScrollAnchor(ScrollKeys.ACCENT_COLOR, scrollState) { ColorPreference(preference = prefs2.accentColor) } }
            Item(
                "color_style",
                showColorStyle,
            ) {
                ScrollAnchor(ScrollKeys.COLOR_STYLE, scrollState) {
                    NavigationActionPreference(
                        label = stringResource(id = R.string.color_style_label),
                        destination = GeneralColorStyle(showLegacyKdrag = isWallpaperAccent),
                        subtitle = colorStyleSubtitle,
                    )
                }
            }
            Item(
                "color_spec",
                showColorSpec,
            ) {
                DropdownPreference(
                    label = stringResource(id = R.string.color_spec_label),
                    description = stringResource(id = R.string.color_spec_description),
                    entries = colorSpecEntries,
                    currentValue = colorSpecValue,
                    onValueChange = { colorSpecAdapter.onChange(it) },
                )
            }
        }

        // ── Notification dots ─────────────────────────────────────────────────
        val notificationEnabled by remember { notificationDotsEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
        val serviceEnabled = notificationServiceEnabled()
        val showNotificationCountAdapter = prefs2.showNotificationCount.getAdapter()
        val showNotificationCount = showNotificationCountAdapter.state.value
        val dotColor = prefs2.notificationDotColor.asState().value
        val dotTextColor = prefs2.notificationDotTextColor.asState().value

        PreferenceGroup(heading = stringResource(id = R.string.notification_dots)) {
            Item { ScrollAnchor(ScrollKeys.NOTIFICATION_DOTS, scrollState) { NotificationDotsPreference(enabled = notificationEnabled, serviceEnabled = serviceEnabled) } }
            val canDisplayNotificationDot = notificationEnabled && serviceEnabled
            Item(
                "notification_dot_color",
                canDisplayNotificationDot,
            ) { ColorPreference(preference = prefs2.notificationDotColor) }
            Item(
                "notification_dot_counter_toggle",
                canDisplayNotificationDot,
            ) {
                SwitchPreference(
                    adapter = showNotificationCountAdapter,
                    label = stringResource(id = R.string.show_notification_count),
                )
            }
            Item(
                "notification_dot_text_color",
                canDisplayNotificationDot && showNotificationCount,
            ) { ColorPreference(preference = prefs2.notificationDotTextColor) }
            Item(
                "notification_dot_color_contrast_warning",
                canDisplayNotificationDot && showNotificationCount,
            ) {
                NotificationDotColorContrastWarnings(
                    dotColor = dotColor,
                    dotTextColor = dotTextColor,
                )
            }
        }

        // ── Settings background blur ───────────────────────────────────────────
        // Matches old Lawnchair's Theme → Blur + Blur Intensity pair.
        // The toggle enables the blurred wallpaper background across all settings
        // screens; the slider controls intensity (10 = subtle frost, 150 = heavy fog).
        // Both prefs are read inside PreferenceLayout → SettingsBlurContainer so the
        // effect is instant on the entire settings stack — no recreate needed.
        //
        // On Android 10+ the blur reads wallpaper pixels, so we need the same
        // storage permissions used by the wallpaper-preview feature.
        val settingsBlurAdapter = prefs.settingsBlurBackground.getAdapter()
        var showSettingsBlurPermissionDialog by rememberSaveable { mutableStateOf(false) }
        var settingsBlurManagedFilesChecked by rememberSaveable {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else false,
            )
        }
        val settingsBlurMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberMultiplePermissionsState(
                listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
            )
        } else null
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
            Item {
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
            Item(
                "settings_blur_intensity",
                settingsBlurAdapter.state.value,
            ) {
                SliderPreference(
                    label = stringResource(id = R.string.settings_blur_intensity_label),
                    adapter = prefs.settingsBlurIntensity.getAdapter(),
                    valueRange = 10F..150F,
                    step = 10f,
                )
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
