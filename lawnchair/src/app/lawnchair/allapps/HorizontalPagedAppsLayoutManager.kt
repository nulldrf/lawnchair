package app.lawnchair.allapps

import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.allapps.BaseAllAppsAdapter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure pagination and cell-geometry calculator for [HorizontalPagedAppsLayoutManager].
 * Kept free of Android View state so all layout math is directly JVM unit-testable.
 */
object HorizontalAppsPageCalculator {

    data class ItemSlot(
        val page: Int,
        val row: Int,
        val col: Int,
        val fullRow: Boolean,
        val hidden: Boolean = false,
    )

    data class SlotMap(
        val slots: List<ItemSlot>,
        val pageCount: Int,
        val pageStartPositions: IntArray,
        val pageEndPositions: IntArray,
    )

    data class PageMetrics(
        val cellWidth: Int,
        val rowHeight: Int,
        val availWidth: Int,
        val availHeight: Int,
        val horizontalRemainder: Int,
        val verticalRemainder: Int,
    ) {
        fun columnLeft(visualCol: Int): Int = visualCol * cellWidth + min(visualCol, horizontalRemainder)

        fun columnRight(visualCol: Int): Int = (visualCol + 1) * cellWidth + min(visualCol + 1, horizontalRemainder)

        fun rowTop(row: Int): Int = row * rowHeight + min(row, verticalRemainder)

        fun rowBottom(row: Int): Int = (row + 1) * rowHeight + min(row + 1, verticalRemainder)
    }

