package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.widget.NestedScrollView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

// ── Stable scroll key constants ───────────────────────────────────────────────
object ScrollKeys {
    // General
    const val ICON_STYLE           = "general.icon_style"
    const val ICON_SHAPE           = "general.icon_shape"
    const val FONT                 = "general.font"
    const val ACCENT_COLOR         = "general.accent_color"
    const val COLOR_STYLE          = "general.color_style"
    const val NOTIFICATION_DOTS    = "general.notification_dots"
    const val AUTO_ADAPTIVE        = "general.auto_adaptive"
    const val COLORIZED_BG         = "general.colorized_bg"
    const val SHADOW_ICONS         = "general.shadow_icons"
    // Home screen
    const val HOME_GRID            = "home.grid"
    const val INFINITE_SCROLLING   = "home.infinite_scrolling"
    const val AUTO_ADD_SHORTCUTS   = "home.auto_add"
    const val LOCK_HOME            = "home.lock"
    const val WALLPAPER_SCROLL     = "home.wallpaper_scroll"
    const val WALLPAPER_BLUR       = "home.wallpaper_blur"
    const val WALLPAPER_DEPTH      = "home.wallpaper_depth"
    const val POPUP_MENU           = "home.popup_menu"
    const val STATUS_BAR           = "home.status_bar"
    // Dock
    const val SHOW_DOCK            = "dock.show"
    const val DOCK_SEARCH          = "dock.search"
    const val DOCK_ICONS           = "dock.icons"
    const val DOCK_BOTTOM_SPACE    = "dock.bottom_space"
    const val DOCK_PAGE_INDICATOR  = "dock.page_indicator"
    // App drawer
    const val HIDDEN_APPS          = "drawer.hidden"
    const val DRAWER_COLUMNS       = "drawer.columns"
    const val DRAWER_ROW_HEIGHT    = "drawer.row_height"
    const val DRAWER_SCROLLBAR     = "drawer.scrollbar"
    const val DRAWER_REMEMBER      = "drawer.remember"
    // Folders
    const val FOLDER_SHAPE         = "folder.shape"
    const val FOLDER_MAX_COLUMNS   = "folder.max_columns"
    const val FOLDER_MAX_ROWS      = "folder.max_rows"
    const val FOLDER_BG_OPACITY    = "folder.bg_opacity"
    const val FOLDER_PREVIEW_OPACITY = "folder.preview_opacity"
    // Gestures
    const val GESTURE_DOUBLE_TAP   = "gesture.double_tap"
    const val GESTURE_SWIPE_UP     = "gesture.swipe_up"
    const val GESTURE_SWIPE_DOWN   = "gesture.swipe_down"
    const val GESTURE_2F_DOWN      = "gesture.2f_down"
    const val GESTURE_2F_UP        = "gesture.2f_up"
    const val GESTURE_HOME         = "gesture.home"
    const val GESTURE_BACK         = "gesture.back"
    const val GESTURE_SLEEP_MODE   = "gesture.sleep_mode"
    // Quickstep
    const val QS_TRANSLUCENT       = "quickstep.translucent"
    const val QS_CORNER_RADIUS     = "quickstep.corner_radius"
    const val QS_TASKBAR           = "quickstep.taskbar"
    // Backup
    const val CREATE_BACKUP        = "backup.create"
    const val RESTORE_BACKUP       = "backup.restore"
    // Extras top-level
    const val EXPERIMENTAL         = "extras.experimental"
    const val DEBUG_MENU           = "extras.debug"
    const val RESTART              = "extras.restart"
    // Experimental features (in ExperimentalFeaturesPreferences)
    const val FONT_PICKER          = "exp.font_picker"
    const val MAX_GRID_SIZE        = "exp.max_grid"
    const val ICON_SWIPE           = "exp.icon_swipe"
    const val DECK_LAYOUT          = "exp.deck_layout"
    const val ALWAYS_RELOAD_ICONS  = "exp.reload_icons"
    const val GNC                  = "exp.gnc"
}

// ── Singleton key handoff ─────────────────────────────────────────────────────
// Set before navigating; consumed once by the destination.
object ScrollTargetManager {
    private var pending: String? = null
    fun set(key: String) { pending = key }
    fun consume(): String? = pending.also { pending = null }
}

// ── Per-screen scroll state ───────────────────────────────────────────────────
class PreferenceScrollState(
    internal val offsets: SnapshotStateMap<String, Int>,
    val scrollKey: String?,
)

/**
 * Call at the top of each destination composable to get a [PreferenceScrollState].
 * Consumes any pending key from [ScrollTargetManager], then scrolls the outer
 * [NestedScrollView] to the target item once its position has been measured.
 */
@Composable
fun rememberPreferenceScrollState(): PreferenceScrollState {
    val scrollKey = remember { ScrollTargetManager.consume() }
    val offsets   = remember { SnapshotStateMap<String, Int>() }
    val view      = LocalView.current
    val density   = LocalDensity.current

    if (scrollKey != null) {
        LaunchedEffect(scrollKey) {
            // Poll offsets until the target item has been laid out.
            flow {
                while (true) {
                    emit(offsets[scrollKey])
                    delay(16)
                }
            }
                .filterNotNull()
                .collect { y ->
                    // Let the navigation enter transition finish first.
                    delay(100)
                    val gap = with(density) { 24.dp.toPx().toInt() }
                    var p = view.parent
                    while (p != null) {
                        if (p is NestedScrollView) {
                            p.smoothScrollTo(0, maxOf(0, y - gap))
                            return@collect
                        }
                        p = (p as? android.view.ViewParent)?.parent
                    }
                }
        }
    }

    return remember(scrollKey) { PreferenceScrollState(offsets, scrollKey) }
}

/**
 * Wrap a preference item with this to register its Y position for scroll-to targeting.
 * Zero overhead when no scroll is pending.
 */
fun Modifier.preferenceScrollTarget(key: String, state: PreferenceScrollState): Modifier =
    if (state.scrollKey != key) this
    else onGloballyPositioned { coords ->
        state.offsets[key] = coords.positionInRoot().y.toInt()
    }

/**
 * Convenience composable that marks its content as a scroll target for [key].
 */
@Composable
fun ScrollAnchor(
    key: String,
    state: PreferenceScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .preferenceScrollTarget(key, state),
    ) {
        content()
    }
}
