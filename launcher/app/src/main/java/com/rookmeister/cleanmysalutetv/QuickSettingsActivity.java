package com.rookmeister.cleanmysalutetv;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CleanMySaluteTV's control panel, after tvOS-Launcher's Control Center and couch-tv's quick buttons: glass tiles at
 * the right over a dimmed (blurred where the TV can) screen, the focused tile white. The gear on Home opens it, and so
 * does the remote's Settings button over any app (settings_key_panel, HomeKeyService). Its own translucent task, so
 * the app behind keeps playing; Back, the Settings button again, or anything taking the screen closes it.
 *
 * Every tile works without system permissions: the status lines read what any app may read, and the tiles open the
 * TV's own settings pages. Sleep timer and screen off would need device admin or an accessibility switch: not here.
 */
public final class QuickSettingsActivity extends Activity {
    private float dp;
    private Typeface medium, regular;
    private TextView memoryStatus, timerStatus;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        medium = font(R.string.font_medium);
        regular = font(R.string.font_regular);
        boolean blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        if (blur) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            getWindow().getAttributes().setBlurBehindRadius(px(36));
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(blur ? 0x8C000000 : 0xC2000000);
        root.setOnClickListener(v -> finish());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        pp.topMargin = px(48);
        pp.setMarginEnd(px(48));
        root.addView(panel, pp);

