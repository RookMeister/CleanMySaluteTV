package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

public class ClearIconTest {
    private static final int N = ClearIcon.N;

    /** A flat background with a filled square glyph in the middle. */
    private static int[] flatWithSquare(int bg, int glyph, int from, int to) {
        int[] px = new int[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            boolean in = x >= from && x < to && y >= from && y < to;
            px[y * N + x] = in ? glyph : bg;
        }
        return px;
    }

    @Test
    public void aWhiteMarkOnABlueSquareBecomesTheGlyph() {
        int[] a = ClearIcon.cut(flatWithSquare(0xFF1E5BD8, 0xFFFFFFFF, 70, 122), 0, 0, N, N);
        assertNotNull(a);
        assertEquals(255, a[96 * N + 96]);   // inside the mark: solid
        assertEquals(0, a[10 * N + 10]);     // the background: gone
    }

    @Test
    public void aGlyphFillingAlmostAllOfTheIconIsRefused() {
        assertNull(ClearIcon.cut(flatWithSquare(0xFF1E5BD8, 0xFFFFFFFF, 6, 186), 0, 0, N, N));
    }

    @Test
    public void aPlainSquareHasNoGlyph() {
        assertNull(ClearIcon.cut(flatWithSquare(0xFF333333, 0xFF333333, 0, 0), 0, 0, N, N));
    }

    @Test
    public void noiseLikeAPhotoIsRefused() {
        Random r = new Random(7);
        int[] px = new int[N * N];
        for (int i = 0; i < px.length; i++) px[i] = 0xFF000000 | r.nextInt(0xFFFFFF);
        assertNull(ClearIcon.cut(px, 0, 0, N, N));
    }

    @Test
    public void aShapeOnClearKeepsTheShapeAndCutsTheMarkOut() {
        // A red rounded box (here a square) on a see-through icon, a white mark inside it: YouTube's kind.
        int[] px = new int[N * N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            boolean box = x >= 30 && x < 162 && y >= 50 && y < 142;
            boolean mark = x >= 86 && x < 106 && y >= 86 && y < 106;
            px[y * N + x] = !box ? 0 : mark ? 0xFFFFFFFF : 0xFFFF0000;
        }
        int[] a = ClearIcon.cut(px, 0, 0, N, N);
        assertNotNull(a);
        assertTrue(a[60 * N + 40] > 200);    // the box: glass
        assertEquals(0, a[96 * N + 96]);     // the mark: a hole
        assertEquals(0, a[5 * N + 5]);       // outside: clear
    }

    @Test
    public void labOfWhiteIsBrightAndNeutral() {
        double[] w = ClearIcon.lab(255, 255, 255);
        assertEquals(100, w[0], 0.5);
        assertEquals(0, w[1], 0.5);
        assertEquals(0, w[2], 0.5);
    }
}
