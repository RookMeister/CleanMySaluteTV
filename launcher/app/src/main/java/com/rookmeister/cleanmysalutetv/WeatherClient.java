package com.rookmeister.cleanmysalutetv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches the weather from Open-Meteo and shows it in the header line under the date. Only while Home is on
 * screen, at most every 30 minutes; the last reading is kept so Home shows it straight away after a restart.
 * Nothing about the TV or its owner goes out: the request carries only the coordinates from the config.
 *
 * Config (config.xml, overlaid by config.json): weather (on or off), weather_lat, weather_lon (the place) and
 * weather_place (the name shown before the temperature; empty shows none).
 */
final class WeatherClient {
    private static final String TAG = "CmstWeather";
    private final Context context;
    private final TextView view;
    private final boolean enabled;
    private final double latitude, longitude;
    private final String place;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active, closed, loading;
    /** Set while the scenes are on: the weather goes into the line over the scene clock instead of the header. */
    interface Listener { void onWeather(String text, int iconRes); }
    private Listener listener;

    void setListener(Listener listener) {
        this.listener = listener;
        view.setVisibility(View.GONE);
    }
    private long lastFailure;

    WeatherClient(Context context, TextView view) {
        this.context = context;
        this.view = view;
        Resources res = context.getResources();
        boolean on = res.getBoolean(R.bool.weather);
        double lat = Double.NaN, lon = Double.NaN;
        String name = res.getString(R.string.weather_place);
        JSONObject json = Config.overlay(context);
        if (json != null) {
            Object v = json.opt("weather");
            if (v instanceof Boolean) on = (Boolean) v;
            v = json.opt("weather_lat");
            if (v instanceof Number) lat = ((Number) v).doubleValue();
            v = json.opt("weather_lon");
            if (v instanceof Number) lon = ((Number) v).doubleValue();
            v = json.opt("weather_place");
            if (v instanceof String) name = ((String) v).trim();
        }
        latitude = lat;
        longitude = lon;
        place = name.length() > 40 ? name.substring(0, 40) : name;
        enabled = on && Weather.validPlace(lat, lon);
        preferences = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
        // A reading kept for another place is not this place's weather.
        String key = Weather.url(lat, lon);
        if (!key.equals(preferences.getString("url", ""))) preferences.edit().clear().putString("url", key).apply();
        view.setVisibility(View.GONE);
    }

    boolean isOn() { return enabled; }

    void start() {
        if (!enabled || closed) return;
        active = true;
        show();
        update();
    }

    void stop() { active = false; }

    void close() {
        active = false;
        closed = true;
        worker.shutdownNow();
    }

    /** From Home's minute tick: fetch when a reading is due, and hide one that went stale. */
    void update() {
        if (!enabled || !active || closed) return;
        show();
        long now = System.currentTimeMillis();
        if (loading || !Weather.due(now, preferences.getLong("time", 0), lastFailure)) return;
        loading = true;
        String url = Weather.url(latitude, longitude);
        worker.execute(() -> {
            JSONObject current = null;
            try {
                current = new JSONObject(get(url)).getJSONObject("current");
            } catch (Exception e) {
                Log.w(TAG, "weather not read: " + e);
            }
            JSONObject reading = current;
            handler.post(() -> {
                loading = false;
                if (closed) return;
                if (reading == null || !reading.has("temperature_2m") || !reading.has("weather_code")) {
                    lastFailure = System.currentTimeMillis();
                    return;
                }
                preferences.edit()
                        .putInt("temp", (int) Math.round(reading.optDouble("temperature_2m")))
                        .putInt("code", (int) Math.round(reading.optDouble("weather_code")))
                        .putBoolean("day", reading.optInt("is_day", 1) == 1)
                        .putLong("time", System.currentTimeMillis())
                        .apply();
                show();
            });
        });
    }

    private void show() {
        if (!Weather.fresh(System.currentTimeMillis(), preferences.getLong("time", 0))) {
            view.setVisibility(View.GONE);
            if (listener != null) listener.onWeather(null, 0);
            return;
        }
        int code = preferences.getInt("code", 0);
        int textRes = Weather.textRes(code);
        String sky = textRes == 0 ? "" : context.getString(textRes);
        int temp = preferences.getInt("temp", 0);
        if (listener != null) {
            // Over the scene clock there is room for the words, as in couch-tv: "1° Переменная облачность".
            listener.onWeather(Weather.temperature(temp) + (sky.isEmpty() ? "" : " " + sky),
                    iconRes(Weather.icon(code, preferences.getBoolean("day", true))));
            return;
        }
        // On screen the icon says what the sky is doing: the words would run into the scene's big clock.
        String line = Weather.line(place, temp, "");
        if (!line.contentEquals(view.getText())) view.setText(line);
        view.setContentDescription(Weather.line(place, temp, sky));
        Drawable icon = context.getDrawable(iconRes(Weather.icon(code, preferences.getBoolean("day", true))));
        if (icon != null) {
            int size = Math.round(view.getTextSize() * 1.25f);
            icon.setBounds(0, 0, size, size);
            icon.setTint(view.getCurrentTextColor());
        }
        view.setCompoundDrawablesRelative(icon, null, null, null);
        view.setVisibility(View.VISIBLE);
    }

    private static int iconRes(Weather.Icon icon) {
        switch (icon) {
            case SUN: return R.drawable.ic_wx_sun;
            case MOON: return R.drawable.ic_wx_moon;
            case RAIN: return R.drawable.ic_wx_rain;
            case SNOW: return R.drawable.ic_wx_snow;
            case STORM: return R.drawable.ic_wx_storm;
            default: return R.drawable.ic_wx_cloud;
        }
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > 64 * 1024) throw new IllegalStateException("answer too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
