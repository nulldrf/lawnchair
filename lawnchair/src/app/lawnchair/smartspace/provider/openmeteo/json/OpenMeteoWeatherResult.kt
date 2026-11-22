package app.lawnchair.smartspace.provider.openmeteo.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoWeatherResult(
    val current: OpenMeteoWeatherCurrent? = null,
    val error: Boolean? = null,
    val reason: String? = null,
)

@Serializable
data class OpenMeteoWeatherCurrent(
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("is_day") val isDay: Int? = null,
    val time: Long? = null,
)

@Serializable
data class OpenMeteoGeocodingResult(
    val results: List<OpenMeteoGeocodingLocation>? = null,
)

@Serializable
data class OpenMeteoGeocodingLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val country: String? = null,
)
