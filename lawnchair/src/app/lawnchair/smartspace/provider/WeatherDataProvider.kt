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
import app.lawnchair.smartspace.provider.accuweather.AccuWeatherApi
import app.lawnchair.smartspace.provider.accuweather.json.AccuCurrentResult
import app.lawnchair.smartspace.provider.openmeteo.OpenMeteoApi
import app.lawnchair.smartspace.provider.openmeteo.OpenMeteoGeocodingApi
import app.lawnchair.smartspace.provider.openmeteo.json.OpenMeteoWeatherCurrent
import app.lawnchair.smartspace.provider.openweathermap.OpenWeatherMapApi
import app.lawnchair.smartspace.provider.openweathermap.json.OpenWeatherCurrentResult
import app.lawnchair.smartspace.provider.pirateweather.PirateWeatherApi
import app.lawnchair.smartspace.provider.pirateweather.json.PirateWeatherCurrently
import app.lawnchair.smartspace.provider.weather.TemperatureUnit
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

private data class WeatherConfigPartial(
    val provider: WeatherProvider,
    val iconPack: String,
    val unit: TemperatureUnit,
    val city: String,
    val pirateApiKey: String,
)

private data class WeatherConfig(
    val provider: WeatherProvider,
    val iconPack: String?,
    val unit: TemperatureUnit,
    val city: String,
    val pirateApiKey: String,
    val owmApiKey: String,
    val accuApiKey: String,
    val refreshInterval: Long,
)

