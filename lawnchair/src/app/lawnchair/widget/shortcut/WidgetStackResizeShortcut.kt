package app.lawnchair.widget.shortcut

import android.appwidget.AppWidgetProviderInfo
import android.view.View
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo

/**
 * Long-press menu shortcut that opens [AppWidgetResizeFrame] for a [WidgetStackView]
 * (using the currently displayed widget's resize constraints). Only shown for widget stacks.
 */
class WidgetStackResizeShortcut(
    target: Launcher,
    itemInfo: LauncherAppWidgetInfo,
    originalView: WidgetStackView,
) : SystemShortcut<Launcher>(
    R.drawable.ic_aspect_ratio,
    R.string.widget_stack_resize,
    target,
    itemInfo,
    originalView,
) {

    override fun onClick(view: View) {
        AbstractFloatingView.closeAllOpenViews(mTarget)
        val stackView = mOriginalView as? WidgetStackView ?: return
        val cellLayout = (stackView.parent?.parent as? CellLayout) ?: return
        AppWidgetResizeFrame.showForWidgetStack(stackView, cellLayout)
    }

    companion object {
        @JvmField
        val FACTORY: SystemShortcut.Factory<Launcher> =
            SystemShortcut.Factory { activity: Launcher, itemInfo: ItemInfo, originalView: View ->
                val stackView = originalView as? WidgetStackView ?: return@Factory null
                val prefs2 = PreferenceManager2.getInstance(activity)
                if (prefs2.lockHomeScreen.firstCached()) {
                    return@Factory null
                }
                val widgetInfo = itemInfo as? LauncherAppWidgetInfo ?: return@Factory null
                if (widgetInfo.container == Favorites.CONTAINER_HOTSEAT) {
                    return@Factory null
                }
                val forceResize = prefs2.forceWidgetResize.firstCached()
                val currentHostView = stackView.getCurrentWidgetView() ?: return@Factory null
                val providerInfo = currentHostView.appWidgetInfo as? LauncherAppWidgetProviderInfo
                val canResize = forceResize ||
                    (providerInfo != null && providerInfo.resizeMode != AppWidgetProviderInfo.RESIZE_NONE)
                if (!canResize) {
                    return@Factory null
                }
                WidgetStackResizeShortcut(activity, widgetInfo, stackView)
            }
    }
}
