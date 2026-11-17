package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {
    private static final long MIN = 60_000L;

    @Test
    public void iconsFollowCouchTvPlusSnow() {
        assertEquals(Weather.Icon.SUN, Weather.icon(0, true));
        assertEquals(Weather.Icon.MOON, Weather.icon(1, false));
        assertEquals(Weather.Icon.SUN, Weather.icon(2, true));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(3, true));
        assertEquals(Weather.Icon.CLOUD, Weather.icon(45, false));
        assertEquals(Weather.Icon.RAIN, Weather.icon(61, true));
        assertEquals(Weather.Icon.RAIN, Weather.icon(81, true));
        assertEquals(Weather.Icon.SNOW, Weather.icon(71, true));
        assertEquals(Weather.Icon.SNOW, Weather.icon(86, false));
        assertEquals(Weather.Icon.STORM, Weather.icon(95, true));
    }

    @Test
    public void everyWmoCodeOpenMeteoSendsHasWords() {
        int[] codes = {0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99};
        for (int c : codes) assertNotEquals("code " + c, 0, Weather.textRes(c));
        assertEquals(0, Weather.textRes(42));
    }

    @Test
    public void temperatureUsesARealMinus() {
        assertEquals("−3°", Weather.temperature(-3));
        assertEquals("0°", Weather.temperature(0));
        assertEquals("21°", Weather.temperature(21));
    }

    @Test
    public void urlHasDotsWhateverTheLocale() {
        java.util.Locale was = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("ru", "RU"));
            String u = Weather.url(55.7558, 37.6173);
            assertTrue(u, u.contains("latitude=55.7558&longitude=37.6173"));
            assertTrue(u.startsWith("https://api.open-meteo.com/"));
        } finally {
            java.util.Locale.setDefault(was);
        }
    }

    @Test
    public void placeMustBeRealCoordinates() {
        assertTrue(Weather.validPlace(55.75, 37.62));
        assertFalse(Weather.validPlace(Double.NaN, 37.62));
        assertFalse(Weather.validPlace(0, 0));
        assertFalse(Weather.validPlace(91, 0));
        assertFalse(Weather.validPlace(10, 181));
    }

    @Test
    public void dueEveryThirtyMinutesWithAFiveMinuteRetry() {
        long now = 1_000_000_000L;
        assertTrue(Weather.due(now, 0, 0));
        assertFalse(Weather.due(now, now - 29 * MIN, 0));
        assertTrue(Weather.due(now, now - 30 * MIN, 0));
        // failed two minutes ago, after an old reading: wait
        assertFalse(Weather.due(now, now - 60 * MIN, now - 2 * MIN));
        assertTrue(Weather.due(now, now - 60 * MIN, now - 5 * MIN));
        // the clock was set back past the reading
        assertTrue(Weather.due(now, now + 10 * MIN, 0));
    }

    @Test
    public void readingsGoStaleAfterThreeHours() {
        long now = 1_000_000_000L;
        assertTrue(Weather.fresh(now, now - 179 * MIN));
        assertFalse(Weather.fresh(now, now - 180 * MIN));
        assertFalse(Weather.fresh(now, 0));
        assertFalse(Weather.fresh(now, now + MIN));
    }

    @Test
    public void metSymbolsBecomeWmoCodes() {
        assertEquals(0, Weather.metCode("clearsky_day"));
        assertEquals(1, Weather.metCode("fair_night"));
        assertEquals(2, Weather.metCode("partlycloudy_polartwilight"));
        assertEquals(3, Weather.metCode("cloudy"));
        assertEquals(45, Weather.metCode("fog"));
        assertEquals(61, Weather.metCode("lightrain"));
        assertEquals(65, Weather.metCode("heavyrain"));
        assertEquals(80, Weather.metCode("lightrainshowers_day"));
        assertEquals(71, Weather.metCode("lightsnow"));
        assertEquals(75, Weather.metCode("heavysnow"));
        assertEquals(85, Weather.metCode("snowshowers_night"));
        assertEquals(66, Weather.metCode("sleet"));
        assertEquals(95, Weather.metCode("rainandthunder"));
        assertEquals(3, Weather.metCode(""));
        assertEquals(Weather.Icon.SNOW, Weather.icon(Weather.metCode("heavysnowshowers_day"), true));
    }

    @Test
    public void metDayFromTheSuffix() {
        assertTrue(Weather.metDay("cloudy"));
        assertTrue(Weather.metDay("fair_day"));
        assertFalse(Weather.metDay("clearsky_night"));
        assertFalse(Weather.metDay("clearsky_polartwilight"));
    }

    @Test
    public void lineLeavesOutWhatIsMissing() {
        assertEquals("Москва · −3° · Пасмурно", Weather.line(" Москва ", -3, "Пасмурно"));
        assertEquals("5° · Ясно", Weather.line("", 5, "Ясно"));
        assertEquals("5°", Weather.line(null, 5, ""));
    }
}
