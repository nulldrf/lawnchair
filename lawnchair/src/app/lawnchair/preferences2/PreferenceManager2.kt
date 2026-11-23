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

package app.lawnchair.preferences2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lawnchair.allapps.DrawerWallpaperBlurHelper
import app.lawnchair.data.Converters
import app.lawnchair.font.FontCache
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.SleepMode
import app.lawnchair.gestures.type.GestureType
import app.lawnchair.hotseat.HotseatMode
import app.lawnchair.icons.CustomAdaptiveIconDrawable
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.icons.shape.IconShapeManager
import app.lawnchair.predictions.PredictionMode
import app.lawnchair.qsb.providers.QsbSearchProvider
import app.lawnchair.search.algorithms.LawnchairSearchAlgorithm
import app.lawnchair.search.algorithms.engine.provider.web.WebSearchProvider
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceMode
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.smartspace.provider.weather.TemperatureUnit
import app.lawnchair.smartspace.provider.weather.WeatherProvider
import app.lawnchair.theme.color.ColorMode
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.toOptionOrderString
import app.lawnchair.ui.preferences.components.HiddenAppsInSearch
import app.lawnchair.ui.preferences.data.liveinfo.LiveInformationManager
import app.lawnchair.util.getApkVersionComparison
import app.lawnchair.util.isGestureNavContractCompatible
import app.lawnchair.util.isOnePlusStock
import app.lawnchair.util.kotlinxJson
import app.lawnchair.views.overlay.AppOpenAnimationType
import app.lawnchair.views.overlay.FullScreenOverlayMode
import com.android.launcher3.BuildConfig
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.INDEX_DEFAULT
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.ENABLE_TWOLINE_ALLAPPS_TOGGLE
import com.android.launcher3.R
import com.android.launcher3.SessionCommitReceiver
import com.android.launcher3.Workspace
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager as L3ThemeManager
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.DynamicResource
import com.android.launcher3.util.SafeCloseable
import com.android.systemui.monet.SpecVersion
import com.google.android.msdl.data.model.FeedbackLevel
import com.google.android.msdl.domain.MSDLPlayer
import com.patrykmichalik.opto.core.PreferenceManager
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

