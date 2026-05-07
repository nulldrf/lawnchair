package app.lawnchair.smartspace.provider.openmeteo

import app.lawnchair.smartspace.provider.openmeteo.json.OpenMeteoGeocodingResult
import app.lawnchair.smartspace.provider.openmeteo.json.OpenMeteoWeatherResult
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/forecast?timezone=auto&timeformat=unixtime")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
    ): OpenMeteoWeatherResult
}

/** Separate interface — different base URL (geocoding.open-meteo.com) */
interface OpenMeteoGeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
    ): OpenMeteoGeocodingResult
}
