package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PowerControlTest {
    @Test
    public void timerStepsThroughTheChoicesAndBackToOff() {
        assertEquals(15, PowerControl.next(0));
        assertEquals(30, PowerControl.next(15));
        assertEquals(30, PowerControl.next(12));   // a running 15-minute timer moves on to 30
        assertEquals(60, PowerControl.next(30));
        assertEquals(90, PowerControl.next(60));
        assertEquals(0, PowerControl.next(90));
    }

    @Test
    public void minutesLeftRoundUp() {
        long now = 1_000_000L;
        assertEquals(0, PowerControl.remaining(0, now));
        assertEquals(0, PowerControl.remaining(now, now));
        assertEquals(1, PowerControl.remaining(now + 1, now));
        assertEquals(15, PowerControl.remaining(now + 15 * 60_000L, now));
        assertEquals(15, PowerControl.remaining(now + 14 * 60_000L + 1, now));
    }
}
