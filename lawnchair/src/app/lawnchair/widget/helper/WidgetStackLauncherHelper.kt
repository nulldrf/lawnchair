package app.lawnchair.widget.helper

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import app.lawnchair.widget.model.WidgetStackInfo
import app.lawnchair.widget.model.WidgetStackManager
import app.lawnchair.widget.ui.showWidgetStackEditSheet
import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.WidgetInflater

/**
 * Launcher-level hooks for widget stack creation, widget-picker completion, item inflation,
 * and stack deletion.
 */
object WidgetStackLauncherHelper {

    private const val TAG = "WidgetStackLauncherHelper"

    @JvmStatic
    fun handleBindWidgetResult(
        resultCode: Int,
        pendingProvider: ComponentName?,
        appWidgetId: Int,
    ) {
        if (resultCode == Activity.RESULT_OK && appWidgetId != LauncherAppWidgetInfo.NO_ID) {
            WidgetStackManager.transferPendingStackInfo(pendingProvider, appWidgetId)
        } else {
            WidgetStackManager.clearPendingStackInfoByProvider(pendingProvider)
            if (appWidgetId != LauncherAppWidgetInfo.NO_ID) {
                WidgetStackManager.clearPendingStackInfo(appWidgetId)
            }
        }
    }

    /**
     * Intercepts [Launcher.completeAddAppWidget] when the newly added widget is targeted at a
     * widget stack (via the Edit/Create Stack "+" flow) or is already inside a displayed stack.
     *
     * @return `true` if handled as a widget stack addition; `false` to proceed with normal single-widget placement.
     */
    @JvmStatic
    fun completeAddAppWidgetToStack(
        launcher: Launcher,
        appWidgetId: Int,
        itemInfo: ItemInfo,
        hostView: AppWidgetHostView?,
    ): Boolean {
        val workspace = launcher.workspace

        // 1. Deduplicate if this widget ID is already displayed in a stack (e.g. after configure activity).
        if (WidgetStackWorkspaceHelper.isAppWidgetIdInDisplayedWidgetStack(workspace, appWidgetId)) {
            WidgetStackWorkspaceHelper.onAppWidgetConfigureCompletedInStack(workspace, appWidgetId)
            return true
        }

        // 2. Check for pending stack target (by allocated ID first, then by provider).
        val provider = when (itemInfo) {
            is PendingAddWidgetInfo -> itemInfo.componentName
            is LauncherAppWidgetInfo -> itemInfo.providerName
            else -> itemInfo.targetComponent
        }
        val pendingStack = WidgetStackManager.consumePendingStackInfo(appWidgetId)
            ?: WidgetStackManager.consumePendingStackInfoByProvider(provider)
            ?: return false

        val widgetInfo = LauncherAppWidgetInfo(appWidgetId, provider).apply {
            spanX = pendingStack.spanX
            spanY = pendingStack.spanY
            container = pendingStack.container
            screenId = pendingStack.screenId
            cellX = pendingStack.cellX
            cellY = pendingStack.cellY
            widgetStackId = pendingStack.stackId
            sourceContainer = Favorites.CONTAINER_WIDGETS_TRAY
        }

        // Validate widget via WidgetInflater before mutating the stack.
        val inflater = WidgetInflater(launcher, launcher.packageManager.isSafeMode)
        val result = inflater.inflateAppWidget(widgetInfo)
        if (result.type == WidgetInflater.TYPE_DELETE) {
            Log.w(TAG, "Refusing to add invalid widget $appWidgetId to stack ${pendingStack.stackId}")
            return false
        }

        // Persist the new member widget row in `favorites`.
        launcher.modelWriter.addItemToDatabase(
            widgetInfo,
            pendingStack.container,
            pendingStack.screenId,
            pendingStack.cellX,
            pendingStack.cellY,
        )

        val updatedStack = pendingStack.withAddedWidget(appWidgetId)
        val existingStackView = WidgetStackWorkspaceHelper.findStackViewByStackId(workspace, pendingStack.stackId)

        if (existingStackView != null) {
            existingStackView.addWidget(widgetInfo, hostView as? LauncherAppWidgetHostView)
        } else {
            // Convert the single anchor widget on workspace into a new WidgetStackView.
            wrapSingleWidgetIntoStack(launcher, pendingStack, updatedStack, widgetInfo)
        }

        launcher.modelWriter.saveWidgetStack(updatedStack)

        // If the user came from the Edit/Create sheet "+", reopen it with the newly added widget
        // while preserving any unsaved edits they made before tapping "+".
        val suspended = WidgetStackManager.consumeSuspendedEditSession()
        if (suspended != null) {
            val resumedStack = suspended.stackInfo.withAddedWidget(appWidgetId)
            showWidgetStackEditSheet(
                launcher = launcher,
                initialStackInfo = resumedStack,
                isEditing = true,
                originalWidgetIds = suspended.originalWidgetIds,
            )
        }

        return true
    }

