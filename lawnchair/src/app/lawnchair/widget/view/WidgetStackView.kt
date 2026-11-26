package app.lawnchair.widget.view

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import app.lawnchair.widget.model.WidgetStackInfo
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.widget.LauncherAppWidgetHostView
import kotlin.math.hypot

/**
 * Home-screen / hotseat host view for a stack of widgets.
 *
 * Implements [DraggableView] and [Reorderable] so [com.android.launcher3.Workspace] and
 * [com.android.launcher3.AppWidgetResizeFrame] can drag, reorder, and resize it like a widget.
 */
class WidgetStackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr),
    DraggableView,
    Reorderable,
    View.OnLongClickListener {

    interface WidgetStackChangeListener {
        fun onStackChanged(stackView: WidgetStackView)
        fun onStackCollapsed(stackView: WidgetStackView, remainingWidgetId: Int)
    }

    private val contentView: WidgetStackContentView
    private val longPressHelper = CheckLongPressHelper(this, this)
    private val translateDelegate = MultiTranslateDelegate(this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var reorderBounceScale = 1f
    private var downX = 0f
    private var downY = 0f
    private var stackChangeListener: WidgetStackChangeListener? = null

    init {
        clipChildren = true
        clipToPadding = true
        isFocusable = true
        isLongClickable = true
        setOnLongClickListener(this)

        contentView = LayoutInflater.from(context)
            .inflate(R.layout.widget_stack_content, this, false) as WidgetStackContentView
        addView(
            contentView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        contentView.setChangeListener(object : WidgetStackContentView.WidgetStackChangeListener {
            override fun onStackChanged(stackInfo: WidgetStackInfo) {
                syncPrimaryTag()
                stackChangeListener?.onStackChanged(this@WidgetStackView)
            }

            override fun onStackCollapsed(remainingWidgetId: Int) {
                stackChangeListener?.onStackCollapsed(this@WidgetStackView, remainingWidgetId)
            }
        })
    }

    fun setStackChangeListener(listener: WidgetStackChangeListener?) {
        stackChangeListener = listener
    }

    fun getStackInfo(): WidgetStackInfo? = contentView.getStackInfo()

    fun getWidgetCount(): Int = contentView.getWidgetCount()

    fun getCurrentWidgetView(): LauncherAppWidgetHostView? = contentView.getCurrentWidgetView()

    fun getWidgetViews(): List<LauncherAppWidgetHostView> = contentView.getWidgetViews()

    fun getCurrentWidgetInfo(): LauncherAppWidgetInfo? = contentView.getCurrentWidgetInfo()

    fun setStackInfo(
        stackInfo: WidgetStackInfo,
        preloadedInfos: Map<Int, LauncherAppWidgetInfo> = emptyMap(),
    ) {
        contentView.setStackInfo(stackInfo, preloadedInfos)
        syncPrimaryTag()
    }

    fun addWidget(widgetInfo: LauncherAppWidgetInfo, hostView: LauncherAppWidgetHostView? = null): Boolean = contentView.addWidget(widgetInfo, hostView)

    fun removeWidget(appWidgetId: Int): Boolean = contentView.removeWidget(appWidgetId)

    fun onAppWidgetConfigureCompleted(appWidgetId: Int) {
        contentView.onAppWidgetConfigureCompleted(appWidgetId)
    }

    fun updateBounds(cellX: Int, cellY: Int, spanX: Int, spanY: Int) {
        contentView.updateBounds(cellX, cellY, spanX, spanY)
    }

    fun updateMemberWidgetSizeRanges(spanX: Int, spanY: Int) {
        contentView.updateMemberWidgetSizeRanges(spanX, spanY)
    }

    fun refreshPendingWidgets() {
        contentView.refreshPendingWidgets()
    }

    private fun syncPrimaryTag() {
        val currentInfo = contentView.getCurrentWidgetInfo()
            ?: contentView.getWidgetViews().firstOrNull()?.tag as? LauncherAppWidgetInfo
        if (currentInfo != null) {
            tag = currentInfo
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = ev.x
            downY = ev.y
        } else if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            cancelLongPressIfScrolled(ev)
        }
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
            cancelLongPressIfScrolled(ev)
        }
        longPressHelper.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }

    private fun cancelLongPressIfScrolled(ev: MotionEvent) {
        if (hypot(ev.x - downX, ev.y - downY) > touchSlop) {
            longPressHelper.cancelLongPress()
        }
    }

    override fun onLongClick(v: View): Boolean {
        return ItemLongClickListener.INSTANCE_WORKSPACE.onLongClick(this)
    }

    // --- DraggableView ---

    override fun getViewType(): Int = DraggableView.DRAGGABLE_WIDGET

    override fun getWorkspaceVisualDragBounds(bounds: Rect) {
        bounds.set(0, 0, width, height)
    }

    // --- Reorderable ---

    override fun getTranslateDelegate(): MultiTranslateDelegate = translateDelegate

    override fun setReorderBounceScale(scale: Float) {
        reorderBounceScale = scale
        scaleX = scale
        scaleY = scale
    }

    override fun getReorderBounceScale(): Float = reorderBounceScale
}
