package app.lawnchair.widget.helper

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.View
import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.model.WidgetsListBaseEntry
import com.android.launcher3.widget.model.WidgetsListContentEntry
import com.android.launcher3.widget.model.WidgetsListHeaderEntry
import com.android.launcher3.widget.picker.WidgetRecommendationCategory
import java.util.function.Predicate

/**
 * One UI-style widget size compatibility rules for widget stacks.
 *
 * A candidate widget can be stacked into a `(targetSpanX, targetSpanY)` slot only if its own
 * default size matches or its declared resize bounds allow resizing to `(targetSpanX, targetSpanY)`.
 */
object WidgetStackCompatibility {

    @JvmStatic
    fun isProviderCompatibleWithStack(
        info: LauncherAppWidgetProviderInfo?,
        targetSpanX: Int,
        targetSpanY: Int,
    ): Boolean {
        if (info == null || targetSpanX <= 0 || targetSpanY <= 0) return false

        val canResizeH = (info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
        val canResizeV = (info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0

        val fitsHorizontal = if (canResizeH) {
            val minX = info.minSpanX.coerceAtLeast(1)
            val maxX = if (info.maxSpanX > 0) info.maxSpanX.coerceAtLeast(minX) else Int.MAX_VALUE
            targetSpanX in minX..maxX
        } else {
            targetSpanX == info.spanX
        }

        if (!fitsHorizontal) return false

        val fitsVertical = if (canResizeV) {
            val minY = info.minSpanY.coerceAtLeast(1)
            val maxY = if (info.maxSpanY > 0) info.maxSpanY.coerceAtLeast(minY) else Int.MAX_VALUE
            targetSpanY in minY..maxY
        } else {
            targetSpanY == info.spanY
        }

        return fitsVertical
    }

    @JvmStatic
    fun isWidgetItemCompatibleWithStack(
        item: WidgetItem?,
        targetSpanX: Int,
        targetSpanY: Int,
    ): Boolean {
        val providerInfo = item?.widgetInfo ?: return false
        return isProviderCompatibleWithStack(providerInfo, targetSpanX, targetSpanY)
    }

    @JvmStatic
    fun isDraggedWidgetCompatibleWithDropTarget(
        context: Context,
        draggedWidgetInfo: LauncherAppWidgetInfo,
        dropOverView: View?,
    ): Boolean {
        val targetSpanX: Int
        val targetSpanY: Int
        when (dropOverView) {
            is WidgetStackView -> {
                val stackInfo = dropOverView.getStackInfo() ?: return false
                targetSpanX = stackInfo.spanX
                targetSpanY = stackInfo.spanY
            }

            is LauncherAppWidgetHostView -> {
                val targetInfo = dropOverView.tag as? LauncherAppWidgetInfo ?: return false
                targetSpanX = targetInfo.spanX
                targetSpanY = targetInfo.spanY
            }

            else -> return false
        }

        val provider = WidgetManagerHelper(context)
            .getLauncherAppWidgetInfo(draggedWidgetInfo.appWidgetId, draggedWidgetInfo.providerName)
            ?: return false

        return isProviderCompatibleWithStack(provider, targetSpanX, targetSpanY)
    }

    @JvmStatic
    fun filterWidgetListEntries(
        entries: List<WidgetsListBaseEntry>,
        filter: Predicate<WidgetItem>,
    ): List<WidgetsListBaseEntry> {
        val filtered = ArrayList<WidgetsListBaseEntry>(entries.size)
        for (entry in entries) {
            val matchingWidgets = entry.mWidgets.filter { filter.test(it) }
            if (matchingWidgets.isEmpty()) continue
            when (entry) {
                is WidgetsListHeaderEntry -> {
                    val header = if (entry.isSearchEntry()) {
                        WidgetsListHeaderEntry.createForSearch(
                            entry.mPkgItem,
                            entry.mTitleSectionName,
                            matchingWidgets,
                        )
                    } else {
                        WidgetsListHeaderEntry.create(
                            entry.mPkgItem,
                            entry.mTitleSectionName,
                            matchingWidgets,
                        )
                    }
                    filtered.add(if (entry.isWidgetListShown()) header.withWidgetListShown() else header)
                }

                is WidgetsListContentEntry -> {
                    filtered.add(
                        WidgetsListContentEntry(
                            entry.mPkgItem,
                            entry.mTitleSectionName,
                            matchingWidgets,
                        ),
                    )
                }

                else -> filtered.add(entry)
            }
        }
        return filtered
    }

    @JvmStatic
    fun filterRecommendations(
        recommendations: Map<WidgetRecommendationCategory, List<WidgetItem>>,
        filter: Predicate<WidgetItem>,
    ): Map<WidgetRecommendationCategory, List<WidgetItem>> {
        val result = LinkedHashMap<WidgetRecommendationCategory, List<WidgetItem>>()
        for ((category, items) in recommendations) {
            val matching = items.filter { filter.test(it) }
            if (matching.isNotEmpty()) {
                result[category] = matching
            }
        }
        return result
    }
}
