package com.rookmeister.cleanmysalutetv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Clear app icons, after Apple's "clear" look, ported from Seagey/couch-tv tv-home/web/clearicon.js (MIT): an app's
 * own icon with its background taken away, so only the glyph is left, drawn white on a glass tile.
 *
 * How: the icon is drawn at {@link #N} px; the colours along its edges are the background (one colour, or the steps
 * of a gradient); every pixel is scored by how far it is from the nearest of them, and that score is the glyph's
 * alpha. An icon that will not split cleanly (a photo, or a glyph that fills nearly all of it) is refused, and the
 * tile shows a grey version instead.
 *
 * Pure arithmetic on ARGB pixels, so the JVM tests can check it; AppAdapter draws the icon and keeps the result.
 */
final class ClearIcon {
    private ClearIcon() {}

    /** Working size, px. */
    static final int N = 192;
    /** Lab distance: under T0 is background, over T1 is fully glyph. */
    private static final double T0 = 16, T1 = 42;

    static double[] lab(int r8, int g8, int b8) {
        double r = r8 / 255.0, g = g8 / 255.0, b = b8 / 255.0;
        r = r > 0.04045 ? Math.pow((r + 0.055) / 1.055, 2.4) : r / 12.92;
        g = g > 0.04045 ? Math.pow((g + 0.055) / 1.055, 2.4) : g / 12.92;
        b = b > 0.04045 ? Math.pow((b + 0.055) / 1.055, 2.4) : b / 12.92;
        double x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
        double y = r * 0.2126 + g * 0.7152 + b * 0.0722;
        double z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
        x = f(x); y = f(y); z = f(z);
        return new double[] {116 * y - 16, 500 * (x - y), 200 * (y - z)};
    }

    private static double f(double t) {
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116.0;
    }

