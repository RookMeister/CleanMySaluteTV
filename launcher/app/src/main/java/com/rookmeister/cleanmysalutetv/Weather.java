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

    /** MET Norway's forecast for a place: the fallback where Open-Meteo cannot be reached. */
    static String metUrl(double latitude, double longitude) {
        return String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
                latitude, longitude);
    }

    /**
     * MET Norway's symbol ("lightrainshowers_day", "cloudy", "heavysnow") as the nearest WMO code, so icons and words
     * work as for Open-Meteo. Unknown symbols count as cloudy.
     */
    static int metCode(String symbol) {
        String s = symbol == null ? "" : symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.contains("thunder")) return 95;
        boolean showers = s.contains("showers");
        if (s.contains("sleet")) return s.startsWith("heavy") ? 67 : 66;
        if (s.contains("snow")) return s.startsWith("heavy") ? (showers ? 86 : 75) : s.startsWith("light") ? (showers ? 85 : 71) : (showers ? 85 : 73);
        if (s.contains("rain")) {
            if (showers) return s.startsWith("heavy") ? 82 : s.startsWith("light") ? 80 : 81;
            return s.startsWith("heavy") ? 65 : s.startsWith("light") ? 61 : 63;
        }
        switch (s) {
            case "clearsky": return 0;
            case "fair": return 1;
            case "partlycloudy": return 2;
            case "fog": return 45;
            default: return 3;
        }
    }

    /** Day unless the symbol says night or polar twilight. */
    static boolean metDay(String symbol) {
        return symbol == null || !(symbol.endsWith("_night") || symbol.endsWith("_polartwilight"));
    }

    /** Open-Meteo's place search, in the TV's language: up to eight places named like {@code name}. */
    static String geocodeUrl(String name, String language) {
        String q;
        try {
            q = java.net.URLEncoder.encode(name.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            q = name.trim();
        }
        String lang = language == null || language.isEmpty() ? "en" : language;
        return "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&language=" + lang + "&name=" + q;
    }

    /** "Казань, Татарстан, Россия": the place, its region and its country, without repeats or blanks. */
    static String placeLabel(String name, String region, String country) {
        StringBuilder b = new StringBuilder(name == null ? "" : name.trim());
        for (String part : new String[] {region, country}) {
            if (part == null || part.trim().isEmpty() || b.toString().contains(part.trim())) continue;
            if (b.length() > 0) b.append(", ");
            b.append(part.trim());
        }
        return b.toString();
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
