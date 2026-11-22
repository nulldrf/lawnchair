/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications copyright 2022 Lawnchair
 */

package app.lawnchair.preferences2

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataMigration
import androidx.datastore.migrations.SharedPreferencesView
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.model.DeviceGridState

class SharedPreferencesMigration(private val context: Context) {

    private val sharedPreferencesName = LauncherFiles.SHARED_PREFERENCES_KEY

    /**
     * Target type used to coerce legacy SharedPreferences values safely (e.g. legacy IntPref
     * previously accepted Float values via ClassCastException fallback).
     */
    private enum class TargetType {
        BOOLEAN,
        INT,
        FLOAT,
        LONG,
        STRING,
        STRING_SET,
    }

    private data class KeySpec(
        val newKey: String,
        val type: TargetType? = null,
    )

    /**
     * Legacy keys migrated out of SharedPreferences into DataStore (and removed from
     * SharedPreferences once migrated).
     */
    private val keys: Map<String, KeySpec> = mapOf(
        // Previously migrated keys
        "pref_darkStatusBar" to KeySpec("dark_status_bar", TargetType.BOOLEAN),
        "pref_dockSearchBar" to KeySpec("dock_search_bar"),
        "pref_iconShape" to KeySpec("icon_shape", TargetType.STRING),
        "pref_themedHotseatQsb" to KeySpec("themed_hotseat_qsb", TargetType.BOOLEAN),
        "pref_accentColor2" to KeySpec("accent_color", TargetType.STRING),
        "hidden-app-set" to KeySpec("hidden_apps", TargetType.STRING_SET),
        "pref_showStatusBar" to KeySpec("show_status_bar", TargetType.BOOLEAN),
        "pref_showSysUiScrim" to KeySpec("show_top_shadow", TargetType.BOOLEAN),
        "pref_hideAppSearchBar" to KeySpec("hide_app_drawer_search_bar", TargetType.BOOLEAN),
        "pref_enableFontSelection" to KeySpec("enable_font_selection", TargetType.BOOLEAN),
        "pref_doubleTap2Sleep" to KeySpec("dt2s"),
        "pref_searchAutoShowKeyboard" to KeySpec("auto_show_keyboard_in_drawer", TargetType.BOOLEAN),
        "pref_iconSizeFactor" to KeySpec("home_icon_size_factor", TargetType.FLOAT),
        "pref_folderPreviewBgOpacity" to KeySpec("folder_preview_background_opacity", TargetType.FLOAT),
        "pref_showHomeLabels" to KeySpec("show_icon_labels_on_home_screen", TargetType.BOOLEAN),
        "pref_allAppsIconSizeFactor" to KeySpec("drawer_icon_size_factor", TargetType.FLOAT),
        "pref_allAppsIconLabels" to KeySpec("show_icon_labels_in_drawer", TargetType.BOOLEAN),
        "pref_textSizeFactor" to KeySpec("home_icon_label_size_factor", TargetType.FLOAT),
        "pref_allAppsTextSizeFactor" to KeySpec("drawer_icon_label_size_factor", TargetType.FLOAT),
        "pref_allAppsCellHeightMultiplier" to KeySpec("drawer_cell_height_factor", TargetType.FLOAT),
        "pref_useFuzzySearch" to KeySpec("enable_fuzzy_search", TargetType.BOOLEAN),
        "pref_smartSpaceEnable" to KeySpec("enable_smartspace", TargetType.BOOLEAN),
        "pref_enableMinusOne" to KeySpec("enable_feed", TargetType.BOOLEAN),
        "pref_enableIconSelection" to KeySpec("enable_icon_selection"),
        "pref_showComponentName" to KeySpec("show_component_names", TargetType.BOOLEAN),
        "pref_allAppsColumns" to KeySpec("drawer_columns", TargetType.INT),
        "pref_folderColumns" to KeySpec("folder_columns", TargetType.INT),
        "pref_maxSearchResultCount" to KeySpec("max_search_result_count", TargetType.INT),
        // Newly migrated PreferenceManager keys (74 non-mirrored keys)
        "pref_iconPackPackage" to KeySpec("icon_pack_package", TargetType.STRING),
        "pref_themedIconPackPackage" to KeySpec("themed_icon_pack_package", TargetType.STRING),
        "pref_motionEnabled" to KeySpec("motion_enabled", TargetType.BOOLEAN),
        "pref_panToMoveEnabled" to KeySpec("pan_to_move_enabled", TargetType.BOOLEAN),
        "pref_panToMoveSensitivity" to KeySpec("pan_to_move_sensitivity", TargetType.FLOAT),
        "pref_hotseatColumns" to KeySpec("hotseat_columns", TargetType.INT),
        "pref_hotseatColumnsUnfolded" to KeySpec("hotseat_columns_unfolded", TargetType.INT),
        "pref_hotseatRows" to KeySpec("hotseat_rows", TargetType.INT),
        "pref_dockPages" to KeySpec("dock_pages", TargetType.INT),
        "pref_workspaceColumns" to KeySpec("workspace_columns", TargetType.INT),
        "pref_workspaceRows" to KeySpec("workspace_rows", TargetType.INT),
        "pref_workspace_increase_max_grid_size" to KeySpec("workspace_increase_max_grid_size", TargetType.BOOLEAN),
        "pref_folderRows" to KeySpec("folder_rows", TargetType.INT),
        "pref_drawerOpacity" to KeySpec("drawer_opacity", TargetType.FLOAT),
        "pref_feedProvider" to KeySpec("feed_provider", TargetType.STRING),
        "pref_ignoreFeedWhitelist" to KeySpec("ignore_feed_whitelist", TargetType.BOOLEAN),
        "pref_launcherTheme" to KeySpec("launcher_theme", TargetType.STRING),
        "pref_overrideWindowCornerRadius" to KeySpec("override_window_corner_radius", TargetType.BOOLEAN),
        "pref_windowCornerRadius" to KeySpec("window_corner_radius", TargetType.INT),
        "pref_autoLaunchRoot" to KeySpec("auto_launch_root", TargetType.BOOLEAN),
        "pref_wallpaperScrolling" to KeySpec("wallpaper_scrolling", TargetType.BOOLEAN),
        "pref_infiniteScrolling" to KeySpec("infinite_scrolling", TargetType.BOOLEAN),
        "pref_enableDebugMenu" to KeySpec("enable_debug_menu", TargetType.BOOLEAN),
        "pref_vibrationFeedbackLevel" to KeySpec("vibration_feedback_level", TargetType.INT),
        "pref_recentActionOrder" to KeySpec("recent_action_order", TargetType.STRING),
        "pref_workspaceFont" to KeySpec("font_workspace", TargetType.STRING),
        "pref_fontHeading" to KeySpec("font_heading", TargetType.STRING),
        "pref_fontHeadingMedium" to KeySpec("font_heading_medium", TargetType.STRING),
        "pref_fontBody" to KeySpec("font_body", TargetType.STRING),
        "pref_fontBodyMedium" to KeySpec("font_body_medium", TargetType.STRING),
        "device_search" to KeySpec("device_search", TargetType.BOOLEAN),
        "pref_searchResultShortcuts" to KeySpec("search_result_shortcuts", TargetType.BOOLEAN),
        "pref_searchResultPeople" to KeySpec("search_result_people", TargetType.BOOLEAN),
        "pref_searchResultPixelTips" to KeySpec("search_result_pixel_tips", TargetType.BOOLEAN),
        "pref_searchResultSettings" to KeySpec("search_result_settings", TargetType.BOOLEAN),
        "pref_searchResultCalculator" to KeySpec("search_result_calculator", TargetType.BOOLEAN),
        "pref_searchResultApps" to KeySpec("search_result_apps", TargetType.BOOLEAN),
        "pref_searchResultFiles" to KeySpec("search_result_files", TargetType.BOOLEAN),
        "pref_searchResultAllFiles" to KeySpec("search_result_all_files", TargetType.BOOLEAN),
        "pref_searchResultAudio" to KeySpec("search_result_audio", TargetType.BOOLEAN),
        "pref_searchResultVisualMedia" to KeySpec("search_result_visual_media", TargetType.BOOLEAN),
        "pref_searchResultStartPageSuggestion" to KeySpec("search_result_start_page_suggestion", TargetType.BOOLEAN),
        "pref_searchResultSettingsEntry" to KeySpec("search_result_settings_entry", TargetType.BOOLEAN),
        "pref_searchResultRecentSuggestion" to KeySpec("search_result_recent_suggestion", TargetType.BOOLEAN),
        "drawer_themed_icons" to KeySpec("drawer_themed_icons", TargetType.BOOLEAN),
        "pref_hotseatQsbCornerRadius" to KeySpec("hotseat_qsb_corner_radius", TargetType.FLOAT),
        "pref_searchHotseatTranparency" to KeySpec("hotseat_qsb_alpha", TargetType.INT),
        "pref_searchStrokeWidth" to KeySpec("hotseat_qsb_stroke_width", TargetType.FLOAT),
        "pref_hotseatBG" to KeySpec("hotseat_bg", TargetType.BOOLEAN),
        "pref_hotseatBGHRinsetLeft" to KeySpec("hotseat_bg_horizontal_inset_left", TargetType.INT),
        "pref_hotseatBGVRinsetTop" to KeySpec("hotseat_bg_vertical_inset_top", TargetType.INT),
        "pref_hotseatBGHRinsetRight" to KeySpec("hotseat_bg_horizontal_inset_right", TargetType.INT),
        "pref_hotseatBGVRinsetBottom" to KeySpec("hotseat_bg_vertical_inset_bottom", TargetType.INT),
        "pref_hotseatBGTransparency" to KeySpec("hotseat_bg_alpha", TargetType.INT),
        "pref_enableWallpaperBlur" to KeySpec("enable_wallpaper_blur", TargetType.BOOLEAN),
        "pref_wallpaperBlur" to KeySpec("wallpaper_blur", TargetType.INT),
        "pref_wallpaperBlurFactor" to KeySpec("wallpaper_blur_factor_threshold", TargetType.FLOAT),
        "pref_settingsBlurBackground" to KeySpec("settings_blur_background", TargetType.BOOLEAN),
        "pref_settingsBlurIntensity" to KeySpec("settings_blur_intensity", TargetType.FLOAT),
        "pref_drawerListOrder" to KeySpec("drawer_list_order", TargetType.STRING),
        "pref_drawerList" to KeySpec("drawer_list", TargetType.BOOLEAN),
        "pref_hideFolderApps" to KeySpec("folder_apps", TargetType.BOOLEAN),
        "pref_recentsActionScreenshot" to KeySpec("recents_action_screenshot", TargetType.BOOLEAN),
        "pref_recentsActionShare" to KeySpec("recents_action_share", TargetType.BOOLEAN),
        "pref_recentsActionLens" to KeySpec("recents_action_lens", TargetType.BOOLEAN),
        "pref_clearAllAsAction" to KeySpec("recents_action_clear_all", TargetType.BOOLEAN),
        "pref_lockedAsAction" to KeySpec("recents_action_locked", TargetType.BOOLEAN),
        "pref_recentsTranslucentBackground" to KeySpec("recents_translucent_background", TargetType.BOOLEAN),
        "pref_recentTranslucentBackgroundAlpha" to KeySpec("recents_translucent_background_alpha", TargetType.FLOAT),
        "pref_hideVersionInfo" to KeySpec("hide_version_info", TargetType.BOOLEAN),
        "pref_pseudonymVersion" to KeySpec("pseudonym_version", TargetType.STRING),
        "pref_enableGnc" to KeySpec("enable_gnc", TargetType.BOOLEAN),
        "pref_hasOpenedSettings" to KeySpec("has_opened_settings", TargetType.BOOLEAN),
        "pref_lawnchairMajorVersion" to KeySpec("lawnchair_major_version", TargetType.INT),
    )

