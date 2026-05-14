package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.AnnouncementPreference
import app.lawnchair.ui.preferences.components.controls.PreferenceCategory
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.SyncLiveInformation
import app.lawnchair.ui.preferences.data.liveinfo.liveInformationManager
import app.lawnchair.ui.preferences.navigation.About
import app.lawnchair.ui.preferences.navigation.AppDrawer
import app.lawnchair.ui.preferences.navigation.BackupAndRestore
import app.lawnchair.ui.preferences.navigation.CreateBackup
import app.lawnchair.ui.preferences.navigation.Dock
import app.lawnchair.ui.preferences.navigation.ExperimentalFeatures
import app.lawnchair.ui.preferences.navigation.Extras
import app.lawnchair.ui.preferences.navigation.Folders
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.ui.preferences.navigation.Gestures
import app.lawnchair.ui.preferences.navigation.HomeScreen
import app.lawnchair.ui.preferences.navigation.PreferenceRootRoute
import app.lawnchair.ui.preferences.navigation.Quickstep
import app.lawnchair.ui.preferences.navigation.Search
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.ui.preferences.components.layout.ScrollKeys
import app.lawnchair.ui.preferences.components.layout.ScrollTargetManager
import app.lawnchair.util.isDefaultLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking

// ── Individual deep searchable entry ─────────────────────────────────────────
// label     = displayed as result title (the actual setting name)
// keywords  = hidden search terms, never shown
// breadcrumb= section name shown as subtitle, e.g. "General"
// route     = top-level section to navigate to when tapped
private data class SearchableEntry(
    val label: String,
    val keywords: String = "",
    val breadcrumb: String,
    val iconResource: Int,
    val route: PreferenceRootRoute,
    val scrollKey: String? = null,
)

