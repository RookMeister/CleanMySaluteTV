package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DockGlassTest {
    @Test
    public void seventyPercentIsCouchTvsGlass() {
        assertEquals(Math.round(0.32f * 255), DockDecoration.glassAlpha(70), 1);
    }

    @Test
    public void theEndsAreSolidAndClear() {
        assertEquals(255, DockDecoration.glassAlpha(0));
        assertEquals(0, DockDecoration.glassAlpha(100));
        assertEquals(255, DockDecoration.glassAlpha(-20));
        assertEquals(0, DockDecoration.glassAlpha(150));
    }
}
