package app.lawnchair.smartspace.provider.pirateweather

import app.lawnchair.smartspace.provider.pirateweather.json.PirateWeatherForecastResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * PirateWeather API — requires a free API key from https://pirateweather.net/
 * Docs: https://docs.pirateweather.net/en/latest/Specification/
 */
interface PirateWeatherApi {

    @GET("forecast/{apikey}/{lat},{lon}")
    suspend fun getForecast(
        @Path("apikey") apiKey: String,
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        /** Keep `daily` alongside `currently` so we have today's sunrise/sunset for day/night icons. */
        @Query("exclude") exclude: String = "minutely,hourly,alerts",
        @Query("units") units: String = "si",
    ): PirateWeatherForecastResult
}
