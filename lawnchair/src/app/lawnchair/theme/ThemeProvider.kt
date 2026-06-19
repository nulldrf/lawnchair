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
import app.lawnchair.theme.color.MonetColorSchemeCompat2025
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
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var accentColor: ColorOption = preferenceManager2.accentColor.firstBlocking()
    private var colorStyle: ColorStyle = preferenceManager2.colorStyle.firstBlocking()
    private var colorSpec: SpecVersion = preferenceManager2.colorSpec.firstBlocking()

    // Cache for SPEC_2021 / LegacyKdrag — keyed by (seedColor, Style, SpecVersion).
    // Value type is the kdrag0n ColorScheme abstract class.
    private val colorSchemeMap = java.util.concurrent.ConcurrentHashMap<Triple<Int, Style, SpecVersion>, ColorScheme>()

    // Dedicated cache for the system accent palette. Replaced atomically on overlay change
    // so stale colors are never served after the user changes the system accent.
    @Volatile
    private var cachedSystemColorScheme: ColorScheme? = null

    // Cache for the kdrag0n ZCAM engine — keyed by seedColor alone.
    private val kdragColorSchemeMap = java.util.concurrent.ConcurrentHashMap<Int, ColorScheme>()

    // Cache for SPEC_2025 — keyed by (seedColor, Style, isDark).
    // isDark is part of the key because DynamicScheme bakes dark/light into palette
    // generation — surface tones and chroma multipliers differ per mode.
    // Typed as MonetColorSchemeCompat2025 directly; no kdrag0n type involved.
    private val colorSchemeMap2025 = java.util.concurrent.ConcurrentHashMap<Triple<Int, Style, Boolean>, MonetColorSchemeCompat2025>()

    private val listeners = mutableListOf<ColorSchemeChangeListener>()

    @Volatile
    private var freshWallpaperPrimary: Int? = null

    init {
        val storedAccent = accentColor
        if (storedAccent is ColorOption.WallpaperDerived) {
            val currentPrimary = wallpaperManager.wallpaperColors?.primaryColor
            if (currentPrimary != null && currentPrimary != storedAccent.wallpaperPrimary) {
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
            freshWallpaperPrimary = null
            notifyColorSchemeChanged()
        }
        preferenceManager2.colorStyle.onEach(launchIn = coroutineScope) {
            colorStyle = it
            colorSchemeMap.clear()
            colorSchemeMap2025.clear()
            if (Utilities.ATLEAST_S) seedSystemColorScheme()
            notifyColorSchemeChanged()
        }
        preferenceManager2.colorSpec.onEach(launchIn = coroutineScope) {
            colorSpec = it
            colorSchemeMap.clear()
            colorSchemeMap2025.clear()
            if (Utilities.ATLEAST_S) seedSystemColorScheme()
            notifyColorSchemeChanged()
        }

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
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
        }
    }

    private fun seedSystemColorScheme() {
        cachedSystemColorScheme = SystemColorScheme(context)
    }

    /**
     * Called from [LawnchairLauncher.onResume] to handle OEM devices (e.g. Samsung)
     * that don't broadcast [android.intent.action.OVERLAY_CHANGED] when the system
     * accent changes. Constructs a fresh [SystemColorScheme], compares a key color
     * against the cached one, and fires [notifyColorSchemeChanged] only if the
     * palette actually changed — avoiding unnecessary recreates on every resume.
     */
    fun reseedSystemAccentIfChanged() {
        if (!Utilities.ATLEAST_S) return
        val fresh = SystemColorScheme(context)
        val oldAccent = cachedSystemColorScheme?.accent1?.get(500)
        val newAccent = fresh.accent1[600]
        cachedSystemColorScheme = fresh
        if (oldAccent == null || oldAccent.toAndroidColor() != newAccent?.toAndroidColor()) {
            if (accentColor is ColorOption.SystemAccent) {
                notifyColorSchemeChanged()
            }
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

    /**
     * Returns the SPEC_2021 / LegacyKdrag [ColorScheme] (kdrag0n type).
     *
     * Used by [Theme.kt] when colorSpec is SPEC_2021, or when accentColor is
     * SystemAccent (which always uses the system palette regardless of spec).
     */
    val colorScheme: ColorScheme
        get() = resolveColorScheme(accentColor)

    /**
     * Returns the SPEC_2025 [MonetColorSchemeCompat2025] (plain type, no kdrag0n).
     *
     * Returns null when accentColor is SystemAccent — the system palette is
     * always rendered via the legacy path. [Theme.kt] checks for null and falls
     * back to [colorScheme] in that case.
     *
     * [isDark] is required because [DynamicScheme] bakes dark/light into palette
     * generation — surface tones and chroma multipliers differ per mode.
     */
    fun colorScheme2025(isDark: Boolean): MonetColorSchemeCompat2025? =
        resolveColorScheme2025(accentColor, isDark)

    private fun resolveColorScheme(accentColor: ColorOption): ColorScheme =
        when (accentColor) {
            is ColorOption.SystemAccent -> systemColorScheme

            is ColorOption.WallpaperPrimary -> {
                val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
                getLegacyColorScheme(
                    wallpaperPrimary ?: ColorOption.LawnchairBlue.color,
                    colorStyle,
                    colorSpec,
                )
            }

            is ColorOption.WallpaperDerived -> {
                val fresh = freshWallpaperPrimary
                val seed = if (fresh != null && fresh != accentColor.wallpaperPrimary) fresh
                           else accentColor.color
                getLegacyColorScheme(seed, colorStyle, colorSpec)
            }

            is ColorOption.CustomColor -> {
                val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
                getLegacyColorScheme(accentColor.color, effectiveStyle, colorSpec)
            }

            else -> getLegacyColorScheme(ColorOption.LawnchairBlue.color, colorStyle, colorSpec)
        }

    private fun resolveColorScheme2025(accentColor: ColorOption, isDark: Boolean): MonetColorSchemeCompat2025? {
        // These Style values have no SPEC_2025 implementation — DynamicScheme's own
        // maybeFallbackSpecVersion forces them to SPEC_2021 tone logic internally,
        // but they'd still run through the materialkolor HCT engine instead of the
        // original CAM16 ColorScheme.kt engine, producing colors that don't match
        // the rest of the SPEC_2021 UI. Returning null here routes them through
        // resolveColorScheme (the legacy kdrag0n path) instead, exactly like
        // SystemAccent and LegacyKdrag already do.
        val styleHasNo2025Variant = when (colorStyle.style) {
            Style.RAINBOW, Style.FRUIT_SALAD, Style.CONTENT, Style.MONOCHROMATIC -> true
            else -> false
        }
        if (styleHasNo2025Variant) return null

        return when (accentColor) {
            // SystemAccent always uses the system palette — no 2025 override.
            is ColorOption.SystemAccent -> null

            is ColorOption.WallpaperPrimary -> {
                val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
                get2025ColorScheme(
                    wallpaperPrimary ?: ColorOption.LawnchairBlue.color,
                    colorStyle,
                    isDark,
                )
            }

            is ColorOption.WallpaperDerived -> {
                val fresh = freshWallpaperPrimary
                val seed = if (fresh != null && fresh != accentColor.wallpaperPrimary) fresh
                           else accentColor.color
                get2025ColorScheme(seed, colorStyle, isDark)
            }

            is ColorOption.CustomColor -> {
                // LegacyKdrag has no 2025 variant — fall back to TonalSpot.
                val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
                get2025ColorScheme(accentColor.color, effectiveStyle, isDark)
            }

            else -> get2025ColorScheme(ColorOption.LawnchairBlue.color, colorStyle, isDark)
        }
    }

    private val systemColorScheme: ColorScheme
        get() = if (Utilities.ATLEAST_S) {
            // Return the pre-seeded instance. If somehow called before init completes,
            // fall back to constructing a fresh one and caching it.
            cachedSystemColorScheme ?: SystemColorScheme(context).also {
                cachedSystemColorScheme = it
            }
        } else {
            val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
            getLegacyColorScheme(
                context.getSystemAccent(darkTheme = false),
                effectiveStyle,
                SpecVersion.SPEC_2021,
            )
        }

    /** Returns a cached kdrag0n [ColorScheme] for the SPEC_2021 / LegacyKdrag path. */
    private fun getLegacyColorScheme(
        colorInt: Int,
        colorStyle: ColorStyle,
        specVersion: SpecVersion = SpecVersion.SPEC_2021,
    ): ColorScheme = if (colorStyle is LegacyKdrag) {
        kdragColorSchemeMap.getOrPut(colorInt) {
            KdragMonetColorScheme(colorInt)
        }
    } else {
        val key = Triple(colorInt, colorStyle.style, specVersion)
        colorSchemeMap.getOrPut(key) {
            MonetColorSchemeCompat(colorInt, colorStyle.style, specVersion)
        }
    }

    /** Returns a cached [MonetColorSchemeCompat2025] for the SPEC_2025 path. */
    private fun get2025ColorScheme(
        colorInt: Int,
        colorStyle: ColorStyle,
        isDark: Boolean,
    ): MonetColorSchemeCompat2025 {
        // LegacyKdrag has no 2025 variant; treat as TonalSpot.
        val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
        return colorSchemeMap2025.getOrPut(Triple(colorInt, effectiveStyle.style, isDark)) {
            MonetColorSchemeCompat2025(colorInt, effectiveStyle.style, isDark)
        }
    }

    fun addListener(listener: ColorSchemeChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ColorSchemeChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyColorSchemeChanged() {
        ArrayList(listeners).forEach(ColorSchemeChangeListener::onColorSchemeChanged)
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
