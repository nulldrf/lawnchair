package app.lawnchair.ui.preferences.components.layout

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.delay

// ── Stable scroll key constants ───────────────────────────────────────────────
object ScrollKeys {
    // General
    const val ICON_STYLE             = "general.icon_style"
    const val ICON_SHAPE             = "general.icon_shape"
    const val FONT                   = "general.font"
    const val ACCENT_COLOR           = "general.accent_color"
    const val COLOR_STYLE            = "general.color_style"
    const val NOTIFICATION_DOTS      = "general.notification_dots"
    const val AUTO_ADAPTIVE          = "general.auto_adaptive"
    const val COLORIZED_BG           = "general.colorized_bg"
    const val SHADOW_ICONS           = "general.shadow_icons"
    // Home screen
    const val HOME_GRID              = "home.grid"
    const val INFINITE_SCROLLING     = "home.infinite_scrolling"
    const val AUTO_ADD_SHORTCUTS     = "home.auto_add"
    const val LOCK_HOME              = "home.lock"
    const val WALLPAPER_SCROLL       = "home.wallpaper_scroll"
    const val WALLPAPER_BLUR         = "home.wallpaper_blur"
    const val WALLPAPER_DEPTH        = "home.wallpaper_depth"
    const val POPUP_MENU             = "home.popup_menu"
    const val STATUS_BAR             = "home.status_bar"
    const val HOME_ICON_SIZE         = "home.icon_size"
    const val HOME_SHOW_LABELS       = "home.show_labels"
    const val HOME_LABEL_SIZE        = "home.label_size"
    const val HOME_TEXT_COLOR        = "home.text_color"
    const val HOME_DARK_STATUS_BAR   = "home.dark_status_bar"
    const val HOME_STATUS_BAR_CLOCK  = "home.status_bar_clock"
    const val HOME_ROUNDED_WIDGETS   = "home.rounded_widgets"
    const val HOME_WIDGET_OVERLAP    = "home.widget_overlap"
    const val HOME_FEED              = "home.feed"
    const val HOME_TOP_SHADOW        = "home.top_shadow"
    const val HOME_WIDGET_UNLIMITED  = "home.widget_unlimited"
    const val HOME_WIDGET_RESIZE     = "home.widget_resize"
    const val HOME_APP_OPEN_ANIM     = "home.app_open_anim"
    const val HOME_APP_CLOSE_ANIM    = "home.app_close_anim"
    // Dock
    const val SHOW_DOCK              = "dock.show"
    const val DOCK_SEARCH            = "dock.search"
    const val DOCK_ICONS             = "dock.icons"
    const val DOCK_BOTTOM_SPACE      = "dock.bottom_space"
    const val DOCK_PAGE_INDICATOR    = "dock.page_indicator"
    const val DOCK_BG                = "dock.bg"
    const val DOCK_SHOW_LABELS       = "dock.show_labels"
    // App drawer
    const val HIDDEN_APPS            = "drawer.hidden"
    const val DRAWER_COLUMNS         = "drawer.columns"
    const val DRAWER_ROW_HEIGHT      = "drawer.row_height"
    const val DRAWER_SCROLLBAR       = "drawer.scrollbar"
    const val DRAWER_REMEMBER        = "drawer.remember"
    // Folders
    const val FOLDER_SHAPE           = "folder.shape"
    const val FOLDER_MAX_COLUMNS     = "folder.max_columns"
    const val FOLDER_MAX_ROWS        = "folder.max_rows"
    const val FOLDER_BG_OPACITY      = "folder.bg_opacity"
    const val FOLDER_PREVIEW_OPACITY = "folder.preview_opacity"
    // Gestures
    const val GESTURE_DOUBLE_TAP     = "gesture.double_tap"
    const val GESTURE_SWIPE_UP       = "gesture.swipe_up"
    const val GESTURE_SWIPE_DOWN     = "gesture.swipe_down"
    const val GESTURE_2F_DOWN        = "gesture.2f_down"
    const val GESTURE_2F_UP          = "gesture.2f_up"
    const val GESTURE_HOME           = "gesture.home"
    const val GESTURE_BACK           = "gesture.back"
    const val GESTURE_SLEEP_MODE     = "gesture.sleep_mode"
    // Quickstep
    const val QS_TRANSLUCENT         = "quickstep.translucent"
    const val QS_CORNER_RADIUS       = "quickstep.corner_radius"
    const val QS_TASKBAR             = "quickstep.taskbar"
    // Backup
    const val CREATE_BACKUP          = "backup.create"
    const val RESTORE_BACKUP         = "backup.restore"
    // Extras top-level
    const val EXPERIMENTAL           = "extras.experimental"
    const val DEBUG_MENU             = "extras.debug"
    const val RESTART                = "extras.restart"
    // Experimental features
    const val FONT_PICKER            = "exp.font_picker"
    const val MAX_GRID_SIZE          = "exp.max_grid"
    const val ICON_SWIPE             = "exp.icon_swipe"
    const val DECK_LAYOUT            = "exp.deck_layout"
    const val ALWAYS_RELOAD_ICONS    = "exp.reload_icons"
    const val GNC                    = "exp.gnc"
    // Smartspace
    const val SS_MODE                = "ss.mode"
    const val SS_WEATHER_SOURCE      = "ss.weather_source"
    const val SS_WEATHER_CITY        = "ss.weather_city"
    const val SS_WEATHER_UNIT        = "ss.weather_unit"
    const val SS_WEATHER_INTERVAL    = "ss.weather_interval"
    const val SS_DATE                = "ss.date"
    const val SS_TIME                = "ss.time"
    const val SS_CALENDAR            = "ss.calendar"
    const val SS_TIME_FORMAT         = "ss.time_format"
    // Drawer search
    const val DS_SHOW_SEARCH_BAR     = "dsearch.show_bar"
    const val DS_AUTO_KEYBOARD       = "dsearch.auto_keyboard"
    const val DS_ALGORITHM           = "dsearch.algorithm"
    const val DS_MATCH_QSB           = "dsearch.match_qsb"
    // Dock search
    const val DOCK_SEARCH_MODE       = "docksearch.mode"
    const val DOCK_SEARCH_PROVIDER   = "docksearch.provider"
    const val DOCK_SEARCH_ACCENT     = "docksearch.accent"
    const val DOCK_SEARCH_RADIUS     = "docksearch.radius"
    const val DOCK_SEARCH_OPACITY    = "docksearch.opacity"
}