    /**
     * 12 keys that are still read directly from SharedPreferences by AOSP Launcher3
     * (RotationHelper, SessionCommitReceiver, ThemeManager) or iconloaderlib (IconPreferences).
     * These are copied into DataStore once without deleting them from SharedPreferences.
     */
    private val mirroredKeys: Map<String, KeySpec> = mapOf(
        "pref_allowRotation" to KeySpec("allow_rotation", TargetType.BOOLEAN),
        "pref_add_icon_to_home" to KeySpec("add_icon_to_home", TargetType.BOOLEAN),
        "themed_icons" to KeySpec("themed_icons", TargetType.BOOLEAN),
        "prefs_wrapAdaptive" to KeySpec("wrap_adaptive_icons", TargetType.BOOLEAN),
        "prefs_transparentIconBackground" to KeySpec("transparent_icon_background", TargetType.BOOLEAN),
        "pref_shadowBGIcons" to KeySpec("shadow_bg_icons", TargetType.BOOLEAN),
        "pref_coloredBackgroundLightness" to KeySpec("colored_background_lightness", TargetType.FLOAT),
        "pref_forceIconMonochrome" to KeySpec("force_icon_monochrome", TargetType.BOOLEAN),
        "pref_colorizedLegacyTreatment" to KeySpec("colorized_backgrounds", TargetType.BOOLEAN),
        "pref_enableWhiteOnlyTreatment" to KeySpec("treat_white_adaptive_icons", TargetType.BOOLEAN),
        "pref_colorizeIconPackBackground" to KeySpec("colorize_icon_pack_background", TargetType.BOOLEAN),
        "pref_appNameMap" to KeySpec("custom_app_name", TargetType.STRING),
    )

