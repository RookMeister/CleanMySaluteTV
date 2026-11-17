package com.rookmeister.cleanmysalutetv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * CleanMySaluteTV: a tile at the end of the small dock that is not an app, as couch-tv has them: the gear (the
 * launcher's settings menu) and "…" (every app, in the app library). Drawn over the grid by Home in the cells after
 * the dock's last app (MainActivity.placeMoreTile), so the grid, its order and its empty dock slots stay as they are.
 */
final class MoreTile {
    private final LinearLayout box;
    private final View card;
    private final TextView name;
    private final float density;
    /** Room above the square for its focus zoom: the box clips what its parent draws outside it. */
    private final int headroom;

    private MoreTile(LinearLayout box, View card, TextView name, float density, int headroom) {
        this.box = box;
        this.card = card;
        this.name = name;
        this.density = density;
        this.headroom = headroom;
    }

    /**
     * @param right where Right goes; null keeps the focus here (the dock's end)
     */
    static MoreTile create(Context c, float density, FrameLayout root, int icon, int label, Runnable open,
                           Runnable left, Runnable right, Runnable up) {
        int nameWidth = Math.round(170 * density);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        int headroom = Math.round(10 * density);
        box.setPadding(0, headroom, 0, 0);

        ImageView card = new ImageView(c);
        Drawable dots = c.getDrawable(icon).mutate();
        dots.setTintList(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {0xFF16171B, 0xFFFFFFFF}));
        card.setImageDrawable(dots);
        int pad = Math.round(14 * density);
        card.setPadding(pad, pad, pad, pad);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, rounded(0xFFFFFFFF, 12 * density));
        bg.addState(new int[] {}, rounded(0x40FFFFFF, 12 * density));
        card.setBackground(bg);
        card.setFocusable(true);
        card.setClickable(true);
        card.setContentDescription(c.getString(label));

        TextView name = new TextView(c);
        name.setText(label);
        name.setTextColor(0xF2FFFFFF);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        try {
            name.setTypeface(UiFont.get(c, R.string.font_medium));
        } catch (Exception ignored) {
            // the system font
        }
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setAlpha(0f);

        box.addView(card, new LinearLayout.LayoutParams(1, 1));   // sized in place()
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(nameWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Math.round(7 * density);
        box.addView(name, np);
        root.addView(box, new FrameLayout.LayoutParams(nameWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.setVisibility(View.INVISIBLE);

        card.setOnClickListener(v -> open.run());
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? AppAdapter.FOCUS_SCALE : 1f).scaleY(focused ? AppAdapter.FOCUS_SCALE : 1f)
                    .setDuration(160).start();
            name.animate().alpha(focused ? 1f : 0f).setDuration(160).start();
        });
        card.setOnKeyListener((v, key, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT: left.run(); return true;
                case KeyEvent.KEYCODE_DPAD_UP: up.run(); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (right != null) right.run();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: return true;   // nothing below the small dock
                default: return false;
            }
        });
        return new MoreTile(box, card, name, density, headroom);
    }

    /** Puts the tile's square at ({@code x}, {@code y}) in Home's root, {@code size} pixels across, like the apps'. */
    void place(int x, int y, int size) {
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp.width != size || lp.height != size || card.getWidth() != size) {
            lp.width = size;
            lp.height = size;
            // Called from the grid's layout and scroll passes, where a layout request is dropped: lay out next frame.
            card.post(() -> card.setLayoutParams(lp));
        }
        box.setX(x + size / 2f - box.getLayoutParams().width / 2f);
        box.setY(y - headroom);
        box.setVisibility(View.VISIBLE);
    }

    void hide() {
        box.setVisibility(View.INVISIBLE);
    }

    void focus() {
        if (box.getVisibility() == View.VISIBLE) card.requestFocus();
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }
}
