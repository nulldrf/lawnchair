package app.lawnchair.widget.shortcut

import android.view.View
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.widget.ui.showWidgetStackDialog
import app.lawnchair.widget.view.WidgetStackView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.popup.SystemShortcut

/**
 * Long-press menu shortcut allowing users to create a widget stack from a single widget
 * or edit an existing widget stack. Respects Lawnchair's `lockHomeScreen` preference.
 */
class WidgetStackShortcut(
    target: Launcher,
    itemInfo: LauncherAppWidgetInfo,
    originalView: View,
    isEditing: Boolean,
) : SystemShortcut<Launcher>(
    SystemShortcut.Widgets.getDrawableId(),
    if (isEditing) R.string.edit_stack else R.string.create_stack,
    target,
    itemInfo,
    originalView,
) {

    override fun onClick(view: View) {
        AbstractFloatingView.closeAllOpenViews(mTarget)
        val widgetInfo = mItemInfo as? LauncherAppWidgetInfo ?: return
        showWidgetStackDialog(mTarget, widgetInfo)
    }

    companion object {
        @JvmField
        val FACTORY: SystemShortcut.Factory<Launcher> =
            SystemShortcut.Factory { activity: Launcher, itemInfo: ItemInfo, originalView: View ->
                val prefs2 = PreferenceManager2.getInstance(activity)
                if (prefs2.lockHomeScreen.firstCached()) {
                    return@Factory null
                }
                val widgetInfo = itemInfo as? LauncherAppWidgetInfo ?: return@Factory null
                val isEditing = originalView is WidgetStackView || widgetInfo.widgetStackId != null
                WidgetStackShortcut(activity, widgetInfo, originalView, isEditing)
            }
    }
}
