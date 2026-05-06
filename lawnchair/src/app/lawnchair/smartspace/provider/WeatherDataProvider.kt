// Physical path: smartspace/provider/WeatherDataProvider.kt
package app.lawnchair.smartspace.provider

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
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
                pollingFlow(config.refreshInterval) { fetchOpenMeteo(config.iconPack) }
            WeatherProvider.PIRATE_WEATHER ->
                pollingFlow(config.refreshInterval) { fetchPirateWeather(config.iconPack, config.pirateApiKey) }
        }
    }.flowOn(Dispatchers.IO)

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

    // ── Open-Meteo ────────────────────────────────────────────────────────────

    private suspend fun fetchOpenMeteo(iconPack: String?): List<SmartspaceTarget> {
        return try {
            val loc = getLocation()
                ?: return emptyList<SmartspaceTarget>().also {
                    Log.w(TAG, "No location available for Open-Meteo")
                }
            Log.d(TAG, "Open-Meteo fetching for ${loc.latitude}, ${loc.longitude}")
            val result = openMeteoApi.getWeather(loc.latitude, loc.longitude, OPEN_METEO_CURRENT_FIELDS)
            if (result.error == true) {
                Log.w(TAG, "Open-Meteo API error: ${result.reason}")
                return emptyList()
            }
            listOfNotNull(result.current?.let { buildOpenMeteoTarget(it, iconPack) })
        } catch (e: Exception) {
            Log.e(TAG, "Open-Meteo fetch failed", e)
            emptyList()
        }
    }

    private fun buildOpenMeteoTarget(current: OpenMeteoWeatherCurrent, iconPack: String?): SmartspaceTarget? {
        val temp = current.temperature ?: return null
        val isDay = current.isDay != 0
        val condition = WeatherCondition.fromWmoCode(current.weatherCode, isDay)

        // title must be EMPTY so BcSmartspaceCard attaches the icon to the subtitle row.
        // subtitle holds the temperature — matching the pattern used by SmartspaceWidgetReader.
        // The condition description goes into contentDescription for accessibility only.
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
            if (apiKey.isBlank()) {
                Log.w(TAG, "PirateWeather: API key is blank")
                return emptyList()
            }
            val loc = getLocation()
                ?: return emptyList<SmartspaceTarget>().also {
                    Log.w(TAG, "No location available for PirateWeather")
                }
            Log.d(TAG, "PirateWeather fetching for ${loc.latitude}, ${loc.longitude}")
            val result = pirateApi.getForecast(apiKey, loc.latitude, loc.longitude)
            listOfNotNull(result.currently?.let { buildPirateTarget(it, iconPack) })
        } catch (e: Exception) {
            Log.e(TAG, "PirateWeather fetch failed", e)
            emptyList()
        }
    }

    private fun buildPirateTarget(currently: PirateWeatherCurrently, iconPack: String?): SmartspaceTarget? {
        val temp = currently.temperature ?: return null
        val isDay = currently.icon?.endsWith("-night") == false
        val condition = WeatherCondition.fromPirateIcon(currently.icon)
        val conditionText = currently.summary ?: condition.toDisplayString()

        // Same pattern: empty title, temperature in subtitle, condition in contentDescription.
        return SmartspaceTarget(
            id = "pirateWeather",
            headerAction = SmartspaceAction(
                id = "pirateWeatherAction",
                icon = iconProvider.getIcon(condition, isDay, iconPack),
                title = "",
                subtitle = "${temp.toInt()}°C",
                contentDescription = conditionText,
            ),
            score = SmartspaceScores.SCORE_WEATHER,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
        )
    }

    // ── Location ──────────────────────────────────────────────────────────────

    @Suppress("MissingPermission")
    private suspend fun getLocation(): Location? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Location permission not granted")
            return null
        }
        val lm = context.getSystemService<LocationManager>() ?: return null

        // Fast path — use any cached fix
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        for (p in providers) {
            val cached = lm.getLastKnownLocation(p)
            if (cached != null) {
                Log.d(TAG, "Using cached location from $p: ${cached.latitude}, ${cached.longitude}")
                return cached
            }
        }
        Log.d(TAG, "No cached location — requesting fresh fix")

        // Slow path — fresh single update with a dedicated HandlerThread (has its own Looper)
        for (provider in providers.filter { lm.isProviderEnabled(it) }) {
            val loc = requestFreshLocation(lm, provider)
            if (loc != null) {
                Log.d(TAG, "Got fresh fix from $provider: ${loc.latitude}, ${loc.longitude}")
                return loc
            }
        }
        Log.w(TAG, "All location providers timed out or unavailable")
        return null
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun requestFreshLocation(lm: LocationManager, provider: String): Location? =
        withTimeoutOrNull(LOCATION_TIMEOUT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                callbackFlow {
                    val signal = CancellationSignal()
                    lm.getCurrentLocation(
                        provider,
                        signal,
                        Executors.newSingleThreadExecutor(),
                    ) { location ->
                        trySend(location)
                        close()
                    }
                    awaitClose { signal.cancel() }
                }.first()
            } else {
                callbackFlow<Location?> {
                    val thread = HandlerThread("WeatherLocation-$provider").also { it.start() }
                    val handler = Handler(thread.looper)
                    val listener = LocationListener { location ->
                        trySend(location)
                        close()
                    }
                    lm.requestSingleUpdate(provider, listener, thread.looper)
                    awaitClose {
                        lm.removeUpdates(listener)
                        thread.quit()
                    }
                }.first()
            }
        }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    // ── Polling ───────────────────────────────────────────────────────────────

    private fun pollingFlow(
        intervalMinutes: Long,
        fetch: suspend () -> List<SmartspaceTarget>,
    ) = flow {
        while (true) {
            emit(fetch())
            delay(intervalMinutes.minutes)
        }
    }

    companion object {
        private const val TAG = "WeatherDataProvider"
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
        private const val PIRATE_BASE_URL = "https://api.pirateweather.net/"
        private const val OPEN_METEO_CURRENT_FIELDS = "temperature_2m,weather_code,is_day"
        private val LOCATION_TIMEOUT = 15.seconds

        val REFRESH_INTERVAL_OPTIONS = listOf(15L, 30L, 60L, 180L)

        private fun buildRetrofit(baseUrl: String): Retrofit {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(
                    json.asConverterFactory("application/json; charset=UTF8".toMediaType()),
                )
                .build()
        }
    }
}
