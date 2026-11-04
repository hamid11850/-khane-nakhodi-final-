package ir.nakhodi.cafeledger;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Central design system for the Nakhodi café app: palette (light + dark), cards, rows,
 * buttons, chips, tiles and window chrome. Every screen is built from these primitives so
 * the whole app keeps one calm, modern, RTL-first look.
 */
final class Ui {

    static final String PREFS = "cafe_ui";
    static final String KEY_THEME = "theme";
    static final String LIGHT = "light";
    static final String DARK = "dark";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isDark(Context c) {
        return DARK.equals(prefs(c).getString(KEY_THEME, LIGHT));
    }

    static void toggle(Context c) {
        prefs(c).edit().putString(KEY_THEME, isDark(c) ? LIGHT : DARK).apply();
    }

    final Activity a;
    final boolean dark;

    final int bg, surface, surfaceAlt, text, muted, primary, primaryInk, onGold,
            green, greenSoft, danger, dangerSoft, hairline, goldSoft,
            hero1, hero2, heroInk, heroSoft,
            headBg1, headBg2, headInk, headSoft, headAccent;

    Ui(Activity a) {
        this.a = a;
        this.dark = isDark(a);
        // "Warm café" palette: espresso browns, cream paper, terracotta accent
        if (dark) {
            bg = 0xFF1B120E;
            surface = 0xFF271A14;
            surfaceAlt = 0xFF2F2018;
            text = 0xFFF6E9D8;
            muted = 0xFFB89B86;
            primary = 0xFFE0875A;
            primaryInk = 0xFFF0A878;
            onGold = 0xFF2B1308;
            green = 0xFF6FCDA0;
            greenSoft = 0xFF1D3A2D;
            danger = 0xFFEE9A86;
            dangerSoft = 0xFF3C2119;
            hairline = 0xFF3A2A20;
            goldSoft = 0xFF3A2519;
            hero1 = 0xFF3B2418;
            hero2 = 0xFF5A3322;
            heroInk = 0xFFFBEBD6;
            heroSoft = 0xFFD7B497;
            headBg1 = 0xFF120B08;
            headBg2 = 0xFF1E130E;
        } else {
            bg = 0xFFF4EADB;
            surface = 0xFFFBF3E6;
            surfaceAlt = 0xFFF8EDDC;
            text = 0xFF2B1B14;
            muted = 0xFF7D5D49;
            primary = 0xFFC8643B;
            primaryInk = 0xFFA9481F;
            onGold = 0xFFFFF8EF;
            green = 0xFF2F7D5B;
            greenSoft = 0xFFE0F0E5;
            danger = 0xFFB3342A;
            dangerSoft = 0xFFFBE4DC;
            hairline = 0xFFE7D6BF;
            goldSoft = 0xFFF6DEC6;
            hero1 = 0xFFFDEBD7;
            hero2 = 0xFFF4C9A4;
            heroInk = 0xFF3A1F12;
            heroSoft = 0xFF7A4B31;
            headBg1 = 0xFF2B1B14;
            headBg2 = 0xFF3A261C;
        }
        headInk = 0xFFFBF3E6;
        headSoft = 0xFFD9BFA8;
        headAccent = 0xFFE8B98E;
    }

