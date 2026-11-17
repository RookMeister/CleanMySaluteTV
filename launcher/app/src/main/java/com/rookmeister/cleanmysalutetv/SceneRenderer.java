package com.rookmeister.cleanmysalutetv;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.IOException;
import java.io.InputStream;

/**
 * Draws one scene with the time on it into a single opaque picture: the photo, its tint, the glass clock,
 * the cut-out over the clock, and the dark band at the bottom that keeps the dock readable. Done off the UI
 * thread once a minute and at each change of scene, so Home itself only shows a finished picture and the
 * source pictures are released straight after.
 *
 * The clock is couch-tv's .gclock: the digits filled with a blurred copy of the photo seen through them
 * (frosted glass), a soft drop shadow, a light diagonal sheen, and a thin bright rim.
 */
final class SceneRenderer {
    private static final String DIR = "scenes/";
    private final AssetManager assets;
    private final Typeface font;

    /** The line over the clock: the date, then the weather's icon and words; any part may be missing. */
    static final class Caption {
        final String date, weather;
        final android.graphics.drawable.Drawable icon;
        final Typeface dateFont, weatherFont;
        /** "Place · Photographer" for a real photo, drawn small in the bottom right corner; null for none. */
        String credit;

        Caption(String date, String weather, android.graphics.drawable.Drawable icon, Typeface dateFont, Typeface weatherFont) {
            this.date = date; this.weather = weather; this.icon = icon; this.dateFont = dateFont; this.weatherFont = weatherFont;
        }
    }

    SceneRenderer(AssetManager assets) {
        this.assets = assets;
        Typeface t;
        try {
            t = Typeface.createFromAsset(assets, DIR + "antonio_light.ttf");
        } catch (RuntimeException e) {
            t = Typeface.create("sans-serif-condensed-light", Typeface.NORMAL);
        }
        font = t;
    }

