package com.shilapi.xcertplay

import android.content.res.Configuration
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Default screen brightness value (out of 255) at or below which the display counts as night. */
internal const val DEFAULT_BRIGHTNESS_NIGHT_THRESHOLD = 51

/**
 * Returns the explicit night state carried by [uiMode], or null when the head unit reports
 * UI_MODE_NIGHT_UNDEFINED so callers keep their previous state instead of flipping to light.
 */
internal fun nightModeOrNull(uiMode: Int): Boolean? =
    when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_YES -> true
        Configuration.UI_MODE_NIGHT_NO -> false
        else -> null
    }

/** Where the app decides whether CarPlay should render its dark (night) interface. */
enum class DarkModeSource {
    /** Follow the head unit day/night UI mode (existing uiMode polling). */
    AUTO_UI_MODE,

    /** Read the screen brightness and treat dim screens as night. */
    AUTO_BRIGHTNESS,

    /** Derive night from sunrise/sunset at the current location. */
    SUN_SCHEDULE,

    /** Always day. */
    FORCE_DAY,

    /** Always night. */
    FORCE_NIGHT,
    ;

    /** Stable name used for SharedPreferences storage. */
    fun persistedName(): String = name

    companion object {
        /** Parses a persisted name, falling back to [AUTO_UI_MODE] when null or unknown. */
        fun fromPersistedName(name: String?): DarkModeSource =
            entries.firstOrNull { it.name == name } ?: AUTO_UI_MODE
    }
}

/**
 * Returns true when [brightness] (Settings.System.SCREEN_BRIGHTNESS, 0-255) is at or below
 * [threshold], i.e. the screen is dim enough to count as night.
 */
internal fun isNightByBrightness(brightness: Int, threshold: Int = DEFAULT_BRIGHTNESS_NIGHT_THRESHOLD): Boolean =
    brightness <= threshold

/**
 * Returns true when [epochMillis] falls between today's sunset and tomorrow's sunrise, evaluated
 * in [timeZone] at [latitude]/[longitude]. Locations inside the polar circles report all-day
 * night or all-day day during polar night / polar day. Uses the NOAA solar approximation
 * (accuracy within a few minutes), far better than the poll interval needs.
 */
internal fun isNightBySunSchedule(
    epochMillis: Long,
    latitude: Double,
    longitude: Double,
    timeZone: TimeZone,
): Boolean {
    val day = Calendar.getInstance(timeZone).apply { timeInMillis = epochMillis }
    val dayOfYear = day.get(Calendar.DAY_OF_YEAR)
    val localMidnightMillis = day.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val utcOffsetMillis = timeZone.getOffset(epochMillis).toLong()

    // cosH > 1: sun never rises that day (polar night, always night); cosH < -1: never sets
    // (polar day, always day). Both events share the same cosH, so test it before converting.
    val cosHourAngle = sunCosHourAngle(dayOfYear, latitude, longitude)
    if (cosHourAngle > 1.0) return true
    if (cosHourAngle < -1.0) return false
    val sunriseUtcHours = sunEventUtcHours(dayOfYear, latitude, longitude, cosHourAngle, rising = true)
    val sunsetUtcHours = sunEventUtcHours(dayOfYear, latitude, longitude, cosHourAngle, rising = false)
    // The event may belong to the previous UTC day (e.g. sunrise in east-Asia time zones), so
    // convert to local wall-clock hours before anchoring to local midnight.
    val offsetHours = utcOffsetMillis / MILLIS_PER_HOUR
    val sunriseMillis = localMidnightMillis + (normalizeHours(sunriseUtcHours + offsetHours) * MILLIS_PER_HOUR).roundToLong()
    val sunsetMillis = localMidnightMillis + (normalizeHours(sunsetUtcHours + offsetHours) * MILLIS_PER_HOUR).roundToLong()
    return epochMillis >= sunsetMillis || epochMillis < sunriseMillis
}

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Cosine of the solar hour angle at the official zenith (90.833°) on [dayOfYear]; values above 1
 * mean the sun never rises and below -1 that it never sets.
 */
private fun sunCosHourAngle(dayOfYear: Int, latitude: Double, longitude: Double): Double {
    val lngHour = longitude / 15.0
    val t = dayOfYear + (12.0 - lngHour) / 24.0
    val meanAnomaly = Math.toRadians(0.9856 * t - 3.289)
    val eclipticLongitude = normalizeDegrees(
        Math.toDegrees(meanAnomaly) + 1.916 * sin(meanAnomaly) +
            0.020 * sin(2.0 * meanAnomaly) + 282.634,
    )
    val sinDec = 0.39782 * sin(eclipticLongitude.toRadians())
    val cosDec = cos(asin(sinDec))
    val zenith = Math.toRadians(90.833)
    return (
        cos(zenith) - sinDec * sin(latitude.toRadians())
        ) / (cosDec * cos(latitude.toRadians()))
}

/**
 * UTC hour (0-24) of sunrise or sunset for [dayOfYear] via the NOAA approximation.
 * [cosHourAngle] must already be confirmed within [-1, 1].
 */
private fun sunEventUtcHours(
    dayOfYear: Int,
    latitude: Double,
    longitude: Double,
    cosHourAngle: Double,
    rising: Boolean,
): Double {
    val lngHour = longitude / 15.0
    val t = dayOfYear + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
    val meanAnomaly = Math.toRadians(0.9856 * t - 3.289)
    val eclipticLongitude = normalizeDegrees(
        Math.toDegrees(meanAnomaly) + 1.916 * sin(meanAnomaly) +
            0.020 * sin(2.0 * meanAnomaly) + 282.634,
    )
    var rightAscension =
        normalizeDegrees(Math.toDegrees(atan(0.91764 * tan(eclipticLongitude.toRadians())))) / 15.0
    val longitudeQuadrant = (eclipticLongitude / 90.0).toInt() * 90
    val ascensionQuadrant = (rightAscension * 15.0 / 90.0).toInt() * 90
    rightAscension += (longitudeQuadrant - ascensionQuadrant) / 15.0

    val hourAngleDegrees = Math.toDegrees(acos(cosHourAngle.coerceIn(-1.0, 1.0)))
    val localHour = if (rising) 360.0 - hourAngleDegrees else hourAngleDegrees
    val localTime = localHour / 15.0 + rightAscension - 0.06571 * t - 6.622
    return normalizeHours(localTime - lngHour)
}

private fun normalizeDegrees(degrees: Double): Double = (degrees % 360.0 + 360.0) % 360.0

private fun normalizeHours(hours: Double): Double = (hours % 24.0 + 24.0) % 24.0

private fun Double.toRadians(): Double = Math.toRadians(this)
