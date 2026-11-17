package com.rookmeister.cleanmysalutetv;

import java.util.ArrayList;
import java.util.List;

/** CleanMySaluteTV settings menu: the choices' arithmetic, kept apart from the views so the JVM tests can check it. */
final class MenuChoices {
    private MenuChoices() {}

    /** One horizontal and one vertical pass of a box blur of {@code radius}, in place, over opaque ARGB pixels. */
    static void boxBlur(int[] px, int w, int h, int radius) {
        int[] tmp = new int[px.length];
        pass(px, tmp, w, h, radius, true);
        pass(tmp, px, w, h, radius, false);
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0, n = 0;
                for (int k = Math.max(0, i - r); k <= Math.min(len - 1, i + r); k++) {
                    int c = src[horizontal ? line * w + k : k * w + line];
                    rs += (c >> 16) & 0xFF; gs += (c >> 8) & 0xFF; bs += c & 0xFF; n++;
                }
                dst[horizontal ? line * w + i : i * w + line] = 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
            }
        }
    }

    /**
     * The scenes left after switching {@code id} in or out of {@code chosen}, in the built-in order {@code all}. The
     * last scene cannot be switched off: with none left there would be nothing to show (switch the scenes off instead).
     */
    static List<String> toggleScene(List<String> all, List<String> chosen, String id) {
        List<String> out = new ArrayList<>();
        boolean removing = chosen.contains(id);
        if (removing && chosen.size() <= 1) return new ArrayList<>(chosen);
        for (String s : all) {
            boolean keep = s.equals(id) ? !removing : chosen.contains(s);
            if (keep) out.add(s);
        }
        return out;
    }
}
