package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Stable keys used to scroll to and highlight a target preference row.
object ScrollKeys {
    // General
    const val THEME = "general.theme"
    const val HOME_ROTATION = "general.home_rotation"
    const val AUTO_UPDATER = "general.auto_updater"
    const val LIVE_INFORMATION = "general.live_information"
    const val FONT_WORKSPACE = "general.font_workspace"
    const val FONT_HEADING = "general.font_heading"
    const val FONT_HEADING_MEDIUM = "general.font_heading_medium"
    const val FONT_BODY = "general.font_body"
    const val FONT_BODY_MEDIUM = "general.font_body_medium"
    const val ICON_PACK = "general.icon_pack"
    const val THEMED_ICONS = "general.themed_icons"
    const val ICON_SHAPE = "general.icon_shape"
    const val ADVANCED_ICONS = "general.advanced_icons"
    const val ACCENT_COLOR = "general.accent_color"
    const val COLOR_STYLE = "general.color_style"
    const val COLOR_SPEC = "general.color_spec"
    const val NOTIFICATION_DOTS = "general.notification_dots"
    const val NOTIFICATION_DOT_COLOR = "general.notification_dot_color"
    const val NOTIFICATION_COUNT = "general.notification_count"
    const val NOTIFICATION_DOT_TEXT_COLOR = "general.notification_dot_text_color"
    const val SETTINGS_BLUR = "general.settings_blur"
    const val SETTINGS_BLUR_INTENSITY = "general.settings_blur_intensity"

    // General -> Themed icons
    const val FALLBACK_ICON_SOURCE = "icons.themed.fallback_source"
    const val ENFORCE_THEMED_ICONS = "icons.themed.enforce"
    const val TRANSPARENT_ICON_BG = "icons.themed.transparent_bg"

    // General -> Advanced icons
    const val ALWAYS_RELOAD_ICONS = "icons.advanced.reload_icons"
    const val SHADOW_ICONS = "icons.advanced.shadow_icons"
    const val AUTO_ADAPTIVE = "icons.advanced.auto_adaptive"
    const val BACKGROUND_LIGHTNESS = "icons.advanced.background_lightness"
    const val COLORIZED_BG = "icons.advanced.colorized_bg"
    const val TREAT_WHITE_ADAPTIVE = "icons.advanced.treat_white_adaptive"
    const val COLORIZE_ICON_PACK_BG = "icons.advanced.colorize_icon_pack_bg"

    // Home screen
    const val HOME_LAYOUT = "home.layout"
    const val AUTO_ADD_SHORTCUTS = "home.auto_add"
    const val HOME_DOUBLE_TAP = "home.double_tap"
    const val INFINITE_SCROLLING = "home.infinite_scrolling"
    const val HOME_PAN_TO_MOVE = "home.pan_to_move"
    const val HOME_PAN_TO_MOVE_SENSITIVITY = "home.pan_to_move_sensitivity"
    const val HOME_CLEAR = "home.clear"
    const val HOME_FEED = "home.feed"
    const val HOME_FEED_PROVIDER = "home.feed_provider"
    const val HOME_TEXT_COLOR = "home.text_color"
    const val HOME_ICON_TEXT_COLOR = "home.icon_text_color"
    const val HOME_APP_OPEN_ANIM = "home.app_open_anim"
    const val HOME_APP_CLOSE_ANIM = "home.app_close_anim"
    const val WALLPAPER_SCROLL = "home.wallpaper_scroll"
    const val WALLPAPER_DEPTH = "home.wallpaper_depth"
    const val HOME_TOP_SHADOW = "home.top_shadow"
    const val HOME_GRID = "home.grid"
    const val HOME_PADDING_HORIZONTAL = "home.padding_horizontal"
    const val HOME_PADDING_VERTICAL = "home.padding_vertical"
    const val LOCK_HOME = "home.lock"
    const val POPUP_MENU = "home.popup_menu"
    const val STATUS_BAR = "home.status_bar"
    const val HOME_DARK_STATUS_BAR = "home.dark_status_bar"
    const val HOME_STATUS_BAR_CLOCK = "home.status_bar_clock"
    const val HOME_ICON_SIZE = "home.icon_size"
    const val HOME_SHOW_LABELS = "home.show_labels"
    const val HOME_LABEL_SIZE = "home.label_size"
    const val HOME_TWO_LINE = "home.two_line"
    const val HOME_RESET_CUSTOM_ICONS = "home.reset_custom_icons"
    const val HOME_ROUNDED_WIDGETS = "home.rounded_widgets"
    const val HOME_WIDGET_OVERLAP = "home.widget_overlap"
    const val HOME_WIDGET_UNLIMITED = "home.widget_unlimited"
    const val HOME_WIDGET_RESIZE = "home.widget_resize"
    const val HOME_WIDGET_PADDING = "home.widget_padding"

