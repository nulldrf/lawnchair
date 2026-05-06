package app.lawnchair.theme.color

import android.graphics.Color
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceEntry
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.R
import com.android.launcher3.Utilities

sealed class ColorOption {

    abstract val isSupported: Boolean
    abstract val colorPreferenceEntry: ColorPreferenceEntry<ColorOption>

    object SystemAccent : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.system) },
            { context -> context.getSystemAccent(false) },
            { context -> context.getSystemAccent(true) },
        )

        override fun toString() = "system_accent"
    }

    object WallpaperPrimary : ColorOption() {
        override val isSupported = Utilities.ATLEAST_O_MR1

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.wallpaper) },
            { context ->
                val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
                val primaryColor = wallpaperManager.wallpaperColors?.primaryColor
                primaryColor ?: LawnchairBlue.color
            },
        )

        override fun toString() = "wallpaper_primary"
    }

    /**
     * A wallpaper-derived colour that stores:
     *  - [color]            the specific swatch the user tapped (used as accent)
     *  - [wallpaperPrimary] the wallpaper's primary colour at selection time,
     *                       used as a fingerprint to detect wallpaper changes.
     *
     * On startup / wallpaper change, if [wallpaperPrimary] differs from the
     * current wallpaper primary, the wallpaper has changed and the accent is
     * reset to the new primary automatically.
     */
    class WallpaperDerived(val color: Int, val wallpaperPrimary: Int = color) : ColorOption() {
        override val isSupported = Utilities.ATLEAST_O_MR1

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.wallpaper) },
            { color },
        )

        constructor(color: Long, wallpaperPrimary: Long = color) :
            this(color.toInt(), wallpaperPrimary.toInt())

        override fun equals(other: Any?) =
            other is WallpaperDerived &&
                other.color == color &&
                other.wallpaperPrimary == wallpaperPrimary

        override fun hashCode() = 31 * color + wallpaperPrimary

        override fun toString() =
            "wallpaper_derived|#${String.format("%08x", color)}|#${String.format("%08x", wallpaperPrimary)}"
    }

    class CustomColor(val color: Int) : ColorOption() {
        override val isSupported = true

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.custom) },
            { color },
        )

        constructor(color: Long) : this(color.toInt())

        override fun equals(other: Any?) = other is CustomColor && other.color == color

        override fun hashCode() = color

        override fun toString() = "custom|#${String.format("%08x", color)}"
    }

    object Default : ColorOption() {
        override val isSupported = false

        override val colorPreferenceEntry = ColorPreferenceEntry<ColorOption>(
            this,
            { stringResource(id = R.string.managed_by_lawnchair) },
            { 0 },
        )

        override fun toString() = "default"
    }

    companion object {
        val LawnchairBlue = CustomColor(0xFF007FFF)

        fun fromString(stringValue: String) = when (stringValue) {
            "system_accent" -> SystemAccent
            "wallpaper_primary" -> WallpaperPrimary
            "default" -> Default
            else -> instantiateCustomColor(stringValue)
        }

        private fun instantiateCustomColor(stringValue: String): ColorOption {
            try {
                if (stringValue.startsWith("wallpaper_derived")) {
                    // Format: "wallpaper_derived|#AARRGGBB" (legacy)
                    //      or "wallpaper_derived|#AARRGGBB|#AARRGGBB" (chosen|primary)
                    val parts = stringValue.removePrefix("wallpaper_derived|").split("|")
                    val chosen = Color.parseColor(parts[0])
                    val primary = if (parts.size >= 2) Color.parseColor(parts[1]) else chosen
                    return WallpaperDerived(chosen, primary)
                }
                if (stringValue.startsWith("custom")) {
                    val color = Color.parseColor(stringValue.substring(7))
                    return CustomColor(color)
                }
            } catch (_: IllegalArgumentException) {
            }
            return when {
                Utilities.ATLEAST_S -> SystemAccent
                Utilities.ATLEAST_O_MR1 -> WallpaperPrimary
                else -> LawnchairBlue
            }
        }
    }
}
