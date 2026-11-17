package com.rookmeister.cleanmysalutetv;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Memory button on the top bar (TopButtons.MEMORY): how much of the TV's memory is in use, and closing the
 * background apps to free it. It was a tile in the control panel; the owner moved it to the bar.
 */
final class MemoryClean {
    private MemoryClean() {}

    /** Memory in use, in percent; -1 when Android does not say. */
    static int used(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am == null) return -1;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        return ControlPanel.percent(info.totalMem - info.availMem, info.totalMem);
    }

    /**
     * Closes the background processes of the TV's apps with a launcher icon, preinstalled ones included
     * (KILL_BACKGROUND_PROCESSES, granted at install like any normal permission). The app on screen, services the
     * system keeps alive and this launcher keep running.
     */
    static void clean(Context c) {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        if (am == null) return;
        PackageManager pm = c.getPackageManager();
        Set<String> packages = new HashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo r : apps) {
                ApplicationInfo ai = r.activityInfo.applicationInfo;
                if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) == 0) packages.add(ai.packageName);
            }
        }
        packages.remove(c.getPackageName());
        for (String p : packages) {
            try {
                am.killBackgroundProcesses(p);
            } catch (SecurityException ignored) {
                // the permission is missing on this build: nothing closed
            }
        }
    }
}
