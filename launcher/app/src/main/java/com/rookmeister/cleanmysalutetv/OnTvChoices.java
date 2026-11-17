package com.rookmeister.cleanmysalutetv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * CleanMySaluteTV: the choices made in the settings menu on the TV (MenuSettingsActivity) for the parts kept outside
 * Config: scenes and weather. Saved with Launcher settings' own choices (Config.SETTINGS_NAME), so a theme push clears
 * them the same way (Config.KEYS_CLEARED_BY_THEME) and they win over config.json until then.
 */
final class OnTvChoices {
    private OnTvChoices() {}

    static final String SCENES = "scenes";
    static final String SCENE_SECONDS = "scene_seconds";
    static final String SCENE_LIST = "scene_list";
    static final String CLOCK_POSITION = "clock_position";
    static final String WEATHER = "weather";
    static final String WEATHER_LAT = "weather_lat";
    static final String WEATHER_LON = "weather_lon";
    static final String WEATHER_PLACE = "weather_place";
    /** Every key above plus the dock's: what a theme push or "follow the theme" removes. */
    static final String[] ALL = {SCENES, SCENE_SECONDS, SCENE_LIST, CLOCK_POSITION, WEATHER, WEATHER_LAT, WEATHER_LON, WEATHER_PLACE,
            DockStyle.KEY, AirPlayGuard.KEY};

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static SharedPreferences.Editor clear(SharedPreferences.Editor editor) {
        for (String k : ALL) editor.remove(k);
        return editor;
    }

    /** "night,fog" to ["night", "fog"]; blanks dropped. */
    static List<String> splitList(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(",")) if (!part.trim().isEmpty()) out.add(part.trim());
        return out;
    }

    static String joinList(List<String> ids) {
        StringBuilder b = new StringBuilder();
        for (String id : ids) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return b.toString();
    }

    /** The coordinates saved as text, so no float rounding moves the place; NaN when missing or unreadable. */
    static double coordinate(SharedPreferences p, String key) {
        try {
            String v = p.getString(key, null);
            return v == null ? Double.NaN : Double.parseDouble(v);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
