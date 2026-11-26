package app.lawnchair.widget.ui

import android.appwidget.AppWidgetManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.lawnchair.views.ComposeBottomSheet
import app.lawnchair.widget.helper.WidgetStackWorkspaceHelper
import app.lawnchair.widget.model.WidgetStackInfo
import app.lawnchair.widget.model.WidgetStackManager
import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors
import com.android.launcher3.widget.PendingAddWidgetInfo
import com.android.launcher3.widget.picker.WidgetsFullSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point invoked by [app.lawnchair.widget.shortcut.WidgetStackShortcut] to create a new
 * widget stack or edit an existing one.
 */
fun showWidgetStackDialog(launcher: Launcher, widgetInfo: LauncherAppWidgetInfo) {
    val stackId = widgetInfo.widgetStackId
    if (stackId == null) {
        val draft = WidgetStackInfo(
            stackId = widgetInfo.id.toLong(),
            widgetIds = listOf(widgetInfo.appWidgetId),
            currentIndex = 0,
            autoRotate = false,
            container = widgetInfo.container,
            screenId = widgetInfo.screenId,
            cellX = widgetInfo.cellX,
            cellY = widgetInfo.cellY,
            spanX = widgetInfo.spanX.coerceAtLeast(1),
            spanY = widgetInfo.spanY.coerceAtLeast(1),
        )
        showWidgetStackEditSheet(
            launcher = launcher,
            initialStackInfo = draft,
            isEditing = false,
            originalWidgetIds = draft.widgetIds,
        )
        return
    }

    Executors.MODEL_EXECUTOR.execute {
        val db = launcher.modelWriter.modelDbController.db
        val loaded = db?.let { WidgetStackManager.loadStack(it, stackId) }
            ?: WidgetStackWorkspaceHelper.findStackViewByStackId(launcher.workspace, stackId)?.getStackInfo()

        if (loaded == null || loaded.widgetIds.isEmpty()) {
            return@execute
        }

        // Validate member IDs against both BgDataModel and DB (so non-first members survive reload).
        val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
        val liveIds = mutableListOf<Int>()
        for (widgetId in loaded.widgetIds) {
            val existsInModel = synchronized(bgDataModel) {
                var found = false
                for (item in bgDataModel.itemsIdMap) {
                    if (item is LauncherAppWidgetInfo && item.appWidgetId == widgetId) {
                        found = true
                        break
                    }
                }
                found
            }
            val existsInDb = existsInModel || (
                db != null && WidgetStackManager.loadLauncherAppWidgetInfoFromFavorites(db, widgetId) != null
                )
            if (existsInDb) {
                liveIds.add(widgetId)
            }
        }

        if (liveIds.isEmpty()) return@execute

        val sanitized = loaded.copy(
            widgetIds = liveIds,
            currentIndex = loaded.currentIndex.coerceIn(0, liveIds.lastIndex),
        )

        Executors.MAIN_EXECUTOR.execute {
            showWidgetStackEditSheet(
                launcher = launcher,
                initialStackInfo = sanitized,
                isEditing = true,
                originalWidgetIds = sanitized.widgetIds,
            )
        }
    }
}

/**
 * Displays the Compose bottom sheet for editing/creating a widget stack, managing member preview
 * loading, widget picker handoff (preserving unsaved edits), and Save/Collapse commits.
 */
