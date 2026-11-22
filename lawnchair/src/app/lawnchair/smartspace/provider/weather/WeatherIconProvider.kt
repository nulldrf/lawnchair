package app.lawnchair.smartspace.provider.weather

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.graphics.drawable.toBitmap
import com.android.launcher3.R
import org.xmlpull.v1.XmlPullParser

/**
 * Resolves [WeatherCondition] to an [Icon] from either the built-in Lawnchair 9
 * weather icon set or an installed external weather icon pack.
 *
 * Pack selection values:
 * - `""` / `null` ([ICON_PACK_BUILTIN]): built-in Lawnchair 9 weather icons (default)
 * - `"none"` ([ICON_PACK_NONE]): no weather icon (temperature only)
 * - Any other package name: external icon pack, falling back to the built-in icon if
 *   the pack is uninstalled or missing a drawable.
 *
 * Supports external packs:
 * - Breezy packs (`org.breezyweather.ICON_PROVIDER`, metadata key `org.breezyweather.DRAWABLE_FILTER`)
 * - Geometric packs (`com.wangdaye.geometricweather.ICON_PROVIDER`, metadata key `com.wangdaye.geometricweather.DRAWABLE_FILTER`)
 * - Chronus packs (`com.dvtonder.chronus.ICON_PACK`) — no filter needed, names are stable
 */
class WeatherIconProvider(private val context: Context) {

    /**
     * Cache of resource-only [Context] objects created per icon pack package name.
     *
     * We only read XML/drawable resources from external packs, so we omit
     * [Context.CONTEXT_INCLUDE_CODE] to avoid loading foreign classloaders/DEX files.
     */
    private val packContextCache = mutableMapOf<String, Context>()

    /** Cache of resolved [PackType] per external icon pack package name. */
    private val packTypeCache = mutableMapOf<String, PackType>()

    /**
     * Cache of drawable-filter maps keyed by "$packageName/$metaKey".
     */
    private val filterCache = mutableMapOf<String, Map<String, String>>()

    fun getIcon(condition: WeatherCondition, isDay: Boolean, packPackageName: String?): Icon? {
        if (packPackageName == ICON_PACK_NONE) return null
        if (packPackageName.isNullOrBlank()) return getBuiltInIcon(condition, isDay)

        val externalIcon = loadExternalPackIcon(condition, isDay, packPackageName)
        return externalIcon ?: getBuiltInIcon(condition, isDay)
    }

    private fun getBuiltInIcon(condition: WeatherCondition, isDay: Boolean): Icon {
        val resId = builtInResId(condition, isDay)
        return Icon.createWithResource(context, resId)
    }

