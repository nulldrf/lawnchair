package app.lawnchair.ui.preferences.components.layout

import android.content.Context
import android.util.AttributeSet
import android.widget.EdgeEffect
import androidx.core.widget.NestedScrollView
import app.lawnchair.ui.StretchEdgeEffect
import com.android.launcher3.Utilities

/**
 * A [NestedScrollView] that applies Lawnchair's stretch overscroll effect on pre-API 31 devices.
 * On API 31+ the system handles stretch overscroll natively.
 */
class StretchNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    private val topEffect = StretchEdgeEffect(context, { invalidate() }, { postInvalidateOnAnimation() })
    private val bottomEffect = StretchEdgeEffect(context, { invalidate() }, { postInvalidateOnAnimation() })

    init {
        if (!Utilities.ATLEAST_S) {
            injectEdgeEffects()
        }
    }

    private fun injectEdgeEffects() {
        try {
            // NestedScrollView extends FrameLayout -> ScrollView -> ScrollView has mEdgeGlowTop/Bottom
            val scrollViewClass = android.widget.ScrollView::class.java
            val topField = scrollViewClass.getDeclaredField("mEdgeGlowTop").apply { isAccessible = true }
            val bottomField = scrollViewClass.getDeclaredField("mEdgeGlowBottom").apply { isAccessible = true }
            topField.set(this, topEffect)
            bottomField.set(this, bottomEffect)
        } catch (e: Exception) {
            // Reflection failed — fall back to default glow effect silently
        }
    }
}