fun showWidgetStackEditSheet(
    launcher: Launcher,
    initialStackInfo: WidgetStackInfo,
    isEditing: Boolean,
    originalWidgetIds: List<Int> = initialStackInfo.widgetIds,
) {
    ComposeBottomSheet.show(launcher) {
        val memberIds = remember(initialStackInfo) {
            mutableStateListOf<Int>().apply { addAll(initialStackInfo.widgetIds) }
        }
        var autoRotate by remember(initialStackInfo) {
            mutableStateOf(initialStackInfo.autoRotate)
        }
        var memberUiMap by remember(initialStackInfo) {
            mutableStateOf<Map<Int, WidgetStackMemberUi>>(emptyMap())
        }

        LaunchedEffect(memberIds.toList()) {
            val resolved = withContext(Dispatchers.IO) {
                resolveMemberUis(launcher, memberIds.toList())
            }
            memberUiMap = resolved
        }

        val members = memberIds.map { id ->
            memberUiMap[id] ?: WidgetStackMemberUi(
                appWidgetId = id,
                label = launcher.getString(R.string.widget_stack_title),
                widgetItem = null,
            )
        }

        WidgetStackEditContent(
            isEditing = isEditing,
            spanX = initialStackInfo.spanX,
            spanY = initialStackInfo.spanY,
            members = members,
            autoRotate = autoRotate,
            onMoveMember = { fromIndex, toIndex ->
                if (fromIndex in memberIds.indices && toIndex in memberIds.indices && fromIndex != toIndex) {
                    val moved = memberIds.removeAt(fromIndex)
                    memberIds.add(toIndex, moved)
                }
            },
            onRemoveMember = { appWidgetId ->
                if (memberIds.size > 1) {
                    memberIds.remove(appWidgetId)
                }
            },
            onAddWidgetClick = {
                val draft = initialStackInfo.copy(
                    widgetIds = memberIds.toList(),
                    autoRotate = autoRotate,
                )
                WidgetStackManager.suspendEditSession(
                    WidgetStackManager.SuspendedEditSession(
                        stackInfo = draft,
                        originalWidgetIds = originalWidgetIds,
                        isEditing = isEditing,
                    ),
                )
                close(true)
                openWidgetPickerForStack(launcher, draft)
            },
            onAutoRotateChange = { checked ->
                autoRotate = checked
            },
            onCancel = {
                close(true)
            },
            onSave = {
                val updated = initialStackInfo.copy(
                    widgetIds = memberIds.toList(),
                    currentIndex = initialStackInfo.currentIndex.coerceIn(0, (memberIds.size - 1).coerceAtLeast(0)),
                    autoRotate = autoRotate,
                )
                commitStackEdits(
                    launcher = launcher,
                    updatedStack = updated,
                    originalWidgetIds = originalWidgetIds,
                    isEditing = isEditing,
                )
                close(true)
            },
        )
    }
}

private fun openWidgetPickerForStack(
    launcher: Launcher,
    draftStack: WidgetStackInfo,
) {
    var picked = false
    WidgetsFullSheet.show(launcher, true).apply {
        mayUpdateTitleAndDescription(
            launcher.getString(R.string.widget_stack_picker_title),
            null,
        )
        setWidgetPickListener { widgetItem ->
            val providerInfo = widgetItem.widgetInfo ?: return@setWidgetPickListener
            picked = true
            val pendingInfo = PendingAddWidgetInfo(providerInfo, draftStack.container).apply {
                spanX = draftStack.spanX
                spanY = draftStack.spanY
            }
            WidgetStackManager.storePendingStackInfoByProvider(providerInfo.provider, draftStack)
            launcher.addPendingItem(
                pendingInfo,
                draftStack.container,
                draftStack.screenId,
                intArrayOf(draftStack.cellX, draftStack.cellY),
                draftStack.spanX,
                draftStack.spanY,
            )
        }
        addOnCloseListener {
            if (!picked) {
                val suspended = WidgetStackManager.consumeSuspendedEditSession() ?: return@addOnCloseListener
                showWidgetStackEditSheet(
                    launcher = launcher,
                    initialStackInfo = suspended.stackInfo,
                    isEditing = suspended.isEditing,
                    originalWidgetIds = suspended.originalWidgetIds,
                )
            }
        }
    }
}

