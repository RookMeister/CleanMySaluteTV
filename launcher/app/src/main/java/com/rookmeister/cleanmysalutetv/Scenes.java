package com.rookmeister.cleanmysalutetv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scenes behind Home: a picture, a big frosted-glass clock and, for most scenes, the scenery cut out of the
 * picture and drawn over the clock, so hills and peaks overlap the digits. The look follows Seagey/couch-tv
 * (MIT): its scenes, sizes and colours are in tv-home/web/app.css (.gclock, .s-night, .s-golden ...), designed
 * at 1920x1080, so every position here is a share of the screen's height (or width for a left margin).
 *
 * Pure data and arithmetic only, so the JVM tests can check it; {@link SceneRenderer} draws, {@link ScenePlayer}
 * rotates.
 */
final class Scenes {
    private Scenes() {}

    /** couch-tv turns the scenes every 20 s and fades for 1.6 s. */
    static final int DEFAULT_SECONDS = 20;
    static final int MIN_SECONDS = 10, MAX_SECONDS = 3600;
    static final long FADE_MS = 1600;
    /** The clock's line box, as a share of its font size (couch-tv: line-height 0.82). */
    static final float LINE_HEIGHT = 0.82f;
    /** letter-spacing -0.02em. */
    static final float LETTER_SPACING = -0.02f;

    static final class Scene {
        final String id;
        /** assets/scenes/scene-<id>.jpg, -glass.jpg and, when {@link #cutout}, -fg.webp. */
        final boolean cutout;
        /** 0 centres the clock; otherwise its left edge as a share of the screen width. */
        final float left;
        /** Picture and cut-out brightness (couch-tv .s-night .sbg: 0.86). */
        final float photoBrightness;
        /** The glass fill: brightness, saturation, contrast and opacity. */
        final float fillBrightness, fillSaturation, fillContrast, fillAlpha;
        /** The rim: the digits' faint fill and their thin outline. */
        final int rimFill, rimStroke;
        /** The diagonal highlight across the digits. */
        final boolean sheen;
        /** Drop shadow under the clock: colour, downward offset and blur as shares of the screen height. */
        final int shadowColor;
        final float shadowDy, shadowBlur;
        /** A top-to-bottom tint over the picture: colours and stops; empty for none. */
        final int[] shadeColors;
        final float[] shadeStops;
        /** The waterline as a share of the height: below it the clock is a softer reflection. 0 for none. */
        final float water;
        /** The date and weather line over the clock: its colours, and whether it has a soft shadow (light text). */
        final int dateColor, weatherColor;
        final boolean captionShadow;
        /** A real photo (couch-tv's backgrounds, Wikimedia Commons): the place (a string resource) and the
         *  photographer, shown in a corner as their licences ask; 0 and null for the made-up scenes. */
        final int placeRes;
        final String author;

        Scene(Builder b) {
            id = b.id; cutout = b.cutout; left = b.left;
            photoBrightness = b.photoBrightness;
            fillBrightness = b.fillBrightness; fillSaturation = b.fillSaturation; fillContrast = b.fillContrast;
            fillAlpha = b.fillAlpha; rimFill = b.rimFill; rimStroke = b.rimStroke; sheen = b.sheen;
            shadowColor = b.shadowColor; shadowDy = b.shadowDy; shadowBlur = b.shadowBlur;
            shadeColors = b.shadeColors; shadeStops = b.shadeStops; water = b.water;
            dateColor = b.dateColor; weatherColor = b.weatherColor; captionShadow = b.captionShadow;
            placeRes = b.placeRes; author = b.author;
        }
    }

    static final class Builder {
        final String id;
        boolean cutout = true;
        float left = 0f;
        float photoBrightness = 1f;
        float fillBrightness = 1f, fillSaturation = 1f, fillContrast = 1f, fillAlpha = 1f;
        int rimFill = 0x1AFFFFFF, rimStroke = 0x8CFFFFFF;
        boolean sheen = true;
        int shadowColor = 0x29000000;
        float shadowDy = px(6), shadowBlur = px(28);
        int[] shadeColors = new int[0];
        float[] shadeStops = new float[0];
        float water = 0f;
        int dateColor = 0xFFFFFFFF, weatherColor = 0xC7FFFFFF;
        boolean captionShadow = true;
        int placeRes;
        String author;

