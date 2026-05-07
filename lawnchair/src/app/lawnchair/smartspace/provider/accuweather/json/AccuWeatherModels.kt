package app.lawnchair.smartspace.provider.accuweather.json

import kotlinx.serialization.Serializable

@Serializable
data class AccuLocationResult(
    val Key: String,
    val LocalizedName: String? = null,
    val Country: AccuLocationArea? = null,
)

@Serializable
data class AccuLocationArea(
    val ID: String? = null,
    val LocalizedName: String? = null,
)

@Serializable
data class AccuCurrentResult(
    val WeatherText: String? = null,
    val WeatherIcon: Int? = null,
    val Temperature: AccuValueContainer? = null,
)

@Serializable
data class AccuValueContainer(
    val Metric: AccuValue? = null,
    val Imperial: AccuValue? = null,
)

@Serializable
data class AccuValue(
    val Value: Double? = null,
)