@Composable
fun PreferencesDashboard(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SyncLiveInformation()
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    // ── Announcement / default-launcher state ─────────────────────────────
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

    // ── Dynamic toolbar title ─────────────────────────────────────────────
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
    } else null

    // ── About description ─────────────────────────────────────────────────
    val aboutDescription = if (prefs.hideVersionInfo.get()) {
        prefs.pseudonymVersion.get()
    } else {
        "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}"
    }

    // ── Search state ──────────────────────────────────────────────────────
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // ── Top-level label/desc strings ──────────────────────────────────────
    val labelGeneral    = stringResource(R.string.general_label)
    val descGeneral     = stringResource(R.string.general_description)
    val labelHomeScreen = stringResource(R.string.home_screen_label)
    val descHomeScreen  = stringResource(R.string.home_screen_description)
    val labelSmartspace = stringResource(id = R.string.smartspace_widget)
    val descSmartspace  = stringResource(R.string.smartspace_widget_description)
    val labelDock       = stringResource(R.string.dock_label)
    val descDock        = stringResource(R.string.dock_description)
    val labelAppDrawer  = stringResource(R.string.app_drawer_label)
    val descAppDrawer   = stringResource(R.string.app_drawer_description)
    val labelSearchBar  = stringResource(R.string.search_bar_label)
    val descSearchBar   = stringResource(R.string.drawer_search_description)
    val labelFolders    = stringResource(R.string.folders_label)
    val descFolders     = stringResource(R.string.folders_description)
    val labelGestures   = stringResource(id = R.string.gestures_label)
    val descGestures    = stringResource(R.string.gestures_description)
    val labelQuickstep  = stringResource(id = R.string.quickstep_label)
    val descQuickstep   = stringResource(id = R.string.quickstep_description)
    val labelBackup     = stringResource(R.string.backup_and_restore_label)
    val descBackup      = stringResource(R.string.backup_and_restore_description)
    val labelExtras     = stringResource(R.string.extras_label)
    val descExtras      = stringResource(R.string.extras_description)
    val labelAbout      = stringResource(R.string.about_label)

    val deckLayout = prefs2.deckLayout.getAdapter()
    val isSmartspaceEnabled = prefs2.enableSmartspace.firstBlocking()

    // ── Deep searchable entry list ────────────────────────────────────────
    val allEntries = buildList {
        // ── General ───────────────────────────────────────────────────
        fun g(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelGeneral, R.drawable.ic_general, General, sk))
        g(stringResource(R.string.icon_style_label), "icon packs apply theme", ScrollKeys.ICON_STYLE)
        g(stringResource(R.string.themed_icon_pack), "themed icon source lawnicons monochrome")
        g(stringResource(R.string.icon_shape_label), "circle square rounded squircle octagon teardrop shape", ScrollKeys.ICON_SHAPE)
        g(stringResource(R.string.notification_dots), "badge notification count dot", ScrollKeys.NOTIFICATION_DOTS)
        g(stringResource(R.string.show_notification_count), "badge counter number notification")
        g(stringResource(R.string.theme_label), "light dark mode amoled black theme")
        g(stringResource(R.string.accent_color), "color picker tint accent custom", ScrollKeys.ACCENT_COLOR)
        g(stringResource(R.string.color_style_label), "tonal spot vibrant expressive material you dynamic", ScrollKeys.COLOR_STYLE)
        g(stringResource(R.string.colorized_backgrounds_label), "smart icon background color analyze pixel")
        g(stringResource(R.string.auto_adaptive_icons_label), "adaptive icons non-adaptive wrap background", ScrollKeys.AUTO_ADAPTIVE)
        g(stringResource(R.string.transparent_background_icons_label), "transparent themed icon background clear")
        g(stringResource(R.string.shadow_bg_icons_label), "shadow behind icons drop shadow", ScrollKeys.SHADOW_ICONS)
        g(stringResource(R.string.font_label), "font customization typography typeface heading body")

        // ── Home screen ───────────────────────────────────────────────
        fun h(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelHomeScreen, R.drawable.ic_home_screen, HomeScreen, sk))
        h(stringResource(R.string.home_screen_grid), "grid columns rows layout size change", ScrollKeys.HOME_GRID)
        h(stringResource(R.string.minus_one), "feed google discover news swipe left page")
        h(stringResource(R.string.status_bar_label), "status bar clock show hide dark light")
        h(stringResource(R.string.infinite_scrolling_label), "loop pages wrap around infinite", ScrollKeys.INFINITE_SCROLLING)
        h(stringResource(R.string.wallpaper_scrolling_label), "scroll wallpaper parallax pan", ScrollKeys.WALLPAPER_SCROLL)
        h(stringResource(R.string.wallpaper_depth_effect_label), "depth parallax zoom wallpaper effect", ScrollKeys.WALLPAPER_DEPTH)
        h(stringResource(R.string.home_screen_lock), "lock home screen prevent changes layout edit", ScrollKeys.LOCK_HOME)
        h(stringResource(R.string.auto_add_shortcuts_label), "add new apps home screen auto install", ScrollKeys.AUTO_ADD_SHORTCUTS)
        h(stringResource(R.string.popup_menu), "popup menu long press shortcuts actions edit", ScrollKeys.POPUP_MENU)
        h(stringResource(R.string.force_rounded_widgets), "rounded widgets corner radius")
        h(stringResource(R.string.wallpaper_quick_picker), "wallpaper picker quick change select")
        h(stringResource(R.string.allow_widget_overlap), "widget overlap allow")
        h(stringResource(R.string.force_widget_resize_label), "widget resize enforce resizable")
        h(stringResource(R.string.show_sys_ui_scrim), "top shadow status bar scrim gradient")
        h(stringResource(R.string.icon_sizes), "icon size scale home screen icons", ScrollKeys.HOME_ICON_SIZE)
        h(stringResource(R.string.home_screen_text_color), "text color light dark workspace", ScrollKeys.HOME_TEXT_COLOR)
        h(stringResource(R.string.app_opening_animation), "app opening animation reveal slide scale blink fade", ScrollKeys.HOME_APP_OPEN_ANIM)
        h(stringResource(R.string.app_closing_animation), "app closing animation overlay fade suck in", ScrollKeys.HOME_APP_CLOSE_ANIM)
        h(stringResource(R.string.show_status_bar), "status bar show hide", ScrollKeys.STATUS_BAR)
        h(stringResource(R.string.dark_status_bar_label), "dark status bar light dark", ScrollKeys.HOME_DARK_STATUS_BAR)
        h(stringResource(R.string.status_bar_clock_label), "status bar clock hide dynamic", ScrollKeys.HOME_STATUS_BAR_CLOCK)
        h(stringResource(R.string.minus_one_enable), "feed google discover news enable", ScrollKeys.HOME_FEED)
        h(stringResource(R.string.show_labels), "show labels app name home screen", ScrollKeys.HOME_SHOW_LABELS)
        h(stringResource(R.string.label_size), "label size text size home screen", ScrollKeys.HOME_LABEL_SIZE)

        // ── Smartspace / At a Glance ──────────────────────────────────
        val smartIcon = if (isSmartspaceEnabled) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off
        fun s(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelSmartspace, smartIcon, Smartspace, sk))
        s(stringResource(R.string.smartspace_widget_toggle_label), "show at a glance home screen enable toggle")
        s(stringResource(R.string.smartspace_mode_label), "provider google smartspacer lawnchair mode", ScrollKeys.SS_MODE)
        s(stringResource(R.string.smartspace_weather), "weather temperature forecast rain sun")
        s(stringResource(R.string.smartspace_weather_source), "weather source provider open-meteo pirate openweathermap accuweather", ScrollKeys.SS_WEATHER_SOURCE)
        s(stringResource(R.string.smartspace_battery_status), "battery charging status level indicator")
        s(stringResource(R.string.smartspace_now_playing), "now playing music media track song")
        s(stringResource(R.string.smartspace_date_and_time), "date time clock format 12h 24h")
        s(stringResource(R.string.smartspace_calendar), "calendar gregorian persian lunar system", ScrollKeys.SS_CALENDAR)
        s(stringResource(R.string.smartspace_date), "date show hide", ScrollKeys.SS_DATE)
        s(stringResource(R.string.smartspace_time), "time show hide clock", ScrollKeys.SS_TIME)
        s(stringResource(R.string.smartspace_time_format), "time format 12h 24h", ScrollKeys.SS_TIME_FORMAT)
        s(stringResource(R.string.smartspace_weather_refresh_interval), "weather refresh interval", ScrollKeys.SS_WEATHER_INTERVAL)
        s(stringResource(R.string.smartspace_weather_city), "city location weather gps auto", ScrollKeys.SS_WEATHER_CITY)
        s(stringResource(R.string.smartspace_weather_unit), "temperature unit celsius fahrenheit kelvin", ScrollKeys.SS_WEATHER_UNIT)

        // ── Dock ──────────────────────────────────────────────────────
        fun d(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelDock, R.drawable.ic_dock, Dock, sk))
        d(stringResource(R.string.show_hotseat_title), "show hide dock hotseat enable", ScrollKeys.SHOW_DOCK)
        d(stringResource(R.string.hotseat_background), "dock background show hide", ScrollKeys.DOCK_BG)
        d(stringResource(R.string.show_labels), "dock labels show hide", ScrollKeys.DOCK_SHOW_LABELS)
        d(stringResource(R.string.hotseat_mode_label), "search bar widget google lawnchair disabled dock")
        d(stringResource(R.string.search_bar_settings), "search bar dock corner radius background settings")
        d(stringResource(R.string.dock_icons), "dock icon count columns hotseat number", ScrollKeys.DOCK_ICONS)
        d(stringResource(R.string.hotseat_bottom_space_label), "bottom padding spacing dock margin", ScrollKeys.DOCK_BOTTOM_SPACE)
        d(stringResource(R.string.page_indicator_height), "page indicator dots height size", ScrollKeys.DOCK_PAGE_INDICATOR)
        d(stringResource(R.string.corner_radius_label), "corner radius search bar rounded")
        d(stringResource(R.string.qsb_hotseat_background_transparency), "search bar background opacity transparent")

        // ── App drawer ────────────────────────────────────────────────
        if (!deckLayout.state.value) {
            fun a(label: String, kw: String = "", sk: String? = null) =
                add(SearchableEntry(label, kw, labelAppDrawer, R.drawable.ic_apps, AppDrawer, sk))
            a(stringResource(R.string.hidden_apps_label), "hide apps from drawer hidden list", ScrollKeys.HIDDEN_APPS)
            a(stringResource(R.string.app_drawer_columns), "columns grid app drawer layout count", ScrollKeys.DRAWER_COLUMNS)
            a(stringResource(R.string.row_height_label), "row height size spacing compact", ScrollKeys.DRAWER_ROW_HEIGHT)
            a(stringResource(R.string.pref_all_apps_show_scrollbar_title), "scrollbar show hide fast scroll", ScrollKeys.DRAWER_SCROLLBAR)
            a(stringResource(R.string.pref_all_apps_remember_position_title), "remember position scroll app drawer keep", ScrollKeys.DRAWER_REMEMBER)
            a(stringResource(R.string.pref_all_apps_bulk_icon_loading_title), "bulk load icons performance speed")
            a(stringResource(R.string.app_drawer_haptic_feedback_label), "haptic vibration feedback touch")
            a(stringResource(R.string.app_drawer_indent_label), "padding horizontal indent margin spacing")
        }

        // ── Search bar ────────────────────────────────────────────────
        fun sb(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelSearchBar, R.drawable.ic_search, Search(), sk))
        sb(stringResource(R.string.show_app_search_bar), "show search bar drawer enable disable", ScrollKeys.DS_SHOW_SEARCH_BAR)
        sb(stringResource(R.string.pref_search_auto_show_keyboard), "auto keyboard search show automatically", ScrollKeys.DS_AUTO_KEYBOARD)
        sb(stringResource(R.string.app_search_algorithm), "algorithm global search on-device ASI app search", ScrollKeys.DS_ALGORITHM)
        sb(stringResource(R.string.allapps_match_qsb_style_label), "match dock search bar actions style", ScrollKeys.DS_MATCH_QSB)
        sb(stringResource(R.string.search_pref_result_web_title), "web suggestions search results")
        sb(stringResource(R.string.search_pref_result_people_title), "contacts people search results")
        sb(stringResource(R.string.search_pref_result_files_title), "files search results")
        sb(stringResource(R.string.search_pref_result_history_title), "search history results")
        sb(stringResource(R.string.all_apps_search_result_calculator), "calculator search")
        sb(stringResource(R.string.hotseat_mode_label), "search bar widget google lawnchair disabled dock mode", ScrollKeys.DOCK_SEARCH_MODE)
        sb(stringResource(R.string.search_provider), "search engine google duckduckgo bing startpage dock", ScrollKeys.DOCK_SEARCH_PROVIDER)
        sb(stringResource(R.string.apply_accent_color_label), "accent color tint dock search bar", ScrollKeys.DOCK_SEARCH_ACCENT)
        sb(stringResource(R.string.corner_radius_label), "corner radius dock search bar rounded", ScrollKeys.DOCK_SEARCH_RADIUS)
        sb(stringResource(R.string.qsb_hotseat_background_transparency), "search bar background opacity transparent", ScrollKeys.DOCK_SEARCH_OPACITY)

        // ── Folders ───────────────────────────────────────────────────
        fun f(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelFolders, R.drawable.ic_folder, Folders, sk))
        f(stringResource(R.string.max_folder_columns), "folder columns maximum grid count", ScrollKeys.FOLDER_MAX_COLUMNS)
        f(stringResource(R.string.max_folder_rows), "folder rows maximum grid count", ScrollKeys.FOLDER_MAX_ROWS)
        f(stringResource(R.string.folder_bg_opacity_label), "folder background opacity transparency", ScrollKeys.FOLDER_BG_OPACITY)
        f(stringResource(R.string.folder_preview_bg_opacity_label), "folder icon preview background opacity", ScrollKeys.FOLDER_PREVIEW_OPACITY)
        f(stringResource(R.string.folder_shape_label), "folder shape icon shape style", ScrollKeys.FOLDER_SHAPE)

        // ── Gestures ──────────────────────────────────────────────────
        fun ge(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelGestures, R.drawable.ic_gestures, Gestures, sk))
        ge(stringResource(R.string.gesture_double_tap), "double tap action sleep lock screen", ScrollKeys.GESTURE_DOUBLE_TAP)
        ge(stringResource(R.string.gesture_swipe_up), "swipe up drawer recents app", ScrollKeys.GESTURE_SWIPE_UP)
        ge(stringResource(R.string.gesture_swipe_down), "swipe down notifications quick settings panel", ScrollKeys.GESTURE_SWIPE_DOWN)
        ge(stringResource(R.string.gesture_two_finger_swipe_down), "two finger swipe down notifications", ScrollKeys.GESTURE_2F_DOWN)
        ge(stringResource(R.string.gesture_two_finger_swipe_up), "two finger swipe up recents", ScrollKeys.GESTURE_2F_UP)
        ge(stringResource(R.string.gesture_home_tap), "home button tap gesture action", ScrollKeys.GESTURE_HOME)
        ge(stringResource(R.string.gesture_back_tap), "back button tap gesture action", ScrollKeys.GESTURE_BACK)
        ge(stringResource(R.string.sleep_mode_label), "sleep mode lock screen accessibility root admin", ScrollKeys.GESTURE_SLEEP_MODE)

        // ── Quickstep / Recents ───────────────────────────────────────
        if (LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
            fun q(label: String, kw: String = "", sk: String? = null) =
                add(SearchableEntry(label, kw, labelQuickstep, R.drawable.ic_quickstep, Quickstep, sk))
            q(stringResource(R.string.recents_clear_all), "clear all recents close apps button")
            q(stringResource(R.string.window_corner_radius_label), "screen corner radius recents card", ScrollKeys.QS_CORNER_RADIUS)
            q(stringResource(R.string.taskbar_label), "taskbar show experimental enable", ScrollKeys.QS_TASKBAR)
            q(stringResource(R.string.translucent_background), "translucent background recents opacity blur", ScrollKeys.QS_TRANSLUCENT)
            q(stringResource(R.string.recents_lock_unlock), "lock unlock recents prevent close clear all")
        }

        // ── Backup and restore ────────────────────────────────────────
        fun b(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelBackup, R.drawable.backup_restore, BackupAndRestore, sk))
        b(stringResource(R.string.create_backup), "export backup save layout settings create", ScrollKeys.CREATE_BACKUP)
        b(stringResource(R.string.restore_backup), "import restore backup load file", ScrollKeys.RESTORE_BACKUP)

        // ── Extras ────────────────────────────────────────────────────
        fun e(label: String, kw: String = "", sk: String? = null, route: PreferenceRootRoute = Extras) =
            add(SearchableEntry(label, kw, labelExtras, R.drawable.ic_extras, route, sk))
        e(stringResource(R.string.experimental_features_label), "experimental beta unstable features labs", ScrollKeys.EXPERIMENTAL)
        e(stringResource(R.string.debug_menu_label), "debug menu developer options", ScrollKeys.DEBUG_MENU)
        e(stringResource(R.string.debug_restart_launcher), "restart lawnchair launcher reboot", ScrollKeys.RESTART)
        e(stringResource(R.string.wallpaper_blur), "blur wallpaper background frosted experimental", route = ExperimentalFeatures)
        e(stringResource(R.string.font_picker_label), "font customization typography typeface heading body weight", ScrollKeys.FONT_PICKER, route = ExperimentalFeatures)
        e(stringResource(R.string.show_deck_layout), "deck layout drawerless no app drawer all apps home", ScrollKeys.DECK_LAYOUT, route = ExperimentalFeatures)
        e(stringResource(R.string.icon_swipe_gestures), "icon swipe left right gesture shortcut", ScrollKeys.ICON_SWIPE, route = ExperimentalFeatures)
        e(stringResource(R.string.gesturenavcontract_label), "gesturenavcontract api gesture navigation enhanced animation", ScrollKeys.GNC, route = ExperimentalFeatures)
        e(stringResource(R.string.workspace_increase_max_grid_size_label), "max grid size 20x20 increase workspace", ScrollKeys.MAX_GRID_SIZE, route = ExperimentalFeatures)
        e(stringResource(R.string.always_reload_icons_label), "always reload icons cache refresh icon pack", ScrollKeys.ALWAYS_RELOAD_ICONS, route = ExperimentalFeatures)

        // ── About ─────────────────────────────────────────────────────
        fun ab(label: String, kw: String = "", sk: String? = null) =
            add(SearchableEntry(label, kw, labelAbout, R.drawable.ic_about, About, sk))
        ab(stringResource(R.string.auto_updater_label), "auto updater check update nightly automatic")
        ab(stringResource(R.string.updater), "update download install version changelog")
        ab(stringResource(R.string.github), "github source code repo open source contribute")
        ab(stringResource(R.string.telegram), "telegram community chat news channel")
        ab(stringResource(R.string.discord), "discord community server chat")
        ab(stringResource(R.string.donate), "donate support fund contribute money")
        ab(stringResource(R.string.privacy_policy), "privacy policy data collection")
        ab(stringResource(R.string.acknowledgements), "acknowledgements credits libraries third party")
        ab("Contributors", "team developers contributors design art")
    }

    // ── Debug badge ───────────────────────────────────────────────────────
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
                TextButton(onClick = { showDebugDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    // ── Back handler ──────────────────────────────────────────────────────
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    // ── Root: animated switch between settings list and search overlay ────
    // Opening: search overlay slides UP from 1/3 height (where the bar sits)
    // + fades in. Closing: slides back down + fades out. The settings list
    // itself just fades — no expand/shrink so nothing jumps or clips.
    AnimatedContent(
        targetState = searchActive,
        transitionSpec = {
            if (targetState) {
                // → entering search
                (slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(240)))
                    .togetherWith(fadeOut(tween(180)))
            } else {
                // ← leaving search
                fadeIn(tween(200))
                    .togetherWith(slideOutVertically(tween(280)) { it / 3 } + fadeOut(tween(200)))
            }
        },
        label = "settings_search_transition",
        modifier = modifier,
    ) { isSearching ->
        if (isSearching) {
            SearchOverlay(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    searchActive = false
                    searchQuery = ""
                },
                entries = allEntries,
                onNavigate = onNavigate,
            )
        } else {
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
                // ── Announcement card ─────────────────────────────────────
                AnimatedVisibility(
                    visible = announcementShowing,
                    enter = expandVertically(tween(350)) + fadeIn(tween(350)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
                ) {
                    AnnouncementPreference()
                }

                // ── Set default card ──────────────────────────────────────
                AnimatedVisibility(
                    visible = isNotDefaultLauncher && !announcementShowing,
                    enter = slideInVertically(tween(350)) { it } + fadeIn(tween(350)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
                ) {
                    PreferencesSetDefaultLauncherCard()
                }

                // ── Search bar — extra spacing below the cards ────────────
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSearchBar(
                    onActivate = { searchActive = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                // ── Preference list ───────────────────────────────────────
                PreferenceGroup {
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelGeneral,
                            description = descGeneral,
                            iconResource = R.drawable.ic_general,
                            onNavigate = { onNavigate(General) },
                            isSelected = currentRoute is General,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelHomeScreen,
                            description = descHomeScreen,
                            iconResource = R.drawable.ic_home_screen,
                            onNavigate = { onNavigate(HomeScreen) },
                            isSelected = currentRoute is HomeScreen,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelSmartspace,
                            description = descSmartspace,
                            iconResource = if (isSmartspaceEnabled) R.drawable.ic_smartspace else R.drawable.ic_smartspace_off,
                            onNavigate = { onNavigate(Smartspace) },
                            isSelected = currentRoute is Smartspace,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelDock,
                            description = descDock,
                            iconResource = R.drawable.ic_dock,
                            onNavigate = { onNavigate(Dock) },
                            isSelected = currentRoute is Dock,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(key = "app_drawer", visible = !deckLayout.state.value) {
                        PreferenceCategory(
                            label = labelAppDrawer,
                            description = descAppDrawer,
                            iconResource = R.drawable.ic_apps,
                            onNavigate = { onNavigate(AppDrawer) },
                            isSelected = currentRoute is AppDrawer,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelSearchBar,
                            description = descSearchBar,
                            iconResource = R.drawable.ic_search,
                            onNavigate = { onNavigate(Search()) },
                            isSelected = currentRoute is Search,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelFolders,
                            description = descFolders,
                            iconResource = R.drawable.ic_folder,
                            onNavigate = { onNavigate(Folders) },
                            isSelected = currentRoute is Folders,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelGestures,
                            description = descGestures,
                            iconResource = R.drawable.ic_gestures,
                            onNavigate = { onNavigate(Gestures) },
                            isSelected = currentRoute is Gestures,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(key = "quickstep", visible = LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
                        PreferenceCategory(
                            label = labelQuickstep,
                            description = descQuickstep,
                            iconResource = R.drawable.ic_quickstep,
                            onNavigate = { onNavigate(Quickstep) },
                            isSelected = currentRoute is Quickstep,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelBackup,
                            description = descBackup,
                            iconResource = R.drawable.backup_restore,
                            onNavigate = { onNavigate(BackupAndRestore) },
                            isSelected = currentRoute is BackupAndRestore,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelExtras,
                            description = descExtras,
                            iconResource = R.drawable.ic_extras,
                            onNavigate = { onNavigate(Extras) },
                            isSelected = currentRoute is Extras,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                    Item(visible = true) {
                        PreferenceCategory(
                            label = labelAbout,
                            description = aboutDescription,
                            iconResource = R.drawable.ic_about,
                            onNavigate = { onNavigate(About) },
                            isSelected = currentRoute is About,
                            isFirst = it.isFirst,
                            isLast = it.isLast,
                        )
                    }
                }
            }
        }
    }
}

// ── Full-screen search overlay ────────────────────────────────────────────────
@Composable
private fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    entries: List<SearchableEntry>,
    onNavigate: (PreferenceRootRoute) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Match label + keywords; breadcrumb is excluded so typing a section name
    // does not flood results with every item inside it.
    val filtered = remember(query, entries) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            entries.filter {
                it.label.lowercase().contains(q) ||
                    it.keywords.lowercase().contains(q)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
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
                keyboardActions = KeyboardActions(onSearch = { /* keep keyboard open */ }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )

            when {
                query.isBlank() -> {
                    // Empty state — nothing yet
                }
                filtered.isEmpty() -> {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "No results for \"$query\"",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                else -> {
                    LazyColumn {
                        items(filtered.size) { idx ->
                            val entry = filtered[idx]
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
                                isFirst = idx == 0,
                                isLast = idx == filtered.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tap-to-activate search bar ────────────────────────────────────────────────
@Composable
private fun SettingsSearchBar(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onActivate,
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

// ── Set default launcher card ─────────────────────────────────────────────────
@Composable
fun PreferencesSetDefaultLauncherCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        PreferenceTemplate(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    Intent(Settings.ACTION_HOME_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .let { context.startActivity(it) }
                    (context as? Activity)?.finish()
                },
            title = {},
            description = {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(id = R.string.set_default_launcher_tip),
                    color = MaterialTheme.colorScheme.background,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.background,
                    contentDescription = null,
                )
            },
        )
    }
}

/**
 * A small pill-shaped badge rendered in the toolbar actions area.
 * Styled after LibChecker's "CI" badge — bordered chip, slight primary tint.
 * Tapping it shows the debug warning dialog. Only shown in debug/nightly builds.
 */
@Composable
private fun DebugBadge(
    onClick: () -> Unit,
) {
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