    private fun loadExternalPackIcon(
        condition: WeatherCondition,
        isDay: Boolean,
        packPackageName: String,
    ): Icon? {
        return try {
            val type = resolvePackType(packPackageName) ?: run {
                Log.w(TAG, "Unknown pack type for $packPackageName")
                return null
            }

            val packContext = getOrCreatePackContext(packPackageName) ?: return null
            val res = packContext.resources

            val resName = when (type) {
                PackType.CHRONUS -> chronusResName(condition, isDay)

                PackType.BREEZY, PackType.GEOMETRIC -> {
                    val logicalName = breezyResName(condition, isDay)
                    val filterMetaKey = when (type) {
                        PackType.BREEZY -> META_DRAWABLE_FILTER_BREEZY
                        else -> META_DRAWABLE_FILTER_GEOMETRIC
                    }
                    val filter = getOrLoadDrawableFilter(packContext, packPackageName, filterMetaKey)
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
            packContextCache.remove(packPackageName)
            packTypeCache.remove(packPackageName)
            null
        }
    }

    /**
     * Returns a cached resource [Context] for [packPackageName], creating one on first access.
     */
    private fun getOrCreatePackContext(packPackageName: String): Context? {
        packContextCache[packPackageName]?.let { return it }
        return try {
            context.createPackageContext(
                packPackageName,
                Context.CONTEXT_IGNORE_SECURITY,
            ).also { packContextCache[packPackageName] = it }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Pack not found when creating context: $packPackageName", e)
            packTypeCache.remove(packPackageName)
            null
        }
    }

    /**
     * Returns the drawable filter map for [packPackageName]/[metaKey], loading and
     * caching it on first access.
     */
    private fun getOrLoadDrawableFilter(
        packContext: Context,
        packPackageName: String,
        metaKey: String,
    ): Map<String, String> {
        val cacheKey = "$packPackageName/$metaKey"
        filterCache[cacheKey]?.let { return it }
        val loaded = readDrawableFilter(packContext, packPackageName, metaKey)
        filterCache[cacheKey] = loaded
        return loaded
    }

    /**
     * Reads the drawable filter XML from the pack's metadata and returns a
     * map of logical_name -> obfuscated_resource_name.
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
     * Parses a Breezy-format filter XML into a name -> value map.
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
        packTypeCache[packageName]?.let { return it }
        val pm = context.packageManager
        val resolved = when {
            pm.queryIntentActivities(Intent(ACTION_BREEZY), PackageManager.GET_RESOLVED_FILTER)
                .any { it.activityInfo.applicationInfo.packageName == packageName } -> PackType.BREEZY

            pm.queryIntentActivities(Intent(ACTION_GEOMETRIC), PackageManager.GET_RESOLVED_FILTER)
                .any { it.activityInfo.applicationInfo.packageName == packageName } -> PackType.GEOMETRIC

            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(CHRONUS_CATEGORY),
                PackageManager.GET_RESOLVED_FILTER,
            ).any { it.activityInfo.applicationInfo.packageName == packageName } -> PackType.CHRONUS

            else -> null
        }
        if (resolved != null) {
            packTypeCache[packageName] = resolved
        }
        return resolved
    }

    private enum class PackType { BREEZY, GEOMETRIC, CHRONUS }

    companion object {
        private const val TAG = "WeatherIconProvider"
        const val ICON_PACK_BUILTIN = ""
        const val ICON_PACK_NONE = "none"

        const val ACTION_BREEZY = "org.breezyweather.ICON_PROVIDER"
        const val ACTION_GEOMETRIC = "com.wangdaye.geometricweather.ICON_PROVIDER"
        const val CHRONUS_CATEGORY = "com.dvtonder.chronus.ICON_PACK"

        // Metadata keys for drawable filter XML — from Breezy's Constants.kt
        private const val META_DRAWABLE_FILTER_BREEZY = "org.breezyweather.DRAWABLE_FILTER"
        private const val META_DRAWABLE_FILTER_GEOMETRIC = "com.wangdaye.geometricweather.DRAWABLE_FILTER"

        // ── Built-in Lawnchair 9 weather icons ────────────────────────────────

        private val BUILTIN_DAY_MAP: Map<WeatherCondition, Int> = mapOf(
            WeatherCondition.CLEAR to R.drawable.weather_01,
            WeatherCondition.MOSTLY_CLEAR to R.drawable.weather_02,
            WeatherCondition.PARTLY_CLOUDY to R.drawable.weather_02,
            WeatherCondition.MOSTLY_CLOUDY to R.drawable.weather_03,
            WeatherCondition.CLOUDY to R.drawable.weather_04,
            WeatherCondition.OVERCAST to R.drawable.weather_04,
            WeatherCondition.FOG to R.drawable.weather_50,
            WeatherCondition.DRIZZLE to R.drawable.weather_10,
            WeatherCondition.RAIN to R.drawable.weather_09,
            WeatherCondition.FREEZING_RAIN to R.drawable.weather_10,
            WeatherCondition.SLEET to R.drawable.weather_13,
            WeatherCondition.SNOW to R.drawable.weather_13,
            WeatherCondition.FLURRIES to R.drawable.weather_13,
            WeatherCondition.HAIL to R.drawable.weather_10,
            WeatherCondition.THUNDERSTORM to R.drawable.weather_11,
            WeatherCondition.WINDY to R.drawable.weather_50,
            WeatherCondition.NA to R.drawable.weather_none_available,
        )

        private val BUILTIN_NIGHT_MAP: Map<WeatherCondition, Int> = mapOf(
            WeatherCondition.CLEAR to R.drawable.weather_01n,
            WeatherCondition.MOSTLY_CLEAR to R.drawable.weather_02n,
            WeatherCondition.PARTLY_CLOUDY to R.drawable.weather_02n,
            WeatherCondition.MOSTLY_CLOUDY to R.drawable.weather_03n,
            WeatherCondition.CLOUDY to R.drawable.weather_04n,
            WeatherCondition.OVERCAST to R.drawable.weather_04n,
            WeatherCondition.FOG to R.drawable.weather_50,
            WeatherCondition.DRIZZLE to R.drawable.weather_10n,
            WeatherCondition.RAIN to R.drawable.weather_09,
            WeatherCondition.FREEZING_RAIN to R.drawable.weather_10n,
            WeatherCondition.SLEET to R.drawable.weather_13,
            WeatherCondition.SNOW to R.drawable.weather_13,
            WeatherCondition.FLURRIES to R.drawable.weather_13,
            WeatherCondition.HAIL to R.drawable.weather_10n,
            WeatherCondition.THUNDERSTORM to R.drawable.weather_11,
            WeatherCondition.WINDY to R.drawable.weather_50,
            WeatherCondition.NA to R.drawable.weather_none_available,
        )

        @DrawableRes
        fun builtInResId(condition: WeatherCondition, isDay: Boolean): Int {
            val map = if (isDay) BUILTIN_DAY_MAP else BUILTIN_NIGHT_MAP
            return map[condition] ?: R.drawable.weather_none_available
        }

        // ── Breezy naming ─────────────────────────────────────────────────────
        // Logical names from Constants.kt: getResourcesName(code) + "_" + "day"/"night"

        private val BREEZY_MAP: Map<WeatherCondition, String> = mapOf(
            WeatherCondition.CLEAR to "weather_clear",
            WeatherCondition.MOSTLY_CLEAR to "weather_clear",
            WeatherCondition.PARTLY_CLOUDY to "weather_partly_cloudy",
            WeatherCondition.MOSTLY_CLOUDY to "weather_cloudy",
            WeatherCondition.CLOUDY to "weather_cloudy",
            WeatherCondition.OVERCAST to "weather_cloudy",
            WeatherCondition.FOG to "weather_fog",
            WeatherCondition.DRIZZLE to "weather_rain",
            WeatherCondition.RAIN to "weather_rain",
            WeatherCondition.FREEZING_RAIN to "weather_sleet",
            WeatherCondition.SLEET to "weather_sleet",
            WeatherCondition.SNOW to "weather_snow",
            WeatherCondition.FLURRIES to "weather_snow",
            WeatherCondition.HAIL to "weather_hail",
            WeatherCondition.THUNDERSTORM to "weather_thunderstorm",
            WeatherCondition.WINDY to "weather_wind",
            WeatherCondition.NA to "weather_cloudy",
        )

        private fun breezyResName(condition: WeatherCondition, isDay: Boolean): String {
            val base = BREEZY_MAP[condition] ?: "weather_cloudy"
            return "${base}_${if (isDay) "day" else "night"}"
        }

        // ── Chronus naming ────────────────────────────────────────────────────

        private val CHRONUS_MAP: Map<WeatherCondition, Pair<String, String?>> = mapOf(
            WeatherCondition.CLEAR to ("32" to "31"),
            WeatherCondition.MOSTLY_CLEAR to ("34" to "33"),
            WeatherCondition.PARTLY_CLOUDY to ("30" to "29"),
            WeatherCondition.MOSTLY_CLOUDY to ("28" to "27"),
            WeatherCondition.CLOUDY to ("26" to null),
            WeatherCondition.OVERCAST to ("26" to null),
            WeatherCondition.FOG to ("20" to null),
            WeatherCondition.DRIZZLE to ("11" to null),
            WeatherCondition.RAIN to ("12" to null),
            WeatherCondition.FREEZING_RAIN to ("10" to null),
            WeatherCondition.SLEET to ("5" to null),
            WeatherCondition.SNOW to ("16" to null),
            WeatherCondition.FLURRIES to ("13" to null),
            WeatherCondition.HAIL to ("17" to null),
            WeatherCondition.THUNDERSTORM to ("4" to "45"),
            WeatherCondition.WINDY to ("24" to null),
            WeatherCondition.NA to ("na" to null),
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
