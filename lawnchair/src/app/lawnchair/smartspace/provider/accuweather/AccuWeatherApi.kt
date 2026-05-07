package app.lawnchair.smartspace.provider.accuweather

import app.lawnchair.smartspace.provider.accuweather.json.AccuCurrentResult
import app.lawnchair.smartspace.provider.accuweather.json.AccuLocationResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AccuWeather API — requires a free API key from developer.accuweather.com
 * Two-step: search city → get locationKey → fetch current conditions
 */
interface AccuWeatherApi {

    /** Search city by name, returns list of matching locations */
    @GET("locations/v1/translate")
    suspend fun searchLocation(
        @Query("apikey") apiKey: String,
        @Query("q") query: String,
        @Query("language") language: String = "en-us",
        @Query("details") details: Boolean = false,
        @Query("alias") alias: String = "Always",
    ): List<AccuLocationResult>

    /** Fetch current conditions for a resolved city key */
    @GET("currentconditions/v1/{locationKey}")
    suspend fun getCurrent(
        @Path("locationKey") locationKey: String,
        @Query("apikey") apiKey: String,
        @Query("language") language: String = "en-us",
        @Query("details") details: Boolean = false,
    ): List<AccuCurrentResult>
}