        Builder(String id) { this.id = id; }
        /** couch-tv's Classic photos: no cut-out, the glass clock over the photo, credited in a corner. */
        Builder photo(int place, String by) {
            cutout = false; placeRes = place; author = by;
            // a soft shade at the top keeps the date and the buttons readable on bright skies
            shadeColors = new int[] {0x52000000, 0x00000000};
            shadeStops = new float[] {0f, 0.45f};
            return this;
        }
        Builder caption(int date, int weather, boolean shadow) {
            dateColor = date; weatherColor = weather; captionShadow = shadow; return this;
        }
        Builder fill(float brightness, float saturation, float contrast, float alpha) {
            fillBrightness = brightness; fillSaturation = saturation; fillContrast = contrast; fillAlpha = alpha;
            return this;
        }
        Builder rim(int fill, int stroke) { rimFill = fill; rimStroke = stroke; return this; }
        Builder shadow(int color, float dyPx, float blurPx) {
            shadowColor = color; shadowDy = px(dyPx); shadowBlur = px(blurPx); return this;
        }
        Builder shade(int[] colors, float[] stops) { shadeColors = colors; shadeStops = stops; return this; }
        Scene build() { return new Scene(this); }
    }

    /** couch-tv's sizes are pixels on a 1080-line screen. */
    static float px(float pixelsAt1080) { return pixelsAt1080 / 1080f; }

    /** The built-in scenes, in couch-tv's order and with its per-scene rules. */
    static final List<Scene> ALL;
    static {
        List<Scene> all = new ArrayList<>();
        // Night: the clock behind the peak, pink glass.
        Builder night = new Builder("night").fill(1.15f, 1.2f, 1f, 0.9f).rim(0x0FFFC8D7, 0x80FFD7E1)
                .caption(0xEBFFE8EE, 0xC7FFE8EE, true);
        night.photoBrightness = 0.86f;
        all.add(night.build());
        // Golden hour: a little smaller, low enough for the volcano's tip to rise into the digits.
        all.add(new Builder("golden").build());
        // Waterline: the clock sits on the water; under the surface it shows as a softer reflection.
        Builder water = new Builder("water").shadow(0x59001428, 8, 30)
                .shade(new int[] {0x6B000C18, 0x1F000C18, 0x00000000, 0x80001428}, new float[] {0f, 0.40f, 0.52f, 1f});
        water.cutout = false;
        water.water = 0.55f;
        all.add(water.build());
        // Fog: a big dark clock on the left, sinking into the misty hills.
        Builder fog = new Builder("fog").fill(0.46f, 0.8f, 1.1f, 0.8f).rim(0x00000000, 0x99FFFFFF)
                .shadow(0, 0, 0).caption(0xFF16201D, 0xB816201D, false);
        fog.left = 96f / 1920f;
        fog.sheen = false;
        all.add(fog.build());
        // Rainforest dawn: warm light at the top.
        all.add(new Builder("dawn").shadow(0x4D3C1E0A, 8, 34)
                .shade(new int[] {0x6B321C0C, 0x00321C0C}, new float[] {0f, 0.55f}).build());
        // couch-tv's free photos (tv-home/web/backgrounds/CREDITS.md), in its order: the clock in glass over them.
        all.add(new Builder("pavement").photo(R.string.scene_pavement, "JJ Harrison").build());
        all.add(new Builder("valley").photo(R.string.scene_valley, "Diliff").build());
        all.add(new Builder("storm").photo(R.string.scene_storm, "Thennicke").build());
        all.add(new Builder("beach").photo(R.string.scene_beach, "Christian Ferrer").build());
        all.add(new Builder("paddy").photo(R.string.scene_paddy, "Basile Morin").build());
        ALL = Collections.unmodifiableList(all);
    }

    /** Where the clock stands: as the scene has it (couch-tv's own), or the owner's choice in the settings menu. */
    static final String POS_AUTO = "auto", POS_LEFT = "left", POS_CENTER = "center", POS_RIGHT = "right";
    static final String[] POSITIONS = {POS_AUTO, POS_LEFT, POS_CENTER, POS_RIGHT};
    /** couch-tv's left margin for a left clock: 96 px on a 1920-wide screen; mirrored for a right one. */
    static final float SIDE_MARGIN = 96f / 1920f;

    /** "left", "center" or "right" for this scene: {@code position} unless it is auto or unknown. */
    static String placement(Scene s, String position) {
        if (POS_LEFT.equals(position) || POS_CENTER.equals(position) || POS_RIGHT.equals(position)) return position;
        // CleanMySaluteTV: auto is the middle in every scene, so the clock never moves when the scene changes.
        return POS_CENTER;
    }

