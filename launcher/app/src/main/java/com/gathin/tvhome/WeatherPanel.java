package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * weather_panel (config.xml): the weather as a smoked-glass card on the right half of Home, between the top bar
 * and the dock. Like game_day the launcher stays offline: the named add-on (the Weather add-on,
 * io.github.sumyabazar1.weather) fetches and publishes rows on content://&lt;pkg&gt;.now/now (the city now, then
 * the next days), and Home only reads them while it is on screen. No blur: a translucent fill and a thin edge.
 */
final class WeatherPanel {
    private static final String[] COLUMNS = {"label", "sky", "temp", "low", "feels", "code", "day"};

    /** Both cards and the framed photo in the wallpaper share this height (px at 1080 lines). */
    static final float CARD_HEIGHT = 563;
    /** The weather card is shorter: the "À voir" widget (PicksPanel) fills the rest of the right column. */
    static final float WEATHER_HEIGHT = 316, GAP = 14;

    private final MainActivity activity;
    private final String pkg;
    private final Uri uri;
    private final Handler main;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private final float unit;   // 1 at 1080 lines
    private LinearLayout card;
    private boolean watching;

    private WeatherPanel(MainActivity activity, String pkg, Uri uri, Handler main) {
        this.activity = activity;
        this.pkg = pkg;
        this.uri = uri;
        this.main = main;
        this.unit = activity.getResources().getDisplayMetrics().heightPixels / 1080f;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /** Null when the key is empty or the package holds no provider at &lt;pkg&gt;.now. */
    static WeatherPanel create(MainActivity activity, String pkg, Handler main) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        String authority = pkg.trim() + ".now";
        ProviderInfo provider = activity.getPackageManager().resolveContentProvider(authority, 0);
        if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).appendPath("now").build();
        return new WeatherPanel(activity, pkg.trim(), uri, main);
    }

    void start() {
        if (!watching) {
            try {
                activity.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on went away; the card keeps what it had.
            }
        }
        read();
    }

    void stop() {
        if (watching) activity.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    private void read() {
        try {
            io.execute(() -> {
                List<Object[]> rows = new ArrayList<>();
                try (Cursor c = activity.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    while (c != null && c.moveToNext()) {
                        rows.add(new Object[] {c.getString(0), c.getString(1), c.getInt(2), c.getInt(3), c.getInt(4),
                                c.getInt(5), c.getInt(6)});
                    }
                } catch (RuntimeException gone) {
                    return;
                }
                main.post(() -> show(rows));
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    private void show(List<Object[]> rows) {
        if (activity.isDestroyed()) return;
        if (rows.isEmpty()) {
            if (card != null) card.setVisibility(View.GONE);
            return;
        }
        FrameLayout root = activity.findViewById(R.id.root);
        if (card == null) {
            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            wire(activity, card, false, unit);
            // Mouse or pointer: a click opens the add-on's full weather page. Never a D-pad stop.
            card.setClickable(true);
            card.setOnClickListener(v -> {
                try {
                    activity.startActivity(new android.content.Intent().setClassName(pkg, pkg + ".WeatherActivity")
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (RuntimeException missing) {
                    // The add-on lost its page: nothing to open.
                }
            });
            card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.setBackground(glass(unit));
            int pad = Math.round(px(36));
            card.setPadding(pad, Math.round(px(22)), pad, Math.round(px(22)));
            // No elevation: a shadow under a large translucent card is redrawn on every frame of the glass moving.
            card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            int width = Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.29f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, Math.round(px(WEATHER_HEIGHT)),
                    Gravity.TOP | Gravity.END);
            lp.topMargin = Math.round(px(250));
            lp.rightMargin = Math.round(px(114));
            // Under the glass focus highlight, which is the root's last child.
            root.addView(card, Math.max(0, root.getChildCount() - 1), lp);
        }
        card.setVisibility(View.VISIBLE);
        card.removeAllViews();

        Object[] now = rows.get(0);
        TextView city = text(((String) now[0]).toUpperCase(Locale.getDefault()), 21, R.string.font_medium, 0xCCFFFFFF);
        city.setLetterSpacing(0.22f);
        card.addView(city);

        LinearLayout big = new LinearLayout(activity);
        big.setOrientation(LinearLayout.HORIZONTAL);
        big.setGravity(Gravity.CENTER_VERTICAL);
        big.addView(text(icon((Integer) now[5], (Integer) now[6] == 1), 54, R.string.font_regular, Color.WHITE));
        TextView temp = text(now[2] + "°", 84, R.string.font_extralight, Color.WHITE);
        temp.setPadding(Math.round(px(12)), 0, Math.round(px(20)), 0);
        big.addView(temp);
        LinearLayout words = new LinearLayout(activity);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text((String) now[1], 26, R.string.font_regular, Color.WHITE));
        words.addView(text(activity.getString(R.string.weather_feels, (Integer) now[4]), 20, R.string.font_light, 0xBFFFFFFF));
        big.addView(words);
        card.addView(big);

        View line = new View(activity);
        line.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(px(2))));
        lineLp.topMargin = Math.round(px(8));
        lineLp.bottomMargin = Math.round(px(10));
        card.addView(line, lineLp);

        LinearLayout days = new LinearLayout(activity);
        days.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i < rows.size(); i++) {
            Object[] d = rows.get(i);
            LinearLayout col = new LinearLayout(activity);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView name = text(((String) d[0]).replace(".", "").toUpperCase(Locale.getDefault()), 19, R.string.font_medium, 0xB3FFFFFF);
            name.setLetterSpacing(0.15f);
            TextView sky = text(icon((Integer) d[5], true), 34, R.string.font_regular, Color.WHITE);
            TextView range = text(d[2] + "°  " + d[3] + "°", 23, R.string.font_regular, Color.WHITE);
            for (TextView t : new TextView[] {name, sky, range}) {
                t.setGravity(Gravity.CENTER_HORIZONTAL);
                col.addView(t, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            sky.setPadding(0, Math.round(px(4)), 0, Math.round(px(4)));
            days.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(days);
    }

    View card() {
        return card;
    }

    /** D-pad on the card: OK opens it (click), Up the top bar, Down the row, Left/Right the other card. */
    static View.OnKeyListener wire(MainActivity activity, View card, boolean tv, float unit) {
        card.setFocusable(true);
        View.OnKeyListener keys = (v, code, e) -> {
            if (e.getAction() != android.view.KeyEvent.ACTION_DOWN) {
                return code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                        || code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
            }
            switch (code) {
                case android.view.KeyEvent.KEYCODE_DPAD_UP:
                    activity.focusTopBarFromPanel(v);
                    return true;
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                    View below = tv ? null : activity.picksCard();
                    if (below != null) below.requestFocus();
                    else activity.focusRowFromPanel(v);
                    return true;
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                    boolean towardTv = code == android.view.KeyEvent.KEYCODE_DPAD_LEFT;
                    View other = towardTv != tv ? activity.panelCard(towardTv) : null;
                    if (other != null) other.requestFocus();
                    return true;
                default:
                    return false;
            }
        };
        card.setOnKeyListener(keys);
        card.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = glass(unit);
            if (has) g.setStroke(Math.max(2, Math.round(4 * unit)), 0xF2FFFFFF);
            v.setBackground(g);
            v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(has ? 120 : 90).start();
        });
        return keys;
    }

    /** {@code old} sits above {@code card} on screen: the remote came down to it. */
    static boolean above(View old, View card) {
        int[] a = new int[2], b = new int[2];
        old.getLocationOnScreen(a);
        card.getLocationOnScreen(b);
        // Middle against top: a focused card grows 3 %, so its bottom edge may overlap the card under it.
        return a[1] + old.getHeight() / 2 < b[1];
    }

    private TextView text(String s, float size, int font, int colour) {
        return text(activity, unit, s, size, font, colour);
    }

    /** Smoked glass: a dark translucent fill, lighter at the top left, and a thin bright edge. No blur. */
    static GradientDrawable glass(float unit) {
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x8C343E4E, 0x99161C26, 0xB30A0D14});
        glass.setCornerRadius(40 * unit);
        glass.setStroke(Math.max(1, Math.round(2 * unit)), 0x66FFFFFF);
        return glass;
    }

    static TextView text(android.content.Context ctx, float unit, String s, float size, int font, int colour) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        Typeface face = UiFont.get(ctx, font);
        t.setTypeface(face);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setShadowLayer(6 * unit, 0, 2 * unit, 0x66000000);
        return t;
    }

    private float px(float at1080) {
        return at1080 * unit;
    }

    /** A weather symbol for a WMO code. */
    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1 || code == 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 95) return "⛈️";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "❄️";
        if (code >= 51 && code <= 82) return "🌧️";
        return "☁️";
    }
}
