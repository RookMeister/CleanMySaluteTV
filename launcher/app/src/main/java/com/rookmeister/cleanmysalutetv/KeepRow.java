package com.rookmeister.cleanmysalutetv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Keep watching row over the dock, couch-tv's Night look (.ncard in tv-home/web/app.css, 1920x1080 sizes halved
 * to dp): glass cards with the show's picture, the app's icon and name, the title, the episode and how far you got.
 * Focus: a white ring and a small lift. Up leaves for the top buttons, Down for the dock (the listener decides).
 */
final class KeepRow {
    interface Listener {
        void onOpen(KeepWatching.Item item);
        void onUp();
        void onDown();
    }

    private static final ExecutorService ART = Executors.newSingleThreadExecutor();
    private static final LruCache<String, Bitmap> PICTURES = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };

    private final Activity activity;
    private final float density;
    private final Listener listener;
    private final LinearLayout box;
    private final LinearLayout cards;
    private final HorizontalScrollView scroller;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int lastFocused;
    private int generation;

    KeepRow(Activity activity, FrameLayout root, float density, Listener listener) {
        this.activity = activity;
        this.density = density;
        this.listener = listener;
        box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setVisibility(View.GONE);
        TextView label = new TextView(activity);
        label.setText(R.string.keep_title);
        label.setTextColor(0xE6FFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setShadowLayer(8 * density, 0, density, 0x66000000);
        label.setPadding(dp(40), 0, 0, dp(8));
        box.addView(label);
        scroller = new HorizontalScrollView(activity);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setFillViewport(false);
        scroller.setSmoothScrollingEnabled(true);
        cards = new LinearLayout(activity);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false);
        cards.setClipToPadding(false);
        // room for the focused card's lift and ring
        cards.setPadding(dp(40), dp(6), dp(40), dp(8));
        scroller.addView(cards, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(box, lp);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    boolean hasCards() {
        return box.getVisibility() == View.VISIBLE && cards.getChildCount() > 0;
    }

    boolean hasFocus() {
        return box.hasFocus();
    }

    void focus() {
        if (!hasCards()) return;
        cards.getChildAt(Math.min(lastFocused, cards.getChildCount() - 1)).requestFocus();
    }

    /** The row's bottom sits clear of the dock's glass band, whose tiles start at {@code dockTop} (root coordinates). */
    void placeAbove(float dockTop, View root) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
        int margin = Math.max(0, Math.round(root.getHeight() - dockTop + dp(26)));
        if (lp.bottomMargin == margin) return;
        lp.bottomMargin = margin;
        box.post(() -> box.setLayoutParams(lp));   // never inside a layout pass
    }

    /** The row's top in root coordinates, or -1 when it is not shown: the scene clock stays above it. */
    float top() {
        if (!hasCards() || box.getHeight() == 0) return -1f;
        return box.getTop();
    }

    void show(List<KeepWatching.Item> items) {
        boolean hadFocus = box.hasFocus();
        generation++;
        cards.removeAllViews();
        if (items.isEmpty()) {
            box.setVisibility(View.GONE);
            if (hadFocus) listener.onDown();
            return;
        }
        for (int i = 0; i < items.size(); i++) cards.addView(card(items.get(i), i, items.size()));
        box.setVisibility(View.VISIBLE);
        if (hadFocus) {
            focus();
        } else {
            // A fresh list starts at the newest card: the old place no longer means the same thing.
            lastFocused = 0;
            scroller.scrollTo(0, 0);
        }
    }

    private View card(KeepWatching.Item item, int index, int count) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(8), dp(12), dp(8));
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(glass(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) lp.setMarginStart(dp(10));
        card.setLayoutParams(lp);

        ImageView thumb = new ImageView(activity);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setCornerRadius(dp(8));
        thumbBg.setColor(0x14FFFFFF);
        thumb.setBackground(thumbBg);
        thumb.setClipToOutline(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(84), dp(48));
        tp.setMarginEnd(dp(10));
        card.addView(thumb, tp);
        loadArt(item, thumb);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout appLine = new LinearLayout(activity);
        appLine.setOrientation(LinearLayout.HORIZONTAL);
        appLine.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appIcon = new ImageView(activity);
        try {
            appIcon.setImageDrawable(activity.getPackageManager().getApplicationIcon(item.pkg));
        } catch (Exception ignored) {
            // no icon: the name alone
        }
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(13), dp(13));
        ip.setMarginEnd(dp(5));
        appLine.addView(appIcon, ip);
        TextView app = line(item.app.toUpperCase(java.util.Locale.getDefault()), 9.5f, 0x94FFFFFF, true);
        app.setLetterSpacing(0.06f);
        appLine.addView(app);
        text.addView(appLine);
        TextView title = line(item.title, 14.5f, 0xFFFFFFFF, true);
        title.setPadding(0, dp(3), 0, 0);
        text.addView(title);
        if (item.subtitle != null) text.addView(line(item.subtitle, 10.5f, 0xC7FFFFFF, false));
        if (item.progress >= 0f) {
            FrameLayout bar = new FrameLayout(activity);
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(dp(2));
            track.setColor(0x40FFFFFF);
            bar.setBackground(track);
            View fill = new View(activity);
            GradientDrawable done = new GradientDrawable();
            done.setCornerRadius(dp(2));
            done.setColor(0xFFFFFFFF);
            fill.setBackground(done);
            int width = dp(260 - 8 - 12 - 84 - 10);
            bar.addView(fill, new FrameLayout.LayoutParams(Math.max(dp(3), Math.round(width * item.progress)), dp(2.5f)));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2.5f));
            bp.topMargin = dp(5);
            text.addView(bar, bp);
        }
        card.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.setOnClickListener(v -> listener.onOpen(item));
        card.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(glass(focused));
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f)
                    .translationZ(focused ? dp(8) : 0).setDuration(focused ? 200 : 150)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();
            if (focused) {
                lastFocused = index;
                int center = v.getLeft() + v.getWidth() / 2 - scroller.getWidth() / 2;
                scroller.smoothScrollTo(Math.max(0, center), 0);
            }
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP: listener.onUp(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: listener.onDown(); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: return index == 0;            // the row's ends
                case KeyEvent.KEYCODE_DPAD_RIGHT: return index == count - 1;
                default: return false;
            }
        });
        return card;
    }

    private TextView line(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(activity);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    /** couch-tv's --glass-tint and --glass-edge; focused, a white ring over it. */
    private Drawable glass(boolean focused) {
        float r = dp(15);
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(r);
        base.setColor(0x5916161E);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x33FFFFFF, 0x0DFFFFFF, 0x17FFFFFF});
        sheen.setCornerRadius(r);
        sheen.setStroke(focused ? dp(1.5f) : Math.max(1, dp(0.5f)), focused ? 0xFFFFFFFF : 0x42FFFFFF);
        return new LayerDrawable(new Drawable[] {base, sheen});
    }

    private void loadArt(KeepWatching.Item item, ImageView into) {
        if (item.art == null) return;
        Bitmap cached = PICTURES.get(item.art);
        if (cached != null) { into.setImageBitmap(cached); return; }
        int ticket = generation;
        int want = dp(84) * 2;
        ART.execute(() -> {
            Bitmap b = decode(item.art, want);
            if (b == null) return;
            PICTURES.put(item.art, b);
            main.post(() -> { if (ticket == generation) into.setImageBitmap(b); });
        });
    }

    /** A content:// or http(s) picture, decoded down to about {@code width} px wide. */
    private Bitmap decode(String raw, int width) {
        try {
            byte[] bytes = read(raw);
            if (bytes == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            d.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, d);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private byte[] read(String raw) throws Exception {
        Uri u = Uri.parse(raw);
        String scheme = u.getScheme();
        InputStream in;
        if ("content".equals(scheme) || "android.resource".equals(scheme)) {
            in = activity.getContentResolver().openInputStream(u);
        } else if ("https".equals(scheme) || "http".equals(scheme)) {
            HttpURLConnection c = (HttpURLConnection) new URL(raw).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            in = c.getInputStream();
        } else {
            return null;
        }
        if (in == null) return null;
        try (InputStream s = in) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > 8 * 1024 * 1024) return null;   // not a thumbnail
            }
            return out.toByteArray();
        }
    }

    static List<KeepWatching.Item> none() {
        return new ArrayList<>();
    }
}
