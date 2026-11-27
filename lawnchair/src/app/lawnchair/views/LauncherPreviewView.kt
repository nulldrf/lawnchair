package app.lawnchair.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.lifecycle.Lifecycle
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.preview.LauncherPreviewRenderer
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.Themes
import kotlin.math.min

@SuppressLint("ViewConstructor")
class LauncherPreviewView(
    context: Context,
    private var idp: InvariantDeviceProfile,
    private val dummySmartspace: Boolean = false,
    private val dummyInsets: Boolean = false, // Note: New Renderer calculates insets internally based on Context
    private val appContext: Context = context.applicationContext,
    private var forcePortrait: Boolean = false,
) : FrameLayout(context) {

    private val onReadyCallbacks = RunnableList()
    private val onDestroyCallbacks = RunnableList()
    private var destroyed = false

    private var rendererView: View? = null

    /**
     * Retained reference to the active [LauncherPreviewRenderer] so [destroy] and [update]
     * can explicitly unregister it from [LauncherModel.mCallbacksList] and release its widget
     * holder.
     *
     * [LauncherPreviewRenderer] calls [LauncherModel.addCallbacksAndLoad] on itself inside
     * its constructor (the last line before returning). [LauncherModel] is a Dagger singleton
     * that lives for the entire app lifetime; it therefore keeps a strong reference to the
     * renderer indefinitely unless we explicitly remove callbacks and destroy it.
     */
    private var renderer: LauncherPreviewRenderer? = null
    private var pendingRenderer: LauncherPreviewRenderer? = null

    private val spinner = createContainedLoadingIndicator(context).apply {
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

    /**
     * Reloads the preview with a new [InvariantDeviceProfile] or orientation while keeping
     * the existing rendered view on screen until the replacement finishes binding.
     */
    @UiThread
    fun update(
        newIdp: InvariantDeviceProfile,
        newForcePortrait: Boolean = forcePortrait,
    ) {
        if (destroyed) return
        idp = newIdp
        forcePortrait = newForcePortrait
        loadAsync()
    }

    /**
     * Reapplies hotseat background styling (visibility, color, alpha, corner radius, insets)
     * directly on the live preview Hotseat without recreating or rebinding the renderer.
     */
    @UiThread
    fun refreshHotseatBackground() {
        if (destroyed) return
        renderer?.hotseat?.updateBackground()
        pendingRenderer?.hotseat?.updateBackground()
    }

    @UiThread
    fun destroy() {
        destroyed = true

        // Remove active and in-flight renderers from LauncherModel.mCallbacksList before
        // clearing references and detaching views.
        disposeRenderer(pendingRenderer)
        pendingRenderer = null
        disposeRenderer(renderer)
        renderer = null
        rendererView = null

        onDestroyCallbacks.executeAllAndDestroy()
        removeAllViews()
    }

    private fun disposeRenderer(target: LauncherPreviewRenderer?) {
        target ?: return
        LauncherAppState.getInstance(appContext).model.removeCallbacks(target)
        if (target.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            target.onViewDestroyed()
        }
    }

    private fun loadAsync() {
        // Cancel any superseded in-flight render so stale callbacks cannot leak or overwrite
        // newer edits during rapid slider changes.
        disposeRenderer(pendingRenderer)
        pendingRenderer = null

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
            forcePortrait,
        )
        pendingRenderer = newRenderer

        if (dummySmartspace) {
            newRenderer.setWorkspaceSearchContainer(R.layout.smartspace_widget_placeholder)
        }

        // Keep attachment queued on MAIN_EXECUTOR so BaseContext's SavedStateRegistry restore
        // (posted in BaseContext.init) runs before any ComposeView inside the preview attaches.
        newRenderer.initialRender.thenAcceptAsync({ view ->
            onRenderCompleted(newRenderer, view)
        }, MAIN_EXECUTOR)
    }

    @UiThread
    private fun onRenderCompleted(completedRenderer: LauncherPreviewRenderer, view: View?) {
        if (destroyed || pendingRenderer !== completedRenderer) {
            disposeRenderer(completedRenderer)
            return
        }
        pendingRenderer = null

        if (view != null) {
            val previousRenderer = renderer
            val previousView = rendererView
            renderer = completedRenderer
            configureAndAttachView(view, previousView)
            disposeRenderer(previousRenderer)
        } else {
            disposeRenderer(completedRenderer)
            onReadyCallbacks.executeAllAndDestroy()
            Log.e("LauncherPreviewView", "Model loading failed or View is null")
        }
    }

    @UiThread
    private fun configureAndAttachView(view: View, previousView: View?) {
        updateScale(view)
        view.pivotX = if (layoutDirection == LAYOUT_DIRECTION_RTL) view.measuredWidth.toFloat() else 0f
        view.pivotY = 0f
        view.layoutParams = LayoutParams(view.measuredWidth, view.measuredHeight)
        if (spinner.parent === this) {
            removeView(spinner)
        }
        rendererView = view
        addView(view)
        if (previousView != null && previousView !== view) {
            removeView(previousView)
        }
        onReadyCallbacks.executeAllAndDestroy()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        rendererView?.let { updateScale(it) }
    }

    private fun updateScale(view: View) {
        if (measuredWidth == 0 || measuredHeight == 0 || view.measuredWidth == 0 || view.measuredHeight == 0) return

        val scale: Float = min(
            measuredWidth / view.measuredWidth.toFloat(),
            measuredHeight / view.measuredHeight.toFloat(),
        )
        view.scaleX = scale
        view.scaleY = scale
    }
}