    fun produceMigrations(): List<DataMigration<Preferences>> = listOf(
        produceMigration(),
        produceMirroredMigration(),
    )

    fun produceMigration(): DataMigration<Preferences> = androidx.datastore.migrations.SharedPreferencesMigration(
        context = context,
        sharedPreferencesName = sharedPreferencesName,
        keysToMigrate = keys.keys,
        shouldRunMigration = getShouldRunMigration(),
        migrate = produceMigrationFunction(),
    )

    private fun produceMirroredMigration(): DataMigration<Preferences> = object : DataMigration<Preferences> {
        private val migratedSentinelKey = booleanPreferencesKey(LEGACY_MIRRORED_MIGRATED_KEY)

        override suspend fun shouldMigrate(currentData: Preferences): Boolean {
            return currentData[migratedSentinelKey] != true
        }

        override suspend fun migrate(currentData: Preferences): Preferences {
            val sp = LauncherPrefs.getPrefs(context)
            val allSharedPrefs = sp.all
            val existingKeys = currentData.asMap().keys.map { it.name }.toSet()
            val mutablePreferences = currentData.toMutablePreferences()

            // Preserve ancient Lawnchair v1 grid migration before copying values.
            val oldVersion = sp.getInt("version", 9999)
            if (oldVersion < 2) {
                val gridState = DeviceGridState(context).toProtoMessage()
                if (gridState.hotseatCount != -1) {
                    val colsAndRows = gridState.gridSize.split(",")
                    if (colsAndRows.size >= 2) {
                        colsAndRows[0].toIntOrNull()?.let {
                            if ("workspace_columns" !in existingKeys) {
                                mutablePreferences[intPreferencesKey("workspace_columns")] = it
                            }
                        }
                        colsAndRows[1].toIntOrNull()?.let {
                            if ("workspace_rows" !in existingKeys) {
                                mutablePreferences[intPreferencesKey("workspace_rows")] = it
                            }
                        }
                    }
                    if ("hotseat_columns" !in existingKeys) {
                        mutablePreferences[intPreferencesKey("hotseat_columns")] = gridState.hotseatCount
                    }
                }
                sp.edit { putInt("version", 2) }
            }

            for ((legacyKey, spec) in mirroredKeys) {
                if (spec.newKey in existingKeys) continue
                val value = allSharedPrefs[legacyKey] ?: continue
                putCoercedValue(mutablePreferences, spec, value)
            }

            mutablePreferences[migratedSentinelKey] = true
            return mutablePreferences.toPreferences()
        }

        override suspend fun cleanUp() {
            // Intentionally keep mirrored keys in SharedPreferences so AOSP Launcher3
            // and iconloaderlib can continue reading them directly.
        }
    }

