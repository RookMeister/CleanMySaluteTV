package com.rookmeister.cleanmysalutetv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The settings menu's Apps list, as couch-tv's Home settings has it: every app is in the dock, in More apps (Home,
 * past the dock, behind "…") or hidden. OK moves an app on to the next of those, holding OK moves it up or down
 * among its neighbours.
 *
 * Home is one ordered list of tile keys (Order) whose first {@link #docked} are the dock; hidden apps are kept apart
 * (LibraryApps' removed set). Pure lists, so the JVM tests check it; MenuSettingsActivity saves the result.
 */
final class AppsPlan {
    static final int DOCK = 0, MORE = 1, HIDDEN = 2;
    /** The dock keeps at least one app, and no more than fit across the screen. */
    static final int MIN_DOCK = 1, MAX_DOCK = 10;

    final List<String> home;
    final int docked;
    final List<String> hidden;
    /** Every Home app is in the dock (dock_all_apps): there is no More apps. */
    final boolean allDocked;

    AppsPlan(List<String> home, int docked, List<String> hidden, boolean allDocked) {
        this.home = Collections.unmodifiableList(new ArrayList<>(home));
        this.allDocked = allDocked;
        this.docked = allDocked ? this.home.size() : Math.max(0, Math.min(docked, this.home.size()));
        this.hidden = Collections.unmodifiableList(new ArrayList<>(hidden));
    }

    int state(String key) {
        int i = home.indexOf(key);
        if (i < 0) return HIDDEN;
        return i < docked ? DOCK : MORE;
    }

    /**
     * OK: dock, then More apps, then hidden, then the dock again (its end). With every app docked there is no More
     * apps. The last app in the dock stays: an empty dock has nothing to focus.
     */
    AppsPlan cycle(String key) {
        List<String> h = new ArrayList<>(home), x = new ArrayList<>(hidden);
        int d = docked;
        switch (state(key)) {
            case DOCK:
                if (docked <= MIN_DOCK) return this;
                h.remove(key);
                d--;
                if (allDocked) {
                    x.add(0, key);
                } else {
                    h.add(d, key);   // the first of More apps
                }
                break;
            case MORE:
                h.remove(key);
                x.add(0, key);
                break;
            default:
                if (!allDocked && docked >= MAX_DOCK) {
                    // a full dock: back to Home as the first of More apps
                    x.remove(key);
                    h.add(docked, key);
                    break;
                }
                x.remove(key);
                h.add(d, key);
                d++;
                break;
        }
        return new AppsPlan(h, d, x, allDocked);
    }

    /** Holding OK: one step up ({@code -1}) or down ({@code 1}) inside the app's own group; hidden apps keep no order. */
    AppsPlan move(String key, int step) {
        int i = home.indexOf(key);
        if (i < 0) return this;
        int from = state(key) == DOCK ? 0 : docked, to = state(key) == DOCK ? docked : home.size();
        int j = i + step;
        if (j < from || j >= to) return this;
        List<String> h = new ArrayList<>(home);
        Collections.swap(h, i, j);
        return new AppsPlan(h, docked, hidden, allDocked);
    }
}
