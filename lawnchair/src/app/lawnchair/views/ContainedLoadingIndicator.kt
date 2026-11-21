package app.lawnchair.views

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.annotation.Px
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.util.Themes
import com.google.android.material.R as MaterialR
import com.google.android.material.loadingindicator.LoadingIndicator
import kotlin.math.roundToInt

/**
 * Creates a Material 3 Expressive contained [LoadingIndicator] tinted with
 * Lawnchair's active primaryContainer / onPrimaryContainer tokens.
 */
fun createContainedLoadingIndicator(
    context: Context,
    @Px containerSizePx: Int? = null,
): LoadingIndicator {
    val activityThemeContext = ContextThemeWrapper(context, Themes.getActivityThemeRes(context))
    val containerColor = ColorTokens.LoadingIndicatorContainer.resolveColor(activityThemeContext)
    val indicatorColor = ColorTokens.LoadingIndicatorActive.resolveColor(activityThemeContext)
    val materialContext = ContextThemeWrapper(
        context,
        MaterialR.style.Theme_Material3_DayNight_NoActionBar,
    )

    return LoadingIndicator(materialContext).apply {
        setContainerColor(containerColor)
        setIndicatorColor(indicatorColor)
        if (containerSizePx != null) {
            setContainerWidth(containerSizePx)
            setContainerHeight(containerSizePx)
            setIndicatorSize((containerSizePx * 38f / 48f).roundToInt())
        }
    }
}
