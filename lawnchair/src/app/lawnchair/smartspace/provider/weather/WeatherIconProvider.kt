package app.lawnchair.smartspace.provider.weather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Log
import android.util.Xml
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/**
 * Resolves [WeatherCondition] to an [Icon] from an installed icon pack.
 *
 * Breezy icon packs obfuscate their drawable resource names (e.g. `Zk.png`, `Y_.png`).
 * A filter XML inside the pack maps logical names (`weather_rain_day`) to the obfuscated
 * resource names. We read this filter — exactly like Breezy's IconPackResourcesProvider —
 * and use the mapped name to look up the actual resource ID.
 *
 * Supports:
 * - Breezy packs (`org.breezyweather.ICON_PROVIDER`, metadata key `org.breezyweather.DRAWABLE_FILTER`)
 * - Geometric packs (`com.wangdaye.geometricweather.ICON_PROVIDER`, metadata key `com.wangdaye.geometricweather.DRAWABLE_FILTER`)
 * - Chronus packs (`com.dvtonder.chronus.ICON_PACK`) — no filter needed, names are stable
 */
class WeatherIconProvider(private val context: Context) {

    fun getIcon(condition: WeatherCondition, isDay: Boolean, packPackageName: String?): Icon? {
        if (packPackageName.isNullOrBlank()) return null
        return try {
            val type = resolvePackType(packPackageName) ?: run {
                Log.w(TAG, "Unknown pack type for $packPackageName")
                return null
            }
            val packContext = context.createPackageContext(
                packPackageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY,
            )
            val res = packContext.resources

            val resName = when (type) {
                PackType.CHRONUS -> chronusResName(condition, isDay)
                PackType.BREEZY, PackType.GEOMETRIC -> {
                    val logicalName = breezyResName(condition, isDay)
                    // Read the drawable filter to map logical → obfuscated name
                    val filterMetaKey = when (type) {
                        PackType.BREEZY -> META_DRAWABLE_FILTER_BREEZY
                        else -> META_DRAWABLE_FILTER_GEOMETRIC
                    }
                    val filter = readDrawableFilter(packContext, packPackageName, filterMetaKey)
                    filter[logicalName] ?: logicalName
                }
            }

            val resId = res.getIdentifier(resName, "drawable", packPackageName)
            if (resId == 0) {
                Log.w(TAG, "Drawable '$resName' (mapped) not found in $packPackageName ($type)")
                return null
            }
            Icon.createWithBitmap(res.getDrawable(resId, null)?.toBitmap() ?: return null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon from $packPackageName", e)
            null
        }
    }

    /**
     * Reads the drawable filter XML from the pack's metadata and returns a
     * map of logical_name → obfuscated_resource_name.
     *
     * The filter XML format (from Breezy):
     * <resources>
     *   <item name="weather_rain_day" value="Zk"/>
     *   ...
     * </resources>
     */
    private fun readDrawableFilter(
        packContext: Context,
        packPackageName: String,
        metaKey: String,
    ): Map<String, String> {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packPackageName, PackageManager.GET_META_DATA)
            val resId = appInfo.metaData?.getInt(metaKey) ?: 0
            if (resId == 0) return emptyMap()

            val parser = packContext.resources.getXml(resId)
            parseFilterMap(parser)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read drawable filter from $packPackageName", e)
            emptyMap()
        }
    }

    /**
     * Parses a Breezy-format filter XML into a name → value map.
     * Mirrors XmlHelper.getFilterMap() from Breezy source.
     */
    private fun parseFilterMap(parser: XmlPullParser): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val name = parser.getAttributeValue(null, "name")
                    val value = parser.getAttributeValue(null, "value")
                    if (!name.isNullOrEmpty() && !value.isNullOrEmpty()) {
                        map[name] = value
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing filter XML", e)
        }
        return map
    }

    private fun resolvePackType(packageName: String): PackType? {
        val pm = context.packageManager
        if (pm.queryIntentActivities(Intent(ACTION_BREEZY), PackageManager.GET_RESOLVED_FILTER)
                .any { it.activityInfo.applicationInfo.packageName == packageName }
        ) return PackType.BREEZY
        if (pm.queryIntentActivities(Intent(ACTION_GEOMETRIC), PackageManager.GET_RESOLVED_FILTER)
                .any { it.activityInfo.applicationInfo.packageName == packageName }
        ) return PackType.GEOMETRIC
        if (pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY),
                PackageManager.GET_RESOLVED_FILTER,
            ).any { it.activityInfo.applicationInfo.packageName == packageName }
        ) return PackType.CHRONUS
        return null
    }

    private enum class PackType { BREEZY, GEOMETRIC, CHRONUS }

    companion object {
        private const val TAG = "WeatherIconProvider"
        const val ACTION_BREEZY = "org.breezyweather.ICON_PROVIDER"
        const val ACTION_GEOMETRIC = "com.wangdaye.geometricweather.ICON_PROVIDER"
        const val CHRONUS_CATEGORY = "com.dvtonder.chronus.ICON_PACK"

        // Metadata keys for drawable filter XML — from Breezy's Constants.kt
        private const val META_DRAWABLE_FILTER_BREEZY = "org.breezyweather.DRAWABLE_FILTER"
        private const val META_DRAWABLE_FILTER_GEOMETRIC = "com.wangdaye.geometricweather.DRAWABLE_FILTER"

        // ── Breezy naming ─────────────────────────────────────────────────────
        // Logical names from Constants.kt: getResourcesName(code) + "_" + "day"/"night"

        private val BREEZY_MAP: Map<WeatherCondition, String> = mapOf(
            WeatherCondition.CLEAR         to "weather_clear",
            WeatherCondition.MOSTLY_CLEAR  to "weather_clear",
            WeatherCondition.PARTLY_CLOUDY to "weather_partly_cloudy",
            WeatherCondition.MOSTLY_CLOUDY to "weather_cloudy",
            WeatherCondition.CLOUDY        to "weather_cloudy",
            WeatherCondition.OVERCAST      to "weather_cloudy",
            WeatherCondition.FOG           to "weather_fog",
            WeatherCondition.DRIZZLE       to "weather_rain",
            WeatherCondition.RAIN          to "weather_rain",
            WeatherCondition.FREEZING_RAIN to "weather_sleet",
            WeatherCondition.SLEET         to "weather_sleet",
            WeatherCondition.SNOW          to "weather_snow",
            WeatherCondition.FLURRIES      to "weather_snow",
            WeatherCondition.HAIL          to "weather_hail",
            WeatherCondition.THUNDERSTORM  to "weather_thunderstorm",
            WeatherCondition.WINDY         to "weather_wind",
            WeatherCondition.NA            to "weather_cloudy",
        )

        private fun breezyResName(condition: WeatherCondition, isDay: Boolean): String {
            val base = BREEZY_MAP[condition] ?: "weather_cloudy"
            return "${base}_${if (isDay) "day" else "night"}"
        }

        // ── Chronus naming ────────────────────────────────────────────────────

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
            return "weather_${if (!isDay && night != null) night else day}"
        }

        fun getInstalledPacks(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            val seen = mutableSetOf<String>()
            val result = mutableListOf<Pair<String, String>>()

            fun addPacks(intent: Intent) {
                pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
                    .forEach { info ->
                        val pkg = info.activityInfo.applicationInfo.packageName
                        if (seen.add(pkg)) result.add(pkg to info.loadLabel(pm).toString())
                    }
            }

            addPacks(Intent(ACTION_BREEZY))
            addPacks(Intent(ACTION_GEOMETRIC))
            addPacks(Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY))

            return result
        }
    }
}
