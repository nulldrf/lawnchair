package app.lawnchair.views.overlay

import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * Controls the animation used when opening (launching) an app from the launcher.
 * Backported from old Lawnchair 2 animation system (ch.deletescape.lawnchair.animations).
 *
 * - DEFAULT   : System default clip-reveal from icon
 * - PIE       : Android Pie (9) style — launcher scales up & fades while app scales in from 0.3
 * - REVEAL    : Circular clip-reveal expanding from the tapped icon
 * - SLIDE_UP  : App slides in from the bottom edge of the screen
 * - SCALE_UP  : App scales up from the tapped icon position
 * - BLINK     : Quick triple-flash blink before the app appears
 * - FADE      : Simple cross-fade between launcher and app
 */
enum class AppOpenAnimationType(val value: String, @StringRes val labelRes: Int) {
    DEFAULT("default", R.string.animation_type_default),
    PIE("pie", R.string.animation_type_pie_like),
    REVEAL("reveal", R.string.animation_type_reveal),
    SLIDE_UP("slide_up", R.string.animation_type_slide_up),
    SCALE_UP("scale_up", R.string.animation_type_scale_up),
    BLINK("blink", R.string.animation_type_blink),
    FADE("fade", R.string.animation_type_fade),
    ;

    companion object {
        fun fromValue(value: String): AppOpenAnimationType =
            entries.find { it.value == value } ?: DEFAULT
    }
}
