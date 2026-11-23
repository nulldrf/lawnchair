/*
 * Copyright 2021, Lawnchair
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
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.data.iconoverride.IconOverrideRepository
import app.lawnchair.hotseat.DisabledHotseat
import app.lawnchair.hotseat.LawnchairHotseat
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.predictions.LawnchairPredictor
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.not
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.firstCached
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.smartspace.model.LawnchairSmartspace
import app.lawnchair.smartspace.model.Smartspacer
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.smartspace.provider.WeatherDataProvider
import app.lawnchair.smartspace.provider.weather.WeatherProvider
import app.lawnchair.theme.color.Cmf
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.TonalSpot
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.AnnouncementPreference
import app.lawnchair.ui.preferences.components.WallpaperAccessPermissionDialog
import app.lawnchair.ui.preferences.components.controls.PreferenceCategory
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.ScrollTargetManager
import app.lawnchair.ui.preferences.components.layout.rememberSettingsBlurState
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import app.lawnchair.ui.preferences.data.liveinfo.SyncLiveInformation
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.navigation.About
import app.lawnchair.ui.preferences.navigation.AppDrawer
import app.lawnchair.ui.preferences.navigation.BackupAndRestore
import app.lawnchair.ui.preferences.navigation.Dock
import app.lawnchair.ui.preferences.navigation.ExperimentalFeatures
import app.lawnchair.ui.preferences.navigation.Extras
import app.lawnchair.ui.preferences.navigation.Folders
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.ui.preferences.navigation.GeneralAdvancedIcons
import app.lawnchair.ui.preferences.navigation.GeneralThemedIcons
import app.lawnchair.ui.preferences.navigation.Gestures
import app.lawnchair.ui.preferences.navigation.HomeScreen
import app.lawnchair.ui.preferences.navigation.Predictions
import app.lawnchair.ui.preferences.navigation.PreferenceRootRoute
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.ui.preferences.navigation.Quickstep
import app.lawnchair.ui.preferences.navigation.Search
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.ui.theme.isSelectedThemeDark
import app.lawnchair.util.FileAccessManager
import app.lawnchair.util.FileAccessState
import app.lawnchair.util.collectAsStateBlocking
import app.lawnchair.util.isDefaultLauncher
import app.lawnchair.util.isOnePlusStock
import com.android.launcher3.BuildConfig
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.systemui.monet.SpecVersion
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.msdl.data.model.MSDLToken

// Checked state and change handler for inline switches in search results.
private data class ToggleAction(
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

private fun PreferenceAdapter<Boolean>.toToggle(): ToggleAction = ToggleAction(checked = state.value) { onChange(it) }

private data class SearchableEntry(
    val label: String,
    val keywords: String = "",
    val breadcrumb: String,
    val iconResource: Int,
    val route: PreferenceRoute,
    val scrollKey: String? = null,
    val toggle: ToggleAction? = null,
)

/**
 * Android 16 / Material 3 Expressive extended palette tokens for Settings dashboard icon badges
 * (`m3_ref_palette_*`: tone 90 light container, tone 80 dark container, tone 30 icon foreground).
 */
private enum class DashboardIconColor(
    val lightContainer: Color,
    val darkContainer: Color,
    val content: Color,
) {
    Orange(
        lightContainer = Color(0xFFFFDCC3),
        darkContainer = Color(0xFFFFB683),
        content = Color(0xFF753403),
    ),
    Blue(
        lightContainer = Color(0xFFD0E4FF),
        darkContainer = Color(0xFFA1C9FF),
        content = Color(0xFF04409F),
    ),
    BlueVariant(
        lightContainer = Color(0xFFBDE9FF),
        darkContainer = Color(0xFF67D4FF),
        content = Color(0xFF004D68),
    ),
    Yellow(
        lightContainer = Color(0xFFFFE07C),
        darkContainer = Color(0xFFFCBD00),
        content = Color(0xFF6D3A01),
    ),
    Cyan(
        lightContainer = Color(0xFFACEDFF),
        darkContainer = Color(0xFF60D5F3),
        content = Color(0xFF004E5D),
    ),
    Purple(
        lightContainer = Color(0xFFEEDCFE),
        darkContainer = Color(0xFFD9BAFD),
        content = Color(0xFF5629A4),
    ),
    Pink(
        lightContainer = Color(0xFFFFD8EF),
        darkContainer = Color(0xFFFFAEE4),
        content = Color(0xFF8D0053),
    ),
    Green(
        lightContainer = Color(0xFFBEEFBB),
        darkContainer = Color(0xFF80DA88),
        content = Color(0xFF00522C),
    ),
    Red(
        lightContainer = Color(0xFFFFDADC),
        darkContainer = Color(0xFFFFB3AE),
        content = Color(0xFF8A1A16),
    ),
    Grey(
        lightContainer = Color(0xFFE3E3E3),
        darkContainer = Color(0xFFC7C7C7),
        content = Color(0xFF474747),
    ),
    ;

    fun containerColor(isDark: Boolean): Color = if (isDark) darkContainer else lightContainer
}