private data class Coords(val lat: Double, val lon: Double)

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherDataProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_weather_source,
    { smartspaceWeatherEnabled },
) {
    private val prefs = PreferenceManager2.getInstance(context)
    private val iconProvider = WeatherIconProvider(context)
    private val sp: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val openMeteoApi = buildRetrofit(OPEN_METEO_BASE_URL).create(OpenMeteoApi::class.java)
    private val openMeteoGeoApi = buildRetrofit(OPEN_METEO_GEO_URL).create(OpenMeteoGeocodingApi::class.java)
    private val pirateApi = buildRetrofit(PIRATE_BASE_URL).create(PirateWeatherApi::class.java)
    private val owmApi = buildRetrofit(OWM_BASE_URL).create(OpenWeatherMapApi::class.java)
    private val accuApi = buildRetrofit(ACCU_BASE_URL).create(AccuWeatherApi::class.java)

    private val configFlow = combine(
        prefs.smartspaceWeatherProvider.get(),
        prefs.smartspaceWeatherIconPack.get(),
        prefs.smartspaceWeatherUnit.get(),
        prefs.smartspaceWeatherCity.get(),
        prefs.pirateWeatherApiKey.get(),
    ) { provider, iconPack, unit, city, pirateKey ->
        WeatherConfigPartial(provider, iconPack, unit, city, pirateKey)
    }.combine(
        combine(
            prefs.openWeatherMapApiKey.get(),
            prefs.accuWeatherApiKey.get(),
            prefs.smartspaceWeatherRefreshInterval.get(),
        ) { owmKey, accuKey, interval -> Triple(owmKey, accuKey, interval) },
    ) { partial, (owmKey, accuKey, interval) ->
        WeatherConfig(
            provider = partial.provider,
            iconPack = partial.iconPack.ifBlank { null },
            unit = partial.unit,
            city = partial.city,
            pirateApiKey = partial.pirateApiKey,
            owmApiKey = owmKey,
            accuApiKey = accuKey,
            refreshInterval = interval,
        )
    }

    override val internalTargets = configFlow.flatMapLatest { config ->
        when (config.provider) {
            WeatherProvider.NONE -> flowOf(emptyList())
            WeatherProvider.OPEN_METEO ->
                weatherFlow(config.refreshInterval) { fetchOpenMeteo(config) }
            WeatherProvider.PIRATE_WEATHER ->
                weatherFlow(config.refreshInterval) { fetchPirateWeather(config) }
            WeatherProvider.OPEN_WEATHER_MAP ->
                weatherFlow(config.refreshInterval) { fetchOpenWeatherMap(config) }
            WeatherProvider.ACCU_WEATHER ->
                weatherFlow(config.refreshInterval) { fetchAccuWeather(config) }
        }
    }.flowOn(Dispatchers.IO)

    // ── Setup ─────────────────────────────────────────────────────────────────

    override suspend fun requiresSetup(): Boolean {
        val provider = prefs.smartspaceWeatherProvider.get().first()
        val city = prefs.smartspaceWeatherCity.get().first()
        val needsLocation = city.isBlank() && !hasLocationPermission()
        return when (provider) {
            WeatherProvider.NONE -> false
            WeatherProvider.OPEN_METEO -> needsLocation
            WeatherProvider.PIRATE_WEATHER ->
                needsLocation || prefs.pirateWeatherApiKey.get().first().isBlank()
            WeatherProvider.OPEN_WEATHER_MAP ->
                needsLocation || prefs.openWeatherMapApiKey.get().first().isBlank()
            WeatherProvider.ACCU_WEATHER ->
                prefs.accuWeatherApiKey.get().first().isBlank()
        }
    }

    override suspend fun startSetup(activity: Activity) {
        val provider = prefs.smartspaceWeatherProvider.get().first()
        val city = prefs.smartspaceWeatherCity.get().first()
        val (title, desc) = when {
            provider == WeatherProvider.PIRATE_WEATHER &&
                prefs.pirateWeatherApiKey.get().first().isBlank() ->
                activity.getString(R.string.smartspace_pirate_weather_api_key_title) to
                    activity.getString(R.string.smartspace_pirate_weather_api_key_description)
            provider == WeatherProvider.OPEN_WEATHER_MAP &&
                prefs.openWeatherMapApiKey.get().first().isBlank() ->
                activity.getString(R.string.smartspace_owm_api_key_title) to
                    activity.getString(R.string.smartspace_owm_api_key_description)
            provider == WeatherProvider.ACCU_WEATHER &&
                prefs.accuWeatherApiKey.get().first().isBlank() ->
                activity.getString(R.string.smartspace_accu_api_key_title) to
                    activity.getString(R.string.smartspace_accu_api_key_description)
            city.isBlank() && !hasLocationPermission() ->
                activity.getString(R.string.smartspace_weather_location_permission_title) to
                    activity.getString(R.string.smartspace_weather_location_permission_description)
            else -> return
        }
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        BlankActivity.startBlankActivityDialog(
            activity, intent, title, desc,
            context.getString(R.string.title_change_settings),
        )
    }

    // ── Polling flow with cache ───────────────────────────────────────────────

    private fun weatherFlow(
        intervalMinutes: Long,
        fetch: suspend () -> List<SmartspaceTarget>,
    ) = flow {
        // Emit cached data immediately to avoid blank card on restart
        val cached = buildCachedTargets()
        if (cached.isNotEmpty()) emit(cached)

        while (true) {
            val fresh = fetch()
            emit(if (fresh.isNotEmpty()) fresh else buildCachedTargets().ifEmpty { listOf(emptyWeatherTarget()) })
            delay(intervalMinutes.minutes)
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    private fun saveCache(tempCelsius: Double, condition: WeatherCondition, isDay: Boolean, summary: String?) {
        sp.edit()
            .putFloat(KEY_TEMP, tempCelsius.toFloat())
            .putString(KEY_CONDITION, condition.name)
            .putBoolean(KEY_IS_DAY, isDay)
            .putString(KEY_SUMMARY, summary)
            .apply()
    }

    private fun buildCachedTargets(): List<SmartspaceTarget> {
        val temp = sp.getFloat(KEY_TEMP, Float.MIN_VALUE)
        if (temp == Float.MIN_VALUE) return emptyList()
        val conditionName = sp.getString(KEY_CONDITION, null) ?: return emptyList()
        val condition = runCatching { WeatherCondition.valueOf(conditionName) }
            .getOrDefault(WeatherCondition.NA)
        val isDay = sp.getBoolean(KEY_IS_DAY, true)
        val summary = sp.getString(KEY_SUMMARY, null)
        val iconPack = prefs.smartspaceWeatherIconPack.get().let {
            try { kotlinx.coroutines.runBlocking { it.first() }.ifBlank { null } } catch (e: Exception) { null }
        }
        val unit = try {
            kotlinx.coroutines.runBlocking { prefs.smartspaceWeatherUnit.get().first() }
        } catch (e: Exception) { TemperatureUnit.CELSIUS }
        return listOf(buildTarget("weatherCached", condition, isDay, temp.toDouble(), summary, iconPack, unit))
    }

    // ── Open-Meteo ────────────────────────────────────────────────────────────

    private suspend fun fetchOpenMeteo(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            val coords = resolveCoords(config) ?: return emptyList()
            val result = openMeteoApi.getWeather(coords.lat, coords.lon, OPEN_METEO_CURRENT_FIELDS)
            if (result.error == true) { Log.w(TAG, "Open-Meteo error: ${result.reason}"); return emptyList() }
            val current = result.current ?: return emptyList()
            val temp = current.temperature ?: return emptyList()
            val isDay = current.isDay != 0
            val condition = WeatherCondition.fromWmoCode(current.weatherCode, isDay)
            saveCache(temp, condition, isDay, null)
            listOf(buildTarget("openMeteoWeather", condition, isDay, temp, null, config.iconPack, config.unit))
        } catch (e: Exception) { Log.e(TAG, "Open-Meteo fetch failed", e); emptyList() }
    }

    // ── PirateWeather ─────────────────────────────────────────────────────────

    private suspend fun fetchPirateWeather(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            if (config.pirateApiKey.isBlank()) return emptyList()
            val coords = resolveCoords(config) ?: return emptyList()
            val result = pirateApi.getForecast(config.pirateApiKey, coords.lat, coords.lon)
            val currently = result.currently ?: return emptyList()
            val temp = currently.temperature ?: return emptyList()
            val isDay = currently.icon?.endsWith("-night") == false
            val condition = WeatherCondition.fromPirateIcon(currently.icon)
            saveCache(temp, condition, isDay, currently.summary)
            listOf(buildTarget("pirateWeather", condition, isDay, temp, currently.summary, config.iconPack, config.unit))
        } catch (e: Exception) { Log.e(TAG, "PirateWeather fetch failed", e); emptyList() }
    }

    // ── OpenWeatherMap ────────────────────────────────────────────────────────

    private suspend fun fetchOpenWeatherMap(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            if (config.owmApiKey.isBlank()) return emptyList()
            val coords = resolveCoords(config) ?: return emptyList()
            // Always fetch in metric — we convert to user's unit at display time
            val result = owmApi.getCurrent(config.owmApiKey, coords.lat, coords.lon, units = "metric")
            val temp = result.main?.temp ?: return emptyList()
            val weatherId = result.weather?.firstOrNull()?.id
            val icon = result.weather?.firstOrNull()?.icon ?: ""
            val isDay = !icon.endsWith("n")
            val condition = owmCodeToCondition(weatherId)
            val description = result.weather?.firstOrNull()?.description
            saveCache(temp, condition, isDay, description)
            listOf(buildTarget("owmWeather", condition, isDay, temp, description, config.iconPack, config.unit))
        } catch (e: Exception) { Log.e(TAG, "OWM fetch failed", e); emptyList() }
    }

    /** Maps OWM weather IDs to WeatherCondition — ported from Breezy's OpenWeatherService */
    private fun owmCodeToCondition(id: Int?): WeatherCondition = when (id) {
        in 200..202, 221, in 230..232 -> WeatherCondition.THUNDERSTORM
        in 210..212 -> WeatherCondition.THUNDERSTORM
        in 300..321 -> WeatherCondition.DRIZZLE
        in 500..504 -> WeatherCondition.RAIN
        511 -> WeatherCondition.SLEET
        in 600..602, in 620..622 -> WeatherCondition.SNOW
        in 611..616 -> WeatherCondition.SLEET
        741 -> WeatherCondition.FOG
        in 700..781 -> WeatherCondition.CLOUDY
        800 -> WeatherCondition.CLEAR
        801, 802 -> WeatherCondition.PARTLY_CLOUDY
        803, 804 -> WeatherCondition.CLOUDY
        else -> WeatherCondition.NA
    }

    // ── AccuWeather ───────────────────────────────────────────────────────────

    private suspend fun fetchAccuWeather(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            if (config.accuApiKey.isBlank()) return emptyList()

            // AccuWeather requires a location key first — resolve from city name or coords
            val locationKey = resolveAccuLocationKey(config) ?: return emptyList()

            val results = accuApi.getCurrent(locationKey, config.accuApiKey)
            val current = results.firstOrNull() ?: return emptyList()
            // AccuWeather always returns metric in .Metric field
            val temp = current.Temperature?.Metric?.Value ?: return emptyList()
            val condition = accuIconToCondition(current.WeatherIcon)
            // AccuWeather icons 1-5 are daytime clear/partly-cloudy, 33-44 are night
            val isDay = (current.WeatherIcon ?: 0) <= 32
            val description = current.WeatherText
            saveCache(temp, condition, isDay, description)
            listOf(buildTarget("accuWeather", condition, isDay, temp, description, config.iconPack, config.unit))
        } catch (e: Exception) { Log.e(TAG, "AccuWeather fetch failed", e); emptyList() }
    }

    /**
     * Resolves an AccuWeather location key.
     * - If city is set: uses AccuWeather's own text search
     * - If no city: uses GPS coordinates via geoposition search
     *   (note: geoposition endpoint needs separate implementation — for now falls back to city search)
     */
    private suspend fun resolveAccuLocationKey(config: WeatherConfig): String? {
        return try {
            if (config.city.isNotBlank()) {
                accuApi.searchLocation(config.accuApiKey, config.city)
                    .firstOrNull()?.Key
            } else {
                // Cache the location key to avoid repeated API calls (it counts against quota)
                val cached = sp.getString(KEY_ACCU_LOCATION_KEY, null)
                if (cached != null) return cached
                // No city set, no cached key — can't proceed without geoposition endpoint
                // (geoposition requires a separate paid/enterprise API call in AccuWeather)
                Log.w(TAG, "AccuWeather: set a city name to avoid repeated location lookups")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "AccuWeather location lookup failed", e)
            null
        }
    }

    /** Maps AccuWeather icon codes to WeatherCondition — ported from Breezy's AccuService */
    private fun accuIconToCondition(icon: Int?): WeatherCondition = when (icon) {
        1, 2, 30, 33, 34 -> WeatherCondition.CLEAR
        3, 4, 6, 35, 36, 38 -> WeatherCondition.PARTLY_CLOUDY
        5, 37 -> WeatherCondition.CLOUDY
        7, 8 -> WeatherCondition.CLOUDY
        11 -> WeatherCondition.FOG
        12, 13, 14, 18, 39, 40 -> WeatherCondition.RAIN
        15, 16, 17, 41, 42 -> WeatherCondition.THUNDERSTORM
        19, 20, 21, 22, 23, 24, 31, 43, 44 -> WeatherCondition.SNOW
        25 -> WeatherCondition.HAIL
        26, 29 -> WeatherCondition.SLEET
        32 -> WeatherCondition.WINDY
        else -> WeatherCondition.NA
    }

    // ── Location / city resolution ────────────────────────────────────────────

    /**
     * Resolves coordinates either from the user's manually entered city name
     * (via the provider's own geocoder) or from the device's LocationManager.
     */
    private suspend fun resolveCoords(config: WeatherConfig): Coords? {
        if (config.city.isNotBlank()) {
            return geocodeCity(config.city, config.provider, config.owmApiKey)
        }
        return getDeviceLocation()?.let { Coords(it.latitude, it.longitude) }
    }

    private suspend fun geocodeCity(
        city: String,
        provider: WeatherProvider,
        owmApiKey: String,
    ): Coords? {
        return try {
            when (provider) {
                WeatherProvider.OPEN_METEO, WeatherProvider.PIRATE_WEATHER -> {
                    // Open-Meteo geocoding — free, no key needed
                    val result = openMeteoGeoApi.search(city)
                    result.results?.firstOrNull()?.let { Coords(it.latitude, it.longitude) }
                }
                WeatherProvider.OPEN_WEATHER_MAP -> {
                    if (owmApiKey.isBlank()) return null
                    val results = owmApi.geocode(owmApiKey, city)
                    results.firstOrNull()?.let { r ->
                        val lat = r.lat ?: return null
                        val lon = r.lon ?: return null
                        Coords(lat, lon)
                    }
                }
                WeatherProvider.ACCU_WEATHER -> null // AccuWeather uses its own location key, not coords
                WeatherProvider.NONE -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed for '$city'", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getDeviceLocation(): Location? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService<LocationManager>() ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        for (p in providers) {
            val cached = lm.getLastKnownLocation(p)
            if (cached != null) return cached
        }
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

    // ── Target builder ────────────────────────────────────────────────────────


    /**
     * A minimal FEATURE_WEATHER target with no headerAction.
     * BcSmartspaceCard will show only the date row (IcuDateTextView) when
     * headerAction is null — preserving the card so the launcher layout
     * isn't disrupted when weather data is temporarily unavailable.
     */
    private fun emptyWeatherTarget() = SmartspaceTarget(
        id = "weatherUnavailable",
        headerAction = null,
        score = SmartspaceScores.SCORE_WEATHER,
        featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
    )

    private fun buildTarget(
        id: String,
        condition: WeatherCondition,
        isDay: Boolean,
        tempCelsius: Double,
        summary: String?,
        iconPack: String?,
        unit: TemperatureUnit,
    ) = SmartspaceTarget(
        id = id,
        headerAction = SmartspaceAction(
            id = "${id}Action",
            icon = iconProvider.getIcon(condition, isDay, iconPack),
            title = "",
            subtitle = unit.format(tempCelsius),
            contentDescription = summary ?: condition.toDisplayString(),
        ),
        score = SmartspaceScores.SCORE_WEATHER,
        featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
    )

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "WeatherDataProvider"
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
        private const val OPEN_METEO_GEO_URL = "https://geocoding-api.open-meteo.com/"
        private const val PIRATE_BASE_URL = "https://api.pirateweather.net/"
        private const val OWM_BASE_URL = "https://api.openweathermap.org/"
        private const val ACCU_BASE_URL = "https://dataservice.accuweather.com/"
        private const val OPEN_METEO_CURRENT_FIELDS = "temperature_2m,weather_code,is_day"
        private val LOCATION_TIMEOUT = 15.seconds

        private const val PREFS_NAME = "weather_data_provider_cache"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_IS_DAY = "is_day"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_ACCU_LOCATION_KEY = "accu_location_key"

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
