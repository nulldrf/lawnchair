/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.lawnchair.animations

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.util.MainThreadInitializedObject
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities

/**
 * Resolves the target activity's splash window background, system bar colors, and display cutout
 * mode from its manifest theme — 1:1 port of Lawnchair 9's `SplashResolver`.
 */
class SplashResolver(private val context: Context) {

    @Suppress("DEPRECATION")
    fun loadSplash(intent: Intent?): SplashData {
        val activityInfo = intent?.resolveActivityInfo(context.packageManager, 0)
        val themedContext: Context = if (activityInfo == null ||
            (activityInfo.packageName == BuildConfig.APPLICATION_ID &&
                activityInfo.name == PreferenceActivity::class.java.name)
        ) {
            context
        } else {
            runCatching {
                val theme = activityInfo.themeResource
                val packageContext = context.createPackageContext(activityInfo.packageName, 0)
                ContextThemeWrapper(packageContext, theme)
            }.getOrDefault(context)
        }

        val layoutInDisplayCutoutMode = if (Utilities.ATLEAST_P) {
            themedContext.getIntAttrSafe(android.R.attr.windowLayoutInDisplayCutoutMode)
        } else {
            0
        }

        val background = themedContext.getDrawableAttrNullable(android.R.attr.windowBackground)
            ?: (
                if (Utilities.ATLEAST_S) {
                    themedContext.getColorAttrNullable(android.R.attr.windowSplashScreenBackground)
                        ?.let(::ColorDrawable)
                } else {
                    null
                }
                )
            ?: themedContext.getColorAttrNullable(android.R.attr.colorBackground)
                ?.let(::ColorDrawable)

        return SplashData(
            background = background,
            statusColor = themedContext.getColorAttrSafe(android.R.attr.statusBarColor),
            navColor = themedContext.getColorAttrSafe(android.R.attr.navigationBarColor),
            layoutInDisplayCutoutMode = layoutInDisplayCutoutMode,
        )
    }

    data class SplashData(
        val background: Drawable?,
        val statusColor: Int,
        val navColor: Int,
        val layoutInDisplayCutoutMode: Int,
    )

    companion object {
        val INSTANCE = MainThreadInitializedObject(::SplashResolver)

        @ColorInt
        private fun Context.getColorAttrSafe(attr: Int): Int {
            return runCatching {
                val ta = obtainStyledAttributes(intArrayOf(attr))
                try {
                    ta.getColor(0, Color.TRANSPARENT)
                } finally {
                    ta.recycle()
                }
            }.getOrDefault(Color.TRANSPARENT)
        }

        @ColorInt
        private fun Context.getColorAttrNullable(attr: Int): Int? {
            return runCatching {
                val ta = obtainStyledAttributes(intArrayOf(attr))
                try {
                    if (ta.hasValue(0)) ta.getColor(0, Color.TRANSPARENT) else null
                } finally {
                    ta.recycle()
                }
            }.getOrNull()
        }

        private fun Context.getDrawableAttrNullable(attr: Int): Drawable? {
            return try {
                val ta = obtainStyledAttributes(intArrayOf(attr))
                try {
                    ta.getDrawable(0)
                } finally {
                    ta.recycle()
                }
            } catch (_: Resources.NotFoundException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }

        private fun Context.getIntAttrSafe(attr: Int): Int {
            return runCatching {
                val ta = obtainStyledAttributes(intArrayOf(attr))
                try {
                    ta.getInt(0, 0)
                } finally {
                    ta.recycle()
                }
            }.getOrDefault(0)
        }
    }
}