    private static double dist(double[] a, double[] b) {
        double dl = a[0] - b[0], da = a[1] - b[1], db = a[2] - b[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static int alpha(int p) { return p >>> 24; }
    private static int red(int p) { return (p >> 16) & 0xFF; }
    private static int green(int p) { return (p >> 8) & 0xFF; }
    private static int blue(int p) { return p & 0xFF; }

    /**
     * @param px  the icon drawn into an N x N square, ARGB, row by row; {@code ox, oy, dw, dh} is where it was drawn
     *            (a square icon fills it: 0, 0, N, N)
     * @return the glyph's alpha, N x N values 0..255 (the glyph is white), or null when the icon does not split cleanly
     */
    static int[] cut(int[] px, float ox, float oy, float dw, float dh) {
        int[] shape = shapeCut(px, ox, oy, dw, dh);
        if (shape != null) return shape.length == 0 ? null : shape;

        // The background: colours in a band just inside the edges, quantised, the common ones kept (the outer 4%:
        // wide wordmarks reach to about 8% from the edge).
        Map<Integer, Integer> bins = new HashMap<>();
        int total = 0;
        int band = Math.max(3, Math.round(Math.min(dw, dh) * 0.04f)), inner = Math.round(Math.min(dw, dh) * 0.12f);
        int x0 = Math.round(ox), y0 = Math.round(oy), x1 = Math.round(ox + dw) - 1, y1 = Math.round(oy + dh) - 1;
        for (int py = y0; py <= y1; py++) {
            for (int qx = x0; qx <= x1; qx++) {
                int e = Math.min(Math.min(qx - x0, x1 - qx), Math.min(py - y0, y1 - py));
                if (e < 1 || e > band) continue;
                int p = px[py * N + qx];
                if (alpha(p) < 200) continue;
                int k = ((red(p) >> 3) << 10) | ((green(p) >> 3) << 5) | (blue(p) >> 3);
                bins.merge(k, 1, Integer::sum);
                total++;
            }
        }
        if (total < 50) return null;
        List<double[]> pal = new ArrayList<>();
        for (Map.Entry<Integer, Integer> b : bins.entrySet()) {
            if ((double) b.getValue() / total < 0.004) continue;
            int k = b.getKey();
            pal.add(lab(((k >> 10) & 31) * 8 + 4, ((k >> 5) & 31) * 8 + 4, (k & 31) * 8 + 4));
        }
        if (pal.isEmpty() || pal.size() > 600) return null;

        // Each pixel's distance to the nearest background colour.
        double[] dbg = new double[N * N];
        Map<Integer, Double> memo = new HashMap<>();
        for (int j = 0; j < N * N; j++) {
            int p = px[j];
            if (alpha(p) < 128) { dbg[j] = -1; continue; }
            int key = ((red(p) >> 2) << 12) | ((green(p) >> 2) << 6) | (blue(p) >> 2);
            Double v = memo.get(key);
            if (v == null) {
                double[] l = lab(red(p), green(p), blue(p));
                double best = 1e9;
                for (double[] c : pal) best = Math.min(best, dist(l, c));
                v = best;
                memo.put(key, v);
            }
            dbg[j] = v;
        }

        // The glyph's own colours, most used first. A background with a pale glow near a white glyph needs a looser
        // "clearly not background" before any glyph pixels count.
        List<double[]> gpal = new ArrayList<>();
        for (double lim : new double[] {T1, 28, 22}) {
            Map<Integer, Integer> gbins = new HashMap<>();
            int gtotal = 0;
            for (int k = 0; k < N * N; k++) {
                if (dbg[k] <= lim) continue;
                int p = px[k];
                int gk = ((red(p) >> 4) << 8) | ((green(p) >> 4) << 4) | (blue(p) >> 4);
                gbins.merge(gk, 1, Integer::sum);
                gtotal++;
            }
            if (gtotal < N * N * 0.004) continue;
            List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(gbins.entrySet());
            sorted.sort((u, w) -> Integer.compare(w.getValue(), u.getValue()));
            for (Map.Entry<Integer, Integer> g : sorted) {
                if ((double) g.getValue() / gtotal < 0.03) continue;
                int k = g.getKey();
                gpal.add(lab(((k >> 8) & 15) * 16 + 8, ((k >> 4) & 15) * 16 + 8, (k & 15) * 16 + 8));
            }
            break;
        }
        if (gpal.isEmpty() || gpal.size() > 12) return null;
        // Colours close to the main one count as it (shading, a gradient in the letters).
        boolean[] mainSet = new boolean[gpal.size()];
        for (int i = 0; i < gpal.size(); i++) mainSet[i] = dist(gpal.get(i), gpal.get(0)) < 30;

        // Alpha: which side a pixel is nearer, the background or the glyph (soft at the edges), so a hole cut in the
        // glyph in a colour close to the background stays a hole.
        int[] out = new int[N * N];
        double sum = 0;
        int cnt = 0, edgeHits = 0, edgeN = 0;
        Map<Integer, double[]> memo2 = new HashMap<>();
        for (int j = 0; j < N * N; j++) {
            double a = 0, db0 = dbg[j];
            if (db0 > T0 * 0.5) {
                int p = px[j];
                int key = ((red(p) >> 2) << 12) | ((green(p) >> 2) << 6) | (blue(p) >> 2);
                double[] dg = memo2.get(key);
                if (dg == null) {
                    double[] l = lab(red(p), green(p), blue(p));
                    int bi = 0;
                    double bd = 1e9;
                    for (int m = 0; m < gpal.size(); m++) {
                        double d = dist(l, gpal.get(m));
                        if (d < bd) { bd = d; bi = m; }
                    }
                    dg = new double[] {bd, bi};
                    memo2.put(key, dg);
                }
                double r = db0 / (db0 + dg[0] + 1e-6);
                a = Math.max(0, Math.min(1, (r - 0.36) / 0.28));
                a = a * a * (3 - 2 * a);
                // A second glyph colour shows at part strength, so the detail inside a glyph is not lost.
                if (!mainSet[(int) dg[1]]) a *= 0.42;
            }
            out[j] = (int) Math.round(a * 255);
            int jx = j % N, jy = j / N;
            int ee = Math.min(Math.min(jx - x0, x1 - jx), Math.min(jy - y0, y1 - jy));
            if (ee >= inner) { sum += a; cnt++; }
            else if (ee >= 0 && ee < band) { edgeN++; if (a > 0.5) edgeHits++; }
        }
        double cover = cnt > 0 ? sum / cnt : 0;
        // A glyph is neither a speck nor the whole icon, and the outer edge is background.
        if (cover < 0.006 || cover > 0.62 || (edgeN > 0 && (double) edgeHits / edgeN > 0.25)) return null;
        return out;
    }

    /**
     * A logo that is itself a shape on a see-through square (YouTube's red rounded box): the shape stays as glass and
     * the mark inside is cut out of it as a hole. Null: not that kind of icon; an empty array: that kind, refused.
     */
    private static int[] shapeCut(int[] px, float ox, float oy, float dw, float dh) {
        int band = Math.max(3, Math.round(Math.min(dw, dh) * 0.04f)), clear = 0, n = 0;
        int x0 = Math.round(ox), y0 = Math.round(oy), x1 = Math.round(ox + dw) - 1, y1 = Math.round(oy + dh) - 1;
        for (int py = y0; py <= y1; py++) for (int qx = x0; qx <= x1; qx++) {
            if (Math.min(Math.min(qx - x0, x1 - qx), Math.min(py - y0, y1 - py)) >= band) continue;
            n++;
            if (alpha(px[py * N + qx]) < 128) clear++;
        }
        if (n == 0 || (double) clear / n < 0.6) return null;
        Map<Integer, Integer> bins = new HashMap<>();
        int best = -1, bestN = 0;
        for (int p : px) {
            if (alpha(p) < 200) continue;
            int k = ((red(p) >> 4) << 8) | ((green(p) >> 4) << 4) | (blue(p) >> 4);
            int c = bins.merge(k, 1, Integer::sum);
            if (c > bestN) { bestN = c; best = k; }
        }
        if (best < 0) return new int[0];
        double[] d = lab(((best >> 8) & 15) * 16 + 8, ((best >> 4) & 15) * 16 + 8, (best & 15) * 16 + 8);
        int[] out = new int[N * N];
        Map<Integer, Double> memo = new HashMap<>();
        double sum = 0;
        for (int j = 0; j < N * N; j++) {
            int p = px[j];
            int key = ((red(p) >> 2) << 12) | ((green(p) >> 2) << 6) | (blue(p) >> 2);
            Double dd = memo.get(key);
            if (dd == null) {
                dd = dist(lab(red(p), green(p), blue(p)), d);
                memo.put(key, dd);
            }
            double a = Math.max(0, Math.min(1, (45 - dd) / 25)) * (alpha(p) / 255.0);
            out[j] = (int) Math.round(a * 255);
            sum += a;
        }
        double cover = sum / (N * N);
        if (cover < 0.05 || cover > 0.95) return new int[0];
        return out;
    }
}
