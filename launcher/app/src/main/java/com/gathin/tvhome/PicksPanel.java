package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Local addition: "À voir", a glass widget under the weather card with what is popular this week on the household's
 * platforms, read from the Weather add-on (content://&lt;pkg&gt;.now/picks: id, tv, name, poster JPEG). The launcher
 * stays offline. Four posters a page, turning every 12 seconds. D-pad: Left/Right move along the posters (Left
 * from the first goes to the TV card), OK opens the chosen title's sheet in the add-on, Up the weather, Down the row.
 * A mouse click on a poster opens its sheet too.
 */
final class PicksPanel {
    private static final String[] COLUMNS = {"id", "tv", "name", "poster"};
    private static final int PER_PAGE = 4;
    private static final long TURN_MS = 12_000;

    private static final class Pick {
        int id;
        boolean tv;
        String name;
        Bitmap poster;
    }

    private final MainActivity activity;
    private final String pkg;
    private final Uri uri;
    private final Handler main;
    private final ContentObserver observer;
    private final float unit;
    private final List<Pick> picks = new ArrayList<>();
    private final List<ImageView> slots = new ArrayList<>();
    private final Runnable turn = this::nextPage;
    private LinearLayout card;
    private TextView label, all;
    /** The "Tout voir" button in the widget's header is chosen. */
    private static final int HEADER = -2;
    private int page, chosen = -1;
    private boolean watching;

    private PicksPanel(MainActivity activity, String pkg, Uri uri, Handler main) {
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

    static PicksPanel create(MainActivity activity, String pkg, Handler main) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        String authority = pkg.trim() + ".now";
        ProviderInfo provider = activity.getPackageManager().resolveContentProvider(authority, 0);
        if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).appendPath("picks").build();
        return new PicksPanel(activity, pkg.trim(), uri, main);
    }

    void start() {
        if (!watching) {
            try {
                activity.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on went away.
            }
        }
        read();
        schedule();
    }

    void stop() {
        if (watching) activity.getContentResolver().unregisterContentObserver(observer);
        watching = false;
        main.removeCallbacks(turn);
    }

    View card() {
        return card;
    }

    private void schedule() {
        main.removeCallbacks(turn);
        if (watching && picks.size() > PER_PAGE && (card == null || !card.hasFocus())) main.postDelayed(turn, TURN_MS);
    }

    private void read() {
        new Thread(() -> {
            List<Pick> fresh = new ArrayList<>();
            try (Cursor c = activity.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    Pick p = new Pick();
                    p.id = c.getInt(0);
                    p.tv = c.getInt(1) == 1;
                    p.name = c.getString(2);
                    byte[] jpg = c.getBlob(3);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    if (jpg != null) p.poster = BitmapFactory.decodeByteArray(jpg, 0, jpg.length, o);
                    if (p.poster != null) fresh.add(p);
                }
            } catch (RuntimeException gone) {
                return;
            }
            main.post(() -> {
                picks.clear();
                picks.addAll(fresh);
                if (page * PER_PAGE >= picks.size()) page = 0;
                show();
                schedule();
            });
        }, "picks-panel").start();
    }

    private void nextPage() {
        int pages = (picks.size() + PER_PAGE - 1) / PER_PAGE;
        if (pages < 2 || card == null) return;
        page = (page + 1) % pages;
        fill();
        schedule();
    }

    private void show() {
        if (activity.isDestroyed()) return;
        if (picks.isEmpty()) {
            if (card != null) card.setVisibility(View.GONE);
            return;
        }
        if (card == null) build();
        card.setVisibility(View.VISIBLE);
        fill();
    }

    private void build() {
        FrameLayout root = activity.findViewById(R.id.root);
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(WeatherPanel.glass(unit));
        int pad = Math.round(24 * unit);
        card.setPadding(Math.round(30 * unit), Math.round(16 * unit), Math.round(30 * unit), pad);
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        card.setFocusable(true);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int width = Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.29f);
        float top = 250 + WeatherPanel.WEATHER_HEIGHT + WeatherPanel.GAP;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width,
                Math.round((250 + WeatherPanel.CARD_HEIGHT - top) * unit), Gravity.TOP | Gravity.END);
        lp.topMargin = Math.round(top * unit);
        lp.rightMargin = Math.round(114 * unit);
        root.addView(card, Math.max(0, root.getChildCount() - 1), lp);

        label = WeatherPanel.text(activity, unit, activity.getString(R.string.picks_title), 20, R.string.font_medium, 0xCCFFFFFF);
        label.setLetterSpacing(0.18f);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, 0, 0, Math.round(8 * unit));
        head.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        all = WeatherPanel.text(activity, unit, activity.getString(R.string.picks_all), 18, R.string.font_medium, Color.WHITE);
        all.setPadding(Math.round(14 * unit), Math.round(4 * unit), Math.round(14 * unit), Math.round(4 * unit));
        all.setBackground(allBackground(false));
        all.setOnClickListener(v -> openAll());
        head.addView(all);
        card.addView(head);
        LinearLayout row = new LinearLayout(activity);
        row.setClipChildren(false);
        card.setClipChildren(false);
        for (int i = 0; i < PER_PAGE; i++) {
            ImageView iv = new ImageView(activity);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x22FFFFFF);
            bg.setCornerRadius(12 * unit);
            iv.setBackground(bg);
            iv.setClipToOutline(true);
            final int slot = i;
            iv.setOnClickListener(v -> open(page * PER_PAGE + slot));
            iv.setOnHoverListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_HOVER_ENTER) v.setForeground(frame());
                else if (e.getAction() == MotionEvent.ACTION_HOVER_EXIT && chosen != slot) v.setForeground(null);
                return false;
            });
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i < PER_PAGE - 1) il.rightMargin = Math.round(12 * unit);
            row.addView(iv, il);
            slots.add(iv);
        }
        card.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        card.setOnClickListener(v -> {
            if (chosen == HEADER) openAll();
            else open(page * PER_PAGE + Math.max(0, chosen));
        });

        card.setOnFocusChangeListener((v, has) -> {
            GradientDrawable g = WeatherPanel.glass(unit);
            if (has) g.setStroke(Math.max(2, Math.round(4 * unit)), 0xF2FFFFFF);
            v.setBackground(g);
            choose(has ? Math.max(0, chosen) : -1);
            if (has) main.removeCallbacks(turn);
            else schedule();
        });
        // Coming down from the weather (or the top bar): "Tout voir" first, as on screen. Posted, so it lands after
        // the card's own focus listener whatever order Android calls them in.
        card.getViewTreeObserver().addOnGlobalFocusChangeListener((old, now) -> {
            if (now == card && old != null && WeatherPanel.above(old, card)) {
                main.post(() -> {
                    if (card.hasFocus()) choose(HEADER);
                });
            }
        });
        card.setOnKeyListener((v, code, e) -> {
            boolean arrow = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (e.getAction() != KeyEvent.ACTION_DOWN) return arrow;
            int onPage = Math.min(PER_PAGE, picks.size() - page * PER_PAGE);
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP: {
                    if (chosen != HEADER) {
                        choose(HEADER);
                        return true;
                    }
                    View w = activity.panelCard(false);
                    if (w != null) w.requestFocus();
                    else activity.focusTopBarFromPanel(v);
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (chosen == HEADER) choose(0);
                    else activity.focusRowFromPanel(v);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (chosen == HEADER) {
                        View tvCard = activity.panelCard(true);
                        if (tvCard != null) tvCard.requestFocus();
                    } else if (chosen > 0) choose(chosen - 1);
                    else if (page > 0) {
                        page--;
                        fill();
                        choose(PER_PAGE - 1);
                    } else {
                        View tvCard = activity.panelCard(true);
                        if (tvCard != null) tvCard.requestFocus();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (chosen == HEADER) return true;
                    if (chosen < onPage - 1) choose(chosen + 1);
                    else if ((page + 1) * PER_PAGE < picks.size()) {
                        page++;
                        fill();
                        choose(0);
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void fill() {
        for (int i = 0; i < PER_PAGE; i++) {
            int n = page * PER_PAGE + i;
            ImageView iv = slots.get(i);
            iv.setImageBitmap(n < picks.size() ? picks.get(n).poster : null);
            iv.setVisibility(n < picks.size() ? View.VISIBLE : View.INVISIBLE);
        }
        if (card != null && card.hasFocus() && chosen != HEADER) choose(Math.min(Math.max(0, chosen), picks.size() - page * PER_PAGE - 1));
        else label.setText(activity.getString(R.string.picks_title));
    }

    private void choose(int slot) {
        chosen = slot;
        for (int i = 0; i < slots.size(); i++) {
            ImageView iv = slots.get(i);
            boolean on = i == slot;
            iv.setForeground(on ? frame() : null);
            iv.animate().scaleX(on ? 1.06f : 1f).scaleY(on ? 1.06f : 1f).setDuration(110).start();
        }
        all.setBackground(allBackground(slot == HEADER));
        all.setTextColor(slot == HEADER ? Color.BLACK : Color.WHITE);
        int n = page * PER_PAGE + slot;
        label.setText(slot >= 0 && n < picks.size() ? picks.get(n).name.toUpperCase(java.util.Locale.FRANCE)
                : activity.getString(R.string.picks_title));
    }

    /** "Tout voir": the add-on's Films et séries page, every platform. */
    private void openAll() {
        try {
            activity.startActivity(new Intent().setClassName(pkg, pkg + ".ReleasesActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException missing) {
            // An older add-on.
        }
    }

    private GradientDrawable allBackground(boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(on ? Color.WHITE : 0x33FFFFFF);
        d.setCornerRadius(20 * unit);
        if (on) d.setStroke(Math.max(2, Math.round(3 * unit)), Color.WHITE);
        return d;
    }

    private void open(int n) {
        if (n < 0 || n >= picks.size()) return;
        Pick p = picks.get(n);
        try {
            activity.startActivity(new Intent().setClassName(pkg, pkg + ".TitleActivity").putExtra("tmdb", p.id)
                    .putExtra("tv", p.tv).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException missing) {
            // An older add-on without the sheet.
        }
    }

    private GradientDrawable frame() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setStroke(Math.max(2, Math.round(4 * unit)), Color.WHITE);
        d.setCornerRadius(12 * unit);
        return d;
    }
}
