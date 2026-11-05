package fr.maison.parents;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Small shared look for the parents' space: dark glass, white frame on focus, sizes at 1080 lines. */
final class Ui {
    private Ui() { }

    static float unit(Context c) {
        return c.getResources().getDisplayMetrics().heightPixels / 1080f;
    }

    static int px(Context c, float at1080) {
        return Math.round(at1080 * unit(c));
    }

    static GradientDrawable pill(Context c, int colour, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(radius * unit(c));
        return d;
    }

    static GradientDrawable framed(Context c, int colour, float radius) {
        GradientDrawable d = pill(c, colour, radius);
        d.setStroke(px(c, 4), Color.WHITE);
        return d;
    }

    static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0xFF1B2230, 0xFF0D1118, 0xFF06080C});
    }

    static TextView text(Context c, String s, float size, int style, int colour) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit(c));
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setTextColor(colour);
        t.setSingleLine(true);
        return t;
    }

    /** A focusable button that turns white, framed and a little bigger under the remote. */
    static TextView button(Context c, String label, boolean primary) {
        TextView b = text(c, label, 26, Typeface.BOLD, primary ? Color.BLACK : Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(c, 30), px(c, 14), px(c, 30), px(c, 14));
        b.setBackground(pill(c, primary ? Color.WHITE : 0x33FFFFFF, 16));
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? framed(c, primary ? Color.WHITE : 0x55FFFFFF, 16) : pill(c, primary ? Color.WHITE : 0x33FFFFFF, 16));
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(110).start();
        });
        return b;
    }
}
