package app.lawnchair.smartspace.provider.openweathermap

import app.lawnchair.smartspace.provider.openweathermap.json.OpenWeatherCurrentResult
import app.lawnchair.smartspace.provider.openweathermap.json.OpenWeatherGeoResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap API — requires a free API key from openweathermap.org
 * Docs: https://openweathermap.org/current
 */
interface OpenWeatherMapApi {

    /** Geocoding — search city by name */
    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("appid") apiKey: String,
        @Query("q") query: String,
        @Query("limit") limit: Int = 1,
    ): List<OpenWeatherGeoResult>

    /** Current weather by coordinates */
    @GET("data/2.5/weather")
    suspend fun getCurrent(
        @Query("appid") apiKey: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String = "metric",
    ): OpenWeatherCurrentResult
}