    // Smartspace
    const val SS_MODE = "ss.mode"
    const val SS_WEATHER_SOURCE = "ss.weather_source"
    const val SS_API_KEY = "ss.api_key"
    const val SS_WEATHER_CITY = "ss.weather_city"
    const val SS_WEATHER_UNIT = "ss.weather_unit"
    const val SS_WEATHER_ICON_PACK = "ss.weather_icon_pack"
    const val SS_WEATHER_INTERVAL = "ss.weather_interval"
    const val SS_WEATHER_REFRESH_NOW = "ss.weather_refresh_now"
    const val SS_WEATHER_WIDGET = "ss.weather_widget"
    const val SS_BATTERY_STATUS = "ss.battery_status"
    const val SS_FLASHLIGHT = "ss.flashlight"
    const val SS_NOW_PLAYING = "ss.now_playing"
    const val SS_ONBOARDING = "ss.onboarding"
    const val SS_PERSONALITY = "ss.personality"
    const val SS_DATE = "ss.date"
    const val SS_CALENDAR = "ss.calendar"
    const val SS_TIME = "ss.time"
    const val SS_TIME_FORMAT = "ss.time_format"
    const val SS_SMARTSPACER_MAX_TARGETS = "ss.smartspacer_max_targets"
    const val SS_SMARTSPACER_OPEN_SETTINGS = "ss.smartspacer_open_settings"

    // Dock
    const val DOCK_BG = "dock.bg"
    const val DOCK_BG_COLOR = "dock.bg_color"
    const val DOCK_BG_CORNER_RADIUS = "dock.bg_corner_radius"
    const val DOCK_BG_OPACITY = "dock.bg_opacity"
    const val DOCK_BG_LEFT_MARGIN = "dock.bg_left_margin"
    const val DOCK_BG_RIGHT_MARGIN = "dock.bg_right_margin"
    const val DOCK_BG_TOP_MARGIN = "dock.bg_top_margin"
    const val DOCK_BG_BOTTOM_MARGIN = "dock.bg_bottom_margin"
    const val DOCK_SEARCH = "dock.search"
    const val DOCK_ICONS = "dock.icons"
    const val DOCK_BOTTOM_SPACE = "dock.bottom_space"
    const val DOCK_PAGE_INDICATOR = "dock.page_indicator"
    const val DOCK_ROWS = "dock.rows"
    const val DOCK_PAGES = "dock.pages"
    const val DOCK_SHOW_LABELS = "dock.show_labels"
    const val DOCK_TWO_LINE = "dock.two_line"

