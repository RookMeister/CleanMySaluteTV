package com.rookmeister.cleanmysalutetv;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keep watching, as couch-tv's TV home has it (tv-home/android MainActivity.readWatchNext): Android's Watch Next list,
 * where the apps keep what you are part way through for the TV's home screen. Readable with READ_TV_LISTINGS, which
 * the launcher asks for once (or install.sh grants); read only. Empty when the permission or the provider is missing.
 */
final class KeepWatching {
    private KeepWatching() {}

    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    private static final String TAG = "CmstKeep";
    /** couch-tv's limits: twenty cards, five from one app. */
    static final int MAX = 20, PER_APP = 5;

    static final class Item {
        final long id;
        final String pkg, app, title, subtitle, art;
        /** 0..1, or -1 when the app gave no position. */
        final float progress;
        final long engaged;

        Item(long id, String pkg, String app, String title, String subtitle, String art, float progress, long engaged) {
            this.id = id; this.pkg = pkg; this.app = app; this.title = title; this.subtitle = subtitle; this.art = art;
            this.progress = progress; this.engaged = engaged;
        }
    }

    static boolean granted(Context c) {
        return c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** The settings menu's switch (OnTvChoices.KEEP_WATCHING); on by default. */
    static boolean enabled(Context c) {
        return OnTvChoices.prefs(c).getBoolean(OnTvChoices.KEEP_WATCHING, true);
    }

    /** Newest first, at most {@link #MAX}, at most {@link #PER_APP} from one app. Call off the main thread. */
    static List<Item> read(Context c) {
        List<Item> out = new ArrayList<>();
        if (!granted(c)) return out;
        PackageManager pm = c.getPackageManager();
        String[] cols = {"_id", "package_name", "title", "episode_title", "season_display_number",
                "episode_display_number", "poster_art_uri", "thumbnail_uri", "last_playback_position_millis",
                "duration_millis", "last_engagement_time_utc_millis"};
        Map<String, Integer> perApp = new HashMap<>();
        try (Cursor q = c.getContentResolver().query(WATCH_NEXT, cols, null, null, "last_engagement_time_utc_millis DESC")) {
            if (q == null) return out;
            while (q.moveToNext() && out.size() < MAX) {
                String pkg = q.getString(1);
                if (pkg == null || pkg.equals(c.getPackageName())) continue;
                String label;
                try {
                    label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                } catch (PackageManager.NameNotFoundException e) {
                    continue;   // the app is gone (or disabled away): its row cannot be opened
                }
                int n = perApp.containsKey(pkg) ? perApp.get(pkg) + 1 : 1;
                if (n > PER_APP) continue;
                perApp.put(pkg, n);
                String title = blank(q.getString(2)) ? label : q.getString(2).trim();
                String art = !blank(q.getString(7)) ? q.getString(7) : !blank(q.getString(6)) ? q.getString(6) : null;
                long pos = q.getLong(8), dur = q.getLong(9);
                out.add(new Item(q.getLong(0), pkg, label, title,
                        subtitle(q.getString(4), q.getString(5), q.getString(3)), art,
                        progress(pos, dur), q.getLong(10)));
            }
        } catch (RuntimeException e) {
            Log.i(TAG, "watch next unreadable: " + e);
        }
        return out;
    }

    /** "S2 · E5 · The episode", leaving out what is missing; null when nothing is there. */
    static String subtitle(String season, String episode, String episodeTitle) {
        StringBuilder b = new StringBuilder();
        if (!blank(season)) b.append('S').append(season.trim());
        if (!blank(episode)) b.append(b.length() > 0 ? " · " : "").append('E').append(episode.trim());
        if (!blank(episodeTitle)) b.append(b.length() > 0 ? " · " : "").append(episodeTitle.trim());
        return b.length() == 0 ? null : b.toString();
    }

    /** The share watched, 0..1, or -1 when the app gave no position or length. */
    static float progress(long position, long duration) {
        if (position <= 0 || duration <= 0) return -1f;
        return Math.max(0f, Math.min(1f, (float) position / duration));
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Opens one row: its own link, only ever in the app that owns the row, else that app's start screen. Null when
     * neither works.
     */
    static Intent intent(Context c, Item item) {
        PackageManager pm = c.getPackageManager();
        try (Cursor q = c.getContentResolver().query(ContentUris.withAppendedId(WATCH_NEXT, item.id),
                new String[] {"package_name", "intent_uri"}, null, null, null)) {
            if (q != null && q.moveToFirst() && item.pkg.equals(q.getString(0)) && !blank(q.getString(1))) {
                Intent i = Intent.parseUri(q.getString(1), Intent.URI_INTENT_SCHEME);
                if (i.getComponent() != null && !item.pkg.equals(i.getComponent().getPackageName())) i.setComponent(null);
                i.setPackage(item.pkg);
                i.setSelector(null);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (i.resolveActivity(pm) != null) return i;
            }
        } catch (Exception e) {
            Log.i(TAG, "watch next link unusable: " + e);
        }
        Intent i = pm.getLeanbackLaunchIntentForPackage(item.pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(item.pkg);
        return i;
    }
}
