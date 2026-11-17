package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DockStyleTest {

    @Test
    public void normalizeAcceptsOnlySmallAndLarge() {
        assertEquals("small", DockStyle.normalize(" Small "));
        assertEquals("large", DockStyle.normalize("LARGE"));
        assertNull(DockStyle.normalize("medium"));
        assertNull(DockStyle.normalize(null));
        assertNull(DockStyle.normalize(5));
    }

    @Test
    public void onTvChoiceWinsThenThemeThenBuiltIn() {
        assertEquals("large", DockStyle.pick("large", "small", "small"));
        assertEquals("small", DockStyle.pick(null, "small", "large"));
        assertEquals("large", DockStyle.pick("bogus", null, "large"));
        assertEquals("small", DockStyle.pick(null, null, null));
    }

    @Test
    public void centringPaddingSplitsTheRestEvenly() {
        assertEquals(400, DockStyle.centringPadding(1800, 5, 200));
        assertEquals(0, DockStyle.centringPadding(800, 5, 200));
    }

    @Test
    public void dockPaddingCentresTheFilledCells() {
        // 4 columns of 200 in 1800, 2 apps: they span 400 centred at 900, the 2 free cells run on to the right.
        int[] p = DockStyle.dockPadding(1800, 4, 2, 200);
        assertEquals(700, p[0]);
        assertEquals(300, p[1]);
        int[] full = DockStyle.dockPadding(1800, 4, 4, 200);
        assertEquals(500, full[0]);
        assertEquals(500, full[1]);
        int[] none = DockStyle.dockPadding(1800, 4, 0, 200);
        assertEquals(500, none[0]);
    }

    @Test
    public void smallTilesAreSquareAndFixed() {
        boolean was = AppAdapter.smallDock;
        try {
            AppAdapter.smallDock = true;
            int w = AppAdapter.tileWidthPx(1800, 1000, 7, 2, true, true, 2f);
            assertEquals(112, w);
            assertEquals(w, AppAdapter.tileHeightPx(w));
            AppAdapter.smallDock = false;
            assertEquals(Math.round(w * 0 + 360 * 101f / 180f), AppAdapter.tileHeightPx(360));
        } finally {
            AppAdapter.smallDock = was;
        }
    }
}
