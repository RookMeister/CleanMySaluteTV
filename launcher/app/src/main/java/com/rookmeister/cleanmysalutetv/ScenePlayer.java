package com.rookmeister.cleanmysalutetv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows the {@link Scenes} behind Home while it is on screen: the time is redrawn into the scene at each new
 * minute, and every scene_seconds the next scene fades in over 1.6 s. Two image views take turns being the
 * front one; only finished pictures reach them (see {@link SceneRenderer}), drawn on one background thread.
 *
 * Config (config.xml, overlaid by config.json): scenes (on or off), scene_seconds (10 to 3600) and scene_list
 * (the scene ids in the order to show them; none or unknown means all).
 */
final class ScenePlayer {
    private final FrameLayout root;
    private final ImageView back, front;
    private final List<Scenes.Scene> scenes;
    private final long turnMs;
    private final String clockPosition;
    private final boolean fade;
    private final boolean enabled;
    /** Scene pictures on (true) or the big clock alone (false). */
    private final boolean photos;
    private final SceneRenderer renderer;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final Context context;
    private final int width, height;
    /** The clock's digits stay above this share of the height; set from the dock's position once laid out. */
    private volatile float limit;
    /** Home's header (greeting, date, weather): left, right, bottom as shares of the screen; null until laid out. */
    private volatile float[] header;
    /** The weather after the date over the clock (WeatherClient); null or empty for none. */
    private String weatherText;
    private int weatherIcon;
    private final android.graphics.Typeface dateFont, weatherFont;
    private int index;
    private boolean active, closed, busy;
    /** Asked for while a picture was being drawn: a redraw (new minute, new dock position) or the next scene. */
    private boolean pendingRedraw, pendingTurn;
    /** Front picture: kept so it can be dropped under memory pressure. */
    private Bitmap shown;
    private String shownTime = "";

    private final Runnable minute = new Runnable() {
        @Override public void run() {
            draw(index, false);
            scheduleMinute();
        }
    };
    private final Runnable turn = new Runnable() {
        @Override public void run() {
            if (scenes.size() > 1) draw(Scenes.next(index, scenes.size()), true);
            handler.postDelayed(this, turnMs);
        }
    };

    ScenePlayer(Context context, FrameLayout root, boolean calm) {
        this.context = context;
        this.root = root;
        Resources res = context.getResources();
        Settings set = Settings.load(context);
        // Scenes off keeps the big clock, on its own over the wallpaper (Scenes.PLAIN); only the pictures go.
        enabled = true;
        photos = set.on;
        scenes = photos ? Scenes.select(set.ids) : java.util.Collections.singletonList(Scenes.PLAIN);
        turnMs = Scenes.seconds(set.seconds) * 1000L;
        clockPosition = set.position;
        fade = !calm;
        preferences = context.getSharedPreferences("scenes", Context.MODE_PRIVATE);
        index = Math.max(0, Math.min(scenes.size() - 1, preferences.getInt("index", 0)));

        // A 1 GB set gets 1280x720 pictures (about 1.8 MB each instead of 4 MB): soft clock edges, half the memory.
        DisplayMetrics m = res.getDisplayMetrics();
        float scale = lowMemory(context) ? Math.min(1f, 720f / Math.min(m.widthPixels, m.heightPixels)) : 1f;
        width = Math.max(1, Math.round(m.widthPixels * scale));
        height = Math.max(1, Math.round(m.heightPixels * scale));
        renderer = enabled ? new SceneRenderer(context.getAssets()) : null;
        dateFont = font(context, R.string.font_medium);
        weatherFont = font(context, R.string.font_regular);
        back = enabled ? image(context) : null;
        front = enabled ? image(context) : null;
        if (enabled) {
            root.addView(back, 0, match());
            root.addView(front, 1, match());
        }
    }

    boolean isOn() { return enabled; }

