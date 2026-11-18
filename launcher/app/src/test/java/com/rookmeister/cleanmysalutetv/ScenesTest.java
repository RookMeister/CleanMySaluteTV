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
        assertEquals(0.37f * 0.25f, Scenes.fitSize(0.14f, 0.37f, 0.92f, 0.15f), 1e-6);
    }

    @Test
    public void aLeftClockStandsAtCouchTvsMargin() {
        Scenes.Scene night = Scenes.byId("night"), fog = Scenes.byId("fog");
        assertEquals(96f / 1920f, fog.left, 1e-6);
        assertEquals(0f, night.left, 0f);
    }

    @Test
    public void theChoiceMovesThePhotosOnlyTheCutOutScenesKeepTheirPlace() {
        Scenes.Scene fog = Scenes.byId("fog"), night = Scenes.byId("night"), valley = Scenes.byId("valley");
        // with a cut-out: couch-tv's own place, whatever the choice
        assertEquals("left", Scenes.placement(fog, "auto"));
        assertEquals("left", Scenes.placement(fog, "right"));
        assertEquals("center", Scenes.placement(night, "left"));
        // a photo: the owner's choice, auto (or anything unknown) in the middle
        assertEquals("right", Scenes.placement(valley, "right"));
        assertEquals("center", Scenes.placement(valley, "auto"));
        assertEquals("center", Scenes.placement(valley, "bogus"));
    }

    @Test
    public void theCutOutScenesKeepCouchTvsClockThePhotosShareOne() {
        assertEquals(150f / 1080f, Scenes.byId("night").top, 1e-6);
        assertEquals(400f / 1080f, Scenes.byId("night").size, 1e-6);
        assertEquals(230f / 1080f, Scenes.byId("dawn").top, 1e-6);
        assertEquals(Scenes.CLOCK_TOP, Scenes.byId("valley").top, 0f);
        assertEquals(Scenes.CLOCK_SIZE, Scenes.byId("water").size, 0f);
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

    @Test
    public void theClockShrinksUntilNoMoreThanAThirdHidesBehindTheHills() {
        // ink 240..616 under a top of 220; hills from 437 hide (616 - 437) / 376 = 48% of it
        float k = Scenes.behindScale(220, 240, 616, 437, 0.3f, 0.6f);
        float inkTop = 220 + 20 * k, inkBottom = 220 + 396 * k;
        assertEquals(0.3f, (inkBottom - 437) / (inkBottom - inkTop), 1e-3);
        // little hidden already: left as it is
        assertEquals(1f, Scenes.behindScale(220, 240, 616, 560, 0.3f, 0.6f), 0f);
        // hills far up: no smaller than 60%
        assertEquals(0.6f, Scenes.behindScale(220, 240, 616, 250, 0.3f, 0.6f), 0f);
    }
}