private fun commitStackEdits(
    launcher: Launcher,
    updatedStack: WidgetStackInfo,
    originalWidgetIds: List<Int>,
    isEditing: Boolean,
) {
    val removedIds = originalWidgetIds.filterNot { it in updatedStack.widgetIds }

    // 1. Delete removed widgets from model, DB, and AppWidgetHost.
    if (removedIds.isNotEmpty()) {
        Executors.MODEL_EXECUTOR.execute {
            val db = launcher.modelWriter.modelDbController.db
            val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
            for (removedId in removedIds) {
                val inMemory = synchronized(bgDataModel) {
                    var found: LauncherAppWidgetInfo? = null
                    for (item in bgDataModel.itemsIdMap) {
                        if (item is LauncherAppWidgetInfo && item.appWidgetId == removedId) {
                            found = item
                            break
                        }
                    }
                    found
                }
                if (inMemory != null) {
                    launcher.modelWriter.deleteWidgetInfo(
                        inMemory,
                        launcher.appWidgetHolder,
                        "Removed from widget stack",
                    )
                } else {
                    if (removedId > 0) {
                        launcher.appWidgetHolder.deleteAppWidgetId(removedId)
                    }
                    db?.delete(
                        com.android.launcher3.LauncherSettings.Favorites.TABLE_NAME,
                        "${com.android.launcher3.LauncherSettings.Favorites.APPWIDGET_ID} = ?",
                        arrayOf(removedId.toString()),
                    )
                }
            }
        }
    }

    val existingStackView = WidgetStackWorkspaceHelper.findStackViewByStackId(
        launcher.workspace,
        updatedStack.stackId,
    )

    // 2. If only 1 widget remains, collapse back to a normal single widget (never persist a 1-widget stack).
    if (updatedStack.widgetIds.size <= 1) {
        if (existingStackView != null && updatedStack.widgetIds.size == 1) {
            WidgetStackWorkspaceHelper.collapseStackToSingleWidget(
                launcher,
                existingStackView,
                updatedStack.widgetIds.first(),
            )
        }
        return
    }

    // 3. Otherwise persist and update (or create) the WidgetStackView.
    if (existingStackView != null) {
        existingStackView.setStackInfo(updatedStack)
        launcher.modelWriter.saveWidgetStack(updatedStack)
    } else if (!isEditing) {
        val primaryId = updatedStack.primaryWidgetId ?: return
        val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
        val primaryInfo = synchronized(bgDataModel) {
            var found: LauncherAppWidgetInfo? = null
            for (item in bgDataModel.itemsIdMap) {
                if (item is LauncherAppWidgetInfo && item.appWidgetId == primaryId) {
                    found = item
                    break
                }
            }
            found
        } ?: return

        val presenterPos = launcher.cellPosMapper.mapModelToPresenter(primaryInfo)
        val cellLayout = launcher.getCellLayout(updatedStack.container, presenterPos.screenId)
        var primaryView: View? = null
        if (cellLayout != null) {
            val container = cellLayout.shortcutsAndWidgets
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                val tag = child.tag as? LauncherAppWidgetInfo
                if (tag != null && tag.appWidgetId == primaryId) {
                    primaryView = child
                    break
                }
            }
        }
        primaryView?.let { (it.parent as? ViewGroup)?.removeView(it) }

        val stackView = WidgetStackView(launcher).apply {
            setStackChangeListener(WidgetStackWorkspaceHelper.createStackChangeListener(launcher))
            setStackInfo(updatedStack, preloadedInfos = mapOf(primaryId to primaryInfo))
            tag = primaryInfo
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
        launcher.modelWriter.saveWidgetStack(updatedStack)
    }
}

private fun resolveMemberUis(
    launcher: Launcher,
    widgetIds: List<Int>,
): Map<Int, WidgetStackMemberUi> {
    val appWidgetManager = AppWidgetManager.getInstance(launcher)
    val pm = launcher.packageManager
    val allWidgetItems = launcher.popupDataProvider.allWidgets
        .flatMap { it.mWidgets }
        .associateBy { it.componentName }

    val bgDataModel = LauncherAppState.getInstance(launcher).model.getBgDataModel()
    val db = launcher.modelWriter.modelDbController.db

    return widgetIds.associateWith { widgetId ->
        val provider = appWidgetManager.getAppWidgetInfo(widgetId)?.provider
            ?: synchronized(bgDataModel) {
                var found: android.content.ComponentName? = null
                for (item in bgDataModel.itemsIdMap) {
                    if (item is LauncherAppWidgetInfo && item.appWidgetId == widgetId) {
                        found = item.providerName
                        break
                    }
                }
                found
            }
            ?: db?.let { WidgetStackManager.loadLauncherAppWidgetInfoFromFavorites(it, widgetId)?.providerName }

        val widgetItem: WidgetItem? = provider?.let { allWidgetItems[it] }
        val label = widgetItem?.label
            ?: appWidgetManager.getAppWidgetInfo(widgetId)?.loadLabel(pm)
            ?: provider?.packageName?.let { pkg ->
                runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            }
            ?: launcher.getString(R.string.widget_stack_title)

        WidgetStackMemberUi(
            appWidgetId = widgetId,
            label = label,
            widgetItem = widgetItem,
        )
    }
}
