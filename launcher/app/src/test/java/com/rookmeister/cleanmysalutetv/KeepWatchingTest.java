package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KeepWatchingTest {
    @Test
    public void subtitleLeavesOutWhatIsMissing() {
        assertEquals("S2 · E5 · Пилот", KeepWatching.subtitle("2", "5", "Пилот"));
        assertEquals("E5", KeepWatching.subtitle(null, " 5 ", ""));
        assertEquals("Пилот", KeepWatching.subtitle("", null, " Пилот "));
        assertNull(KeepWatching.subtitle(null, " ", null));
    }

    @Test
    public void progressIsAShareOrUnknown() {
        assertEquals(0.25f, KeepWatching.progress(15_000, 60_000), 1e-6);
        assertEquals(1f, KeepWatching.progress(90_000, 60_000), 0f);
        assertEquals(-1f, KeepWatching.progress(0, 60_000), 0f);
        assertEquals(-1f, KeepWatching.progress(10_000, 0), 0f);
    }
}
