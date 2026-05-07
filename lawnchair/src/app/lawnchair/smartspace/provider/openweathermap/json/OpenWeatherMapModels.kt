package app.lawnchair.smartspace.provider.openweathermap.json

import kotlinx.serialization.Serializable

@Serializable
data class OpenWeatherCurrentResult(
    val weather: List<OpenWeatherCondition>? = null,
    val main: OpenWeatherMain? = null,
)

@Serializable
data class OpenWeatherCondition(
    val id: Int? = null,
    val description: String? = null,
    val icon: String? = null,
)

@Serializable
data class OpenWeatherMain(
    val temp: Double? = null,
)

/** Used for city geocoding */
@Serializable
data class OpenWeatherGeoResult(
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val country: String? = null,
)
