package com.rookmeister.cleanmysalutetv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * CleanMySaluteTV: the Mac app's way in (mac/). Over adb it asks ThemeProvider.call for:
 *
 * <ul>
 * <li>"export": remote/state.json with what Home uses now (the on-TV choices as they stand, after config.json and
 * the built-in defaults), the scenes and top buttons there are, and where every app stands (HomeApps), with each
 * app's icon in remote/icons/&lt;package&gt;.png;</li>
 * <li>"apply": reads remote/apply.json ({"choices": {...}, "apps": {"home": [...], "hidden": [...]}}, either part
 * optional), saves it as the settings menu would, and Home on screen rebuilds with it.</li>
 * </ul>
 *
 * Only keys in {@link #BOOLS}, {@link #INTS} and {@link #STRINGS} are taken, each as its own type.
 */
final class RemoteSettings {
    private RemoteSettings() {}

    static final String DIR = "remote";
    static final String STATE = "state.json", APPLY = "apply.json", ICONS = "icons";

    static final Set<String> BOOLS = new HashSet<>(Arrays.asList(OnTvChoices.SCENES, OnTvChoices.WEATHER,
            OnTvChoices.GLASS_ICONS, OnTvChoices.LIBRARY_TILE, AirPlayGuard.KEY));
    static final Set<String> INTS = new HashSet<>(Arrays.asList(OnTvChoices.SCENE_SECONDS, "columns",
            Config.DOCK_OPACITY_KEY, OnTvChoices.DOCK_TRANSPARENCY));
    static final Set<String> STRINGS = new HashSet<>(Arrays.asList(OnTvChoices.SCENE_LIST, OnTvChoices.CLOCK_POSITION,
            OnTvChoices.WEATHER_PLACE, OnTvChoices.WEATHER_LAT, OnTvChoices.WEATHER_LON, DockStyle.KEY, TopButtons.KEY,
            OnTvChoices.VPN_APP, Config.CLOCK_FORMAT_KEY));

    private static File dir(Context c) {
        File d = Config.dir(c);
        return d == null ? null : new File(d, DIR);
    }

    // ---- export -----------------------------------------------------------------------------------

    static void export(Context c) throws IOException, JSONException {
        File d = dir(c);
        if (d == null) throw new IOException("no storage");
        File icons = new File(d, ICONS);
        if (!icons.isDirectory() && !icons.mkdirs()) throw new IOException("no icons folder");

        Config cfg = Config.load(c);
        SharedPreferences tv = OnTvChoices.prefs(c);
        ScenePlayer.Settings scenes = ScenePlayer.Settings.load(c);
        WeatherClient.Settings weather = WeatherClient.Settings.load(c);

        JSONObject choices = new JSONObject();
        choices.put(OnTvChoices.SCENES, scenes.on);
        choices.put(OnTvChoices.SCENE_SECONDS, scenes.seconds);
        choices.put(OnTvChoices.SCENE_LIST, OnTvChoices.joinList(scenes.ids));
        choices.put(OnTvChoices.CLOCK_POSITION, scenes.position);
        choices.put(OnTvChoices.WEATHER, weather.on);
        choices.put(OnTvChoices.WEATHER_PLACE, weather.place);
        choices.put(OnTvChoices.WEATHER_LAT, String.valueOf(weather.latitude));
        choices.put(OnTvChoices.WEATHER_LON, String.valueOf(weather.longitude));
        choices.put(DockStyle.KEY, DockStyle.current(c));
        choices.put("columns", cfg.columns);
        choices.put(Config.DOCK_OPACITY_KEY, cfg.dockOpacity);
        choices.put(OnTvChoices.DOCK_TRANSPARENCY, tv.getInt(OnTvChoices.DOCK_TRANSPARENCY, OnTvChoices.DOCK_TRANSPARENCY_DEFAULT));
        choices.put(OnTvChoices.GLASS_ICONS, tv.getBoolean(OnTvChoices.GLASS_ICONS, false));
        choices.put(OnTvChoices.LIBRARY_TILE, tv.getBoolean(OnTvChoices.LIBRARY_TILE, true));
        choices.put(AirPlayGuard.KEY, AirPlayGuard.enabled(c));
        choices.put(TopButtons.KEY, OnTvChoices.joinList(TopButtons.chosen(c)));
        choices.put(OnTvChoices.VPN_APP, cfg.vpnApp);
        choices.put(Config.CLOCK_FORMAT_KEY, cfg.clockFormat);

        JSONArray sceneIds = new JSONArray();
        for (Scenes.Scene s : Scenes.ALL) sceneIds.put(s.id);
        JSONArray buttons = new JSONArray();
        for (String kind : TopButtons.ALL) {
            buttons.put(new JSONObject().put("kind", kind).put("label", c.getString(TopButtons.label(kind))));
        }

        AppsPlan plan = HomeApps.load(c, cfg);
        JSONObject apps = new JSONObject()
                .put("home", appList(c, plan.home, icons))
                .put("hidden", appList(c, plan.hidden, icons))
                .put("docked", plan.docked);

        String version = "";
        try {
            version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // unknown
        }
        JSONObject state = new JSONObject()
                .put("version", version)
                .put("choices", choices)
                .put("scenes", sceneIds)
                .put("buttons", buttons)
                .put("apps", apps)
                .put("dockMin", Config.DOCK_MIN)
                .put("dockMax", Config.dockMax(c))
                .put("dockMaxSmall", Config.DOCK_MAX)
                .put("dockMaxLarge", Config.DOCK_MAX_LARGE);
        write(new File(d, STATE), state.toString(1).getBytes(StandardCharsets.UTF_8));
    }

    private static JSONArray appList(Context c, List<String> keys, File icons) throws JSONException {
        JSONArray out = new JSONArray();
        for (String key : keys) {
            String label = HomeApps.label(c, key);
            if (label == null) continue;
            String pkg = HomeApps.pkgOf(key);
            out.put(new JSONObject().put("key", key).put("package", pkg).put("label", label));
            File png = new File(icons, pkg + ".png");
            if (!png.exists()) saveIcon(HomeApps.icon(c, key), png);
        }
        return out;
    }

    private static void saveIcon(Drawable icon, File to) {
        if (icon == null) return;
        int size = 96;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, size, size);
        icon.draw(new Canvas(b));
        try (OutputStream out = new FileOutputStream(to)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException ignored) {
            // the Mac shows a placeholder
        } finally {
            b.recycle();
        }
    }

    // ---- apply ------------------------------------------------------------------------------------

    static void apply(Context c) throws IOException, JSONException {
        File d = dir(c);
        if (d == null) throw new IOException("no storage");
        JSONObject in = new JSONObject(read(new File(d, APPLY)));

        SharedPreferences.Editor e = OnTvChoices.prefs(c).edit();
        JSONObject choices = in.optJSONObject("choices");
        if (choices != null) {
            for (Iterator<String> it = choices.keys(); it.hasNext(); ) {
                String key = it.next();
                Object v = choices.get(key);
                if (BOOLS.contains(key) && v instanceof Boolean) {
                    e.putBoolean(key, (Boolean) v);
                } else if (INTS.contains(key) && v instanceof Number) {
                    int n = ((Number) v).intValue();
                    if ("columns".equals(key)) n = Math.max(Config.DOCK_MIN, Math.min(Config.DOCK_MAX, n));
                    e.putInt(key, n);
                } else if (STRINGS.contains(key) && v instanceof String) {
                    e.putString(key, ((String) v).trim());
                }
            }
        }
        e.commit();

        JSONObject apps = in.optJSONObject("apps");
        if (apps != null) {
            Config cfg = Config.load(c);   // after the choices: the dock size may have just changed
            AppsPlan before = HomeApps.load(c, cfg);
            AppsPlan next = new AppsPlan(strings(apps.optJSONArray("home")), cfg.columns,
                    strings(apps.optJSONArray("hidden")));
            if (!next.home.isEmpty()) HomeApps.save(c, before, next);
        }

        SharedPreferences p = OnTvChoices.prefs(c);
        p.edit().putLong(Config.SETTINGS_REVISION_KEY, p.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).commit();
        // Home on screen rebuilds now (onResume sees the new revision); anywhere else, when it is next opened.
        if (MainActivity.onScreen) {
            c.startActivity(new Intent(c, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
    }

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] all = new byte[(int) f.length()];
            int n = 0;
            while (n < all.length) {
                int r = in.read(all, n, all.length - n);
                if (r < 0) break;
                n += r;
            }
            return new String(all, 0, n, StandardCharsets.UTF_8);
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }
}