    /**
     * @param width   picture size; the scene covers it like CSS object-fit: cover
     * @param time    the digits, e.g. "21:07"
     * @param dimmed  the night-time dimming (couch-tv: brightness 0.72 from 21:00 to 06:00)
     * @param limit   the clock's digits stay above this share of the height (the hero and dock); 0 for no limit
     * @param header  Home's header (left, right, bottom as shares of width, width and height), kept clear of the
     *                clock's digits; null for none
     * @return an opaque RGB_565 picture, or null if a picture could not be read or memory ran out
     */
    Bitmap render(Scenes.Scene s, int width, int height, String time, boolean dimmed, float limit, float[] header,
                  Caption caption, String position) {
        String where = Scenes.placement(s, position);
        Bitmap out = null, glass = null, cutout = null;
        boolean plain = Scenes.isPlain(s);
        try {
            if (plain) {
                // The clock alone on a transparent layer, over the wallpaper or the background colour.
                out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                float[] clock = drawClock(c, s, null, time, width, height, limit, header, where, captionWidth(caption, height));
                if (caption != null) drawCaption(c, s, caption, clock[0], clock[1], clock[2], where, width, height);
                return out;
            }
            out = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(out);
            drawCover(c, s.id + ".jpg", 1, brightness(s.photoBrightness), width, height, false);
            if (s.shadeColors.length > 1) {
                Paint shade = new Paint();
                shade.setShader(new LinearGradient(0, 0, 0, height, s.shadeColors, s.shadeStops, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, width, height, shade);
            }

            // The glass is a blur: half size is plenty, and a quarter of the memory.
            glass = decode(s.id + "-glass.jpg", 2, Bitmap.Config.RGB_565);
            float[] clock = drawClock(c, s, glass, time, width, height, limit, header, where, captionWidth(caption, height));
            if (glass != null) { glass.recycle(); glass = null; }

            if (s.cutout) {
                cutout = decode(s.id + "-fg.webp", 1, Bitmap.Config.ARGB_8888);
                if (cutout != null) {
                    Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                    p.setColorFilter(brightness(s.photoBrightness));
                    c.drawBitmap(cutout, coverRect(cutout, width, height), new RectF(0, 0, width, height), p);
                    cutout.recycle();
                    cutout = null;
                }
            }

            // couch-tv .scrim: clear down to 60%, then darker to the bottom edge, under the dock.
            Paint scrim = new Paint();
            scrim.setShader(new LinearGradient(0, 0.6f * height, 0, height, 0x00000000, 0x80000000, Shader.TileMode.CLAMP));
            c.drawRect(0, 0.6f * height, width, height, scrim);
            if (dimmed) c.drawColor(0x47000000);   // brightness 0.72
            if (caption != null) drawCaption(c, s, caption, clock[0], clock[1], clock[2], where, width, height);
            if (caption != null && caption.credit != null) drawCredit(c, caption, width, height);
            return out;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            if (out != null) out.recycle();
            return null;
        } finally {
            if (glass != null) glass.recycle();
            if (cutout != null) cutout.recycle();
        }
    }

    /** @return the clock's line-box top and the left and right edges of its ink, for the date line over it */
    private float[] drawClock(Canvas c, Scenes.Scene s, Bitmap glass, String time, int width, int height, float limit,
                              float[] header, String where, float captionW) {
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        text.setTypeface(font);
        text.setLetterSpacing(Scenes.LETTER_SPACING);
        float x;
        if (Scenes.POS_LEFT.equals(where)) {
            text.setTextAlign(Paint.Align.LEFT);
            x = (s.left > 0f ? s.left : Scenes.SIDE_MARGIN) * width;
        } else if (Scenes.POS_RIGHT.equals(where)) {
            text.setTextAlign(Paint.Align.RIGHT);
            x = (1f - Scenes.SIDE_MARGIN) * width;
        } else {
            text.setTextAlign(Paint.Align.CENTER);
            x = width / 2f;
        }
        float top = Scenes.CLOCK_TOP * height;
        // The digits' ink ends about 0.92 of the font size under the top of the line box (Antonio, line-height 0.82).
        float size = Scenes.fitSize(Scenes.CLOCK_TOP, Scenes.CLOCK_SIZE, 0.92f, limit) * height;
        RectF box = new RectF();
        float baseline = place(text, time, x, top, size, box);
        if (header != null) {
            float moved = Scenes.clearOfHeader(box.left, box.top, box.right, top,
                    header[0] * width, header[1] * width, header[2] * height, 14f * height / 1080f);
            if (moved != top) {
                top = moved;
                size = Scenes.fitSize(top / height, Scenes.CLOCK_SIZE, 0.92f, limit) * height;
                baseline = place(text, time, x, top, size, box);
            }
            // CleanMySaluteTV: the date line over the digits too, placed as drawCaption places it; a long weather
            // line ran under the top buttons, which are part of the header.
            if (captionW > 0f) {
                float unit = height / 1080f;
                float lineTop = top - Scenes.CAPTION_ABOVE * height;
                float cl = Scenes.POS_LEFT.equals(where) ? box.left + 8f * unit
                        : Scenes.POS_RIGHT.equals(where) ? box.right - 8f * unit - captionW : (width - captionW) / 2f;
                float lowered = Scenes.clearOfHeader(cl, lineTop, cl + captionW, top,
                        header[0] * width, header[1] * width, header[2] * height, 8f * unit);
                if (lowered != top) {
                    top = lowered;
                    size = Scenes.fitSize(top / height, Scenes.CLOCK_SIZE, 0.92f, limit) * height;
                    baseline = place(text, time, x, top, size, box);
                }
            }
        }

        if (s.water > 0f) {
            float line = s.water * height;
            c.save();
            c.clipRect(0, 0, width, line);
            drawGlassText(c, s, glass, time, x, baseline, box, text, width, height, false);
            c.restore();
            c.save();
            c.clipRect(0, line, width, height);
            drawGlassText(c, s, glass, time, x, baseline, box, text, width, height, true);
            c.restore();
        } else {
            drawGlassText(c, s, glass, time, x, baseline, box, text, width, height, false);
        }
        return new float[] {top, box.left, box.right};
    }

    /**
     * couch-tv's .date over the clock: 34 px type on a 1080 screen, centred over a centred clock, from a left clock's
     * left edge, ending at a right clock's right edge; the weather follows after a gap, its icon tinted like its words.
     */
    /** The date line's width as drawCaption lays it out: date, gap, weather icon, gap, weather; 0 when empty. */
    private static float captionWidth(Caption cap, int height) {
        if (cap == null) return 0f;
        float unit = height / 1080f;
        boolean hasDate = cap.date != null && !cap.date.isEmpty();
        boolean hasWeather = cap.weather != null && !cap.weather.isEmpty();
        if (!hasDate && !hasWeather) return 0f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(34f * unit);
        p.setTypeface(cap.dateFont);
        float dateW = hasDate ? p.measureText(cap.date) : 0f;
        p.setTypeface(cap.weatherFont);
        float wxW = hasWeather ? p.measureText(cap.weather) : 0f;
        float iconSize = cap.icon != null && hasWeather ? 30f * unit : 0f;
        float gapBefore = hasDate && hasWeather ? 16f * unit : 0f, gapIcon = iconSize > 0 ? 7f * unit : 0f;
        return dateW + gapBefore + iconSize + gapIcon + wxW;
    }

    private static void drawCaption(Canvas c, Scenes.Scene s, Caption cap, float clockTop, float clockLeft,
                                    float clockRight, String where, int width, int height) {
        float unit = height / 1080f;
        boolean hasDate = cap.date != null && !cap.date.isEmpty();
        boolean hasWeather = cap.weather != null && !cap.weather.isEmpty();
        if (!hasDate && !hasWeather) return;
        Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);
        date.setTypeface(cap.dateFont);
        date.setTextSize(34f * unit);
        date.setColor(s.dateColor);
        Paint wx = new Paint(date);
        wx.setTypeface(cap.weatherFont);
        wx.setColor(s.weatherColor);
        if (s.captionShadow) {
            date.setShadowLayer(15f * unit, 0, 2f * unit, 0x40000000);
            wx.setShadowLayer(15f * unit, 0, 2f * unit, 0x40000000);
        }
        float iconSize = cap.icon != null && hasWeather ? 30f * unit : 0f;
        float gapBefore = hasDate && hasWeather ? 16f * unit : 0f, gapIcon = iconSize > 0 ? 7f * unit : 0f;
        float dateW = hasDate ? date.measureText(cap.date) : 0f;
        float wxW = hasWeather ? wx.measureText(cap.weather) : 0f;
        float total = captionWidth(cap, height);
        float x = Scenes.POS_LEFT.equals(where) ? clockLeft + 8f * unit
                : Scenes.POS_RIGHT.equals(where) ? clockRight - 8f * unit - total : (width - total) / 2f;
        Paint.FontMetrics fm = date.getFontMetrics();
        float lineTop = Math.max(8f * unit, clockTop - Scenes.CAPTION_ABOVE * height);
        // a 40 px line, the text centred in it
        float baseline = lineTop + 20f * unit - (fm.ascent + fm.descent) / 2f;
        if (hasDate) c.drawText(cap.date, x, baseline, date);
        x += dateW + gapBefore;
        if (iconSize > 0) {
            float iconTop = baseline - iconSize + 5f * unit;
            cap.icon.setBounds(Math.round(x), Math.round(iconTop), Math.round(x + iconSize), Math.round(iconTop + iconSize));
            cap.icon.setTint(s.weatherColor);
            cap.icon.draw(c);
            x += iconSize + gapIcon;
        }
        if (hasWeather) c.drawText(cap.weather, x, baseline, wx);
    }

