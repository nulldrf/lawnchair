package app.lawnchair.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.AndroidColor
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.theme.color.KdragMonetColorScheme
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.TonalSpot
import app.lawnchair.theme.color.MonetColorSchemeCompat
import app.lawnchair.theme.color.SystemColorScheme
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import com.android.systemui.monet.Style
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.conversion.ConversionGraph.convert
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.monet.theme.ColorScheme
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@LauncherAppSingleton
class ThemeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable {
    private val preferenceManager2 = PreferenceManager2.getInstance(context)
    private val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    // Main dispatcher: onEach callbacks and listeners must run on the main thread
    // because ColorSchemeChangeListener implementations call Activity.recreate().
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var accentColor: ColorOption = preferenceManager2.accentColor.firstBlocking()
    private var colorStyle: ColorStyle = preferenceManager2.colorStyle.firstBlocking()

    // Startup sync: if the stored accent is WallpaperDerived but the wallpaper
    // has since changed (e.g. changed while Lawnchair was not running), update
    // the preference immediately so the theme and UI are correct from first frame.
    // We read wallpaperManager.wallpaperColors here — it is populated before
    // ThemeProvider in the Dagger graph, so the value is available synchronously.

    // Cache for Android-system Monet schemes — keyed by (seedColor, Style).
    private val colorSchemeMap = HashMap<Pair<Int, Style>, ColorScheme>()

    // Separate cache for the kdrag0n ZCAM engine — keyed by seedColor alone,
    // since LegacyKdrag has no Style variant.
    private val kdragColorSchemeMap = HashMap<Int, ColorScheme>()

    private val listeners = mutableListOf<ColorSchemeChangeListener>()

    init {
        if (Utilities.ATLEAST_S) {
            colorSchemeMap[Pair(0, Style.TONAL_SPOT)] = SystemColorScheme(context)
            registerOverlayChangedListener()
        }
        wallpaperManager.addOnChangeListener(object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                when (val current = accentColor) {
                    is ColorOption.WallpaperPrimary -> notifyColorSchemeChanged()
                    is ColorOption.WallpaperDerived -> {
                        val newPrimary = wallpaperManager.wallpaperColors?.primaryColor
                            ?: return
                        // Only update if the wallpaper actually changed.
                        // Compare against the stored fingerprint, not the chosen
                        // swatch color, so non-primary swatch picks are preserved.
                        if (newPrimary != current.wallpaperPrimary) {
                            coroutineScope.launch {
                                preferenceManager2.accentColor.set(
                                    ColorOption.WallpaperDerived(
                                        color = newPrimary,
                                        wallpaperPrimary = newPrimary,
                                    ),
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        })
        preferenceManager2.accentColor.onEach(launchIn = coroutineScope) {
            accentColor = it
            notifyColorSchemeChanged()
        }
        preferenceManager2.colorStyle.onEach(launchIn = coroutineScope) {
            colorStyle = it
            notifyColorSchemeChanged()
        }

        // Register a direct system WallpaperManager listener for WallpaperDerived.
        // Unlike WallpaperManagerCompat.OnColorsChangedListener, this callback
        // receives the new WallpaperColors as a parameter — no cache read needed,
        // so the update is always immediate and reliable.
        if (Utilities.ATLEAST_O_MR1) {
            android.app.WallpaperManager.getInstance(context)
                .addOnColorsChangedListener(
                    { colors, which ->
                        val current = accentColor
                        if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0 &&
                            current is ColorOption.WallpaperDerived
                        ) {
                            val newPrimary = colors?.primaryColor?.toArgb()
                            // Only update if the wallpaper fingerprint changed.
                            if (newPrimary != null && newPrimary != current.wallpaperPrimary) {
                                coroutineScope.launch {
                                    preferenceManager2.accentColor.set(
                                        ColorOption.WallpaperDerived(
                                            color = newPrimary,
                                            wallpaperPrimary = newPrimary,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
        }
    }

    private fun registerOverlayChangedListener() {
        val packageFilter = IntentFilter("android.intent.action.OVERLAY_CHANGED")
        packageFilter.addDataScheme("package")
        packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    colorSchemeMap[Pair(0, Style.TONAL_SPOT)] = SystemColorScheme(context)
                    if (accentColor is ColorOption.SystemAccent) {
                        notifyColorSchemeChanged()
                    }
                }
            },
            packageFilter,
            null,
            Handler(Looper.getMainLooper()),
        )
    }

    val colorScheme get() = when (val accentColor = this.accentColor) {
        is ColorOption.SystemAccent -> systemColorScheme

        is ColorOption.WallpaperPrimary -> {
            val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
            getColorScheme(wallpaperPrimary ?: ColorOption.LawnchairBlue.color, colorStyle)
        }

        // WallpaperDerived: use the specific colour the user tapped (stored in
        // accentColor.color). When the wallpaper changes, the onColorsChanged()
        // listener writes a new WallpaperDerived to the preference and onEach
        // fires, updating accentColor here and triggering notifyColorSchemeChanged().
        is ColorOption.WallpaperDerived ->
            getColorScheme(accentColor.color, colorStyle)

        // LegacyKdrag is only meaningful for wallpaper-derived seed colours.
        // When the user has picked a specific custom colour, silently fall back
        // to TonalSpot so the engine choice doesn't accidentally affect
        // manually-picked accents and the Custom page swatch grid.
        is ColorOption.CustomColor -> {
            val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
            getColorScheme(accentColor.color, effectiveStyle)
        }

        else -> getColorScheme(ColorOption.LawnchairBlue.color, colorStyle)
    }

    private val systemColorScheme get() = when {
        Utilities.ATLEAST_S -> getColorScheme(0, colorStyle)
        else -> getColorScheme(context.getSystemAccent(darkTheme = false), colorStyle)
    }

    /**
     * Returns a [ColorScheme] for [colorInt] using the requested [colorStyle].
     *
     * When [colorStyle] is [LegacyKdrag] the kdrag0n ZCAM engine is used and the
     * result is stored in [kdragColorSchemeMap].  For every other style the Android
     * system engine ([MonetColorSchemeCompat]) is used and cached in [colorSchemeMap].
     */
    private fun getColorScheme(
        colorInt: Int,
        colorStyle: ColorStyle,
    ): ColorScheme {
        return if (colorStyle is LegacyKdrag) {
            kdragColorSchemeMap.getOrPut(colorInt) {
                KdragMonetColorScheme(colorInt)
            }
        } else {
            val key = Pair(colorInt, colorStyle.style)
            colorSchemeMap.getOrPut(key) {
                MonetColorSchemeCompat(colorInt, colorStyle.style)
            }
        }
    }

    fun addListener(listener: ColorSchemeChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ColorSchemeChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyColorSchemeChanged() {
        ArrayList(listeners)
            .forEach(ColorSchemeChangeListener::onColorSchemeChanged)
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeProvider)
    }

    sealed interface ColorSchemeChangeListener {
        fun onColorSchemeChanged()
    }
}

fun Color.toAndroidColor(): Int {
    return when (this) {
        is AndroidColor -> color
        is Srgb -> ColorUtils.setAlphaComponent(toRgb8(), 255)
        else -> convert<Srgb>().toAndroidColor()
    }
}
