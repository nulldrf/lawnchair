package app.lawnchair.widget.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * [ViewPager] subclass that cooperates with Launcher3's [com.android.launcher3.Workspace] paging
 * and long-press detection.
 *
 * On `ACTION_DOWN` the parent is allowed to intercept (so long-press on the stack works). Once a
 * clearly horizontal drag exceeds touch slop, parent interception is disallowed so horizontal
 * swipes cycle widgets in the stack instead of scrolling workspace pages.
 */
class InterceptingWidgetPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f
    private var isHorizontalDrag = false

    var onUserInteraction: (() -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val pageCount = adapter?.count ?: 0
        if (pageCount <= 1) {
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                isHorizontalDrag = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - initialX)
                val dy = abs(ev.y - initialY)
                if (!isHorizontalDrag && dx > touchSlop && dx > dy) {
                    isHorizontalDrag = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onUserInteraction?.invoke()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHorizontalDrag = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return try {
            super.onInterceptTouchEvent(ev)
        } catch (_: IllegalArgumentException) {
            // Pointer index out of range in multi-touch edge cases
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val pageCount = adapter?.count ?: 0
        if (pageCount <= 1) {
            return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isHorizontalDrag) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    onUserInteraction?.invoke()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isHorizontalDrag = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return try {
            super.onTouchEvent(ev)
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
