// Physical path: smartspace/provider/WeatherDataProvider.kt
package app.lawnchair.smartspace.provider

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.HandlerThread
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.accuweather.AccuWeatherApi
import app.lawnchair.smartspace.provider.openmeteo.OpenMeteoApi
import app.lawnchair.smartspace.provider.openmeteo.OpenMeteoGeocodingApi
import app.lawnchair.smartspace.provider.openweathermap.OpenWeatherMapApi
import app.lawnchair.smartspace.provider.pirateweather.PirateWeatherApi
import app.lawnchair.smartspace.provider.pirateweather.json.PirateWeatherForecastResult
import app.lawnchair.smartspace.provider.weather.TemperatureUnit
import app.lawnchair.smartspace.provider.weather.WeatherCondition
import app.lawnchair.smartspace.provider.weather.WeatherIconProvider
import app.lawnchair.smartspace.provider.weather.WeatherProvider
import com.android.launcher3.R
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
class WeatherDataProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_weather_source,
        { smartspaceWeatherEnabled },
    ) {
    // Emit emptyList() when disabled so we never duplicate the date card owned by
    // SmartspaceWidgetReader / SmartspaceProvider's single fallback.
    override val disabledTargets get() = emptyList<SmartspaceTarget>()
    override suspend fun requiresSetup() = false

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
            city = partial.city.trim(),
            pirateApiKey = partial.pirateApiKey.trim(),
            owmApiKey = owmKey.trim(),
            accuApiKey = accuKey.trim(),
            refreshInterval = interval.coerceAtLeast(15L),
        )
    }.distinctUntilChanged()

    override val internalTargets = configFlow.flatMapLatest { config ->
        when (config.provider) {
            WeatherProvider.NONE -> flowOf(emptyList())

            WeatherProvider.OPEN_METEO ->
                weatherFlow(config) { fetchOpenMeteo(config) }

            WeatherProvider.PIRATE_WEATHER ->
                weatherFlow(config) { fetchPirateWeather(config) }

            WeatherProvider.OPEN_WEATHER_MAP ->
                weatherFlow(config) { fetchOpenWeatherMap(config) }

            WeatherProvider.ACCU_WEATHER ->
                weatherFlow(config) { fetchAccuWeather(config) }
        }
    }.flowOn(Dispatchers.IO)

    fun refreshNow() {
        restart()
    }

    // ── Polling flow with cache & retry backoff ──────────────────────────────

    private fun weatherFlow(
        config: WeatherConfig,
        fetch: suspend () -> List<SmartspaceTarget>,
    ) = flow {
        // Always emit immediately so the date card is never blank, even on first run
        // with no cache and no internet. Re-renders cached temperature with the latest
        // unit & icon pack immediately when those settings change.
        emit(buildCachedTargets(config).ifEmpty { listOf(emptyWeatherTarget()) })

        var consecutiveFailures = 0
        while (true) {
            val fresh = fetch()
            if (fresh.isNotEmpty()) {
                consecutiveFailures = 0
                emit(fresh)
                delay(config.refreshInterval.minutes)
            } else {
                consecutiveFailures++
                emit(buildCachedTargets(config).ifEmpty { listOf(emptyWeatherTarget()) })
                // Short progressive retry backoff (1m -> 2m -> 5m, capped at refreshInterval)
                // so temporary boot/network/GPS failures recover quickly.
                val retryMinutes = when (consecutiveFailures) {
                    1 -> 1L
                    2 -> 2L
                    else -> 5L
                }.coerceAtMost(config.refreshInterval)
                delay(retryMinutes.minutes)
            }
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────

    private fun saveCache(
        config: WeatherConfig,
        tempCelsius: Double,
        condition: WeatherCondition,
        isDay: Boolean,
        summary: String?,
    ) {
        sp.edit()
            .putFloat(KEY_TEMP, tempCelsius.toFloat())
            .putString(KEY_CONDITION, condition.name)
            .putBoolean(KEY_IS_DAY, isDay)
            .putString(KEY_SUMMARY, summary)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_PROVIDER, config.provider.name)
            .putString(KEY_CITY, config.city.lowercase(Locale.ROOT))
            .apply()
    }

    private fun buildCachedTargets(config: WeatherConfig): List<SmartspaceTarget> {
        if (!sp.contains(KEY_TEMP)) return emptyList()
        val cachedProvider = sp.getString(KEY_PROVIDER, null)
        val cachedCity = sp.getString(KEY_CITY, null)
        if (cachedProvider != null && cachedProvider != config.provider.name) return emptyList()
        if (cachedCity != null && cachedCity != config.city.lowercase(Locale.ROOT)) return emptyList()

        val timestamp = sp.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp > 0L && System.currentTimeMillis() - timestamp > MAX_CACHE_AGE_MS) {
            return emptyList()
        }

        val temp = sp.getFloat(KEY_TEMP, Float.NaN)
        if (temp.isNaN()) return emptyList()
        val conditionName = sp.getString(KEY_CONDITION, null) ?: return emptyList()
        val condition = runCatching { WeatherCondition.valueOf(conditionName) }
            .getOrDefault(WeatherCondition.NA)
        val isDay = sp.getBoolean(KEY_IS_DAY, true)
        val summary = sp.getString(KEY_SUMMARY, null)
        return listOf(
            buildTarget(
                id = "weatherCached",
                condition = condition,
                isDay = isDay,
                tempCelsius = temp.toDouble(),
                summary = summary,
                iconPack = config.iconPack,
                unit = config.unit,
            ),
        )
    }

    // ── Open-Meteo ────────────────────────────────────────────────────────────

    private suspend fun fetchOpenMeteo(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            val coords = resolveCoords(config) ?: return emptyList()
            val result = openMeteoApi.getWeather(coords.lat, coords.lon, OPEN_METEO_CURRENT_FIELDS)
            if (result.error == true) {
                Log.w(TAG, "Open-Meteo error: ${result.reason}")
                return emptyList()
            }
            val current = result.current ?: return emptyList()
            val temp = current.temperature ?: return emptyList()
            val isDay = current.isDay != 0
            val condition = WeatherCondition.fromWmoCode(current.weatherCode, isDay)
            saveCache(config, temp, condition, isDay, null)
            listOf(buildTarget("openMeteoWeather", condition, isDay, temp, null, config.iconPack, config.unit))
        } catch (e: Exception) {
            Log.e(TAG, "Open-Meteo fetch failed", e)
            emptyList()
        }
    }

    // ── PirateWeather ─────────────────────────────────────────────────────────

    private suspend fun fetchPirateWeather(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            if (config.pirateApiKey.isBlank()) return emptyList()
            val coords = resolveCoords(config) ?: return emptyList()
            val result = pirateApi.getForecast(config.pirateApiKey, coords.lat, coords.lon)
            val currently = result.currently ?: return emptyList()
            val temp = currently.temperature ?: return emptyList()
            val isDay = resolvePirateIsDay(result)
            val condition = WeatherCondition.fromPirateIcon(currently.icon)
            saveCache(config, temp, condition, isDay, currently.summary)
            listOf(buildTarget("pirateWeather", condition, isDay, temp, currently.summary, config.iconPack, config.unit))
        } catch (e: Exception) {
            Log.e(TAG, "PirateWeather fetch failed", e)
            emptyList()
        }
    }

    private fun resolvePirateIsDay(result: PirateWeatherForecastResult): Boolean {
        val icon = result.currently?.icon
        if (icon != null) {
            if (icon.endsWith("-night")) return false
            if (icon.endsWith("-day")) return true
        }
        val nowSec = result.currently?.time ?: (System.currentTimeMillis() / 1000L)
        val today = result.daily?.data?.firstOrNull()
        val sunrise = today?.sunriseTime
        val sunset = today?.sunsetTime
        if (sunrise != null && sunset != null && sunrise < sunset) {
            return nowSec in sunrise..sunset
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour in 6..18
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
            saveCache(config, temp, condition, isDay, description)
            listOf(buildTarget("owmWeather", condition, isDay, temp, description, config.iconPack, config.unit))
        } catch (e: Exception) {
            Log.e(TAG, "OWM fetch failed", e)
            emptyList()
        }
    }

    /** Maps OWM weather IDs to WeatherCondition — ported from Breezy's OpenWeatherService */
    private fun owmCodeToCondition(id: Int?): WeatherCondition = when (id) {
        in 200..232 -> WeatherCondition.THUNDERSTORM
        in 300..321 -> WeatherCondition.DRIZZLE
        in 500..504, in 520..531 -> WeatherCondition.RAIN
        511 -> WeatherCondition.FREEZING_RAIN
        in 600..602, in 620..622 -> WeatherCondition.SNOW
        in 611..616 -> WeatherCondition.SLEET
        701, 711, 721, 731, 741, 751, 761, 762 -> WeatherCondition.FOG
        771, 781 -> WeatherCondition.WINDY
        800 -> WeatherCondition.CLEAR
        801, 802 -> WeatherCondition.PARTLY_CLOUDY
        803 -> WeatherCondition.MOSTLY_CLOUDY
        804 -> WeatherCondition.OVERCAST
        else -> WeatherCondition.NA
    }

    // ── AccuWeather ───────────────────────────────────────────────────────────

    private suspend fun fetchAccuWeather(config: WeatherConfig): List<SmartspaceTarget> {
        return try {
            if (config.accuApiKey.isBlank()) return emptyList()

            // AccuWeather requires a location key first — resolve from city name or GPS coords
            val locationKey = resolveAccuLocationKey(config) ?: return emptyList()

            val results = accuApi.getCurrent(locationKey, config.accuApiKey)
            val current = results.firstOrNull() ?: return emptyList()
            // AccuWeather always returns metric in .Metric field
            val temp = current.Temperature?.Metric?.Value ?: return emptyList()
            val condition = accuIconToCondition(current.WeatherIcon)
            val isDay = current.IsDayTime ?: ((current.WeatherIcon ?: 0) <= 32)
            val description = current.WeatherText
            saveCache(config, temp, condition, isDay, description)
            listOf(buildTarget("accuWeather", condition, isDay, temp, description, config.iconPack, config.unit))
        } catch (e: Exception) {
            Log.e(TAG, "AccuWeather fetch failed", e)
            emptyList()
        }
    }

    /**
     * Resolves and caches an AccuWeather location key.
     * - If city is set: uses AccuWeather's city text search (`locations/v1/cities/search`)
     * - If no city: resolves GPS coordinates via geoposition search (`locations/v1/cities/geoposition/search`)
     * Results are cached by normalized query so repeated refreshes do not burn API quota.
     */
    private suspend fun resolveAccuLocationKey(config: WeatherConfig): String? {
        return try {
            if (config.city.isNotBlank()) {
                val queryKey = "city:${config.city.lowercase(Locale.ROOT)}"
                getCachedAccuLocationKey(queryKey)?.let { return it }

                val key = accuApi.searchLocation(config.accuApiKey, config.city)
                    .firstOrNull()?.Key
                if (!key.isNullOrBlank()) {
                    saveAccuLocationKey(queryKey, key)
                }
                key
            } else {
                val loc = getDeviceLocation() ?: return sp.getString(KEY_ACCU_LOCATION_KEY, null)
                // Round to ~1 km so tiny GPS jitter reuses the cached location key.
                val latKey = (loc.latitude * 100.0).roundToInt() / 100.0
                val lonKey = (loc.longitude * 100.0).roundToInt() / 100.0
                val queryKey = "geo:$latKey,$lonKey"
                getCachedAccuLocationKey(queryKey)?.let { return it }

                val key = accuApi.searchByGeoposition(config.accuApiKey, "${loc.latitude},${loc.longitude}").Key
                if (!key.isNullOrBlank()) {
                    saveAccuLocationKey(queryKey, key)
                }
                key
            }
        } catch (e: Exception) {
            Log.e(TAG, "AccuWeather location lookup failed", e)
            null
        }
    }

    private fun getCachedAccuLocationKey(queryKey: String): String? {
        val cachedQuery = sp.getString(KEY_ACCU_LOCATION_QUERY, null)
        if (cachedQuery != queryKey) return null
        return sp.getString(KEY_ACCU_LOCATION_KEY, null)
    }

    private fun saveAccuLocationKey(queryKey: String, locationKey: String) {
        sp.edit()
            .putString(KEY_ACCU_LOCATION_QUERY, queryKey)
            .putString(KEY_ACCU_LOCATION_KEY, locationKey)
            .apply()
    }

    /**
     * Maps all 44 AccuWeather icon codes to WeatherCondition.
     * Reference: https://developer.accuweather.com/weather-icons
     */
    private fun accuIconToCondition(icon: Int?): WeatherCondition = when (icon) {
        1 -> WeatherCondition.CLEAR

        // Sunny
        2 -> WeatherCondition.MOSTLY_CLEAR

        // Mostly Sunny
        3, 4 -> WeatherCondition.PARTLY_CLOUDY

        // Partly Sunny / Intermittent Clouds
        5 -> WeatherCondition.MOSTLY_CLOUDY

        // Hazy Sunshine
        6 -> WeatherCondition.MOSTLY_CLOUDY

        // Mostly Cloudy
        7, 8 -> WeatherCondition.CLOUDY

        // Cloudy / Dreary
        11 -> WeatherCondition.FOG

        // Fog
        12, 13, 14, 18 -> WeatherCondition.RAIN

        // Showers / Rain
        15, 16, 17 -> WeatherCondition.THUNDERSTORM

        // T-Storms
        19, 20, 21 -> WeatherCondition.FLURRIES

        // Flurries
        22, 23 -> WeatherCondition.SNOW

        // Snow / Mostly Cloudy w/ Snow
        24, 25, 29 -> WeatherCondition.SLEET

        // Ice / Sleet / Rain and Snow
        26 -> WeatherCondition.FREEZING_RAIN

        // Freezing Rain
        30, 31 -> WeatherCondition.CLEAR

        // Hot / Cold
        32 -> WeatherCondition.WINDY

        // Windy
        33 -> WeatherCondition.CLEAR

        // Clear (night)
        34 -> WeatherCondition.MOSTLY_CLEAR

        // Mostly Clear (night)
        35, 36 -> WeatherCondition.PARTLY_CLOUDY

        // Partly Cloudy / Intermittent Clouds (night)
        37, 38 -> WeatherCondition.MOSTLY_CLOUDY

        // Hazy Moonlight / Mostly Cloudy (night)
        39, 40 -> WeatherCondition.RAIN

        // Showers (night)
        41, 42 -> WeatherCondition.THUNDERSTORM

        // T-Storms (night)
        43, 44 -> WeatherCondition.SNOW

        // Flurries / Snow (night)
        else -> WeatherCondition.NA
    }

    // ── Location / city resolution ────────────────────────────────────────────

    /**
     * Resolves coordinates either from the user's manually entered city name
     * (via the provider's own geocoder, cached locally) or from the device's LocationManager.
     */
    private suspend fun resolveCoords(config: WeatherConfig): Coords? {
        if (config.city.isNotBlank()) {
            return geocodeCityCached(config.city, config.provider, config.owmApiKey)
        }
        return getDeviceLocation()?.let { Coords(it.latitude, it.longitude) }
    }

    private suspend fun geocodeCityCached(
        city: String,
        provider: WeatherProvider,
        owmApiKey: String,
    ): Coords? {
        val normalizedCity = city.lowercase(Locale.ROOT)
        if (sp.getString(KEY_GEO_CITY, null) == normalizedCity &&
            sp.contains(KEY_GEO_LAT) &&
            sp.contains(KEY_GEO_LON)
        ) {
            val lat = Double.fromBits(sp.getLong(KEY_GEO_LAT, 0L))
            val lon = Double.fromBits(sp.getLong(KEY_GEO_LON, 0L))
            if (!lat.isNaN() && !lon.isNaN()) {
                return Coords(lat, lon)
            }
        }
        val coords = geocodeCity(city, provider, owmApiKey) ?: return null
        sp.edit()
            .putString(KEY_GEO_CITY, normalizedCity)
            .putLong(KEY_GEO_LAT, coords.lat.toRawBits())
            .putLong(KEY_GEO_LON, coords.lon.toRawBits())
            .apply()
        return coords
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

                WeatherProvider.ACCU_WEATHER -> null

                // AccuWeather uses its own location key
                WeatherProvider.NONE -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding failed for '$city'", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getDeviceLocation(): Location? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted and no city configured")
            return null
        }
        val lm = context.getSystemService<LocationManager>() ?: return null

        val cachedProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        // Pick the freshest last-known location across all providers.
        val bestCached = cachedProviders
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }

        if (bestCached != null && System.currentTimeMillis() - bestCached.time <= FRESH_LOCATION_MAX_AGE_MS) {
            return bestCached
        }

        val activeProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { provider -> runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false) }

        for (provider in activeProviders) {
            val fresh = requestFreshLocation(lm, provider)
            if (fresh != null) return fresh
        }

        // Fall back to older last-known fix if requesting a fresh fix timed out.
        return bestCached
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestFreshLocation(lm: LocationManager, provider: String): Location? = withTimeoutOrNull(LOCATION_TIMEOUT) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            callbackFlow {
                val signal = CancellationSignal()
                try {
                    lm.getCurrentLocation(provider, signal, locationExecutor) { loc ->
                        trySend(loc)
                        close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "getCurrentLocation($provider) failed", e)
                    trySend(null)
                    close()
                }
                awaitClose { signal.cancel() }
            }.first()
        } else {
            callbackFlow<Location?> {
                val thread = HandlerThread("WeatherLoc-$provider").also { it.start() }
                val listener = LocationListener { loc ->
                    trySend(loc)
                    close()
                }
                try {
                    lm.requestSingleUpdate(provider, listener, thread.looper)
                } catch (e: Exception) {
                    Log.w(TAG, "requestSingleUpdate($provider) failed", e)
                    trySend(null)
                    close()
                }
                awaitClose {
                    runCatching { lm.removeUpdates(listener) }
                    thread.quitSafely()
                }
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

    fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
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
        private val MAX_CACHE_AGE_MS = 6.hours.inWholeMilliseconds
        private val FRESH_LOCATION_MAX_AGE_MS = 30.minutes.inWholeMilliseconds

        private const val PREFS_NAME = "weather_data_provider_cache"
        private const val KEY_TEMP = "temp"
        private const val KEY_CONDITION = "condition"
        private const val KEY_IS_DAY = "is_day"
        private const val KEY_SUMMARY = "summary"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_CITY = "city"
        private const val KEY_GEO_CITY = "geo_city"
        private const val KEY_GEO_LAT = "geo_lat"
        private const val KEY_GEO_LON = "geo_lon"
        private const val KEY_ACCU_LOCATION_QUERY = "accu_location_query"
        private const val KEY_ACCU_LOCATION_KEY = "accu_location_key"

        val REFRESH_INTERVAL_OPTIONS = listOf(15L, 30L, 60L, 180L)

        private val sharedJson = Json { ignoreUnknownKeys = true }

        private val sharedHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val req = chain.request().newBuilder()
                        .header("User-Agent", "Lawnchair/16 (Android)")
                        .build()
                    chain.proceed(req)
                }
                .build()
        }

        private val locationExecutor by lazy {
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "WeatherLocation").apply { isDaemon = true }
            }
        }

        private fun buildRetrofit(baseUrl: String): Retrofit {
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(sharedHttpClient)
                .addConverterFactory(sharedJson.asConverterFactory("application/json; charset=UTF8".toMediaType()))
                .build()
        }
    }
}
