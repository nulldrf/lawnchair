package app.lawnchair

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.RemoteViews
import app.lawnchair.smartspace.SmartspaceAppWidgetProvider
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherAppWidgetHostView

class LawnchairAppWidgetHostView @JvmOverloads constructor(
    context: Context,
    private var previewMode: Boolean = false,
) : LauncherAppWidgetHostView(context) {

    private var customView: ViewGroup? = null

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        inflateCustomView(info)
        super.setAppWidget(appWidgetId, info)
    }

    fun disablePreviewMode() {
        previewMode = false
        inflateCustomView(appWidgetInfo)
    }

    private fun inflateCustomView(info: AppWidgetProviderInfo) {
        customView = inflateCustomView(context, info, previewMode)
        if (customView == null) {
            return
        }
        customView!!.setOnLongClickListener(this)
        removeAllViews()
        addView(customView, MATCH_PARENT, MATCH_PARENT)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        if (customView != null) return
        super.updateAppWidget(remoteViews)
    }

    override fun getDefaultView(): View {
        if (customView != null) return getEmptyView()
        return super.getDefaultView()
    }

    override fun getErrorView(): View {
        if (customView != null) return getEmptyView()
        return super.getErrorView()
    }

    private fun getEmptyView(): View {
        return View(context)
    }

    companion object {

        private val customLayouts = mapOf(
            SmartspaceAppWidgetProvider.componentName to R.layout.smartspace_widget,
        )

        @JvmStatic
        fun inflateCustomView(context: Context, info: AppWidgetProviderInfo, previewMode: Boolean): ViewGroup? {
            val layoutId = customLayouts[info.provider] ?: return null

            // For the smartspace widget, we must use the launcher's themed context so that
            // theme attributes like workspaceTextColor resolve correctly. Without this,
            // CardPagerAdapter.currentTextColor resolves to 0 (transparent) and text is invisible.
            // For preview mode, use the widget preview context as before.
            val inflationContext = when {
                previewMode -> Themes.createWidgetPreviewContext(context)
                info.provider == SmartspaceAppWidgetProvider.componentName -> {
                    // Use the launcher activity itself as context — it has the full workspace
                    // theme applied, so workspaceTextColor resolves correctly in CardPagerAdapter.
                    LawnchairLauncher.instance?.launcherNullable ?: context
                }
                else -> context
            }

            return LayoutInflater.from(inflationContext)
                .inflate(layoutId, null, false) as ViewGroup
        }
    }
}