    /**
     * Assigns each adapter item to a `(page, row, col)` grid slot.
     * Icon and folder items occupy one cell (`columns × rows` per page); full-width items
     * (headers, dividers, work cards) occupy a dedicated full row and wrap cleanly to the next page.
     */
    fun computeSlots(
        itemCount: Int,
        columns: Int,
        rows: Int,
        viewTypeAt: (Int) -> Int,
    ): SlotMap {
        if (itemCount <= 0) {
            return SlotMap(
                slots = emptyList(),
                pageCount = 0,
                pageStartPositions = IntArray(0),
                pageEndPositions = IntArray(0),
            )
        }

        val cols = columns.coerceAtLeast(1)
        val maxRows = rows.coerceAtLeast(1)
        val slots = ArrayList<ItemSlot>(itemCount)

        var curPage = 0
        var curRow = 0
        var curCol = 0
        var maxUsedPage = -1

        for (i in 0 until itemCount) {
            val viewType = viewTypeAt(i)
            if (viewType == BaseAllAppsAdapter.VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO) {
                slots.add(
                    ItemSlot(
                        page = max(0, maxUsedPage),
                        row = 0,
                        col = 0,
                        fullRow = false,
                        hidden = true,
                    ),
                )
                continue
            }

            val isGridIcon = BaseAllAppsAdapter.isIconViewType(viewType)
            if (isGridIcon) {
                slots.add(ItemSlot(page = curPage, row = curRow, col = curCol, fullRow = false))
                if (curPage > maxUsedPage) maxUsedPage = curPage
                curCol++
                if (curCol >= cols) {
                    curCol = 0
                    curRow++
                    if (curRow >= maxRows) {
                        curRow = 0
                        curPage++
                    }
                }
            } else {
                if (curCol > 0) {
                    curCol = 0
                    curRow++
                    if (curRow >= maxRows) {
                        curRow = 0
                        curPage++
                    }
                }
                slots.add(ItemSlot(page = curPage, row = curRow, col = 0, fullRow = true))
                if (curPage > maxUsedPage) maxUsedPage = curPage
                curRow++
                if (curRow >= maxRows) {
                    curRow = 0
                    curPage++
                }
            }
        }

        val pageCount = if (maxUsedPage >= 0) maxUsedPage + 1 else 0
        val pageStart = IntArray(pageCount) { Int.MAX_VALUE }
        val pageEnd = IntArray(pageCount) { 0 }

        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.hidden || slot.page >= pageCount) continue
            if (i < pageStart[slot.page]) pageStart[slot.page] = i
            if (i + 1 > pageEnd[slot.page]) pageEnd[slot.page] = i + 1
        }
        for (p in 0 until pageCount) {
            if (pageStart[p] == Int.MAX_VALUE) {
                pageStart[p] = 0
                pageEnd[p] = 0
            }
        }

        return SlotMap(
            slots = slots,
            pageCount = pageCount,
            pageStartPositions = pageStart,
            pageEndPositions = pageEnd,
        )
    }

    /**
     * Computes exact per-cell dimensions inside `[availWidth, availHeight]` so all [rows] rows
     * fit strictly within [availHeight] without overflowing into the page indicator or bottom
     * search bar.
     */
    fun computePageMetrics(
        availWidth: Int,
        availHeight: Int,
        columns: Int,
        rows: Int,
        cellHeightFactor: Float = 1f,
    ): PageMetrics {
        val cols = columns.coerceAtLeast(1)
        val rowCount = rows.coerceAtLeast(1)
        val w = availWidth.coerceAtLeast(1)
        val h = availHeight.coerceAtLeast(1)

        val cellWidth = (w / cols).coerceAtLeast(1)
        val horizontalRemainder = w - cellWidth * cols

        val maxRowHeight = (h / rowCount).coerceAtLeast(1)
        val clampedFactor = cellHeightFactor.coerceIn(0.3f, 1.5f)
        val rowHeight = if (clampedFactor < 1f) {
            (maxRowHeight * clampedFactor).roundToInt().coerceIn(1, maxRowHeight)
        } else {
            maxRowHeight
        }
        val verticalRemainder = if (rowHeight == maxRowHeight) h - rowHeight * rowCount else 0

        return PageMetrics(
            cellWidth = cellWidth,
            rowHeight = rowHeight,
            availWidth = w,
            availHeight = h,
            horizontalRemainder = horizontalRemainder,
            verticalRemainder = verticalRemainder,
        )
    }

    /**
     * Computes bottom padding reserved below the horizontal app grid so apps never overlap
     * the navigation bar, bottom-pinned search bar, page indicator dots, or extra profile bars.
     */
    fun computeReservedBottomPadding(
        bottomInset: Int,
        searchBarAtBottom: Boolean,
        searchBarHeight: Int,
        pageIndicatorReservedHeight: Int,
        extraBottomOffset: Int = 0,
    ): Int {
        val searchSpace = if (searchBarAtBottom) searchBarHeight.coerceAtLeast(0) else 0
        return bottomInset.coerceAtLeast(0) +
            searchSpace +
            pageIndicatorReservedHeight.coerceAtLeast(0) +
            extraBottomOffset.coerceAtLeast(0)
    }

    fun computeVisiblePageRange(scrollOffsetX: Int, pageStride: Int, pageCount: Int): IntRange {
        if (pageCount <= 0 || pageStride <= 0) return IntRange.EMPTY
        val maxScroll = (pageCount - 1) * pageStride
        val clamped = scrollOffsetX.coerceIn(0, maxScroll)
        val first = (clamped / pageStride).coerceIn(0, pageCount - 1)
        val last = ceil((clamped + pageStride).toDouble() / pageStride).toInt()
            .minus(1)
            .coerceIn(first, pageCount - 1)
        return first..last
    }
}

/**
 * Horizontal paged grid [RecyclerView.LayoutManager] for the App Drawer.
 *
 * Limits each screen/page to `columns × rows` items while respecting all existing App Drawer
 * padding, icon sizing, header/tabs, and bottom-search-bar space reservations.
 */
