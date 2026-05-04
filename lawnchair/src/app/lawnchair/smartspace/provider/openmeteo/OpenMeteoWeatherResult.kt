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
    @SerialName("temperature_2m") val temperature: Double?,
    @SerialName("weather_code") val weatherCode: Int?,
    /** 1 = daytime, 0 = night — used for icon day/night variant selection. */
    @SerialName("is_day") val isDay: Int?,
    val time: Long,
)
