package com.rookmeister.cleanmysalutetv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CleanMySaluteTV's settings menu, after couch-tv's Home settings (the layout techgenius1/tvOS-Launcher also follows):
 * sections down the left, their rows on the right as frosted glass over a blurred copy of the scene, the focused row
 * white. OK changes a row. Everything is saved with Launcher settings' on-TV choices, so Home rebuilds with it on
 * return and a theme push clears it (Config, OnTvChoices); the older Launcher settings page stays one row away.
 */
public final class MenuSettingsActivity extends Activity {
    private static final int LOOK = 0, WEATHER = 1, APPS = 2, BUTTONS = 3, MORE = 4, ABOUT = 5;
    private static final int[] SECTION_NAMES = {R.string.menu_look, R.string.menu_weather, R.string.menu_apps,
            R.string.menu_buttons, R.string.menu_more, R.string.menu_about};
    private static final int[] SECTION_ICONS = {R.drawable.ic_menu_look, R.drawable.ic_wx_cloud, R.drawable.ic_menu_apps,
            R.drawable.ic_settings, R.drawable.ic_menu_more, R.drawable.ic_menu_info};
    private static final int[] SCENE_NAMES = {R.string.menu_scene_night, R.string.menu_scene_golden,
            R.string.menu_scene_water, R.string.menu_scene_fog, R.string.menu_scene_dawn,
            R.string.scene_pavement, R.string.scene_valley, R.string.scene_storm, R.string.scene_beach, R.string.scene_paddy};
    private static final int[] OPACITIES = {100, 70, 50, 30};
    private static final int[] SCENE_SECONDS = {10, 20, 30, 60, 120, 300};
    private static final String[] CLOCKS = {"system", "12", "24"};

