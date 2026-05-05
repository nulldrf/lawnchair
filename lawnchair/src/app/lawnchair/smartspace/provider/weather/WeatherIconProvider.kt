package app.lawnchair.smartspace.provider.weather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

class WeatherIconProvider(private val context: Context) {

    /**
     * Resolves a [WeatherCondition] to an [Icon] from the given Chronus icon pack.
     * Returns null if [packPackageName] is blank or the resource isn't found —
     * the smartspace card will show without an icon in that case.
     *
     * [packPackageName] is passed in rather than read from prefs internally to avoid
     * calling firstBlocking() inside a coroutine flow on the IO dispatcher.
     */
    fun getIcon(condition: WeatherCondition, isDay: Boolean, packPackageName: String?): Icon? {
        if (packPackageName.isNullOrBlank()) return null
        return try {
            val res = context.packageManager.getResourcesForApplication(packPackageName)
            val resName = resNameFor(condition, isDay)
            val resId = res.getIdentifier(resName, "drawable", packPackageName)
            if (resId == 0) {
                Log.w(TAG, "Drawable '$resName' not found in pack $packPackageName")
                return null
            }
            val drawable = res.getDrawable(resId, null) ?: return null
            Icon.createWithBitmap(drawable.toBitmap())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon '$condition' from pack $packPackageName", e)
            null
        }
    }

    companion object {
        private const val TAG = "WeatherIconProvider"
        const val CHRONUS_CATEGORY = "com.dvtonder.chronus.ICON_PACK"
        private const val PREFIX = "weather_"
        private const val NA = "na"

        /** Chronus drawable name table: (dayResNum, nightResNum?) */
        private val MAP: Map<WeatherCondition, Pair<String, String?>> = mapOf(
            WeatherCondition.CLEAR         to ("32" to "31"),
            WeatherCondition.MOSTLY_CLEAR  to ("34" to "33"),
            WeatherCondition.PARTLY_CLOUDY to ("30" to "29"),
            WeatherCondition.MOSTLY_CLOUDY to ("28" to "27"),
            WeatherCondition.CLOUDY        to ("26" to null),
            WeatherCondition.OVERCAST      to ("26" to null),
            WeatherCondition.FOG           to ("20" to null),
            WeatherCondition.DRIZZLE       to ("11" to null),
            WeatherCondition.RAIN          to ("12" to null),
            WeatherCondition.FREEZING_RAIN to ("10" to null),
            WeatherCondition.SLEET         to ("18" to null),
            WeatherCondition.SNOW          to ("16" to null),
            WeatherCondition.FLURRIES      to ("13" to null),
            WeatherCondition.HAIL          to ("17" to null),
            WeatherCondition.THUNDERSTORM  to ("4"  to "45"),
            WeatherCondition.WINDY         to ("24" to null),
            WeatherCondition.NA            to (NA   to null),
        )

        fun resNameFor(condition: WeatherCondition, isDay: Boolean): String {
            val (day, night) = MAP[condition] ?: (NA to null)
            val num = if (!isDay && night != null) night else day
            return "$PREFIX$num"
        }

        /** Returns all installed Chronus-compatible icon packs as (packageName, label) pairs. */
        fun getInstalledPacks(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY),
                PackageManager.GET_META_DATA,
            ).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        }
    }
}
