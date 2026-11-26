package app.lawnchair.widget.helper

import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import app.lawnchair.widget.model.WidgetStackInfo
import app.lawnchair.widget.model.WidgetStackManager
import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.util.WidgetSizes

/**
 * Workspace-level operations for widget stacks (lookup, drag-to-stack, drop persistence,
 * auto-collapse to single widget, and touch hit-testing).
 */
object WidgetStackWorkspaceHelper {

    private const val TAG = "WidgetStackWorkspaceHelper"

    @JvmStatic
    fun createStackChangeListener(launcher: Launcher): WidgetStackView.WidgetStackChangeListener =
        object : WidgetStackView.WidgetStackChangeListener {
            override fun onStackChanged(stackView: WidgetStackView) {
                val info = stackView.getStackInfo() ?: return
                launcher.modelWriter.saveWidgetStack(info)
            }

            override fun onStackCollapsed(stackView: WidgetStackView, remainingWidgetId: Int) {
                collapseStackToSingleWidget(launcher, stackView, remainingWidgetId)
            }
        }

    @JvmStatic
    fun findStackViewByStackId(workspace: Workspace<*>, stackId: Long): WidgetStackView? {
        var found: WidgetStackView? = null
        workspace.forEachVisibleOrAllCellLayout { cellLayout ->
            if (found != null) return@forEachVisibleOrAllCellLayout
            val container = cellLayout.shortcutsAndWidgets
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is WidgetStackView && child.getStackInfo()?.stackId == stackId) {
                    found = child
                    return@forEachVisibleOrAllCellLayout
                }
            }
        }
        return found
    }

    @JvmStatic
    fun findStackViewContainingWidget(workspace: Workspace<*>, appWidgetId: Int): WidgetStackView? {
        var found: WidgetStackView? = null
        workspace.forEachVisibleOrAllCellLayout { cellLayout ->
            if (found != null) return@forEachVisibleOrAllCellLayout
            val container = cellLayout.shortcutsAndWidgets
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is WidgetStackView && child.getStackInfo()?.widgetIds?.contains(appWidgetId) == true) {
                    found = child
                    return@forEachVisibleOrAllCellLayout
                }
            }
        }
        return found
    }

    @JvmStatic
    fun isAppWidgetIdInDisplayedWidgetStack(workspace: Workspace<*>, appWidgetId: Int): Boolean =
        findStackViewContainingWidget(workspace, appWidgetId) != null

    @JvmStatic
    fun onAppWidgetConfigureCompletedInStack(workspace: Workspace<*>, appWidgetId: Int): Boolean {
        val stackView = findStackViewContainingWidget(workspace, appWidgetId) ?: return false
        stackView.onAppWidgetConfigureCompleted(appWidgetId)
        return true
    }

    /**
     * Checks whether [ev] (in [container]'s coordinate space) lands inside any visible
     * [WidgetStackView] descendant. Works across both Workspace and multi-page Hotseat.
     */
    @JvmStatic
    fun isTouchOverWidgetStack(container: ViewGroup, ev: MotionEvent): Boolean {
        val coords = floatArrayOf(ev.x, ev.y)
        return isPointOverWidgetStackRecursive(container, container, coords)
    }

    private fun isPointOverWidgetStackRecursive(
        root: ViewGroup,
        current: ViewGroup,
        rootCoords: FloatArray,
    ): Boolean {
        for (i in current.childCount - 1 downTo 0) {
            val child = current.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child is WidgetStackView) {
                val local = floatArrayOf(rootCoords[0], rootCoords[1])
                Utilities.mapCoordInSelfToDescendant(child, root, local)
                if (local[0] >= 0f && local[0] <= child.width &&
                    local[1] >= 0f && local[1] <= child.height
                ) {
                    return true
                }
            } else if (child is ViewGroup) {
                if (isPointOverWidgetStackRecursive(root, child, rootCoords)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Merges a dragged widget onto a target single widget on the workspace/hotseat into a new
     * [WidgetStackView].
     */
    @JvmStatic
    fun createStackFromDrop(
        launcher: Launcher,
        targetView: View,
        draggedView: View?,
        draggedWidgetInfo: LauncherAppWidgetInfo,
        targetWidgetInfo: LauncherAppWidgetInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ): WidgetStackView? {
        if (draggedWidgetInfo.appWidgetId == targetWidgetInfo.appWidgetId) return null

        val validContainer = if (container == Favorites.CONTAINER_HOTSEAT) {
            Favorites.CONTAINER_HOTSEAT
        } else {
            Favorites.CONTAINER_DESKTOP
        }

        val spanX = targetWidgetInfo.spanX.coerceAtLeast(1)
        val spanY = targetWidgetInfo.spanY.coerceAtLeast(1)
        val stackId = targetWidgetInfo.id.toLong()

        val stackInfo = WidgetStackInfo(
            stackId = stackId,
            widgetIds = listOf(targetWidgetInfo.appWidgetId, draggedWidgetInfo.appWidgetId),
            currentIndex = 1,
            autoRotate = false,
            container = validContainer,
            screenId = screenId,
            cellX = cellX,
            cellY = cellY,
            spanX = spanX,
            spanY = spanY,
        )

        targetWidgetInfo.widgetStackId = stackId
        draggedWidgetInfo.widgetStackId = stackId
        draggedWidgetInfo.container = validContainer
        draggedWidgetInfo.screenId = screenId
        draggedWidgetInfo.cellX = cellX
        draggedWidgetInfo.cellY = cellY
        draggedWidgetInfo.spanX = spanX
        draggedWidgetInfo.spanY = spanY

        val workspace = launcher.workspace
        val targetCellLayout = launcher.getCellLayout(validContainer, screenId)
        targetCellLayout?.removeView(targetView)
        (draggedView?.parent as? ViewGroup)?.removeView(draggedView)

        val stackView = WidgetStackView(launcher).apply {
            setStackChangeListener(createStackChangeListener(launcher))
            setStackInfo(
                stackInfo,
                preloadedInfos = mapOf(
                    targetWidgetInfo.appWidgetId to targetWidgetInfo,
                    draggedWidgetInfo.appWidgetId to draggedWidgetInfo,
                ),
            )
            tag = targetWidgetInfo
        }

        workspace.addInScreen(stackView, validContainer, screenId, cellX, cellY, spanX, spanY)

        launcher.modelWriter.modifyItemInDatabase(
            targetWidgetInfo,
            validContainer,
            screenId,
            cellX,
            cellY,
            spanX,
            spanY,
        )
        launcher.modelWriter.modifyItemInDatabase(
            draggedWidgetInfo,
            validContainer,
            screenId,
            cellX,
            cellY,
            spanX,
            spanY,
        )
        launcher.modelWriter.saveWidgetStack(stackInfo)

        return stackView
    }

    /**
     * Adds a dragged widget onto an existing [WidgetStackView].
     */
    @JvmStatic
    fun addToExistingStackFromDrop(
        launcher: Launcher,
        stackView: WidgetStackView,
        draggedView: View?,
        draggedWidgetInfo: LauncherAppWidgetInfo,
    ): Boolean {
        val stackInfo = stackView.getStackInfo() ?: return false
        if (stackInfo.widgetIds.contains(draggedWidgetInfo.appWidgetId)) return false

        (draggedView?.parent as? ViewGroup)?.removeView(draggedView)
        val hostView = draggedView as? LauncherAppWidgetHostView
        val added = stackView.addWidget(draggedWidgetInfo, hostView)
        if (added) {
            launcher.modelWriter.modifyItemInDatabase(
                draggedWidgetInfo,
                stackInfo.container,
                stackInfo.screenId,
                stackInfo.cellX,
                stackInfo.cellY,
                stackInfo.spanX,
                stackInfo.spanY,
            )
            stackView.getStackInfo()?.let { launcher.modelWriter.saveWidgetStack(it) }
        }
        return added
    }

    /**
     * Persists a moved [WidgetStackView] and all of its member widgets to the database.
     * Copies member list outside the `BgDataModel` lock before invoking `ModelWriter`.
     */
    @JvmStatic
    fun persistMovedStack(
        launcher: Launcher,
        stackView: WidgetStackView,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        val current = stackView.getStackInfo() ?: return
        val updated = current.withBounds(
            container = container,
            screenId = screenId,
            cellX = cellX,
            cellY = cellY,
        )

        val membersToUpdate = mutableListOf<LauncherAppWidgetInfo>()
        val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
        synchronized(bgDataModel) {
            for (item in bgDataModel.itemsIdMap) {
                if (item is LauncherAppWidgetInfo && updated.widgetIds.contains(item.appWidgetId)) {
                    membersToUpdate.add(item)
                }
            }
        }

        val writer = launcher.modelWriter
        for (member in membersToUpdate) {
            writer.modifyItemInDatabase(
                member,
                container,
                screenId,
                cellX,
                cellY,
                updated.spanX,
                updated.spanY,
            )
        }
        // Also update the stack view's in-memory bounds & JSON in DB
        stackView.setStackInfo(updated)
        writer.saveWidgetStack(updated)
    }

    /**
     * Collapses a 1-widget [WidgetStackView] back into a normal single [LauncherAppWidgetHostView].
     * Always performs any DB fallback lookup on [Executors.MODEL_EXECUTOR] (never on the UI thread).
     */
    @JvmStatic
    fun collapseStackToSingleWidget(
        launcher: Launcher,
        stackView: WidgetStackView,
        remainingWidgetId: Int,
    ) {
        val stackInfo = stackView.getStackInfo() ?: return

        val inMemory = findWidgetInfoInMemory(launcher, remainingWidgetId)
            ?: (stackView.getWidgetViews().firstOrNull {
                (it.tag as? LauncherAppWidgetInfo)?.appWidgetId == remainingWidgetId
            }?.tag as? LauncherAppWidgetInfo)

        if (inMemory != null) {
            finishCollapseOnMainThread(launcher, stackView, stackInfo, inMemory)
        } else {
            Executors.MODEL_EXECUTOR.execute {
                val db = launcher.modelWriter.modelDbController.db
                val loaded = db?.let {
                    WidgetStackManager.loadLauncherAppWidgetInfoFromFavorites(it, remainingWidgetId)
                }
                Executors.MAIN_EXECUTOR.execute {
                    if (loaded != null) {
                        finishCollapseOnMainThread(launcher, stackView, stackInfo, loaded)
                    } else {
                        Log.w(TAG, "Unable to locate remaining widget $remainingWidgetId for stack collapse")
                        (stackView.parent as? ViewGroup)?.removeView(stackView)
                        Executors.MODEL_EXECUTOR.execute {
                            launcher.modelWriter.modelDbController.db?.let {
                                WidgetStackManager.deleteStack(it, stackInfo.stackId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun finishCollapseOnMainThread(
        launcher: Launcher,
        stackView: WidgetStackView,
        stackInfo: WidgetStackInfo,
        remainingInfo: LauncherAppWidgetInfo,
    ) {
        remainingInfo.widgetStackId = null
        remainingInfo.container = stackInfo.container
        remainingInfo.screenId = stackInfo.screenId
        remainingInfo.cellX = stackInfo.cellX
        remainingInfo.cellY = stackInfo.cellY
        remainingInfo.spanX = stackInfo.spanX
        remainingInfo.spanY = stackInfo.spanY

        val parentCellLayout = (stackView.parent?.parent as? CellLayout)
        parentCellLayout?.removeView(stackView) ?: (stackView.parent as? ViewGroup)?.removeView(stackView)

        val singleView = launcher.itemInflater?.inflateItem(remainingInfo, null)
        if (singleView != null) {
            val presenterPos = launcher.cellPosMapper.mapModelToPresenter(remainingInfo)
            launcher.workspace.addInScreen(
                singleView,
                remainingInfo.container,
                presenterPos.screenId,
                presenterPos.cellX,
                presenterPos.cellY,
                remainingInfo.spanX,
                remainingInfo.spanY,
            )
            if (singleView is LauncherAppWidgetHostView) {
                WidgetSizes.updateWidgetSizeRanges(
                    singleView,
                    launcher,
                    remainingInfo.spanX,
                    remainingInfo.spanY,
                )
            }
        }

        launcher.modelWriter.modifyItemInDatabase(
            remainingInfo,
            remainingInfo.container,
            remainingInfo.screenId,
            remainingInfo.cellX,
            remainingInfo.cellY,
            remainingInfo.spanX,
            remainingInfo.spanY,
        )

        Executors.MODEL_EXECUTOR.execute {
            launcher.modelWriter.modelDbController.db?.let { db ->
                WidgetStackManager.deleteStack(db, stackInfo.stackId)
            }
        }
    }

    private fun findWidgetInfoInMemory(launcher: Launcher, appWidgetId: Int): LauncherAppWidgetInfo? {
        val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
        synchronized(bgDataModel) {
            for (item in bgDataModel.itemsIdMap) {
                if (item is LauncherAppWidgetInfo && item.appWidgetId == appWidgetId) {
                    return item
                }
            }
        }
        return null
    }

    private inline fun Workspace<*>.forEachVisibleOrAllCellLayout(block: (CellLayout) -> Unit) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is CellLayout) {
                block(child)
            }
        }
        val hotseat = hotseat
        if (hotseat != null) {
            val count = hotseat.childCount
            for (i in 0 until count) {
                val child = hotseat.getChildAt(i)
                if (child is CellLayout) {
                    block(child)
                }
            }
            val currentHotseatLayout = hotseat.shortcutsAndWidgets?.parent as? CellLayout
            if (currentHotseatLayout != null) {
                block(currentHotseatLayout)
            }
        }
    }
}
