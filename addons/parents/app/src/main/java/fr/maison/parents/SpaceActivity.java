package fr.maison.parents;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The parents' space, behind the code: Proton VPN, the desktop browser, then the favourite sites as app-like
 * tiles, and Ajouter un site. Hold OK (or Menu) on a site to rename or remove it. Opened only by {@link LockActivity}
 * with a ticket that is good for a few seconds, so nothing else can open it.
 */
public final class SpaceActivity extends Activity {
    static final String VPN = "ch.protonvpn.android";
    static final String FIREFOX = "org.mozilla.firefox";
    private static final int COLUMNS = 5;
    private static long ticket;
    private static long ticketAt;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout grid;

    static long ticket(Context c) {
        ticket = new java.security.SecureRandom().nextLong();
        ticketAt = SystemClock.elapsedRealtime();
        return ticket;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean fresh = SystemClock.elapsedRealtime() - ticketAt < 10_000;
        if (state == null && (!fresh || getIntent().getLongExtra("unlocked", 0) != ticket || ticket == 0)) {
            finish();
            return;
        }
        ticket = 0;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackground(Ui.background());
        page.setPadding(Ui.px(this, 90), Ui.px(this, 60), Ui.px(this, 90), Ui.px(this, 40));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, getString(R.string.space_title), 44, Typeface.BOLD, Color.WHITE),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView lock = Ui.button(this, getString(R.string.space_lock), false);
        lock.setOnClickListener(v -> finish());
        head.addView(lock);
        page.addView(head);
        page.addView(Ui.text(this, getString(R.string.space_hint), 22, Typeface.NORMAL, 0x99FFFFFF));
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(Ui.px(this, 12), Ui.px(this, 30), Ui.px(this, 12), Ui.px(this, 12));
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        fill();
    }

    private void fill() {
        grid.removeAllViews();
        List<Sites.Site> sites = Sites.load(this);
        boolean vpn = getPackageManager().getLeanbackLaunchIntentForPackage(VPN) != null
                || getPackageManager().getLaunchIntentForPackage(VPN) != null;
        LinearLayout row = null;
        int n = 0;
        boolean firefox = getPackageManager().getLaunchIntentForPackage(FIREFOX) != null;
        // Fixed tiles first: Proton VPN, this space's browser, then Firefox when installed.
        // Proton VPN only when installed (it cuts the phone remote off on the free plan, so it was removed).
        final int vpnTiles = vpn ? 1 : 0;
        final int fixed = vpnTiles + 1 + (firefox ? 1 : 0);
        int total = fixed + sites.size() + 2;
        for (int i = 0; i < total; i++) {
            if (n % COLUMNS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.bottomMargin = Ui.px(this, 26);
                grid.addView(row, rl);
            }
            View tile;
            if (i == 0 && vpn) {
                tile = tile("🛡", "Proton VPN", 0xFF6D4AFF, null);
                tile.setOnClickListener(v -> openVpn());
            } else if (i == vpnTiles) {
                tile = tile("🌐", getString(R.string.space_browser), 0xFF1E6FD9, null);
                tile.setOnClickListener(v -> browse("https://www.google.fr"));
            } else if (i == vpnTiles + 1 && firefox) {
                tile = tile("🦊", "Firefox", 0xFFE66000, null);
                tile.setOnClickListener(v -> {
                    Intent ff = getPackageManager().getLaunchIntentForPackage(FIREFOX);
                    if (ff != null) startActivity(ff.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                });
            } else if (i < fixed + sites.size()) {
                Sites.Site s = sites.get(i - fixed);
                Bitmap icon = BitmapFactory.decodeFile(Sites.icon(this, s.url).getPath());
                tile = tile(s.name.isEmpty() ? "?" : s.name.substring(0, 1).toUpperCase(), s.name, 0xFF2E3A4C, icon);
                tile.setOnClickListener(v -> {
                    if (s.chrome && external(s.url)) return;
                    startActivity(new Intent(this, BrowserActivity.class)
                            .putExtra("url", s.url).putExtra("app", s.app).putExtra("width", s.width).putExtra("pc", s.pc));
                });
                final int index = i - fixed;
                tile.setOnLongClickListener(v -> {
                    edit(index);
                    return true;
                });
                tile.setOnKeyListener((v, code, e) -> {
                    if (code != KeyEvent.KEYCODE_MENU) return false;
                    if (e.getAction() == KeyEvent.ACTION_UP) edit(index);
                    return true;
                });
            } else if (i == fixed + sites.size()) {
                tile = tile("+", getString(R.string.space_add), 0x33FFFFFF, null);
                tile.setOnClickListener(v -> add(null, null));
            } else {
                tile = tile("🔑", getString(R.string.space_code), 0x33FFFFFF, null);
                tile.setOnClickListener(v -> changeCode());
            }
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.px(this, 300), Ui.px(this, 190));
            tl.rightMargin = Ui.px(this, 28);
            row.addView(tile, tl);
            n++;
        }
        if (grid.getChildCount() > 0) ((LinearLayout) grid.getChildAt(0)).getChildAt(vpn ? 1 : 0).requestFocus();
        // Icons for sites that have none yet.
        new Thread(() -> {
            boolean any = false;
            for (Sites.Site s : sites) any |= Sites.fetchIcon(this, s.url);
            if (any) main.post(() -> {
                if (!isFinishing()) fill();
            });
        }, "icons").start();
    }

    private View tile(String glyph, String label, int colour, Bitmap icon) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.pill(this, colour, 26));
        t.setFocusable(true);
        t.setClickable(true);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            t.addView(iv, new LinearLayout.LayoutParams(Ui.px(this, 84), Ui.px(this, 84)));
        } else {
            TextView g = Ui.text(this, glyph, 64, Typeface.BOLD, Color.WHITE);
            g.setGravity(Gravity.CENTER);
            t.addView(g);
        }
        TextView l = Ui.text(this, label, 24, Typeface.BOLD, Color.WHITE);
        l.setGravity(Gravity.CENTER);
        l.setPadding(Ui.px(this, 10), Ui.px(this, 12), Ui.px(this, 10), 0);
        l.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(l, new LinearLayout.LayoutParams(-1, -2));
        t.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? Ui.framed(this, colour, 26) : Ui.pill(this, colour, 26));
            v.animate().scaleX(has ? 1.07f : 1f).scaleY(has ? 1.07f : 1f).setDuration(110).start();
        });
        return t;
    }

    /** The site in Firefox, a full browser, if installed; false otherwise (then it opens here). */
    private boolean external(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(FIREFOX)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException notInstalled) {
            return false;
        }
    }

    private void browse(String url) {
        startActivity(new Intent(this, BrowserActivity.class).putExtra("url", url));
    }

    private void openVpn() {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(VPN);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(VPN);
        if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + VPN));
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException noStore) {
            browse("https://protonvpn.com/fr/download");
        }
    }

    /** Ajouter / modifier un site: a name and an address (nas.local:5000, 192.168.1.10, mon-site.fr…). */
    void add(String name, String url) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.px(this, 40), Ui.px(this, 20), Ui.px(this, 40), 0);
        EditText n = new EditText(this);
        n.setHint(R.string.site_name);
        n.setSingleLine(true);
        if (name != null) n.setText(name);
        EditText u = new EditText(this);
        u.setHint(R.string.site_url);
        u.setSingleLine(true);
        u.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (url != null) u.setText(url);
        form.addView(n);
        form.addView(u);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.space_add)
                .setView(form)
                .setPositiveButton(R.string.site_save, (d, w) -> {
                    String nn = n.getText().toString().trim(), uu = u.getText().toString().trim();
                    if (uu.isEmpty()) return;
                    Sites.add(this, nn.isEmpty() ? Sites.host(Sites.normalise(uu)) : nn, uu);
                    fill();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void edit(int index) {
        List<Sites.Site> all = Sites.load(this);
        if (index >= all.size()) return;
        Sites.Site s = all.get(index);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(s.name)
                .setItems(new String[] {getString(R.string.site_edit),
                        getString(s.app ? R.string.site_to_mouse : R.string.site_to_app),
                        getString(R.string.site_size, getString(s.width <= 1280 ? R.string.size_large
                                : s.width <= 1600 ? R.string.size_medium : R.string.size_pc)),
                        getString(s.pc ? R.string.site_id_pc : R.string.site_id_tv),
                        getString(s.chrome ? R.string.site_chrome_on : R.string.site_chrome_off),
                        getString(R.string.site_remove)}, (d, w) -> {
                    if (w == 4) {
                        s.chrome = !s.chrome;
                        Sites.save(this, all);
                        return;
                    }
                    if (w == 3) {
                        s.pc = !s.pc;
                        Sites.save(this, all);
                        return;
                    }
                    if (w == 1) {
                        s.app = !s.app;
                        Sites.save(this, all);
                        return;
                    }
                    if (w == 2) {
                        s.width = s.width <= 1280 ? 1600 : s.width <= 1600 ? 1920 : 1280;
                        Sites.save(this, all);
                        return;
                    }
                    all.remove(index);
                    Sites.save(this, all);
                    if (w == 0) add(s.name, s.url);
                    else fill();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void changeCode() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.space_code)
                .setMessage(R.string.space_code_msg)
                .setPositiveButton(R.string.lock_ok, (d, w) -> {
                    LockActivity.forget(this);
                    startActivity(new Intent(this, LockActivity.class));
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
