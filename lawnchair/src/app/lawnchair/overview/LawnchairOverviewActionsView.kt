package app.lawnchair.overview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.util.isOnePlusStock
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import com.android.quickstep.views.OverviewActionsView
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class LawnchairOverviewActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : OverviewActionsView<TaskOverlayFactoryImpl.OverlayUICallbacks>(context, attrs, defStyleAttr) {

    private val prefs = PreferenceManager2.getInstance(context)
    private lateinit var container: LinearLayout
    private lateinit var screenshotAction: Button
    private lateinit var shareAction: Button
    private lateinit var lensAction: Button
    private lateinit var clearAllAction: Button
    private lateinit var lockedAction: Button

    override fun onFinishInflate() {
        super.onFinishInflate()

        container = ViewCompat.requireViewById(this, R.id.action_buttons)
        clearAllAction = ViewCompat.requireViewById(this, R.id.action_clear_all)
        shareAction = ViewCompat.requireViewById(this, R.id.action_share)
        lensAction = ViewCompat.requireViewById(this, R.id.action_lens)
        screenshotAction = ViewCompat.requireViewById(this, R.id.action_screenshot)
        lockedAction = ViewCompat.requireViewById(this, R.id.action_locked)

        shareAction.setOnClickListener { mCallbacks?.onShare() }
        lensAction.setOnClickListener { mCallbacks?.onLens() }

        repeatOnAttached {
            merge(
                prefs.recentsActionClearAll.get().drop(1),
                prefs.recentsActionLens.get().drop(1),
                prefs.recentsActionScreenshot.get().drop(1),
                prefs.recentsActionShare.get().drop(1),
                prefs.recentsActionLocked.get().drop(1),
                prefs.recentActionOrder.get().drop(1),
            ).onEach {
                updateVisibilities()
            }.launchIn(this)
        }

        updateVisibilities()
    }

    private fun updateVisibilities() {
        val order = prefs.recentActionOrder.firstCached().split(",").map { it.toInt() }

        val buttonMap = mutableMapOf<Int, View>()
        if (prefs.recentsActionScreenshot.firstCached() && !isOnePlusStock) {
            buttonMap[0] = screenshotAction
        }
        if (prefs.recentsActionShare.firstCached()) {
            buttonMap[1] = shareAction
        }
        if (prefs.recentsActionLens.firstCached() && isLensAvailable()) {
            buttonMap[2] = lensAction
        }
        if (prefs.recentsActionLocked.firstCached()) {
            buttonMap[3] = lockedAction
        }
        if (prefs.recentsActionClearAll.firstCached()) {
            buttonMap[4] = clearAllAction
        }

        val buttonsInOrder = order.mapNotNull { buttonMap[it] }

        container.removeAllViews()
        container.addView(createSpace())
        buttonsInOrder.forEach { view ->
            view.isVisible = true
            container.addView(view)
            container.addView(createSpace())
        }
    }

    private fun isLensAvailable(): Boolean {
        val lensIntent = context.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
        return lensIntent != null
    }

    private fun createSpace(): View {
        return Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1).apply { weight = 1f }
        }
    }

    override fun setClearAllClickListener(clearAllClickListener: OnClickListener?) {
        clearAllAction.setOnClickListener(clearAllClickListener)
    }
}
