package com.rookmeister.cleanmysalutetv;

import java.util.Locale;

/**
 * The weather line under the date: place, an icon, the temperature and a word for the sky, as couch-tv shows it
 * (tv-home/web/app.js: weatherDirect, wxIcon, WMO_KEY). Open-Meteo, free and without a key; the reading is the
 * WMO weather code. Pure logic only, so the JVM tests can check it; {@link WeatherClient} fetches and shows.
 */
final class Weather {
    private Weather() {}

    /** couch-tv refreshes every 30 minutes, and only while Home is on screen. */
    static final long REFRESH_MS = 30 * 60_000L;
    /** After a failed try, wait this long before the next one rather than calling on every minute tick. */
    static final long RETRY_MS = 5 * 60_000L;
    /** A reading older than this is not shown: three hours on, it says more about the past than the sky. */
    static final long STALE_MS = 3 * 60 * 60_000L;

    enum Icon { SUN, MOON, CLOUD, RAIN, SNOW, STORM }

    /** couch-tv's wxIcon (storm from 95, rain from 51, cloud for overcast and fog), plus snow, which it folds into rain. */
    static Icon icon(int code, boolean day) {
        if (code >= 95) return Icon.STORM;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Icon.SNOW;
        if (code >= 51) return Icon.RAIN;
        if (code >= 3 || code == 45 || code == 48) return Icon.CLOUD;
        return day ? Icon.SUN : Icon.MOON;
    }

    /** The words for the sky, by WMO code; snow is told apart, which couch-tv folds into rain. */
    static int textRes(int code) {
        switch (code) {
            case 0: return R.string.wx_clear;
            case 1: return R.string.wx_mostly_clear;
            case 2: return R.string.wx_partly;
            case 3: return R.string.wx_cloudy;
            case 45: case 48: return R.string.wx_fog;
            case 51: case 53: case 55: case 56: case 57: return R.string.wx_drizzle;
            case 61: return R.string.wx_light_rain;
            case 63: case 66: case 67: return R.string.wx_rain;
            case 65: return R.string.wx_heavy_rain;
            case 71: case 73: case 77: case 85: return R.string.wx_snow;
            case 75: case 86: return R.string.wx_heavy_snow;
            case 80: case 81: return R.string.wx_showers;
            case 82: return R.string.wx_heavy_showers;
            case 95: case 96: case 99: return R.string.wx_storms;
            default: return 0;
        }
    }

    /** "−3°", "0°", "12°": a real minus sign, which reads better on a TV than a hyphen. */
    static String temperature(int celsius) {
        return (celsius < 0 ? "−" + (-celsius) : String.valueOf(celsius)) + "°";
    }

    /** Open-Meteo's current reading for a place; coordinates with a dot whatever the TV's language. */
    static String url(double latitude, double longitude) {
        return String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                + "&current=temperature_2m,weather_code,is_day&timezone=auto&forecast_days=1", latitude, longitude);
    }

    static boolean validPlace(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180
                && !(latitude == 0 && longitude == 0);
    }

    /** A fetch is due when there is no reading, the last one is old, or (after a failure) the retry wait is over. */
    static boolean due(long now, long lastReading, long lastFailure) {
        if (lastFailure > lastReading && now - lastFailure < RETRY_MS && now >= lastFailure) return false;
        return lastReading <= 0 || now - lastReading >= REFRESH_MS || now < lastReading;
    }

    static boolean fresh(long now, long reading) {
        return reading > 0 && now >= reading && now - reading < STALE_MS;
    }

    /**
     * "Москва · −3° · Пасмурно", leaving out the parts there are none of; with an empty {@code sky} it is the short
     * line shown on screen ("Москва · −3°", the icon says the rest), with it the line a screen reader speaks.
     */
    static String line(String place, int celsius, String sky) {
        StringBuilder b = new StringBuilder();
        if (place != null && !place.trim().isEmpty()) b.append(place.trim()).append(" · ");
        b.append(temperature(celsius));
        if (sky != null && !sky.isEmpty()) b.append(" · ").append(sky);
        return b.toString();
    }
}
