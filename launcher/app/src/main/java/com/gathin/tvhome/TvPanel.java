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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * tv_panel (config.xml): tonight's prime time as a glass card on the left half of Home, the twin of
 * {@link WeatherPanel}. The launcher stays offline: the add-on publishes rows on content://&lt;pkg&gt;.now/tv
 * (channel, time, title, kind, logo PNG) and Home only reads them while it is on screen. Eight channels a page,
 * turning every 10 seconds while Home is shown. A mouse or pointer click on a row opens that programme in the add-on's
 * guide (action &lt;pkg&gt;.TONIGHT, extra "index").
 * Two tabs in the card's header, CE SOIR À LA TÉLÉ and RADIOS (the add-on's stations, content://&lt;pkg&gt;.now/radio).
 * The D-pad follows the screen: Up from the card reaches the tabs, Left / Right there pick one, Up again the top bar.
 * Down from the tabs enters the list: on Radios, Up / Down choose a station, OK plays it through the add-on's
 * RadioService and OK again stops it; Down past the last one goes to the row. Right outside the tabs: the weather.
 */
final class TvPanel {
    private static final String[] COLUMNS = {"channel", "time", "title", "kind", "logo"};
    private static final int PER_PAGE = 8;
    private static final long TURN_MS = 10_000;

    private final MainActivity activity;
    private final String pkg;
    private final Uri uri;
    private final Handler main;
    private final ContentObserver observer;
    private final float unit;
    private final Runnable turn = this::nextPage;
    private final List<Row> rows = new ArrayList<>();
    private LinearLayout card;
    private LinearLayout list;
    private LinearLayout dots;
    private int page;
    private boolean watching;
    /** The Radios tab is shown instead of tonight's programmes. */
    private boolean radio;
    private int chosen;
    private final Uri radioUri;
    private final List<Station> stations = new ArrayList<>();
    private TextView tabTv, tabRadio;
    /** The tabs in the header have the remote, not the list. */
    private boolean onTabs;
    private View.OnKeyListener baseKeys;

    private static final class Station {
        String name, kind;
        Bitmap logo;
        boolean playing;
    }

    private static final class Row {
        String channel, time, title, kind;
        Bitmap logo;
    }

