package app.lawnchair.views

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.UiThread
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.preview.LauncherPreviewRenderer
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherWidgetHolder
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LauncherPreviewView(
    context: Context,
    private val idp: InvariantDeviceProfile,
    private val dummySmartspace: Boolean = false,
    private val dummyInsets: Boolean = false, // Note: New Renderer calculates insets internally based on Context
    private val appContext: Context = context.applicationContext,
) : FrameLayout(context) {

    private val onReadyCallbacks = RunnableList()
    private val onDestroyCallbacks = RunnableList()
    private var destroyed = false

    private var rendererView: View? = null

    /**
     * Retained reference to the [LauncherPreviewRenderer] so [destroy] can explicitly
     * remove it from [LauncherModel.mCallbacksList].
     *
     * [LauncherPreviewRenderer] calls [LauncherModel.addCallbacksAndLoad] on itself inside
     * its constructor (the last line before returning). [LauncherModel] is a Dagger singleton
     * that lives for the entire app lifetime; it therefore keeps a strong reference to the
     * renderer indefinitely unless we explicitly call [LauncherModel.removeCallbacksAndLoad].
     *
     * Without this cleanup the destroyed [PreferenceActivity] is reachable via:
     *   LauncherModel.mCallbacksList
     *     → LauncherPreviewRenderer          (retaining 4.6 MB in 26 596 objects)
     *       → BaseContext.mBase (= PreferenceActivity, mDestroyed = true)
     *
     * LeakCanary confirmed this leak: retainedDurationMillis = 235 115 ms (~4 minutes).
     */
    private var renderer: LauncherPreviewRenderer? = null

    private val spinner = CircularProgressIndicator(context).apply {
        val themedContext = ContextThemeWrapper(context, Themes.getActivityThemeRes(context))
        val textColor = Themes.getAttrColor(themedContext, R.attr.workspaceTextColor)
        isIndeterminate = true
        setIndicatorColor(textColor)
        trackCornerRadius = 1000
        alpha = 0f
        animate()
            .alpha(1f)
            .withLayer()
            .setStartDelay(100)
            .setDuration(300)
            .start()
    }

    init {
        addView(spinner, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER })
        loadAsync()
    }

    fun addOnReadyCallback(runnable: Runnable) {
        onReadyCallbacks.add(runnable)
    }

    @UiThread
    fun destroy() {
        destroyed = true

        // Remove the renderer from LauncherModel.mCallbacksList before clearing our reference.
        //
        // The renderer registers itself via model.addCallbacksAndLoad(this) in its constructor.
        // LauncherModel is a singleton — it will keep the renderer (and through it the Activity
        // context used to construct the renderer) alive indefinitely unless we unregister here.
        //
        // This must happen before removeAllViews() so the renderer's root view is still attached
        // and any in-flight bind callbacks dispatched during remove have a valid target.
        renderer?.let { r ->
            LauncherAppState.getInstance(appContext).model.removeCallbacks(r)
            renderer = null
        }

        onDestroyCallbacks.executeAllAndDestroy()
        removeAllViews()
    }

    private fun loadAsync() {
        // The new Renderer requires the LauncherModel to be passed in,
        // and it handles the loading callbacks internally.
        val model = LauncherAppState.getInstance(appContext).model

        // Create the renderer on the Main Thread (it initializes handlers)
        // Workspace.FIRST_SCREEN_ID is typically 0
        val workspaceScreenId = 0
        val themeRes = Themes.getActivityThemeRes(context)

        val newRenderer = LauncherPreviewRenderer(
            context,
            workspaceScreenId,
            null, // Wallpaper colors
            model,
            themeRes,
            idp,
        )
        // Store so destroy() can unregister it from LauncherModel callbacks.
        renderer = newRenderer

        if (dummySmartspace) {
            newRenderer.setWorkspaceSearchContainer(R.layout.smartspace_widget_placeholder)
        }

        // The renderer exposes a CompletableFuture that completes when the model is bound and view is measured
        newRenderer.initialRender.thenAcceptAsync({ view ->
            if (destroyed) return@thenAcceptAsync

            if (view != null) {
                configureAndAttachView(view)
            } else {
                onReadyCallbacks.executeAllAndDestroy()
                Log.e("LauncherPreviewView", "Model loading failed or View is null")
            }
        }, MAIN_EXECUTOR)
    }

    @UiThread
    private fun configureAndAttachView(view: View) {
        updateScale(view)
        view.pivotX = if (layoutDirection == LAYOUT_DIRECTION_RTL) view.measuredWidth.toFloat() else 0f
        view.pivotY = 0f
        view.layoutParams = LayoutParams(view.measuredWidth, view.measuredHeight)
        removeView(spinner)
        rendererView = view
        addView(view)
        onReadyCallbacks.executeAllAndDestroy()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        rendererView?.let { updateScale(it) }
    }

    private fun updateScale(view: View) {
        if (view.measuredWidth == 0 || view.measuredHeight == 0) return

        val scale: Float = min(
            measuredWidth / view.measuredWidth.toFloat(),
            measuredHeight / view.measuredHeight.toFloat(),
        )
        view.scaleX = scale
        view.scaleY = scale
    }
}