    static String nextPosition(String position) {
        for (int i = 0; i < POSITIONS.length; i++) if (POSITIONS[i].equals(position)) return POSITIONS[(i + 1) % POSITIONS.length];
        return POS_LEFT;
    }

    /**
     * CleanMySaluteTV: one place for the clock in every scene, the owner's wish: the scenes' own heights (150 to
     * 268 px) put the date line under the top buttons on some. The date line's top lands 166 px down, clear of the
     * 88 px buttons that end at 148.
     */
    static final float CLOCK_TOP = px(220), CLOCK_SIZE = px(430);

    /** couch-tv's date line sits this far above the clock's line box (96 px over 150 at 1080 lines). */
    static final float CAPTION_ABOVE = px(54);

    /** "Вторник 17 ноября", "Tuesday 17 November": weekday, day and month, no comma, a capital first letter. */
    static String dateLine(java.util.Date date, java.util.Locale locale) {
        String text = new java.text.SimpleDateFormat("EEEE d MMMM", locale).format(date).replace(",", "");
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    /**
     * Scenes switched off: the big clock alone, over the wallpaper or the plain background. No picture to fill the
     * glass with, so the digits are near-white with couch-tv's rim, sheen and shadow; drawn on a transparent layer.
     */
    static final Scene PLAIN;
    static {
        Builder plain = new Builder("plain").fill(1f, 1f, 1f, 0.82f).shadow(0x40000000, 6, 28);
        plain.cutout = false;
        PLAIN = plain.build();
    }

    static boolean isPlain(Scene s) {
        return s == PLAIN;
    }

    /** The scenes named in scene_list, in that order; unknown and repeated names are skipped. None named: all. */
    static List<Scene> select(List<String> ids) {
        if (ids == null || ids.isEmpty()) return ALL;
        List<Scene> out = new ArrayList<>();
        for (String id : ids) {
            Scene s = byId(id == null ? "" : id.trim());
            if (s != null && !out.contains(s)) out.add(s);
        }
        return out.isEmpty() ? ALL : out;
    }

    static Scene byId(String id) {
        for (Scene s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    static int next(int index, int count) {
        return count <= 0 ? 0 : (Math.max(0, index) + 1) % count;
    }

    /** scene_seconds: out of range or missing is the default. */
    static int seconds(int value) {
        return value < MIN_SECONDS || value > MAX_SECONDS ? DEFAULT_SECONDS : value;
    }

    /** couch-tv dims every scene a little from 21:00 to 06:00. */
    static boolean dimmed(int hour) {
        return hour >= 21 || hour < 6;
    }

    /**
     * The text baseline for a line box whose top is at {@code top}, as CSS lays out one line: the line height's
     * spare room (negative when the line is tighter than the font) is split above and below the font's
     * ascent+descent. {@code ascent} is negative, as Android's FontMetrics gives it.
     */
    static float baseline(float top, float fontSize, float ascent, float descent) {
        float lineHeight = LINE_HEIGHT * fontSize;
        return top + (lineHeight - (descent - ascent)) / 2f - ascent;
    }

    /**
     * The clock's top, moved down under the header (greeting, date, weather) when its ink would run into it; all in
     * pixels. A header that ends left of the digits, as it does beside a centred clock, leaves the clock where it is.
     */
    static float clearOfHeader(float inkLeft, float inkTop, float inkRight, float top,
                               float headerLeft, float headerRight, float headerBottom, float gap) {
        if (headerBottom <= 0f || headerRight <= headerLeft) return top;
        boolean across = inkLeft < headerRight + gap && inkRight > headerLeft - gap;
        if (!across || inkTop >= headerBottom + gap) return top;
        return top + (headerBottom + gap - inkTop);
    }

    /**
     * Keeps the clock's digits above the row that starts at {@code limit} (a share of the height): returns the
     * font size to use, never larger than the scene's own and never under a quarter of it.
     */
    static float fitSize(float top, float size, float inkBelowTop, float limit) {
        if (limit <= 0f || top + inkBelowTop * size <= limit) return size;
        float fitted = (limit - top) / inkBelowTop;
        // CleanMySaluteTV: down to a quarter (was 60%): the large dock with a row of apps under it stands high, and
        // the clock now stays clear of it rather than running into it.
        return Math.max(size * 0.25f, Math.min(size, fitted));
    }
}
