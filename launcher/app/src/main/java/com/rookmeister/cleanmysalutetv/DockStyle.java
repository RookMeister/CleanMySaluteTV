package com.rookmeister.cleanmysalutetv;

import android.content.Context;

import org.json.JSONObject;

/**
 * CleanMySaluteTV: the dock's size. "small" is couch-tv's dock: small square app icons in a glass band, centred
 * near the bottom of the screen, the name under the focused one. "large" is cleanyourtv's: wide banner tiles across
 * the screen with the grid under them.
 *
 * Where it comes from, the first that says: the choice made on the TV (Launcher settings, saved with the other
 * on-TV choices and cleared by a theme push like them), config.json "dock_style", config.xml dock_style.
 */
final class DockStyle {
    private DockStyle() {}

    static final String KEY = "dock_style";
    static final String SMALL = "small", LARGE = "large";

    /** "small" or "large", whatever the case and spaces; anything else is null (not set). */
    static String normalize(Object value) {
        if (!(value instanceof String)) return null;
        String v = ((String) value).trim().toLowerCase(java.util.Locale.ROOT);
        return SMALL.equals(v) || LARGE.equals(v) ? v : null;
    }

    /** The first of the on-TV choice, the theme's and the built-in default that is set; small if none is. */
    static String pick(String onTv, String theme, String builtIn) {
        String v = normalize(onTv);
        if (v == null) v = normalize(theme);
        if (v == null) v = normalize(builtIn);
        return v == null ? SMALL : v;
    }

    static String current(Context context) {
        String onTv = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(KEY, null);
        JSONObject json = Config.overlay(context);
        Object theme = json == null ? null : json.opt(KEY);
        return pick(onTv, theme instanceof String ? (String) theme : null, context.getString(R.string.dock_style));
    }

    static boolean small(Context context) {
        return SMALL.equals(current(context));
    }

    /** The grid's left and right padding that centres {@code columns} cells of {@code cellPx} in {@code widthPx}. */
    static int centringPadding(int widthPx, int columns, int cellPx) {
        return Math.max(0, (widthPx - columns * cellPx) / 2);
    }

    /**
     * Left and right padding for a grid of {@code columns} cells of which the first {@code filled} hold the dock's
     * apps: those are centred on the screen and the free cells run off to their right, out of sight.
     */
    static int[] dockPadding(int widthPx, int columns, int filled, int cellPx) {
        int n = filled <= 0 || filled > columns ? columns : filled;
        int left = Math.max(0, (widthPx - n * cellPx) / 2);
        int right = Math.max(0, widthPx - left - columns * cellPx);
        return new int[] {left, right};
    }
}
