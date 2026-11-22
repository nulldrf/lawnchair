package app.lawnchair.smartspace.provider.pirateweather.json

import kotlinx.serialization.Serializable

@Serializable
data class PirateWeatherForecastResult(
    val currently: PirateWeatherCurrently? = null,
    val daily: PirateWeatherDaily? = null,
)

@Serializable
data class PirateWeatherCurrently(
    val time: Long? = null,
    /** Dark Sky / PirateWeather icon string e.g. "clear-day", "rain", "snow" */
    val icon: String? = null,
    val summary: String? = null,
    val temperature: Double? = null,
)

@Serializable
data class PirateWeatherDaily(
    val data: List<PirateWeatherDailyData>? = null,
)

@Serializable
data class PirateWeatherDailyData(
    val sunriseTime: Long? = null,
    val sunsetTime: Long? = null,
)