private fun dashboardIconColorFor(@DrawableRes iconResource: Int): DashboardIconColor = when (iconResource) {
    R.drawable.ic_general -> DashboardIconColor.Orange
    R.drawable.ic_home_screen -> DashboardIconColor.Blue
    R.drawable.ic_smartspace, R.drawable.ic_smartspace_off -> DashboardIconColor.Yellow
    R.drawable.ic_dock -> DashboardIconColor.Cyan
    R.drawable.ic_folder -> DashboardIconColor.Purple
    R.drawable.ic_apps -> DashboardIconColor.Pink
    R.drawable.ic_search -> DashboardIconColor.BlueVariant
    R.drawable.ic_gestures -> DashboardIconColor.Green
    R.drawable.ic_quickstep -> DashboardIconColor.Blue
    R.drawable.backup_restore -> DashboardIconColor.Yellow
    R.drawable.ic_extras -> DashboardIconColor.Red
    R.drawable.ic_about -> DashboardIconColor.Grey
    else -> DashboardIconColor.Grey
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun segmentedItemShapes(index: Int, count: Int): ListItemShapes = when {
    count <= 1 -> ListItemDefaults.segmentedShapes(index = 0, count = 1)
    index <= 0 -> ListItemDefaults.segmentedShapes(index = 0, count = 2)
    index >= count - 1 -> ListItemDefaults.segmentedShapes(index = 1, count = 2)
    else -> ListItemDefaults.segmentedShapes(index = 1, count = 3)
}

@Composable
private fun DashboardCategory(
    label: String,
    description: String,
    @DrawableRes iconResource: Int,
    onNavigate: () -> Unit,
    isSelected: Boolean,
    shapes: ListItemShapes,
    isDarkTheme: Boolean,
) {
    val badgeColor = dashboardIconColorFor(iconResource)
    PreferenceCategory(
        label = label,
        description = description,
        iconResource = iconResource,
        onNavigate = onNavigate,
        isSelected = isSelected,
        shapes = shapes,
        iconContainerColor = badgeColor.containerColor(isDarkTheme),
        iconContentColor = badgeColor.content,
    )
}

// Mirrors AppDrawerHapticFeedbackPreference so the search entry matches device support.
private fun isDrawerHapticFeedbackSupported(context: Context): Boolean {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return false
    if (!vibrator.hasVibrator()) return false

    if (Flags.msdlFeedback() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return vibrator.arePrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)[0] ||
            vibrator.areEffectsSupported(VibrationEffect.EFFECT_CLICK)[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
    }

    return true
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PreferencesDashboard(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    SyncLiveInformation()
    val prefs2 = preferenceManager2()

    val liveInformationManager = liveInformationManager()
    val enabled by liveInformationManager.enabled.asState()
    val showAnnouncements by liveInformationManager.showAnnouncements.asState()
    val dismissedAnnouncementIds by liveInformationManager.dismissedAnnouncementIds.asState()
    val liveInformation by liveInformationManager.liveInformation.asState()

    val activeAnnouncements = remember(liveInformation, dismissedAnnouncementIds) {
        liveInformation.announcements.filter {
            it.shouldBeVisible && it.id !in dismissedAnnouncementIds
        }
    }
    val announcementShowing = enabled && showAnnouncements && activeAnnouncements.isNotEmpty()
    val isNotDefaultLauncher = !context.isDefaultLauncher()

    val settingsLabel = stringResource(id = R.string.settings)
    val setDefaultLabel = stringResource(id = R.string.set_default_launcher_short)
    val expandedLabel = if (announcementShowing && isNotDefaultLauncher) setDefaultLabel else settingsLabel

    val onExpandedTitleClick: (() -> Unit)? = if (announcementShowing && isNotDefaultLauncher) {
        {
            Intent(Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
            (context as? Activity)?.finish()
        }
    } else {
        null
    }

    val aboutDescription = if (prefs2.hideVersionInfo.firstCached()) {
        prefs2.pseudonymVersion.firstCached()
    } else {
        "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}"
    }

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var searchBarOffsetY by remember { mutableIntStateOf(0) }

    val labelGeneral = stringResource(R.string.general_label)
    val descGeneral = stringResource(R.string.general_description)
    val labelThemedIcons = stringResource(R.string.themed_icon_title)
    val labelAdvancedIcons = stringResource(R.string.advanced_icon_settings_title)
    val labelHomeScreen = stringResource(R.string.home_screen_label)
    val descHomeScreen = stringResource(R.string.home_screen_description)
    val labelSmartspace = stringResource(id = R.string.smartspace_widget)
    val descSmartspace = stringResource(R.string.smartspace_widget_description)
    val labelDock = stringResource(R.string.dock_label)
    val descDock = stringResource(R.string.dock_description)
    val labelAppDrawer = stringResource(R.string.app_drawer_label)
    val descAppDrawer = stringResource(R.string.app_drawer_description)
    val labelSuggestions = stringResource(R.string.suggestion_pref_screen_title)
    val labelSearchBar = stringResource(R.string.search_bar_label)
    val descSearchBar = stringResource(R.string.drawer_search_description)
    val labelFolders = stringResource(R.string.folders_label)
    val descFolders = stringResource(R.string.folders_description)
    val labelGestures = stringResource(id = R.string.gestures_label)
    val descGestures = stringResource(R.string.gestures_description)
    val labelQuickstep = stringResource(id = R.string.quickstep_label)
    val descQuickstep = stringResource(id = R.string.quickstep_description)
    val labelBackup = stringResource(R.string.backup_and_restore_label)
    val descBackup = stringResource(R.string.backup_and_restore_description)
    val labelExtras = stringResource(R.string.extras_label)
    val descExtras = stringResource(R.string.extras_description)
    val labelExperimental = stringResource(R.string.experimental_features_label)
    val labelAbout = stringResource(R.string.about_label)

    val deckLayout = prefs2.deckLayout.getAdapter()
    val isSmartspaceEnabled = prefs2.enableSmartspace.firstCached()

    // General state
    val allowRotationAdapter = prefs2.allowRotation.getAdapter()
    val isNightlyBuild = BuildConfig.APPLICATION_ID.contains("nightly")
    val autoUpdaterNightlyAdapter = prefs2.autoUpdaterNightly.getAdapter()
    val liveInfoEnabledAdapter = liveInformationManager.enabled.getAdapter()
    val enableFontSelectionAdapter = prefs2.enableFontSelection.getAdapter()
    val fontSelectionEnabled = prefs2.enableFontSelection.asState().value
    val accentColorValue = prefs2.accentColor.getAdapter().state.value
    val isWallpaperAccent = accentColorValue is ColorOption.WallpaperPrimary || accentColorValue is ColorOption.WallpaperDerived
    val isCustomAccent = accentColorValue is ColorOption.CustomColor
    val currentColorStyle = prefs2.colorStyle.asState().value
    val colorSpecValue by prefs2.colorSpec.asState()
    val effectiveColorStyle = when {
        !isWallpaperAccent && currentColorStyle is LegacyKdrag -> TonalSpot
        colorSpecValue != SpecVersion.SPEC_2026 && currentColorStyle is Cmf -> TonalSpot
        else -> currentColorStyle
    }
    val showColorStyle = !(Utilities.ATLEAST_S && accentColorValue == ColorOption.SystemAccent) || !Utilities.ATLEAST_S
    val showColorSpec = (isWallpaperAccent || isCustomAccent) && effectiveColorStyle !is LegacyKdrag

    val notificationEnabled by remember { notificationDotsEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
    val notificationServiceOn = notificationServiceEnabled()
    val canDisplayNotificationDot = notificationEnabled && notificationServiceOn
    val showNotificationCountAdapter = prefs2.showNotificationCount.getAdapter()
    val showNotificationCount = showNotificationCountAdapter.state.value

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
    val settingsBlurToggle = ToggleAction(checked = settingsBlurAdapter.state.value) { checked ->
        if (checked && !settingsBlurPermissionsGranted) {
            showSettingsBlurPermissionDialog = true
        } else {
            settingsBlurAdapter.onChange(checked)
        }
    }

    // Themed & advanced icon state
    val themedIconsActive = prefs2.themedIcons.getAdapter().state.value
    val forceIconMonochromeAdapter = prefs2.forceIconMonochrome.getAdapter()
    val transparentIconBackgroundAdapter = prefs2.transparentIconBackground.getAdapter()
    val transparentIconBackground = transparentIconBackgroundAdapter.state.value
    val alwaysReloadIconsAdapter = prefs2.alwaysReloadIcons.getAdapter()
    val shadowBGIconsAdapter = prefs2.shadowBGIcons.getAdapter()
    val wrapAdaptiveIconsAdapter = prefs2.wrapAdaptiveIcons.getAdapter()
    val wrapAdaptiveIcons = wrapAdaptiveIconsAdapter.state.value
    val colorizedBackgroundsAdapter = prefs2.colorizedBackgrounds.getAdapter()
    val colorizedBackgrounds = colorizedBackgroundsAdapter.state.value
    val treatWhiteAdaptiveIconsAdapter = prefs2.treatWhiteAdaptiveIcons.getAdapter()
    val colorizeIconPackBackgroundAdapter = prefs2.colorizeIconPackBackground.getAdapter()

    // Home screen state
    val hasMotionSensor = remember {
        context.getSystemService(SensorManager::class.java)?.let { manager ->
            manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null ||
                manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        } == true
    }
    val showDeckLayoutAdapter = prefs2.showDeckLayout.getAdapter()
    val addIconToHomeAdapter = prefs2.addIconToHome.getAdapter()
    val infiniteScrollingAdapter = prefs2.infiniteScrolling.getAdapter()
    val panToMoveEnabledAdapter = prefs2.panToMoveEnabled.getAdapter()
    val enableFeedAdapter = prefs2.enableFeed.getAdapter()
    val enableFeedState = enableFeedAdapter.state.value
    val feedAvailableState = OverlayCallbackImpl.minusOneAvailable(context)
    val wallpaperScrollingAdapter = prefs2.wallpaperScrolling.getAdapter()
    val wallpaperDepthEffectAdapter = prefs2.wallpaperDepthEffect.getAdapter()
    val showTopShadowAdapter = prefs2.showTopShadow.getAdapter()
    val lockHomeScreenAdapter = prefs2.lockHomeScreen.getAdapter()
    val showStatusBarAdapter = prefs2.showStatusBar.getAdapter()
    val showStatusBarState = showStatusBarAdapter.state.value
    val darkStatusBarAdapter = prefs2.darkStatusBar.getAdapter()
    val statusBarClockAdapter = prefs2.statusBarClock.getAdapter()
    val homeScreenLabelsAdapterObj = prefs2.showIconLabelsOnHomeScreen.getAdapter()
    val homeScreenLabelsAdapter = homeScreenLabelsAdapterObj.state.value
    val twoLineHomeScreenAdapter = prefs2.twoLineHomeScreen.getAdapter()
    val overrideRepo = IconOverrideRepository.INSTANCE.get(context)
    val customIconsCount by remember { overrideRepo.observeCount() }.collectAsStateBlocking()
    val roundedWidgetsAdapter = prefs2.roundedWidgets.getAdapter()
    val allowWidgetOverlapAdapter = prefs2.allowWidgetOverlap.getAdapter()
    val widgetUnlimitedSizeAdapter = prefs2.widgetUnlimitedSize.getAdapter()
    val forceWidgetResizeAdapter = prefs2.forceWidgetResize.getAdapter()

    // Smartspace state
    val enableSmartspaceAdapter = prefs2.enableSmartspace.getAdapter()
    val smartspaceEnabledReactive = enableSmartspaceAdapter.state.value
    val smartspaceModeValue = prefs2.smartspaceMode.getAdapter().state.value
    val isLawnchairSmartspace = smartspaceModeValue == LawnchairSmartspace
    val isSmartspacerMode = smartspaceModeValue == Smartspacer
    val smartspaceLawnchairActive = smartspaceEnabledReactive && isLawnchairSmartspace
    val weatherProviderValue = prefs2.smartspaceWeatherProvider.getAdapter().state.value
    val hasWeatherSource = weatherProviderValue != WeatherProvider.NONE
    val smartspaceCalendarValue = prefs2.smartspaceCalendar.getAdapter().state.value
    val supportsCalendarCustomization = smartspaceCalendarValue.formatCustomizationSupport
    val smartspaceShowDateAdapter = prefs2.smartspaceShowDate.getAdapter()
    val smartspaceShowDate = smartspaceShowDateAdapter.state.value
    val smartspaceShowTimeAdapter = prefs2.smartspaceShowTime.getAdapter()
    val smartspaceShowTime = smartspaceShowTimeAdapter.state.value
    val smartspaceHasMinimumContent = !smartspaceShowDate || !smartspaceShowTime

    val smartspaceDataSources = if (smartspaceLawnchairActive) {
        SmartspaceProvider.INSTANCE.get(context).dataSources
            .filter { it.isAvailable && it !is WeatherDataProvider }
    } else {
        emptyList()
    }
    val weatherWidgetAdapter = smartspaceDataSources
        .firstOrNull { it.providerName == R.string.smartspace_weather }?.enabledPref?.getAdapter()
    val batteryStatusAdapter = smartspaceDataSources
        .firstOrNull { it.providerName == R.string.smartspace_battery_status }?.enabledPref?.getAdapter()
    val torchAdapter = smartspaceDataSources
        .firstOrNull { it.providerName == R.string.smartspace_torch }?.enabledPref?.getAdapter()
    val nowPlayingAdapter = smartspaceDataSources
        .firstOrNull { it.providerName == R.string.smartspace_now_playing }?.enabledPref?.getAdapter()
    val onboardingAdapter = smartspaceDataSources
        .firstOrNull { it.providerName == R.string.smartspace_onboarding }?.enabledPref?.getAdapter()
    val personalityAdapter = smartspaceDataSources
        .firstOrNull { it.providerName == R.string.smartspace_personality }?.enabledPref?.getAdapter()

    // Dock state
    val isHotseatEnabledAdapter = prefs2.isHotseatEnabled.getAdapter()
    val isHotseatEnabled = isHotseatEnabledAdapter.state.value
    val hotseatModeValue = prefs2.hotseatMode.getAdapter().state.value
    val isLawnchairHotseat = hotseatModeValue == LawnchairHotseat && hotseatModeValue != DisabledHotseat
    val hotseatBgAdapter = prefs2.hotseatBG.getAdapter()
    val hotseatBgEnabled = hotseatBgAdapter.state.value
    val enableLabelInDockAdapter = prefs2.enableLabelInDock.getAdapter()
    val twoLineDockAdapter = prefs2.twoLineDock.getAdapter()
    val themedHotseatQsbAdapter = prefs2.themedHotseatQsb.getAdapter()
    val hotseatQsbStrokeWidth = prefs2.hotseatQsbStrokeWidth.getAdapter().state.value

    // App drawer & predictions state
    val drawerListEnabled = prefs2.drawerList.getAdapter().state.value
    val hiddenApps = prefs2.hiddenApps.getAdapter().state.value
    val showIconLabelsInDrawerAdapterObj = prefs2.showIconLabelsInDrawer.getAdapter()
    val showDrawerLabels = showIconLabelsInDrawerAdapterObj.state.value
    val drawerHapticSupported = remember { isDrawerHapticFeedbackSupported(context) }
    val appDrawerSearchBarAtBottomAdapter = prefs2.appDrawerSearchBarAtBottom.getAdapter()
    val appDrawerHapticFeedbackAdapter = prefs2.appDrawerHapticFeedback.getAdapter()
    val workProfileTabContainerBackgroundAdapter = prefs2.workProfileTabContainerBackground.getAdapter()
    val appDrawerSearchBarBackgroundAdapter = prefs2.appDrawerSearchBarBackground.getAdapter()
    val twoLineAllAppsAdapter = prefs2.twoLineAllApps.getAdapter()
    val rememberPositionAdapter = prefs2.rememberPosition.getAdapter()
    val showScrollbarAdapter = prefs2.showScrollbar.getAdapter()
    val enableGlobalPredictionAdapter = prefs2.enableGlobalPrediction.getAdapter()
    val globalPredictionEnabled = enableGlobalPredictionAdapter.state.value
    val predictionModeValue = prefs2.predictionMode.getAdapter().state.value

    val drawerBlurBackgroundAdapter = prefs2.drawerBlurBackground.getAdapter()
    var showDrawerBlurPermissionDialog by rememberSaveable { mutableStateOf(false) }
    var drawerBlurManagedFilesChecked by rememberSaveable {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                false
            },
        )
    }
    val drawerBlurMediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO),
        )
    } else {
        null
    }
    val drawerBlurPermissionsGranted = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            drawerBlurManagedFilesChecked && (drawerBlurMediaPermission?.allPermissionsGranted == true)

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> drawerBlurManagedFilesChecked

        else -> true
    }
    LaunchedEffect(drawerBlurManagedFilesChecked, drawerBlurMediaPermission?.allPermissionsGranted) {
        if (showDrawerBlurPermissionDialog && drawerBlurPermissionsGranted) {
            showDrawerBlurPermissionDialog = false
            drawerBlurBackgroundAdapter.onChange(true)
        }
    }
    val drawerBlurToggle = ToggleAction(checked = drawerBlurBackgroundAdapter.state.value) { checked ->
        if (checked && !drawerBlurPermissionsGranted) {
            showDrawerBlurPermissionDialog = true
        } else {
            drawerBlurBackgroundAdapter.onChange(checked)
        }
    }

    // Drawer search state
    val showDrawerSearchBarAdapter = !prefs2.hideAppDrawerSearchBar.getAdapter()
    val showDrawerSearchBarEnabled = showDrawerSearchBarAdapter.state.value
    val searchAlgorithmValue = prefs2.searchAlgorithm.getAdapter().state.value
    val isAsiSearch = searchAlgorithmValue == LawnchairSearchAlgorithm.ASI_SEARCH
    val isLocalSearch = searchAlgorithmValue == LawnchairSearchAlgorithm.LOCAL_SEARCH
    val autoShowKeyboardInDrawerAdapter = prefs2.autoShowKeyboardInDrawer.getAdapter()
    val matchHotseatQsbStyleAdapter = prefs2.matchHotseatQsbStyle.getAdapter()
    val searchResultShortcutsAdapter = prefs2.searchResultShortcuts.getAdapter()
    val searchResultPeopleAdapter = prefs2.searchResultPeople.getAdapter()
    val searchResultPixelTipsAdapter = prefs2.searchResultPixelTips.getAdapter()
    val searchResultSettingsAdapter = prefs2.searchResultSettings.getAdapter()
    val searchResultStartPageSuggestionAdapter = prefs2.searchResultStartPageSuggestion.getAdapter()
    val searchResultSettingsEntryAdapter = prefs2.searchResultSettingsEntry.getAdapter()
    val searchResulRecentSuggestionAdapter = prefs2.searchResulRecentSuggestion.getAdapter()
    val searchResultCalculatorAdapter = prefs2.searchResultCalculator.getAdapter()

    // Folders state
    val showIconLabelsOnHomeScreenFolderAdapterObj = prefs2.showIconLabelsOnHomeScreenFolder.getAdapter()
    val folderShowLabels = showIconLabelsOnHomeScreenFolderAdapterObj.state.value

    // Quickstep state
    val showQuickstep = LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG
    val lensAvailable = remember {
        context.packageManager.getLaunchIntentForPackage("com.google.ar.lens") != null
    }
    val recentsTranslucentBackgroundAdapter = prefs2.recentsTranslucentBackground.getAdapter()
    val recentsTranslucentBackground = recentsTranslucentBackgroundAdapter.state.value
    val recentsActionScreenshotAdapter = prefs2.recentsActionScreenshot.getAdapter()
    val recentsActionShareAdapter = prefs2.recentsActionShare.getAdapter()
    val recentsActionLensAdapter = prefs2.recentsActionLens.getAdapter()
    val recentsActionLockedAdapter = prefs2.recentsActionLocked.getAdapter()
    val recentsActionClearAllAdapter = prefs2.recentsActionClearAll.getAdapter()
    val overrideWindowCornerRadiusAdapter = prefs2.overrideWindowCornerRadius.getAdapter()
    val overrideWindowCornerRadius = overrideWindowCornerRadiusAdapter.state.value
    val enableTaskbarOnPhoneAdapter = prefs2.enableTaskbarOnPhone.getAdapter()

    // Extras & experimental state
    val enableDebug by prefs2.enableDebugMenu.asState()
    val workspaceIncreaseMaxGridSizeAdapter = prefs2.workspaceIncreaseMaxGridSize.getAdapter()
    val enableGncAdapter = prefs2.enableGnc.getAdapter()

    val enableWallpaperBlurAdapter = prefs2.enableWallpaperBlur.getAdapter()
    val wallpaperBlurFileAccessManager = remember { FileAccessManager.getInstance(context) }
    val wallpaperBlurAllFilesAccessState by wallpaperBlurFileAccessManager.allFilesAccessState.collectAsStateWithLifecycle()
    val wallpaperBlurWallpaperAccessState by wallpaperBlurFileAccessManager.wallpaperAccessState.collectAsStateWithLifecycle()
    val wallpaperBlurHasPermission = wallpaperBlurWallpaperAccessState != FileAccessState.Denied
    val canWallpaperBlur = wallpaperBlurHasPermission && enableWallpaperBlurAdapter.state.value
    var showWallpaperBlurPermissionDialog by remember { mutableStateOf(false) }
    val wallpaperBlurToggle = ToggleAction(
        checked = canWallpaperBlur,
    ) { checked ->
        if (!wallpaperBlurHasPermission) {
            showWallpaperBlurPermissionDialog = true
        } else {
            enableWallpaperBlurAdapter.onChange(checked)
        }
    }
    LifecycleResumeEffect(Unit) {
        showWallpaperBlurPermissionDialog = false
        wallpaperBlurFileAccessManager.refresh()
        onPauseOrDispose { }
    }

    val allEntries = buildList {
        fun g(
            label: String,
            kw: String = "",
            sk: String? = null,
            visible: Boolean = true,
            route: PreferenceRoute = General,
            breadcrumb: String = labelGeneral,
            toggle: ToggleAction? = null,
        ) {
            if (visible) add(SearchableEntry(label, kw, breadcrumb, R.drawable.ic_general, route, sk, toggle))
        }
        g(stringResource(R.string.theme_label), "theme light dark system appearance mode", ScrollKeys.THEME)
        g(stringResource(R.string.home_screen_rotation_label), "rotate allow home screen rotation", ScrollKeys.HOME_ROTATION, toggle = allowRotationAdapter.toToggle())
        g(stringResource(R.string.auto_updater_label), "updater nightly automatic updates", ScrollKeys.AUTO_UPDATER, visible = isNightlyBuild, toggle = autoUpdaterNightlyAdapter.toToggle())
        g(stringResource(R.string.live_information_label), "live information announcements news updater", ScrollKeys.LIVE_INFORMATION, toggle = liveInfoEnabledAdapter.toToggle())
        g(stringResource(R.string.fontWorkspace), "font customization general typography typeface", ScrollKeys.FONT_WORKSPACE, visible = fontSelectionEnabled)
        g(stringResource(R.string.fontHeading), "font customization headings typography typeface", ScrollKeys.FONT_HEADING, visible = fontSelectionEnabled)
        g(stringResource(R.string.fontHeadingMedium), "font customization heading medium typography typeface", ScrollKeys.FONT_HEADING_MEDIUM, visible = fontSelectionEnabled)
        g(stringResource(R.string.fontBody), "font customization body typography typeface", ScrollKeys.FONT_BODY, visible = fontSelectionEnabled)
        g(stringResource(R.string.fontBodyMedium), "font customization body medium typography typeface", ScrollKeys.FONT_BODY_MEDIUM, visible = fontSelectionEnabled)
        g(stringResource(R.string.icon_pack), "icon packs style apply theme", ScrollKeys.ICON_PACK)
        g(stringResource(R.string.themed_icon_title), "themed icons monochrome material you", ScrollKeys.THEMED_ICONS)
        g(stringResource(R.string.icon_shape_label), "circle square rounded squircle octagon teardrop shape", ScrollKeys.ICON_SHAPE)
        g(stringResource(R.string.advanced_icon_settings_title), "advanced icons adaptive shadow reload", ScrollKeys.ADVANCED_ICONS)
        g(stringResource(R.string.accent_color), "color picker tint accent custom", ScrollKeys.ACCENT_COLOR)
        g(stringResource(R.string.color_style_label), "tonal spot vibrant expressive material you dynamic", ScrollKeys.COLOR_STYLE, visible = showColorStyle)
        g(stringResource(R.string.color_spec_label), "color spec 2021 2025 2026 expressive", ScrollKeys.COLOR_SPEC, visible = showColorSpec)
        g(stringResource(R.string.notification_dots), "badge notification count dot", ScrollKeys.NOTIFICATION_DOTS)
        g(stringResource(R.string.notification_dots_color), "notification dot badge color", ScrollKeys.NOTIFICATION_DOT_COLOR, visible = canDisplayNotificationDot)
        g(stringResource(R.string.show_notification_count), "notification badge number count", ScrollKeys.NOTIFICATION_COUNT, visible = canDisplayNotificationDot, toggle = showNotificationCountAdapter.toToggle())
        g(stringResource(R.string.notification_dots_text_color), "notification dot text count color", ScrollKeys.NOTIFICATION_DOT_TEXT_COLOR, visible = canDisplayNotificationDot && showNotificationCount)
        g(stringResource(R.string.settings_blur_label), "blur wallpaper settings background", ScrollKeys.SETTINGS_BLUR, toggle = settingsBlurToggle)
        g(stringResource(R.string.settings_blur_intensity_label), "blur intensity strength settings background", ScrollKeys.SETTINGS_BLUR_INTENSITY, visible = settingsBlurAdapter.state.value)

        val breadcrumbThemedIcons = "$labelGeneral › $labelThemedIcons"
        g(stringResource(R.string.fallback_icon_source_title), "themed icons fallback icon pack lawnicons", ScrollKeys.FALLBACK_ICON_SOURCE, visible = themedIconsActive, route = GeneralThemedIcons, breadcrumb = breadcrumbThemedIcons)
        g(stringResource(R.string.enforce_themed_icons_title), "enforce monochrome themed icons unthemed", ScrollKeys.ENFORCE_THEMED_ICONS, visible = themedIconsActive, route = GeneralThemedIcons, breadcrumb = breadcrumbThemedIcons, toggle = forceIconMonochromeAdapter.toToggle())
        g(stringResource(R.string.transparent_background_icons_label), "transparent icon background themed adaptive", ScrollKeys.TRANSPARENT_ICON_BG, visible = themedIconsActive, route = GeneralThemedIcons, breadcrumb = breadcrumbThemedIcons, toggle = transparentIconBackgroundAdapter.toToggle())

        val breadcrumbAdvancedIcons = "$labelGeneral › $labelAdvancedIcons"
        g(stringResource(R.string.always_reload_icons_label), "always reload icons cache refresh icon pack", ScrollKeys.ALWAYS_RELOAD_ICONS, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons, toggle = alwaysReloadIconsAdapter.toToggle())
        g(stringResource(R.string.shadow_bg_icons_label), "shadow behind icons drop shadow", ScrollKeys.SHADOW_ICONS, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons, toggle = shadowBGIconsAdapter.toToggle())
        g(stringResource(R.string.reshape_unthemed_icons_title), "auto adaptive icons non-adaptive wrap background reshape", ScrollKeys.AUTO_ADAPTIVE, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons, toggle = wrapAdaptiveIconsAdapter.toToggle())
        g(stringResource(R.string.background_lightness_label), "background lightness adaptive icon", ScrollKeys.BACKGROUND_LIGHTNESS, visible = wrapAdaptiveIcons && !transparentIconBackground, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons)
        g(stringResource(R.string.colorized_backgrounds_label), "smart icon background color analyze pixel", ScrollKeys.COLORIZED_BG, visible = wrapAdaptiveIcons, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons, toggle = colorizedBackgroundsAdapter.toToggle())
        g(stringResource(R.string.treat_white_adaptive_icons_label), "colorize foreground only adaptive icons white", ScrollKeys.TREAT_WHITE_ADAPTIVE, visible = wrapAdaptiveIcons && colorizedBackgrounds, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons, toggle = treatWhiteAdaptiveIconsAdapter.toToggle())
        g(stringResource(R.string.colorize_icon_pack_background_label), "apply smart backgrounds to icon pack", ScrollKeys.COLORIZE_ICON_PACK_BG, visible = colorizedBackgrounds, route = GeneralAdvancedIcons, breadcrumb = breadcrumbAdvancedIcons, toggle = colorizeIconPackBackgroundAdapter.toToggle())

        fun h(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
            if (visible) add(SearchableEntry(label, kw, labelHomeScreen, R.drawable.ic_home_screen, HomeScreen, sk, toggle))
        }
        h(stringResource(R.string.layout), "home layout lawndeck default deck", ScrollKeys.HOME_LAYOUT, visible = showDeckLayoutAdapter.state.value)
        h(stringResource(R.string.auto_add_shortcuts_label), "add new apps home screen auto install", ScrollKeys.AUTO_ADD_SHORTCUTS, visible = !deckLayout.state.value, toggle = if (!lockHomeScreenAdapter.state.value) addIconToHomeAdapter.toToggle() else null)
        h(stringResource(R.string.gesture_double_tap), "double tap home screen gesture", ScrollKeys.HOME_DOUBLE_TAP)
        h(stringResource(R.string.infinite_scrolling_label), "loop pages wrap around infinite", ScrollKeys.INFINITE_SCROLLING, toggle = infiniteScrollingAdapter.toToggle())
        h(stringResource(R.string.pan_to_move_icon_label), "tilt pan move icon across pages motion sensor", ScrollKeys.HOME_PAN_TO_MOVE, visible = hasMotionSensor, toggle = panToMoveEnabledAdapter.toToggle())
        h(stringResource(R.string.pan_to_move_sensitivity_label), "tilt pan sensitivity motion sensor", ScrollKeys.HOME_PAN_TO_MOVE_SENSITIVITY, visible = hasMotionSensor && panToMoveEnabledAdapter.state.value)
        h(stringResource(R.string.remove_all_views_from_home_screen), "clear home screen remove all views", ScrollKeys.HOME_CLEAR)
        h(stringResource(R.string.minus_one_enable), "feed google discover news enable show", ScrollKeys.HOME_FEED, toggle = if (feedAvailableState) enableFeedAdapter.toToggle() else null)
        h(stringResource(R.string.feed_provider), "feed provider google discover news source", ScrollKeys.HOME_FEED_PROVIDER, visible = feedAvailableState && enableFeedState)
        h(stringResource(R.string.home_screen_text_color), "text color light dark workspace", ScrollKeys.HOME_TEXT_COLOR)
        h(stringResource(R.string.home_screen_icon_text_color), "icon text color workspace label", ScrollKeys.HOME_ICON_TEXT_COLOR)
        h(stringResource(R.string.app_opening_animation), "app opening animation reveal slide scale blink fade", ScrollKeys.HOME_APP_OPEN_ANIM)
        h(stringResource(R.string.app_closing_animation), "app closing animation overlay fade suck in", ScrollKeys.HOME_APP_CLOSE_ANIM)
        h(stringResource(R.string.wallpaper_scrolling_label), "scroll wallpaper parallax pan", ScrollKeys.WALLPAPER_SCROLL, toggle = wallpaperScrollingAdapter.toToggle())
        h(stringResource(R.string.wallpaper_depth_effect_label), "depth parallax zoom wallpaper effect", ScrollKeys.WALLPAPER_DEPTH, visible = Utilities.ATLEAST_R, toggle = wallpaperDepthEffectAdapter.toToggle())
        h(stringResource(R.string.show_sys_ui_scrim), "top shadow status bar scrim gradient", ScrollKeys.HOME_TOP_SHADOW, toggle = showTopShadowAdapter.toToggle())
        h(stringResource(R.string.home_screen_grid), "grid columns rows layout size change", ScrollKeys.HOME_GRID)
        h(stringResource(R.string.horizontal_padding_label), "horizontal padding home screen workspace", ScrollKeys.HOME_PADDING_HORIZONTAL)
        h(stringResource(R.string.vertical_padding_label), "vertical padding home screen workspace", ScrollKeys.HOME_PADDING_VERTICAL)
        h(stringResource(R.string.home_screen_lock), "lock home screen prevent changes layout edit", ScrollKeys.LOCK_HOME, toggle = lockHomeScreenAdapter.toToggle())
        h(stringResource(R.string.edit_menu_items), "edit popup menu items long press shortcuts actions", ScrollKeys.POPUP_MENU)
        h(stringResource(R.string.show_status_bar), "status bar show hide", ScrollKeys.STATUS_BAR, toggle = showStatusBarAdapter.toToggle())
        h(stringResource(R.string.dark_status_bar_label), "dark status bar light dark", ScrollKeys.HOME_DARK_STATUS_BAR, visible = showStatusBarState, toggle = darkStatusBarAdapter.toToggle())
        h(stringResource(R.string.status_bar_clock_label), "status bar clock time show hide", ScrollKeys.HOME_STATUS_BAR_CLOCK, visible = showStatusBarState && LawnchairApp.isRecentsEnabled, toggle = statusBarClockAdapter.toToggle())
        h(stringResource(R.string.icon_sizes), "icon size scale home screen icons", ScrollKeys.HOME_ICON_SIZE)
        h(stringResource(R.string.show_labels), "show labels app name home screen", ScrollKeys.HOME_SHOW_LABELS, toggle = homeScreenLabelsAdapterObj.toToggle())
        h(stringResource(R.string.label_size), "label size text size home screen", ScrollKeys.HOME_LABEL_SIZE, visible = homeScreenLabelsAdapter)
        h(stringResource(R.string.home_screen_two_line_label), "two line labels home screen", ScrollKeys.HOME_TWO_LINE, visible = homeScreenLabelsAdapter, toggle = twoLineHomeScreenAdapter.toToggle())
        h(stringResource(R.string.reset_custom_icons), "reset custom icons overrides restore default", ScrollKeys.HOME_RESET_CUSTOM_ICONS, visible = customIconsCount > 0)
        h(stringResource(R.string.force_rounded_widgets), "rounded widgets corner radius", ScrollKeys.HOME_ROUNDED_WIDGETS, toggle = roundedWidgetsAdapter.toToggle())
        h(stringResource(R.string.allow_widget_overlap), "widget overlap allow", ScrollKeys.HOME_WIDGET_OVERLAP, toggle = allowWidgetOverlapAdapter.toToggle())
        h(stringResource(R.string.widget_unlimited_size_label), "widget unlimited size remove constraints", ScrollKeys.HOME_WIDGET_UNLIMITED, toggle = widgetUnlimitedSizeAdapter.toToggle())
        h(stringResource(R.string.force_widget_resize_label), "widget resize enforce resizable", ScrollKeys.HOME_WIDGET_RESIZE, toggle = forceWidgetResizeAdapter.toToggle())
        h(stringResource(R.string.widget_padding_label), "widget padding spacing", ScrollKeys.HOME_WIDGET_PADDING)

        val smartIcon = if (smartspaceEnabledReactive) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off
        fun s(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
            if (visible) add(SearchableEntry(label, kw, labelSmartspace, smartIcon, Smartspace, sk, toggle))
        }
        s(stringResource(R.string.smartspace_widget_toggle_label), "at a glance smartspace enable show toggle", toggle = enableSmartspaceAdapter.toToggle())
        s(stringResource(R.string.smartspace_mode_label), "provider google smartspacer lawnchair mode", ScrollKeys.SS_MODE, visible = smartspaceEnabledReactive)
        s(stringResource(R.string.smartspace_weather_source), "weather temperature forecast source provider open-meteo pirate openweathermap accuweather", ScrollKeys.SS_WEATHER_SOURCE, visible = smartspaceLawnchairActive)
        s(
            stringResource(R.string.smartspace_pirate_weather_api_key),
            "api key pirateweather",
            ScrollKeys.SS_API_KEY,
            visible = smartspaceLawnchairActive && weatherProviderValue == WeatherProvider.PIRATE_WEATHER,
        )
        s(
            stringResource(R.string.smartspace_owm_api_key),
            "api key openweathermap",
            ScrollKeys.SS_API_KEY,
            visible = smartspaceLawnchairActive && weatherProviderValue == WeatherProvider.OPEN_WEATHER_MAP,
        )
        s(
            stringResource(R.string.smartspace_accu_api_key),
            "api key accuweather",
            ScrollKeys.SS_API_KEY,
            visible = smartspaceLawnchairActive && weatherProviderValue == WeatherProvider.ACCU_WEATHER,
        )
        s(stringResource(R.string.smartspace_weather_city), "city location weather gps auto", ScrollKeys.SS_WEATHER_CITY, visible = smartspaceLawnchairActive && hasWeatherSource)
        s(stringResource(R.string.smartspace_weather_unit), "temperature unit celsius fahrenheit kelvin", ScrollKeys.SS_WEATHER_UNIT, visible = smartspaceLawnchairActive && hasWeatherSource)
        s(stringResource(R.string.smartspace_weather_icon_pack), "weather icon pack breezy", ScrollKeys.SS_WEATHER_ICON_PACK, visible = smartspaceLawnchairActive && hasWeatherSource)
        s(stringResource(R.string.smartspace_weather_refresh_interval), "weather refresh interval", ScrollKeys.SS_WEATHER_INTERVAL, visible = smartspaceLawnchairActive && hasWeatherSource)
        s(stringResource(R.string.smartspace_weather_refresh_now), "weather refresh now manual", ScrollKeys.SS_WEATHER_REFRESH_NOW, visible = smartspaceLawnchairActive && hasWeatherSource)
        s(stringResource(R.string.smartspace_weather), "weather google widget at a glance", ScrollKeys.SS_WEATHER_WIDGET, visible = weatherWidgetAdapter != null, toggle = weatherWidgetAdapter?.toToggle())
        s(stringResource(R.string.smartspace_battery_status), "battery charging status level indicator", ScrollKeys.SS_BATTERY_STATUS, visible = batteryStatusAdapter != null, toggle = batteryStatusAdapter?.toToggle())
        s(stringResource(R.string.smartspace_torch), "flashlight status torch", ScrollKeys.SS_FLASHLIGHT, visible = torchAdapter != null, toggle = torchAdapter?.toToggle())
        s(stringResource(R.string.smartspace_now_playing), "now playing music media track song", ScrollKeys.SS_NOW_PLAYING, visible = nowPlayingAdapter != null, toggle = nowPlayingAdapter?.toToggle())
        s(stringResource(R.string.smartspace_onboarding), "onboarding setup at a glance", ScrollKeys.SS_ONBOARDING, visible = onboardingAdapter != null, toggle = onboardingAdapter?.toToggle())
        s(stringResource(R.string.smartspace_personality), "greetings personality good morning evening night message", ScrollKeys.SS_PERSONALITY, visible = personalityAdapter != null, toggle = personalityAdapter?.toToggle())
        s(
            stringResource(R.string.smartspace_date),
            "date show hide",
            ScrollKeys.SS_DATE,
            visible = smartspaceLawnchairActive && supportsCalendarCustomization,
            toggle = if (!smartspaceShowDate || !smartspaceHasMinimumContent) smartspaceShowDateAdapter.toToggle() else null,
        )
        s(stringResource(R.string.smartspace_calendar), "calendar gregorian persian lunar system", ScrollKeys.SS_CALENDAR, visible = smartspaceLawnchairActive && supportsCalendarCustomization && smartspaceShowDate)
        s(
            stringResource(R.string.smartspace_time),
            "time show hide clock",
            ScrollKeys.SS_TIME,
            visible = smartspaceLawnchairActive && supportsCalendarCustomization,
            toggle = if (!smartspaceShowTime || !smartspaceHasMinimumContent) smartspaceShowTimeAdapter.toToggle() else null,
        )
        s(stringResource(R.string.smartspace_time_format), "time format 12h 24h", ScrollKeys.SS_TIME_FORMAT, visible = smartspaceLawnchairActive && supportsCalendarCustomization && smartspaceShowTime)
        s(stringResource(R.string.maximum_number_of_targets), "smartspacer targets maximum count", ScrollKeys.SS_SMARTSPACER_MAX_TARGETS, visible = smartspaceEnabledReactive && isSmartspacerMode)
        s(stringResource(R.string.open_smartspacer_settings), "smartspacer settings open app", ScrollKeys.SS_SMARTSPACER_OPEN_SETTINGS, visible = smartspaceEnabledReactive && isSmartspacerMode)

        fun d(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
            if (visible) add(SearchableEntry(label, kw, labelDock, R.drawable.ic_dock, Dock, sk, toggle))
        }
        d(stringResource(R.string.show_hotseat_title), "show dock hotseat enable toggle", toggle = isHotseatEnabledAdapter.toToggle())
        d(stringResource(R.string.hotseat_background), "dock background show hide", ScrollKeys.DOCK_BG, visible = isHotseatEnabled, toggle = hotseatBgAdapter.toToggle())
        d(stringResource(R.string.hotseat_bg_color_label), "background color dock hotseat", ScrollKeys.DOCK_BG_COLOR, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.hotseat_bg_corner_radius), "corner radius dock background hotseat rounded", ScrollKeys.DOCK_BG_CORNER_RADIUS, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.hotseat_bg_alpha), "background opacity dock hotseat", ScrollKeys.DOCK_BG_OPACITY, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.hotseat_bg_horizontal_inset_left), "left margin dock background", ScrollKeys.DOCK_BG_LEFT_MARGIN, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.hotseat_bg_horizontal_inset_right), "right margin dock background", ScrollKeys.DOCK_BG_RIGHT_MARGIN, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.hotseat_bg_vertical_inset_top), "top margin dock background", ScrollKeys.DOCK_BG_TOP_MARGIN, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.hotseat_bg_vertical_inset_bottom), "bottom margin dock background", ScrollKeys.DOCK_BG_BOTTOM_MARGIN, visible = isHotseatEnabled && hotseatBgEnabled)
        d(stringResource(R.string.search_bar_settings), "search bar dock settings", ScrollKeys.DOCK_SEARCH, visible = isHotseatEnabled)
        d(stringResource(R.string.dock_icons), "dock icon count columns hotseat number", ScrollKeys.DOCK_ICONS, visible = isHotseatEnabled)
        d(stringResource(R.string.hotseat_bottom_space_label), "bottom padding spacing dock margin", ScrollKeys.DOCK_BOTTOM_SPACE, visible = isHotseatEnabled)
        d(stringResource(R.string.page_indicator_height), "page indicator dots height size", ScrollKeys.DOCK_PAGE_INDICATOR, visible = isHotseatEnabled)
        d(stringResource(R.string.dock_rows), "dock rows hotseat count", ScrollKeys.DOCK_ROWS, visible = isHotseatEnabled)
        d(stringResource(R.string.dock_pages), "dock pages hotseat scrollable", ScrollKeys.DOCK_PAGES, visible = isHotseatEnabled)
        d(stringResource(R.string.show_labels), "dock labels show hide", ScrollKeys.DOCK_SHOW_LABELS, visible = isHotseatEnabled, toggle = enableLabelInDockAdapter.toToggle())
        d(stringResource(R.string.dock_two_line_label), "two line label dock", ScrollKeys.DOCK_TWO_LINE, visible = isHotseatEnabled && enableLabelInDockAdapter.state.value, toggle = twoLineDockAdapter.toToggle())

        if (!deckLayout.state.value) {
            fun a(
                label: String,
                kw: String = "",
                sk: String? = null,
                visible: Boolean = true,
                route: PreferenceRoute = AppDrawer,
                breadcrumb: String = labelAppDrawer,
                toggle: ToggleAction? = null,
            ) {
                if (visible) add(SearchableEntry(label, kw, breadcrumb, R.drawable.ic_apps, route, sk, toggle))
            }
            a(stringResource(R.string.layout), "app drawer layout caddy default categories", ScrollKeys.DRAWER_LAYOUT)
            a(stringResource(R.string.app_drawer_folder), "app drawer folders layout", ScrollKeys.DRAWER_FOLDERS, visible = drawerListEnabled)
            a(stringResource(R.string.hidden_apps_label), "hide apps from drawer hidden list", ScrollKeys.HIDDEN_APPS)
            a(stringResource(R.string.search_bar_settings), "app drawer search bar settings", ScrollKeys.DRAWER_SEARCH_ENTRY)
            a(stringResource(R.string.pref_app_drawer_search_bar_at_bottom), "search bar at bottom app drawer", ScrollKeys.DRAWER_SEARCH_AT_BOTTOM, toggle = appDrawerSearchBarAtBottomAdapter.toToggle())
            a(stringResource(R.string.suggestion_pref_screen_title), "suggestions predictions apps content", ScrollKeys.DRAWER_SUGGESTIONS)
            a(stringResource(R.string.app_drawer_haptic_feedback_label), "haptic feedback vibration app drawer", ScrollKeys.DRAWER_HAPTIC_FEEDBACK, visible = drawerHapticSupported, toggle = appDrawerHapticFeedbackAdapter.toToggle())
            a(stringResource(R.string.app_drawer_bg_color_label), "background color app drawer", ScrollKeys.DRAWER_BG_COLOR)
            a(stringResource(R.string.background_opacity), "background opacity app drawer", ScrollKeys.DRAWER_BG_OPACITY)
            a(stringResource(R.string.work_profile_tab_background_label), "tab background color work profile app drawer", ScrollKeys.DRAWER_TAB_BG_COLOR)
            a(stringResource(R.string.work_profile_tab_container_background_label), "show background behind tabs work profile", ScrollKeys.DRAWER_TAB_CONTAINER_BG, toggle = workProfileTabContainerBackgroundAdapter.toToggle())
            a(stringResource(R.string.pref_all_apps_search_bar_background), "show background behind search bar app drawer", ScrollKeys.DRAWER_SEARCH_BAR_BG, toggle = appDrawerSearchBarBackgroundAdapter.toToggle())
            a(stringResource(R.string.drawer_icon_text_color), "text color app drawer icon label", ScrollKeys.DRAWER_TEXT_COLOR)
            a(stringResource(R.string.drawer_hoko_blur_label), "blur app drawer background wallpaper", ScrollKeys.DRAWER_BLUR, toggle = drawerBlurToggle)
            a(stringResource(R.string.drawer_hoko_blur_intensity_label), "blur intensity strength app drawer", ScrollKeys.DRAWER_BLUR_INTENSITY, visible = drawerBlurBackgroundAdapter.state.value)
            a(stringResource(R.string.app_drawer_columns), "columns grid app drawer layout count", ScrollKeys.DRAWER_COLUMNS)
            a(stringResource(R.string.row_height_label), "row height size spacing compact", ScrollKeys.DRAWER_ROW_HEIGHT)
            a(stringResource(R.string.app_drawer_indent_label), "horizontal padding indent margin spacing", ScrollKeys.DRAWER_INDENT)
            a(stringResource(R.string.top_padding_label), "top padding app drawer", ScrollKeys.DRAWER_TOP_PADDING)
            a(stringResource(R.string.icon_sizes), "icon size scale app drawer icons", ScrollKeys.DRAWER_ICON_SIZE)
            a(stringResource(R.string.show_labels), "show labels app drawer", ScrollKeys.DRAWER_SHOW_LABELS, toggle = showIconLabelsInDrawerAdapterObj.toToggle())
            a(stringResource(R.string.label_size), "label size app drawer", ScrollKeys.DRAWER_LABEL_SIZE, visible = showDrawerLabels)
            a(stringResource(R.string.twoline_label), "use multiple lines app drawer labels", ScrollKeys.DRAWER_TWO_LINE, visible = showDrawerLabels, toggle = twoLineAllAppsAdapter.toToggle())
            a(stringResource(R.string.pref_all_apps_remember_position_title), "remember position scroll app drawer keep", ScrollKeys.DRAWER_REMEMBER, toggle = rememberPositionAdapter.toToggle())
            a(stringResource(R.string.pref_all_apps_show_scrollbar_title), "scrollbar show hide fast scroll", ScrollKeys.DRAWER_SCROLLBAR, toggle = showScrollbarAdapter.toToggle())

            val breadcrumbPredictions = "$labelAppDrawer › $labelSuggestions"
            a(stringResource(R.string.global_predictions_label), "predictions suggestions enable toggle", route = Predictions, breadcrumb = breadcrumbPredictions, toggle = enableGlobalPredictionAdapter.toToggle())
            a(stringResource(R.string.prediction_mode_label), "prediction mode provider system lawnchair", ScrollKeys.PREDICTION_MODE, visible = globalPredictionEnabled, route = Predictions, breadcrumb = breadcrumbPredictions)
            a(stringResource(R.string.prediction_weighted_usage_stats_label), "weighted usage stats predictions", ScrollKeys.PREDICTION_WEIGHTED_USAGE, visible = globalPredictionEnabled && predictionModeValue == LawnchairPredictor, route = Predictions, breadcrumb = breadcrumbPredictions)
            a(stringResource(R.string.dismissed_prediction_apps_label), "dismissed prediction apps hidden suggestions", ScrollKeys.PREDICTION_DISMISSED_APPS, visible = globalPredictionEnabled && predictionModeValue == LawnchairPredictor, route = Predictions, breadcrumb = breadcrumbPredictions)
        }

        fun sd(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
            if (visible) add(SearchableEntry(label, kw, "$labelSearchBar › $labelDock", R.drawable.ic_search, Search(SearchRoute.DOCK_SEARCH), sk, toggle))
        }
        fun sa(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
            if (visible) add(SearchableEntry(label, kw, "$labelSearchBar › $labelAppDrawer", R.drawable.ic_search, Search(SearchRoute.DRAWER_SEARCH), sk, toggle))
        }
        sd(stringResource(R.string.hotseat_mode_label), "search bar widget google lawnchair disabled dock mode", ScrollKeys.DOCK_SEARCH_MODE, visible = isHotseatEnabled)
        sd(stringResource(R.string.search_provider), "search engine google duckduckgo bing startpage dock", ScrollKeys.DOCK_SEARCH_PROVIDER, visible = isHotseatEnabled && isLawnchairHotseat)
        sd(stringResource(R.string.apply_accent_color_label), "accent color tint dock search bar", ScrollKeys.DOCK_SEARCH_ACCENT, visible = isHotseatEnabled && isLawnchairHotseat, toggle = themedHotseatQsbAdapter.toToggle())
        sd(stringResource(R.string.corner_radius_label), "corner radius dock search bar rounded", ScrollKeys.DOCK_SEARCH_RADIUS, visible = isHotseatEnabled && isLawnchairHotseat)
        sd(stringResource(R.string.qsb_hotseat_background_transparency), "search bar background opacity transparent dock", ScrollKeys.DOCK_SEARCH_OPACITY, visible = isHotseatEnabled && isLawnchairHotseat)
        sd(stringResource(R.string.qsb_hotseat_stroke_width), "outline width dock search bar stroke", ScrollKeys.DOCK_SEARCH_STROKE_WIDTH, visible = isHotseatEnabled && isLawnchairHotseat)
        sd(stringResource(R.string.qsb_hotseat_stroke_color), "outline color dock search bar stroke", ScrollKeys.DOCK_SEARCH_STROKE_COLOR, visible = isHotseatEnabled && isLawnchairHotseat && hotseatQsbStrokeWidth > 0f)

        sa(stringResource(R.string.show_app_search_bar), "show app drawer search bar toggle", toggle = showDrawerSearchBarAdapter.toToggle())
        sa(stringResource(R.string.show_hidden_apps_in_search_results), "show hidden apps in search results drawer", ScrollKeys.DS_HIDDEN_APPS, visible = showDrawerSearchBarEnabled && hiddenApps.isNotEmpty())
        sa(stringResource(R.string.pref_search_auto_show_keyboard), "auto keyboard search show automatically", ScrollKeys.DS_AUTO_KEYBOARD, visible = showDrawerSearchBarEnabled, toggle = autoShowKeyboardInDrawerAdapter.toToggle())
        sa(stringResource(R.string.app_search_algorithm), "algorithm global search on-device ASI app search", ScrollKeys.DS_ALGORITHM, visible = showDrawerSearchBarEnabled)
        sa(stringResource(R.string.allapps_match_qsb_style_label), "match dock search bar actions style", ScrollKeys.DS_MATCH_QSB, visible = showDrawerSearchBarEnabled, toggle = matchHotseatQsbStyleAdapter.toToggle())
        sa(stringResource(R.string.search_pref_result_apps_and_shortcuts_title), "apps and shortcuts search results", ScrollKeys.DS_APPS_SHORTCUTS, visible = showDrawerSearchBarEnabled && !isAsiSearch)
        sa(stringResource(R.string.search_pref_result_shortcuts_title), "shortcuts search results asi", ScrollKeys.DS_SHORTCUTS, visible = showDrawerSearchBarEnabled && isAsiSearch, toggle = searchResultShortcutsAdapter.toToggle())
        sa(stringResource(R.string.search_pref_result_web_title), "web suggestions search results", ScrollKeys.DS_WEB, visible = showDrawerSearchBarEnabled && isLocalSearch, toggle = searchResultStartPageSuggestionAdapter.toToggle())
        sa(stringResource(R.string.search_pref_result_people_title), "contacts people search results", ScrollKeys.DS_PEOPLE, visible = showDrawerSearchBarEnabled && (isAsiSearch || isLocalSearch), toggle = if (isAsiSearch) searchResultPeopleAdapter.toToggle() else null)
        sa(stringResource(R.string.search_pref_result_tips_title), "pixel tips search results asi", ScrollKeys.DS_TIPS, visible = showDrawerSearchBarEnabled && isAsiSearch, toggle = searchResultPixelTipsAdapter.toToggle())
        sa(stringResource(R.string.search_pref_result_files_title), "files search results", ScrollKeys.DS_FILES, visible = showDrawerSearchBarEnabled && isLocalSearch)
        sa(stringResource(R.string.search_pref_result_settings_title), "android settings search results", ScrollKeys.DS_SETTINGS, visible = showDrawerSearchBarEnabled && (isAsiSearch || isLocalSearch), toggle = if (isAsiSearch) searchResultSettingsAdapter.toToggle() else searchResultSettingsEntryAdapter.toToggle())
        sa(stringResource(R.string.search_pref_result_history_title), "search history results", ScrollKeys.DS_HISTORY, visible = showDrawerSearchBarEnabled && isLocalSearch, toggle = searchResulRecentSuggestionAdapter.toToggle())
        sa(stringResource(R.string.all_apps_search_result_calculator), "calculator search", ScrollKeys.DS_CALCULATOR, visible = showDrawerSearchBarEnabled && isLocalSearch, toggle = searchResultCalculatorAdapter.toToggle())

        fun f(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
            if (visible) add(SearchableEntry(label, kw, labelFolders, R.drawable.ic_folder, Folders, sk, toggle))
        }
        f(stringResource(R.string.folder_shape_label), "folder shape icon shape style", ScrollKeys.FOLDER_SHAPE)
        f(stringResource(R.string.folder_preview_bg_color_label), "icon background color folder preview", ScrollKeys.FOLDER_ICON_BG_COLOR)
        f(stringResource(R.string.folder_preview_bg_opacity_label), "folder icon preview background opacity", ScrollKeys.FOLDER_PREVIEW_OPACITY)
        f(stringResource(R.string.folder_bg_opacity_label), "folder background opacity transparency", ScrollKeys.FOLDER_BG_OPACITY)
        f(stringResource(R.string.max_folder_columns), "folder columns maximum grid count", ScrollKeys.FOLDER_MAX_COLUMNS)
        f(stringResource(R.string.max_folder_rows), "folder rows maximum grid count", ScrollKeys.FOLDER_MAX_ROWS)
        f(stringResource(R.string.show_labels), "show labels folder", ScrollKeys.FOLDER_SHOW_LABELS, toggle = showIconLabelsOnHomeScreenFolderAdapterObj.toToggle())
        f(stringResource(R.string.label_size), "label size folder", ScrollKeys.FOLDER_LABEL_SIZE, visible = folderShowLabels)

        fun ge(label: String, kw: String = "", sk: String? = null) = add(SearchableEntry(label, kw, labelGestures, R.drawable.ic_gestures, Gestures, sk))
        ge(stringResource(R.string.gesture_double_tap), "double tap action sleep lock screen", ScrollKeys.GESTURE_DOUBLE_TAP)
        ge(stringResource(R.string.gesture_swipe_up), "swipe up drawer recents app", ScrollKeys.GESTURE_SWIPE_UP)
        ge(stringResource(R.string.gesture_swipe_down), "swipe down notifications quick settings panel", ScrollKeys.GESTURE_SWIPE_DOWN)
        ge(stringResource(R.string.gesture_two_finger_swipe_up), "two finger swipe up recents", ScrollKeys.GESTURE_2F_UP)
        ge(stringResource(R.string.gesture_two_finger_swipe_down), "two finger swipe down notifications", ScrollKeys.GESTURE_2F_DOWN)
        ge(stringResource(R.string.gesture_home_tap), "home button tap gesture action", ScrollKeys.GESTURE_HOME)
        ge(stringResource(R.string.gesture_back_tap), "back button tap gesture action", ScrollKeys.GESTURE_BACK)
        ge(stringResource(R.string.sleep_mode_label), "sleep mode lock screen accessibility root admin", ScrollKeys.GESTURE_SLEEP_MODE)

        if (showQuickstep) {
            fun q(label: String, kw: String = "", sk: String? = null, visible: Boolean = true, toggle: ToggleAction? = null) {
                if (visible) add(SearchableEntry(label, kw, labelQuickstep, R.drawable.ic_quickstep, Quickstep, sk, toggle))
            }
            q(stringResource(R.string.translucent_background), "recents translucent transparent background", ScrollKeys.QS_TRANSLUCENT, toggle = recentsTranslucentBackgroundAdapter.toToggle())
            q(stringResource(R.string.translucent_background_alpha), "recents background opacity transparency alpha", ScrollKeys.QS_TRANSLUCENT_ALPHA, visible = recentsTranslucentBackground)
            q(stringResource(R.string.action_screenshot), "recents quick actions screenshot", ScrollKeys.QS_RECENTS_ACTIONS, visible = !isOnePlusStock, toggle = recentsActionScreenshotAdapter.toToggle())
            q(stringResource(R.string.action_share), "recents quick actions share", ScrollKeys.QS_RECENTS_ACTIONS, toggle = recentsActionShareAdapter.toToggle())
            q(stringResource(R.string.action_lens), "recents quick actions google lens", ScrollKeys.QS_RECENTS_ACTIONS, visible = lensAvailable, toggle = recentsActionLensAdapter.toToggle())
            q(stringResource(R.string.recents_lock_unlock), "recents quick actions lock unlock", ScrollKeys.QS_RECENTS_ACTIONS, toggle = recentsActionLockedAdapter.toToggle())
            q(stringResource(R.string.recents_clear_all), "recents quick actions clear all", ScrollKeys.QS_RECENTS_ACTIONS, toggle = recentsActionClearAllAdapter.toToggle())
            q(stringResource(R.string.override_window_corner_radius_label), "override window corner radius recents", ScrollKeys.QS_CORNER_RADIUS, toggle = overrideWindowCornerRadiusAdapter.toToggle())
            q(stringResource(R.string.window_corner_radius_label), "window corner radius size recents", ScrollKeys.QS_CORNER_RADIUS_VALUE, visible = overrideWindowCornerRadius)
            q(stringResource(R.string.enable_taskbar_experimental), "taskbar phone quickstep experimental", ScrollKeys.QS_TASKBAR, visible = Utilities.ATLEAST_S_V2, toggle = enableTaskbarOnPhoneAdapter.toToggle())
        }

        fun b(label: String, kw: String = "", sk: String? = null) = add(SearchableEntry(label, kw, labelBackup, R.drawable.backup_restore, BackupAndRestore, sk))
        b(stringResource(R.string.create_backup), "export backup save layout settings create", ScrollKeys.CREATE_BACKUP)
        b(stringResource(R.string.restore_backup), "import restore backup load file", ScrollKeys.RESTORE_BACKUP)
        b(stringResource(R.string.restore_nova_backup), "import restore nova launcher backup", ScrollKeys.RESTORE_NOVA_BACKUP)

        fun e(
            label: String,
            kw: String = "",
            sk: String? = null,
            visible: Boolean = true,
            route: PreferenceRoute = Extras,
            breadcrumb: String = labelExtras,
            toggle: ToggleAction? = null,
        ) {
            if (visible) add(SearchableEntry(label, kw, breadcrumb, R.drawable.ic_extras, route, sk, toggle))
        }
        e(stringResource(R.string.experimental_features_label), "experimental features labs", ScrollKeys.EXPERIMENTAL)
        e(stringResource(R.string.debug_menu_label), "debug menu developer flags", ScrollKeys.DEBUG_MENU, visible = enableDebug)
        e(stringResource(R.string.debug_restart_launcher), "restart lawnchair launcher reboot", ScrollKeys.RESTART)
        e(stringResource(R.string.app_info_drop_target_label), "app info settings details", ScrollKeys.APP_INFO)

        val breadcrumbExperimental = "$labelExtras › $labelExperimental"
        e(stringResource(R.string.font_picker_label), "font customization typography typeface heading body weight", ScrollKeys.FONT_PICKER, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental, toggle = enableFontSelectionAdapter.toToggle())
        e(stringResource(R.string.workspace_increase_max_grid_size_label), "max grid size 20x20 increase workspace", ScrollKeys.MAX_GRID_SIZE, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental, toggle = workspaceIncreaseMaxGridSizeAdapter.toToggle())
        e(stringResource(R.string.show_deck_layout), "deck layout drawerless no app drawer all apps home", ScrollKeys.DECK_LAYOUT, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental, toggle = showDeckLayoutAdapter.toToggle())
        e(stringResource(R.string.wallpaper_blur), "blur wallpaper background frosted experimental", ScrollKeys.EXP_WALLPAPER_BLUR, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental, toggle = wallpaperBlurToggle)
        e(stringResource(R.string.wallpaper_background_blur), "wallpaper blur amount intensity strength", ScrollKeys.EXP_WALLPAPER_BLUR_AMOUNT, visible = canWallpaperBlur, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental)
        e(stringResource(R.string.wallpaper_background_blur_factor), "wallpaper blur factor threshold", ScrollKeys.EXP_WALLPAPER_BLUR_FACTOR, visible = canWallpaperBlur, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental)
        e(stringResource(R.string.gesturenavcontract_label), "gesturenavcontract api gesture navigation enhanced animation", ScrollKeys.GNC, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental, toggle = if (Utilities.ATLEAST_Q) enableGncAdapter.toToggle() else null)
        e(stringResource(R.string.vibration_feedback_level_label), "haptic vibration feedback level msdl", ScrollKeys.VIBRATION_FEEDBACK_LEVEL, route = ExperimentalFeatures, breadcrumb = breadcrumbExperimental)

        fun ab(label: String, kw: String = "", sk: String? = null) = add(SearchableEntry(label, kw, labelAbout, R.drawable.ic_about, About, sk))
        ab(stringResource(R.string.news), "news announcements updates channel")
        ab(stringResource(R.string.support), "support help faq")
        ab(stringResource(R.string.github), "github source code repository")
        ab(stringResource(R.string.translate), "translate crowdin localization")
        ab(stringResource(R.string.donate), "donate open collective sponsor")
        ab(stringResource(R.string.telegram), "telegram community chat news channel")
        ab(stringResource(R.string.discord), "discord community server chat")
        ab(stringResource(R.string.x_twitter), "x twitter social")
        ab(stringResource(R.string.acknowledgements), "acknowledgements credits libraries third party")
        ab(stringResource(R.string.privacy_policy), "privacy policy data collection")
    }

    val isDebugBuild = BuildConfig.APPLICATION_ID.contains("nightly") || BuildConfig.DEBUG
    var showDebugDialog by remember { mutableStateOf(false) }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Debug Build") },
            text = {
                Text("You are using a development build, which may contain bugs and broken features. Use at your own risk!")
            },
            confirmButton = {
                Button(
                    onClick = { showDebugDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    Box(modifier = modifier.fillMaxSize()) {
        PreferenceLayout(
            label = settingsLabel,
            expandedLabel = expandedLabel,
            onExpandedTitleClick = onExpandedTitleClick,
            verticalArrangement = Arrangement.Top,
            backArrowVisible = false,
            actions = {
                if (isDebugBuild) {
                    DebugBadge(onClick = { showDebugDialog = true })
                }
            },
        ) {
            AnimatedVisibility(
                visible = announcementShowing,
                enter = expandVertically(tween(350)) + fadeIn(tween(350)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
            ) {
                AnnouncementPreference()
            }

            AnimatedVisibility(
                visible = isNotDefaultLauncher && !announcementShowing,
                enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
            ) {
                PreferencesSetDefaultLauncherCard()
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSearchBar(
                onActivate = { searchActive = true },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .onGloballyPositioned { coords ->
                        searchBarOffsetY = coords.positionInRoot().y.toInt()
                    },
            )

            Spacer(modifier = Modifier.height(6.dp))

            val isDarkTheme = isSelectedThemeDark
            val showAppDrawerCategory = !deckLayout.state.value
            val showQuickstepCategory = LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG

            PreferenceGroup {
                DashboardCategory(
                    label = labelGeneral,
                    description = descGeneral,
                    iconResource = R.drawable.ic_general,
                    onNavigate = { onNavigate(General) },
                    isSelected = currentRoute is General,
                    shapes = segmentedItemShapes(index = 0, count = 5),
                    isDarkTheme = isDarkTheme,
                )
                DashboardCategory(
                    label = labelHomeScreen,
                    description = descHomeScreen,
                    iconResource = R.drawable.ic_home_screen,
                    onNavigate = { onNavigate(HomeScreen) },
                    isSelected = currentRoute is HomeScreen,
                    shapes = segmentedItemShapes(index = 1, count = 5),
                    isDarkTheme = isDarkTheme,
                )
                DashboardCategory(
                    label = labelSmartspace,
                    description = descSmartspace,
                    iconResource = if (isSmartspaceEnabled) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off,
                    onNavigate = { onNavigate(Smartspace) },
                    isSelected = currentRoute is Smartspace,
                    shapes = segmentedItemShapes(index = 2, count = 5),
                    isDarkTheme = isDarkTheme,
                )
                DashboardCategory(
                    label = labelDock,
                    description = descDock,
                    iconResource = R.drawable.ic_dock,
                    onNavigate = { onNavigate(Dock) },
                    isSelected = currentRoute is Dock,
                    shapes = segmentedItemShapes(index = 3, count = 5),
                    isDarkTheme = isDarkTheme,
                )
                DashboardCategory(
                    label = labelFolders,
                    description = descFolders,
                    iconResource = R.drawable.ic_folder,
                    onNavigate = { onNavigate(Folders) },
                    isSelected = currentRoute is Folders,
                    shapes = segmentedItemShapes(index = 4, count = 5),
                    isDarkTheme = isDarkTheme,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val appsAndSearchCount = if (showAppDrawerCategory) 2 else 1
            PreferenceGroup {
                ExpandAndShrink(visible = showAppDrawerCategory) {
                    DashboardCategory(
                        label = labelAppDrawer,
                        description = descAppDrawer,
                        iconResource = R.drawable.ic_apps,
                        onNavigate = { onNavigate(AppDrawer) },
                        isSelected = currentRoute is AppDrawer,
                        shapes = segmentedItemShapes(index = 0, count = appsAndSearchCount),
                        isDarkTheme = isDarkTheme,
                    )
                }
                DashboardCategory(
                    label = labelSearchBar,
                    description = descSearchBar,
                    iconResource = R.drawable.ic_search,
                    onNavigate = { onNavigate(Search()) },
                    isSelected = currentRoute is Search,
                    shapes = segmentedItemShapes(
                        index = if (showAppDrawerCategory) 1 else 0,
                        count = appsAndSearchCount,
                    ),
                    isDarkTheme = isDarkTheme,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val controlsCount = if (showQuickstepCategory) 2 else 1
            PreferenceGroup {
                DashboardCategory(
                    label = labelGestures,
                    description = descGestures,
                    iconResource = R.drawable.ic_gestures,
                    onNavigate = { onNavigate(Gestures) },
                    isSelected = currentRoute is Gestures,
                    shapes = segmentedItemShapes(index = 0, count = controlsCount),
                    isDarkTheme = isDarkTheme,
                )
                ExpandAndShrink(visible = showQuickstepCategory) {
                    DashboardCategory(
                        label = labelQuickstep,
                        description = descQuickstep,
                        iconResource = R.drawable.ic_quickstep,
                        onNavigate = { onNavigate(Quickstep) },
                        isSelected = currentRoute is Quickstep,
                        shapes = segmentedItemShapes(index = 1, count = controlsCount),
                        isDarkTheme = isDarkTheme,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PreferenceGroup {
                DashboardCategory(
                    label = labelBackup,
                    description = descBackup,
                    iconResource = R.drawable.backup_restore,
                    onNavigate = { onNavigate(BackupAndRestore) },
                    isSelected = currentRoute is BackupAndRestore,
                    shapes = segmentedItemShapes(index = 0, count = 3),
                    isDarkTheme = isDarkTheme,
                )
                DashboardCategory(
                    label = labelExtras,
                    description = descExtras,
                    iconResource = R.drawable.ic_extras,
                    onNavigate = { onNavigate(Extras) },
                    isSelected = currentRoute is Extras,
                    shapes = segmentedItemShapes(index = 1, count = 3),
                    isDarkTheme = isDarkTheme,
                )
                DashboardCategory(
                    label = labelAbout,
                    description = aboutDescription,
                    iconResource = R.drawable.ic_about,
                    onNavigate = { onNavigate(About) },
                    isSelected = currentRoute is About,
                    shapes = segmentedItemShapes(index = 2, count = 3),
                    isDarkTheme = isDarkTheme,
                )
            }
        }

        AnimatedVisibility(
            visible = searchActive,
            enter = slideInVertically(
                animationSpec = tween(380, easing = FastOutSlowInEasing),
                initialOffsetY = { searchBarOffsetY },
            ) + fadeIn(tween(260)),
            exit = slideOutVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetY = { searchBarOffsetY },
            ) + fadeOut(tween(220)),
        ) {
            SearchOverlay(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    searchActive = false
                    searchQuery = ""
                },
                entries = allEntries,
                onNavigate = { route ->
                    if (route is PreferenceRootRoute) {
                        onNavigate(route)
                    } else {
                        navController.navigate(route)
                    }
                },
            )
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
        if (showDrawerBlurPermissionDialog) {
            WallpaperAccessPermissionDialog(
                managedFilesChecked = drawerBlurManagedFilesChecked,
                onDismiss = { showDrawerBlurPermissionDialog = false },
                onPermissionRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        drawerBlurManagedFilesChecked = Environment.isExternalStorageManager()
                    }
                },
            )
        }
        if (showWallpaperBlurPermissionDialog) {
            WallpaperAccessPermissionDialog(
                managedFilesChecked = wallpaperBlurAllFilesAccessState != FileAccessState.Denied,
                onDismiss = { showWallpaperBlurPermissionDialog = false },
                onPermissionRequest = { wallpaperBlurFileAccessManager.refresh() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    entries: List<SearchableEntry>,
    onNavigate: (PreferenceRoute) -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filtered = remember(query, entries) {
        if (query.isBlank()) {
            emptyList()
        } else {
            val q = query.trim().lowercase()
            entries.filter {
                it.label.lowercase().contains(q) || it.keywords.lowercase().contains(q)
            }
        }
    }

    val prefs = preferenceManager2()
    val blurEnabled = prefs.settingsBlurBackground.getAdapter().state.value
    val blurIntensity = prefs.settingsBlurIntensity.getAdapter().state.value.toInt()
    val blurState = rememberSettingsBlurState(blurEnabled, blurIntensity)

    val surfaceArgb = MaterialTheme.colorScheme.surface.toArgb()
    val scrimColor = Color(ColorUtils.setAlphaComponent(surfaceArgb, (0.72f * 255).toInt()))
    val bitmap = blurState.bitmap
    val showBlur = bitmap != null

    Box(modifier = Modifier.fillMaxSize().then(blurState.sizeModifier)) {
        if (showBlur) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxSize().background(scrimColor))
        } else {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_settings),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )

            when {
                query.isBlank() -> {}

                filtered.isEmpty() -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "No results for \"$query\"",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }

                else -> {
                    val isDarkTheme = isSelectedThemeDark
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        items(filtered.size) { idx ->
                            val entry = filtered[idx]
                            val toggle = entry.toggle
                            val badgeColor = dashboardIconColorFor(entry.iconResource)
                            val itemShapes = segmentedItemShapes(index = idx, count = filtered.size)
                            PreferenceCategory(
                                label = entry.label,
                                description = entry.breadcrumb,
                                iconResource = entry.iconResource,
                                onNavigate = {
                                    onClose()
                                    entry.scrollKey?.let { ScrollTargetManager.set(it) }
                                    onNavigate(entry.route)
                                },
                                isSelected = false,
                                shapes = itemShapes,
                                iconContainerColor = badgeColor.containerColor(isDarkTheme),
                                iconContentColor = badgeColor.content,
                                endWidget = if (toggle != null) {
                                    {
                                        Switch(
                                            checked = toggle.checked,
                                            onCheckedChange = { newValue ->
                                                mMSDLPlayerWrapper.playToken(
                                                    if (newValue) MSDLToken.SWITCH_ON else MSDLToken.SWITCH_OFF,
                                                )
                                                toggle.onCheckedChange(newValue)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedIconColor = MaterialTheme.colorScheme.primary,
                                            ),
                                            thumbContent = {
                                                if (toggle.checked) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSearchBar(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    Surface(
        onClick = {
            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
            onActivate()
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.search_settings),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreferencesSetDefaultLauncherCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    Surface(
        onClick = {
            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
            Intent(Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let { context.startActivity(it) }
            (context as? Activity)?.finish()
        },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.TipsAndUpdates,
                tint = MaterialTheme.colorScheme.background,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(id = R.string.set_default_launcher_tip),
                color = MaterialTheme.colorScheme.background,
            )
        }
    }
}

@Composable
private fun DebugBadge(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
        ),
    ) {
        Text(
            text = "debug",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        )
    }
}

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
