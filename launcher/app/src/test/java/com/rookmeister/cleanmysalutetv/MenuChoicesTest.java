package com.rookmeister.cleanmysalutetv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class MenuChoicesTest {
    private static final List<String> ALL = Arrays.asList("night", "golden", "water", "fog", "dawn");

    @Test
    public void switchingOffKeepsTheRestInOrder() {
        assertEquals(Arrays.asList("night", "fog", "dawn"),
                MenuChoices.toggleScene(ALL, Arrays.asList("night", "water", "fog", "dawn"), "water"));
    }

    @Test
    public void switchingOnGoesBackToItsBuiltInPlace() {
        assertEquals(Arrays.asList("night", "golden", "fog"),
                MenuChoices.toggleScene(ALL, Arrays.asList("fog", "night"), "golden"));
    }

    @Test
    public void boxBlurAveragesAndStaysOpaque() {
        int[] px = {0xFF000000, 0xFFFFFFFF, 0xFF000000};
        MenuChoices.boxBlur(px, 3, 1, 1);
        assertEquals(0xFF7F7F7F, px[0]);
        assertEquals(0xFF555555, px[1]);
        int[] flat = {0xFF102030, 0xFF102030, 0xFF102030, 0xFF102030};
        MenuChoices.boxBlur(flat, 2, 2, 3);
        for (int c : flat) assertEquals(0xFF102030, c);
    }

    @Test
    public void theLastSceneStays() {
        assertEquals(Collections.singletonList("fog"), MenuChoices.toggleScene(ALL, Collections.singletonList("fog"), "fog"));
    }

    @Test
    public void geocodeUrlEncodesTheName() {
        String u = Weather.geocodeUrl(" Нижний Новгород ", "ru");
        assertTrue(u, u.startsWith("https://geocoding-api.open-meteo.com/v1/search?"));
        assertTrue(u, u.contains("language=ru"));
        assertTrue(u, u.endsWith("&name=%D0%9D%D0%B8%D0%B6%D0%BD%D0%B8%D0%B9+%D0%9D%D0%BE%D0%B2%D0%B3%D0%BE%D1%80%D0%BE%D0%B4"));
        assertTrue(Weather.geocodeUrl("x", "").contains("language=en"));
    }

    @Test
    public void placeLabelSkipsBlanksAndRepeats() {
        assertEquals("Казань, Татарстан, Россия", Weather.placeLabel("Казань", "Татарстан", "Россия"));
        assertEquals("Москва, Россия", Weather.placeLabel("Москва", "Москва", "Россия"));
        assertEquals("Минск, Беларусь", Weather.placeLabel("Минск", "", "Беларусь"));
    }
}