// ── Singleton key handoff ─────────────────────────────────────────────────────
object ScrollTargetManager {
    private var pending: String? = null
    fun set(key: String) { pending = key }
    fun consume(): String? = pending.also { pending = null }
}

// ── Per-screen scroll state ───────────────────────────────────────────────────
class PreferenceScrollState(val scrollKey: String?)

@Composable
fun rememberPreferenceScrollState(): PreferenceScrollState {
    val scrollKey = remember { ScrollTargetManager.consume() }
    return remember(scrollKey) { PreferenceScrollState(scrollKey) }
}

/**
 * Wraps [content] and, if [key] matches the pending scroll target:
 *
 * 1. **Scrolls** to this item via [NestedScrollView.smoothScrollTo] after a
 *    300ms postDelayed (navigation shared-axis transition is ~300ms).
 *    We pass `y - gap` directly — no custom maxScroll clamp.  Our previous
 *    clamp using `child.measuredHeight` was stale: [EagerLazyListScope] adds
 *    items lazily after the first frame, so the child height keeps growing
 *    after [onGloballyPositioned] fires.  [NestedScrollView.smoothScrollTo]
 *    calls `clamp()` internally on every frame of the animation, so it always
 *    respects the final content height even if it grew after we posted.
 *
 * 2. **Highlights** the item with a brief primary-color flash once the scroll
 *    has settled (~800ms after mount), so the user can see which setting was
 *    navigated to.  Uses [MaterialTheme.colorScheme.primary] so it respects
 *    the current dynamic-color theme in both light and dark mode.
 */
@Composable
fun ScrollAnchor(
    key: String,
    state: PreferenceScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Fast path: not the scroll target — zero overhead.
    if (state.scrollKey != key) {
        content()
        return
    }

    val view = LocalView.current
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val hasScheduled = remember { booleanArrayOf(false) }

    // Drives the highlight overlay.
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // 300ms postDelayed + ~450ms smooth scroll animation + safety margin.
        delay(800)
        alpha.animateTo(1f, tween(180))
        delay(600)
        alpha.animateTo(0f, tween(600))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (alpha.value > 0f) {
                    drawRect(color = primaryColor, alpha = alpha.value * 0.18f)
                }
            }
            .onGloballyPositioned { coords ->
                if (!hasScheduled[0]) {
                    hasScheduled[0] = true
                    val y = coords.positionInRoot().y.toInt()
                    val gap = (24f * density.density).toInt()

                    // Step 1: wait for navigation transition to settle (300ms),
                    // then collapse AppBar so NestedScrollView has its full range.
                    view.postDelayed({
                        if (!view.isAttachedToWindow) return@postDelayed

                        // Walk up to find NestedScrollView and CoordinatorLayout.
                        var nsv: NestedScrollView? = null
                        var p: android.view.ViewParent? = view.parent
                        while (p != null) {
                            if (p is NestedScrollView && nsv == null) nsv = p
                            if (p is androidx.coordinatorlayout.widget.CoordinatorLayout) {
                                for (i in 0 until p.childCount) {
                                    val child = p.getChildAt(i)
                                    if (child is AppBarLayout) {
                                        child.setExpanded(false, false)
                                        break
                                    }
                                }
                                break
                            }
                            p = p.parent
                        }

                        // Step 2: wait one more frame for the AppBar collapse layout
                        // pass AND for EagerLazyListScope to finish adding items,
                        // then scroll. Using postOnAnimation ensures the View tree
                        // has fully remeasured before we read getScrollRange().
                        nsv?.postOnAnimation {
                            if (view.isAttachedToWindow) {
                                nsv.smoothScrollTo(0, maxOf(0, y - gap))
                            }
                        }
                    }, 300L)
                }
            },
    ) {
        content()
    }
}

// Kept for API compatibility — prefer ScrollAnchor composable.
fun Modifier.preferenceScrollTarget(key: String, state: PreferenceScrollState): Modifier =
    if (state.scrollKey != key) this else this
