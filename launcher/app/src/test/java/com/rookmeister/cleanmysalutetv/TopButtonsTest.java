package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class TopButtonsTest {
    @Test
    public void parseKeepsBarOrderAndDropsUnknown() {
        assertEquals(Arrays.asList("sleep", "inputs", "settings"),
                TopButtons.parse("settings, inputs,bogus,sleep,inputs"));
        assertEquals(Collections.emptyList(), TopButtons.parse(""));
    }

    @Test
    public void toggleAddsInBarOrderAndRemoves() {
        assertEquals(Arrays.asList("sleep", "inputs", "network", "vpn", "settings"),
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
