package com.shilapi.xcertplay

import android.content.res.Configuration
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkModeTest {
    @Test
    fun darkModeIsDetectedWithUnrelatedConfigurationBits() {
        assertEquals(true, nightModeOrNull(Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun lightModeIsDetectedWithUnrelatedConfigurationBits() {
        assertEquals(false, nightModeOrNull(Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun undefinedModeIsIgnored() {
        assertNull(nightModeOrNull(Configuration.UI_MODE_NIGHT_UNDEFINED))
        assertNull(nightModeOrNull(Configuration.UI_MODE_TYPE_CAR))
    }

    @Test
    fun darkModeSourceRoundTripsThroughPersistedName() {
        for (source in DarkModeSource.entries) {
            assertEquals(source, DarkModeSource.fromPersistedName(source.persistedName()))
        }
    }

    @Test
    fun darkModeSourceFallsBackToAutoUiMode() {
        assertEquals(DarkModeSource.AUTO_UI_MODE, DarkModeSource.fromPersistedName(null))
        assertEquals(DarkModeSource.AUTO_UI_MODE, DarkModeSource.fromPersistedName(""))
        assertEquals(DarkModeSource.AUTO_UI_MODE, DarkModeSource.fromPersistedName("not_a_source"))
        assertEquals(DarkModeSource.AUTO_UI_MODE, DarkModeSource.fromPersistedName("auto_ui_mode"))
    }

    @Test
    fun brightnessAtOrBelowDefaultThresholdIsNight() {
        assertTrue(isNightByBrightness(0))
        assertTrue(isNightByBrightness(DEFAULT_BRIGHTNESS_NIGHT_THRESHOLD))
        assertFalse(isNightByBrightness(DEFAULT_BRIGHTNESS_NIGHT_THRESHOLD + 1))
        assertFalse(isNightByBrightness(255))
    }

    @Test
    fun brightnessRespectsCustomThreshold() {
        assertTrue(isNightByBrightness(100, threshold = 100))
        assertFalse(isNightByBrightness(101, threshold = 100))
        assertFalse(isNightByBrightness(0, threshold = -1))
    }

    @Test
    fun beijingNoonIsDayOnBothSolstices() {
        assertFalse(isNightAt(shanghai, 2026, 6, 21, 12, 0, BEIJING_LATITUDE, BEIJING_LONGITUDE))
        assertFalse(isNightAt(shanghai, 2026, 12, 21, 12, 0, BEIJING_LATITUDE, BEIJING_LONGITUDE))
    }

    @Test
    fun beijingMidnightIsNightOnBothSolstices() {
        assertTrue(isNightAt(shanghai, 2026, 6, 21, 0, 0, BEIJING_LATITUDE, BEIJING_LONGITUDE))
        assertTrue(isNightAt(shanghai, 2026, 12, 21, 0, 0, BEIJING_LATITUDE, BEIJING_LONGITUDE))
    }

    @Test
    fun northPoleHasPolarDayInJuneAndPolarNightInDecember() {
        // Any hour of the day must report the same state during polar day / polar night.
        for (hour in listOf(0, 6, 12, 18)) {
            assertFalse(isNightAt(utc, 2026, 6, 21, hour, 0, NORTH_POLE_LATITUDE, 0.0))
            assertTrue(isNightAt(utc, 2026, 12, 21, hour, 0, NORTH_POLE_LATITUDE, 0.0))
        }
    }

    private companion object {
        val shanghai: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val utc: TimeZone = TimeZone.getTimeZone("UTC")
        const val BEIJING_LATITUDE = 39.9042
        const val BEIJING_LONGITUDE = 116.4074
        const val NORTH_POLE_LATITUDE = 90.0

        fun isNightAt(
            timeZone: TimeZone,
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            latitude: Double,
            longitude: Double,
        ): Boolean = isNightBySunSchedule(
            epochMillis(timeZone, year, month, day, hour, minute),
            latitude,
            longitude,
            timeZone,
        )

        fun epochMillis(
            timeZone: TimeZone,
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
        ): Long = Calendar.getInstance(timeZone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
