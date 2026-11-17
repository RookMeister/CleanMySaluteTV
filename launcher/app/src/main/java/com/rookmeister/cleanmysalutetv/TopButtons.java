package com.rookmeister.cleanmysalutetv;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CleanMySaluteTV: which round buttons stand at the top right, and in what order, as couch-tv's quick buttons. The
 * owner picks them in the settings menu (MenuSettingsActivity, "Buttons"); the choice is saved with the other on-TV
 * choices. Four were there before (inputs, network, VPN, the gear) and stay the default.
 */
final class TopButtons {
    private TopButtons() {}

    static final String KEY = "top_buttons";
    static final String INPUTS = "inputs", NETWORK = "network", VPN = "vpn", SETTINGS = "settings";
    static final String SLEEP = "sleep", PICTURE = "picture", SCREEN_OFF = "screen_off", BLUETOOTH = "bluetooth",
            SOUND = "sound", SCREENSAVER = "screensaver", APPS = "apps", TV_SETTINGS = "tv_settings", MEMORY = "memory";

    /** Every button, in the order the bar shows them. */
    static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            SLEEP, PICTURE, SOUND, SCREENSAVER, SCREEN_OFF, BLUETOOTH, MEMORY, INPUTS, NETWORK, VPN, APPS, TV_SETTINGS, SETTINGS));
    static final List<String> DEFAULT = Collections.unmodifiableList(Arrays.asList(INPUTS, NETWORK, VPN, SETTINGS));

    static List<String> chosen(Context c) {
        String saved = OnTvChoices.prefs(c).getString(KEY, null);
        return saved == null ? DEFAULT : parse(saved);
    }

    /** "sleep,inputs" to the known buttons in the saved order (the owner's); unknown names and repeats are dropped. */
    static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        for (String k : OnTvChoices.splitList(text)) {
            String name = k.trim();
            if (ALL.contains(name) && !out.contains(name)) out.add(name);
        }
        return out;
    }

    /**
     * {@code chosen} with {@code kind} switched off, or on: a new button goes last, before the gear when the gear
     * ends the bar.
     */
    static List<String> toggle(List<String> chosen, String kind) {
        List<String> out = new ArrayList<>(chosen);
        if (out.remove(kind)) return out;
        int at = !out.isEmpty() && SETTINGS.equals(out.get(out.size() - 1)) ? out.size() - 1 : out.size();
        out.add(at, kind);
        return out;
    }

    /** {@code kind} one place to the left ({@code -1}) or right ({@code 1}) on the bar; unchanged at either end. */
    static List<String> move(List<String> chosen, String kind, int step) {
        List<String> out = new ArrayList<>(chosen);
        int i = out.indexOf(kind), j = i + step;
        if (i < 0 || j < 0 || j >= out.size()) return out;
        Collections.swap(out, i, j);
        return out;
    }

    static int icon(String kind) {
        switch (kind) {
            case SLEEP: return R.drawable.ic_cp_timer;
            case PICTURE: return R.drawable.ic_picture;
            case SOUND: return R.drawable.ic_cp_sound;
            case SCREENSAVER: return R.drawable.ic_cp_clock;
            case SCREEN_OFF: return R.drawable.ic_cp_power;
            case BLUETOOTH: return R.drawable.ic_cp_bluetooth;
            case MEMORY: return R.drawable.ic_cp_memory;
            case INPUTS: return R.drawable.ic_input;
            case NETWORK: return R.drawable.ic_wifi;
            case VPN: return R.drawable.ic_lock;
            case APPS: return R.drawable.ic_apps;
            case TV_SETTINGS: return R.drawable.ic_menu_more;
            default: return R.drawable.ic_settings;   // SETTINGS: the gear, which opens the control panel
        }
    }

    static int label(String kind) {
        switch (kind) {
            case SLEEP: return R.string.cp_timer;
            case PICTURE: return R.string.cp_picture;
            case SOUND: return R.string.cp_sound;
            case SCREENSAVER: return R.string.cp_screensaver;
            case SCREEN_OFF: return R.string.cp_screen_off;
            case BLUETOOTH: return R.string.cp_bluetooth;
            case MEMORY: return R.string.cp_memory;
            case INPUTS: return R.string.inputs;
            case NETWORK: return R.string.cp_wifi;
            case VPN: return R.string.tb_vpn;
            case APPS: return R.string.cp_all_apps;
            case TV_SETTINGS: return R.string.tv_settings;
            default: return R.string.tb_panel;
        }
    }
}