@LauncherAppSingleton
class PreferenceManager2 @Inject constructor(
    @ApplicationContext private val context: Context,
) : PreferenceManager,
    SafeCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resourceProvider = DynamicResource.provider(context)
    private var liveInformationManager: LiveInformationManager =
        LiveInformationManager.getInstance(context)
    private val legacyPrefs: SharedPreferences = LauncherPrefs.getPrefs(context)
    private val fontCache = FontCache.INSTANCE.get(context)
    private val deviceType = DisplayController.INSTANCE.get(context).info.deviceType

    private fun idpPreference(
        key: Preferences.Key<Int>,
        defaultSelector: InvariantDeviceProfile.GridOption.() -> Int,
        onSet: (Int) -> Unit = {},
    ): IdpPreference = IdpPreference(
        key = key,
        defaultSelector = defaultSelector,
        preferencesDataStore = preferencesDataStore,
        onSet = onSet,
    )

    private val cachedDataStore = CachedPreferencesDataStore(
        delegate = context.preferencesDataStore,
        ioScope = ioScope,
    )

    override val preferencesDataStore: DataStore<Preferences> = cachedDataStore

    fun getCachedPreferences(): Preferences = cachedDataStore.currentPreferences

    val hasPendingWrites: Boolean
        get() = cachedDataStore.hasPendingWrites

    suspend fun flush() = cachedDataStore.flush()

    fun flushAsync() = cachedDataStore.flushAsync()

    fun flushBlocking(timeoutMs: Long = 2_000L) = cachedDataStore.flushBlocking(timeoutMs)

    fun batchEdit(block: MutablePreferences.() -> Unit) {
        runBlocking {
            cachedDataStore.editBatch(block)
        }
    }

    private val reloadHelper = ReloadHelper(context)

    val darkStatusBar = preference(
        key = booleanPreferencesKey(name = "dark_status_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dark_status_bar),
    )

    val hotseatMode = preference(
        key = stringPreferencesKey("hotseat_mode"),
        defaultValue = HotseatMode.fromString(context.getString(R.string.config_default_hotseat_mode)),
        parse = { HotseatMode.fromString(it) },
        save = { it.toString() },
    )

    val iconShape = preference(
        key = stringPreferencesKey(name = "icon_shape"),
        defaultValue = IconShape.fromString(
            value = context.getString(R.string.config_default_icon_shape),
            context = context,
        ) ?: IconShape.Circle,
        parse = {
            IconShape.fromString(value = it, context = context)
                ?: IconShapeManager.getSystemIconShape(context)
        },
        save = { it.toString() },
        onSet = {
            reloadHelper.reloadIcons()
        },
    )

    val folderShape = preference(
        key = stringPreferencesKey(name = "folder_shape"),
        defaultValue = IconShape.fromString(
            value = context.getString(R.string.config_default_folder_shape),
            context = context,
        ) ?: IconShape.Circle,
        parse = {
            IconShape.fromString(value = it, context = context)
                ?: IconShapeManager.getSystemIconShape(context)
        },
        save = { it.toString() },
        onSet = {
            reloadHelper.reloadIcons()
        },
    )

    val customIconShape = preference(
        key = stringPreferencesKey(name = "custom_icon_shape"),
        defaultValue = null,
        parse = {
            IconShape.CustomCornerBased.fromStringOrNull(value = it)
                ?: IconShape.CustomCornerBased(
                    IconShapeManager.getSystemIconShape(context).findNearestShape(),
                )
        },
        save = { it.toString() },
        onSet = { it?.let(iconShape::setBlocking) },
    )

    val customFolderShape = preference(
        key = stringPreferencesKey(name = "custom_folder_shape"),
        defaultValue = null,
        parse = {
            IconShape.CustomCornerBased.fromStringOrNull(value = it)
                ?: IconShape.CustomCornerBased(
                    IconShapeManager.getSystemIconShape(context).findNearestShape(),
                )
        },
        save = { it.toString() },
        onSet = { it?.let(folderShape::setBlocking) },
    )

    val alwaysReloadIcons = preference(
        key = booleanPreferencesKey(name = "always_reload_icons"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_always_reload_icons),
    )

    val colorStyle = preference(
        key = stringPreferencesKey("color_style"),
        defaultValue = ColorStyle.fromString("tonal_spot"),
        parse = ColorStyle::fromString,
        save = ColorStyle::toString,
        onSet = { reloadHelper.recreate() },
    )

    val colorSpec = preference(
        key = stringPreferencesKey("color_spec"),
        defaultValue = SpecVersion.SPEC_2021,
        parse = { value ->
            when (value) {
                "spec_2026" -> SpecVersion.SPEC_2026
                "spec_2025" -> SpecVersion.SPEC_2025
                else -> SpecVersion.SPEC_2021
            }
        },
        save = { specVersion ->
            when (specVersion) {
                SpecVersion.SPEC_2026 -> "spec_2026"
                SpecVersion.SPEC_2025 -> "spec_2025"
                SpecVersion.SPEC_2021 -> "spec_2021"
            }
        },
        onSet = { reloadHelper.recreate() },
    )

    // Tracks the last non-2026 spec (SPEC_2021 or SPEC_2025) so that when SPEC_2026
    // is active, all non-CMF styles fall back to whichever spec the user used before.
    // Defaults to SPEC_2025 since 2025 and 2026 share the same base spec.
    val colorSpecFallback = preference(
        key = stringPreferencesKey("color_spec_fallback"),
        defaultValue = SpecVersion.SPEC_2025,
        parse = { value ->
            when (value) {
                "spec_2021" -> SpecVersion.SPEC_2021
                else -> SpecVersion.SPEC_2025
            }
        },
        save = { specVersion ->
            when (specVersion) {
                SpecVersion.SPEC_2021 -> "spec_2021"
                else -> "spec_2025"
            }
        },
    )

    val strokeColorStyle = preference(
        key = stringPreferencesKey(name = "stroke_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.restart() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_accent_color)),
    )

    val hotseatBackgroundColor = preference(
        key = stringPreferencesKey(name = "hotseat_bg_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_hotseat_bg_color)),
    )

    val hotseatBackgroundCornerRadius = preference(
        key = floatPreferencesKey(name = "hotseat_bg_corner_radius"),
        defaultValue = context.resources.getDimension(R.dimen.bg_round_rect_radius) /
            context.resources.displayMetrics.density, // This should hopefully match corner of all devices
        onSet = { reloadHelper.recreate() },
    )

    val appDrawerBackgroundColor = preference(
        key = stringPreferencesKey(name = "app_drawer_bg_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_app_drawer_bg_color)),
    )

    val appDrawerSearchBarBackground = preference(
        key = booleanPreferencesKey(name = "all_apps_search_bar_background"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_search_bar_background),
        onSet = { reloadHelper.recreate() },
    )

    val workProfileTabBackgroundColor = preference(
        key = stringPreferencesKey(name = "work_profile_tab_background_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.SystemAccent,
    )

    val workProfileTabContainerBackground = preference(
        key = booleanPreferencesKey(name = "work_profile_tab_container_background"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val notificationDotColor = preference(
        key = stringPreferencesKey(name = "notification_dot_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_notification_dot_color)),
    )

    val notificationDotTextColor = preference(
        key = stringPreferencesKey(name = "notification_dot_text_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_notification_dot_text_color)),
    )

    val folderColor = preference(
        key = stringPreferencesKey(name = "folder_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.reloadGrid() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_folder_color)),
    )

    val showNotificationCount = preference(
        key = booleanPreferencesKey(name = "show_notification_count"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_notification_count),
        onSet = { reloadHelper.reloadGrid() },
    )

    val themedHotseatQsb = preference(
        key = booleanPreferencesKey(name = "themed_hotseat_qsb"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_themed_hotseat_qsb),
    )

    val isHotseatEnabled = preference(
        key = booleanPreferencesKey(name = "pref_show_hotseat"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_hotseat),
    )

    val hotseatQsbProvider = preference(
        key = stringPreferencesKey(name = "dock_search_bar_provider"),
        defaultValue = getRemoteDefault("dock_search_bar_provider")?.let {
            QsbSearchProvider.fromId(it)
        } ?: QsbSearchProvider.resolveDefault(context),
        parse = { QsbSearchProvider.fromId(it) },
        save = { it.id },
    )

    val hotseatQsbForceWebsite = preference(
        key = booleanPreferencesKey(name = "dock_search_bar_force_website"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dock_search_bar_force_website),
    )

    val accentColor = preference(
        key = stringPreferencesKey(name = "accent_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        onSet = { reloadHelper.recreate() },
        defaultValue = ColorOption.fromString(context.getString(R.string.config_default_accent_color)),
    )

    val hiddenApps = preference(
        key = stringSetPreferencesKey(name = "hidden_apps"),
        defaultValue = setOf(),
    )

    val roundedWidgets = preference(
        key = booleanPreferencesKey(name = "rounded_widgets"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_rounded_widgets),
        onSet = { reloadHelper.reloadGrid() },
    )

    val allowWidgetOverlap = preference(
        key = booleanPreferencesKey(name = "allow_widget_overlap"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_allow_widget_overlap),
        onSet = { reloadHelper.reloadGrid() },
    )

    val forceWidgetResize = preference(
        key = booleanPreferencesKey(name = "force_widget_resize"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_force_widget_resize),
    )

    val widgetUnlimitedSize = preference(
        key = booleanPreferencesKey(name = "widget_unlimited_size"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_widget_unlimited_size),
    )

    val showStatusBar = preference(
        key = booleanPreferencesKey(name = "show_status_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_status_bar),
    )

    val statusBarClock = preference(
        key = booleanPreferencesKey(name = "status_bar_clock"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_dynamic_hide_status_bar_clock),
        onSet = { reloadHelper.reloadGrid() },
    )

    val rememberPosition = preference(
        key = booleanPreferencesKey(name = "all_apps_remember_position"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_remember_position),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showScrollbar = preference(
        key = booleanPreferencesKey(name = "all_apps_show_scrollbar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_scrollbar),
        onSet = { reloadHelper.recreate() },
    )

    val showTopShadow = preference(
        key = booleanPreferencesKey(name = "show_top_shadow"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_top_shadow),
    )

    val lockHomeScreen = preference(
        key = booleanPreferencesKey(name = "lock_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_lock_home_screen),
    )

    val defaultHomePage = preference(
        key = intPreferencesKey(name = "default_home_page"),
        defaultValue = Workspace.DEFAULT_PAGE,
    )

    val legacyPopupOptionsMigrated = preference(
        key = booleanPreferencesKey(name = "legacy_popup_options_migrated"),
        defaultValue = false,
    )

    val enableGlobalPrediction = preference(
        key = booleanPreferencesKey(name = "enable_global_prediction"),
        defaultValue = true,
    )

    val predictionMode = preference(
        key = stringPreferencesKey(name = "prediction_mode"),
        defaultValue = PredictionMode.fromString(context.getString(R.string.config_default_prediction_mode)),
        parse = { PredictionMode.fromString(it) },
        save = { it.toString() },
    )

    val lawnchairPredictorUseWeightedUsageStats = preference(
        key = booleanPreferencesKey(name = "lawnchair_prediction_use_weighted_usage"),
        defaultValue = false,
    )

    val launcherPopupOrder = preference(
        key = stringPreferencesKey(name = "launcher_popup_order"),
        defaultValue = LauncherOptionsPopup.DEFAULT_ORDER.toOptionOrderString(),
        onSet = { reloadHelper.reloadGrid() },
    )

    val lockHomeScreenButtonOnPopUp = preference(
        key = booleanPreferencesKey(name = "lock_home_screen_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_lock_home_screen_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val editHomeScreenButtonOnPopUp = preference(
        key = booleanPreferencesKey(name = "edit_home_screen_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_edit_home_screen_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showSystemSettingsEntryOnPopUp = preference(
        key = booleanPreferencesKey(name = "show_system_settings_entry_on_popup"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_system_settings_entry_on_popup),
        onSet = { reloadHelper.reloadGrid() },
    )

    val hideAppDrawerSearchBar = preference(
        key = booleanPreferencesKey(name = "hide_app_drawer_search_bar"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_hide_app_drawer_search_bar),
        onSet = { reloadHelper.recreate() },
    )

    // LC-Note: pins the app drawer search bar to the bottom of the drawer instead
    // of the top. Independent of hideAppDrawerSearchBar (the two can be combined,
    // though if the bar is hidden this has no visible effect). Uses recreate(),
    // matching hideAppDrawerSearchBar, since this changes the RelativeLayout
    // structure of ActivityAllAppsContainerView, not just a visual toggle.
    val appDrawerSearchBarAtBottom = preference(
        key = booleanPreferencesKey(name = "app_drawer_search_bar_at_bottom"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val appDrawerHapticFeedback = preference(
        key = booleanPreferencesKey(name = "app_drawer_haptic_feedback"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_app_drawer_haptic_feedback),
    )

    val hiddenAppsInSearch = preference(
        key = stringPreferencesKey(name = "hidden_apps_in_search"),
        defaultValue = HiddenAppsInSearch.NEVER,
        onSet = { reloadHelper.recreate() },
    )

    val searchAlgorithm = preference(
        key = stringPreferencesKey(name = "search_algorithm"),
        defaultValue = LawnchairSearchAlgorithm.LOCAL_SEARCH,
        onSet = { reloadHelper.recreate() },
    )

    val showSuggestedAppsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_suggested_apps_at_drawer_top"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_suggested_apps_at_drawer_top),
        onSet = { reloadHelper.recreate() },
    )

    // -----------------------------------------------------------------------
    // App Drawer HokoBlur
    //
    // Uses the HokoBlur library to render a blurred wallpaper bitmap behind
    // the app drawer on phones (non-sheet layout).  Tablet / bottom-sheet
    // mode is intentionally excluded to avoid visual conflicts with the
    // ScrimView-managed sheet background.
    //
    // drawerBlurIntensity changes clear the DrawerWallpaperBlurHelper cache
    // in addition to recreating the launcher so the new radius is applied
    // immediately on the fresh instance.
    // -----------------------------------------------------------------------

    val drawerBlurBackground = preference(
        key = booleanPreferencesKey(name = "drawer_hoko_blur_background"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val drawerBlurIntensity = preference(
        key = floatPreferencesKey(name = "drawer_hoko_blur_intensity"),
        defaultValue = 75f,
        onSet = {
            // Clear cached bitmap so the next applyDrawerHokoBlur() recomputes
            // with the new intensity value after the launcher is recreated.
            DrawerWallpaperBlurHelper.clearCache()
            reloadHelper.recreate()
        },
    )

    val enableFontSelection = preference(
        key = booleanPreferencesKey(name = "enable_font_selection"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_font_selection),
        onSet = { newValue ->
            if (!newValue) {
                fontWorkspace.setCached(fontCache.uiText)
            }
        },
    )

    val autoShowKeyboardInDrawer = preference(
        key = booleanPreferencesKey(name = "auto_show_keyboard_in_drawer"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_auto_show_keyboard_in_drawer),
    )

    val workspaceTextColor = preference(
        key = stringPreferencesKey(name = "workspace_text_color"),
        defaultValue = ColorMode.AUTO,
        parse = { ColorMode.fromString(it) ?: ColorMode.AUTO },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    // -----------------------------------------------------------------------
    // Home screen icon text color (ColorOption-based full color picker)
    //
    // Uses Default (Lawnchair-managed automatic) as the out-of-the-box value
    // so existing behaviour is preserved. When the user picks a custom colour
    // the resolved ARGB int is applied directly as the icon label text colour
    // on the workspace, overriding the theme-derived colour.
    // -----------------------------------------------------------------------
    val workspaceIconTextColor = preference(
        key = stringPreferencesKey(name = "workspace_icon_text_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        // Default means "let the theme / workspaceTextColor setting decide"
        defaultValue = ColorOption.Default,
        onSet = { reloadHelper.reloadGrid() },
    )

    // -----------------------------------------------------------------------
    // App drawer icon text color (ColorOption-based full color picker)
    //
    // Default (Lawnchair-managed automatic) preserves existing drawer text
    // colour behaviour. Any other value is resolved and applied directly to
    // the BubbleTextView in DISPLAY_ALL_APPS mode.
    // -----------------------------------------------------------------------
    val drawerIconTextColor = preference(
        key = stringPreferencesKey(name = "drawer_icon_text_color"),
        parse = ColorOption::fromString,
        save = ColorOption::toString,
        defaultValue = ColorOption.Default,
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderPreviewBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_preview_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_preview_background_opacity),
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderBackgroundOpacity = preference(
        key = floatPreferencesKey(name = "folder_background_opacity"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_folder_background_opacity),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsOnHomeScreen = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsOnHomeScreenFolder = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_on_home_screen_folder"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_on_home_screen),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val showIconLabelsInDrawer = preference(
        key = booleanPreferencesKey(name = "show_icon_labels_in_drawer"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_icon_labels_in_drawer),
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_label_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val homeIconLabelFolderSizeFactor = preference(
        key = floatPreferencesKey(name = "home_icon_label_folder_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_home_icon_label_folder_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val pageIndicatorHeightFactor = preference(
        key = floatPreferencesKey(name = "page_indicator_height_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.page_indicator_height_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerIconLabelSizeFactor = preference(
        key = floatPreferencesKey(name = "drawer_icon_label_size_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_icon_label_size_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerCellHeightFactor = preference(
        key = floatPreferencesKey(name = "drawer_cell_height_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_cell_height_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerLeftRightMarginFactor = preference(
        key = floatPreferencesKey(name = "drawer_left_right_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_left_right_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val hotseatBottomFactor = preference(
        key = floatPreferencesKey(name = "hotseat_bottom_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_hotseat_bottom_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val workspacePaddingHorizontalFactor = preference(
        key = floatPreferencesKey(name = "workspace_padding_horizontal"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_workspace_padding_horizontal),
        onSet = { reloadHelper.reloadGrid() },
    )

    val workspacePaddingVerticalFactor = preference(
        key = floatPreferencesKey(name = "workspace_padding_vertical"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_workspace_padding_vertical),
        onSet = { reloadHelper.reloadGrid() },
    )

    val widgetPaddingFactor = preference(
        key = floatPreferencesKey(name = "widget_padding_factor"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_widget_padding_factor),
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerPaddingTopFactor = preference(
        key = floatPreferencesKey(name = "drawer_padding_top"),
        defaultValue = resourceProvider.getFloat(R.dimen.config_default_drawer_padding_top),
        onSet = { reloadHelper.reloadGrid() },
    )

    val enableFuzzySearch = preference(
        key = booleanPreferencesKey(name = "enable_fuzzy_search"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_fuzzy_search),
    )

    // ── App-close / GNC overlay ───────────────────────────────────────────────
    val closingAppOverlay = preference(
        key = stringPreferencesKey(name = "closing_app_overlay"),
        defaultValue = FullScreenOverlayMode.fromValue(context.resources.getString(R.string.config_default_overlay)),
        parse = { FullScreenOverlayMode.fromValue(it) },
        save = { it.value },
        onSet = { reloadHelper.reloadGrid() },
    )

    // ── App-open / launch animation (1:1 port from Lawnchair 9) ───────────────

    /**
     * Controls the [ActivityOptions] and launcher-side animation used when launching
     * an app. Mirrors `pref_animationType` from Lawnchair 9 (`AnimationType`).
     *
     *  DEFAULT  → system / Quickstep default launch animation
     *  PIE      → emulated Android 9 Pie icon-to-splash launch + slide-down close
     *  REVEAL   → system clip-reveal from icon bounds
     *  SLIDE_UP → app slides in from the bottom edge (task_open_enter / no_anim)
     *  SCALE_UP → system scale-up from icon bounds
     *  BLINK    → stretch-blink transition (blink_open_* / blink_close_*)
     *  FADE     → short fade transition (fade_in_short / fade_out_short)
     */
    val appOpenAnimation = preference(
        key = stringPreferencesKey(name = "app_open_animation"),
        defaultValue = AppOpenAnimationType.DEFAULT,
        parse = { AppOpenAnimationType.fromValue(it) },
        save = { it.value },
        // No launcher restart needed — ActivityOptions are resolved at launch time.
    )

    val matchHotseatQsbStyle = preference(
        key = booleanPreferencesKey(name = "use_drawer_search_icon"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val webSuggestionProvider = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider"),
        defaultValue = WebSearchProvider.fromString(
            getRemoteDefault("web_suggestion_provider")
                ?: context.resources.getString(R.string.config_default_web_suggestion_provider),
        ),
        parse = { WebSearchProvider.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val webSuggestionProviderUrl = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider_url"),
        defaultValue = "",
    )

    val webSuggestionProviderSuggestionsUrl = preference(
        key = stringPreferencesKey(name = "web_suggestions_provider_suggestions_url"),
        defaultValue = "",
    )

    val webSuggestionProviderName = preference(
        key = stringPreferencesKey(name = "web_suggestion_provider_name"),
        defaultValue = context.resources.getString(R.string.custom),
    )

    val maxAppSearchResultCount = preference(
        key = intPreferencesKey(name = "max_search_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_search_max_result_count),
    )

    val maxWebSuggestionResultCount = preference(
        key = intPreferencesKey(name = "max_suggestion_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_suggestion_max_result_count),
    )

    val maxFileResultCount = preference(
        key = intPreferencesKey(name = "max_files_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_files_max_result_count),
    )

    val maxPeopleResultCount = preference(
        key = intPreferencesKey(name = "max_people_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_people_max_result_count),
    )

    val maxWebSuggestionDelay = preference(
        key = intPreferencesKey(name = "max_web_suggestion_delay"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_max_web_suggestion_delay),
    )

    val maxSettingsEntryResultCount = preference(
        key = intPreferencesKey(name = "max_settings_entry_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_settings_entry_max_result_count),
    )

    val maxRecentResultCount = preference(
        key = intPreferencesKey(name = "max_recent_result_count"),
        defaultValue = resourceProvider.getInt(R.dimen.config_default_recent_max_result_count),
    )

    val enableSmartspace = preference(
        key = booleanPreferencesKey(name = "enable_smartspace"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_smartspace),
        onSet = { reloadHelper.restart() },
    )

    val twoLineAllApps = preference(
        key = booleanPreferencesKey(name = "two_line_all_apps"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_two_line_allapps),
        onSet = { value ->
            LauncherPrefs.get(context).put(ENABLE_TWOLINE_ALLAPPS_TOGGLE, value)
        },
    )

    // -----------------------------------------------------------------------
    // Home screen icon label line count
    //
    // Lawnchair: home screen counterpart to twoLineAllApps above. Unlike the
    // App Drawer version there is no platform/IDP-level field driving this —
    // DeviceProfile.maxIconTextLineCount is normally only non-1 when the AOSP
    // flag enableScalabilityForDesktopExperience() is on, which regular
    // phones never have. So instead, this preference is read directly inside
    // DeviceProfile.getHomeIconTextLineCount() (used by updateIconSize() to
    // both set maxIconTextLineCount and grow cellHeightPx so the extra line
    // has room), and BubbleTextView.shouldUseTwoLine() reads the resulting
    // maxIconTextLineCount back for DISPLAY_WORKSPACE — the same pattern
    // InvariantDeviceProfile.enableTwoLinesInAllApps already uses for the
    // drawer, just without an IDP-level field in between.
    //
    // When enabled, long app names on the home screen wrap onto a second
    // line instead of being truncated with "…".
    //
    // Covers all three DeviceProfile grid branches:
    //  - mIsScalableGrid / legacy fallback: cellHeightPx is grown to fit.
    //  - mIsResponsiveGrid: the line count is only ever bumped *up* to 2 as
    //    a starting point for CellContentDimensions.resizeToFitCellHeight(),
    //    which silently shrinks it back to 1 if the existing (untouched)
    //    responsive row height doesn't actually have room — best-effort,
    //    never forces a layout change there.
    // -----------------------------------------------------------------------
    val twoLineHomeScreen = preference(
        key = booleanPreferencesKey(name = "two_line_home_screen"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    val enableFeed = preference(
        key = booleanPreferencesKey(name = "enable_feed"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_enable_feed),
    )

    val showComponentNames = preference(
        key = booleanPreferencesKey(name = "show_component_names"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_show_component_names),
    )

    val drawerColumns = idpPreference(
        key = intPreferencesKey(name = "drawer_columns"),
        defaultSelector = { numAllAppsColumns },
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerColumnsUnfolded = idpPreference(
        key = intPreferencesKey(name = "drawer_columns_unfolded"),
        defaultSelector = { numAllAppsColumns + 2 },
        onSet = { reloadHelper.reloadGrid() },
    )

    val folderColumns = idpPreference(
        key = intPreferencesKey(name = "folder_columns"),
        defaultSelector = { numFolderColumns[INDEX_DEFAULT] },
        onSet = { reloadHelper.reloadGrid() },
    )

    val additionalFonts = preference(
        key = stringPreferencesKey(name = "additional_fonts"),
        defaultValue = "",
    )

    val enableTaskbarOnPhone = preference(
        key = booleanPreferencesKey("enable_taskbar_on_phone"),
        defaultValue = false,
        onSet = {
            reloadHelper.reloadGrid()
            reloadHelper.reloadTaskbar()
            reloadHelper.recreate()
        },
    )

    val smartspaceMode = preference(
        key = stringPreferencesKey("smartspace_mode"),
        defaultValue = SmartspaceMode.fromString(context.getString(R.string.config_default_smartspace_mode)),
        parse = { SmartspaceMode.fromString(it) },
        save = { it.toString() },
        onSet = { reloadHelper.recreate() },
    )

    val smartspaceAagWidget = preference(
        key = booleanPreferencesKey("enable_smartspace_aag_widget"),
        defaultValue = true,
    )

    val smartspaceBatteryStatus = preference(
        key = booleanPreferencesKey("enable_smartspace_battery_status"),
        defaultValue = true,
    )

    val smartspaceTorch = preference(
        key = booleanPreferencesKey("enable_smartspace_torch"),
        defaultValue = true,
    )

    val smartspaceNowPlaying = preference(
        key = booleanPreferencesKey("enable_smartspace_now_playing"),
        defaultValue = true,
    )

    val smartspaceOnboarding = preference(
        key = booleanPreferencesKey("enable_smartspace_onboarding"),
        defaultValue = true,
    )
	
    val smartspacePersonality = preference(
        key = booleanPreferencesKey("enable_smartspace_personality"),
        defaultValue = true,
    )
	
    val smartspaceWeatherEnabled = preference(
        key = booleanPreferencesKey("enable_smartspace_weather"),
        defaultValue = true,
    )

    val smartspaceWeatherProvider = preference(
        key = stringPreferencesKey("smartspace_weather_provider"),
        defaultValue = WeatherProvider.NONE,
        parse = { WeatherProvider.fromString(it) },
        save = { it.name },
    )

    val smartspaceWeatherCity = preference(
        key = stringPreferencesKey("smartspace_weather_city"),
        defaultValue = "",
    )

    val smartspaceWeatherUnit = preference(
        key = stringPreferencesKey("smartspace_weather_unit"),
        defaultValue = TemperatureUnit.CELSIUS,
        parse = { TemperatureUnit.fromString(it) },
        save = { it.name },
    )

    val smartspaceWeatherIconPack = preference(
        key = stringPreferencesKey("smartspace_weather_icon_pack"),
        defaultValue = "",
    )

    val smartspaceWeatherRefreshInterval = preference(
        key = stringPreferencesKey("smartspace_weather_refresh_interval"),
        defaultValue = 30L,
        parse = { it.toLongOrNull() ?: 30L },
        save = { it.toString() },
    )

    val pirateWeatherApiKey = preference(
        key = stringPreferencesKey("pirate_weather_api_key"),
        defaultValue = "",
    )

    val openWeatherMapApiKey = preference(
        key = stringPreferencesKey("open_weather_map_api_key"),
        defaultValue = "",
    )

    val accuWeatherApiKey = preference(
        key = stringPreferencesKey("accu_weather_api_key"),
        defaultValue = "",
    )

    val smartspaceShowDate = preference(
        key = booleanPreferencesKey("smartspace_show_date"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_date),
    )

    val smartspaceShowTime = preference(
        key = booleanPreferencesKey("smartspace_show_time"),
        defaultValue = context.resources.getBoolean(R.bool.config_default_smartspace_show_time),
    )

    val smartspaceTimeFormat = preference(
        key = stringPreferencesKey("smartspace_time_format"),
        defaultValue = SmartspaceTimeFormat.fromString(context.getString(R.string.config_default_smartspace_time_format)),
        parse = { SmartspaceTimeFormat.fromString(it) },
        save = { it.toString() },
    )

    val smartspaceCalendar = preference(
        key = stringPreferencesKey(name = "smartspace_calendar"),
        defaultValue = SmartspaceCalendar.fromString(context.getString(R.string.config_default_smart_space_calendar)),
        parse = { SmartspaceCalendar.fromString(it) },
        save = { it.toString() },
    )

    val smartspacerMaxCount = preference(
        key = intPreferencesKey(name = "smartspace_max_count"),
        defaultValue = 5,
        onSet = { reloadHelper.recreate() },
    )

    val wallpaperDepthEffect = preference(
        key = booleanPreferencesKey(name = "enable_wallpaper_depth_effect"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val deckLayout = preference(
        key = booleanPreferencesKey(name = "enable_lawn_deck"),
        defaultValue = false,
        onSet = { reloadHelper.reloadIcons() },
    )

    val showDeckLayout = preference(
        key = booleanPreferencesKey(name = "show_deck_layout"),
        defaultValue = false,
    )

    val enableLabelInDock = preference(
        key = booleanPreferencesKey(name = "enable_label_dock"),
        defaultValue = false,
    )

    // -----------------------------------------------------------------------
    // Dock (hotseat) icon label line count
    //
    // Lawnchair: dock counterpart to twoLineHomeScreen above. Deliberately a
    // fully separate preference/field/code path — not a re-use of
    // twoLineHomeScreen — so home screen icons and dock icons can each be
    // toggled to two-line labels independently of one another:
    //
    //  - BubbleTextView shares mDisplay == DISPLAY_WORKSPACE between true
    //    home-screen icons and dock icons (the dock has no display type of
    //    its own). BubbleTextView.shouldUseTwoLine() tells them apart via
    //    ItemInfo.container (CONTAINER_HOTSEAT / CONTAINER_HOTSEAT_PREDICTION,
    //    the same check shouldTextBeVisible() already uses) and reads
    //    DeviceProfile.maxHotseatTextLineCount for dock icons,
    //    DeviceProfile.maxIconTextLineCount for everything else.
    //  - This preference itself is read directly inside
    //    DeviceProfile.getHotseatIconTextLineCount(), consumed only by
    //    DeviceProfile.updateHotseatSizes() — the single method (shared by
    //    all three grid branches) that already grows hotseatCellHeightPx /
    //    hotseatBarSizePx for enableLabelInDock's existing single-line case.
    //
    // Only has a visible effect when enableLabelInDock is also true, since
    // two-line wrapping is meaningless with dock labels hidden entirely.
    // -----------------------------------------------------------------------
    val twoLineDock = preference(
        key = booleanPreferencesKey(name = "two_line_dock"),
        defaultValue = false,
        onSet = { reloadHelper.reloadGrid() },
    )

    val doubleTapGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("double_tap_gesture_handler"),
        defaultValue = GestureHandlerConfig.Sleep,
    )

    val sleepMode = preference(
        key = stringPreferencesKey(name = "sleep_mode"),
        defaultValue = SleepMode.AUTO,
        parse = { SleepMode.fromString(it) ?: SleepMode.AUTO },
        save = { it.toString() },
    )

    val swipeUpGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_up_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenAppDrawer,
    )

    /** Swipe down gesture handler
     *
     * Important: It is imperative swipe down gesture handler only passed over to or handled by the native touch controller at [com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController]
     *
     * This is needed for ease of touch slop implementation and smooth notifications panel pulling for rooted device via SystemUI Proxy.
     * */
    val swipeDownGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("swipe_down_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenNotifications,
    )

    val twoFingerSwipeUpGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("two_finger_swipe_up_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val twoFingerSwipeDownGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("two_finger_swipe_down_gesture_handler"),
        defaultValue = GestureHandlerConfig.OpenQuickSettings,
    )

    val homePressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("home_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val backPressGestureHandler = serializablePreference<GestureHandlerConfig>(
        key = stringPreferencesKey("back_press_gesture_handler"),
        defaultValue = GestureHandlerConfig.NoOp,
    )

    val autoUpdaterNightly = preference(
        key = booleanPreferencesKey(name = "enable_nightly_auto_updater"),
        defaultValue = if (BuildConfig.APPLICATION_ID.contains("nightly")) {
            context.resources.getBoolean(R.bool.config_default_enable_nightly_auto_updater)
        } else {
            false
        },
    )

    // -----------------------------------------------------------------------
    // Migrated legacy PreferenceManager keys (86 keys)
    // -----------------------------------------------------------------------

    // Reload handled by LawnchairIconProvider.IconPackChangeReceiver (single serialized reload + loading overlay).
    val iconPackPackage = preference(
        key = stringPreferencesKey(name = "icon_pack_package"),
        defaultValue = "",
    )

    val themedIconPackPackage = preference(
        key = stringPreferencesKey(name = "themed_icon_pack_package"),
        defaultValue = "",
    )

    val allowRotation = preference(
        key = booleanPreferencesKey(name = "allow_rotation"),
        defaultValue = false,
        onSet = { value ->
            LauncherPrefs.get(context).put(LauncherPrefs.ALLOW_ROTATION, value)
        },
    )

    /** Enables motion-driven interactions that use the device's motion sensors. */
    val motionEnabled = preference(
        key = booleanPreferencesKey(name = "motion_enabled"),
        defaultValue = false,
    )

    /** Enables tilt-to-page gestures while rearranging home-screen items. */
    val panToMoveEnabled = preference(
        key = booleanPreferencesKey(name = "pan_to_move_enabled"),
        defaultValue = false,
    )

    /** Controls how far a motion reading must move toward the gesture threshold. */
    val panToMoveSensitivity = preference(
        key = floatPreferencesKey(name = "pan_to_move_sensitivity"),
        defaultValue = 0.5f,
    )

    // Mirrored to SharedPreferences for iconloaderlib (IconPreferences.kt).
    // LawnchairThemeManager observes these via Flow and calls verifyIconState() -> onThemeChanged().
    val wrapAdaptiveIcons = preference(
        key = booleanPreferencesKey(name = "wrap_adaptive_icons"),
        defaultValue = true,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_WRAP_ADAPTIVE, value) } },
    )

    val transparentIconBackground = preference(
        key = booleanPreferencesKey(name = "transparent_icon_background"),
        defaultValue = false,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_TRANSPARENT_ICON_BG, value) } },
    )

    val shadowBGIcons = preference(
        key = booleanPreferencesKey(name = "shadow_bg_icons"),
        defaultValue = true,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_SHADOW_BG_ICONS, value) } },
    )

    val addIconToHome = preference(
        key = booleanPreferencesKey(name = "add_icon_to_home"),
        defaultValue = true,
        onSet = { value ->
            legacyPrefs.edit { putBoolean(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY, value) }
        },
    )

    private val isPhone: Boolean get() = deviceType == InvariantDeviceProfile.TYPE_PHONE
    private val isTablet: Boolean get() = deviceType == InvariantDeviceProfile.TYPE_TABLET
    private val isFoldable: Boolean get() = deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY
    private val isDesktop: Boolean get() = deviceType == InvariantDeviceProfile.TYPE_DESKTOP

    val calculatedGridSpec = when {
        // This grid configuration is perfect for Phone, tested against Pixel 7,
        // alternative dense configuration can be 5x5x7
        isPhone -> LayoutConfig(4, 4, 6)

        // This grid configuration is perfect for Tablet, tested against Pixel Tablet
        isTablet -> LayoutConfig(6, 6, 5)

        // This grid configuration is perfect for Foldable, tested against Pixel 10 Pro Fold
        // Note: Hotseat column is 4 when folded, unfolded uses hotseatColumns + 2 or higher number
        // defined in numExtendedHotseatIcons from device profile
        isFoldable -> LayoutConfig(4, 4, 6, 6)

        // This grid configuration is not tested against actual desktop devices,
        // but tablet configuration works perfectly when displayed via emulator
        isDesktop -> LayoutConfig(6, 6, 5)

        // This grid configuration is the fallback for all devices type, this shouldn't be possible
        else -> LayoutConfig(4, 4, 7)
    }

    val hotseatColumns = preference(
        key = intPreferencesKey(name = "hotseat_columns"),
        defaultValue = calculatedGridSpec.hotseatColumns,
        onSet = { reloadHelper.reloadGrid() },
    )

    val hotseatColumnsUnfolded = preference(
        key = intPreferencesKey(name = "hotseat_columns_unfolded"),
        defaultValue = calculatedGridSpec.hotseatColumnsUnfolded,
        onSet = { reloadHelper.reloadGrid() },
    )

    val hotseatRows = preference(
        key = intPreferencesKey(name = "hotseat_rows"),
        defaultValue = 1,
        onSet = { reloadHelper.reloadGrid() },
    )

    val dockPages = preference(
        key = intPreferencesKey(name = "dock_pages"),
        defaultValue = 1,
        onSet = { reloadHelper.reloadGrid() },
    )

    val workspaceColumns = preference(
        key = intPreferencesKey(name = "workspace_columns"),
        defaultValue = calculatedGridSpec.workspaceColumns,
    )

    val workspaceRows = preference(
        key = intPreferencesKey(name = "workspace_rows"),
        defaultValue = calculatedGridSpec.workspaceRows,
    )

    val workspaceIncreaseMaxGridSize = preference(
        key = booleanPreferencesKey(name = "workspace_increase_max_grid_size"),
        defaultValue = false,
    )

    val folderRows = idpPreference(
        key = intPreferencesKey(name = "folder_rows"),
        defaultSelector = { numFolderRows[INDEX_DEFAULT] },
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerOpacity = preference(
        key = floatPreferencesKey(name = "drawer_opacity"),
        defaultValue = .5f,
        onSet = { reloadHelper.recreate() },
    )

    // Mirrored to SharedPreferences for iconloaderlib; tracked by LawnchairThemeManager via Flow.
    val coloredBackgroundLightness = preference(
        key = floatPreferencesKey(name = "colored_background_lightness"),
        defaultValue = 1F,
        onSet = { value -> legacyPrefs.edit { putFloat(SP_COLORED_BG_LIGHTNESS, value) } },
    )

    val feedProvider = preference(
        key = stringPreferencesKey(name = "feed_provider"),
        defaultValue = "",
    )

    val ignoreFeedWhitelist = preference(
        key = booleanPreferencesKey(name = "ignore_feed_whitelist"),
        defaultValue = false,
    )

    val launcherTheme = preference(
        key = stringPreferencesKey(name = "launcher_theme"),
        defaultValue = "system",
    )

    val overrideWindowCornerRadius = preference(
        key = booleanPreferencesKey(name = "override_window_corner_radius"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val windowCornerRadius = preference(
        key = intPreferencesKey(name = "window_corner_radius"),
        defaultValue = 80,
        onSet = { reloadHelper.recreate() },
    )

    val autoLaunchRoot = preference(
        key = booleanPreferencesKey(name = "auto_launch_root"),
        defaultValue = false,
    )

    val wallpaperScrolling = preference(
        key = booleanPreferencesKey(name = "wallpaper_scrolling"),
        defaultValue = true,
    )

    val infiniteScrolling = preference(
        key = booleanPreferencesKey(name = "infinite_scrolling"),
        defaultValue = false,
    )

    val enableDebugMenu = preference(
        key = booleanPreferencesKey(name = "enable_debug_menu"),
        defaultValue = false,
    )

    val vibrationFeedbackLevel = preference(
        key = intPreferencesKey(name = "vibration_feedback_level"),
        defaultValue = FeedbackLevel.DEFAULT.ordinal,
        onSet = { normalizeVibrationFeedbackLevel() },
    )

    val customAppName = preference(
        key = stringPreferencesKey(name = "custom_app_name"),
        defaultValue = emptyMap<ComponentKey, String>(),
        parse = ::parseCustomAppNameMap,
        save = ::serializeCustomAppNameMap,
        onSet = { map ->
            legacyPrefs.edit { putString(SP_APP_NAME_MAP, serializeCustomAppNameMap(map)) }
            reloadHelper.reloadGrid()
        },
    )

    val recentActionOrder = preference(
        key = stringPreferencesKey(name = "recent_action_order"),
        defaultValue = "0,1,2,3,4",
        onSet = { reloadHelper.recreate() },
    )

    val fontWorkspace = fontPreference(
        key = stringPreferencesKey(name = "font_workspace"),
        defaultValue = fontCache.uiText,
    )

    val fontHeading = fontPreference(
        key = stringPreferencesKey(name = "font_heading"),
        defaultValue = fontCache.uiRegular,
    )

    val fontHeadingMedium = fontPreference(
        key = stringPreferencesKey(name = "font_heading_medium"),
        defaultValue = fontCache.uiMedium,
    )

    val fontBody = fontPreference(
        key = stringPreferencesKey(name = "font_body"),
        defaultValue = fontCache.uiText,
    )

    val fontBodyMedium = fontPreference(
        key = stringPreferencesKey(name = "font_body_medium"),
        defaultValue = fontCache.uiTextMedium,
    )

    val fontPrefsMap by lazy {
        listOf(
            fontWorkspace,
            fontHeading,
            fontHeadingMedium,
            fontBody,
            fontBodyMedium,
        ).associateBy { it.key.name }
    }

    // TODO REMOVE
    val deviceSearch = preference(
        key = booleanPreferencesKey(name = "device_search"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultShortcuts = preference(
        key = booleanPreferencesKey(name = "search_result_shortcuts"),
        defaultValue = false,
    )

    val searchResultPeople = preference(
        key = booleanPreferencesKey(name = "search_result_people"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultPixelTips = preference(
        key = booleanPreferencesKey(name = "search_result_pixel_tips"),
        defaultValue = false,
    )

    val searchResultSettings = preference(
        key = booleanPreferencesKey(name = "search_result_settings"),
        defaultValue = false,
    )

    val searchResultCalculator = preference(
        key = booleanPreferencesKey(name = "search_result_calculator"),
        defaultValue = false,
    )

    val searchResultApps = preference(
        key = booleanPreferencesKey(name = "search_result_apps"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultFilesToggle = preference(
        key = booleanPreferencesKey(name = "search_result_files"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultAllFiles = preference(
        key = booleanPreferencesKey(name = "search_result_all_files"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultAudio = preference(
        key = booleanPreferencesKey(name = "search_result_audio"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultVisualMedia = preference(
        key = booleanPreferencesKey(name = "search_result_visual_media"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultStartPageSuggestion = preference(
        key = booleanPreferencesKey(name = "search_result_start_page_suggestion"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val searchResultSettingsEntry = preference(
        key = booleanPreferencesKey(name = "search_result_settings_entry"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val searchResulRecentSuggestion = preference(
        key = booleanPreferencesKey(name = "search_result_recent_suggestion"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val themedIcons = preference(
        key = booleanPreferencesKey(name = "themed_icons"),
        defaultValue = false,
        onSet = { value ->
            LauncherPrefs.get(context).put(L3ThemeManager.THEMED_ICONS, value)
            reloadHelper.reloadIconsFull()
        },
    )

    val drawerThemedIcons = preference(
        key = booleanPreferencesKey(name = "drawer_themed_icons"),
        defaultValue = false,
        onSet = { reloadHelper.reloadIconsFull() },
    )

    val hotseatQsbCornerRadius = preference(
        key = floatPreferencesKey(name = "hotseat_qsb_corner_radius"),
        defaultValue = 1F,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatQsbAlpha = preference(
        key = intPreferencesKey(name = "hotseat_qsb_alpha"),
        defaultValue = 100,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatQsbStrokeWidth = preference(
        key = floatPreferencesKey(name = "hotseat_qsb_stroke_width"),
        defaultValue = 0F,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatBG = preference(
        key = booleanPreferencesKey(name = "hotseat_bg"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatBGHorizontalInsetLeft = preference(
        key = intPreferencesKey(name = "hotseat_bg_horizontal_inset_left"),
        defaultValue = 0,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatBGVerticalInsetTop = preference(
        key = intPreferencesKey(name = "hotseat_bg_vertical_inset_top"),
        defaultValue = 0,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatBGHorizontalInsetRight = preference(
        key = intPreferencesKey(name = "hotseat_bg_horizontal_inset_right"),
        defaultValue = 0,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatBGVerticalInsetBottom = preference(
        key = intPreferencesKey(name = "hotseat_bg_vertical_inset_bottom"),
        defaultValue = 0,
        onSet = { reloadHelper.recreate() },
    )

    val hotseatBGAlpha = preference(
        key = intPreferencesKey(name = "hotseat_bg_alpha"),
        defaultValue = 100,
        onSet = { reloadHelper.recreate() },
    )

    val enableWallpaperBlur = preference(
        key = booleanPreferencesKey(name = "enable_wallpaper_blur"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val wallpaperBlur = preference(
        key = intPreferencesKey(name = "wallpaper_blur"),
        defaultValue = 25,
        onSet = { reloadHelper.recreate() },
    )

    val wallpaperBlurFactorThreshold = preference(
        key = floatPreferencesKey(name = "wallpaper_blur_factor_threshold"),
        defaultValue = 3.0F,
        onSet = { reloadHelper.recreate() },
    )

    // Settings background blur — Compose recomposes directly, no recreate needed.
    val settingsBlurBackground = preference(
        key = booleanPreferencesKey(name = "settings_blur_background"),
        defaultValue = false,
    )

    val settingsBlurIntensity = preference(
        key = floatPreferencesKey(name = "settings_blur_intensity"),
        defaultValue = 75f,
    )

    val drawerListOrder = preference(
        key = stringPreferencesKey(name = "drawer_list_order"),
        defaultValue = "",
        onSet = { reloadHelper.reloadGrid() },
    )

    val drawerList = preference(
        key = booleanPreferencesKey(name = "drawer_list"),
        defaultValue = true,
        onSet = { reloadHelper.recreate() },
    )

    val folderApps = preference(
        key = booleanPreferencesKey(name = "folder_apps"),
        defaultValue = true,
        onSet = { reloadHelper.reloadGrid() },
    )

    val recentsActionScreenshot = preference(
        key = booleanPreferencesKey(name = "recents_action_screenshot"),
        defaultValue = !isOnePlusStock,
    )

    val recentsActionShare = preference(
        key = booleanPreferencesKey(name = "recents_action_share"),
        defaultValue = isOnePlusStock,
    )

    val recentsActionLens = preference(
        key = booleanPreferencesKey(name = "recents_action_lens"),
        defaultValue = true,
    )

    val recentsActionClearAll = preference(
        key = booleanPreferencesKey(name = "recents_action_clear_all"),
        defaultValue = false,
    )

    val recentsActionLocked = preference(
        key = booleanPreferencesKey(name = "recents_action_locked"),
        defaultValue = false,
    )

    val recentsTranslucentBackground = preference(
        key = booleanPreferencesKey(name = "recents_translucent_background"),
        defaultValue = false,
        onSet = { reloadHelper.recreate() },
    )

    val recentsTranslucentBackgroundAlpha = preference(
        key = floatPreferencesKey(name = "recents_translucent_background_alpha"),
        defaultValue = .8f,
        onSet = { reloadHelper.recreate() },
    )

    val hideVersionInfo = preference(
        key = booleanPreferencesKey(name = "hide_version_info"),
        defaultValue = false,
    )

    val pseudonymVersion = preference(
        key = stringPreferencesKey(name = "pseudonym_version"),
        defaultValue = "Bubble Tea",
    )

    val enableGnc = preference(
        key = booleanPreferencesKey(name = "enable_gnc"),
        defaultValue = isGestureNavContractCompatible,
        onSet = { reloadHelper.recreate() },
    )

    val hasOpenedSettings = preference(
        key = booleanPreferencesKey(name = "has_opened_settings"),
        defaultValue = false,
    )

    val lawnchairMajorVersion = preference(
        key = intPreferencesKey(name = "lawnchair_major_version"),
        defaultValue = context.getApkVersionComparison().first[0],
    )

    // Mirrored to SharedPreferences for iconloaderlib; tracked by LawnchairThemeManager via Flow.
    val forceIconMonochrome = preference(
        key = booleanPreferencesKey(name = "force_icon_monochrome"),
        defaultValue = false,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_FORCE_ICON_MONOCHROME, value) } },
    )

    val colorizedBackgrounds = preference(
        key = booleanPreferencesKey(name = "colorized_backgrounds"),
        defaultValue = false,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_COLORIZED_LEGACY_TREATMENT, value) } },
    )

    val treatWhiteAdaptiveIcons = preference(
        key = booleanPreferencesKey(name = "treat_white_adaptive_icons"),
        defaultValue = false,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_ENABLE_WHITE_ONLY_TREATMENT, value) } },
    )

    // Mirrored to SharedPreferences for iconloaderlib; LawnchairIconProvider observes via Flow.
    val colorizeIconPackBackground = preference(
        key = booleanPreferencesKey(name = "colorize_icon_pack_background"),
        defaultValue = false,
        onSet = { value -> legacyPrefs.edit { putBoolean(SP_COLORIZE_ICON_PACK_BG, value) } },
    )

    private fun fontPreference(
        key: Preferences.Key<String>,
        defaultValue: FontCache.Font,
    ) = preference(
        key = key,
        defaultValue = defaultValue,
        parse = { stringValue ->
            runCatching { FontCache.Font.fromJsonString(context, stringValue) }
                .getOrDefault(defaultValue)
        },
        save = { it.toJsonString() },
        onSet = { reloadHelper.recreate() },
    )

    private inline fun <reified T> serializablePreference(
        key: Preferences.Key<String>,
        defaultValue: T,
    ) = preference(
        key = key,
        defaultValue = defaultValue,
        parse = { value ->
            runCatching { kotlinxJson.decodeFromString<T>(value) }.getOrDefault(defaultValue)
        },
        save = kotlinxJson::encodeToString,
    )

    fun getCustomAppName(key: ComponentKey): String? = customAppName.firstCached(this)[key]

    fun setCustomAppName(key: ComponentKey, value: String?) {
        val updated = customAppName.firstCached(this).toMutableMap()
        if (value != null) {
            updated[key] = value
        } else {
            updated.remove(key)
        }
        customAppName.setCached(updated)
    }

    private fun normalizeVibrationFeedbackLevel() {
        val storedLevel = vibrationFeedbackLevel.firstCached(this)
        val normalizedLevel = storedLevel.toFeedbackLevel().ordinal
        if (storedLevel != normalizedLevel) {
            vibrationFeedbackLevel.setCached(normalizedLevel)
        }
        MSDLPlayer.SYSTEM_FEEDBACK_LEVEL = FeedbackLevel.entries[normalizedLevel]
    }

    /**
     * Ensures the 12 keys read directly by AOSP Launcher3 or iconloaderlib match DataStore
     * (e.g. after restoring a DataStore backup). Only writes keys that exist in DataStore and
     * differ from SharedPreferences, so normal startups perform zero SharedPreferences writes.
     */
    private fun syncMirroredPreferences() {
        val cached = getCachedPreferences()
        val launcherPrefs = LauncherPrefs.get(context)

        cached[allowRotation.key]?.let { value ->
            if (!legacyPrefs.contains(SP_ALLOW_ROTATION) ||
                legacyPrefs.getBoolean(SP_ALLOW_ROTATION, !value) != value
            ) {
                launcherPrefs.put(LauncherPrefs.ALLOW_ROTATION, value)
            }
        }
        cached[themedIcons.key]?.let { value ->
            if (!legacyPrefs.contains(SP_THEMED_ICONS) ||
                legacyPrefs.getBoolean(SP_THEMED_ICONS, !value) != value
            ) {
                launcherPrefs.put(L3ThemeManager.THEMED_ICONS, value)
            }
        }

        val boolMirrors = listOf(
            addIconToHome.key to SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY,
            wrapAdaptiveIcons.key to SP_WRAP_ADAPTIVE,
            transparentIconBackground.key to SP_TRANSPARENT_ICON_BG,
            shadowBGIcons.key to SP_SHADOW_BG_ICONS,
            forceIconMonochrome.key to SP_FORCE_ICON_MONOCHROME,
            colorizedBackgrounds.key to SP_COLORIZED_LEGACY_TREATMENT,
            treatWhiteAdaptiveIcons.key to SP_ENABLE_WHITE_ONLY_TREATMENT,
            colorizeIconPackBackground.key to SP_COLORIZE_ICON_PACK_BG,
        )

        var editor: SharedPreferences.Editor? = null
        fun ensureEditor(): SharedPreferences.Editor = editor ?: legacyPrefs.edit().also { editor = it }

        for ((dsKey, spKey) in boolMirrors) {
            val value = cached[dsKey] ?: continue
            if (!legacyPrefs.contains(spKey) || legacyPrefs.getBoolean(spKey, !value) != value) {
                ensureEditor().putBoolean(spKey, value)
            }
        }

        cached[coloredBackgroundLightness.key]?.let { value ->
            if (!legacyPrefs.contains(SP_COLORED_BG_LIGHTNESS) ||
                legacyPrefs.getFloat(SP_COLORED_BG_LIGHTNESS, Float.NaN) != value
            ) {
                ensureEditor().putFloat(SP_COLORED_BG_LIGHTNESS, value)
            }
        }

        cached[customAppName.key]?.let { value ->
            if (!legacyPrefs.contains(SP_APP_NAME_MAP) ||
                legacyPrefs.getString(SP_APP_NAME_MAP, null) != value
            ) {
                ensureEditor().putString(SP_APP_NAME_MAP, value)
            }
        }

        editor?.apply()
    }

    private val launcherPrefChangeListener = LauncherPrefChangeListener { key ->
        val launcherPrefs = LauncherPrefs.get(context)
        when (key) {
            L3ThemeManager.KEY_THEMED_ICONS -> {
                val value = launcherPrefs.get(L3ThemeManager.THEMED_ICONS)
                if (themedIcons.firstCached(this) != value) {
                    scope.launch {
                        cachedDataStore.editBatch { this[themedIcons.key] = value }
                    }
                }
            }

            RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY -> {
                val value = launcherPrefs.get(LauncherPrefs.ALLOW_ROTATION)
                if (allowRotation.firstCached(this) != value) {
                    scope.launch {
                        cachedDataStore.editBatch { this[allowRotation.key] = value }
                    }
                }
            }
        }
    }

    init {
        syncMirroredPreferences()
        LauncherPrefs.get(context).addListener(
            launcherPrefChangeListener,
            L3ThemeManager.THEMED_ICONS,
            LauncherPrefs.ALLOW_ROTATION,
        )
        normalizeVibrationFeedbackLevel()

        initializeIconShape(iconShape.firstCached(this))
        iconShape.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { shape ->
                initializeIconShape(shape)
                L3ThemeManager.INSTANCE.get(context)
                LauncherAppState.getInstance(context).model.reloadIfActive()
            }
            .launchIn(scope)

        hotseatMode.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { reloadHelper.restart() }
            .launchIn(scope)

        isHotseatEnabled.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                reloadHelper.recreate()
                reloadHelper.reloadGrid()
            }
            .launchIn(scope)

        hotseatQsbProvider.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { reloadHelper.recreate() }
            .launchIn(scope)

        enableLabelInDock.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach { reloadHelper.reloadGrid() }
            .launchIn(scope)
    }

    suspend fun setGestureForApp(
        key: ComponentKey,
        gestureType: GestureType,
        gesture: GestureHandlerConfig,
    ) {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        preferencesDataStore.edit { prefs ->
            prefs[key] = kotlinxJson.encodeToString(gesture)
        }
    }

    fun getGestureForApp(key: ComponentKey, gestureType: GestureType): Flow<GestureHandlerConfig> {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        return preferencesDataStore.data.map { prefs ->
            prefs[key]?.let {
                runCatching { kotlinxJson.decodeFromString<GestureHandlerConfig>(it) }
                    .getOrDefault(GestureHandlerConfig.NoOp)
            } ?: GestureHandlerConfig.NoOp
        }
    }

    fun getGestureForAppCached(key: ComponentKey, gestureType: GestureType): GestureHandlerConfig {
        val cmp = Converters().fromComponentKey(key)
        val key = stringPreferencesKey("$cmp:${gestureType.name}")
        val prefs = getCachedPreferences()
        return prefs[key]?.let {
            runCatching { kotlinxJson.decodeFromString<GestureHandlerConfig>(it) }
                .getOrDefault(GestureHandlerConfig.NoOp)
        } ?: GestureHandlerConfig.NoOp
    }

    private fun initializeIconShape(shape: IconShape) {
        CustomAdaptiveIconDrawable.sInitialized = true
        CustomAdaptiveIconDrawable.sMaskId = shape.getHashString()
        CustomAdaptiveIconDrawable.sMask = shape.getMaskPath()
    }

    override fun close() {
        LauncherPrefs.get(context).removeListener(
            launcherPrefChangeListener,
            L3ThemeManager.THEMED_ICONS,
            LauncherPrefs.ALLOW_ROTATION,
        )
        cachedDataStore.flushBlocking()
        scope.cancel()
        ioScope.cancel()
    }

    private fun getRemoteDefault(key: String): String? = liveInformationManager.liveInformation
        .firstBlocking()
        .features[key]
        .also { value ->
            if (value == null) {
                Log.d(TAG, "getRemoteDefault: $key -> no remote default")
            } else {
                Log.d(TAG, "getRemoteDefault: $key -> $value")
            }
        }

    companion object {
        private val Context.preferencesDataStore by preferencesDataStore(
            name = "preferences",
            produceMigrations = { SharedPreferencesMigration(context = it).produceMigrations() },
        )

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getPreferenceManager2)

        @JvmStatic
        fun getInstance(context: Context) = INSTANCE.get(context)!!

        private const val TAG = "PreferenceManager2"

        private const val SP_ALLOW_ROTATION = "pref_allowRotation"
        private const val SP_THEMED_ICONS = "themed_icons"
        private const val SP_WRAP_ADAPTIVE = "prefs_wrapAdaptive"
        private const val SP_TRANSPARENT_ICON_BG = "prefs_transparentIconBackground"
        private const val SP_SHADOW_BG_ICONS = "pref_shadowBGIcons"
        private const val SP_COLORED_BG_LIGHTNESS = "pref_coloredBackgroundLightness"
        private const val SP_FORCE_ICON_MONOCHROME = "pref_forceIconMonochrome"
        private const val SP_COLORIZED_LEGACY_TREATMENT = "pref_colorizedLegacyTreatment"
        private const val SP_ENABLE_WHITE_ONLY_TREATMENT = "pref_enableWhiteOnlyTreatment"
        private const val SP_COLORIZE_ICON_PACK_BG = "pref_colorizeIconPackBackground"
        private const val SP_APP_NAME_MAP = "pref_appNameMap"

        private fun parseCustomAppNameMap(json: String): Map<ComponentKey, String> {
            if (json.isBlank()) return emptyMap()
            return runCatching {
                val obj = JSONObject(json)
                val map = LinkedHashMap<ComponentKey, String>(obj.length())
                obj.keys().forEach { keyStr ->
                    val componentKey = ComponentKey.fromString(keyStr) ?: return@forEach
                    map[componentKey] = obj.getString(keyStr)
                }
                map
            }.getOrDefault(emptyMap())
        }

        private fun serializeCustomAppNameMap(map: Map<ComponentKey, String>): String {
            val obj = JSONObject()
            map.entries.forEach { obj.put(it.key.toString(), it.value) }
            return obj.toString()
        }
    }
}

private fun Int.toFeedbackLevel() = FeedbackLevel.entries.getOrNull(this) ?: FeedbackLevel.DEFAULT

@Composable
fun preferenceManager2() = PreferenceManager2.getInstance(LocalContext.current)

/**
 * Grid layout configuration for a device's workspace.
 *
 * @param hotseatColumns The amount of column the dock can contain
 * @param workspaceColumns The amount of column the home screen can contain
 * @param workspaceRows The amount of row the home screen can contain
 * @param hotseatColumnsUnfolded The amount of column the dock can contain when unfolded (foldables only)
 */
data class LayoutConfig(
    /**
     * Hotseat columns refer to the amount of column the dock can contain.
     * For foldables, this is the folded (closed) state.
     */
    val hotseatColumns: Int,

    /**
     * Workspace columns refer to the amount of column the home screen can contain.
     */
    val workspaceColumns: Int,

    /**
     * Workspace rows refer to the amount of row the home screen can contain.
     */
    val workspaceRows: Int,

    /**
     * Hotseat columns when the foldable is in unfolded (opened) state.
     * For non-foldable devices, this must be equals to [hotseatColumns].
     */
    val hotseatColumnsUnfolded: Int = hotseatColumns,
)
