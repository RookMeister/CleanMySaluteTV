package com.rookmeister.cleanmysalutetv;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

/**
 * CleanMySaluteTV: screen off now, and the sleep timer that does it later. Turning the screen off needs this app as
 * a device admin with only the force-lock policy (res/xml/device_admin.xml): install.sh makes it one with
 * "dpm set-active-admin", or the TV's own "Activate device admin app?" prompt does (activationIntent). The timer is an
 * alarm that survives Home being closed; it is gone after a reboot, as a TV's own sleep timer is.
 */
final class PowerControl {
    private PowerControl() {}

    /** Off, then 15, 30, 60 and 90 minutes, as tvOS-Launcher's Control Center offers. */
    static final int[] STEPS = {0, 15, 30, 60, 90};
    private static final String PREFS = "power", AT = "sleep_at";

    /** The device admin component; it only has to exist, the work is done through DevicePolicyManager. */
    public static final class Admin extends DeviceAdminReceiver {}

    /** Fires when the timer is up. */
    public static final class TimerReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            prefs(context).edit().remove(AT).apply();
            if (!sleepNow(context)) Toast.makeText(context, R.string.cp_power_needed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Gives up the device admin role, so the launcher can be uninstalled and POWER=no takes effect: "dpm
     * remove-active-admin" only works for test builds. Only the adb shell can send this (WRITE_SECURE_SETTINGS on the
     * receiver in the manifest); restore.sh and install.sh do.
     */
    public static final class Release extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            set(context, 0);
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            if (dpm != null && dpm.isAdminActive(admin(context))) dpm.removeActiveAdmin(admin(context));
        }
    }

    static ComponentName admin(Context c) {
        return new ComponentName(c, Admin.class);
    }

    static boolean canSleep(Context c) {
        DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isAdminActive(admin(c));
    }

    /** Turns the screen off; false when this app is not a device admin (nothing happens then). */
    static boolean sleepNow(Context c) {
        if (!canSleep(c)) return false;
        try {
            c.getSystemService(DevicePolicyManager.class).lockNow();
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /** The TV's own prompt that makes this app a device admin, where the firmware has one. */
    static Intent activationIntent(Context c) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(c))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, c.getString(R.string.cp_power_explain));
    }

    /** Minutes until the timer turns the screen off, rounded up; 0 when it is off. */
    static int minutesLeft(Context c) {
        return remaining(prefs(c).getLong(AT, 0), System.currentTimeMillis());
    }

    static int remaining(long at, long now) {
        if (at <= now) return 0;
        return (int) ((at - now + 59_999) / 60_000);
    }

    /**
     * The next step after the running timer: off becomes 15, and a timer moves on from the step it was set to (the
     * smallest step at or above what is left: 14 minutes left of 15 is still the 15-minute step, so OK gives 30).
     */
    static int next(int minutesLeft) {
        for (int i = 0; i < STEPS.length; i++) {
            if (STEPS[i] >= minutesLeft) return STEPS[(i + 1) % STEPS.length];
        }
        return 0;
    }

    /** Sets the timer to {@code minutes} from now, or cancels it with 0. */
    static void set(Context c, int minutes) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, TimerReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (am != null) am.cancel(pi);
        if (minutes <= 0) {
            prefs(c).edit().remove(AT).apply();
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        prefs(c).edit().putLong(AT, at).apply();
        if (am == null) return;
        // On time where exact alarms are allowed (install.sh grants SCHEDULE_EXACT_ALARM with POWER=yes); otherwise
        // within the ten-minute window Android allows any app, rather than its default of up to three quarters of
        // the wait (22 minutes late on a 30-minute timer).
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
