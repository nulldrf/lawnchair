package app.lawnchair.smartspace.provider.pirateweather.json

import kotlinx.serialization.Serializable

@Serializable
data class PirateWeatherForecastResult(
    val currently: PirateWeatherCurrently? = null,
)

@Serializable
data class PirateWeatherCurrently(
    val time: Long,
    /** Dark Sky / PirateWeather icon string e.g. "clear-day", "rain", "snow" */
    val icon: String? = null,
    val summary: String? = null,
    val temperature: Double? = null,
)