    int dp(float v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    int ripple() {
        return dark ? 0x2EFFFFFF : 0x16000000;
    }

    /** Status/navigation bar colours follow the active theme (the header is always dark espresso). */
    void chrome() {
        Window w = a.getWindow();
        boolean edgeToEdge = Build.VERSION.SDK_INT >= 35;   // status bar colour is ignored there, page background shows
        w.setStatusBarColor(edgeToEdge ? bg : headBg1);
        w.setNavigationBarColor(surface);
        View d = w.getDecorView();
        int f = d.getSystemUiVisibility();
        boolean darkStatusIcons = edgeToEdge && !dark;
        if (darkStatusIcons) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            if (dark) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        d.setSystemUiVisibility(f);
    }

    /** Espresso header with rounded bottom corners. */
    GradientDrawable headerBg() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{headBg1, headBg2});
        float r = dp(30);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        return g;
    }

    // ------------------------------------------------------------------ text

    TextView t(String s, float size, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        v.setLineSpacing(0, 1.18f);
        v.setIncludeFontPadding(false);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    TextView eyebrow(String s) {
        TextView v = t(s, 10f, primaryInk, true);
        v.setLetterSpacing(0.14f);
        v.setPadding(0, 0, 0, dp(4));
        return v;
    }

    TextView chip(String s, int fg, int fill) {
        TextView v = t(s, 11.5f, fg, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(11), dp(6), dp(11), dp(6));
        v.setBackground(round(fill, 30));
        return v;
    }

    // ------------------------------------------------------------------ shapes

    GradientDrawable round(int fill, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        return g;
    }

    GradientDrawable round(int fill, float radius, int stroke) {
        GradientDrawable g = round(fill, radius);
        g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    GradientDrawable circle(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    GradientDrawable grad(int from, int to, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(dp(radius));
        return g;
    }

    GradientDrawable gold(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xFFF2A878, 0xFFD97848} : new int[]{0xFFD2693F, 0xFFB9532B});
        g.setCornerRadius(dp(radius));
        return g;
    }

    GradientDrawable heroBg() {
        GradientDrawable g = grad(hero1, hero2, 24);
        g.setStroke(Math.max(1, dp(1)), dark ? 0x44E0875A : 0x66C8643B);
        return g;
    }

    View hairline() {
        View v = new View(a);
        v.setBackgroundColor(hairline);
        return v;
    }

    // ------------------------------------------------------------------ containers

    LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(15), dp(16), dp(15));
        c.setBackground(round(surface, 20, hairline));
        c.setElevation(dp(2));
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return c;
    }

    LinearLayout cardSoft() {
        LinearLayout c = card();
        c.setBackground(round(surfaceAlt, 20, hairline));
        c.setElevation(0);
        return c;
    }

    LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    void add(LinearLayout parent, View v) {
        add(parent, v, 10);
    }

    void add(LinearLayout parent, View v, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(topDp), 0, 0);
        parent.addView(v, p);
    }

    // ------------------------------------------------------------------ buttons

    Button primaryBtn(String label, Runnable r) {
        Button b = base(label);
        b.setTextColor(onGold);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), gold(15), null));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    Button ghostBtn(String label, Runnable r) {
        Button b = base(label);
        b.setTextColor(text);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple()), round(surface, 15, hairline), null));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    Button softBtn(String label, Runnable r) {
        Button b = base(label);
        b.setTextColor(primaryInk);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple()), round(goldSoft, 15), null));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private Button base(String label) {
        Button b = new Button(a);
        b.setText(label);
        b.setTextSize(13.5f);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinHeight(dp(50));
        b.setMinimumHeight(dp(50));
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        b.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return b;
    }

    EditText edit(String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setHintTextColor(muted);
        e.setTextColor(text);
        e.setSingleLine(true);
        e.setTextSize(14.5f);
        e.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        e.setPadding(dp(15), dp(12), dp(15), dp(12));
        e.setBackground(round(surface, 15, hairline));
        return e;
    }

    // ------------------------------------------------------------------ rows & tiles

    /** A list row: icon badge, title/subtitle, trailing chevron. */
    LinearLayout row(String icon, String title, String sub, Runnable act) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple()),
                round(surface, 16, hairline), null));

        TextView ic = t(icon, 15f, primaryInk, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(circle(goldSoft));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(38), dp(38));
        ip.setMarginEnd(dp(12));
        r.addView(ic, ip);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(t(title, 14.5f, text, true));
        if (sub != null && sub.length() > 0) col.addView(t(sub, 11.5f, muted, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = t("\u2039", 17f, muted, false);
        arrow.setGravity(Gravity.CENTER);
        r.addView(arrow);

        if (act != null) r.setOnClickListener(v -> act.run());
        return r;
    }

    /** A quick-action tile used in the 2x2 dashboard grid. */
    LinearLayout tile(String icon, String title, String sub, Runnable act) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        c.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple()),
                round(surface, 18, hairline), null));
        c.setElevation(dp(1));

        TextView ic = t(icon, 16f, primaryInk, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(circle(goldSoft));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(38), dp(38));
        ip.setMargins(0, 0, 0, dp(10));
        c.addView(ic, ip);

        c.addView(t(title, 14f, text, true));
        TextView s = t(sub, 11f, muted, false);
        s.setMaxLines(2);
        c.addView(s);
        if (act != null) c.setOnClickListener(v -> act.run());
        return c;
    }

    /** Small section title with an optional trailing action label. */
    LinearLayout sectionHeader(String title, String actionLabel, Runnable act) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setPadding(dp(4), dp(18), dp(4), dp(4));
        TextView t1 = t(title, 13f, primaryInk, true);
        t1.setLetterSpacing(0.04f);
        r.addView(t1, new LinearLayout.LayoutParams(0, -2, 1f));
        if (actionLabel != null) {
            TextView t2 = t(actionLabel, 11.5f, muted, true);
            t2.setOnClickListener(v -> act.run());
            r.addView(t2);
        }
        return r;
    }

    // ------------------------------------------------------------------ standalone page (pairing / sync screens)

    /** یک صفحه کامل در همین تم می‌سازد و بدنهٔ اسکرول‌شونده را برمی‌گرداند. */
    LinearLayout page(String title, String subtitle) {
        chrome();
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        top.setPadding(dp(18), dp(14), dp(16), dp(18));
        top.setBackground(headerBg());
        top.setElevation(dp(5));

        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView eb = t("چایخانه نخودی", 10.5f, headAccent, true);
        eb.setLetterSpacing(0.1f);
        eb.setPadding(0, 0, 0, dp(3));
        names.addView(eb);
        names.addView(t(title, 18f, headInk, true));
        if (subtitle != null) names.addView(t(subtitle, 11f, headSoft, false));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView back = t("بازگشت", 13f, headAccent, true);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> a.finish());
        top.addView(back);
        root.addView(top);

        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        body.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        a.setContentView(root);
        return body;
    }

    /** دکمه‌های با «＋» طلایی، بقیه خط‌کشی. */
    Button btn(String label, Runnable r) {
        return label.trim().startsWith("＋") ? primaryBtn(label, r) : ghostBtn(label, r);
    }
}
