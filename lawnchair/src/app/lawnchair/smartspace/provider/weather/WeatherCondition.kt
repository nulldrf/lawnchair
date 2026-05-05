package app.lawnchair.smartspace.provider.weather

enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    MOSTLY_CLOUDY,
    CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    FREEZING_RAIN,
    SLEET,
    SNOW,
    FLURRIES,
    HAIL,
    THUNDERSTORM,
    WINDY,
    NA;

    fun toDisplayString(): String = when (this) {
        CLEAR         -> "Clear"
        MOSTLY_CLEAR  -> "Mostly clear"
        PARTLY_CLOUDY -> "Partly cloudy"
        MOSTLY_CLOUDY -> "Mostly cloudy"
        CLOUDY        -> "Cloudy"
        OVERCAST      -> "Overcast"
        FOG           -> "Fog"
        DRIZZLE       -> "Drizzle"
        RAIN          -> "Rain"
        FREEZING_RAIN -> "Freezing rain"
        SLEET         -> "Sleet"
        SNOW          -> "Snow"
        FLURRIES      -> "Flurries"
        HAIL          -> "Hail"
        THUNDERSTORM  -> "Thunderstorm"
        WINDY         -> "Windy"
        NA            -> ""
    }

    companion object {
        fun fromWmoCode(code: Int?, isDay: Boolean): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MOSTLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53 -> DRIZZLE
            55, 61, 63, 65 -> RAIN
            56, 57, 66, 67 -> FREEZING_RAIN
            71, 73, 75 -> SNOW
            77 -> FLURRIES
            80, 81, 82 -> RAIN
            85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> NA
        }

        fun fromPirateIcon(icon: String?): WeatherCondition = when (icon) {
            "clear-day", "clear-night"                 -> CLEAR
            "partly-cloudy-day", "partly-cloudy-night" -> PARTLY_CLOUDY
            "cloudy"                                   -> CLOUDY
            "rain"                                     -> RAIN
            "sleet"                                    -> SLEET
            "snow"                                     -> SNOW
            "fog"                                      -> FOG
            "wind"                                     -> WINDY
            "thunderstorm"                             -> THUNDERSTORM
            else                                       -> NA
        }
    }
}
