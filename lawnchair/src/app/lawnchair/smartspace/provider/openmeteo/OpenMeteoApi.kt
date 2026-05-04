package app.lawnchair.smartspace.provider.openmeteo

import app.lawnchair.smartspace.provider.openmeteo.json.OpenMeteoWeatherResult
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo free weather API — no API key required.
 * Docs: https://open-meteo.com/en/docs
 */
interface OpenMeteoApi {

    @GET("v1/forecast?timezone=auto&timeformat=unixtime")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
    ): OpenMeteoWeatherResult
}
