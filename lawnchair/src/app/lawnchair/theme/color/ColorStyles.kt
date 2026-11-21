package app.lawnchair.theme.color

import androidx.annotation.StringRes
import com.android.launcher3.R
import com.android.systemui.monet.SpecVersion
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
    @StringRes val descriptionResourceId: Int,
) {
    companion object {
        fun fromString(value: String): ColorStyle = when (value) {
            "spritz" -> Spritz
            "vibrant" -> Vibrant
            "expressive" -> Expressive
            "cmf" -> Cmf
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
            Cmf,
            Rainbow,
            FruitSalad,
            Content,
            Monochromatic,
            LegacyKdrag,
        )
    }
}

/**
 * Resolves the effective [SpecVersion] to apply for a given [colorStyle].
 *
 * Spec 2026 only defines the [Cmf] variant; all other styles fall back to the
 * user's previous non-2026 spec ([fallbackSpec] — either [SpecVersion.SPEC_2021]
 * or [SpecVersion.SPEC_2025], defaulting to [SpecVersion.SPEC_2025]).
 */
fun resolveEffectiveSpec(
    colorSpec: SpecVersion,
    fallbackSpec: SpecVersion,
    colorStyle: ColorStyle,
): SpecVersion {
    val safeFallback = if (fallbackSpec == SpecVersion.SPEC_2021) {
        SpecVersion.SPEC_2021
    } else {
        SpecVersion.SPEC_2025
    }
    return when {
        colorStyle is Cmf && colorSpec == SpecVersion.SPEC_2026 -> SpecVersion.SPEC_2026
        colorSpec == SpecVersion.SPEC_2026 -> safeFallback
        else -> colorSpec
    }
}

object Spritz : ColorStyle(
    style = Style.SPRITZ,
    nameResourceId = R.string.color_style_spritz,
    descriptionResourceId = R.string.color_style_spritz_description,
) {
    override fun toString() = "spritz"
}

object TonalSpot : ColorStyle(
    style = Style.TONAL_SPOT,
    nameResourceId = R.string.color_style_tonal_spot,
    descriptionResourceId = R.string.color_style_tonal_spot_description,
) {
    override fun toString() = "tonal_spot"
}

object Vibrant : ColorStyle(
    style = Style.VIBRANT,
    nameResourceId = R.string.color_style_vibrant,
    descriptionResourceId = R.string.color_style_vibrant_description,
) {
    override fun toString() = "vibrant"
}

object Expressive : ColorStyle(
    style = Style.EXPRESSIVE,
    nameResourceId = R.string.color_style_expressive,
    descriptionResourceId = R.string.color_style_expressive_description,
) {
    override fun toString() = "expressive"
}

/**
 * Color, Material, Finish (CMF) — Material 3 Spec 2026 variant supporting dual source colors.
 * Only shown and active when [SpecVersion.SPEC_2026] is selected.
 */
object Cmf : ColorStyle(
    style = Style.CMF,
    nameResourceId = R.string.color_style_cmf,
    descriptionResourceId = R.string.color_style_cmf_description,
) {
    override fun toString() = "cmf"
}

object Rainbow : ColorStyle(
    style = Style.RAINBOW,
    nameResourceId = R.string.color_style_rainbow,
    descriptionResourceId = R.string.color_style_rainbow_description,
) {
    override fun toString() = "rainbow"
}

object FruitSalad : ColorStyle(
    style = Style.FRUIT_SALAD,
    nameResourceId = R.string.color_style_fruit_salad,
    descriptionResourceId = R.string.color_style_fruit_salad_description,
) {
    override fun toString() = "fruit_salad"
}

object Content : ColorStyle(
    style = Style.CONTENT,
    nameResourceId = R.string.color_style_content,
    descriptionResourceId = R.string.color_style_content_description,
) {
    override fun toString() = "content"
}

object Monochromatic : ColorStyle(
    style = Style.MONOCHROMATIC,
    nameResourceId = R.string.color_style_monochromatic,
    descriptionResourceId = R.string.color_style_monochromatic_description,
) {
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
object LegacyKdrag : ColorStyle(
    style = Style.TONAL_SPOT,
    nameResourceId = R.string.color_style_legacy_kdrag,
    descriptionResourceId = R.string.color_style_legacy_kdrag_description,
) {
    override fun toString() = "legacy_kdrag"
}
