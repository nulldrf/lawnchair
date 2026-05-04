// Physical path: smartspace/provider/pirateweather/PirateWeatherDataProvider.kt
// Package must match the sealed class to allow inheritance.
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
import androidx.preference.PreferenceManager
import app.lawnchair.BlankActivity
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.pirateweather.PirateWeatherApi
import app.lawnchair.smartspace.provider.pirateweather.json.PirateWeatherCurrently
import app.lawnchair.smartspace.provider.weather.WeatherCondition
import app.lawnchair.smartspace.provider.weather.WeatherIconProvider
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration.Companion.minutes

class PirateWeatherDataProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_pirate_weather,
    { smartspacePirateWeather },
) {
    private val api = buildApi()
    private val iconProvider = WeatherIconProvider(context)

    override val internalTargets = flow {
        while (true) {
            emit(fetchWeather())
            delay(REFRESH_INTERVAL)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun requiresSetup(): Boolean {
        val noPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED
        val noKey = getApiKey().isBlank()
        return noPermission || noKey
    }

    override suspend fun startSetup(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        val (title, desc) = if (getApiKey().isBlank()) {
            activity.getString(R.string.smartspace_pirate_weather_api_key_title) to
                activity.getString(R.string.smartspace_pirate_weather_api_key_description)
        } else {
            activity.getString(R.string.smartspace_weather_location_permission_title) to
                activity.getString(R.string.smartspace_weather_location_permission_description)
        }
        BlankActivity.startBlankActivityDialog(
            activity,
            intent,
            title,
            desc,
            context.getString(R.string.title_change_settings),
        )
    }

    private suspend fun fetchWeather(): List<SmartspaceTarget> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) return emptyList()
            val location = getLastKnownLocation()
                ?: return emptyList<SmartspaceTarget>().also {
                    Log.w(TAG, "Location unavailable — skipping PirateWeather fetch")
                }
            val result = api.getForecast(
                apiKey = apiKey,
                lat = location.latitude,
                lon = location.longitude,
            )
            val currently = result.currently ?: return emptyList()
            listOfNotNull(buildTarget(currently))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch PirateWeather forecast", e)
            emptyList()
        }
    }

    private fun buildTarget(currently: PirateWeatherCurrently): SmartspaceTarget? {
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

    private fun getApiKey(): String =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_KEY_API_KEY, "") ?: ""

    @Suppress("MissingPermission")
    private fun getLastKnownLocation(): android.location.Location? {
        val lm = context.getSystemService<LocationManager>() ?: return null
        return lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }

    companion object {
        private const val TAG = "PirateWeatherProvider"
        private const val BASE_URL = "https://api.pirateweather.net/"
        const val PREF_KEY_API_KEY = "pref_pirate_weather_api_key"
        private val REFRESH_INTERVAL = 30.minutes

        private fun buildApi(): PirateWeatherApi {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(
                    json.asConverterFactory("application/json; charset=UTF8".toMediaType()),
                )
                .build()
                .create(PirateWeatherApi::class.java)
        }
    }
}
