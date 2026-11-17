package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ControlPanelTest {
    @Test
    public void percentRoundsAndClamps() {
        assertEquals(71, ControlPanel.percent(71, 100));
        assertEquals(33, ControlPanel.percent(1, 3));
        assertEquals(0, ControlPanel.percent(5, 0));
        assertEquals(100, ControlPanel.percent(150, 100));
        assertEquals(0, ControlPanel.percent(-5, 100));
    }

    @Test
    public void ssidDropsQuotesAndHiddenNames() {
        assertEquals("Keenetic-9311", ControlPanel.ssid("\"Keenetic-9311\""));
        assertEquals("", ControlPanel.ssid("<unknown ssid>"));
        assertEquals("", ControlPanel.ssid("0x"));
        assertEquals("", ControlPanel.ssid(null));
    }
}
