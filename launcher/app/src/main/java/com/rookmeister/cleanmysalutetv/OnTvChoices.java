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
    /** Every app in the dock's row, scrolling sideways (Config's single_row). */
    static final String DOCK_ALL_APPS = "single_row";
    static final String WEATHER = "weather";
    static final String WEATHER_LAT = "weather_lat";
    static final String WEATHER_LON = "weather_lon";
    static final String WEATHER_PLACE = "weather_place";
    /** The app the VPN button opens, picked in the menu's Buttons section; "" for none. Over the theme's vpn_app. */
    static final String VPN_APP = "vpn_app";
    /** Glass app icons (AppAdapter.glassIcons): the dock's icons as white glyphs on glass, couch-tv's look. */
    static final String GLASS_ICONS = "glass_icons";
    /** The "App library" tile after Home's apps; on by default (the settings menu's Apps). */
    static final String LIBRARY_TILE = "library_tile";
    /** Every key above plus the dock's: what a theme push or "follow the theme" removes. */
    static final String[] ALL = {SCENES, SCENE_SECONDS, SCENE_LIST, CLOCK_POSITION, DOCK_ALL_APPS, WEATHER, WEATHER_LAT, WEATHER_LON, WEATHER_PLACE,
            DockStyle.KEY, AirPlayGuard.KEY, TopButtons.KEY, VPN_APP, GLASS_ICONS, LIBRARY_TILE};

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
