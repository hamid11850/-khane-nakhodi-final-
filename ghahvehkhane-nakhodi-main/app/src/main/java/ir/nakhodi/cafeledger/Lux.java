package ir.nakhodi.cafeledger;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Thin helper used by the extra screens (pairing / sync settings).
 * It is a wrapper around the app design system {@link Ui}, so these screens follow
 * the same light/dark theme as the rest of the app. Colours: use {@code ui.u.text}, {@code ui.u.muted}, ...
 */
final class Lux {
    final Activity a;
    final Ui u;

    Lux(Activity a) {
        this.a = a;
        this.u = new Ui(a);
    }

    int dp(float v) { return u.dp(v); }

    TextView text(String s, float size, int color, boolean bold) {
        TextView t = u.t(s, size, color, bold);
        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        return t;
    }

    /** Labels starting with "＋" get the gold primary style, all others the outlined style. */
    Button btn(String s, Runnable r) {
        return s.trim().startsWith("＋") ? u.primaryBtn(s, r) : u.ghostBtn(s, r);
    }

    EditText edit(String hint) {
        EditText e = u.edit(hint);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(6));
        e.setLayoutParams(p);
        return e;
    }

    LinearLayout card() {
        LinearLayout c = u.card();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(8));
        c.setLayoutParams(p);
        return c;
    }

    void add(LinearLayout body, View v) {
        u.add(body, v, 8);
    }

    /** Builds a full screen (top bar + scrolling body) in the current theme and returns the body. */
    LinearLayout page(String title, String subtitle) {
        u.chrome();
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(u.bg);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        top.setPadding(dp(18), dp(14), dp(16), dp(18));
        top.setBackground(u.headerBg());
        top.setElevation(dp(5));

        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView eb = u.t("چایخانه نخودی", 10.5f, u.headAccent, true);
        eb.setLetterSpacing(0.1f);
        eb.setPadding(0, 0, 0, dp(3));
        names.addView(eb);
        names.addView(u.t(title, 18f, u.headInk, true));
        if (subtitle != null) names.addView(u.t(subtitle, 11f, u.headSoft, false));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView back = u.t("بازگشت", 13f, u.headAccent, true);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> a.finish());
        top.addView(back);
        root.addView(top);

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        a.setContentView(root);
        return body;
    }
}
