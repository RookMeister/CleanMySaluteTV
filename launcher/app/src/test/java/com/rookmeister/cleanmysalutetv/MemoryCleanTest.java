package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MemoryCleanTest {
    @Test
    public void percentRoundsAndClamps() {
        assertEquals(71, MemoryClean.percent(71, 100));
        assertEquals(33, MemoryClean.percent(1, 3));
        assertEquals(0, MemoryClean.percent(5, 0));
        assertEquals(100, MemoryClean.percent(150, 100));
        assertEquals(0, MemoryClean.percent(-5, 100));
    }
}
