// Physical path: smartspace/provider/WeatherDataProvider.kt
package app.lawnchair.smartspace.provider

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.lawnchair.BlankActivity
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.openmeteo.OpenMeteoApi
import app.lawnchair.smartspace.provider.openmeteo.json.OpenMeteoWeatherCurrent
import app.lawnchair.smartspace.provider.pirateweather.PirateWeatherApi
import app.lawnchair.smartspace.provider.pirateweather.json.PirateWeatherCurrently
import app.lawnchair.smartspace.provider.weather.WeatherCondition
import app.lawnchair.smartspace.provider.weather.WeatherIconProvider
import app.lawnchair.smartspace.provider.weather.WeatherProvider
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private data class WeatherConfig(
    val provider: WeatherProvider,
    val iconPack: String?,
    val pirateApiKey: String,
    val refreshInterval: Long,
)

/** Minimal weather state we persist to survive restarts and connectivity loss. */
private data class CachedWeather(
    val tempInt: Int,
    val conditionName: String,
    val isDay: Boolean,
    val summary: String?,
    val timestamp: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherDataProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_weather_source,
    { smartspaceWeatherEnabled },
) {
    private val prefs = PreferenceManager2.getInstance(context)
    private val iconProvider = WeatherIconProvider(context)
    private val openMeteoApi = buildRetrofit(OPEN_METEO_BASE_URL).create(OpenMeteoApi::class.java)
    private val pirateApi = buildRetrofit(PIRATE_BASE_URL).create(PirateWeatherApi::class.java)
    private val sp: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val internalTargets = combine(
        prefs.smartspaceWeatherProvider.get(),
        prefs.smartspaceWeatherIconPack.get(),
        prefs.pirateWeatherApiKey.get(),
        prefs.smartspaceWeatherRefreshInterval.get(),
    ) { provider, iconPack, apiKey, interval ->
        WeatherConfig(
            provider = provider,
            iconPack = iconPack.ifBlank { null },
            pirateApiKey = apiKey,
            refreshInterval = interval,
        )
    }.flatMapLatest { config ->
        when (config.provider) {
            WeatherProvider.NONE -> flowOf(emptyList())
            WeatherProvider.OPEN_METEO ->
                weatherFlow(config) { fetchOpenMeteo(config.iconPack) }
            WeatherProvider.PIRATE_WEATHER ->
                weatherFlow(config) { fetchPirateWeather(config.iconPack, config.pirateApiKey) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Polling flow that:
     * 1. Immediately emits cached data (if available) so the card is never blank on startup.
     * 2. Fetches fresh data; on success updates cache and emits; on failure re-emits cache.
     * 3. Waits [WeatherConfig.refreshInterval] minutes, then repeats.
     */
    private fun weatherFlow(
        config: WeatherConfig,
        fetch: suspend () -> List<SmartspaceTarget>,
    ) = flow {
        // Emit cached data immediately so the UI is never blank while fetching
        val cached = buildCachedTargets(config.iconPack)
        if (cached.isNotEmpty()) emit(cached)

        while (true) {
            val fresh = fetch()
            if (fresh.isNotEmpty()) {
                emit(fresh)
            } else {
                // Fetch failed — re-emit cache so the card doesn't disappear
                val fallback = buildCachedTargets(config.iconPack)
                if (fallback.isNotEmpty()) emit(fallback)
            }
            delay(config.refreshInterval.minutes)
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    private fun saveCache(tempInt: Int, condition: WeatherCondition, isDay: Boolean, summary: String?) {
        sp.edit()
            .putInt(KEY_TEMP, tempInt)
            .putString(KEY_CONDITION, condition.name)
            .putBoolean(KEY_IS_DAY, isDay)
            .putString(KEY_SUMMARY, summary)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun loadCache(): CachedWeather? {
        val temp = sp.getInt(KEY_TEMP, Int.MIN_VALUE)
        if (temp == Int.MIN_VALUE) return null
        val conditionName = sp.getString(KEY_CONDITION, null) ?: return null
        return CachedWeather(
            tempInt = temp,
            conditionName = conditionName,
            isDay = sp.getBoolean(KEY_IS_DAY, true),
            summary = sp.getString(KEY_SUMMARY, null),
            timestamp = sp.getLong(KEY_TIMESTAMP, 0L),
        )
    }

    private fun buildCachedTargets(iconPack: String?): List<SmartspaceTarget> {
        val cache = loadCache() ?: return emptyList()
        val condition = runCatching { WeatherCondition.valueOf(cache.conditionName) }
            .getOrDefault(WeatherCondition.NA)
        return listOf(
            SmartspaceTarget(
                id = "weatherCached",
                headerAction = SmartspaceAction(
                    id = "weatherCachedAction",
                    icon = iconProvider.getIcon(condition, cache.isDay, iconPack),
                    title = "",
                    subtitle = "${cache.tempInt}°C",
                    contentDescription = cache.summary ?: condition.toDisplayString(),
                ),
                score = SmartspaceScores.SCORE_WEATHER,
                featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
            ),
        )
    }

    // ── Open-Meteo ────────────────────────────────────────────────────────────

    private suspend fun fetchOpenMeteo(iconPack: String?): List<SmartspaceTarget> {
        return try {
            val loc = getLocation()
                ?: return emptyList<SmartspaceTarget>().also { Log.w(TAG, "No location for Open-Meteo") }
            Log.d(TAG, "Open-Meteo fetching for ${loc.latitude}, ${loc.longitude}")
            val result = openMeteoApi.getWeather(loc.latitude, loc.longitude, OPEN_METEO_CURRENT_FIELDS)
            if (result.error == true) { Log.w(TAG, "Open-Meteo error: ${result.reason}"); return emptyList() }
            val current = result.current ?: return emptyList()
            buildOpenMeteoTarget(current, iconPack)?.let { target ->
                // Persist to cache
                val temp = current.temperature?.toInt() ?: return emptyList()
                val isDay = current.isDay != 0
                val condition = WeatherCondition.fromWmoCode(current.weatherCode, isDay)
                saveCache(temp, condition, isDay, null)
                listOf(target)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Open-Meteo fetch failed", e)
            emptyList()
        }
    }

    private fun buildOpenMeteoTarget(current: OpenMeteoWeatherCurrent, iconPack: String?): SmartspaceTarget? {
        val temp = current.temperature ?: return null
        val isDay = current.isDay != 0
        val condition = WeatherCondition.fromWmoCode(current.weatherCode, isDay)
        return SmartspaceTarget(
            id = "openMeteoWeather",
            headerAction = SmartspaceAction(
                id = "openMeteoWeatherAction",
                icon = iconProvider.getIcon(condition, isDay, iconPack),
                title = "",
                subtitle = "${temp.toInt()}°C",
                contentDescription = condition.toDisplayString(),
            ),
            score = SmartspaceScores.SCORE_WEATHER,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
        )
    }

    // ── PirateWeather ─────────────────────────────────────────────────────────

    private suspend fun fetchPirateWeather(iconPack: String?, apiKey: String): List<SmartspaceTarget> {
        return try {
            if (apiKey.isBlank()) { Log.w(TAG, "PirateWeather: blank API key"); return emptyList() }
            val loc = getLocation()
                ?: return emptyList<SmartspaceTarget>().also { Log.w(TAG, "No location for PirateWeather") }
            Log.d(TAG, "PirateWeather fetching for ${loc.latitude}, ${loc.longitude}")
            val result = pirateApi.getForecast(apiKey, loc.latitude, loc.longitude)
            val currently = result.currently ?: return emptyList()
            buildPirateTarget(currently, iconPack)?.let { target ->
                val temp = currently.temperature?.toInt() ?: return emptyList()
                val isDay = currently.icon?.endsWith("-night") == false
                val condition = WeatherCondition.fromPirateIcon(currently.icon)
                saveCache(temp, condition, isDay, currently.summary)
                listOf(target)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "PirateWeather fetch failed", e)
            emptyList()
        }
    }

    private fun buildPirateTarget(currently: PirateWeatherCurrently, iconPack: String?): SmartspaceTarget? {
        val temp = currently.temperature ?: return null
        val isDay = currently.icon?.endsWith("-night") == false
        val condition = WeatherCondition.fromPirateIcon(currently.icon)
        return SmartspaceTarget(
            id = "pirateWeather",
            headerAction = SmartspaceAction(
                id = "pirateWeatherAction",
                icon = iconProvider.getIcon(condition, isDay, iconPack),
                title = "",
                subtitle = "${temp.toInt()}°C",
                contentDescription = currently.summary ?: condition.toDisplayString(),
            ),
            score = SmartspaceScores.SCORE_WEATHER,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
        )
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    override suspend fun requiresSetup(): Boolean {
        return when (prefs.smartspaceWeatherProvider.get().first()) {
            WeatherProvider.NONE -> false
            WeatherProvider.OPEN_METEO -> !hasLocationPermission()
            WeatherProvider.PIRATE_WEATHER ->
                !hasLocationPermission() || prefs.pirateWeatherApiKey.get().first().isBlank()
        }
    }

    override suspend fun startSetup(activity: Activity) {
        val provider = prefs.smartspaceWeatherProvider.get().first()
        val (title, desc) = when {
            provider == WeatherProvider.PIRATE_WEATHER &&
                prefs.pirateWeatherApiKey.get().first().isBlank() ->
                activity.getString(R.string.smartspace_pirate_weather_api_key_title) to
                    activity.getString(R.string.smartspace_pirate_weather_api_key_description)
            else ->
                activity.getString(R.string.smartspace_weather_location_permission_title) to
                    activity.getString(R.string.smartspace_weather_location_permission_description)
        }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        BlankActivity.startBlankActivityDialog(
            activity, intent, title, desc,
            context.getString(R.string.title_change_settings),
        )
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private suspend fun getLocation(): Location? {
        if (!hasLocationPermission()) { Log.w(TAG, "Location permission not granted"); return null }
        val lm = context.getSystemService<LocationManager>() ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        for (p in providers) {
            val cached = lm.getLastKnownLocation(p)
            if (cached != null) { Log.d(TAG, "Cached location from $p"); return cached }
        }
        Log.d(TAG, "No cached location — requesting fresh fix")
        for (provider in providers.filter { lm.isProviderEnabled(it) }) {
            val loc = requestFreshLocation(lm, provider)
            if (loc != null) return loc
        }
        return null
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestFreshLocation(lm: LocationManager, provider: String): Location? =
        withTimeoutOrNull(LOCATION_TIMEOUT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                callbackFlow {
                    val signal = CancellationSignal()
                    lm.getCurrentLocation(provider, signal, Executors.newSingleThreadExecutor()) { loc ->
                        trySend(loc); close()
                    }
                    awaitClose { signal.cancel() }
                }.first()
            } else {
                callbackFlow<Location?> {
                    val thread = HandlerThread("WeatherLoc-$provider").also { it.start() }
                    val listener = LocationListener { loc -> trySend(loc); close() }
                    lm.requestSingleUpdate(provider, listener, thread.looper)
                    awaitClose { lm.removeUpdates(listener); thread.quit() }
                }.first()
            }
        }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "WeatherDataProvider"
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
        private const val PIRATE_BASE_URL = "https://api.pirateweather.net/"
        private const val OPEN_METEO_CURRENT_FIELDS = "temperature_2m,weather_code,is_day"
        private val LOCATION_TIMEOUT = 15.seconds

        // SharedPreferences cache keys
        private const val PREFS_NAME = "weather_data_provider_cache"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_IS_DAY = "is_day"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_TIMESTAMP = "timestamp"

        val REFRESH_INTERVAL_OPTIONS = listOf(15L, 30L, 60L, 180L)

        private fun buildRetrofit(baseUrl: String): Retrofit {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory("application/json; charset=UTF8".toMediaType()))
                .build()
        }
    }
}
