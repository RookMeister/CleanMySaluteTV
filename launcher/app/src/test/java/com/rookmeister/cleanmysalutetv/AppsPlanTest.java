package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class AppsPlanTest {
    private static AppsPlan plan(int docked, boolean all, String... home) {
        return new AppsPlan(Arrays.asList(home), docked, Collections.singletonList("x"), all);
    }

    @Test
    public void okGoesDockMoreHiddenAndBackToTheDockEnd() {
        AppsPlan p = plan(2, false, "a", "b", "c");
        p = p.cycle("a");
        assertEquals(Arrays.asList("b", "a", "c"), p.home);
        assertEquals(1, p.docked);
        assertEquals(AppsPlan.MORE, p.state("a"));
        p = p.cycle("a");
        assertEquals(Arrays.asList("b", "c"), p.home);
        assertEquals(Arrays.asList("a", "x"), p.hidden);
        p = p.cycle("a");
        assertEquals(Arrays.asList("b", "a", "c"), p.home);
        assertEquals(2, p.docked);
        assertEquals(AppsPlan.DOCK, p.state("a"));
    }

    @Test
    public void withEveryAppDockedThereIsNoMoreApps() {
        AppsPlan p = plan(0, true, "a", "b");
        assertEquals(2, p.docked);
        p = p.cycle("a");
        assertEquals(Arrays.asList("b"), p.home);
        assertEquals(AppsPlan.HIDDEN, p.state("a"));
        p = p.cycle("x");
        assertEquals(Arrays.asList("b", "x"), p.home);
    }

    @Test
    public void theLastDockedAppStays() {
        AppsPlan p = plan(1, false, "a", "b");
        assertSame(p, p.cycle("a"));
    }

    @Test
    public void aFullDockTakesNoMore() {
        String[] ten = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        AppsPlan p = plan(10, false, ten).cycle("x");
        assertEquals(10, p.docked);
        assertEquals(AppsPlan.MORE, p.state("x"));
    }

    @Test
    public void movingStaysInsideTheGroup() {
        AppsPlan p = plan(2, false, "a", "b", "c", "d");
        assertEquals(Arrays.asList("b", "a", "c", "d"), p.move("a", 1).home);
        assertSame(p, p.move("b", 1));    // the dock's end
        assertSame(p, p.move("c", -1));   // More apps' start
        assertEquals(Arrays.asList("a", "b", "d", "c"), p.move("c", 1).home);
        assertSame(p, p.move("x", 1));    // hidden
    }
}
