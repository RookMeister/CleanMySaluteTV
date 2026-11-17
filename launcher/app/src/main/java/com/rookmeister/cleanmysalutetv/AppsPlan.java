package com.rookmeister.cleanmysalutetv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The settings menu's Apps list, as couch-tv's Home settings has it: every app is in the dock, in More apps (Home,
 * past the dock, behind "…") or hidden. OK moves an app on to the next of those, holding OK moves it up or down
 * among its neighbours.
 *
 * Home is one ordered list of tile keys (Order) whose first {@link #size} are the dock. The dock's size is the
 * owner's choice (4 to 9, Config.DOCK_MIN..DOCK_MAX) and moving apps never changes it: an app leaving the dock lets
 * the first of More apps in, and one coming in pushes the dock's last out to More apps. Hidden apps are kept apart
 * (LibraryApps' removed set). Pure lists, so the JVM tests check it; MenuSettingsActivity saves the result.
 */
final class AppsPlan {
    static final int DOCK = 0, MORE = 1, HIDDEN = 2;

    final List<String> home;
    /** The dock's size as chosen. */
    final int size;
    /** How many apps are in the dock: the size, or fewer when Home has fewer apps. */
    final int docked;
    final List<String> hidden;

    AppsPlan(List<String> home, int size, List<String> hidden) {
        this.home = Collections.unmodifiableList(new ArrayList<>(home));
        this.size = Math.max(1, size);
        this.docked = Math.min(this.size, this.home.size());
        this.hidden = Collections.unmodifiableList(new ArrayList<>(hidden));
    }

    int state(String key) {
        int i = home.indexOf(key);
        if (i < 0) return HIDDEN;
        return i < docked ? DOCK : MORE;
    }

    /**
     * OK: dock, then More apps, then hidden, then the dock again (its end). With no More apps to step in, a docked app
     * goes straight to hidden. The last app on Home stays: an empty dock has nothing to focus.
     */
    AppsPlan cycle(String key) {
        List<String> h = new ArrayList<>(home), x = new ArrayList<>(hidden);
        switch (state(key)) {
            case DOCK:
                if (home.size() <= 1) return this;
                h.remove(key);
                if (home.size() > size) {
                    // the first of More apps has moved up into the dock: the key goes in just after it
                    h.add(size, key);
                } else {
                    x.add(0, key);
                }
                break;
            case MORE:
                h.remove(key);
                x.add(0, key);
                break;
            default:
                x.remove(key);
                // the dock's end; a full dock's last app steps out to More apps
                h.add(home.size() >= size ? size - 1 : home.size(), key);
                break;
        }
        return new AppsPlan(h, size, x);
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
        return new AppsPlan(h, size, hidden);
    }
}