    /** The scene pictures are on: they cover the screen, so no wallpaper is drawn under them. */
    boolean hasPhotos() { return photos; }

    /** What the scenes are set to: config.xml, then config.json, then the settings menu on the TV (OnTvChoices). */
    static final class Settings {
        final boolean on;
        final int seconds;
        final List<String> ids;
        /** Scenes.POS_*: the clock's place, "auto" for each scene's own. */
        final String position;

        private Settings(boolean on, int seconds, List<String> ids, String position) {
            this.on = on; this.seconds = seconds; this.ids = ids; this.position = position;
        }

        static Settings load(Context context) {
            Resources res = context.getResources();
            boolean on = res.getBoolean(R.bool.scenes);
            int seconds = res.getInteger(R.integer.scene_seconds);
            List<String> ids = new ArrayList<>();
            String position = Scenes.POS_AUTO;
            JSONObject json = Config.overlay(context);
            if (json != null) {
                Object v = json.opt("scenes");
                if (v instanceof Boolean) on = (Boolean) v;
                v = json.opt("scene_seconds");
                if (v instanceof Number) seconds = ((Number) v).intValue();
                JSONArray list = json.optJSONArray("scene_list");
                if (list != null) for (int i = 0; i < list.length(); i++) ids.add(list.optString(i, ""));
                position = json.optString("clock_position", position);
            }
            SharedPreferences tv = OnTvChoices.prefs(context);
            if (tv.contains(OnTvChoices.SCENES)) on = tv.getBoolean(OnTvChoices.SCENES, on);
            if (tv.contains(OnTvChoices.SCENE_SECONDS)) seconds = tv.getInt(OnTvChoices.SCENE_SECONDS, seconds);
            if (tv.contains(OnTvChoices.SCENE_LIST)) {
                ids.clear();
                ids.addAll(OnTvChoices.splitList(tv.getString(OnTvChoices.SCENE_LIST, "")));
            }
            position = tv.getString(OnTvChoices.CLOCK_POSITION, position);
            return new Settings(on, Scenes.seconds(seconds), ids, position);
        }
    }

    /** The top of Home's dock, in pixels from the top of the screen, so the clock never runs into it. */
    void setDockTop(int pixels) {
        int screen = root.getHeight();
        if (screen <= 0 || pixels <= 0) return;
        float l = Math.max(0.3f, pixels / (float) screen - 0.02f);
        if (Math.abs(l - limit) > 0.005f) {
            limit = l;
            if (active) draw(index, false);
        }
    }

    /** The header's bounds in pixels in Home's root, so the clock moves under it rather than through it. */
    void setHeader(int left, int right, int bottom) {
        int w = root.getWidth(), h = root.getHeight();
        if (w <= 0 || h <= 0 || bottom <= 0) return;
        float[] next = {left / (float) w, right / (float) w, bottom / (float) h};
        float[] was = header;
        if (was != null && Math.abs(was[0] - next[0]) < 0.005f && Math.abs(was[1] - next[1]) < 0.005f
                && Math.abs(was[2] - next[2]) < 0.005f) return;
        header = next;
        if (active) draw(index, false);
    }

    /** The weather for the line over the clock: "1° Переменная облачность" and its icon; null hides it. */
    void setWeather(String text, int iconRes) {
        if (java.util.Objects.equals(text, weatherText) && iconRes == weatherIcon) return;
        weatherText = text;
        weatherIcon = iconRes;
        if (active) draw(index, false);
    }

    void start() {
        if (!enabled || closed) return;
        active = true;
        handler.removeCallbacks(minute);
        handler.removeCallbacks(turn);
        if (shown == null || !time().equals(shownTime)) draw(index, false);
        scheduleMinute();
        handler.postDelayed(turn, turnMs);
    }

    void stop() {
        active = false;
        handler.removeCallbacks(minute);
        handler.removeCallbacks(turn);
    }