class HorizontalPagedAppsLayoutManager(
    columns: Int,
    rows: Int,
    cellHeightFactor: Float = 1f,
) : RecyclerView.LayoutManager(),
    RecyclerView.SmoothScroller.ScrollVectorProvider {

    fun interface OnPageScrollListener {
        fun onPageScrollChanged(
            scrollOffsetX: Int,
            maxScrollX: Int,
            pageCount: Int,
            currentPage: Int,
            isRtl: Boolean,
        )
    }

    var columns: Int = columns.coerceAtLeast(1)
        private set
    var rows: Int = rows.coerceAtLeast(1)
        private set
    var cellHeightFactor: Float = cellHeightFactor.coerceIn(0.3f, 1.5f)
        private set

    private var recyclerView: RecyclerView? = null
    private var slotMap: HorizontalAppsPageCalculator.SlotMap? = null
    private var slotItemCount: Int = -1

    /** Logical horizontal scroll offset in pixels (`0 .. maxScrollX`). */
    var scrollOffsetX: Int = 0
        private set

    private var lastPageStride: Int = 0
    private var lastKnownPage: Int = 0
    private var pendingTargetPage: Int = RecyclerView.NO_POSITION
    private var onPageScrollListener: OnPageScrollListener? = null
    private val tmpDecorRect = Rect()

    val pageCount: Int
        get() = slotMap?.pageCount ?: 0

    val pageStride: Int
        get() = width

    val maxScrollX: Int
        get() {
            val count = pageCount
            val stride = pageStride
            return if (count <= 1 || stride <= 0) 0 else (count - 1) * stride
        }

    val currentPage: Int
        get() {
            val count = pageCount
            if (count <= 1) return 0
            val stride = pageStride
            if (stride <= 0) return lastKnownPage.coerceIn(0, count - 1)
            return (scrollOffsetX.toFloat() / stride).roundToInt().coerceIn(0, count - 1)
        }

    val isRtlLayout: Boolean
        get() = layoutDirection == View.LAYOUT_DIRECTION_RTL

    fun setOnPageScrollListener(listener: OnPageScrollListener?) {
        onPageScrollListener = listener
        notifyPageScroll()
    }

    fun updateGrid(columns: Int, rows: Int, cellHeightFactor: Float) {
        val newCols = columns.coerceAtLeast(1)
        val newRows = rows.coerceAtLeast(1)
        val newFactor = cellHeightFactor.coerceIn(0.3f, 1.5f)
        if (this.columns == newCols && this.rows == newRows && this.cellHeightFactor == newFactor) {
            return
        }
        this.columns = newCols
        this.rows = newRows
        this.cellHeightFactor = newFactor
        invalidateSlots()
        requestLayout()
    }

    fun scrollToPage(page: Int) {
        val count = pageCount
        val target = if (count > 0) page.coerceIn(0, count - 1) else max(0, page)
        if (width <= 0 || slotMap == null) {
            pendingTargetPage = target
            lastKnownPage = target
            return
        }
        pendingTargetPage = RecyclerView.NO_POSITION
        lastKnownPage = target
        scrollOffsetX = (target * width).coerceIn(0, maxScrollX)
        requestLayout()
        notifyPageScroll()
    }

    /**
     * Converts a desired change in logical [scrollOffsetX] into a view-space pixel delta for
     * [RecyclerView.smoothScrollBy].
     */
    fun logicalDeltaToViewDx(logicalDelta: Int): Int = if (isRtlLayout) -logicalDelta else logicalDelta

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams = RecyclerView.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    override fun isAutoMeasureEnabled(): Boolean = false

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        recyclerView = view
    }

    override fun onDetachedFromWindow(view: RecyclerView, recycler: RecyclerView.Recycler) {
        super.onDetachedFromWindow(view, recycler)
        if (recyclerView === view) {
            recyclerView = null
        }
    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?,
    ) {
        removeAllViews()
        invalidateSlots()
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        invalidateSlots()
    }

    override fun onItemsAdded(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        invalidateSlots()
    }

    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        invalidateSlots()
    }

    override fun onItemsMoved(recyclerView: RecyclerView, from: Int, to: Int, itemCount: Int) {
        invalidateSlots()
    }

    override fun onItemsUpdated(
        recyclerView: RecyclerView,
        positionStart: Int,
        itemCount: Int,
        payload: Any?,
    ) {
        invalidateSlots()
    }

    private fun invalidateSlots() {
        slotMap = null
        slotItemCount = -1
    }

    private fun ensureSlots(stateItemCount: Int): HorizontalAppsPageCalculator.SlotMap {
        val adapter = recyclerView?.adapter
        val existing = slotMap
        if (existing != null && slotItemCount == stateItemCount) {
            return existing
        }
        val count = min(stateItemCount, adapter?.itemCount ?: stateItemCount)
        val computed = HorizontalAppsPageCalculator.computeSlots(
            itemCount = count,
            columns = columns,
            rows = rows,
            viewTypeAt = { pos -> adapter?.getItemViewType(pos) ?: BaseAllAppsAdapter.VIEW_TYPE_ICON },
        )
        slotMap = computed
        slotItemCount = stateItemCount
        return computed
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        if (state.itemCount <= 0) {
            detachAndScrapAttachedViews(recycler)
            slotMap = HorizontalAppsPageCalculator.computeSlots(0, columns, rows) { 0 }
            slotItemCount = 0
            scrollOffsetX = 0
            lastKnownPage = 0
            pendingTargetPage = RecyclerView.NO_POSITION
            notifyPageScroll()
            return
        }

        val map = ensureSlots(state.itemCount)
        val stride = width
        if (stride <= 0 || height <= 0) {
            return
        }

        if (pendingTargetPage != RecyclerView.NO_POSITION) {
            val target = pendingTargetPage.coerceIn(0, max(0, map.pageCount - 1))
            scrollOffsetX = target * stride
            lastKnownPage = target
            pendingTargetPage = RecyclerView.NO_POSITION
        } else if (lastPageStride > 0 && lastPageStride != stride) {
            val target = lastKnownPage.coerceIn(0, max(0, map.pageCount - 1))
            scrollOffsetX = target * stride
        }
        lastPageStride = stride

        val maxOffset = maxScrollX
        scrollOffsetX = scrollOffsetX.coerceIn(0, maxOffset)
        lastKnownPage = currentPage

        fill(recycler, state, map)
        notifyPageScroll()
    }

    private fun fill(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        map: HorizontalAppsPageCalculator.SlotMap,
    ) {
        val stride = width
        if (stride <= 0 || height <= 0 || map.pageCount <= 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }

        val availWidth = (stride - paddingLeft - paddingRight).coerceAtLeast(1)
        val availHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val metrics = HorizontalAppsPageCalculator.computePageMetrics(
            availWidth = availWidth,
            availHeight = availHeight,
            columns = columns,
            rows = rows,
            cellHeightFactor = cellHeightFactor,
        )

        val visiblePages = HorizontalAppsPageCalculator.computeVisiblePageRange(
            scrollOffsetX = scrollOffsetX,
            pageStride = stride,
            pageCount = map.pageCount,
        )
        val isRtl = isRtlLayout
        val adapter = recyclerView?.adapter

        detachAndScrapAttachedViews(recycler)

        for (page in visiblePages) {
            val start = map.pageStartPositions[page]
            val end = min(map.pageEndPositions[page], state.itemCount)
            val pageItemCount = end - start
            val pageOffsetX = if (isRtl) {
                -(page * stride - scrollOffsetX)
            } else {
                page * stride - scrollOffsetX
            }

            for (pos in start until end) {
                val slot = map.slots[pos]
                if (slot.hidden) continue

                val view = recycler.getViewForPosition(pos)
                addView(view)
                calculateItemDecorationsForChild(view, tmpDecorRect)
                val decorH = tmpDecorRect.left + tmpDecorRect.right
                val decorV = tmpDecorRect.top + tmpDecorRect.bottom

                if (!slot.fullRow) {
                    val visualCol = if (isRtl) columns - 1 - slot.col else slot.col
                    val colLeft = metrics.columnLeft(visualCol)
                    val colRight = metrics.columnRight(visualCol)
                    val rowTop = metrics.rowTop(slot.row)
                    val rowBottom = metrics.rowBottom(slot.row)

                    val childW = (colRight - colLeft - decorH).coerceAtLeast(1)
                    val childH = (rowBottom - rowTop - decorV).coerceAtLeast(1)
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(childW, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(childH, View.MeasureSpec.EXACTLY),
                    )
                    layoutDecorated(
                        view,
                        pageOffsetX + paddingLeft + colLeft,
                        paddingTop + rowTop,
                        pageOffsetX + paddingLeft + colRight,
                        paddingTop + rowBottom,
                    )
                } else {
                    val viewType = adapter?.getItemViewType(pos) ?: 0
                    val isCard = viewType == BaseAllAppsAdapter.VIEW_TYPE_WORK_DISABLED_CARD ||
                        viewType == BaseAllAppsAdapter.VIEW_TYPE_WORK_EDU_CARD
                    val rowTop = metrics.rowTop(slot.row)
                    val maxCardH = if (isCard) {
                        (availHeight - rowTop - decorV).coerceAtLeast(1)
                    } else {
                        (metrics.rowHeight - decorV).coerceAtLeast(1)
                    }
                    val childW = (availWidth - decorH).coerceAtLeast(1)
                    view.measure(
                        View.MeasureSpec.makeMeasureSpec(childW, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(maxCardH, View.MeasureSpec.AT_MOST),
                    )
                    val measuredH = view.measuredHeight.coerceAtMost(maxCardH)
                    val top = if (isCard && pageItemCount == 1) {
                        paddingTop + max(0, (availHeight - measuredH) / 2)
                    } else {
                        paddingTop + rowTop
                    }
                    layoutDecorated(
                        view,
                        pageOffsetX + paddingLeft,
                        top,
                        pageOffsetX + paddingLeft + availWidth,
                        top + measuredH + decorV,
                    )
                }
            }
        }

        recycleRemainingScrap(recycler)
    }

    private fun recycleRemainingScrap(recycler: RecyclerView.Recycler) {
        val scrap = recycler.scrapList
        if (scrap.isEmpty()) return
        val copy = ArrayList(scrap)
        for (vh in copy) {
            recycler.recycleView(vh.itemView)
        }
    }

    override fun canScrollHorizontally(): Boolean = pageCount > 1 || scrollOffsetX > 0

    override fun canScrollVertically(): Boolean = false

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        if (dx == 0 || width <= 0) return 0
        val map = ensureSlots(state.itemCount)
        val maxOffset = maxScrollX
        if (maxOffset <= 0) return 0

        val logicalDelta = if (isRtlLayout) -dx else dx
        val oldOffset = scrollOffsetX
        val newOffset = (oldOffset + logicalDelta).coerceIn(0, maxOffset)
        val consumedLogical = newOffset - oldOffset
        if (consumedLogical == 0) return 0

        scrollOffsetX = newOffset
        lastKnownPage = currentPage
        fill(recycler, state, map)
        notifyPageScroll()

        return if (isRtlLayout) -consumedLogical else consumedLogical
    }

    override fun scrollToPosition(position: Int) {
        val map = slotMap
        if (position <= 0) {
            scrollToPage(0)
            return
        }
        if (map != null && position < map.slots.size) {
            scrollToPage(map.slots[position].page)
        } else {
            val perPage = max(1, columns * rows)
            scrollToPage(position / perPage)
        }
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int,
    ) {
        val map = ensureSlots(state.itemCount)
        val targetPage = when {
            map.pageCount <= 1 -> 0
            position in map.slots.indices -> map.slots[position].page
            else -> (position / max(1, columns * rows)).coerceIn(0, map.pageCount - 1)
        }
        val stride = width
        if (stride <= 0) {
            scrollToPage(targetPage)
            return
        }
        val targetOffset = (targetPage * stride).coerceIn(0, maxScrollX)
        val logicalDelta = targetOffset - scrollOffsetX
        if (logicalDelta != 0) {
            recyclerView.smoothScrollBy(logicalDeltaToViewDx(logicalDelta), 0)
        }
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        val map = slotMap ?: return null
        if (map.pageCount <= 1 || targetPosition !in map.slots.indices) return null
        val targetPage = map.slots[targetPosition].page
        val diff = targetPage - currentPage
        if (diff == 0) return null
        val dir = if (diff > 0) 1f else -1f
        return PointF(if (isRtlLayout) -dir else dir, 0f)
    }

    override fun computeHorizontalScrollOffset(state: RecyclerView.State): Int {
        val maxOffset = maxScrollX
        if (maxOffset <= 0) return 0
        return if (isRtlLayout) (maxOffset - scrollOffsetX).coerceIn(0, maxOffset) else scrollOffsetX
    }

    override fun computeHorizontalScrollExtent(state: RecyclerView.State): Int = pageStride.coerceAtLeast(0)

    override fun computeHorizontalScrollRange(state: RecyclerView.State): Int =
        (pageCount * pageStride).coerceAtLeast(0)

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = 0

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = height.coerceAtLeast(0)

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = 0

    private fun notifyPageScroll() {
        onPageScrollListener?.onPageScrollChanged(
            scrollOffsetX = scrollOffsetX,
            maxScrollX = maxScrollX,
            pageCount = pageCount,
            currentPage = currentPage,
            isRtl = isRtlLayout,
        )
    }
}