    /** couch-tv's photo credit: small, quiet, in the bottom right corner, clear of the centred dock. */
    private static void drawCredit(Canvas c, Caption cap, int width, int height) {
        float unit = height / 1080f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(cap.weatherFont);
        p.setTextSize(17f * unit);
        p.setColor(0xA6FFFFFF);
        p.setShadowLayer(8f * unit, 0, 1f * unit, 0x80000000);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(cap.credit, width - 36f * unit, height - 24f * unit, p);
    }

    /** Sets the text size and returns the baseline for a line box at {@code top}; {@code box} gets the ink's bounds. */
    private static float place(Paint text, String time, float x, float top, float size, RectF box) {
        text.setTextSize(size);
        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = Scenes.baseline(top, size, fm.ascent, fm.descent);
        Rect ink = new Rect();
        text.getTextBounds(time, 0, time.length(), ink);
        // getTextBounds measures from a left-aligned origin; the alignment shifts the drawn text by its advance.
        float advance = text.measureText(time);
        Paint.Align align = text.getTextAlign();
        float origin = align == Paint.Align.RIGHT ? x - advance : align == Paint.Align.CENTER ? x - advance / 2f : x;
        float inkLeft = origin + ink.left;
        box.set(inkLeft, baseline + ink.top, inkLeft + ink.width(), baseline + ink.bottom);
        return baseline;
    }

