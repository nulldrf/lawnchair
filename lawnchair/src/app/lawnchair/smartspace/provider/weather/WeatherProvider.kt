package app.lawnchair.smartspace.provider.weather

import com.android.launcher3.R

enum class WeatherProvider(val nameResId: Int) {
    NONE(R.string.smartspace_weather_provider_none),
    OPEN_METEO(R.string.smartspace_open_meteo_weather),
    PIRATE_WEATHER(R.string.smartspace_pirate_weather);

    companion object {
        fun fromString(value: String): WeatherProvider =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}
