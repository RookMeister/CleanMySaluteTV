package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class AppsPlanTest {
    private static AppsPlan plan(int size, String... home) {
        return new AppsPlan(Arrays.asList(home), size, Collections.singletonList("x"));
    }

    @Test
    public void okGoesDockMoreHiddenAndBackToTheDockEnd() {
        AppsPlan p = plan(2, "a", "b", "c");
        p = p.cycle("a");                       // c steps into the dock, a leads More apps
        assertEquals(Arrays.asList("b", "c", "a"), p.home);
        assertEquals(AppsPlan.MORE, p.state("a"));
        p = p.cycle("a");
        assertEquals(Arrays.asList("b", "c"), p.home);
        assertEquals(Arrays.asList("a", "x"), p.hidden);
        p = p.cycle("a");                       // back as the dock's last; c steps out to More apps
        assertEquals(Arrays.asList("b", "a", "c"), p.home);
        assertEquals(AppsPlan.DOCK, p.state("a"));
        assertEquals(AppsPlan.MORE, p.state("c"));
    }

    @Test
    public void theDockKeepsItsSize() {
        AppsPlan p = plan(3, "a", "b", "c", "d", "e");
        assertEquals(3, p.cycle("a").docked);
        assertEquals(3, p.cycle("x").docked);
        assertEquals(3, p.cycle("x").size);
    }

    @Test
    public void withNoMoreAppsADockedAppGoesHidden() {
        AppsPlan p = plan(4, "a", "b").cycle("a");
        assertEquals(Arrays.asList("b"), p.home);
        assertEquals(AppsPlan.HIDDEN, p.state("a"));
        p = p.cycle("x");                       // room in the dock: in at its end
        assertEquals(Arrays.asList("b", "x"), p.home);
    }

    @Test
    public void theLastAppStays() {
        AppsPlan p = plan(4, "a");
        assertSame(p, p.cycle("a"));
    }

    @Test
    public void movingStaysInsideTheGroup() {
        AppsPlan p = plan(2, "a", "b", "c", "d");
        assertEquals(Arrays.asList("b", "a", "c", "d"), p.move("a", 1).home);
        assertSame(p, p.move("b", 1));    // the dock's end
        assertSame(p, p.move("c", -1));   // More apps' start
        assertEquals(Arrays.asList("a", "b", "d", "c"), p.move("c", 1).home);
        assertSame(p, p.move("x", 1));    // hidden
    }
}
