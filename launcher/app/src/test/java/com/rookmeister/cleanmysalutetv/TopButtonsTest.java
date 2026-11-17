package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TopButtonsTest {
    @Test
    public void parseKeepsTheSavedOrderAndDropsUnknown() {
        assertEquals(Arrays.asList("settings", "inputs", "sleep"),
                TopButtons.parse("settings, inputs,bogus,sleep,inputs"));
        assertEquals(Collections.emptyList(), TopButtons.parse(""));
    }

    @Test
    public void moveSwapsWithANeighbourAndStopsAtTheEnds() {
        assertEquals(Arrays.asList("network", "inputs", "vpn", "settings"),
                TopButtons.move(TopButtons.DEFAULT, "inputs", 1));
        assertEquals(TopButtons.DEFAULT, TopButtons.move(TopButtons.DEFAULT, "inputs", -1));
        assertEquals(TopButtons.DEFAULT, TopButtons.move(TopButtons.DEFAULT, "settings", 1));
    }

    @Test
    public void toggleAddsBeforeTheGearAndRemoves() {
        assertEquals(Arrays.asList("inputs", "network", "vpn", "sleep", "settings"),
                TopButtons.toggle(TopButtons.DEFAULT, "sleep"));
        assertEquals(Arrays.asList("inputs", "network", "settings"),
                TopButtons.toggle(TopButtons.DEFAULT, "vpn"));
    }

    @Test
    public void everyKindHasAnIconAndAName() {
        for (String k : TopButtons.ALL) {
            TopButtons.icon(k);
            TopButtons.label(k);
        }
        assertEquals(13, TopButtons.ALL.size());
    }
}
