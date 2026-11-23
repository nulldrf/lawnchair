/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.color.Cmf
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.LegacyKdrag
import app.lawnchair.theme.color.TonalSpot
import app.lawnchair.theme.color.resolveEffectiveSpec
import app.lawnchair.theme.toComposeColorScheme
import app.lawnchair.theme.toComposeColorScheme2025
import app.lawnchair.ui.preferences.components.ThemeChoice
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import com.android.systemui.monet.SpecVersion

@Composable
fun LawnchairTheme(
    darkTheme: Boolean = isSelectedThemeDark,
    content: @Composable () -> Unit,
) {
    val colorScheme = getColorScheme(darkTheme = darkTheme)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
        shapes = Shapes,
        motionScheme = MotionScheme.expressive(),
    )
}

@Composable
fun ComponentActivity.EdgeToEdge() {
    val darkTheme = isSelectedThemeDark
    val scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f).toArgb()
    val contentColor = MaterialTheme.colorScheme.onSurface.toArgb()

    LaunchedEffect(darkTheme, scrimColor, contentColor) {
        val statusBarStyle = SystemBarStyle.auto(
            Color.TRANSPARENT,
            Color.TRANSPARENT,
            detectDarkMode = { darkTheme },
        )
        val navigationBarStyle = if (!darkTheme) {
            SystemBarStyle.light(scrimColor, contentColor)
        } else {
            SystemBarStyle.dark(scrimColor)
        }
        enableEdgeToEdge(
            statusBarStyle = statusBarStyle,
            navigationBarStyle = navigationBarStyle,
        )
    }
}

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
    if (LocalInspectionMode.current) return getPreviewColorScheme(darkTheme)

    val context = LocalContext.current
    val themeProvider = remember(context) { ThemeProvider.INSTANCE.get(context) }
    val preferenceManager2 = preferenceManager2()
    val accentColor by preferenceManager2.accentColor.asState()
    val colorStyle by preferenceManager2.colorStyle.asState()
    val colorSpec by preferenceManager2.colorSpec.asState()
    val colorSpecFallback by preferenceManager2.colorSpecFallback.asState()

    // Track external palette updates (wallpaper color changes, system overlay changes) so
    // an open Settings screen refreshes live without having to close and re-enter it.
    var themeVersion by remember(themeProvider) { mutableIntStateOf(0) }
    DisposableEffect(themeProvider) {
        val listener = object : ThemeProvider.ColorSchemeChangeListener {
            override fun onColorSchemeChanged() {
                themeVersion++
            }
        }
        themeProvider.addListener(listener)
        onDispose { themeProvider.removeListener(listener) }
    }

    val isSystemAccent = accentColor == ColorOption.SystemAccent
    val effectiveStyle = if (colorSpec != SpecVersion.SPEC_2026 && colorStyle is Cmf) {
        TonalSpot
    } else {
        colorStyle
    }
    val effectiveSpec = resolveEffectiveSpec(colorSpec, colorSpecFallback, effectiveStyle)

    // Styles with no SPEC_2025/2026 implementation. DynamicScheme's own
    // maybeFallbackSpecVersion forces these to SPEC_2021 tone logic internally,
    // but routing them through the materialkolor HCT engine would still produce
    // colors that don't match the rest of the SPEC_2021 UI (different engine,
    // different rounding). They must use the legacy CAM16 ColorScheme.kt engine
    // instead, exactly as if the user had picked SPEC_2021 themselves.
    val styleHasNo2025Variant = effectiveStyle.style in setOf(
        com.android.systemui.monet.Style.RAINBOW,
        com.android.systemui.monet.Style.FRUIT_SALAD,
        com.android.systemui.monet.Style.CONTENT,
        com.android.systemui.monet.Style.MONOCHROMATIC,
    )

    // Routing:
    //   1. Effective spec is SPEC_2021                    → legacy engine
    //   2. Effective spec is SPEC_2025 + 2025-capable     → materialkolor SPEC_2025
    //   3. Effective spec is SPEC_2026 + CMF              → materialkolor SPEC_2026 (SchemeCmf)
    //   4. SystemAccent, LegacyKdrag, or non-2025 style   → legacy / ZCAM / system engine
    val useMaterialKolor =
        (effectiveSpec == SpecVersion.SPEC_2025 || effectiveSpec == SpecVersion.SPEC_2026) &&
            !isSystemAccent &&
            !styleHasNo2025Variant &&
            effectiveStyle !is LegacyKdrag

    if (useMaterialKolor) {
        val scheme2025 = remember(
            accentColor,
            effectiveStyle,
            colorSpec,
            colorSpecFallback,
            darkTheme,
            themeVersion,
        ) {
            themeProvider.colorScheme2025(
                isDark = darkTheme,
                accentColor = accentColor,
                colorStyle = effectiveStyle,
                colorSpec = colorSpec,
                colorSpecFallback = colorSpecFallback,
            )
        }
        if (scheme2025 != null) {
            return scheme2025.toComposeColorScheme2025(isDark = darkTheme)
        }
    }

    // Legacy path: SPEC_2021, SystemAccent, LegacyKdrag, or a style with no
    // SPEC_2025/2026 implementation. Uses the kdrag0n ColorScheme engine.
    val colorScheme = remember(
        accentColor,
        effectiveStyle,
        if (isSystemAccent) null else effectiveSpec,
        themeVersion,
    ) {
        themeProvider.colorSchemeFor(
            accentColor = accentColor,
            colorStyle = effectiveStyle,
            colorSpec = colorSpec,
            colorSpecFallback = colorSpecFallback,
        )
    }
    return colorScheme.toComposeColorScheme(isDark = darkTheme)
}

private fun getPreviewColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme()
} else {
    expressiveLightColorScheme()
}

val isSelectedThemeDark: Boolean
    @Composable get() {
        if (LocalInspectionMode.current) return isAutoThemeDark

        val themeChoice by preferenceManager2().launcherTheme.asState()
        return when (themeChoice) {
            ThemeChoice.LIGHT -> false
            ThemeChoice.DARK -> true
            else -> isAutoThemeDark
        }
    }

val isAutoThemeDark: Boolean
    @Composable get() =
        if (LocalInspectionMode.current || Utilities.ATLEAST_P) {
            isSystemInDarkTheme()
        } else {
            wallpaperSupportsDarkTheme
        }

val wallpaperSupportsDarkTheme: Boolean
    @Composable get() {
        val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(LocalContext.current)
        var supportsDarkTheme by remember { mutableStateOf(wallpaperManager.supportsDarkTheme) }

        DisposableEffect(wallpaperManager) {
            val listener = object : WallpaperManagerCompat.OnColorsChangedListener {
                override fun onColorsChanged() {
                    supportsDarkTheme = wallpaperManager.supportsDarkTheme
                }
            }
            wallpaperManager.addOnChangeListener(listener)
            onDispose { wallpaperManager.removeOnChangeListener(listener) }
        }
        return supportsDarkTheme
    }