    // App drawer
    const val DRAWER_LAYOUT = "drawer.layout"
    const val DRAWER_FOLDERS = "drawer.folders"
    const val HIDDEN_APPS = "drawer.hidden"
    const val DRAWER_SEARCH_ENTRY = "drawer.search_bar_entry"
    const val DRAWER_SEARCH_AT_BOTTOM = "drawer.search_bar_bottom"
    const val DRAWER_SUGGESTIONS = "drawer.suggestions"
    const val DRAWER_HAPTIC_FEEDBACK = "drawer.haptic_feedback"
    const val DRAWER_BG_COLOR = "drawer.bg_color"
    const val DRAWER_BG_OPACITY = "drawer.bg_opacity"
    const val DRAWER_TAB_BG_COLOR = "drawer.tab_bg_color"
    const val DRAWER_TAB_CONTAINER_BG = "drawer.tab_container_bg"
    const val DRAWER_SEARCH_BAR_BG = "drawer.search_bar_bg"
    const val DRAWER_TEXT_COLOR = "drawer.text_color"
    const val DRAWER_BLUR = "drawer.blur"
    const val DRAWER_BLUR_INTENSITY = "drawer.blur_intensity"
    const val DRAWER_COLUMNS = "drawer.columns"
    const val DRAWER_ROW_HEIGHT = "drawer.row_height"
    const val DRAWER_INDENT = "drawer.indent"
    const val DRAWER_TOP_PADDING = "drawer.top_padding"
    const val DRAWER_ICON_SIZE = "drawer.icon_size"
    const val DRAWER_SHOW_LABELS = "drawer.show_labels"
    const val DRAWER_LABEL_SIZE = "drawer.label_size"
    const val DRAWER_TWO_LINE = "drawer.two_line"
    const val DRAWER_REMEMBER = "drawer.remember"
    const val DRAWER_SCROLLBAR = "drawer.scrollbar"

    // App drawer -> Suggestions / Predictions
    const val PREDICTION_MODE = "predictions.mode"
    const val PREDICTION_WEIGHTED_USAGE = "predictions.weighted_usage"
    const val PREDICTION_DISMISSED_APPS = "predictions.dismissed_apps"

    // Search -> Dock search
    const val DOCK_SEARCH_MODE = "docksearch.mode"
    const val DOCK_SEARCH_PROVIDER = "docksearch.provider"
    const val DOCK_SEARCH_ACCENT = "docksearch.accent"
    const val DOCK_SEARCH_RADIUS = "docksearch.radius"
    const val DOCK_SEARCH_OPACITY = "docksearch.opacity"
    const val DOCK_SEARCH_STROKE_WIDTH = "docksearch.stroke_width"
    const val DOCK_SEARCH_STROKE_COLOR = "docksearch.stroke_color"

    // Search -> Drawer search
    const val DS_HIDDEN_APPS = "dsearch.hidden_apps"
    const val DS_AUTO_KEYBOARD = "dsearch.auto_keyboard"
    const val DS_ALGORITHM = "dsearch.algorithm"
    const val DS_MATCH_QSB = "dsearch.match_qsb"
    const val DS_APPS_SHORTCUTS = "dsearch.apps_shortcuts"
    const val DS_SHORTCUTS = "dsearch.shortcuts"
    const val DS_WEB = "dsearch.web"
    const val DS_PEOPLE = "dsearch.people"
    const val DS_TIPS = "dsearch.tips"
    const val DS_FILES = "dsearch.files"
    const val DS_SETTINGS = "dsearch.settings"
    const val DS_HISTORY = "dsearch.history"
    const val DS_CALCULATOR = "dsearch.calculator"

    // Folders
    const val FOLDER_SHAPE = "folder.shape"
    const val FOLDER_ICON_BG_COLOR = "folder.icon_bg_color"
    const val FOLDER_PREVIEW_OPACITY = "folder.preview_opacity"
    const val FOLDER_BG_OPACITY = "folder.bg_opacity"
    const val FOLDER_MAX_COLUMNS = "folder.max_columns"
    const val FOLDER_MAX_ROWS = "folder.max_rows"
    const val FOLDER_SHOW_LABELS = "folder.show_labels"
    const val FOLDER_LABEL_SIZE = "folder.label_size"

    // Gestures
    const val GESTURE_DOUBLE_TAP = "gesture.double_tap"
    const val GESTURE_SWIPE_UP = "gesture.swipe_up"
    const val GESTURE_SWIPE_DOWN = "gesture.swipe_down"
    const val GESTURE_2F_UP = "gesture.2f_up"
    const val GESTURE_2F_DOWN = "gesture.2f_down"
    const val GESTURE_HOME = "gesture.home"
    const val GESTURE_BACK = "gesture.back"
    const val GESTURE_SLEEP_MODE = "gesture.sleep_mode"

