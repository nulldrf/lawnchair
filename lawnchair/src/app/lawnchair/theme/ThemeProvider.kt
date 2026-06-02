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
import com.android.systemui.monet.SpecVersion
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
    private var colorSpec: SpecVersion = preferenceManager2.colorSpec.firstBlocking()

    // Startup sync: if the stored accent is WallpaperDerived but the wallpaper
    // has since changed (e.g. changed while Lawnchair was not running), update
    // the preference immediately so the theme and UI are correct from first frame.
    // We read wallpaperManager.wallpaperColors here — it is populated before
    // ThemeProvider in the Dagger graph, so the value is available synchronously.

    // Cache for Android-system Monet schemes — keyed by (seedColor, Style, SpecVersion).
    private val colorSchemeMap = HashMap<Triple<Int, Style, SpecVersion>, ColorScheme>()

    // Separate cache for the kdrag0n ZCAM engine — keyed by seedColor alone,
    // since LegacyKdrag has no Style variant.
    private val kdragColorSchemeMap = HashMap<Int, ColorScheme>()

    private val listeners = mutableListOf<ColorSchemeChangeListener>()

    // Holds the most-recent wallpaper primary received directly from the system
    // OnColorsChangedListener callback — always fresh, never stale from cache.
    // Read by the colorScheme getter to render the correct colour immediately
    // without waiting for the async preference write to complete.
    @Volatile
    private var freshWallpaperPrimary: Int? = null

    init {
        // Startup sync: if the wallpaper changed while Lawnchair was not running,
        // the live primary will differ from the stored fingerprint.
        // This is now safe because we compare wallpaperPrimary (fingerprint),
        // not color (the user's chosen swatch).
        val storedAccent = accentColor
        if (storedAccent is ColorOption.WallpaperDerived) {
            val currentPrimary = wallpaperManager.wallpaperColors?.primaryColor
            if (currentPrimary != null && currentPrimary != storedAccent.wallpaperPrimary) {
                // Wallpaper changed while closed — reset to new primary.
                // Write synchronously via firstBlocking equivalent: launch and
                // let onEach handle notifyColorSchemeChanged on Main.
                coroutineScope.launch {
                    preferenceManager2.accentColor.set(
                        ColorOption.WallpaperDerived(
                            color = currentPrimary,
                            wallpaperPrimary = currentPrimary,
                        ),
                    )
                }
            }
        }

        if (Utilities.ATLEAST_S) {
            seedSystemColorScheme()
            registerOverlayChangedListener()
        }
        wallpaperManager.addOnChangeListener(object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                when (val current = accentColor) {
                    is ColorOption.WallpaperPrimary -> notifyColorSchemeChanged()
                    is ColorOption.WallpaperDerived -> {
                        // WallpaperManagerCompat.wallpaperColors is updated before
                        // notifyChange() fires, so this value is current.
                        val newPrimary = wallpaperManager.wallpaperColors?.primaryColor
                            ?: return
                        freshWallpaperPrimary = newPrimary
                        notifyColorSchemeChanged()
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
            // Clear the live override — the preference now stores the correct value.
            freshWallpaperPrimary = null
            notifyColorSchemeChanged()
        }
        preferenceManager2.colorStyle.onEach(launchIn = coroutineScope) {
            colorStyle = it
            notifyColorSchemeChanged()
        }
        preferenceManager2.colorSpec.onEach(launchIn = coroutineScope) {
            colorSpec = it
            colorSchemeMap.clear()
            // Re-seed the real system palette after clearing — clear() removed it
            // and getColorScheme(0, ...) would otherwise create a synthetic
            // MonetColorSchemeCompat(0) instead of returning the system colors.
            if (Utilities.ATLEAST_S) seedSystemColorScheme()
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
                            if (newPrimary != null) {
                                // Store the fresh primary so the colorScheme getter
                                // can use it immediately on the next render call.
                                freshWallpaperPrimary = newPrimary
                                // Trigger re-render immediately — no waiting for
                                // the async preference write.
                                notifyColorSchemeChanged()
                                // Persist to preference if wallpaper actually changed.
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
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
        }
    }

    private fun seedSystemColorScheme() {
        // SystemColorScheme reads Android's system_accent/neutral color resources directly.
        // It must be stored under ALL style keys for seed=0 so that systemColorScheme
        // never falls through to MonetColorSchemeCompat(0, ...) regardless of colorStyle.
        val systemScheme = SystemColorScheme(context)
        Style.values().forEach { style ->
            colorSchemeMap[Triple(0, style, SpecVersion.SPEC_2021)] = systemScheme
        }
    }

    private fun registerOverlayChangedListener() {
        val packageFilter = IntentFilter("android.intent.action.OVERLAY_CHANGED")
        packageFilter.addDataScheme("package")
        packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    seedSystemColorScheme()
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
            getColorScheme(wallpaperPrimary ?: ColorOption.LawnchairBlue.color, colorStyle, colorSpec)
        }

        // WallpaperDerived: use stored chosen swatch color normally.
        // If freshWallpaperPrimary (set by the system listener callback which
        // always receives colors as a parameter) differs from the stored fingerprint,
        // the wallpaper changed — use it immediately for rendering.
        is ColorOption.WallpaperDerived -> {
            val fresh = freshWallpaperPrimary
            val seedColor = if (fresh != null && fresh != accentColor.wallpaperPrimary) {
                fresh
            } else {
                accentColor.color
            }
            getColorScheme(seedColor, colorStyle, colorSpec)
        }

        // LegacyKdrag is only meaningful for wallpaper-derived seed colours.
        // When the user has picked a specific custom colour, silently fall back
        // to TonalSpot so the engine choice doesn't accidentally affect
        // manually-picked accents and the Custom page swatch grid.
        is ColorOption.CustomColor -> {
            val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
            getColorScheme(accentColor.color, effectiveStyle, colorSpec)
        }

        else -> getColorScheme(ColorOption.LawnchairBlue.color, colorStyle, colorSpec)
    }

    private val systemColorScheme get() = when {
        // SystemAccent always uses the real system Monet pipeline (SystemColorScheme on S+).
        // SpecVersion is irrelevant here — the system generates its own palette and
        // colorSpec must never override it. Always use SPEC_2021 so the cache key
        // Triple(0, style, SPEC_2021) hits the SystemColorScheme stored at init.
        Utilities.ATLEAST_S -> getColorScheme(0, if (colorStyle is LegacyKdrag) TonalSpot else colorStyle, SpecVersion.SPEC_2021)
        else -> getColorScheme(context.getSystemAccent(darkTheme = false), if (colorStyle is LegacyKdrag) TonalSpot else colorStyle, SpecVersion.SPEC_2021)
    }

    /**
     * Returns a [ColorScheme] for [colorInt] using the requested [colorStyle] and [specVersion].
     *
     * When [colorStyle] is [LegacyKdrag] the kdrag0n ZCAM engine is used and the
     * result is stored in [kdragColorSchemeMap].  For every other style the Android
     * system engine ([MonetColorSchemeCompat]) is used and cached in [colorSchemeMap].
     * [specVersion] is ignored for [LegacyKdrag] (ZCAM has its own algorithm).
     */
    private fun getColorScheme(
        colorInt: Int,
        colorStyle: ColorStyle,
        specVersion: SpecVersion = SpecVersion.SPEC_2021,
    ): ColorScheme {
        return if (colorStyle is LegacyKdrag) {
            kdragColorSchemeMap.getOrPut(colorInt) {
                KdragMonetColorScheme(colorInt)
            }
        } else {
            val key = Triple(colorInt, colorStyle.style, specVersion)
            colorSchemeMap.getOrPut(key) {
                MonetColorSchemeCompat(colorInt, colorStyle.style, specVersion)
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