    private TvPanel(MainActivity activity, String pkg, Uri uri, Handler main) {
        this.activity = activity;
        this.pkg = pkg;
        this.uri = uri;
        this.main = main;
        this.unit = activity.getResources().getDisplayMetrics().heightPixels / 1080f;
        this.radioUri = uri.buildUpon().path("radio").build();
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
                readRadio();
            }
        };
    }

    static TvPanel create(MainActivity activity, String pkg, Handler main) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        String authority = pkg.trim() + ".now";
        ProviderInfo provider = activity.getPackageManager().resolveContentProvider(authority, 0);
        if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).appendPath("tv").build();
        return new TvPanel(activity, pkg.trim(), uri, main);
    }

    void start() {
        if (!watching) {
            try {
                activity.getContentResolver().registerContentObserver(uri, false, observer);
                activity.getContentResolver().registerContentObserver(radioUri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on went away.
            }
        }
        read();
        readRadio();
        schedule();
    }

    void stop() {
        if (watching) activity.getContentResolver().unregisterContentObserver(observer);
        watching = false;
        main.removeCallbacks(turn);
    }

    private void schedule() {
        main.removeCallbacks(turn);
        if (watching && !radio && rows.size() > PER_PAGE) main.postDelayed(turn, TURN_MS);
    }

    private void read() {
        new Thread(() -> {
            List<Row> fresh = new ArrayList<>();
            try (Cursor c = activity.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                while (c != null && c.moveToNext()) {
                    Row r = new Row();
                    r.channel = c.getString(0);
                    r.time = c.getString(1);
                    r.title = c.getString(2);
                    r.kind = c.getString(3);
                    byte[] png = c.getBlob(4);
                    if (png != null) r.logo = BitmapFactory.decodeByteArray(png, 0, png.length);
                    fresh.add(r);
                }
            } catch (RuntimeException gone) {
                return;
            }
            main.post(() -> {
                rows.clear();
                rows.addAll(fresh);
                if (page * PER_PAGE >= rows.size()) page = 0;
                show();
                schedule();
            });
        }, "tv-panel").start();
    }

    private void readRadio() {
        new Thread(() -> {
            List<Station> fresh = new ArrayList<>();
            try (Cursor c = activity.getContentResolver().query(radioUri, new String[] {"name", "logo", "playing", "kind"},
                    null, null, null)) {
                while (c != null && c.moveToNext()) {
                    Station st = new Station();
                    st.name = c.getString(0);
                    byte[] png = c.getBlob(1);
                    if (png != null) st.logo = BitmapFactory.decodeByteArray(png, 0, png.length);
                    st.playing = c.getInt(2) == 1;
                    st.kind = c.getString(3);
                    fresh.add(st);
                }
            } catch (RuntimeException gone) {
                return;
            }
            main.post(() -> {
                stations.clear();
                stations.addAll(fresh);
                paintTabs();
                if (radio && card != null) fill();
            });
        }, "radio-panel").start();
    }

    /** Left on the card: tonight's programmes or the radios. */
    private void toggle() {
        radio = !radio && !stations.isEmpty();
        page = radio ? chosen / PER_PAGE : 0;
        paintTabs();
        fill();
        schedule();
    }

    /** OK on a station: plays it, or stops it when it is the one playing. */
    private void play(int index) {
        if (index < 0 || index >= stations.size()) return;
        Intent i = new Intent(pkg + ".RADIO_PLAY").setPackage(pkg).putExtra("index", index);
        try {
            activity.startForegroundService(i);
        } catch (RuntimeException refused) {
            // An add-on without the radio.
        }
    }

    private boolean radioKey(View v, int code, android.view.KeyEvent e) {
        boolean down = e.getAction() == android.view.KeyEvent.ACTION_DOWN;
        boolean ok = code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER;
        boolean arrow = code == android.view.KeyEvent.KEYCODE_DPAD_UP || code == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                || code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!down) {
            if (ok && !e.isCanceled()) {
                if (onTabs) toggle();
                else if (radio) play(chosen);
                else open(page * PER_PAGE);
                return true;
            }
            return arrow || ok;
        }
        switch (code) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP:
                if (onTabs) return baseKeys.onKey(v, code, e);
                if (radio && chosen > 0) choose(chosen - 1);
                else setTabs(true);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                if (onTabs) setTabs(false);
                else if (radio && chosen < stations.size() - 1) choose(chosen + 1);
                else return baseKeys.onKey(v, code, e);
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                if (onTabs && radio && e.getRepeatCount() == 0) toggle();
                return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                if (onTabs && !radio && !stations.isEmpty()) {
                    if (e.getRepeatCount() == 0) toggle();
                    return true;
                }
                return baseKeys.onKey(v, code, e);
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
                return true;
            default:
                return baseKeys.onKey(v, code, e);
        }
    }

    private void setTabs(boolean on) {
        onTabs = on;
        paintTabs();
        if (radio) fill();
    }

    /** The open tab is white; with the remote on the tabs it wears the pill the "Tout voir" button wears. */
    private void paintTabs() {
        if (tabTv == null) return;
        boolean focused = card.hasFocus() && onTabs;
        for (TextView t : new TextView[] {tabTv, tabRadio}) {
            boolean open = (t == tabRadio) == radio;
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(20 * unit);
            d.setColor(open && focused ? Color.WHITE : open ? 0x33FFFFFF : 0x00000000);
            t.setBackground(d);
            t.setTextColor(open && focused ? Color.BLACK : open ? Color.WHITE : 0x99FFFFFF);
        }
        tabRadio.setVisibility(stations.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void choose(int index) {
        chosen = index;
        int p = chosen / PER_PAGE;
        if (p != page) page = p;
        fill();
    }

    private void nextPage() {
        int pages = (rows.size() + PER_PAGE - 1) / PER_PAGE;
        if (pages < 2 || card == null) return;
        page = (page + 1) % pages;
        list.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            fill();
            list.animate().alpha(1f).setDuration(220).start();
        }).start();
        schedule();
    }

    private void show() {
        if (activity.isDestroyed()) return;
        if (rows.isEmpty()) {
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
        card.setGravity(Gravity.CENTER_VERTICAL);
        baseKeys = WeatherPanel.wire(activity, card, true, unit);
        card.setOnKeyListener(this::radioKey);
        View.OnFocusChangeListener glassFocus = card.getOnFocusChangeListener();
        card.setOnFocusChangeListener((v, has) -> {
            if (glassFocus != null) glassFocus.onFocusChange(v, has);
            if (!has) onTabs = false;
            paintTabs();
            if (radio) fill();
        });
        // Coming down from the top bar: the tabs first, as on screen.
        card.getViewTreeObserver().addOnGlobalFocusChangeListener((old, now) -> {
            if (now == card && old != null && WeatherPanel.above(old, card)) setTabs(true);
        });
        // Mouse or pointer: a click on the card opens the guide; a click on a row opens that programme.
        card.setClickable(true);
        card.setOnClickListener(v -> {
            if (radio) play(chosen);
            else open(page * PER_PAGE);
        });
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.setBackground(WeatherPanel.glass(unit));
        int pad = Math.round(32 * unit);
        card.setPadding(pad, Math.round(36 * unit), pad, Math.round(26 * unit));
        // Cached as a texture: the D-pad redraws the screen, not this card.
        card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int width = Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.29f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, Math.round(WeatherPanel.CARD_HEIGHT * unit),
                Gravity.TOP | Gravity.START);
        lp.topMargin = Math.round(250 * unit);
        lp.leftMargin = Math.round(114 * unit);
        root.addView(card, Math.max(0, root.getChildCount() - 1), lp);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        tabTv = tab(R.string.tv_tonight);
        tabTv.setOnClickListener(v -> {
            if (radio) toggle();
        });
        head.addView(tabTv);
        tabRadio = tab(R.string.radio_title);
        tabRadio.setOnClickListener(v -> {
            if (!radio) toggle();
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rl.leftMargin = Math.round(8 * unit);
        head.addView(tabRadio, rl);
        head.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        dots = new LinearLayout(activity);
        head.addView(dots);
        head.setPadding(0, 0, 0, Math.round(12 * unit));
        card.addView(head);

        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        card.addView(list);
    }

    private void fill() {
        list.removeAllViews();
        if (radio) {
            fillRadio();
            return;
        }
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(rows.size(), from + PER_PAGE); i++) {
            Row r = rows.get(i);
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Math.round(8 * unit), Math.round(8 * unit), Math.round(8 * unit), Math.round(8 * unit));
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(Math.round(96 * unit), Math.round(36 * unit));
            cell.rightMargin = Math.round(14 * unit);
            if (r.logo != null) {
                ImageView logo = new ImageView(activity);
                logo.setImageBitmap(r.logo);
                logo.setScaleType(ImageView.ScaleType.FIT_START);
                logo.setAdjustViewBounds(true);
                line.addView(logo, cell);
            } else {
                TextView name = WeatherPanel.text(activity, unit, r.channel, 25, R.string.font_medium, Color.WHITE);
                name.setGravity(Gravity.CENTER_VERTICAL);
                line.addView(name, cell);
            }
            TextView time = WeatherPanel.text(activity, unit, r.time, 22, R.string.font_light, 0xB3FFFFFF);
            line.addView(time, new LinearLayout.LayoutParams(Math.round(76 * unit), LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView title = WeatherPanel.text(activity, unit, r.title, 25, R.string.font_regular, Color.WHITE);
            title.setEllipsize(TextUtils.TruncateAt.END);
            line.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            // Mouse or pointer only: clickable without being focusable, so the D-pad never stops here.
            final int index = i;
            line.setFocusable(false);
            line.setClickable(true);
            line.setOnClickListener(v -> open(index));
            line.setOnHoverListener((v, e) -> {
                v.setBackground(e.getAction() == android.view.MotionEvent.ACTION_HOVER_EXIT ? null : hover());
                return false;
            });
            list.addView(line);
        }
        dots.removeAllViews();
        int pages = (rows.size() + PER_PAGE - 1) / PER_PAGE;
        for (int p = 0; pages > 1 && p < pages; p++) {
            View dot = new View(activity);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(p == page ? Color.WHITE : 0x55FFFFFF);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.round(10 * unit), Math.round(10 * unit));
            dl.leftMargin = Math.round(8 * unit);
            dots.addView(dot, dl);
        }
    }

    private void fillRadio() {
        dots.removeAllViews();
        int from = page * PER_PAGE;
        for (int i = from; i < Math.min(stations.size(), from + PER_PAGE); i++) {
            Station st = stations.get(i);
            LinearLayout line = new LinearLayout(activity);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(Math.round(8 * unit), Math.round(7 * unit), Math.round(8 * unit), Math.round(7 * unit));
            boolean sel = i == chosen && card.hasFocus() && !onTabs;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(14 * unit);
            bg.setColor(st.playing ? 0x40FFD27A : sel ? 0x33FFFFFF : 0x00000000);
            if (sel) bg.setStroke(Math.max(2, Math.round(3 * unit)), Color.WHITE);
            line.setBackground(bg);
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(Math.round(36 * unit), Math.round(36 * unit));
            cell.rightMargin = Math.round(16 * unit);
            ImageView logo = new ImageView(activity);
            if (st.logo != null) logo.setImageBitmap(st.logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            line.addView(logo, cell);
            TextView name = WeatherPanel.text(activity, unit, st.name, 25, R.string.font_regular, Color.WHITE);
            line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView kind = WeatherPanel.text(activity, unit, st.playing ? activity.getString(R.string.radio_playing) : st.kind,
                    20, R.string.font_light, st.playing ? 0xFFFFD27A : 0x99FFFFFF);
            line.addView(kind);
            final int index = i;
            line.setClickable(true);
            line.setOnClickListener(v -> {
                chosen = index;
                play(index);
            });
            list.addView(line);
        }
        int pages = (stations.size() + PER_PAGE - 1) / PER_PAGE;
        for (int p = 0; pages > 1 && p < pages; p++) {
            View dot = new View(activity);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(p == page ? Color.WHITE : 0x55FFFFFF);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Math.round(10 * unit), Math.round(10 * unit));
            dl.leftMargin = Math.round(8 * unit);
            dots.addView(dot, dl);
        }
    }

    View card() {
        return card;
    }

    private TextView tab(int label) {
        TextView t = WeatherPanel.text(activity, unit, activity.getString(label), 21, R.string.font_medium, 0x99FFFFFF);
        t.setLetterSpacing(0.12f);
        t.setPadding(Math.round(14 * unit), Math.round(5 * unit), Math.round(14 * unit), Math.round(5 * unit));
        t.setClickable(true);
        return t;
    }

    private GradientDrawable hover() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x26FFFFFF);
        d.setCornerRadius(14 * unit);
        return d;
    }

    private void open(int index) {
        Intent i = new Intent(pkg + ".TONIGHT").setPackage(pkg).putExtra("index", index)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(i);
        } catch (RuntimeException missing) {
            // An add-on without the guide page: nothing to open.
        }
    }
}
