package fr.maison.parents;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

/**
 * The parents' browser: a WebView that presents itself as Chrome on a Windows PC, so sites serve their desktop
 * pages (a NAS's admin, a router, webmail…). The remote moves an on-screen mouse pointer (faster when held), OK clicks
 * where it points, and the pointer at the page's edge scrolls it; Up past the top edge reaches the toolbar (back,
 * forward, reload, the address, add to favourites, close). Back goes back a page. A real mouse or the phone's pointer
 * works directly. A NAS's own certificate is accepted after asking, once per address.
 */
public final class BrowserActivity extends Activity {
    private static final String DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    /** Page width in CSS pixels: 1920 like a PC screen, or larger text for a site (Sites.Site.width). */
    private int pcWidth = 1920;
    /** Sets the page's viewport to a 1920-pixel PC screen. */
    private String fitPc() {
        return FIT_HEAD + pcWidth + "';})();";
    }

    private static final String FIT_HEAD = "(function(){var m=document.querySelector('meta[name=viewport]');"
            + "if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m);}"
            + "m.content='width=";

    /** Mode appli: the remote moves to the nearest link or button in its direction (our own, far better than
     *  WebView's), scrolls it into view and frames it in white; tvPoint() gives its centre for OK. res/raw/tvnav.js. */
    private String NAV = "";
    /** Scrolls what is under the pointer (an inner panel if it scrolls, else the page). Fractions of the view. */
    private static final String SCROLL = "(function(fx,fy,dx,dy){var e=document.elementFromPoint(fx*innerWidth,fy*innerHeight);"
            + "while(e&&e!==document.body&&e!==document.documentElement){var s=getComputedStyle(e);"
            + "var v=(s.overflowY=='auto'||s.overflowY=='scroll')&&e.scrollHeight>e.clientHeight+2;"
            + "var h=(s.overflowX=='auto'||s.overflowX=='scroll')&&e.scrollWidth>e.clientWidth+2;"
            + "if((dy&&v)||(dx&&h)){var t=e.scrollTop,l=e.scrollLeft;e.scrollBy(dx,dy);if(e.scrollTop!=t||e.scrollLeft!=l)return 1;}"
            + "e=e.parentElement;}var y=scrollY,x=scrollX;scrollBy(dx,dy);return (scrollY!=y||scrollX!=x)?1:0;})";
    /** Run before any page script: no Android, no touch screen, a Windows PC with a 1920 × 1080 screen. */
    private static final String PC = "(function(){function d(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v},configurable:true})}catch(e){}}"
            + "d(Navigator.prototype,'platform','Win32');d(Navigator.prototype,'maxTouchPoints',0);"
            + "d(Navigator.prototype,'vendor','Google Inc.');d(Navigator.prototype,'oscpu',undefined);"
            + "if(navigator.userAgentData){var u=navigator.userAgentData,b=u.brands;"
            + "d(Navigator.prototype,'userAgentData',{brands:b,mobile:false,platform:'Windows',toJSON:function(){return{brands:b,mobile:false,platform:'Windows'}},"
            + "getHighEntropyValues:function(){return Promise.resolve({brands:b,mobile:false,platform:'Windows',platformVersion:'15.0.0',"
            + "architecture:'x86',bitness:'64',model:'',uaFullVersion:'142.0.0.0'})}});}"
            + "d(Screen.prototype,'width',1920);d(Screen.prototype,'height',1080);d(Screen.prototype,'availWidth',1920);"
            + "d(Screen.prototype,'availHeight',1040);"
            + "try{delete window.ontouchstart;delete Document.prototype.ontouchstart;}catch(e){}"
            + "var mm=window.matchMedia;window.matchMedia=function(q){var r=mm.call(window,q);"
            + "if(/pointer\\s*:\\s*coarse|hover\\s*:\\s*none/.test(q))return Object.assign(Object.create(r),{matches:false,media:q});"
            + "if(/pointer\\s*:\\s*fine|hover\\s*:\\s*hover/.test(q))return Object.assign(Object.create(r),{matches:true,media:q});return r};})();";
    private boolean appMode;
    private static final String FOCUS_STYLE = "(function(){if(document.getElementById('tv-focus'))return;"
            + "var s=document.createElement('style');s.id='tv-focus';s.textContent="
            + "':focus,:focus-visible{outline:4px solid #fff!important;outline-offset:3px!important;"
            + "box-shadow:0 0 0 7px rgba(0,0,0,.55)!important;border-radius:6px}';"
            + "(document.head||document.documentElement).appendChild(s);})();";
    private final Set<String> trusted = new HashSet<>();
    private WebView web;
    private EditText address;
    private LinearLayout bar;
    private Pointer pointer;
    /** A video in full screen (the page's own button): its view covers everything until Back. */
    private View fullView;
    private WebChromeClient.CustomViewCallback fullCallback;
    private static final String VIDEO = "(function(a){var v=document.fullscreenElement&&document.fullscreenElement.tagName=='VIDEO'"
            + "?document.fullscreenElement:(document.fullscreenElement||document).querySelector('video');if(!v)return;"
            + "if(a=='toggle'){v.paused?v.play():v.pause();}else{v.currentTime=Math.max(0,v.currentTime+a);}})";
    private float x, y;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF0D1118);

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(this, 20), Ui.px(this, 10), Ui.px(this, 20), Ui.px(this, 10));
        bar.addView(tool("‹", v -> { if (web.canGoBack()) web.goBack(); }));
        bar.addView(tool("›", v -> { if (web.canGoForward()) web.goForward(); }));
        bar.addView(tool("⟳", v -> web.reload()));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 24 * Ui.unit(this));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setBackground(Ui.pill(this, 0x26FFFFFF, 14));
        address.setPadding(Ui.px(this, 20), Ui.px(this, 8), Ui.px(this, 20), Ui.px(this, 8));
        address.setOnFocusChangeListener((v, has) -> v.setBackground(has ? Ui.framed(this, 0x33FFFFFF, 14) : Ui.pill(this, 0x26FFFFFF, 14)));
        address.setOnEditorActionListener((v, a, e) -> {
            go(address.getText().toString());
            return true;
        });
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(0, -2, 1f);
        al.leftMargin = Ui.px(this, 10);
        al.rightMargin = Ui.px(this, 10);
        bar.addView(address, al);
        bar.addView(tool("★", v -> favourite()));
        bar.addView(tool("✕", v -> finish()));
        for (int i = 0; i < bar.getChildCount(); i++) {
            bar.getChildAt(i).setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN) toPage();
                return true;
            });
        }
        page.addView(bar);

        FrameLayout frame = new FrameLayout(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        // "pc" false: the TV's own identity, so protected video (OQEE, myCANAL) gets the TV's hardware Widevine
        // (L1); a site that believes it talks to Windows asks for the software kind (L3), which this decoder refuses.
        boolean pc = getIntent().getBooleanExtra("pc", true);
        if (pc) s.setUserAgentString(DESKTOP);
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // A PC screen: pages are laid out 1920 CSS pixels wide (one per TV pixel) and drawn across the TV's width, whatever their
        // viewport tag says (rewritten by FIT_PC when each page commits).
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setTextZoom(100);
        // The PC disguise: before the page's own scripts where WebView allows it, Client Hints saying Windows and
        // not mobile, and no X-Requested-With header naming this app.
        if (pc && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(web, PC, java.util.Collections.singleton("*"));
        }
        if (pc && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
            androidx.webkit.WebSettingsCompat.setUserAgentMetadata(s, new androidx.webkit.UserAgentMetadata.Builder()
                    .setPlatform("Windows").setPlatformVersion("15.0.0").setMobile(false).setArchitecture("x86")
                    .setBitness(64).setModel("").setFullVersion("142.0.0.0").build());
        }
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, java.util.Collections.emptySet());
        }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullView = view;
                fullCallback = callback;
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(-1, -1));
                view.setBackgroundColor(Color.BLACK);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                leaveFullScreen();
            }

            /**
             * Protected video (Widevine: OQEE, myCANAL, Netflix on the web…): WebView refuses the page's DRM
             * request unless the app grants it. Only that is granted; camera and microphone stay refused.
             */
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                for (String r : request.getResources()) {
                    if (android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                        runOnUiThread(() -> request.grant(new String[] {android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID}));
                        return;
                    }
                }
                request.deny();
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                if (!address.hasFocus()) address.setText(url);
                if (getIntent().getBooleanExtra("pc", true)
                        && !androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    view.evaluateJavascript(PC, null);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                view.evaluateJavascript(fitPc(), null);
                view.setInitialScale(Math.round(100f * view.getWidth() / pcWidth));
                if (appMode) view.evaluateJavascript(NAV, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(fitPc(), null);
                if (appMode) {
                    view.evaluateJavascript(NAV, null);
                    return;
                }
                // A page that put the text cursor in a field (Google's search box) must not take the remote.
                view.evaluateJavascript("if(document.activeElement&&document.activeElement.blur)document.activeElement.blur();", null);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String host = Sites.host(error.getUrl());
                if (trusted.contains(host)) {
                    handler.proceed();
                    return;
                }
                new AlertDialog.Builder(BrowserActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(R.string.ssl_title)
                        .setMessage(getString(R.string.ssl_msg, host))
                        .setPositiveButton(R.string.ssl_go, (d, w) -> {
                            trusted.add(host);
                            handler.proceed();
                        })
                        .setNegativeButton(android.R.string.cancel, (d, w) -> handler.cancel())
                        .setOnCancelListener(d -> handler.cancel())
                        .show();
            }

            @Override
            public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
                LinearLayout form = new LinearLayout(BrowserActivity.this);
                form.setOrientation(LinearLayout.VERTICAL);
                form.setPadding(Ui.px(BrowserActivity.this, 40), Ui.px(BrowserActivity.this, 20), Ui.px(BrowserActivity.this, 40), 0);
                EditText user = new EditText(BrowserActivity.this);
                user.setHint(R.string.auth_user);
                EditText pass = new EditText(BrowserActivity.this);
                pass.setHint(R.string.auth_pass);
                pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                form.addView(user);
                form.addView(pass);
                new AlertDialog.Builder(BrowserActivity.this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle(host)
                        .setView(form)
                        .setPositiveButton(R.string.lock_ok, (d, w) -> handler.proceed(user.getText().toString(), pass.getText().toString()))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> handler.cancel())
                        .show();
            }
        });
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        pointer = new Pointer(this);
        frame.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        page.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);

        String url = getIntent().getStringExtra("url");
        try (java.io.InputStream in = getResources().openRawResource(R.raw.tvnav)) {
            // InputStream.readAllBytes is Android 13+; this TV is Android 11.
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
            NAV = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            NAV = "";
        }
        appMode = getIntent().getBooleanExtra("app", false);
        pcWidth = getIntent().getIntExtra("width", 1920);
        if (appMode) {
            bar.setVisibility(View.GONE);
            pointer.setVisibility(View.GONE);
            TextView hint = Ui.text(this, getString(R.string.app_mode_hint), 20, Typeface.NORMAL, Color.WHITE);
            hint.setBackground(Ui.pill(this, 0xB3000000, 14));
            hint.setPadding(Ui.px(this, 20), Ui.px(this, 8), Ui.px(this, 20), Ui.px(this, 8));
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            hl.bottomMargin = Ui.px(this, 24);
            frame.addView(hint, hl);
            hint.postDelayed(() -> hint.animate().alpha(0f).setDuration(600).withEndAction(() -> hint.setVisibility(View.GONE)).start(), 5000);
        }
        web.post(() -> web.setInitialScale(Math.round(100f * web.getWidth() / pcWidth)));
        web.loadUrl(url == null ? "https://www.google.fr" : Sites.normalise(url));
        web.post(() -> {
            x = web.getWidth() / 2f;
            y = web.getHeight() / 3f;
            if (appMode) web.requestFocus();
            else toPage();
        });
    }

    private TextView tool(String glyph, View.OnClickListener click) {
        TextView t = Ui.button(this, glyph, false);
        t.setPadding(Ui.px(this, 22), Ui.px(this, 8), Ui.px(this, 22), Ui.px(this, 8));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.px(this, 8);
        t.setLayoutParams(lp);
        return t;
    }

    private void go(String text) {
        String t = text.trim();
        if (t.isEmpty()) return;
        boolean looksLikeUrl = t.contains("://") || (t.contains(".") && !t.contains(" ")) || t.matches("^[\\w.-]+:\\d+.*");
        web.loadUrl(looksLikeUrl ? Sites.normalise(t)
                : "https://www.google.fr/search?q=" + android.net.Uri.encode(t));
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        toPage();
    }

    private void favourite() {
        String url = web.getUrl();
        if (url == null) return;
        String title = web.getTitle();
        EditText n = new EditText(this);
        n.setSingleLine(true);
        n.setText(title == null || title.isEmpty() ? Sites.host(url) : title);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.fav_title)
                .setView(n)
                .setPositiveButton(R.string.site_save, (d, w) -> {
                    Sites.add(this, n.getText().toString().trim(), url);
                    new Thread(() -> Sites.fetchIcon(this, url), "icon").start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The pointer takes over: the page gets focus for typing, the toolbar loses it. */
    private void toPage() {
        pointer.setVisibility(View.VISIBLE);
        web.requestFocus();
        pointer.invalidate();
    }

    /** The pointer has the remote unless the toolbar (or a dialog) does. */
    private boolean pointing() {
        View f = getCurrentFocus();
        return pointer.getVisibility() == View.VISIBLE && f != null && f.getParent() != bar && f != address;
    }

    private void leaveFullScreen() {
        if (fullView == null) return;
        ((FrameLayout) getWindow().getDecorView()).removeView(fullView);
        fullView = null;
        if (fullCallback != null) fullCallback.onCustomViewHidden();
        fullCallback = null;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        web.evaluateJavascript("if(document.fullscreenElement&&document.exitFullscreen)document.exitFullscreen();", null);
        toPage();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean arrow = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        // Full-screen video: OK pauses / plays, Left / Right go back / forward 10 s, Back leaves full screen.
        if (fullView != null) {
            if (e.getAction() == KeyEvent.ACTION_UP && code == KeyEvent.KEYCODE_BACK) {
                leaveFullScreen();
            } else if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                if (ok || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) web.evaluateJavascript(VIDEO + "('toggle')", null);
                else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_MEDIA_REWIND)
                    web.evaluateJavascript(VIDEO + "(-10)", null);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
                    web.evaluateJavascript(VIDEO + "(10)", null);
            }
            boolean volume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                    || code == KeyEvent.KEYCODE_VOLUME_MUTE;
            return !volume || super.dispatchKeyEvent(e);
        }
        // Mode appli: Menu switches to the mouse pointer (and back); Back goes back a page, then closes.
        if (appMode) {
            if (code == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) {
                boolean mouse = pointer.getVisibility() != View.VISIBLE;
                pointer.setVisibility(mouse ? View.VISIBLE : View.GONE);
                pointer.invalidate();
                web.requestFocus();
                return true;
            }
            if (pointer.getVisibility() != View.VISIBLE) {
                if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && web.canGoBack()) {
                    web.goBack();
                    return true;
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                boolean typing = imm.isActive(web) && imm.isAcceptingText()
                        && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT);
                if ((arrow || ok) && !typing) {
                    if (e.getAction() == KeyEvent.ACTION_DOWN) {
                        if (arrow) {
                            String dir = code == KeyEvent.KEYCODE_DPAD_UP ? "up" : code == KeyEvent.KEYCODE_DPAD_DOWN ? "down"
                                    : code == KeyEvent.KEYCODE_DPAD_LEFT ? "left" : "right";
                            web.evaluateJavascript(NAV + "tvNav('" + dir + "');", null);
                        } else if (e.getRepeatCount() == 0) {
                            web.evaluateJavascript(NAV + "tvPoint();", at -> {
                                String v = at == null ? "" : at.replace("\"", "");
                                String[] xy = v.split(",");
                                if (xy.length != 2) return;
                                try {
                                    x = Float.parseFloat(xy[0]) * web.getWidth();
                                    y = Float.parseFloat(xy[1]) * web.getHeight();
                                    click();
                                } catch (NumberFormatException ignored) {
                                    // Nothing chosen yet.
                                }
                            });
                        }
                    }
                    return true;
                }
                return super.dispatchKeyEvent(e);
            }
        }
        if (pointing() && (arrow || ok)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (arrow) move(code, e.getRepeatCount());
                else if (e.getRepeatCount() == 0) click();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP && web.canGoBack() && pointing()) {
            web.goBack();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void move(int code, int repeat) {
        float step = Math.min(70, 14 + repeat * 6) * Ui.unit(this);
        float w = web.getWidth(), h = web.getHeight();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: x -= step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: x += step; break;
            case KeyEvent.KEYCODE_DPAD_UP: y -= step; break;
            default: y += step; break;
        }
        // At an edge the page scrolls under the pointer; at the very top, a page that cannot go higher hands the
        // remote to the toolbar.
        float edge = 40 * Ui.unit(this);
        int dx = 0, dy = 0;
        if (y < edge) {
            y = Math.max(0, y);
            if (code == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
        } else if (y > h - edge) {
            y = Math.min(h - 1, y);
            if (code == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
        }
        if (x < edge) {
            x = Math.max(0, x);
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
        } else if (x > w - edge) {
            x = Math.min(w - 1, x);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
        }
        if (dx != 0 || dy != 0) scroll(dx * 320, dy * Math.min(700, 260 + repeat * 40), dy < 0 && repeat == 0 && y <= 0);
        pointer.invalidate();
    }

    private void scroll(int dx, int dy, boolean toolbarIfStuck) {
        String js = SCROLL + "(" + (x / web.getWidth()) + "," + (y / web.getHeight()) + "," + dx + "," + dy + ")";
        web.evaluateJavascript(js, moved -> {
            if (toolbarIfStuck && "0".equals(moved)) {
                pointer.setVisibility(View.INVISIBLE);
                bar.getChildAt(3).requestFocus();
            }
        });
    }

    /** A mouse wheel turn at the pointer: scrolls whatever is under it, page or inner panel. */
    private void wheel(float v) {
        send(MotionEvent.AXIS_VSCROLL, v);
    }

    private void hwheel(float h) {
        send(MotionEvent.AXIS_HSCROLL, h);
    }

    private void send(int axis, float value) {
        MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = {new MotionEvent.PointerCoords()};
        pc[0].x = x;
        pc[0].y = y;
        pc[0].setAxisValue(axis, value);
        long t = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, pp, pc, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private void click() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
        pointer.pulse();
    }

    /** The on-screen arrow, drawn where the remote points. */
    private final class Pointer extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private long pulseAt;

        Pointer(Context c) {
            super(c);
            fill.setColor(Color.WHITE);
            edge.setColor(Color.BLACK);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(2.5f * Ui.unit(c));
            float s = 1.6f * Ui.unit(c);
            arrow.moveTo(0, 0);
            arrow.lineTo(0, 26 * s);
            arrow.lineTo(7 * s, 19 * s);
            arrow.lineTo(12 * s, 30 * s);
            arrow.lineTo(16 * s, 28 * s);
            arrow.lineTo(11 * s, 17 * s);
            arrow.lineTo(20 * s, 17 * s);
            arrow.close();
            setFocusable(false);
        }

        void pulse() {
            pulseAt = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            long since = SystemClock.uptimeMillis() - pulseAt;
            if (since < 250) {
                Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
                ring.setColor(0x66FFFFFF);
                c.drawCircle(x, y, (10 + since / 8f) * Ui.unit(getContext()), ring);
                postInvalidateOnAnimation();
            }
            c.save();
            c.translate(x, y);
            c.drawPath(arrow, fill);
            c.drawPath(arrow, edge);
            c.restore();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }
}
