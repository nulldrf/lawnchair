package app.lawnchair.views.overlay

import androidx.annotation.StringRes
import com.android.launcher3.R

/**
 * Controls the animation used when opening (launching) an app from the launcher.
 * 1:1 port of Lawnchair 9's animation types (`ch.deletescape.lawnchair.animations.AnimationType`).
 *
 * - DEFAULT   : System / Quickstep default launch animation
 * - PIE       : Emulated Android Pie (9) icon-to-splash launch and slide-down return
 * - REVEAL    : System clip-reveal expanding from the tapped icon
 * - SLIDE_UP  : App slides up from the bottom edge (`task_open_enter` / `no_anim`)
 * - SCALE_UP  : System scale-up from the tapped icon bounds
 * - BLINK     : Horizontal/vertical stretch-blink (`blink_open_*` / `blink_close_*`)
 * - FADE      : Short cross-fade (`fade_in_short` / `fade_out_short`)
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
