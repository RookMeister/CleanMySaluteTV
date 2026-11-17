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
    private static final int LOOK = 0, WEATHER = 1, APPS = 2, MORE = 3, ABOUT = 4;
    private static final int[] SECTION_NAMES = {R.string.menu_look, R.string.menu_weather, R.string.menu_apps,
            R.string.menu_more, R.string.menu_about};
    private static final int[] SECTION_ICONS = {R.drawable.ic_menu_look, R.drawable.ic_wx_cloud, R.drawable.ic_menu_apps,
            R.drawable.ic_menu_more, R.drawable.ic_menu_info};
    private static final int[] SCENE_NAMES = {R.string.menu_scene_night, R.string.menu_scene_golden,
            R.string.menu_scene_water, R.string.menu_scene_fog, R.string.menu_scene_dawn};
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
        boolean small = DockStyle.small(this);
        group(R.string.menu_group_layout);
        row(getString(R.string.menu_columns), null, String.valueOf(cfg.columns), null, null,
                () -> choices.edit().putInt("columns", cfg.columns >= 7 ? 4 : cfg.columns + 1).apply());
        if (small) {
            info(getString(R.string.menu_rows), getString(R.string.menu_rows_small), "0");
        } else {
            row(getString(R.string.menu_rows), null, String.valueOf(cfg.rowsBelowDock), null, null,
                    () -> choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, (cfg.rowsBelowDock + 1) % 4).apply());
        }
        group(R.string.menu_group_order);
        rowAction(getString(R.string.menu_reorder), getString(R.string.menu_reorder_sub), null, () -> {
            setResult(RESULT_OK, new Intent().putExtra(LauncherSettingsActivity.EXTRA_REORDER, true));
            finish();
        });
    }

    private void more() {
        rowAction(getString(R.string.menu_all_settings), getString(R.string.menu_all_settings_sub), null,
                () -> open(new Intent(this, LauncherSettingsActivity.class)));
        rowAction(getString(R.string.menu_default_home), null, null, () -> open(new Intent(this, HomeSettingsActivity.class)));
        rowAction(getString(R.string.menu_tv_settings), null, null,
                () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
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
        // Back leaves the city search for the Weather section first, as couch-tv's sub-pages do.
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
        TextView g = text(getString(name), 12, medium, 0x99FFFFFF);
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
            t.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.setClipToOutline(true);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(px(6));
            t.setBackground(shape);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(px(58), px(32));
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
            if (searching) { searching = false; show(i, false); return; }
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