        String[] net = network();
        View wifi = tile(net[1].equals("ethernet") ? R.drawable.ic_ethernet : R.drawable.ic_wifi,
                getString(net[1].equals("ethernet") ? R.string.cp_ethernet : R.string.cp_wifi), net[0],
                () -> open(SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS)));
        View bt = tile(R.drawable.ic_cp_bluetooth, getString(R.string.cp_bluetooth), bluetooth(),
                () -> open(SystemSettings.intent(this, Settings.ACTION_BLUETOOTH_SETTINGS)));
        View sound = tile(R.drawable.ic_cp_sound, getString(R.string.cp_sound), volume(),
                () -> open(SystemSettings.intent(this, Settings.ACTION_SOUND_SETTINGS)));
        View picture = tile(R.drawable.ic_picture, getString(R.string.cp_picture), null,
                () -> open(SystemSettings.displayIntent(this)));
        View memory = tile(R.drawable.ic_cp_memory, getString(R.string.cp_memory), memory(), this::cleanMemory);
        memoryStatus = memory.findViewWithTag("status");
        View clock = tile(R.drawable.ic_cp_clock, getString(R.string.cp_screensaver), getString(R.string.cp_screensaver_sub),
                () -> open(new Intent(this, ClockActivity.class)));
        // Wrap, not the vertical parent's default match: the round row is narrower and would squeeze the pairs.
        LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        panel.addView(pair(wifi, bt), wrap);
        panel.addView(pair(sound, picture), new LinearLayout.LayoutParams(wrap));
        panel.addView(pair(memory, clock), new LinearLayout.LayoutParams(wrap));
        View timer = tile(R.drawable.ic_cp_timer, getString(R.string.cp_timer), timerText(), this::stepTimer);
        timerStatus = timer.findViewWithTag("status");
        View off = tile(R.drawable.ic_cp_power, getString(R.string.cp_screen_off),
                PowerControl.canSleep(this) ? getString(R.string.cp_screen_off_sub) : getString(R.string.cp_power_needed_short),
                this::screenOff);
        panel.addView(pair(timer, off), new LinearLayout.LayoutParams(wrap));

        LinearLayout round = new LinearLayout(this);
        round.setOrientation(LinearLayout.HORIZONTAL);
        round.addView(roundTile(R.drawable.ic_input, R.string.inputs,
                () -> open(home(MainActivity.ACTION_VIEW_INPUTS))));
        round.addView(roundTile(R.drawable.ic_apps, R.string.cp_all_apps,
                () -> open(home(MainActivity.ACTION_APP_LIBRARY))));
        // Through Home, so it rebuilds with the new choices when the settings menu closes.
        round.addView(roundTile(R.drawable.ic_home_settings, R.string.launcher_settings,
                () -> open(home(MainActivity.ACTION_LAUNCHER_SETTINGS))));
        round.addView(roundTile(R.drawable.ic_settings, R.string.tv_settings,
                () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS))));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = px(6);
        panel.addView(round, rp);

        setContentView(root);
        wifi.requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    @SuppressWarnings("deprecation")
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- sleep timer and screen off (PowerControl) -------------------------------------------

    private String timerText() {
        if (!PowerControl.canSleep(this)) return getString(R.string.cp_power_needed_short);
        int left = PowerControl.minutesLeft(this);
        return left == 0 ? getString(R.string.menu_off) : getString(R.string.cp_timer_left, left);
    }

    /** OK steps the timer: off, 15, 30, 60, 90 minutes, off. The panel stays open so the choice can be seen. */
    private void stepTimer() {
        if (!PowerControl.canSleep(this)) {
            askForAdmin();
            return;
        }
        PowerControl.set(this, PowerControl.next(PowerControl.minutesLeft(this)));
        timerStatus.setText(timerText());
    }

    private void screenOff() {
        if (!PowerControl.canSleep(this)) {
            askForAdmin();
            return;
        }
        finish();
        PowerControl.sleepNow(this);
    }

    /** The TV's "Activate device admin app?" prompt; where the firmware has none, install.sh does it (POWER=yes). */
    private void askForAdmin() {
        try {
            startActivity(PowerControl.activationIntent(this));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cp_power_needed, Toast.LENGTH_LONG).show();
        }
    }

    // ---- statuses -------------------------------------------------------------------------------

    /** {status line, "wifi" | "ethernet" | "none"}. The network name needs the location grant install.sh gives. */
    @SuppressWarnings("deprecation")
    private String[] network() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return new String[] {getString(R.string.cp_connected), "ethernet"};
            }
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm == null ? null : wm.getConnectionInfo();
                String ssid = ControlPanel.ssid(info == null ? null : info.getSSID());
                return new String[] {ssid.isEmpty() ? getString(R.string.cp_connected) : ssid, "wifi"};
            }
        } catch (RuntimeException ignored) {
            // no status rather than no panel
        }
        return new String[] {getString(R.string.cp_offline), "none"};
    }

    /** On or off where the TV lets an app read it; nothing otherwise. */
    @SuppressWarnings("deprecation")
    private String bluetooth() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return getString(R.string.cp_none);
            return getString(a.isEnabled() ? R.string.menu_on : R.string.menu_off);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String volume() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return null;
        int v = am.getStreamVolume(AudioManager.STREAM_MUSIC), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (v == 0 || am.isStreamMute(AudioManager.STREAM_MUSIC)) return getString(R.string.cp_muted);
        return getString(R.string.cp_volume, ControlPanel.percent(v, max));
    }

    private String memory() {
        int used = memoryUsed();
        return used < 0 ? null : getString(R.string.cp_memory_used, used);
    }

    /** Memory in use, in percent; -1 when Android does not say. */
    private int memoryUsed() {
        ActivityManager am = getSystemService(ActivityManager.class);
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
    private void cleanMemory() {
        ActivityManager am = getSystemService(ActivityManager.class);
        if (am == null) return;
        PackageManager pm = getPackageManager();
        Set<String> packages = new HashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
            for (ResolveInfo r : apps) {
                ApplicationInfo ai = r.activityInfo.applicationInfo;
                if ((ai.flags & ApplicationInfo.FLAG_PERSISTENT) == 0) packages.add(ai.packageName);
            }
        }
        packages.remove(getPackageName());
        for (String p : packages) {
            try {
                am.killBackgroundProcesses(p);
            } catch (SecurityException ignored) {
                // the permission is missing on this build: nothing closed
            }
        }
        int before = memoryUsed();
        if (memoryStatus == null) return;
        // The freed memory shows after a moment, once the processes are gone.
        memoryStatus.postDelayed(() -> {
            int after = memoryUsed();
            memoryStatus.setText(memory());
            Toast.makeText(this, getString(R.string.cp_memory_cleaned, before, after), Toast.LENGTH_SHORT).show();
        }, 800);
    }

    // ---- tiles ----------------------------------------------------------------------------------

    private LinearLayout pair(View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(236), px(70));
        lp.setMargins(px(6), px(6), px(6), px(6));
        row.addView(a, lp);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(px(236), px(70));
        lp2.setMargins(px(6), px(6), px(6), px(6));
        row.addView(b, lp2);
        return row;
    }

    private View tile(int icon, String title, String status, Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.HORIZONTAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(20), 0, px(16), 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(glass(px(35)));
        ColorStateList main = states(0xFF16171B, 0xFFFFFFFF), dim = states(0xFF55585F, 0xB3FFFFFF);
        ImageView i = new ImageView(this);
        Drawable d = getDrawable(icon).mutate();
        d.setTintList(states(0xFF1E88FF, 0xFFFFFFFF));
        i.setImageDrawable(d);
        i.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(px(26), px(26));
        ip.setMarginEnd(px(14));
        t.addView(i, ip);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        TextView ti = text(title, 16, medium, main);
        texts.addView(ti);
        if (status != null) {
            TextView s = text(status, 13, regular, dim);
            s.setTag("status");
            texts.addView(s);
        }
        t.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        t.setOnClickListener(v -> action.run());
        t.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f)
                .setDuration(140).start());
        return t;
    }

    private View roundTile(int icon, int description, Runnable action) {
        ImageView t = new ImageView(this);
        Drawable d = getDrawable(icon).mutate();
        d.setTintList(states(0xFF16171B, 0xFFFFFFFF));
        t.setImageDrawable(d);
        t.setPadding(px(19), px(19), px(19), px(19));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(glass(px(20)));
        t.setContentDescription(getString(description));
        t.setOnClickListener(v -> action.run());
        t.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f)
                .setDuration(140).start());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(112), px(64));
        lp.setMargins(px(6), px(6), px(6), px(6));
        t.setLayoutParams(lp);
        return t;
    }

    private static StateListDrawable glass(int radius) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(radius);
        on.setColor(0xFFFFFFFF);
        GradientDrawable off = new GradientDrawable();
        off.setCornerRadius(radius);
        off.setColor(0x33FFFFFF);
        off.setStroke(2, 0x40FFFFFF);
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, off);
        return s;
    }

    private static ColorStateList states(int focused, int plain) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {focused, plain});
    }

    private TextView text(String s, float sp, Typeface face, ColorStateList color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setDuplicateParentStateEnabled(true);
        return t;
    }

    private Intent home(String action) {
        return new Intent(this, MainActivity.class).setAction(action);
    }

    private void open(Intent target) {
        try {
            startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException | NullPointerException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private Typeface font(int res) {
        try {
            return UiFont.get(this, res);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    private int px(float dps) { return Math.round(dps * dp); }
}
