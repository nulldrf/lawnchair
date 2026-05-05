// Physical path: smartspace/provider/WeatherDataProvider.kt
package app.lawnchair.smartspace.provider

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration.Companion.minutes

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

    override val internalTargets = prefs.smartspaceWeatherProvider.get()
        .flatMapLatest { provider ->
            when (provider) {
                WeatherProvider.NONE -> flowOf(emptyList())
                WeatherProvider.OPEN_METEO -> pollingFlow { fetchOpenMeteo() }
                WeatherProvider.PIRATE_WEATHER -> pollingFlow { fetchPirateWeather() }
            }
        }
        .flowOn(Dispatchers.IO)

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

    private suspend fun fetchOpenMeteo(): List<SmartspaceTarget> {
        return try {
            val loc = getLastKnownLocation()
                ?: return emptyList<SmartspaceTarget>().also { Log.w(TAG, "No location for Open-Meteo") }
            val result = openMeteoApi.getWeather(loc.latitude, loc.longitude, OPEN_METEO_CURRENT_FIELDS)
            if (result.error == true) { Log.w(TAG, "Open-Meteo error: ${result.reason}"); return emptyList() }
            listOfNotNull(result.current?.let { buildOpenMeteoTarget(it) })
        } catch (e: Exception) {
            Log.e(TAG, "Open-Meteo fetch failed", e)
            emptyList()
        }
    }

    private fun buildOpenMeteoTarget(current: OpenMeteoWeatherCurrent): SmartspaceTarget? {
        val temp = current.temperature ?: return null
        val isDay = current.isDay != 0
        val condition = WeatherCondition.fromWmoCode(current.weatherCode, isDay)
        return SmartspaceTarget(
            id = "openMeteoWeather",
            headerAction = SmartspaceAction(
                id = "openMeteoWeatherAction",
                icon = iconProvider.getIcon(condition, isDay),
                title = "${temp.toInt()}°C",
                subtitle = condition.toDisplayString(),
            ),
            score = SmartspaceScores.SCORE_WEATHER,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
        )
    }

    // ── PirateWeather ─────────────────────────────────────────────────────────

    private suspend fun fetchPirateWeather(): List<SmartspaceTarget> {
        return try {
            val apiKey = prefs.pirateWeatherApiKey.get().first()
            if (apiKey.isBlank()) return emptyList()
            val loc = getLastKnownLocation()
                ?: return emptyList<SmartspaceTarget>().also { Log.w(TAG, "No location for PirateWeather") }
            val result = pirateApi.getForecast(apiKey, loc.latitude, loc.longitude)
            listOfNotNull(result.currently?.let { buildPirateTarget(it) })
        } catch (e: Exception) {
            Log.e(TAG, "PirateWeather fetch failed", e)
            emptyList()
        }
    }

    private fun buildPirateTarget(currently: PirateWeatherCurrently): SmartspaceTarget? {
        val temp = currently.temperature ?: return null
        val isDay = currently.icon?.endsWith("-night") == false
        val condition = WeatherCondition.fromPirateIcon(currently.icon)
        return SmartspaceTarget(
            id = "pirateWeather",
            headerAction = SmartspaceAction(
                id = "pirateWeatherAction",
                icon = iconProvider.getIcon(condition, isDay),
                title = "${temp.toInt()}°C",
                subtitle = currently.summary ?: condition.toDisplayString(),
            ),
            score = SmartspaceScores.SCORE_WEATHER,
            featureType = SmartspaceTarget.FeatureType.FEATURE_WEATHER,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun getLastKnownLocation(): android.location.Location? {
        val lm = context.getSystemService<LocationManager>() ?: return null
        return lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }

    private fun pollingFlow(fetch: suspend () -> List<SmartspaceTarget>) = flow {
        while (true) {
            emit(fetch())
            delay(REFRESH_INTERVAL)
        }
    }

    companion object {
        private const val TAG = "WeatherDataProvider"
        private const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com/"
        private const val PIRATE_BASE_URL = "https://api.pirateweather.net/"
        private const val OPEN_METEO_CURRENT_FIELDS = "temperature_2m,weather_code,is_day"
        private val REFRESH_INTERVAL = 30.minutes

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
