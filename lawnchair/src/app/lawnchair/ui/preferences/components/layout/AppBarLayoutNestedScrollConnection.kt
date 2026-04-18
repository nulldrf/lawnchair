package app.lawnchair.ui.preferences.components.layout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.google.android.material.appbar.AppBarLayout

/**
 * A [NestedScrollConnection] that forwards Compose scroll events to a View-based
 * [AppBarLayout]. This allows [CollapsingToolbarLayout] to collapse/expand as the
 * user scrolls Compose content inside a plain [android.widget.FrameLayout].
 *
 * Without this bridge, the AppBarLayout never receives scroll events from Compose
 * content since AppBarLayout$ScrollingViewBehavior only listens to View scroll events.
 */
class AppBarLayoutNestedScrollConnection(
    private val appBarLayout: AppBarLayout,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Forward upward scroll (collapsing) to AppBarLayout
        if (available.y < 0) {
            val consumed = appBarLayout.dispatchNestedPreScroll(
                0,
                available.y.toInt(),
                null,
                null,
            )
            if (consumed) {
                return Offset(0f, available.y)
            }
        }
        return Offset.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        // Forward downward scroll (expanding) to AppBarLayout
        if (available.y > 0) {
            appBarLayout.dispatchNestedScroll(
                0,
                consumed.y.toInt(),
                0,
                available.y.toInt(),
                null,
            )
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (available.y < 0) {
            appBarLayout.dispatchNestedPreFling(0f, available.y)
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        appBarLayout.dispatchNestedFling(0f, available.y, true)
        return Velocity.Zero
    }
}