    private SharedPreferences choices;
    private float dp;
    private Typeface medium, regular;
    private final List<View> sideItems = new ArrayList<>();
    private TextView head;
    private LinearLayout list;
    private ScrollView scroller;
    private int section = LOOK;
    /** The city search page is showing in the Weather section: its last answer, and what it is doing. */
    private boolean searching;
    /** The Buttons section's app list for the VPN button is open. */
    private boolean pickingVpn;
    /** The Apps list: where every app is; null until the section is first drawn. */
    private AppsPlan plan;
    /** The app being moved with Up and Down (held OK), or null. */
    private String moving;
    /** The top button being moved with Up and Down (held OK), or null. */
    private String movingButton;
    /** True while the right pane is being drawn again: focus moves made for it are not section changes. */
    private boolean rebuilding;
    private List<String[]> found;
    private String status;
    private String query = "";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        choices = OnTvChoices.prefs(this);
        medium = font(R.string.font_medium);
        regular = font(R.string.font_regular);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0C10);
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap blur = blurredScene();
        if (blur != null) backdrop.setImageBitmap(blur);
        root.addView(backdrop, match());
        View dim = new View(this);
        dim.setBackgroundColor(0xB8000000);
        root.addView(dim, match());

        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(LinearLayout.HORIZONTAL);
        panes.setPadding(px(56), px(40), px(56), px(28));
        root.addView(panes, match());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        panes.addView(left, new LinearLayout.LayoutParams(px(290), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView title = text(getString(R.string.menu_title), 30, medium, 0xFFFFFFFF);
        title.setPadding(px(8), 0, 0, px(22));
        left.addView(title);
        for (int i = 0; i < SECTION_NAMES.length; i++) left.addView(sideItem(i));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rp.setMarginStart(px(48));
        panes.addView(right, rp);
        head = text("", 24, medium, 0xFFFFFFFF);
        head.setPadding(px(4), px(4), 0, px(16));
        right.addView(head);
        scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setPadding(0, px(4), 0, px(12));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(6), 0, px(6), 0);
        list.setClipToPadding(false);   // a focused row grows a little past the padding
        list.setClipChildren(false);
        scroller.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView hint = text(getString(R.string.menu_hint), 13, regular, 0xB3FFFFFF);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(px(18));
        pill.setColor(0x14FFFFFF);
        pill.setStroke(Math.max(1, px(1)), 0x33FFFFFF);
        hint.setBackground(pill);
        hint.setPadding(px(14), px(7), px(14), px(7));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = px(10);
        right.addView(hint, hp);

        setContentView(root);
        show(LOOK, false);
        sideItems.get(LOOK).requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    // ---- sections -------------------------------------------------------------------------------

    /** Fills the right pane with section {@code s}; {@code keepRow} >= 0 puts the focus back on that row. */
    private void show(int s, boolean focusList) {
        show(s, focusList ? 0 : -1);
    }

    private void show(int s, int keepRow) {
        if (s != WEATHER) searching = false;
        if (s != BUTTONS) pickingVpn = false;
        if (s != APPS) moving = null;
        if (s != BUTTONS) movingButton = null;
        rebuilding = true;
        // The focused row is about to go: park the focus on this section's own item first, or Android hands it to
        // the first item on the left, which would switch the section.
        View focused = getCurrentFocus();
        if (focused != null && focused != sideItems.get(s) && isInList(focused)) sideItems.get(s).requestFocus();
        section = s;
        for (int i = 0; i < sideItems.size(); i++) sideItems.get(i).setSelected(i == s);
        head.setText(SECTION_NAMES[s]);
        list.removeAllViews();
        switch (s) {
            case LOOK: look(); break;
            case WEATHER: weather(); break;
            case APPS: apps(); break;
            case BUTTONS: buttons(); break;
            case MORE: more(); break;
            default: about(); break;
        }
        if (keepRow >= 0) {
            List<View> rows = rows();
            if (!rows.isEmpty()) rows.get(Math.min(keepRow, rows.size() - 1)).requestFocus();
        } else {
            scroller.scrollTo(0, 0);
        }
        rebuilding = false;
    }

    private boolean isInList(View v) {
        for (android.view.ViewParent p = v.getParent(); p != null; p = p.getParent()) if (p == list) return true;
        return false;
    }

    private void look() {
        Config cfg = Config.load(this);
        boolean small = DockStyle.small(this);
        group(R.string.menu_group_dock);
        row(getString(R.string.menu_dock_size), null, getString(small ? R.string.dock_small : R.string.dock_large), null, null,
                () -> choices.edit().putString(DockStyle.KEY, small ? DockStyle.LARGE : DockStyle.SMALL).apply());
        row(getString(R.string.menu_dock_opacity), null, cfg.dockOpacity + "%", null, null,
                () -> choices.edit().putInt(Config.DOCK_OPACITY_KEY, next(OPACITIES, cfg.dockOpacity)).apply());
        boolean glass = choices.getBoolean(OnTvChoices.GLASS_ICONS, false);
        row(getString(R.string.menu_glass_icons), getString(R.string.menu_glass_icons_sub), onOff(glass), null, null,
                () -> choices.edit().putBoolean(OnTvChoices.GLASS_ICONS, !glass).apply());

        ScenePlayer.Settings sc = ScenePlayer.Settings.load(this);
        group(R.string.menu_group_scenes);
        row(getString(R.string.menu_scenes), getString(R.string.menu_scenes_sub), onOff(sc.on), null, null,
                () -> choices.edit().putBoolean(OnTvChoices.SCENES, !sc.on).apply());
        List<String> chosen = ids(Scenes.select(sc.ids));
        for (int i = 0; i < Scenes.ALL.size(); i++) {
            String id = Scenes.ALL.get(i).id;
            boolean in = chosen.contains(id);
            row(getString(SCENE_NAMES[i]), null, null, thumbnail(id),
                    getDrawable(in ? R.drawable.ic_menu_check_on : R.drawable.ic_menu_check_off), () -> {
                        List<String> next = MenuChoices.toggleScene(ids(Scenes.ALL), chosen, id);
                        choices.edit().putString(OnTvChoices.SCENE_LIST, OnTvChoices.joinList(next)).apply();
                    });
        }
        row(getString(R.string.menu_scene_seconds), null, duration(sc.seconds), null, null,
                () -> choices.edit().putInt(OnTvChoices.SCENE_SECONDS, next(SCENE_SECONDS, sc.seconds)).apply());

        group(R.string.menu_group_clock);
        int clock = Math.max(0, java.util.Arrays.asList(CLOCKS).indexOf(cfg.clockFormat));
        int[] clockNames = {R.string.menu_clock_system, R.string.menu_clock_12, R.string.menu_clock_24};
        row(getString(R.string.menu_clock), null, getString(clockNames[clock]), null, null,
                () -> choices.edit().putString(Config.CLOCK_FORMAT_KEY, CLOCKS[(clock + 1) % CLOCKS.length]).apply());
        int[] positionNames = {R.string.menu_pos_auto, R.string.menu_pos_left, R.string.menu_pos_center, R.string.menu_pos_right};
        int pos = Math.max(0, java.util.Arrays.asList(Scenes.POSITIONS).indexOf(sc.position));
        row(getString(R.string.menu_clock_position), getString(R.string.menu_clock_position_sub),
                getString(positionNames[pos]), null, null,
                () -> choices.edit().putString(OnTvChoices.CLOCK_POSITION, Scenes.nextPosition(sc.position)).apply());
    }

    /** couch-tv's quick buttons: the owner ticks which round buttons stand at the top right (TopButtons). */
    private void buttons() {
        if (pickingVpn) { vpnPage(); return; }
        group(R.string.menu_group_buttons);
        // The bar's buttons in the bar's order (left to right), then the ones left off, as couch-tv's lists go.
        List<String> chosen = TopButtons.chosen(this);
        List<String> order = new ArrayList<>(chosen);
        for (String kind : TopButtons.ALL) if (!order.contains(kind)) order.add(kind);
        for (String kind : order) {
            buttonRow(kind, chosen);
            if (TopButtons.VPN.equals(kind)) {
                String app = Config.load(this).vpnApp;
                rowAction(getString(R.string.menu_vpn_app), getString(R.string.menu_vpn_app_sub),
                        app.isEmpty() ? getString(R.string.menu_vpn_none) : appLabel(app), () -> {
                            pickingVpn = true;
                            show(BUTTONS, 0);
                        });
            }
        }
        TextView note = text(getString(movingButton != null ? R.string.menu_buttons_moving : R.string.menu_buttons_note),
                14, regular, 0xB3FFFFFF);
        note.setPadding(px(16), px(10), px(16), px(6));
        list.addView(note);
    }

    /** One top button: OK puts it on the bar or takes it off; holding OK moves a shown one left and right (Up, Down). */
    private void buttonRow(String kind, List<String> chosen) {
        boolean on = chosen.contains(kind);
        String value = kind.equals(movingButton) ? getString(R.string.menu_apps_move_value) : null;
        View r = rowView(getString(TopButtons.label(kind)), null, value, getDrawable(TopButtons.icon(kind)),
                value != null ? null : getDrawable(on ? R.drawable.ic_menu_check_on : R.drawable.ic_menu_check_off));
        r.setTag(kind);
        r.setOnClickListener(v -> {
            if (movingButton == null) {
                choices.edit().putString(TopButtons.KEY, OnTvChoices.joinList(TopButtons.toggle(chosen, kind))).apply();
                changed();
            }
            movingButton = null;
            showTagged(BUTTONS, kind);
        });
        r.setOnLongClickListener(v -> {
            if (!on) return true;
            movingButton = kind;
            showTagged(BUTTONS, kind);
            return true;
        });
        r.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (kind.equals(movingButton) && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                List<String> moved = TopButtons.move(chosen, kind, code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (!moved.equals(chosen)) {
                    choices.edit().putString(TopButtons.KEY, OnTvChoices.joinList(moved)).apply();
                    changed();
                    showTagged(BUTTONS, kind);
                }
                return true;
            }
            if (movingButton != null && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return true;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                sideItems.get(section).requestFocus();
                return true;
            }
            return false;
        });
        list.addView(r, rowParams());
    }

    /** The VPN button's app: "none" (Android's VPN settings), then every app on the TV, the current one ticked. */
    private void vpnPage() {
        head.setText(R.string.menu_vpn_pick);
        String current = Config.load(this).vpnApp;
        Drawable on = getDrawable(R.drawable.ic_menu_check_on), off = getDrawable(R.drawable.ic_menu_check_off);
        pickRow(getString(R.string.menu_vpn_none), null, current.isEmpty() ? on : off, "");
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.Map<String, android.content.pm.ResolveInfo> apps = new java.util.TreeMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(probe, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                String label = String.valueOf(r.loadLabel(pm));
                boolean known = false;
                for (android.content.pm.ResolveInfo seen : apps.values()) known |= seen.activityInfo.packageName.equals(pkg);
                if (!known) apps.put(label.toLowerCase(java.util.Locale.ROOT) + "\u0000" + pkg, r);
            }
        }
        for (android.content.pm.ResolveInfo r : apps.values()) {
            String pkg = r.activityInfo.packageName;
            pickRow(String.valueOf(r.loadLabel(pm)), r.loadIcon(pm), pkg.equals(current) ? on : off, pkg);
        }
    }

    private void pickRow(String title, Drawable icon, Drawable mark, String pkg) {
        View r = rowView(title, null, null, icon, mark);
        r.setOnClickListener(v -> {
            choices.edit().putString(OnTvChoices.VPN_APP, pkg).apply();
            changed();
            pickingVpn = false;
            show(BUTTONS, 0);
        });
        list.addView(r, rowParams());
    }

    private String appLabel(String pkg) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    private void weather() {
        if (searching) { searchPage(); return; }
        WeatherClient.Settings w = WeatherClient.Settings.load(this);
        row(getString(R.string.menu_weather_show), getString(R.string.menu_weather_sub), onOff(w.on), null, null,
                () -> choices.edit().putBoolean(OnTvChoices.WEATHER, !w.on).apply());
        group(R.string.menu_group_place);
        String place = w.hasPlace() ? (w.place.isEmpty() ? String.format(Locale.US, "%.2f, %.2f", w.latitude, w.longitude)
                : w.place) : getString(R.string.menu_weather_place_none);
        rowAction(getString(R.string.menu_weather_place), null, place, this::searchCity);
    }

    private void apps() {
        Config cfg = Config.load(this);
        if (!DockStyle.small(this)) {
            group(R.string.menu_group_layout);
            row(getString(R.string.menu_rows), null, String.valueOf(cfg.rowsBelowDock), null, null,
                    () -> choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, (cfg.rowsBelowDock + 1) % 4).apply());
        }
        if (plan == null) plan = loadPlan(cfg);
        // The dock's size, 4 to 9 apps: the dock's band is as long as the apps in it.
        group(R.string.menu_group_dock);
        row(getString(R.string.menu_columns), null, String.valueOf(plan.size), null, null, () -> {
            int next = plan.size >= Config.DOCK_MAX ? Config.DOCK_MIN : Math.max(Config.DOCK_MIN, plan.size + 1);
            choices.edit().putInt("columns", next).apply();
            plan = new AppsPlan(plan.home, next, plan.hidden);
        });
        boolean libraryTile = choices.getBoolean(OnTvChoices.LIBRARY_TILE, true);
        row(getString(R.string.menu_library_tile), null, onOff(libraryTile), null, null,
                () -> choices.edit().putBoolean(OnTvChoices.LIBRARY_TILE, !libraryTile).apply());
        // couch-tv's Apps list: the dock, More apps (behind "…") and hidden, each app with its icon and where it is.
        appsGroup(getString(R.string.menu_apps_dock, plan.docked), plan.home.subList(0, plan.docked));
        appsGroup(getString(R.string.menu_apps_more), plan.home.subList(plan.docked, plan.home.size()));
        appsGroup(getString(R.string.menu_apps_hidden), plan.hidden);
        TextView note = text(getString(moving != null ? R.string.menu_apps_moving : R.string.menu_apps_note), 14, regular,
                0xB3FFFFFF);
        note.setPadding(px(16), px(10), px(16), px(6));
        list.addView(note);
    }

    private void appsGroup(String name, List<String> keys) {
        if (keys.isEmpty()) return;
        TextView g = text(name.toUpperCase(java.util.Locale.getDefault()), 12, medium, 0x99FFFFFF);
        g.setLetterSpacing(0.08f);
        g.setPadding(px(16), px(14), 0, px(6));
        list.addView(g);
        for (String key : keys) appRow(key);
    }

    private void appRow(String key) {
        int state = plan.state(key);
        String where = key.equals(moving) ? getString(R.string.menu_apps_move_value)
                : getString(state == AppsPlan.DOCK ? R.string.menu_apps_in_dock
                        : state == AppsPlan.MORE ? R.string.menu_apps_in_more : R.string.menu_apps_is_hidden);
        String[] app = appInfo(key);
        if (app == null) return;   // uninstalled since
        View r = rowView(app[0], null, where, appIcon(key), null);
        r.setTag(key);
        if (key.equals(moving)) r.setActivated(true);
        r.setOnClickListener(v -> {
            if (moving != null) {
                moving = null;
                showApp(key);
                return;
            }
            setPlan(plan.cycle(key));
            showApp(key);
        });
        r.setOnLongClickListener(v -> {
            if (plan.state(key) == AppsPlan.HIDDEN) return true;   // hidden apps keep no order
            moving = key;
            showApp(key);
            return true;
        });
        View.OnKeyListener left = (v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (key.equals(moving) && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                AppsPlan next = plan.move(key, code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                if (next != plan) {
                    setPlan(next);
                    showApp(key);
                }
                return true;
            }
            if (moving != null && code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return true;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                sideItems.get(section).requestFocus();
                return true;
            }
            return false;
        };
        r.setOnKeyListener(left);
        list.addView(r, rowParams());
    }

    /** The Apps list again, the focus on {@code key}'s row (where the app now is). */
    private void showApp(String key) {
        showTagged(APPS, key);
    }

    /** Section {@code s} again, the focus on the row tagged {@code tag}, scrolled into view. */
    private void showTagged(int s, String tag) {
        String key = tag;
        show(s, -1);
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (key.equals(v.getTag())) {
                v.requestFocus();
                v.post(() -> scroller.smoothScrollTo(0, Math.max(0, v.getTop() - scroller.getHeight() / 3)));
                return;
            }
        }
        focusList();
    }

    private AppsPlan loadPlan(Config cfg) {
        android.content.pm.PackageManager pm = getPackageManager();
        List<String> home = new ArrayList<>();
        java.util.Set<String> homePkgs = new java.util.HashSet<>();
        for (String key : HomeTiles.load(this)) {
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(key);
            if (cn == null || cn.getPackageName().equals(getPackageName()) || appInfo(key) == null) continue;
            home.add(key);
            homePkgs.add(cn.getPackageName());
        }
        java.util.TreeMap<String, String> hidden = new java.util.TreeMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>(homePkgs);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(probe, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                String key = new android.content.ComponentName(pkg, r.activityInfo.name).flattenToShortString();
                hidden.put(String.valueOf(r.loadLabel(pm)).toLowerCase(java.util.Locale.ROOT) + "\u0000" + key, key);
            }
        }
        return new AppsPlan(home, cfg.columns, new ArrayList<>(hidden.values()));
    }

    /** Saves a new plan: Order (and the dock size), and which apps left Home or came back to it; Home rebuilds. */
    private void setPlan(AppsPlan next) {
        AppsPlan before = plan;
        plan = next;
        Config cfg = Config.load(this);
        for (String key : before.hidden) {
            if (!next.hidden.contains(key)) LibraryApps.save(this, pkgOf(key), true);
        }
        for (String key : next.hidden) {
            if (!before.hidden.contains(key)) LibraryApps.save(this, pkgOf(key), false);
        }
        Order.save(Order.file(this), new Order(Order.dockKey(cfg.dockApps), next.home, cfg.fillDock ? -1 : next.docked));
        HomeTiles.save(this, next.home);
        changed();
    }

    private static String pkgOf(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }

    /** {label} for a tile key, or null when its app is gone. */
    private String[] appInfo(String key) {
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(key);
        if (cn == null) return null;
        android.content.pm.PackageManager pm = getPackageManager();
        try {
            return new String[] {String.valueOf(pm.getActivityInfo(cn, 0).loadLabel(pm))};
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private Drawable appIcon(String key) {
        android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(key);
        try {
            return getPackageManager().getActivityIcon(cn);
        } catch (Exception e) {
            return null;
        }
    }

    private void more() {
        rowAction(getString(R.string.menu_all_settings), getString(R.string.menu_all_settings_sub), null,
                () -> open(new Intent(this, LauncherSettingsActivity.class)));
        rowAction(getString(R.string.menu_default_home), null, null, () -> open(new Intent(this, HomeSettingsActivity.class)));
        rowAction(getString(R.string.menu_tv_settings), null, null,
                () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        group(R.string.menu_group_airplay);
        if (!AirPlayGuard.installed(this)) {
            info(getString(R.string.menu_airplay_missing), getString(R.string.menu_airplay_missing_sub), null);
        } else {
            boolean on = AirPlayGuard.enabled(this);
            row(getString(R.string.menu_airplay_guard), getString(R.string.menu_airplay_guard_sub), onOff(on), null, null,
                    () -> choices.edit().putBoolean(AirPlayGuard.KEY, !on).apply());
            info(getString(R.string.menu_airplay_restart), null, getString(AirPlayGuard.canRestart(this)
                    ? R.string.menu_airplay_restart_full : R.string.menu_airplay_restart_start_only));
            if (on) rowAction(getString(R.string.menu_airplay_check), null, null, () -> {
                AirPlayGuard.check(this, true);
                Toast.makeText(this, R.string.menu_airplay_checking, Toast.LENGTH_SHORT).show();
            });
        }
        group(R.string.menu_group_reset);
        row(getString(R.string.menu_follow_theme), getString(R.string.menu_follow_theme_sub), null, null, null, () ->
                OnTvChoices.clear(choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY)
                        .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY)).apply());
    }

    private void about() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "?";
        }
        info(getString(R.string.menu_version), null, version);
        info(getString(R.string.menu_based_on), null, getString(R.string.menu_based_on_value));
    }

    // ---- city search ----------------------------------------------------------------------------

    @Override
    public void onBackPressed() {
        // Back leaves a sub-page (the city search, the VPN app list) for its section first, as couch-tv's do.
        if (moving != null) {
            String key = moving;
            moving = null;
            showApp(key);
            return;
        }
        if (movingButton != null) {
            String kind = movingButton;
            movingButton = null;
            showTagged(BUTTONS, kind);
            return;
        }
        if (pickingVpn) {
            pickingVpn = false;
            show(BUTTONS, 0);
            return;
        }
        if (searching) {
            searching = false;
            show(WEATHER, 1);
            return;
        }
        super.onBackPressed();
    }

    private void searchCity() {
        searching = true;
        found = null;
        status = null;
        show(WEATHER, 0);
        showKeyboard();
    }

    /** The city search, in the menu's own look: a glass field, then the places found as rows. */
    private void searchPage() {
        head.setText(R.string.menu_weather_search);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(query);
        input.setSelection(query.length());
        input.setHint(R.string.menu_weather_search_hint);
        input.setHintTextColor(0x80FFFFFF);
        input.setTextColor(0xFFFFFFFF);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        input.setTypeface(medium);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setPadding(px(20), px(14), px(20), px(14));
        StateListDrawable field = new StateListDrawable();
        field.addState(new int[] {android.R.attr.state_focused}, solid(px(14), 0x33FFFFFF, 0xCCFFFFFF));
        field.addState(new int[] {}, solid(px(14), 0x1FFFFFFF, 0x1AFFFFFF));
        input.setBackground(field);
        input.setCompoundDrawablePadding(px(12));
        Drawable glass = getDrawable(R.drawable.ic_menu_search).mutate();
        glass.setTint(0xCCFFFFFF);
        glass.setBounds(0, 0, px(22), px(22));
        input.setCompoundDrawablesRelative(glass, null, null, null);
        input.setOnEditorActionListener((v, action, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE && !enter) return false;
            query = v.getText().toString();
            geocode(query);
            return true;
        });
        list.addView(input, rowParams());
        if (status != null) info(status, null, null);
        if (found != null) for (String[] place : found) {
            rowAction(place[1], null, null, () -> {
                choices.edit().putString(OnTvChoices.WEATHER_PLACE, place[0]).putString(OnTvChoices.WEATHER_LAT, place[2])
                        .putString(OnTvChoices.WEATHER_LON, place[3]).putBoolean(OnTvChoices.WEATHER, true).apply();
                changed();
                searching = false;
                show(WEATHER, 1);
            });
        }
    }

    private void showKeyboard() {
        View f = getCurrentFocus();
        if (!(f instanceof EditText)) return;
        f.post(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(f, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(list.getWindowToken(), 0);
    }

    private void geocode(String name) {
        if (name.trim().isEmpty()) return;
        found = null;
        status = getString(R.string.menu_weather_searching);
        show(WEATHER, 0);
        String url = Weather.geocodeUrl(name, Locale.getDefault().getLanguage());
        worker.execute(() -> {
            List<String[]> places = new ArrayList<>();
            boolean failed = false;
            try {
                JSONArray results = new JSONObject(get(url)).optJSONArray("results");
                if (results != null) for (int i = 0; i < results.length(); i++) {
                    JSONObject r = results.getJSONObject(i);
                    String label = Weather.placeLabel(r.optString("name"), r.optString("admin1"), r.optString("country"));
                    places.add(new String[] {r.optString("name"), label,
                            String.valueOf(r.getDouble("latitude")), String.valueOf(r.getDouble("longitude"))});
                }
            } catch (Exception e) {
                failed = true;
            }
            boolean error = failed;
            handler.post(() -> {
                if (isFinishing() || !searching) return;
                if (error) status = getString(R.string.menu_weather_failed);
                else if (places.isEmpty()) status = getString(R.string.menu_weather_none_found);
                else status = null;
                found = places;
                if (!places.isEmpty()) hideKeyboard();   // the places are under it
                // The first place found takes the focus, ready for OK; with none, the field keeps it.
                show(WEATHER, places.isEmpty() ? 0 : (status == null ? 1 : 2));
            });
        });
    }

    private static String get(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 128 * 1024) throw new IllegalStateException("answer too large");
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ---- rows -----------------------------------------------------------------------------------

    private void group(int name) {
        TextView g = text(getString(name).toUpperCase(java.util.Locale.getDefault()), 12, medium, 0x99FFFFFF);
        g.setLetterSpacing(0.08f);
        g.setPadding(px(16), px(14), 0, px(6));
        list.addView(g);
    }

    /** A row that changes a choice: Home is told, and the section is drawn again with the focus kept on the row. */
    private void row(String title, String sub, String value, Drawable thumb, Drawable mark, Runnable change) {
        View r = rowView(title, sub, value, thumb, mark);
        r.setOnClickListener(v -> {
            int at = rows().indexOf(v);
            change.run();
            changed();
            show(section, Math.max(0, at));
        });
        list.addView(r, rowParams());
    }

    /** A row that opens something rather than changing a choice. */
    private void rowAction(String title, String sub, String value, Runnable action) {
        View r = rowView(title, sub, value, null, null);
        r.setOnClickListener(v -> action.run());
        list.addView(r, rowParams());
    }

    /** A row that only shows something; it still takes the focus, so the list can be scrolled to it. */
    private void info(String title, String sub, String value) {
        list.addView(rowView(title, sub, value, null, null), rowParams());
    }

    private View rowView(String title, String sub, String value, Drawable thumb, Drawable mark) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(sub == null ? 50 : 60));
        r.setPadding(px(18), px(8), px(18), px(8));
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(glass(px(14), 0xFFFFFFFF, 0x1FFFFFFF, 0x1AFFFFFF));
        ColorStateList main = states(0xFF16171B, 0xFFFFFFFF), dimmed = states(0xFF55585F, 0xB3FFFFFF);
        if (thumb != null) {
            ImageView t = new ImageView(this);
            t.setImageDrawable(thumb);
            boolean glyph = thumb instanceof android.graphics.drawable.VectorDrawable;
            boolean photo = thumb.getIntrinsicWidth() > thumb.getIntrinsicHeight() * 1.3f;
            // A glyph (the Buttons section) keeps its shape and follows the row's text colour; a scene photo fills the
            // frame; an app icon is shown whole.
            t.setScaleType(photo ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            if (!photo && !glyph) t.setBackground(null);
            if (glyph) {
                t.setDuplicateParentStateEnabled(true);   // dark on the focused (white) row
                t.setImageTintList(main);
                t.setPadding(px(15), px(3), px(15), px(3));
            }
            t.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(px(6));
            t.setBackground(shape);
            // an app icon in a square, as couch-tv's Apps list has it; a glyph or a scene photo in the wide frame
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(photo || glyph ? px(58) : px(32), px(32));
            tp.setMarginEnd(px(16));
            r.addView(t, tp);
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        TextView t = text(title, 18, medium, 0);
        t.setTextColor(main);
        t.setDuplicateParentStateEnabled(true);
        texts.addView(t);
        if (sub != null) {
            TextView s = text(sub, 13, regular, 0);
            s.setTextColor(dimmed);
            s.setDuplicateParentStateEnabled(true);
            texts.addView(s);
        }
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null) {
            TextView v = text(value, 17, regular, 0);
            v.setTextColor(dimmed);
            v.setDuplicateParentStateEnabled(true);
            v.setSingleLine(true);
            v.setPadding(px(16), 0, 0, 0);
            r.addView(v);
        }
        if (mark != null) {
            ImageView m = new ImageView(this);
            m.setImageDrawable(mark);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(px(24), px(24));
            mp.setMarginStart(px(16));
            r.addView(m, mp);
        }
        r.setOnFocusChangeListener((v, focused) -> v.animate().scaleX(focused ? 1.015f : 1f)
                .scaleY(focused ? 1.015f : 1f).setDuration(150).start());
        r.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_LEFT) {
                sideItems.get(section).requestFocus();
                return true;
            }
            return false;
        });
        return r;
    }

    private View sideItem(int i) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(px(10), px(7), px(14), px(7));
        item.setFocusable(true);
        item.setClickable(true);
        item.setBackground(glass(px(14), 0xFFFFFFFF, 0, 0x26FFFFFF));
        ImageView icon = new ImageView(this);
        Drawable d = getDrawable(SECTION_ICONS[i]).mutate();
        d.setTintList(states(0xFF16171B, 0xFFFFFFFF));
        icon.setImageDrawable(d);
        icon.setDuplicateParentStateEnabled(true);
        icon.setPadding(px(6), px(6), px(6), px(6));
        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(px(9));
        box.setColor(0x1FFFFFFF);
        icon.setBackground(box);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(px(32), px(32));
        ip.setMarginEnd(px(14));
        item.addView(icon, ip);
        TextView label = text(getString(SECTION_NAMES[i]), 18, medium, 0);
        label.setTextColor(states(0xFF16171B, 0xFFFFFFFF));
        label.setDuplicateParentStateEnabled(true);
        item.addView(label);
        item.setOnFocusChangeListener((v, focused) -> {
            if (!focused || rebuilding) return;
            if (searching || pickingVpn) { searching = false; pickingVpn = false; show(i, false); return; }
            if (section != i) show(i, false);
        });
        item.setOnClickListener(v -> focusList());
        item.setOnKeyListener((v, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                focusList();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(4);
        item.setLayoutParams(lp);
        sideItems.add(item);
        return item;
    }

    private void focusList() {
        List<View> rows = rows();
        if (!rows.isEmpty()) rows.get(0).requestFocus();
    }

    private List<View> rows() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).isFocusable()) out.add(list.getChildAt(i));
        return out;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = px(4);
        p.bottomMargin = px(4);
        return p;
    }

    // ---- helpers --------------------------------------------------------------------------------

    /** Home rebuilds with the change when it comes back (MainActivity compares this revision). */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException | NullPointerException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private static int next(int[] values, int current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    private String onOff(boolean on) { return getString(on ? R.string.menu_on : R.string.menu_off); }

    private String duration(int seconds) {
        return seconds >= 60 && seconds % 60 == 0 ? getString(R.string.menu_minutes, seconds / 60)
                : getString(R.string.menu_seconds, seconds);
    }

    private static List<String> ids(List<Scenes.Scene> scenes) {
        List<String> out = new ArrayList<>();
        for (Scenes.Scene s : scenes) out.add(s.id);
        return out;
    }

    /** A focused state in white, a selected one (the current section) in faint glass, and the plain row. */
    private static StateListDrawable glass(int radius, int focused, int plain, int selected) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[] {android.R.attr.state_focused}, solid(radius, focused, 0));
        d.addState(new int[] {android.R.attr.state_selected}, solid(radius, selected, 0));
        d.addState(new int[] {}, solid(radius, plain, plain == 0 ? 0 : 0x14FFFFFF));
        return d;
    }

    private static GradientDrawable solid(int radius, int color, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(color);
        if (stroke != 0) g.setStroke(2, stroke);
        return g;
    }

    private static ColorStateList states(int focused, int plain) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {focused, plain});
    }

    private TextView text(String s, float sp, Typeface face, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        if (color != 0) t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    private Typeface font(int res) {
        try {
            return UiFont.get(this, res);
        } catch (Exception e) {
            return Typeface.DEFAULT;
        }
    }

    private Drawable thumbnail(String id) {
        Bitmap b = decodeScene(id, 8);
        return b == null ? null : new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    /** The scene Home last showed, a few pixels across and stretched: a blur without the cost of one. */
    private Bitmap blurredScene() {
        ScenePlayer.Settings sc = ScenePlayer.Settings.load(this);
        if (!sc.on) return null;
        List<Scenes.Scene> scenes = Scenes.select(sc.ids);
        int index = getSharedPreferences("scenes", MODE_PRIVATE).getInt("index", 0);
        Bitmap small = decodeScene(scenes.get(Math.max(0, Math.min(index, scenes.size() - 1))).id, 16);
        if (small == null) return null;
        // 120x68: a few passes of a box blur, so the stretched picture is a soft wash and not visible squares.
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) MenuChoices.boxBlur(px, w, h, 4);
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private Bitmap decodeScene(String id, int sample) {
        try (InputStream in = getAssets().open("scenes/scene-" + id + ".jpg")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(in, null, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private int px(float dps) { return Math.round(dps * dp); }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
