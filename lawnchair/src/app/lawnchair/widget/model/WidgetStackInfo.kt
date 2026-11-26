package app.lawnchair.widget.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Metadata describing a stack of widgets occupying the same home screen cell area.
 *
 * Persisted as JSON in [com.android.launcher3.LauncherSettings.Favorites.WIDGET_STACK_DATA]
 * on each member widget's `favorites` row, keyed by [stackId] (the primary widget's DB `_id`).
 */
@Serializable
@Parcelize
data class WidgetStackInfo(
    val stackId: Long,
    val widgetIds: List<Int>,
    var currentIndex: Int = 0,
    var autoRotate: Boolean = false,
    val container: Int,
    val screenId: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
) : Parcelable {

    val primaryWidgetId: Int?
        get() = widgetIds.firstOrNull()

    fun currentWidgetId(): Int? = widgetIds.getOrNull(currentIndex.coerceIn(0, (widgetIds.size - 1).coerceAtLeast(0)))

    fun withAddedWidget(appWidgetId: Int): WidgetStackInfo {
        if (widgetIds.contains(appWidgetId)) return this
        return copy(widgetIds = widgetIds + appWidgetId)
    }

    fun withRemovedWidget(appWidgetId: Int): WidgetStackInfo {
        val nextIds = widgetIds.filterNot { it == appWidgetId }
        val nextIndex = if (nextIds.isEmpty()) 0 else currentIndex.coerceIn(0, nextIds.lastIndex)
        return copy(widgetIds = nextIds, currentIndex = nextIndex)
    }

    fun withBounds(
        container: Int = this.container,
        screenId: Int = this.screenId,
        cellX: Int = this.cellX,
        cellY: Int = this.cellY,
        spanX: Int = this.spanX,
        spanY: Int = this.spanY,
    ): WidgetStackInfo = copy(
        container = container,
        screenId = screenId,
        cellX = cellX,
        cellY = cellY,
        spanX = spanX,
        spanY = spanY,
    )
}
