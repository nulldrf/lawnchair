package app.lawnchair.gestures

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import app.lawnchair.animation.PhysicsAnimator
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import kotlin.math.abs

/**
 * Handles vertical swipe gestures (up or down) on a cover-mode [FolderIcon] to open the
 * underlying folder while preserving tap-to-launch and horizontal workspace/dock paging.
 */
class FolderCoverGestureListener(
    private val folderIcon: FolderIcon,
) : DirectionalGestureListener(folderIcon.context) {

    private val context get() = folderIcon.context
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDistanceThresholdPx = maxOf(
        touchSlop * 2f,
        SWIPE_DISTANCE_DP * context.resources.displayMetrics.density,
    )
    private val msdlPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)

    private var axisLock = AxisLock.NONE
    private var downX = 0f
    private var downY = 0f
    private var isAnimating = false

    private enum class AxisLock { NONE, HORIZONTAL, VERTICAL }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            axisLock = AxisLock.NONE
            downX = event.x
            downY = event.y
        }

        val flingHandled = super.onTouchEvent(event)
        if (flingHandled) {
            axisLock = AxisLock.NONE
            return true
        }

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP) {
            val diffX = event.x - downX
            val diffY = event.y - downY
            if (axisLock == AxisLock.VERTICAL &&
                abs(diffY) >= swipeDistanceThresholdPx &&
                abs(diffY) > abs(diffX)
            ) {
                axisLock = AxisLock.NONE
                return openCoverFolder(0f)
            }
            axisLock = AxisLock.NONE
        } else if (action == MotionEvent.ACTION_CANCEL) {
            axisLock = AxisLock.NONE
        }

        return false
    }

    override fun onSwipeRight(velocity: Float): Boolean = false
    override fun onSwipeLeft(velocity: Float): Boolean = false
    override fun onSwipeTop(velocity: Float): Boolean = openCoverFolder(velocity)
    override fun onSwipeDown(velocity: Float): Boolean = openCoverFolder(velocity)

    override fun onScroll(diffX: Float, diffY: Float) {
        if (axisLock == AxisLock.NONE) {
            val absX = abs(diffX)
            val absY = abs(diffY)
            if (absX > touchSlop || absY > touchSlop) {
                if (isAnimating || folderIcon.translationY != 0f) {
                    PhysicsAnimator.getInstance(folderIcon).cancel()
                    isAnimating = false
                }
                axisLock = if (absY >= absX) AxisLock.VERTICAL else AxisLock.HORIZONTAL
                if (axisLock == AxisLock.VERTICAL) {
                    folderIcon.cancelLongPress()
                }
            } else {
                return
            }
        }

        if (axisLock == AxisLock.VERTICAL) {
            folderIcon.translationY = diffY * TRANSLATION_FACTOR
        }
    }

    override fun onActionUp(handled: Boolean) {
        if (!handled && folderIcon.translationY != 0f) {
            isAnimating = true
            PhysicsAnimator.getInstance(folderIcon)
                .spring(
                    DynamicAnimation.TRANSLATION_Y,
                    0f,
                    0f,
                    SpringForce.STIFFNESS_HIGH,
                    SpringForce.DAMPING_RATIO_NO_BOUNCY,
                )
                .start()
        }
    }

    private fun openCoverFolder(velocity: Float): Boolean {
        if (!folderIcon.isCoverModeActive) {
            return false
        }

        isAnimating = true
        PhysicsAnimator.getInstance(folderIcon)
            .spring(
                DynamicAnimation.TRANSLATION_Y,
                0f,
                velocity * VELOCITY_FACTOR,
                SpringForce.STIFFNESS_MEDIUM,
                SpringForce.DAMPING_RATIO_NO_BOUNCY,
            )
            .start()

        msdlPlayerWrapper.playToken(MSDLToken.SWIPE_THRESHOLD_INDICATOR)
        ItemClickHandler.openFolder(folderIcon)
        return true
    }

    companion object {
        private const val SWIPE_DISTANCE_DP = 32f
    }
}
