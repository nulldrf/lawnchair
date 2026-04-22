package app.lawnchair.theme.color

import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.systemui.monet.Style

/**
 * Represents a Monet color-generation style.
 *
 * [style] is the Android-system [Style] enum used by [MonetColorSchemeCompat].
 * For [LegacyKdrag] this field is a no-op placeholder; [ThemeProvider] detects
 * the subtype and routes to [KdragMonetColorScheme] instead.
 */
sealed class ColorStyle(
    val style: Style,
    @StringRes val nameResourceId: Int,
) {
    companion object {
        fun fromString(value: String): ColorStyle = when (value) {
            "spritz" -> Spritz
            "vibrant" -> Vibrant
            "expressive" -> Expressive
            "rainbow" -> Rainbow
            "fruit_salad" -> FruitSalad
            "content" -> Content
            "monochromatic" -> Monochromatic
            "legacy_kdrag" -> LegacyKdrag
            else -> TonalSpot // TonalSpot is the default scheme
        }

        /**
         * @return The list of all color styles modes.
         */
        fun values() = listOf(
            Spritz,
            TonalSpot,
            Vibrant,
            Expressive,
            Rainbow,
            FruitSalad,
            Content,
            Monochromatic,
            LegacyKdrag,
        )
    }
}

object Spritz : ColorStyle(Style.SPRITZ, R.string.color_style_spritz) {
    override fun toString() = "spritz"
}
object TonalSpot : ColorStyle(Style.TONAL_SPOT, R.string.color_style_tonal_spot) {
    override fun toString() = "tonal_spot"
}
object Vibrant : ColorStyle(Style.VIBRANT, R.string.color_style_vibrant) {
    override fun toString() = "vibrant"
}
object Expressive : ColorStyle(Style.EXPRESSIVE, R.string.color_style_expressive) {
    override fun toString() = "expressive"
}
object Rainbow : ColorStyle(Style.RAINBOW, R.string.color_style_rainbow) {
    override fun toString() = "rainbow"
}
object FruitSalad : ColorStyle(Style.FRUIT_SALAD, R.string.color_style_fruit_salad) {
    override fun toString() = "fruit_salad"
}
object Content : ColorStyle(Style.CONTENT, R.string.color_style_content) {
    override fun toString() = "content"
}
object Monochromatic : ColorStyle(Style.MONOCHROMATIC, R.string.color_style_monochromatic) {
    override fun toString() = "monochromatic"
}

/**
 * Uses the kdrag0n ZCAM-based Monet engine ([KdragMonetColorScheme]) instead of the
 * Android system engine.  Only meaningful when the accent source is a wallpaper color
 * (i.e. [ColorOption.WallpaperPrimary]); the UI hides it for other accent sources.
 *
 * [Style.TONAL_SPOT] is a harmless placeholder — [ThemeProvider] never reads [style]
 * when this subtype is active.
 */
object LegacyKdrag : ColorStyle(Style.TONAL_SPOT, R.string.color_style_legacy_kdrag) {
    override fun toString() = "legacy_kdrag"
}
