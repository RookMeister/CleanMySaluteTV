package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ScenesTest {

    @Test
    public void couchTvScenesThenItsPhotos() {
        assertEquals(Arrays.asList("night", "golden", "water", "fog", "dawn", "pavement", "valley", "storm", "beach", "paddy"),
                ids(Scenes.ALL));
    }

    @Test
    public void onlyTheMadeUpScenesHaveCutoutsAndOnlyPhotosACredit() {
        for (Scenes.Scene s : Scenes.ALL) {
            boolean photo = s.placeRes != 0;
            assertEquals(s.id, photo, s.author != null);
            assertEquals(s.id, !photo && !s.id.equals("water"), s.cutout);
        }
        assertTrue(Scenes.byId("water").water > 0f);
    }

    @Test
    public void selectKeepsListedOrderAndSkipsUnknownAndRepeats() {
        assertEquals(Arrays.asList("fog", "night"), ids(Scenes.select(Arrays.asList("fog", "moon", " night ", "fog"))));
    }

    @Test
    public void selectWithNothingUsableIsAll() {
        assertSame(Scenes.ALL, Scenes.select(Collections.emptyList()));
        assertSame(Scenes.ALL, Scenes.select(null));
        assertSame(Scenes.ALL, Scenes.select(Arrays.asList("nope", "")));
    }

    @Test
    public void nextWrapsAround() {
        assertEquals(1, Scenes.next(0, 5));
        assertEquals(0, Scenes.next(4, 5));
        assertEquals(0, Scenes.next(0, 1));
        assertEquals(0, Scenes.next(3, 0));
    }

    @Test
    public void secondsOutOfRangeIsDefault() {
        assertEquals(20, Scenes.seconds(0));
        assertEquals(20, Scenes.seconds(9));
        assertEquals(10, Scenes.seconds(10));
        assertEquals(3600, Scenes.seconds(3600));
        assertEquals(20, Scenes.seconds(3601));
    }

    @Test
    public void dimmedFromNineToSix() {
        assertTrue(Scenes.dimmed(21));
        assertTrue(Scenes.dimmed(0));
        assertTrue(Scenes.dimmed(5));
        assertFalse(Scenes.dimmed(6));
        assertFalse(Scenes.dimmed(20));
    }

    @Test
    public void baselineMatchesCssLineBox() {
        // Antonio: ascent 2365, descent 285 per 2048 units. At 400 px the line box is 328 px tall.
        float size = 400f, ascent = -2365f / 2048f * size, descent = 285f / 2048f * size;
        float baseline = Scenes.baseline(150f, size, ascent, descent);
        // top + (328 - 517.6) / 2 + 461.9 = 150 + 367.1
        assertEquals(517.1f, baseline, 0.5f);
    }

    @Test
    public void fitSizeShrinksOnlyWhenTheDigitsWouldReachTheDock() {
        assertEquals(0.37f, Scenes.fitSize(0.14f, 0.37f, 0.92f, 0f), 1e-6);
        assertEquals(0.37f, Scenes.fitSize(0.14f, 0.37f, 0.92f, 0.6f), 1e-6);
        assertEquals((0.4f - 0.14f) / 0.92f, Scenes.fitSize(0.14f, 0.37f, 0.92f, 0.4f), 1e-6);
        assertEquals(0.37f * 0.6f, Scenes.fitSize(0.14f, 0.37f, 0.92f, 0.2f), 1e-6);
    }

    @Test
    public void sizesAreCouchTvPixelsAt1080() {
        Scenes.Scene night = Scenes.byId("night"), fog = Scenes.byId("fog");
        assertEquals(150f / 1080f, night.top, 1e-6);
        assertEquals(400f / 1080f, night.size, 1e-6);
        assertEquals(96f / 1920f, fog.left, 1e-6);
        assertEquals(0f, night.left, 0f);
    }

    @Test
    public void placementFollowsTheChoiceOrTheScene() {
        Scenes.Scene fog = Scenes.byId("fog"), night = Scenes.byId("night");
        assertEquals("left", Scenes.placement(fog, "auto"));
        assertEquals("center", Scenes.placement(night, "auto"));
        assertEquals("right", Scenes.placement(fog, "right"));
        assertEquals("center", Scenes.placement(fog, "center"));
        assertEquals("center", Scenes.placement(night, "bogus"));
    }

    @Test
    public void positionsCycle() {
        assertEquals("left", Scenes.nextPosition("auto"));
        assertEquals("right", Scenes.nextPosition("center"));
        assertEquals("auto", Scenes.nextPosition("right"));
        assertEquals("left", Scenes.nextPosition("whatever"));
    }

    private static List<String> ids(List<Scenes.Scene> scenes) {
        String[] out = new String[scenes.size()];
        for (int i = 0; i < out.length; i++) out[i] = scenes.get(i).id;
        return Arrays.asList(out);
    }
}
