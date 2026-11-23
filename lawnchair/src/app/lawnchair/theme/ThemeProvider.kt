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
import app.lawnchair.preferences2.firstCached
import app.lawnchair.preferences2.setCached
import app.lawnchair.theme.color.AndroidColor
import app.lawnchair.theme.color.Cmf
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import app.lawnchair.theme.color.KdragMonetColorScheme
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.MonetColorSchemeCompat
import app.lawnchair.theme.color.MonetColorSchemeCompat2025
import app.lawnchair.theme.color.SystemColorScheme
import app.lawnchair.theme.color.TonalSpot
import app.lawnchair.theme.color.resolveEffectiveSpec
import app.lawnchair.ui.theme.getSystemAccent
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.SafeCloseable
import com.android.systemui.monet.SpecVersion
import com.android.systemui.monet.Style
import com.materialkolor.dynamiccolor.ColorSpec
import dev.kdrag0n.colorkt.Color
import dev.kdrag0n.colorkt.conversion.ConversionGraph.convert
import dev.kdrag0n.colorkt.rgb.Srgb
import dev.kdrag0n.monet.theme.ColorScheme
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@LauncherAppSingleton
class ThemeProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    tracker: DaggerSingletonTracker,
) : SafeCloseable {
    private val preferenceManager2 = PreferenceManager2.getInstance(context)
    private val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(context)
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Read directly from CachedPreferencesDataStore's lock-free RAM snapshot so callers
    // (including Compose recompositions triggered synchronously on write) never observe a
    // stale value waiting for an async collector turn.
    private val accentColor: ColorOption
        get() = preferenceManager2.accentColor.firstCached()

    private val colorStyle: ColorStyle
        get() = preferenceManager2.colorStyle.firstCached()

    private val colorSpec: SpecVersion
        get() = preferenceManager2.colorSpec.firstCached()

    private val colorSpecFallback: SpecVersion
        get() = preferenceManager2.colorSpecFallback.firstCached()

    // Cache for SPEC_2021 / LegacyKdrag — keyed by (seedColor, Style, SpecVersion).
    // Value type is the kdrag0n ColorScheme abstract class.
    private val colorSchemeMap = ConcurrentHashMap<Triple<Int, Style, SpecVersion>, ColorScheme>()

    // Dedicated cache for the system accent palette. Replaced atomically on overlay change
    // so stale colors are never served after the user changes the system accent.
    @Volatile
    private var cachedSystemColorScheme: ColorScheme? = null

    // Cache for the kdrag0n ZCAM engine — keyed by seedColor alone.
    private val kdragColorSchemeMap = ConcurrentHashMap<Int, ColorScheme>()

    private data class Scheme2025Key(
        val seedColor: Int,
        val secondarySeedColor: Int?,
        val style: Style,
        val specVersion: ColorSpec.SpecVersion,
        val isDark: Boolean,
    )

    // Cache for SPEC_2025 / SPEC_2026 — keyed by (seedColor, secondarySeedColor, Style,
    // specVersion, isDark). isDark is part of the key because DynamicScheme bakes dark/light
    // into palette generation — surface tones and chroma multipliers differ per mode.
    // Typed as MonetColorSchemeCompat2025 directly; no kdrag0n type involved.
    private val colorSchemeMap2025 = ConcurrentHashMap<Scheme2025Key, MonetColorSchemeCompat2025>()

    private val listeners = CopyOnWriteArrayList<ColorSchemeChangeListener>()
    private var overlayChangedReceiver: BroadcastReceiver? = null
    private var systemWallpaperColorsListener: android.app.WallpaperManager.OnColorsChangedListener? = null
    private val wallpaperColorsChangedListener =
        object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                when (val current = accentColor) {
                    is ColorOption.WallpaperPrimary -> {
                        colorSchemeMap2025.clear()
                        notifyColorSchemeChanged()
                    }

                    is ColorOption.WallpaperDerived -> {
                        val newPrimary = wallpaperManager.wallpaperColors?.primaryColor
                            ?: return
                        // Clear 2025/2026 cache so the new seed is always computed fresh —
                        // avoids stale MonetColorSchemeCompat2025 being served if the
                        // cache was hit before freshWallpaperPrimary was read.
                        colorSchemeMap2025.clear()
                        freshWallpaperPrimary = newPrimary
                        freshWallpaperSecondary = wallpaperManager.wallpaperColors?.secondaryColor
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
        }

    @Volatile
    private var isClosed = false

    @Volatile
    private var freshWallpaperPrimary: Int? = null

    @Volatile
    private var freshWallpaperSecondary: Int? = null

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
        wallpaperManager.addOnChangeListener(wallpaperColorsChangedListener)
        preferenceManager2.accentColor.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                freshWallpaperPrimary = null
                freshWallpaperSecondary = null
                notifyColorSchemeChanged()
            }
            .launchIn(coroutineScope)
        preferenceManager2.colorStyle.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                if (Utilities.ATLEAST_S && accentColor is ColorOption.SystemAccent) {
                    seedSystemColorScheme()
                }
                notifyColorSchemeChanged()
            }
            .launchIn(coroutineScope)
        preferenceManager2.colorSpec.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                if (it != SpecVersion.SPEC_2026 && colorStyle is Cmf) {
                    preferenceManager2.colorStyle.setCached(TonalSpot)
                }
                if (Utilities.ATLEAST_S && accentColor is ColorOption.SystemAccent) {
                    seedSystemColorScheme()
                }
                notifyColorSchemeChanged()
            }
            .launchIn(coroutineScope)
        preferenceManager2.colorSpecFallback.get()
            .distinctUntilChanged()
            .drop(1)
            .onEach {
                notifyColorSchemeChanged()
            }
            .launchIn(coroutineScope)

        if (Utilities.ATLEAST_O_MR1) {
            val listener = android.app.WallpaperManager.OnColorsChangedListener { colors, which ->
                val current = accentColor
                if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0 &&
                    current is ColorOption.WallpaperDerived
                ) {
                    val newPrimary = colors?.primaryColor?.toArgb()
                    if (newPrimary != null) {
                        colorSchemeMap2025.clear()
                        freshWallpaperPrimary = newPrimary
                        freshWallpaperSecondary = colors.secondaryColor?.toArgb()
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
            }
            android.app.WallpaperManager.getInstance(context)
                .addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            systemWallpaperColorsListener = listener
        }
        tracker.addCloseable(this)
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
        val newAccent = fresh.accent1[500]
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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                seedSystemColorScheme()
                if (accentColor is ColorOption.SystemAccent) {
                    notifyColorSchemeChanged()
                }
            }
        }
        context.registerReceiver(receiver, packageFilter, null, Handler(Looper.getMainLooper()))
        overlayChangedReceiver = receiver
    }

    /**
     * Returns the SPEC_2021 / LegacyKdrag [ColorScheme] (kdrag0n type).
     *
     * Used by [Theme.kt] when colorSpec is SPEC_2021, or when accentColor is
     * SystemAccent (which always uses the system palette regardless of spec).
     */
    val colorScheme: ColorScheme
        get() = resolveColorScheme(accentColor, colorStyle, colorSpec, colorSpecFallback)

    fun colorSchemeFor(
        accentColor: ColorOption = this.accentColor,
        colorStyle: ColorStyle = this.colorStyle,
        colorSpec: SpecVersion = this.colorSpec,
        colorSpecFallback: SpecVersion = this.colorSpecFallback,
    ): ColorScheme = resolveColorScheme(accentColor, colorStyle, colorSpec, colorSpecFallback)

    /** Public accessor so Java callers (e.g. AccentColorExtractor) can gate on spec. */
    val activeColorSpec: SpecVersion get() = colorSpec

    /**
     * Returns the SPEC_2025 / SPEC_2026 [MonetColorSchemeCompat2025] (plain type, no kdrag0n).
     *
     * Returns null when:
     * - [accentColor] is [ColorOption.SystemAccent] (always uses the system palette),
     * - [colorStyle] is [LegacyKdrag] (always uses ZCAM),
     * - the effective spec resolves to [SpecVersion.SPEC_2021], or
     * - [colorStyle] has no 2025/2026 implementation.
     *
     * Callers fall back to [colorScheme] when null is returned.
     *
     * [isDark] is required because [DynamicScheme] bakes dark/light into palette
     * generation — surface tones and chroma multipliers differ per mode.
     */
    @JvmOverloads
    fun colorScheme2025(
        isDark: Boolean,
        accentColor: ColorOption = this.accentColor,
        colorStyle: ColorStyle = this.colorStyle,
        colorSpec: SpecVersion = this.colorSpec,
        colorSpecFallback: SpecVersion = this.colorSpecFallback,
    ): MonetColorSchemeCompat2025? = resolveColorScheme2025(accentColor, colorStyle, colorSpec, colorSpecFallback, isDark)

    private fun effectiveColorStyle(
        style: ColorStyle = colorStyle,
        spec: SpecVersion = colorSpec,
    ): ColorStyle = if (spec != SpecVersion.SPEC_2026 && style is Cmf) TonalSpot else style

    private fun resolveSecondarySeedForCmf(primarySeed: Int, accentColor: ColorOption): Int? = when (accentColor) {
        is ColorOption.WallpaperPrimary -> wallpaperManager.wallpaperColors?.secondaryColor

        is ColorOption.WallpaperDerived -> {
            val liveSecondary = freshWallpaperSecondary ?: wallpaperManager.wallpaperColors?.secondaryColor
            val livePrimary = freshWallpaperPrimary
                ?: wallpaperManager.wallpaperColors?.primaryColor
                ?: accentColor.wallpaperPrimary
            when {
                liveSecondary != null && liveSecondary != primarySeed -> liveSecondary
                livePrimary != primarySeed -> livePrimary
                else -> liveSecondary
            }
        }

        else -> null
    }

    private fun resolveColorScheme(
        accentColor: ColorOption,
        colorStyle: ColorStyle,
        colorSpec: SpecVersion,
        colorSpecFallback: SpecVersion,
    ): ColorScheme {
        val activeStyle = effectiveColorStyle(colorStyle, colorSpec)
        val effectiveSpec = resolveEffectiveSpec(colorSpec, colorSpecFallback, activeStyle)
        return when (accentColor) {
            is ColorOption.SystemAccent -> resolveSystemColorScheme(activeStyle)

            is ColorOption.WallpaperPrimary -> {
                val wallpaperPrimary = wallpaperManager.wallpaperColors?.primaryColor
                getLegacyColorScheme(
                    wallpaperPrimary ?: ColorOption.LawnchairBlue.color,
                    activeStyle,
                    effectiveSpec,
                )
            }

            is ColorOption.WallpaperDerived -> {
                val fresh = freshWallpaperPrimary
                val seed = if (fresh != null && fresh != accentColor.wallpaperPrimary) {
                    fresh
                } else {
                    accentColor.color
                }
                getLegacyColorScheme(seed, activeStyle, effectiveSpec)
            }

            is ColorOption.CustomColor -> {
                val effectiveStyle = if (activeStyle is LegacyKdrag) TonalSpot else activeStyle
                getLegacyColorScheme(accentColor.color, effectiveStyle, effectiveSpec)
            }

            else -> getLegacyColorScheme(ColorOption.LawnchairBlue.color, activeStyle, effectiveSpec)
        }
    }

    private fun resolveColorScheme2025(
        accentColor: ColorOption,
        colorStyle: ColorStyle,
        colorSpec: SpecVersion,
        colorSpecFallback: SpecVersion,
        isDark: Boolean,
    ): MonetColorSchemeCompat2025? {
        val activeStyle = effectiveColorStyle(colorStyle, colorSpec)

        // LegacyKdrag always routes to the ZCAM engine regardless of active spec version.
        // Returning null here lets the caller fall back to resolveColorScheme(), which
        // correctly dispatches to KdragMonetColorScheme. Without this guard, LegacyKdrag
        // would pass the styleHasNo2025Variant check below (because its style placeholder
        // is TONAL_SPOT) and silently produce a SPEC_2025 TonalSpot scheme instead of ZCAM.
        if (activeStyle is LegacyKdrag) return null

        // Respect effective spec (handles SPEC_2021 directly, as well as SPEC_2026 falling
        // back to SPEC_2021 for non-CMF styles when the user came from SPEC_2021).
        val effectiveSpec = resolveEffectiveSpec(colorSpec, colorSpecFallback, activeStyle)
        if (effectiveSpec == SpecVersion.SPEC_2021) return null

        // These ColorStyle subtypes have no SPEC_2025/2026 implementation — DynamicScheme's own
        // maybeFallbackSpecVersion forces them to SPEC_2021 tone logic internally,
        // but they'd still run through the materialkolor HCT engine instead of the
        // original CAM16 ColorScheme.kt engine, producing colors that don't match
        // the rest of the SPEC_2021 UI. Returning null here routes them through
        // resolveColorScheme (the legacy kdrag0n path) instead, exactly like
        // SystemAccent and LegacyKdrag already do.
        // NOTE: intentionally checked against the sealed subtype (not activeStyle.style) so
        // that any future ColorStyle that shares a Style enum value can't silently bypass this.
        val styleHasNo2025Variant = when (activeStyle) {
            is app.lawnchair.theme.color.Rainbow,
            is app.lawnchair.theme.color.FruitSalad,
            is app.lawnchair.theme.color.Content,
            is app.lawnchair.theme.color.Monochromatic,
            -> true

            else -> false
        }
        if (styleHasNo2025Variant) return null

        val mkSpec = if (effectiveSpec == SpecVersion.SPEC_2026) {
            ColorSpec.SpecVersion.SPEC_2026
        } else {
            ColorSpec.SpecVersion.SPEC_2025
        }

        return when (accentColor) {
            // SystemAccent always uses the system palette — no 2025/2026 override.
            is ColorOption.SystemAccent -> null

            is ColorOption.WallpaperPrimary -> {
                val seed = wallpaperManager.wallpaperColors?.primaryColor
                    ?: ColorOption.LawnchairBlue.color
                val secondarySeed = if (activeStyle is Cmf) resolveSecondarySeedForCmf(seed, accentColor) else null
                get2025ColorScheme(seed, activeStyle, isDark, mkSpec, secondarySeed)
            }

            is ColorOption.WallpaperDerived -> {
                val fresh = freshWallpaperPrimary
                val seed = if (fresh != null && fresh != accentColor.wallpaperPrimary) {
                    fresh
                } else {
                    accentColor.color
                }
                val secondarySeed = if (activeStyle is Cmf) resolveSecondarySeedForCmf(seed, accentColor) else null
                get2025ColorScheme(seed, activeStyle, isDark, mkSpec, secondarySeed)
            }

            is ColorOption.CustomColor -> {
                // LegacyKdrag is already returned null above; activeStyle is safe to use directly.
                get2025ColorScheme(accentColor.color, activeStyle, isDark, mkSpec, null)
            }

            else -> get2025ColorScheme(ColorOption.LawnchairBlue.color, activeStyle, isDark, mkSpec, null)
        }
    }

    private fun resolveSystemColorScheme(activeStyle: ColorStyle): ColorScheme = if (Utilities.ATLEAST_S) {
        // Return the pre-seeded instance. If somehow called before init completes,
        // fall back to constructing a fresh one and caching it.
        cachedSystemColorScheme ?: SystemColorScheme(context).also {
            cachedSystemColorScheme = it
        }
    } else {
        val effectiveStyle = if (activeStyle is LegacyKdrag) TonalSpot else activeStyle
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
        if (kdragColorSchemeMap.size > MAX_CACHED_SCHEMES) kdragColorSchemeMap.clear()
        kdragColorSchemeMap.computeIfAbsent(colorInt) {
            KdragMonetColorScheme(colorInt)
        }
    } else {
        if (colorSchemeMap.size > MAX_CACHED_SCHEMES) colorSchemeMap.clear()
        val key = Triple(colorInt, colorStyle.style, specVersion)
        colorSchemeMap.computeIfAbsent(key) {
            MonetColorSchemeCompat(colorInt, colorStyle.style, specVersion)
        }
    }

    /** Returns a cached [MonetColorSchemeCompat2025] for the SPEC_2025 / SPEC_2026 path. */
    private fun get2025ColorScheme(
        colorInt: Int,
        colorStyle: ColorStyle,
        isDark: Boolean,
        specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
        secondaryColorInt: Int? = null,
    ): MonetColorSchemeCompat2025 {
        // LegacyKdrag is intercepted upstream in resolveColorScheme2025() before this
        // function is ever called, so this branch is unreachable in normal operation.
        // Kept as a defensive fallback in case of future call-site changes.
        val effectiveStyle = if (colorStyle is LegacyKdrag) TonalSpot else colorStyle
        if (colorSchemeMap2025.size > MAX_CACHED_SCHEMES) colorSchemeMap2025.clear()
        val key = Scheme2025Key(colorInt, secondaryColorInt, effectiveStyle.style, specVersion, isDark)
        return colorSchemeMap2025.computeIfAbsent(key) {
            MonetColorSchemeCompat2025(colorInt, effectiveStyle.style, isDark, specVersion, secondaryColorInt)
        }
    }

    fun addListener(listener: ColorSchemeChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ColorSchemeChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyColorSchemeChanged() {
        if (isClosed) return
        listeners.forEach(ColorSchemeChangeListener::onColorSchemeChanged)
    }

    override fun close() {
        isClosed = true
        coroutineScope.cancel()
        wallpaperManager.removeOnChangeListener(wallpaperColorsChangedListener)
        if (Utilities.ATLEAST_O_MR1) {
            systemWallpaperColorsListener?.let {
                android.app.WallpaperManager.getInstance(context).removeOnColorsChangedListener(it)
                systemWallpaperColorsListener = null
            }
        }
        overlayChangedReceiver?.let {
            context.unregisterReceiver(it)
            overlayChangedReceiver = null
        }
        listeners.clear()
    }

    companion object {
        private const val MAX_CACHED_SCHEMES = 64

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getThemeProvider)
    }

    interface ColorSchemeChangeListener {
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
