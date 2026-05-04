// Physical path: smartspace/provider/openmeteo/OpenMeteoWeatherDataProvider.kt
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
import app.lawnchair.BlankActivity
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.openmeteo.OpenMeteoApi
import app.lawnchair.smartspace.provider.openmeteo.json.OpenMeteoWeatherCurrent
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

class OpenMeteoWeatherDataProvider(context: Context) : SmartspaceDataSource(
    context,
    R.string.smartspace_open_meteo_weather,
    { smartspaceOpenMeteoWeather },
) {
    private val api = buildApi()
    private val iconProvider = WeatherIconProvider(context)

    override val internalTargets = flow {
        while (true) {
            emit(fetchWeather())
            delay(REFRESH_INTERVAL)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun requiresSetup(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) != PackageManager.PERMISSION_GRANTED

    override suspend fun startSetup(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        BlankActivity.startBlankActivityDialog(
            activity,
            intent,
            activity.getString(R.string.smartspace_weather_location_permission_title),
            activity.getString(R.string.smartspace_weather_location_permission_description),
            context.getString(R.string.title_change_settings),
        )
    }

    private suspend fun fetchWeather(): List<SmartspaceTarget> {
        return try {
            val location = getLastKnownLocation()
                ?: return emptyList<SmartspaceTarget>().also {
                    Log.w(TAG, "Location unavailable — skipping Open-Meteo fetch")
                }
            val result = api.getWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                current = CURRENT_FIELDS,
            )
            if (result.error == true) {
                Log.w(TAG, "Open-Meteo API error: ${result.reason}")
                return emptyList()
            }
            val current = result.current ?: return emptyList()
            listOfNotNull(buildTarget(current))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Open-Meteo weather", e)
            emptyList()
        }
    }

    private fun buildTarget(current: OpenMeteoWeatherCurrent): SmartspaceTarget? {
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

    @Suppress("MissingPermission")
    private fun getLastKnownLocation(): android.location.Location? {
        val lm = context.getSystemService<LocationManager>() ?: return null
        return lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }

    companion object {
        private const val TAG = "OpenMeteoProvider"
        private const val BASE_URL = "https://api.open-meteo.com/"
        private const val CURRENT_FIELDS = "temperature_2m,weather_code,is_day"
        private val REFRESH_INTERVAL = 30.minutes

        private fun buildApi(): OpenMeteoApi {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(
                    json.asConverterFactory("application/json; charset=UTF8".toMediaType()),
                )
                .build()
                .create(OpenMeteoApi::class.java)
        }
    }
}
