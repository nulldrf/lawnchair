package app.lawnchair.smartspace.provider.weather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

/**
 * Resolves [WeatherCondition] to an [Icon] from an installed icon pack.
 *
 * Supports two pack formats:
 * - **Chronus** (`com.dvtonder.chronus.ICON_PACK`): drawables named `weather_32`, `weather_31`, etc.
 * - **Breezy** (`org.breezyweather.ICON_PROVIDER_ACTION`): drawables named `clear_day`, `partly_cloudy_night`, etc.
 *
 * Compatible packs:
 * - Any Chronus-format pack (MIUI Weather Icons, etc.)
 * - Breezy Weather icon packs: https://github.com/breezy-weather/breezy-weather-icon-packs
 */
class WeatherIconProvider(private val context: Context) {

    fun getIcon(condition: WeatherCondition, isDay: Boolean, packPackageName: String?): Icon? {
        if (packPackageName.isNullOrBlank()) return null
        return try {
            val type = resolvePackType(packPackageName) ?: return null
            val res = context.packageManager.getResourcesForApplication(packPackageName)
            val resName = when (type) {
                PackType.CHRONUS -> chronusResName(condition, isDay)
                PackType.BREEZY -> breezyResName(condition, isDay)
            }
            val resId = res.getIdentifier(resName, "drawable", packPackageName)
            if (resId == 0) {
                Log.w(TAG, "Drawable '$resName' not found in $packPackageName ($type)")
                return null
            }
            val drawable = res.getDrawable(resId, null) ?: return null
            Icon.createWithBitmap(drawable.toBitmap())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon from $packPackageName", e)
            null
        }
    }

    /** Detects whether a package is a Chronus or Breezy icon pack. */
    private fun resolvePackType(packageName: String): PackType? {
        val pm = context.packageManager
        val isBreezy = pm.queryIntentActivities(
            Intent(BREEZY_ACTION),
            PackageManager.GET_META_DATA,
        ).any { it.activityInfo.applicationInfo.packageName == packageName }
        if (isBreezy) return PackType.BREEZY

        val isChronus = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY),
            PackageManager.GET_META_DATA,
        ).any { it.activityInfo.applicationInfo.packageName == packageName }
        if (isChronus) return PackType.CHRONUS

        return null
    }

    private enum class PackType { CHRONUS, BREEZY }

    companion object {
        private const val TAG = "WeatherIconProvider"

        /** Chronus-compatible icon pack intent category */
        const val CHRONUS_CATEGORY = "com.dvtonder.chronus.ICON_PACK"

        /** Breezy Weather icon pack intent action */
        const val BREEZY_ACTION = "org.breezyweather.ICON_PROVIDER_ACTION"

        // ── Chronus naming ────────────────────────────────────────────────────
        // drawables: weather_32 (clear day), weather_31 (clear night), weather_26 (cloudy), etc.

        private val CHRONUS_MAP: Map<WeatherCondition, Pair<String, String?>> = mapOf(
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
            WeatherCondition.SLEET         to ("5"  to null),
            WeatherCondition.SNOW          to ("16" to null),
            WeatherCondition.FLURRIES      to ("13" to null),
            WeatherCondition.HAIL          to ("17" to null),
            WeatherCondition.THUNDERSTORM  to ("4"  to "45"),
            WeatherCondition.WINDY         to ("24" to null),
            WeatherCondition.NA            to ("na" to null),
        )

        private fun chronusResName(condition: WeatherCondition, isDay: Boolean): String {
            val (day, night) = CHRONUS_MAP[condition] ?: ("na" to null)
            val num = if (!isDay && night != null) night else day
            return "weather_$num"
        }

        // ── Breezy naming ─────────────────────────────────────────────────────
        // drawables: clear_day, partly_cloudy_night, thunderstorm_day, etc.

        private val BREEZY_MAP: Map<WeatherCondition, String> = mapOf(
            WeatherCondition.CLEAR         to "clear",
            WeatherCondition.MOSTLY_CLEAR  to "clear",
            WeatherCondition.PARTLY_CLOUDY to "partly_cloudy",
            WeatherCondition.MOSTLY_CLOUDY to "cloudy",
            WeatherCondition.CLOUDY        to "cloudy",
            WeatherCondition.OVERCAST      to "cloudy",
            WeatherCondition.FOG           to "fog",
            WeatherCondition.DRIZZLE       to "rain",
            WeatherCondition.RAIN          to "rain",
            WeatherCondition.FREEZING_RAIN to "sleet",
            WeatherCondition.SLEET         to "sleet",
            WeatherCondition.SNOW          to "snow",
            WeatherCondition.FLURRIES      to "snow",
            WeatherCondition.HAIL          to "hail",
            WeatherCondition.THUNDERSTORM  to "thunderstorm",
            WeatherCondition.WINDY         to "wind",
            WeatherCondition.NA            to "cloudy",
        )

        private fun breezyResName(condition: WeatherCondition, isDay: Boolean): String {
            val code = BREEZY_MAP[condition] ?: "cloudy"
            val suffix = if (isDay) "day" else "night"
            return "${code}_$suffix"
        }

        /**
         * Returns all installed Chronus and Breezy icon packs as (packageName, label) pairs.
         * Deduplicates packages that appear in both categories.
         */
        fun getInstalledPacks(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            val seen = mutableSetOf<String>()
            val result = mutableListOf<Pair<String, String>>()

            // Breezy-format packs first
            pm.queryIntentActivities(
                Intent(BREEZY_ACTION),
                PackageManager.GET_META_DATA,
            ).forEach { info ->
                val pkg = info.activityInfo.applicationInfo.packageName
                if (seen.add(pkg)) {
                    result.add(pkg to info.loadLabel(pm).toString())
                }
            }

            // Then Chronus-format packs
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY),
                PackageManager.GET_META_DATA,
            ).forEach { info ->
                val pkg = info.activityInfo.applicationInfo.packageName
                if (seen.add(pkg)) {
                    result.add(pkg to info.loadLabel(pm).toString())
                }
            }

            return result
        }
    }
}