    private fun getShouldRunMigration(): suspend (Preferences) -> Boolean = { preferences ->
        val allKeys = preferences.asMap().keys.map { it.name }.toSet()
        keys.values.any { it.newKey !in allKeys }
    }

    private fun produceMigrationFunction(): suspend (SharedPreferencesView, Preferences) -> Preferences = { sharedPreferences: SharedPreferencesView, currentData: Preferences ->
        val currentKeys = currentData.asMap().keys.map { it.name }.toSet()
        val mutablePreferences = currentData.toMutablePreferences()

        for ((legacyKey, value) in sharedPreferences.getAll()) {
            val spec = keys[legacyKey] ?: KeySpec(legacyKey)
            if (spec.newKey in currentKeys) continue
            if (value == null) continue
            putCoercedValue(mutablePreferences, spec, value)
        }

        mutablePreferences.toPreferences()
    }

    @Suppress("UNCHECKED_CAST")
    private fun putCoercedValue(
        target: MutablePreferences,
        spec: KeySpec,
        value: Any,
    ) {
        val newKey = spec.newKey
        when (spec.type) {
            TargetType.BOOLEAN -> {
                if (value is Boolean) target[booleanPreferencesKey(newKey)] = value
            }

            TargetType.INT -> {
                when (value) {
                    is Int -> target[intPreferencesKey(newKey)] = value
                    is Float -> target[intPreferencesKey(newKey)] = value.toInt()
                    is Long -> target[intPreferencesKey(newKey)] = value.toInt()
                }
            }

            TargetType.FLOAT -> {
                when (value) {
                    is Float -> target[floatPreferencesKey(newKey)] = value
                    is Int -> target[floatPreferencesKey(newKey)] = value.toFloat()
                    is Long -> target[floatPreferencesKey(newKey)] = value.toFloat()
                }
            }

            TargetType.LONG -> {
                when (value) {
                    is Long -> target[longPreferencesKey(newKey)] = value
                    is Int -> target[longPreferencesKey(newKey)] = value.toLong()
                }
            }

            TargetType.STRING -> {
                if (value is String) target[stringPreferencesKey(newKey)] = value
            }

            TargetType.STRING_SET -> {
                if (value is Set<*>) {
                    target[stringSetPreferencesKey(newKey)] = value.filterIsInstance<String>().toSet()
                }
            }

            null -> {
                when (value) {
                    is Boolean -> target[booleanPreferencesKey(newKey)] = value
                    is Float -> target[floatPreferencesKey(newKey)] = value
                    is Int -> target[intPreferencesKey(newKey)] = value
                    is Long -> target[longPreferencesKey(newKey)] = value
                    is String -> target[stringPreferencesKey(newKey)] = value
                    is Set<*> -> target[stringSetPreferencesKey(newKey)] = value.filterIsInstance<String>().toSet()
                }
            }
        }
    }

    companion object {
        private const val LEGACY_MIRRORED_MIGRATED_KEY = "legacy_mirrored_prefs_migrated"
    }
}
