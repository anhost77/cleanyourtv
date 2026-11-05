package fr.maison.parents;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** The parents' favourite sites (name, address) and their icons, kept in the app's own storage only. */
final class Sites {
    static final class Site {
        String name, url;
        /** Mode appli: full screen, the remote moves between links and buttons. Off = the mouse pointer. */
        boolean app = true;
        /** Page width in CSS pixels: 1280 grande, 1600 moyenne, 1920 PC. */
        int width = 1280;
        /** Seen as a Windows PC (true) or as this TV (false: protected video gets the TV's hardware DRM). */
        boolean pc = true;
        /** Opened in Google Chrome (its hardware Widevine plays what this app's WebView cannot) instead of here. */
        boolean chrome = false;

        Site(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    private Sites() { }

    static List<Site> load(Context c) {
        List<Site> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("sites", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Site s = new Site(o.optString("name"), o.optString("url"));
                s.app = o.optBoolean("app", true);
                s.width = o.optInt("width", s.app ? 1280 : 1920);
                s.pc = o.optBoolean("pc", !s.url.contains("oqee"));
                s.chrome = o.optBoolean("chrome", false);
                out.add(s);
            }
        } catch (Exception broken) {
            out.clear();
        }
        return out;
    }

    static void save(Context c, List<Site> sites) {
        JSONArray a = new JSONArray();
        try {
            for (Site s : sites) a.put(new JSONObject().put("name", s.name).put("url", s.url).put("app", s.app).put("width", s.width).put("pc", s.pc).put("chrome", s.chrome));
        } catch (Exception ignored) {
            return;
        }
        prefs(c).edit().putString("sites", a.toString()).apply();
    }

    static void add(Context c, String name, String url) {
        List<Site> all = load(c);
        Site s = new Site(name, normalise(url));
        s.pc = !s.url.contains("oqee");
        all.add(s);
        save(c, all);
    }

    /** "nas.local:5000" → "http://nas.local:5000"; a bare domain gets https. */
    static String normalise(String url) {
        String u = url.trim();
        if (u.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) return u;
        boolean local = u.matches("^(\\d{1,3}\\.){3}\\d{1,3}.*") || u.contains(".local") || u.startsWith("localhost")
                || !u.contains(".");
        return (local ? "http://" : "https://") + u;
    }

    static String host(String url) {
        String h = Uri.parse(url).getHost();
        return h == null ? url : h;
    }

    static File icon(Context c, String url) {
        return new File(new File(c.getFilesDir(), "icons"), Integer.toHexString(host(url).hashCode()) + ".png");
    }

    /** Tries the site's touch icon, then its favicon; true when one was saved. Run off the main thread. */
    static boolean fetchIcon(Context c, String url) {
        File f = icon(c, url);
        if (f.isFile()) return false;
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) return false;
        Uri u = Uri.parse(url);
        String origin = u.getScheme() + "://" + u.getAuthority();
        for (String path : new String[] {"/apple-touch-icon.png", "/favicon.ico"}) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(origin + path).openConnection();
                conn.setConnectTimeout(5_000);
                conn.setReadTimeout(6_000);
                Bitmap b;
                try (InputStream in = conn.getInputStream()) {
                    b = BitmapFactory.decodeStream(in);
                }
                if (b == null || b.getWidth() < 16) continue;
                try (FileOutputStream out = new FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                return true;
            } catch (Exception next) {
                // A NAS with its own certificate, or no icon: the tile keeps its letter.
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return false;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sites", Context.MODE_PRIVATE);
    }
}
