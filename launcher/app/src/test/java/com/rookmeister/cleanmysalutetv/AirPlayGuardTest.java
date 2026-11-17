package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class AirPlayGuardTest {
    @Test
    public void queryIsOnePtrQuestion() {
        byte[] q = AirPlayGuard.query("_airplay._tcp.local");
        byte[] expected = {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                8, '_', 'a', 'i', 'r', 'p', 'l', 'a', 'y', 4, '_', 't', 'c', 'p', 5, 'l', 'o', 'c', 'a', 'l',
                0, 0, 12, 0, 1};
        assertArrayEquals(expected, q);
    }

    @Test
    public void containsFindsTheNameOnlyWithinTheLength() {
        byte[] buf = "xxSalute TVyy".getBytes(StandardCharsets.UTF_8);
        byte[] want = "Salute TV".getBytes(StandardCharsets.UTF_8);
        assertTrue(AirPlayGuard.contains(buf, buf.length, want));
        assertFalse(AirPlayGuard.contains(buf, 8, want));
        assertFalse(AirPlayGuard.contains(buf, buf.length, "Mi Box".getBytes(StandardCharsets.UTF_8)));
    }
}
