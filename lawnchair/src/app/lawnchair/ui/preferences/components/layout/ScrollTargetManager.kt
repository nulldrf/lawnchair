package app.lawnchair.ui.preferences.components.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.widget.NestedScrollView

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
    const val HOME_ICON_SIZE       = "home.icon_size"
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
    // Experimental features
    const val FONT_PICKER          = "exp.font_picker"
    const val MAX_GRID_SIZE        = "exp.max_grid"
    const val ICON_SWIPE           = "exp.icon_swipe"
    const val DECK_LAYOUT          = "exp.deck_layout"
    const val ALWAYS_RELOAD_ICONS  = "exp.reload_icons"
    const val GNC                  = "exp.gnc"
}

// ── Singleton key handoff ─────────────────────────────────────────────────────
// Set before navigating; consumed once by the destination composable.
object ScrollTargetManager {
    private var pending: String? = null
    fun set(key: String) { pending = key }
    fun consume(): String? = pending.also { pending = null }
}

// ── Per-screen scroll state ───────────────────────────────────────────────────
// Intentionally minimal — just carries the key. All scroll logic lives in
// ScrollAnchor so there are no coroutine/snapshotFlow timing hazards.
class PreferenceScrollState(val scrollKey: String?)

/**
 * Call at the top of each destination composable.
 * Consumes any pending key from [ScrollTargetManager] and returns a
 * [PreferenceScrollState] to pass into every [ScrollAnchor] in the screen.
 */
@Composable
fun rememberPreferenceScrollState(): PreferenceScrollState {
    val scrollKey = remember { ScrollTargetManager.consume() }
    return remember(scrollKey) { PreferenceScrollState(scrollKey) }
}

/**
 * Wraps [content] and scrolls to it if [key] matches the pending scroll target.
 *
 * How it works:
 * 1. [onGloballyPositioned] fires during Compose's layout phase with the item's
 *    Y position in the ComposeView coordinate space (= NestedScrollView content space).
 * 2. A single [android.view.View.postDelayed] (300 ms) lets the navigation
 *    shared-axis transition finish before the scroll starts.
 * 3. We walk up the Android view parent chain from [LocalView] (AndroidComposeView)
 *    → ComposeView → contentFrame → StretchNestedScrollView, then call
 *    [NestedScrollView.smoothScrollTo].
 * 4. [hasScheduled] (a BooleanArray in remember) ensures we schedule exactly once
 *    even if layout fires multiple times.
 *
 * When [key] does not match [state.scrollKey], this composable is a zero-cost
 * pass-through — [content] is called directly with no extra Box or modifier.
 */
@Composable
fun ScrollAnchor(
    key: String,
    state: PreferenceScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Fast path — not the scroll target, render content with zero overhead.
    if (state.scrollKey != key) {
        content()
        return
    }

    val view = LocalView.current
    val density = LocalDensity.current
    // BooleanArray so we can mutate inside the onGloballyPositioned lambda
    // without triggering a recomposition.
    val hasScheduled = remember { booleanArrayOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (!hasScheduled[0]) {
                    hasScheduled[0] = true
                    val y = coords.positionInRoot().y.toInt()
                    val gap = (24f * density.density).toInt()

                    // postDelayed gives the navigation enter transition (material
                    // shared axis, ~300 ms) time to finish so the scroll animation
                    // doesn't compete with the slide-in.
                    view.postDelayed({
                        // Guard against the view being detached before the delay fires.
                        if (!view.isAttachedToWindow) return@postDelayed

                        // Walk: AndroidComposeView → ComposeView → contentFrame
                        //       → StretchNestedScrollView (extends NestedScrollView)
                        var p: android.view.ViewParent? = view.parent
                        while (p != null) {
                            if (p is NestedScrollView) {
                                val child = p.getChildAt(0)
                                if (child != null) {
                                    // Clamp to the actual scrollable range so items near
                                    // the bottom don't get silently ignored.
                                    val maxScroll = maxOf(0, child.height - p.height)
                                    val target = maxOf(0, minOf(y - gap, maxScroll))
                                    p.smoothScrollTo(0, target)
                                }
                                break
                            }
                            p = p.parent
                        }
                    }, 300L)
                }
            },
    ) {
        content()
    }
}

// ── Modifier extension — kept for API compatibility but no longer used internally.
// Prefer ScrollAnchor composable instead.
fun Modifier.preferenceScrollTarget(key: String, state: PreferenceScrollState): Modifier =
    if (state.scrollKey != key) this
    else onGloballyPositioned { /* no-op — use ScrollAnchor instead */ }