    // Quickstep
    const val QS_TRANSLUCENT = "quickstep.translucent"
    const val QS_TRANSLUCENT_ALPHA = "quickstep.translucent_alpha"
    const val QS_RECENTS_ACTIONS = "quickstep.recents_actions"
    const val QS_CORNER_RADIUS = "quickstep.corner_radius"
    const val QS_CORNER_RADIUS_VALUE = "quickstep.corner_radius_value"
    const val QS_TASKBAR = "quickstep.taskbar"

    // Backup
    const val CREATE_BACKUP = "backup.create"
    const val RESTORE_BACKUP = "backup.restore"
    const val RESTORE_NOVA_BACKUP = "backup.restore_nova"

    // Extras
    const val EXPERIMENTAL = "extras.experimental"
    const val DEBUG_MENU = "extras.debug"
    const val RESTART = "extras.restart"
    const val APP_INFO = "extras.app_info"

    // Experimental features
    const val FONT_PICKER = "exp.font_picker"
    const val MAX_GRID_SIZE = "exp.max_grid"
    const val DECK_LAYOUT = "exp.deck_layout"
    const val EXP_WALLPAPER_BLUR = "exp.wallpaper_blur"
    const val EXP_WALLPAPER_BLUR_AMOUNT = "exp.wallpaper_blur_amount"
    const val EXP_WALLPAPER_BLUR_FACTOR = "exp.wallpaper_blur_factor"
    const val GNC = "exp.gnc"
    const val VIBRATION_FEEDBACK_LEVEL = "exp.vibration_feedback_level"
}

// Passes a one-shot scroll target key from settings search to the destination screen.
object ScrollTargetManager {
    private var pending: String? = null
    fun set(key: String) {
        pending = key
    }
    fun consume(): String? = pending.also { pending = null }
}

// Holds the consumed scroll target key for the current destination screen.
class PreferenceScrollState(val scrollKey: String?)

@Composable
fun rememberPreferenceScrollState(): PreferenceScrollState {
    val scrollKey = remember { ScrollTargetManager.consume() }
    return remember(scrollKey) { PreferenceScrollState(scrollKey) }
}

/**
 * Scrolls to and briefly highlights [content] when [key] matches [state.scrollKey].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollAnchor(
    key: String,
    state: PreferenceScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (state.scrollKey != key) {
        content()
        return
    }

    val scrollState = LocalPreferenceColumnScrollState.current
    val topBarState = LocalPreferenceTopBarState.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary

    var itemYInRoot by remember { mutableFloatStateOf(0f) }
    var isPositioned by remember { mutableStateOf(false) }

    val alpha = remember { Animatable(0f) }
    LaunchedEffect(isPositioned) {
        if (!isPositioned) return@LaunchedEffect

        // Wait for the navigation transition before scrolling.
        delay(300)

        // Collapse the top app bar so the full scroll range is available.
        topBarState?.scrollBehavior?.state?.let { barState ->
            if (barState.heightOffsetLimit < 0f) {
                barState.heightOffset = barState.heightOffsetLimit
            }
        }
        // Wait for layout to settle with the collapsed top bar before reading coordinates.
        withFrameNanos {}
        withFrameNanos {}

        val gapPx = with(density) { 24.dp.toPx() }
        if (scrollState != null) {
            val topEdge = (topBarState?.topBarBottomInRoot ?: 0f) + gapPx
            val delta = itemYInRoot - topEdge
            val target = (scrollState.value + delta).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(target, tween(450))
        } else {
            bringIntoViewRequester.bringIntoView()
        }

        alpha.animateTo(1f, tween(180))
        delay(600)
        alpha.animateTo(0f, tween(600))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .drawWithContent {
                drawContent()
                // Draw the highlight overlay on top of opaque card backgrounds.
                if (alpha.value > 0f) {
                    drawRect(color = primaryColor, alpha = alpha.value * 0.18f)
                }
            }
            .onGloballyPositioned { coords ->
                itemYInRoot = coords.positionInRoot().y
                if (!isPositioned) isPositioned = true
            },
    ) {
        content()
    }
}
