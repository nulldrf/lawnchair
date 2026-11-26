package app.lawnchair.widget.view

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import app.lawnchair.smartspace.PageIndicator
import app.lawnchair.widget.model.WidgetStackInfo
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetInflater
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.util.WidgetSizes

/**
 * Inner content view of a [WidgetStackView], hosting an [InterceptingWidgetPager],
 * a One UI-style auto-fading [PageIndicator], auto-rotation scheduling, and
 * pending-to-real widget inflation upgrades.
 */
class WidgetStackContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface WidgetStackChangeListener {
        fun onStackChanged(stackInfo: WidgetStackInfo)
        fun onStackCollapsed(remainingWidgetId: Int)
    }

    private lateinit var pager: InterceptingWidgetPager
    private lateinit var indicator: PageIndicator
    private val adapter = WidgetStackAdapter()

    private val widgetViews = mutableListOf<LauncherAppWidgetHostView>()
    private val knownWidgetInfos = mutableMapOf<Int, LauncherAppWidgetInfo>()

    private var stackInfo: WidgetStackInfo? = null
    private var changeListener: WidgetStackChangeListener? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRefreshAttempts = 0
    private var isAutoRotateScheduled = false

    private val autoRotateRunnable = object : Runnable {
        override fun run() {
            isAutoRotateScheduled = false
            val info = stackInfo ?: return
            if (!info.autoRotate || widgetViews.size <= 1 || !isAttachedToWindow) return
            val next = (pager.currentItem + 1) % widgetViews.size
            pager.setCurrentItem(next, true)
            scheduleAutoRotate()
        }
    }

    private val hideIndicatorRunnable = Runnable {
        if (::indicator.isInitialized && indicator.visibility == View.VISIBLE) {
            indicator.animate()
                .alpha(0f)
                .setDuration(INDICATOR_FADE_OUT_MS)
                .start()
        }
    }

    private val pendingRefreshRunnable = Runnable {
        refreshPendingWidgets()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        pager = findViewById(R.id.widget_stack_pager)
        indicator = findViewById(R.id.widget_stack_indicator)

        pager.adapter = adapter
        pager.offscreenPageLimit = OFFSCREEN_PAGE_LIMIT
        pager.onUserInteraction = { showIndicatorTemporarily() }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (widgetViews.size > 1) {
                    indicator.setPageOffset(position, positionOffset)
                }
            }

            override fun onPageSelected(position: Int) {
                stackInfo?.currentIndex = position
                showIndicatorTemporarily()
                restartAutoRotate()
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    showIndicatorTemporarily(autoHide = false)
                } else if (state == ViewPager.SCROLL_STATE_IDLE) {
                    scheduleIndicatorFadeOut()
                }
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (widgetViews.size > 1) {
            showIndicatorTemporarily()
        }
        restartAutoRotate()
        if (hasPendingOrMissingWidgets()) {
            pendingRefreshAttempts = 0
            mainHandler.postDelayed(pendingRefreshRunnable, PENDING_RETRY_BASE_MS)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoRotate()
        mainHandler.removeCallbacks(hideIndicatorRunnable)
        mainHandler.removeCallbacks(pendingRefreshRunnable)
    }

    fun setChangeListener(listener: WidgetStackChangeListener?) {
        changeListener = listener
    }

    fun getStackInfo(): WidgetStackInfo? = stackInfo

    fun getWidgetCount(): Int = widgetViews.size

    fun getCurrentWidgetView(): LauncherAppWidgetHostView? = widgetViews.getOrNull(pager.currentItem)

    fun getWidgetViews(): List<LauncherAppWidgetHostView> = widgetViews.toList()

    fun getCurrentWidgetInfo(): LauncherAppWidgetInfo? = getCurrentWidgetView()?.tag as? LauncherAppWidgetInfo

    fun setStackInfo(
        info: WidgetStackInfo,
        preloadedInfos: Map<Int, LauncherAppWidgetInfo> = emptyMap(),
    ) {
        stackInfo = info
        knownWidgetInfos.putAll(preloadedInfos)
        pendingRefreshAttempts = 0
        mainHandler.removeCallbacks(pendingRefreshRunnable)

        widgetViews.clear()
        val missingIds = mutableListOf<Int>()
        for (widgetId in info.widgetIds) {
            val view = createWidgetView(widgetId, info)
            if (view != null) {
                widgetViews.add(view)
            } else {
                missingIds.add(widgetId)
            }
        }

        adapter.notifyDataSetChanged()
        updateIndicatorCount()

        if (widgetViews.isNotEmpty()) {
            val targetIndex = info.currentIndex.coerceIn(0, widgetViews.lastIndex)
            info.currentIndex = targetIndex
            pager.setCurrentItem(targetIndex, false)
            indicator.setPageOffset(targetIndex, 0f)
        }

        if (widgetViews.size > 1) {
            showIndicatorTemporarily()
        }
        restartAutoRotate()

        if (missingIds.isNotEmpty() || hasPendingWidgets()) {
            schedulePendingRefresh()
        }
    }

    fun addWidget(widgetInfo: LauncherAppWidgetInfo, hostView: LauncherAppWidgetHostView? = null): Boolean {
        val current = stackInfo ?: return false
        if (current.widgetIds.contains(widgetInfo.appWidgetId)) {
            val existingIdx = current.widgetIds.indexOf(widgetInfo.appWidgetId)
            if (existingIdx in widgetViews.indices) {
                pager.setCurrentItem(existingIdx, true)
            }
            return false
        }

        knownWidgetInfos[widgetInfo.appWidgetId] = widgetInfo
        val updated = current.withAddedWidget(widgetInfo.appWidgetId)
        val view = hostView?.also {
            (it.parent as? ViewGroup)?.removeView(it)
            it.tag = widgetInfo
        } ?: createWidgetView(widgetInfo.appWidgetId, updated)
            ?: return false

        stackInfo = updated
        widgetInfo.widgetStackId = updated.stackId
        syncPositionToStack(widgetInfo, updated)

        widgetViews.add(view)
        adapter.notifyDataSetChanged()
        updateIndicatorCount()

        val newIndex = widgetViews.lastIndex
        updated.currentIndex = newIndex
        pager.setCurrentItem(newIndex, true)
        showIndicatorTemporarily()
        restartAutoRotate()

        persistStackAsync(updated)
        changeListener?.onStackChanged(updated)
        return true
    }

    fun removeWidget(appWidgetId: Int): Boolean {
        val current = stackInfo ?: return false
        val index = current.widgetIds.indexOf(appWidgetId)
        if (index < 0) return false

        val updated = current.withRemovedWidget(appWidgetId)
        stackInfo = updated
        knownWidgetInfos.remove(appWidgetId)

        if (index in widgetViews.indices) {
            widgetViews.removeAt(index)
        }
        adapter.notifyDataSetChanged()
        updateIndicatorCount()

        if (updated.widgetIds.size == 1) {
            stopAutoRotate()
            val remainingId = updated.widgetIds.first()
            changeListener?.onStackCollapsed(remainingId)
            return true
        }

        if (widgetViews.isNotEmpty()) {
            val nextIndex = updated.currentIndex.coerceIn(0, widgetViews.lastIndex)
            updated.currentIndex = nextIndex
            pager.setCurrentItem(nextIndex, false)
        }
        restartAutoRotate()
        persistStackAsync(updated)
        changeListener?.onStackChanged(updated)
        return true
    }

    fun onAppWidgetConfigureCompleted(appWidgetId: Int) {
        val info = stackInfo ?: return
        if (!info.widgetIds.contains(appWidgetId)) return

        resolveWidgetInfoInMemory(appWidgetId)?.let { widgetInfo ->
            widgetInfo.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
        }
        pendingRefreshAttempts = 0
        refreshPendingWidgets()
        mainHandler.postDelayed(pendingRefreshRunnable, CONFIGURE_REFRESH_DELAY_MS)
    }

    fun updateMemberWidgetSizeRanges(spanX: Int, spanY: Int) {
        val launcher = Launcher.getLauncher(context)
        val helper = WidgetManagerHelper(context)
        for (view in widgetViews) {
            val widgetInfo = view.tag as? LauncherAppWidgetInfo ?: continue
            widgetInfo.spanX = spanX
            widgetInfo.spanY = spanY
            val provider = view.appWidgetInfo as? LauncherAppWidgetProviderInfo
                ?: helper.getLauncherAppWidgetInfo(widgetInfo.appWidgetId, widgetInfo.providerName)
            WidgetSizes.updateWidgetSizeRanges(view, launcher, spanX, spanY)
            if (provider != null) {
                view.setAppWidget(widgetInfo.appWidgetId, provider)
            }
        }
    }

    fun refreshPendingWidgets() {
        val info = stackInfo ?: return
        val launcher = runCatching { Launcher.getLauncher(context) }.getOrNull() ?: return
        val holder = launcher.appWidgetHolder
        val inflater = WidgetInflater(context, context.packageManager.isSafeMode)

        var changed = false

        // 1. Upgrade any PendingAppWidgetHostView to a real LauncherAppWidgetHostView
        for (i in widgetViews.indices) {
            val existing = widgetViews[i]
            if (existing !is PendingAppWidgetHostView) continue
            val widgetInfo = existing.tag as? LauncherAppWidgetInfo ?: continue
            val result = inflater.inflateAppWidget(widgetInfo)
            val provider = result.widgetInfo
            if (result.type == WidgetInflater.TYPE_REAL && provider != null && holder.isListening) {
                val realView = setupRealWidgetView(launcher, holder, widgetInfo, provider, info)
                widgetInfo.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
                widgetViews[i] = realView
                changed = true
            }
        }

        // 2. Fill in any widgetIds that were missing during initial bind
        if (widgetViews.size < info.widgetIds.size) {
            val presentIds = widgetViews.mapNotNull { (it.tag as? LauncherAppWidgetInfo)?.appWidgetId }.toSet()
            for (widgetId in info.widgetIds) {
                if (widgetId in presentIds) continue
                val view = createWidgetView(widgetId, info)
                if (view != null) {
                    widgetViews.add(view)
                    changed = true
                }
            }
        }

        if (changed) {
            adapter.notifyDataSetChanged()
            updateIndicatorCount()
            if (widgetViews.isNotEmpty()) {
                val targetIndex = info.currentIndex.coerceIn(0, widgetViews.lastIndex)
                pager.setCurrentItem(targetIndex, false)
            }
        }

        if (hasPendingOrMissingWidgets() && pendingRefreshAttempts < MAX_PENDING_RETRIES) {
            schedulePendingRefresh()
        }
    }

    private fun createWidgetView(appWidgetId: Int, info: WidgetStackInfo): LauncherAppWidgetHostView? {
        val launcher = runCatching { Launcher.getLauncher(context) }.getOrNull() ?: return null
        val holder = launcher.appWidgetHolder
        val widgetInfo = resolveWidgetInfoInMemory(appWidgetId) ?: return null

        syncPositionToStack(widgetInfo, info)

        val inflater = WidgetInflater(context, context.packageManager.isSafeMode)
        val result = inflater.inflateAppWidget(widgetInfo)

        return when {
            result.type == WidgetInflater.TYPE_DELETE -> {
                tryRestoreWidget(launcher, holder, widgetInfo, info)
            }
            result.type == WidgetInflater.TYPE_REAL && result.widgetInfo != null -> {
                if (holder.isListening && Looper.myLooper() == Looper.getMainLooper()) {
                    setupRealWidgetView(launcher, holder, widgetInfo, result.widgetInfo, info)
                } else {
                    PendingAppWidgetHostView(context, holder, widgetInfo, result.widgetInfo).apply {
                        launcher.itemInflater?.prepareAppWidget(this, widgetInfo)
                    }
                }
            }
            else -> {
                PendingAppWidgetHostView(context, holder, widgetInfo, result.widgetInfo).apply {
                    launcher.itemInflater?.prepareAppWidget(this, widgetInfo)
                }
            }
        }
    }

    private fun setupRealWidgetView(
        launcher: Launcher,
        holder: LauncherWidgetHolder,
        widgetInfo: LauncherAppWidgetInfo,
        provider: LauncherAppWidgetProviderInfo,
        stack: WidgetStackInfo,
    ): LauncherAppWidgetHostView {
        val hostView = holder.createView(widgetInfo.appWidgetId, provider) as LauncherAppWidgetHostView
        launcher.itemInflater?.prepareAppWidget(hostView, widgetInfo)
        WidgetSizes.updateWidgetSizeRanges(hostView, launcher, stack.spanX, stack.spanY)
        return hostView
    }

    private fun tryRestoreWidget(
        launcher: Launcher,
        holder: LauncherWidgetHolder,
        widgetInfo: LauncherAppWidgetInfo,
        stack: WidgetStackInfo,
    ): LauncherAppWidgetHostView? {
        val helper = WidgetManagerHelper(context)
        val provider = helper.getLauncherAppWidgetInfo(widgetInfo.appWidgetId, widgetInfo.providerName)
            ?: return null
        widgetInfo.providerName = provider.provider
        return if (holder.isListening && Looper.myLooper() == Looper.getMainLooper()) {
            setupRealWidgetView(launcher, holder, widgetInfo, provider, stack)
        } else {
            PendingAppWidgetHostView(context, holder, widgetInfo, provider).apply {
                launcher.itemInflater?.prepareAppWidget(this, widgetInfo)
            }
        }
    }

    private fun resolveWidgetInfoInMemory(appWidgetId: Int): LauncherAppWidgetInfo? {
        knownWidgetInfos[appWidgetId]?.let { return it }
        val bgDataModel = LauncherAppState.getInstance(context).model.getBgDataModel()
        synchronized(bgDataModel) {
            for (item in bgDataModel.itemsIdMap) {
                if (item is LauncherAppWidgetInfo && item.appWidgetId == appWidgetId) {
                    knownWidgetInfos[appWidgetId] = item
                    return item
                }
            }
        }
        return null
    }

    private fun syncPositionToStack(widgetInfo: LauncherAppWidgetInfo, info: WidgetStackInfo) {
        val needsSync = widgetInfo.container != info.container ||
            widgetInfo.screenId != info.screenId ||
            widgetInfo.cellX != info.cellX ||
            widgetInfo.cellY != info.cellY ||
            widgetInfo.spanX != info.spanX ||
            widgetInfo.spanY != info.spanY ||
            widgetInfo.widgetStackId != info.stackId

        if (!needsSync) return

        widgetInfo.container = info.container
        widgetInfo.screenId = info.screenId
        widgetInfo.cellX = info.cellX
        widgetInfo.cellY = info.cellY
        widgetInfo.spanX = info.spanX
        widgetInfo.spanY = info.spanY
        widgetInfo.widgetStackId = info.stackId
    }

    private fun persistStackAsync(info: WidgetStackInfo) {
        val launcher = runCatching { Launcher.getLauncher(context) }.getOrNull() ?: return
        launcher.modelWriter.saveWidgetStack(info)
    }

    private fun hasPendingWidgets(): Boolean = widgetViews.any { it is PendingAppWidgetHostView }

    private fun hasPendingOrMissingWidgets(): Boolean {
        val info = stackInfo ?: return false
        return widgetViews.size < info.widgetIds.size || hasPendingWidgets()
    }

    private fun schedulePendingRefresh() {
        pendingRefreshAttempts++
        val delay = (PENDING_RETRY_BASE_MS * pendingRefreshAttempts).coerceAtMost(PENDING_RETRY_MAX_MS)
        mainHandler.removeCallbacks(pendingRefreshRunnable)
        mainHandler.postDelayed(pendingRefreshRunnable, delay)
    }

    private fun updateIndicatorCount() {
        val count = widgetViews.size
        if (count > 1) {
            indicator.visibility = View.VISIBLE
            indicator.setNumPages(count)
        } else {
            mainHandler.removeCallbacks(hideIndicatorRunnable)
            indicator.animate().cancel()
            indicator.visibility = View.GONE
        }
    }

    private fun showIndicatorTemporarily(autoHide: Boolean = true) {
        if (widgetViews.size <= 1) return
        mainHandler.removeCallbacks(hideIndicatorRunnable)
        indicator.visibility = View.VISIBLE
        indicator.animate().cancel()
        if (indicator.alpha < 1f) {
            indicator.animate()
                .alpha(1f)
                .setDuration(INDICATOR_FADE_IN_MS)
                .start()
        }
        if (autoHide) {
            scheduleIndicatorFadeOut()
        }
    }

    private fun scheduleIndicatorFadeOut() {
        if (widgetViews.size <= 1) return
        mainHandler.removeCallbacks(hideIndicatorRunnable)
        mainHandler.postDelayed(hideIndicatorRunnable, INDICATOR_HIDE_DELAY_MS)
    }

    private fun restartAutoRotate() {
        stopAutoRotate()
        scheduleAutoRotate()
    }

    private fun scheduleAutoRotate() {
        val info = stackInfo ?: return
        if (!info.autoRotate || widgetViews.size <= 1 || !isAttachedToWindow) return
        if (!isAutoRotateScheduled) {
            isAutoRotateScheduled = true
            mainHandler.postDelayed(autoRotateRunnable, AUTO_ROTATE_INTERVAL_MS)
        }
    }

    private fun stopAutoRotate() {
        isAutoRotateScheduled = false
        mainHandler.removeCallbacks(autoRotateRunnable)
    }

    private inner class WidgetStackAdapter : PagerAdapter() {
        override fun getCount(): Int = widgetViews.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

        override fun getItemPosition(obj: Any): Int {
            val idx = widgetViews.indexOf(obj)
            return if (idx >= 0) idx else POSITION_NONE
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view = widgetViews[position]
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            (obj as? View)?.let { container.removeView(it) }
        }
    }

    companion object {
        private const val TAG = "WidgetStackContentView"
        private const val OFFSCREEN_PAGE_LIMIT = 2
        private const val AUTO_ROTATE_INTERVAL_MS = 20_000L
        private const val INDICATOR_HIDE_DELAY_MS = 1_500L
        private const val INDICATOR_FADE_IN_MS = 150L
        private const val INDICATOR_FADE_OUT_MS = 300L
        private const val PENDING_RETRY_BASE_MS = 500L
        private const val PENDING_RETRY_MAX_MS = 2_000L
        private const val MAX_PENDING_RETRIES = 5
        private const val CONFIGURE_REFRESH_DELAY_MS = 350L
    }
}
