package app.lawnchair.smartspace.provider.weather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

/**
 * Resolves a [WeatherCondition] to an [Icon] suitable for use in [SmartspaceAction].
 *
 * Icon resolution order:
 * 1. First installed Chronus-compatible icon pack
 *    (intent category: "com.dvtonder.chronus.ICON_PACK")
 * 2. null — no icon shown; the temperature text alone is displayed
 *
 * The Breezy Pixel Icon Provider APK is a compatible pack:
 * https://github.com/breezy-weather/pixel-icon-provider/releases
 *
 * Drawables in a Chronus pack are named e.g. "weather_32" (day clear) / "weather_31" (night clear).
 * The mapping here mirrors [WeatherIconPackProviderImpl] from old Lawnchair.
 */
class WeatherIconProvider(private val context: Context) {

    /**
     * Returns an [Icon] for the given condition, or null if no pack is installed.
     * [isDay] selects the day vs night variant where one exists.
     */
    fun getIcon(condition: WeatherCondition, isDay: Boolean): Icon? {
        val packPackageName = findInstalledPack() ?: return null
        return try {
            val res = context.packageManager.getResourcesForApplication(packPackageName)
            val resName = resNameFor(condition, isDay)
            val resId = res.getIdentifier(resName, "drawable", packPackageName)
            if (resId == 0) return null
            val drawable = res.getDrawable(resId, null) ?: return null
            val bitmap = drawable.toBitmap()
            Icon.createWithBitmap(bitmap)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon from pack $packPackageName", e)
            null
        }
    }

    /** Scans installed apps for the first Chronus-compatible icon pack. */
    private fun findInstalledPack(): String? {
        return context.packageManager
            .queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY),
                PackageManager.GET_META_DATA,
            )
            .firstOrNull()
            ?.activityInfo
            ?.packageName
    }

    companion object {
        private const val TAG = "WeatherIconProvider"
        private const val CHRONUS_CATEGORY = "com.dvtonder.chronus.ICON_PACK"
        private const val PREFIX = "weather_"
        private const val NA = "na"

        /**
         * Chronus drawable name table.
         * Each entry is (dayResNum, nightResNum?) — night is null when day/night share the same icon.
         * Source: WeatherIconPackProviderImpl from old Lawnchair + DvTonder sample icon set spec.
         */
        private val MAP: Map<WeatherCondition, Pair<String, String?>> = mapOf(
            WeatherCondition.CLEAR             to ("32" to "31"),
            WeatherCondition.MOSTLY_CLEAR      to ("34" to "33"),
            WeatherCondition.PARTLY_CLOUDY     to ("30" to "29"),
            WeatherCondition.MOSTLY_CLOUDY     to ("28" to "27"),
            WeatherCondition.CLOUDY            to ("26" to null),
            WeatherCondition.OVERCAST          to ("26" to null),
            WeatherCondition.FOG               to ("20" to null),
            WeatherCondition.DRIZZLE           to ("11" to null),
            WeatherCondition.RAIN              to ("12" to null),
            WeatherCondition.FREEZING_RAIN     to ("10" to null),
            WeatherCondition.SLEET             to ("18" to null),
            WeatherCondition.SNOW              to ("16" to null),
            WeatherCondition.FLURRIES          to ("13" to null),
            WeatherCondition.HAIL              to ("17" to null),
            WeatherCondition.THUNDERSTORM      to ("4"  to "45"),
            WeatherCondition.WINDY             to ("24" to null),
            WeatherCondition.NA                to (NA   to null),
        )

        private fun resNameFor(condition: WeatherCondition, isDay: Boolean): String {
            val (day, night) = MAP[condition] ?: (NA to null)
            val num = if (!isDay && night != null) night else day
            return "$PREFIX$num"
        }
    }
}
