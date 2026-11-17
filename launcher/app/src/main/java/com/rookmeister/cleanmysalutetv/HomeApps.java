package com.rookmeister.cleanmysalutetv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where every app on the TV stands (AppsPlan), read and saved for the settings menu's Apps list and for the Mac app
 * (RemoteSettings): Home's tiles as Home last left them (HomeTiles), the dock's size, and every other launchable app as
 * hidden. Saving writes Order, which apps left Home or came back (LibraryApps) and the tiles; Home rebuilds once the
 * caller bumps the settings revision.
 */
final class HomeApps {
    private HomeApps() {}

    static AppsPlan load(Context c, Config cfg) {
        PackageManager pm = c.getPackageManager();
        List<String> home = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String key : HomeTiles.load(c)) {
            ComponentName cn = ComponentName.unflattenFromString(key);
            if (cn == null || cn.getPackageName().equals(c.getPackageName()) || label(c, key) == null) continue;
            home.add(key);
            seen.add(cn.getPackageName());
        }
        TreeMap<String, String> hidden = new TreeMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(probe, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || !seen.add(pkg)) continue;
                String key = new ComponentName(pkg, r.activityInfo.name).flattenToShortString();
                hidden.put(String.valueOf(r.loadLabel(pm)).toLowerCase(Locale.ROOT) + "\u0000" + key, key);
            }
        }
        return new AppsPlan(home, cfg.columns, new ArrayList<>(hidden.values()));
    }

    /** Saves {@code next} over {@code before}; the caller tells Home (the settings revision). */
    static void save(Context c, AppsPlan before, AppsPlan next) {
        Config cfg = Config.load(c);
        for (String key : before.hidden) {
            if (!next.hidden.contains(key)) LibraryApps.save(c, pkgOf(key), true);
        }
        for (String key : next.hidden) {
            if (!before.hidden.contains(key)) LibraryApps.save(c, pkgOf(key), false);
        }
        Order.save(Order.file(c), new Order(Order.dockKey(cfg.dockApps), next.home, cfg.fillDock ? -1 : next.docked));
        HomeTiles.save(c, next.home);
    }

    static String pkgOf(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }

    /** The app's name for a tile key, or null when the app is gone. */
    static String label(Context c, String key) {
        ComponentName cn = ComponentName.unflattenFromString(key);
        if (cn == null) return null;
        PackageManager pm = c.getPackageManager();
        try {
            return String.valueOf(pm.getActivityInfo(cn, 0).loadLabel(pm));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    static Drawable icon(Context c, String key) {
        try {
            return c.getPackageManager().getActivityIcon(ComponentName.unflattenFromString(key));
        } catch (Exception e) {
            return null;
        }
    }
}
