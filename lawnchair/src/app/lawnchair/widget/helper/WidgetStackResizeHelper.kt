package app.lawnchair.widget.helper

import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.model.data.LauncherAppWidgetInfo

/**
 * Helper for propagating [com.android.launcher3.AppWidgetResizeFrame] resizes to all member
 * widgets and persisted metadata of a [WidgetStackView].
 */
object WidgetStackResizeHelper {

    @JvmStatic
    fun updateMemberSizeRanges(stackView: WidgetStackView, spanX: Int, spanY: Int) {
        stackView.updateMemberWidgetSizeRanges(spanX, spanY)
    }

    @JvmStatic
    fun commitStackResize(
        launcher: Launcher,
        stackView: WidgetStackView,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        val current = stackView.getStackInfo() ?: return
        val updated = current.withBounds(
            cellX = cellX,
            cellY = cellY,
            spanX = spanX,
            spanY = spanY,
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
                updated.container,
                updated.screenId,
                cellX,
                cellY,
                spanX,
                spanY,
            )
        }

        stackView.updateMemberWidgetSizeRanges(spanX, spanY)
        writer.saveWidgetStack(updated)
    }
}