    /** Home is hidden and Android wants memory back: drop the pictures; start() draws again. */
    void releaseForMemoryPressure() {
        if (!enabled || active) return;
        back.setImageDrawable(null);
        front.setImageDrawable(null);
        shown = null;
        shownTime = "";
    }

    void close() {
        stop();
        closed = true;
        worker.shutdownNow();
    }

    private void scheduleMinute() {
        long now = System.currentTimeMillis();
        handler.postDelayed(minute, 60_000L - now % 60_000L + 50);
    }

    private String time() {
        return DateFormat.format(DateFormat.is24HourFormat(context) ? "HH:mm" : "h:mm", System.currentTimeMillis()).toString();
    }

    /** Draws scene {@code i} off the UI thread and puts it on screen: faded in as a new scene, or swapped for a new minute. */
    private void draw(int i, boolean asTurn) {
        if (closed) return;
        if (busy) {
            // Not dropped: a dock position or minute that arrives mid-draw is drawn right after.
            if (asTurn) pendingTurn = true; else pendingRedraw = true;
            return;
        }
        busy = true;
        Scenes.Scene s = scenes.get(i);
        String t = time();
        boolean dim = Scenes.dimmed(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        float l = limit;
        float[] hd = header;
        String day = Scenes.dateLine(new java.util.Date(), java.util.Locale.getDefault());
        android.graphics.drawable.Drawable icon = weatherText != null && weatherIcon != 0
                ? context.getDrawable(weatherIcon).mutate() : null;
        SceneRenderer.Caption caption = new SceneRenderer.Caption(day, weatherText, icon, dateFont, weatherFont);
        if (s.placeRes != 0) caption.credit = context.getString(s.placeRes) + " \u00B7 " + s.author;
        worker.execute(() -> {
            Bitmap b = renderer.render(s, width, height, t, dim, l, hd, caption, clockPosition);
            Bitmap gpu = b == null ? null : b.copy(Bitmap.Config.HARDWARE, false);
            if (gpu != null) b.recycle(); else gpu = b;
            Bitmap result = gpu;
            handler.post(() -> {
                busy = false;
                if (closed) return;
                if (result != null && (active || shown == null)) show(result, i, t, asTurn && fade && shown != null);
                // hidden meanwhile: start() draws a fresh one
                if (!active) { pendingTurn = false; pendingRedraw = false; return; }
                if (pendingTurn) {
                    pendingTurn = false;
                    pendingRedraw = false;
                    draw(Scenes.next(index, scenes.size()), true);
                } else if (pendingRedraw) {
                    pendingRedraw = false;
                    draw(index, false);
                }
            });
        });
    }

    private void show(Bitmap b, int i, String t, boolean animate) {
        index = i;
        shownTime = t;
        preferences.edit().putInt("index", i).apply();
        if (photos) root.setBackground(null);   // the scene covers the screen; a clock alone leaves the background
        shown = b;
        if (!animate) {
            front.animate().cancel();
            front.setAlpha(1f);
            front.setImageBitmap(b);
            back.setImageDrawable(null);
            return;
        }
        // The new scene goes behind the current one, which then fades out: the screen never dips to black.
        back.setImageBitmap(b);
        back.setAlpha(1f);
        front.animate().cancel();
        front.animate().alpha(0f).setDuration(Scenes.FADE_MS).withEndAction(() -> {
            front.setImageBitmap(b);
            front.setAlpha(1f);
            back.setImageDrawable(null);
        }).start();
    }

    private static android.graphics.Typeface font(Context context, int res) {
        try {
            return UiFont.get(context, res);
        } catch (Exception e) {
            return android.graphics.Typeface.DEFAULT;
        }
    }

    private static ImageView image(Context context) {
        ImageView v = new ImageView(context);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        v.setFocusable(false);
        return v;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static boolean lowMemory(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;
        if (am.isLowRamDevice()) return true;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return info.totalMem > 0 && info.totalMem < 1_500L * 1024 * 1024;
    }
}
