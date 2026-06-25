package app.lawnchair.theme

import android.content.Context
import app.lawnchair.theme.color.Spec2025CompatColorScheme
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import dev.kdrag0n.monet.theme.ColorScheme

interface ResourceToken<T> {
    fun resolve(context: Context): T = resolve(context, UiColorMode(Themes.getAttrInteger(context, R.attr.uiColorMode)))

    // Every ResourceToken subtype (ColorToken, DrawableToken, and anything added later)
    // funnels through here to get its ColorScheme. SPEC_2025 awareness lives in exactly
    // one place now — previously ColorToken carried its own duplicate copy of this branch
    // under a different method name (resolveColor), while DrawableToken inherited this
    // method unpatched and silently kept resolving against the legacy SPEC_2021 scheme.
    fun resolve(context: Context, uiColorMode: UiColorMode): T {
        val themeProvider = ThemeProvider.INSTANCE.get(context)
        val scheme2025 = themeProvider.colorScheme2025(isDark = uiColorMode.isDarkTheme)
        val scheme = if (scheme2025 != null) {
            Spec2025CompatColorScheme(scheme2025)
        } else {
            themeProvider.colorScheme
        }
        return resolve(context, scheme, uiColorMode)
    }

    fun resolve(context: Context, scheme: ColorScheme, uiColorMode: UiColorMode): T
}