    /** One copy of the digits: shadow, frosted fill, sheen, rim. {@code under} is the waterline's reflection. */
    private void drawGlassText(Canvas c, Scenes.Scene s, Bitmap glass, String time, float x, float baseline, RectF box,
                               Paint base, int width, int height, boolean under) {
        float unit = height / 1080f;
        int layer = under ? c.saveLayerAlpha(0, 0, width, height, Math.round(0.78f * 255)) : -1;
        BlurMaskFilter soften = under ? new BlurMaskFilter(2.2f * unit, BlurMaskFilter.Blur.NORMAL) : null;

        if (Color.alpha(s.shadowColor) > 0 && s.shadowBlur > 0f) {
            Paint shadow = new Paint(base);
            shadow.setColor(s.shadowColor);
            shadow.setMaskFilter(new BlurMaskFilter(s.shadowBlur * height / 2f, BlurMaskFilter.Blur.NORMAL));
            c.drawText(time, x, baseline + s.shadowDy * height, shadow);
        }

        Paint fill = new Paint(base);
        if (glass != null) {
            BitmapShader shader = new BitmapShader(glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setScale(width / (float) glass.getWidth(), height / (float) glass.getHeight());
            shader.setLocalMatrix(m);
            fill.setShader(shader);
        } else {
            fill.setColor(0xFFFFFFFF);
        }
        ColorMatrix cm = fillMatrix(s.fillBrightness, under ? s.fillSaturation * 1.4f : s.fillSaturation, s.fillContrast);
        fill.setColorFilter(new ColorMatrixColorFilter(cm));
        fill.setAlpha(Math.round(s.fillAlpha * 255));
        if (soften != null) fill.setMaskFilter(soften);
        c.drawText(time, x, baseline, fill);

        if (s.sheen) {
            // linear-gradient(160deg, white .55 0%, transparent 38%, transparent 70%, white .22 100%)
            double a = Math.toRadians(160);
            float dx = (float) Math.sin(a), dy = (float) -Math.cos(a);
            float half = (Math.abs(box.width() * dx) + Math.abs(box.height() * dy)) / 2f;
            float cx = box.centerX(), cy = box.centerY();
            Paint sheen = new Paint(base);
            sheen.setShader(new LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                    new int[] {0x8CFFFFFF, 0x00FFFFFF, 0x00FFFFFF, 0x38FFFFFF}, new float[] {0f, 0.38f, 0.70f, 1f},
                    Shader.TileMode.CLAMP));
            if (soften != null) sheen.setMaskFilter(soften);
            c.drawText(time, x, baseline, sheen);
        }

        Paint rimFill = new Paint(base);
        rimFill.setColor(s.rimFill);
        if (soften != null) rimFill.setMaskFilter(soften);
        if (Color.alpha(s.rimFill) > 0) c.drawText(time, x, baseline, rimFill);
        Paint rim = new Paint(base);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 1.44f * unit));
        rim.setColor(under ? (s.rimStroke & 0xFF000000) | 0x00AAF0FF : s.rimStroke);
        if (soften != null) rim.setMaskFilter(soften);
        c.drawText(time, x, baseline, rim);

        if (layer >= 0) c.restoreToCount(layer);
    }

    private void drawCover(Canvas c, String name, int sample, ColorMatrixColorFilter filter, int width, int height,
                           boolean alpha) throws IOException {
        Bitmap b = decode(name, sample, alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        if (b == null) throw new IOException("unreadable " + name);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        if (filter != null) p.setColorFilter(filter);
        c.drawBitmap(b, coverRect(b, width, height), new RectF(0, 0, width, height), p);
        b.recycle();
    }

    /** The part of the picture that covers the target's shape, centred (object-fit: cover). */
    private static Rect coverRect(Bitmap b, int width, int height) {
        float scale = Math.max(width / (float) b.getWidth(), height / (float) b.getHeight());
        int w = Math.round(width / scale), h = Math.round(height / scale);
        int l = (b.getWidth() - w) / 2, t = (b.getHeight() - h) / 2;
        return new Rect(l, t, l + w, t + h);
    }

    private Bitmap decode(String name, int sample, Bitmap.Config config) throws IOException {
        try (InputStream in = assets.open(DIR + "scene-" + name)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = config;
            o.inScaled = false;
            return BitmapFactory.decodeStream(in, null, o);
        }
    }

    private static ColorMatrixColorFilter brightness(float b) {
        if (b == 1f) return null;
        ColorMatrix m = new ColorMatrix();
        m.setScale(b, b, b, 1f);
        return new ColorMatrixColorFilter(m);
    }

    /** CSS brightness(), saturate() and contrast(), in that order. */
    private static ColorMatrix fillMatrix(float brightness, float saturation, float contrast) {
        ColorMatrix m = new ColorMatrix();
        m.setScale(brightness, brightness, brightness, 1f);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(saturation);
        m.postConcat(sat);
        if (contrast != 1f) {
            float t = 128f * (1f - contrast);
            m.postConcat(new ColorMatrix(new float[] {
                    contrast, 0, 0, 0, t,
                    0, contrast, 0, 0, t,
                    0, 0, contrast, 0, t,
                    0, 0, 0, 1, 0}));
        }
        return m;
    }

    /** android.graphics.Color, kept local so the arithmetic above reads plainly. */
    private static final class Color {
        static int alpha(int color) { return color >>> 24; }
    }
}
