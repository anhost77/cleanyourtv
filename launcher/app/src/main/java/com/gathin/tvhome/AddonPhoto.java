package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Local addition (tv_panel add-on): the home photo the household sends from the phone to the add-on's Réglages.
 * The launcher stays offline: it reads content://&lt;pkg&gt;.now/photo (a version, then the JPEG) and copies it over
 * its own wallpaper.jpg. The theme's own wallpaper is kept aside (wallpaper-theme.jpg) on the first copy; removing
 * the photo leaves the plain background.
 */
final class AddonPhoto {
    private static final String PREFS = "addon_photo";
    private static final String KEPT = "wallpaper-theme.jpg";
    private static volatile boolean busy;

    private AddonPhoto() { }

    /** The add-on's Réglages screen, or null when it has none. */
    static Intent settingsIntent(Context ctx, String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        Intent i = new Intent(pkg.trim() + ".SETTINGS").setPackage(pkg.trim()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return ctx.getPackageManager().resolveActivity(i, 0) != null ? i : null;
    }

    /** Off the main thread: copies a new photo in (or puts the theme's back), then runs {@code changed} on it. */
    static void sync(Activity activity, String pkg, Runnable changed) {
        if (pkg == null || pkg.trim().isEmpty() || busy) return;
        Context app = activity.getApplicationContext();
        Uri uri = Uri.parse("content://" + pkg.trim() + ".now/photo");
        busy = true;
        new Thread(() -> {
            try {
                if (copy(app, uri)) activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) changed.run();
                });
            } finally {
                busy = false;
            }
        }, "addon-photo").start();
    }

    private static boolean copy(Context ctx, Uri uri) {
        long version;
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return false;
            version = c.getLong(0);
        } catch (RuntimeException noAddon) {
            return false;
        }
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (version == prefs.getLong("version", 0)) return false;
        File dir = Config.dir(ctx);
        if (dir == null) return false;
        File wallpaper = new File(dir, Config.WALLPAPER_NAME), kept = new File(dir, KEPT);
        try {
            if (version == 0) {
                // Photo removed in Réglages: no photo at all, the theme's plain background (not the picture that
                // was there before, which may be one the household wanted gone too).
                if (wallpaper.isFile() && !wallpaper.delete()) return false;
            } else {
                if (!kept.isFile() && wallpaper.isFile() && !wallpaper.renameTo(kept)) return false;
                File tmp = new File(dir, Config.WALLPAPER_NAME + ".tmp");
                try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(tmp)) {
                    if (in == null) return false;
                    byte[] b = new byte[64 * 1024];
                    for (int n; (n = in.read(b)) > 0; ) out.write(b, 0, n);
                }
                if (!tmp.renameTo(wallpaper)) return false;
            }
        } catch (Exception e) {
            return false;
        }
        prefs.edit().putLong("version", version).apply();
        return true;
    }
}
