package app.lawnchair.widget.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * [ViewPager] subclass that cooperates with Launcher3's [com.android.launcher3.Workspace] paging,
 * long-press detection, and scrollable child widgets.
 *
 * On `ACTION_DOWN` the parent is allowed to intercept (so long-press on the stack works). Once a
 * clearly horizontal drag exceeds touch slop, parent interception is disallowed so horizontal
 * swipes cycle widgets in the stack instead of scrolling workspace pages.
 *
 * Child widgets cannot block horizontal stack swipes via [requestDisallowInterceptTouchEvent] or
 * [canScroll], while vertical scrolling inside scrollable widgets continues to work normally.
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
    var onPagerSizeChanged: (() -> Unit)? = null

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        // Forward upward so Workspace does not steal touches, but do not let child widgets
        // prevent this pager from intercepting horizontal stack swipes.
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        if (!disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(false)
        }
    }

    override fun canScroll(v: View, checkV: Boolean, dx: Int, x: Int, y: Int): Boolean = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && (w != oldw || h != oldh)) {
            onPagerSizeChanged?.invoke()
        }
    }

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

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
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

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> {
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
