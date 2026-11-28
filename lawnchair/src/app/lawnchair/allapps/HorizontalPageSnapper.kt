package app.lawnchair.allapps

import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Page-snapping controller for an [androidx.recyclerview.widget.RecyclerView] backed by
 * [HorizontalPagedAppsLayoutManager]. Handles both flings and slow drags with smooth
 * page-by-page snapping.
 */
class HorizontalPageSnapper : RecyclerView.OnFlingListener() {

    private var recyclerView: RecyclerView? = null
    private var dragStartPage: Int = 0
    private var dragging: Boolean = false
    private var minFlingVelocityPx: Int = 0

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            val lm = rv.layoutManager as? HorizontalPagedAppsLayoutManager ?: return
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    if (!dragging) {
                        dragStartPage = lm.currentPage
                        dragging = true
                    }
                }

                RecyclerView.SCROLL_STATE_IDLE -> {
                    val wasDragging = dragging
                    dragging = false
                    snapToTargetPage(rv, lm, wasDragging)
                }

                RecyclerView.SCROLL_STATE_SETTLING -> Unit
            }
        }
    }

    fun attachToRecyclerView(rv: RecyclerView?) {
        if (recyclerView === rv) return
        detach()
        recyclerView = rv
        if (rv != null) {
            minFlingVelocityPx = ViewConfiguration.get(rv.context).scaledMinimumFlingVelocity * 2
            rv.onFlingListener = this
            rv.addOnScrollListener(scrollListener)
        }
    }

    fun detach() {
        recyclerView?.let { rv ->
            if (rv.onFlingListener === this) {
                rv.onFlingListener = null
            }
            rv.removeOnScrollListener(scrollListener)
        }
        recyclerView = null
        dragging = false
    }

    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
        val rv = recyclerView ?: return false
        val lm = rv.layoutManager as? HorizontalPagedAppsLayoutManager ?: return false
        val stride = lm.pageStride
        val count = lm.pageCount
        if (stride <= 0 || count <= 1) return false

        val logicalVelocity = if (lm.isRtlLayout) -velocityX else velocityX
        val targetPage = if (abs(logicalVelocity) >= minFlingVelocityPx) {
            val deltaPage = if (logicalVelocity > 0) 1 else -1
            val anchor = if (dragging) dragStartPage else lm.currentPage
            (anchor + deltaPage).coerceIn(0, count - 1)
        } else {
            resolveDragReleasePage(lm)
        }
        dragging = false

        val targetOffset = targetPage * stride
        val logicalDelta = targetOffset - lm.scrollOffsetX
        if (logicalDelta != 0) {
            val fraction = (abs(logicalDelta).toFloat() / stride).coerceIn(0.2f, 1f)
            val durationMs = (BASE_SNAP_DURATION_MS * (0.55f + 0.45f * fraction)).roundToInt()
                .coerceIn(MIN_SNAP_DURATION_MS, MAX_SNAP_DURATION_MS)
            rv.smoothScrollBy(
                lm.logicalDeltaToViewDx(logicalDelta),
                0,
                SNAP_INTERPOLATOR,
                durationMs,
            )
        }
        return true
    }

    private fun snapToTargetPage(
        rv: RecyclerView,
        lm: HorizontalPagedAppsLayoutManager,
        wasDragging: Boolean,
    ) {
        val stride = lm.pageStride
        val count = lm.pageCount
        if (stride <= 0 || count <= 1) return

        val remainder = lm.scrollOffsetX % stride
        if (remainder == 0) return

        val targetPage = if (wasDragging) {
            resolveDragReleasePage(lm)
        } else {
            lm.currentPage
        }
        val targetOffset = targetPage * stride
        val logicalDelta = targetOffset - lm.scrollOffsetX
        if (logicalDelta != 0) {
            rv.smoothScrollBy(
                lm.logicalDeltaToViewDx(logicalDelta),
                0,
                SNAP_INTERPOLATOR,
                BASE_SNAP_DURATION_MS,
            )
        }
    }

    private fun resolveDragReleasePage(lm: HorizontalPagedAppsLayoutManager): Int {
        val stride = lm.pageStride
        val count = lm.pageCount
        if (stride <= 0 || count <= 1) return 0
        val startOffset = dragStartPage.coerceIn(0, count - 1) * stride
        val movedFraction = (lm.scrollOffsetX - startOffset).toFloat() / stride
        return when {
            movedFraction >= DRAG_ADVANCE_THRESHOLD -> (dragStartPage + 1).coerceIn(0, count - 1)
            movedFraction <= -DRAG_ADVANCE_THRESHOLD -> (dragStartPage - 1).coerceIn(0, count - 1)
            else -> (lm.scrollOffsetX.toFloat() / stride).roundToInt().coerceIn(0, count - 1)
        }
    }

    companion object {
        private const val DRAG_ADVANCE_THRESHOLD = 0.35f
        private const val MIN_SNAP_DURATION_MS = 180
        private const val BASE_SNAP_DURATION_MS = 280
        private const val MAX_SNAP_DURATION_MS = 340
        private val SNAP_INTERPOLATOR = DecelerateInterpolator(1.5f)
    }
}
