package fr.maison.parents;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The padlock: asks the parents' code before the space opens. The first time, the code is chosen (twice). Only a
 * salted SHA-256 of it is kept. Five wrong codes in a row lock the screen for a minute. The digits come from the
 * remote's number keys, the phone app's keyboard or the TV keyboard. Every opening asks again.
 */
public final class LockActivity extends Activity {
    private static final int MIN = 4;
    private SharedPreferences prefs;
    private EditText code;
    private TextView title, hint;
    private String first;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("lock", MODE_PRIVATE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setBackground(Ui.background());
        TextView lock = Ui.text(this, "🔒", 110, Typeface.NORMAL, Color.WHITE);
        lock.setGravity(Gravity.CENTER);
        page.addView(lock);
        title = Ui.text(this, "", 42, Typeface.BOLD, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.px(this, 20), 0, Ui.px(this, 8));
        page.addView(title);
        hint = Ui.text(this, "", 24, Typeface.NORMAL, 0xB3FFFFFF);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint);
        code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        code.setImeOptions(EditorInfo.IME_ACTION_DONE);
        code.setGravity(Gravity.CENTER);
        code.setTextColor(Color.WHITE);
        code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 56 * Ui.unit(this));
        code.setLetterSpacing(0.4f);
        code.setBackground(Ui.framed(this, 0x26FFFFFF, 20));
        code.setPadding(Ui.px(this, 30), Ui.px(this, 14), Ui.px(this, 30), Ui.px(this, 14));
        code.setOnEditorActionListener((v, a, e) -> {
            submit();
            return true;
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(this, 460), -2);
        cl.topMargin = Ui.px(this, 34);
        cl.bottomMargin = Ui.px(this, 24);
        page.addView(code, cl);
        TextView ok = Ui.button(this, getString(R.string.lock_ok), true);
        ok.setOnClickListener(v -> submit());
        page.addView(ok, new LinearLayout.LayoutParams(Ui.px(this, 300), -2));
        setContentView(page);
        reset();
        code.requestFocus();
        code.postDelayed(() -> ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(code, InputMethodManager.SHOW_IMPLICIT), 250);
    }

    private boolean hasCode() {
        return prefs.contains("hash");
    }

    private void reset() {
        first = null;
        code.setText("");
        title.setText(hasCode() ? R.string.lock_title : R.string.lock_new);
        hint.setText(hasCode() ? R.string.lock_hint : R.string.lock_new_hint);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            submit();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void submit() {
        String c = code.getText().toString().trim();
        code.setText("");
        if (!hasCode()) {
            if (c.length() < MIN) {
                hint.setText(getString(R.string.lock_short, MIN));
            } else if (first == null) {
                first = c;
                title.setText(R.string.lock_again);
            } else if (first.equals(c)) {
                byte[] salt = new byte[16];
                new SecureRandom().nextBytes(salt);
                String s = hex(salt);
                prefs.edit().putString("salt", s).putString("hash", hash(s, c)).apply();
                open();
            } else {
                reset();
                hint.setText(R.string.lock_mismatch);
            }
            return;
        }
        long until = prefs.getLong("until", 0);
        if (SystemClock.elapsedRealtime() < until && until - SystemClock.elapsedRealtime() < 120_000) {
            hint.setText(R.string.lock_wait);
            return;
        }
        if (hash(prefs.getString("salt", ""), c).equals(prefs.getString("hash", ""))) {
            prefs.edit().putInt("fails", 0).apply();
            open();
        } else {
            int fails = prefs.getInt("fails", 0) + 1;
            SharedPreferences.Editor e = prefs.edit().putInt("fails", fails);
            if (fails >= 5) e.putInt("fails", 0).putLong("until", SystemClock.elapsedRealtime() + 60_000);
            e.apply();
            hint.setText(fails >= 5 ? R.string.lock_wait : R.string.lock_wrong);
        }
    }

    private void open() {
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(code.getWindowToken(), 0);
        startActivity(new Intent(this, SpaceActivity.class).putExtra("unlocked", SpaceActivity.ticket(this)));
        finish();
    }

    static String hash(String salt, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((salt + ":" + code).getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 20_000; i++) h = md.digest(h);
            return hex(h);
        } catch (Exception e) {
            return "";
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    /** Settings > change the code: forgets it, so the next opening asks for a new one. */
    static void forget(Context c) {
        c.getSharedPreferences("lock", MODE_PRIVATE).edit().remove("hash").remove("salt").apply();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();
    }

    View codeView() {
        return code;
    }
}
