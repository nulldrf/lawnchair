package app.lawnchair.smartspace.provider.weather

import com.android.launcher3.R

enum class TemperatureUnit(val nameResId: Int, val suffix: String) {
    CELSIUS(R.string.smartspace_weather_unit_celsius, "°C"),
    FAHRENHEIT(R.string.smartspace_weather_unit_fahrenheit, "°F"),
    KELVIN(R.string.smartspace_weather_unit_kelvin, "K");

    fun format(celsius: Double): String = when (this) {
        CELSIUS    -> "${celsius.toInt()}$suffix"
        FAHRENHEIT -> "${(celsius * 9.0 / 5.0 + 32.0).toInt()}$suffix"
        KELVIN     -> "${(celsius + 273.15).toInt()}$suffix"
    }

    companion object {
        fun fromString(value: String): TemperatureUnit =
            entries.firstOrNull { it.name == value } ?: CELSIUS
    }
}