    private fun wrapSingleWidgetIntoStack(
        launcher: Launcher,
        pendingStack: WidgetStackInfo,
        updatedStack: WidgetStackInfo,
        addedWidgetInfo: LauncherAppWidgetInfo,
    ) {
        val primaryId = pendingStack.primaryWidgetId ?: return
        val presenterPos = launcher.cellPosMapper.mapModelToPresenter(addedWidgetInfo)
        val cellLayout = launcher.getCellLayout(pendingStack.container, presenterPos.screenId)

        var primaryView: View? = null
        var primaryInfo: LauncherAppWidgetInfo? = null
        if (cellLayout != null) {
            val container = cellLayout.shortcutsAndWidgets
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val tag = child.tag as? LauncherAppWidgetInfo
                if (tag != null && tag.appWidgetId == primaryId) {
                    primaryView = child
                    primaryInfo = tag
                    break
                }
            }
        }

        if (primaryInfo == null) {
            val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
            synchronized(bgDataModel) {
                for (item in bgDataModel.itemsIdMap) {
                    if (item is LauncherAppWidgetInfo && item.appWidgetId == primaryId) {
                        primaryInfo = item
                        break
                    }
                }
            }
        }

        primaryInfo?.widgetStackId = updatedStack.stackId
        primaryView?.let { (it.parent as? ViewGroup)?.removeView(it) }

        val preloaded = buildMap {
            primaryInfo?.let { put(it.appWidgetId, it) }
            put(addedWidgetInfo.appWidgetId, addedWidgetInfo)
        }

        val stackView = WidgetStackView(launcher).apply {
            setStackChangeListener(WidgetStackWorkspaceHelper.createStackChangeListener(launcher))
            setStackInfo(updatedStack, preloadedInfos = preloaded)
            tag = primaryInfo ?: addedWidgetInfo
        }

        launcher.workspace.addInScreen(
            stackView,
            updatedStack.container,
            presenterPos.screenId,
            presenterPos.cellX,
            presenterPos.cellY,
            updatedStack.spanX,
            updatedStack.spanY,
        )

        primaryInfo?.let {
            launcher.modelWriter.modifyItemInDatabase(
                it,
                updatedStack.container,
                updatedStack.screenId,
                updatedStack.cellX,
                updatedStack.cellY,
                updatedStack.spanX,
                updatedStack.spanY,
            )
        }
    }

    /**
     * Deletes an entire widget stack when the user removes a [WidgetStackView] or a stacked widget.
     *
     * @return `true` if stack deletion was handled; `false` to proceed with normal single-item removal.
     */
    @JvmStatic
    fun deleteStackForItem(
        launcher: Launcher,
        v: View?,
        itemInfo: LauncherAppWidgetInfo,
        reason: String?,
    ): Boolean {
        val stackView = v as? WidgetStackView
        val stackInfo = stackView?.getStackInfo()
        if (stackInfo != null) {
            launcher.modelWriter.deleteWidgetStack(stackInfo, launcher.appWidgetHolder, reason)
            return true
        }

        val stackId = itemInfo.widgetStackId ?: return false
        Executors.MODEL_EXECUTOR.execute {
            val db = launcher.modelWriter.modelDbController.db
            val loaded = db?.let { WidgetStackManager.loadStack(it, stackId) }
            if (loaded != null) {
                launcher.modelWriter.deleteWidgetStack(loaded, launcher.appWidgetHolder, reason)
            } else {
                launcher.modelWriter.deleteWidgetInfo(itemInfo, launcher.appWidgetHolder, reason)
            }
        }
        return true
    }

    /**
     * Inflates a [WidgetStackView] for the primary member of a widget stack during workspace bind,
     * returns `null` for non-first members (so they don't duplicate on workspace), and falls back
     * to normal single-widget inflation if the widget is not stacked or its stack metadata is gone.
     */
    @JvmStatic
    fun inflateWidgetOrStack(
        context: Context,
        item: LauncherAppWidgetInfo,
        cache: MutableMap<Long, WidgetStackInfo?>,
        fallbackInflate: () -> View?,
    ): View? {
        val stackId = item.widgetStackId ?: return fallbackInflate()

        val stackInfo = cache.getOrPut(stackId) {
            val db = LauncherAppState.getInstance(context).model.modelDbController.db
            db?.let { WidgetStackManager.loadStack(it, stackId) }
        }

        if (stackInfo == null || stackInfo.widgetIds.size <= 1) {
            // Orphaned or 1-widget stack: clear metadata and inflate as a normal single widget.
            item.widgetStackId = null
            return fallbackInflate()
        }

        if (stackInfo.primaryWidgetId != item.appWidgetId) {
            // Non-primary member: hosted inside the primary member's WidgetStackView.
            return null
        }

        val launcher = runCatching { Launcher.getLauncher(context) }.getOrNull()
            ?: return fallbackInflate()

        return WidgetStackView(context).apply {
            setStackChangeListener(WidgetStackWorkspaceHelper.createStackChangeListener(launcher))
            setStackInfo(stackInfo, preloadedInfos = mapOf(item.appWidgetId to item))
            tag = item
        }
    }
}
