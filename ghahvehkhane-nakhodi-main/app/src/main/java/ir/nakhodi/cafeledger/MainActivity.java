package ir.nakhodi.cafeledger;

import android.app.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

import ir.nakhodi.cafeledger.db.AppDb;
import ir.nakhodi.cafeledger.db.BalanceRow;
import ir.nakhodi.cafeledger.db.Customer;
import ir.nakhodi.cafeledger.db.Product;
import ir.nakhodi.cafeledger.db.Txn;

/**
 * صفحه اصلی برنامه. تمام داده‌ها روی همین گوشی در دیتابیس Room ذخیره می‌شوند و
 * همگام‌سازی فقط از طریق اتصال محلی Wi-Fi (بدون اینترنت) انجام می‌شود.
 */
public class MainActivity extends Activity {
    AppDb db;
    Ui ui;
    LinearLayout root, body;
    TextView clockTime, clockDate;
    final Handler clockH = new Handler(Looper.getMainLooper());
    final Runnable clockTick = new Runnable() { public void run() { updateClock(); refreshSyncCard(); clockH.postDelayed(this, 20000); } };
    static final int REQ_EXPORT = 41, REQ_IMPORT = 42, REQ_CONTACT = 43;
    EditText customerNameField, customerPhoneField;
    boolean onHome = false, resumedOnce = false;

    // ------------------------------------------------------------------ helpers
    String fa(String s) { char[] c = s.toCharArray(); for (int i = 0; i < c.length; i++) if (c[i] >= '0' && c[i] <= '9') c[i] = (char) ('۰' + (c[i] - '0')); return new String(c); }
    int[] jalali(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = gm > 2 ? gy + 1 : gy;
        int days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1];
        int jy = -1595 + 33 * (days / 12053); days %= 12053;
        jy += 4 * (days / 1461); days %= 1461;
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365; }
        int jm = days < 186 ? 1 + days / 31 : 7 + (days - 186) / 30;
        int jd = 1 + (days < 186 ? days % 31 : (days - 186) % 30);
        return new int[]{jy, jm, jd};
    }
    void updateClock() {
        if (clockTime == null || clockDate == null) return;
        Calendar c = Calendar.getInstance();
        int[] j = jalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        String[] wd = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه"};
        String[] mn = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        clockTime.setText(fa(String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))));
        clockDate.setText(fa(wd[c.get(Calendar.DAY_OF_WEEK) % 7] + " " + j[2] + " " + mn[j[1] - 1]));
    }
    int dp(float v) { return ui != null ? ui.dp(v) : Math.round(v * getResources().getDisplayMetrics().density); }
    String money(long n) { return String.format(Locale.US, "%,d", n) + " تومان"; }
    String date() { return Repo.stamp(); }

    /** Shows a stored date (Gregorian, any digit style) as a Jalali date with Persian digits. */
    String shownDate(String created) {
        if (created == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char ch : created.toCharArray()) {
            if (ch >= '۰' && ch <= '۹') sb.append((char) ('0' + (ch - '۰')));
            else if (ch >= '٠' && ch <= '٩') sb.append((char) ('0' + (ch - '٠')));
            else sb.append(ch);
        }
        String raw = sb.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d{4})/(\\d{1,2})/(\\d{1,2})(.*)$").matcher(raw);
        if (!m.matches()) return fa(raw);
        int y = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), dd = Integer.parseInt(m.group(3));
        if (y >= 1700) {
            int[] j = jalali(y, mo, dd);
            y = j[0];
            mo = j[1];
            dd = j[2];
        }
        String rest = m.group(4).trim();
        return fa(String.format(Locale.US, "%04d/%02d/%02d", y, mo, dd) + (rest.isEmpty() ? "" : "  " + rest));
    }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    AlertDialog.Builder dlg() { return new AlertDialog.Builder(this, Ui.isDark(this) ? R.style.LuxDialogDark : R.style.LuxDialog); }
    LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setPadding(dp(18), dp(6), dp(18), dp(2));
        return l;
    }
    EditText edit(String hint) {
        EditText e = ui.edit(hint);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(6));
        e.setLayoutParams(p);
        return e;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        db = AppDb.get(this);
        home();
        clockH.post(clockTick);
    }
    @Override protected void onDestroy() { clockH.removeCallbacks(clockTick); super.onDestroy(); }
    @Override protected void onResume() {
        super.onResume();
        if (onHome && resumedOnce) home();
        resumedOnce = true;
        SyncManager sm = SyncManager.get(this);
        sm.setListener(this::onSyncEvent);
        sm.start();
        if (sm.isPaired() && !sm.isManager()) sm.syncNow(null);   // شاگرد با باز شدن برنامه خودکار همگام می‌شود
        if (Pro.isUnlocked(this)) BazaarBilling.get(this).refreshOwned(null);   // quietly re-verify the purchase
    }
    @Override protected void onPause() { SyncManager.get(this).setListener(null); super.onPause(); }

    // ------------------------------------------------------------------ shell
    /** Builds the app shell: top bar, scrolling body, bottom navigation. */
    void base(String title, String subtitle, int tab) {
        onHome = (tab == 0);
        ui = new Ui(this);
        ui.chrome();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(ui.bg);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(tab == 0 ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        top.setPadding(dp(18), dp(14), dp(16), dp(tab == 0 ? 60 : 18));
        top.setBackground(ui.headerBg());

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView eb = ui.t("چایخانه نخودی", 10.5f, ui.headAccent, true);
        eb.setLetterSpacing(0.1f);
        eb.setPadding(0, 0, 0, dp(3));
        names.addView(eb);
        names.addView(ui.t(title, tab == 0 ? 21f : 18f, ui.headInk, true));
        if (subtitle != null) names.addView(ui.t(subtitle, 11f, ui.headSoft, false));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.LEFT);
        clockTime = ui.t("", 14f, ui.headAccent, true);
        clockTime.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        clockDate = ui.t("", 10f, ui.headSoft, false);
        clockDate.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        clockBox.addView(clockTime);
        clockBox.addView(clockDate);
        LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(-2, -2);
        cbp.setMarginEnd(dp(8));
        top.addView(clockBox, cbp);

        TextView themeBtn = ui.t(Ui.isDark(this) ? "☀" : "☾", 15f, ui.headAccent, true);
        themeBtn.setGravity(Gravity.CENTER);
        themeBtn.setBackground(ui.circle(0x22FFFFFF));
        themeBtn.setOnClickListener(v -> {
            Ui.toggle(this);
            ui = new Ui(this);
            if (onHome) home(); else recreate();
        });
        top.addView(themeBtn, new LinearLayout.LayoutParams(dp(36), dp(36)));
        if (tab == 0) {
            FrameLayout wrap = new FrameLayout(this);
            wrap.addView(top, new FrameLayout.LayoutParams(-1, -2));
            ImageView cup = new ImageView(this);
            cup.setContentDescription("قلیان");
            cup.setImageResource(R.drawable.ic_hookah);
            FrameLayout.LayoutParams cupLp = new FrameLayout.LayoutParams(dp(64), dp(80), Gravity.LEFT | Gravity.BOTTOM);
            cupLp.setMargins(dp(14), 0, 0, dp(8));
            wrap.addView(cup, cupLp);
            wrap.setElevation(dp(5));
            root.addView(wrap);
        } else {
            top.setElevation(dp(5));
            root.addView(top);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        body.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        updateClock();

        root.addView(ui.hairline(), new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(6), dp(8), dp(6), dp(8));
        nav.setBackgroundColor(ui.surface);
        nav.addView(navItem("⌂", "خانه", tab == 0, this::home), new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(navItem("♙", "مشتری‌ها", tab == 1, this::customers), new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(navFab(), new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(navItem("▤", "گزارش", tab == 3, this::reports), new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(navItem("⋯", "بیشتر", false, this::moreSheet), new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(nav);

        setContentView(root);
    }

    LinearLayout navItem(String icon, String label, boolean active, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));
        int color = active ? ui.primaryInk : ui.muted;
        TextView i = ui.t(icon, 18f, color, true);
        i.setGravity(Gravity.CENTER);
        TextView l = ui.t(label, 10f, color, active);
        l.setGravity(Gravity.CENTER);
        item.addView(i);
        item.addView(l);
        if (active) {
            View dot = new View(this);
            dot.setBackground(ui.round(ui.primaryInk, 4));
            LinearLayout.LayoutParams dpp = new LinearLayout.LayoutParams(dp(5), dp(5));
            dpp.setMargins(0, dp(3), 0, 0);
            item.addView(dot, dpp);
        }
        item.setOnClickListener(v -> action.run());
        return item;
    }

    /** Raised gold "+" slot in the middle of the bottom bar. */
    LinearLayout navFab() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setGravity(Gravity.CENTER);
        TextView fab = ui.t("＋", 22f, ui.onGold, true);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), ui.gold(18), null));
        fab.setElevation(dp(4));
        fab.setOnClickListener(v -> newSale());
        wrap.addView(fab, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return wrap;
    }

    AlertDialog moreDialog;

    void moreSheet() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setPadding(dp(14), dp(6), dp(14), dp(10));
        l.setBackgroundColor(ui.bg);

        String syncStatus = SyncManager.get(this).statusLine();
        String sub = syncStatus == null ? "اتصال محلی گوشی مدیر و شاگرد" : syncStatus.replace("\n", "  •  ");

        l.addView(headerRow("▦", "محصولات و قیمت‌ها", "منوی چایخانه و نرخ روز", this::products));
        l.addView(headerRow("⇧", "تهیه فایل پشتیبان", "ذخیره امن اطلاعات روی گوشی", this::exportBackup));
        l.addView(headerRow("⇩", "بازیابی فایل پشتیبان", "برگرداندن اطلاعات از فایل", this::importBackup));
        l.addView(headerRow("⇄", "جفت‌سازی و همگام‌سازی", sub, () -> startActivity(new Intent(this, PairingActivity.class))));
        l.addView(headerRow("★", "نسخه حرفه‌ای", Pro.isUnlocked(this) ? "فعال است ✓" : "ثبت نامحدود حساب  •  " + Pro.PRICE_TEXT, this::proScreen));
        l.addView(headerRow("✈", "ارتباط با سازنده", "تلگرام @" + Contact.TELEGRAM, () -> Contact.openTelegram(this)));
        l.addView(headerRow("☾", "تغییر تم روشن / تیره", Ui.isDark(this) ? "الان: تیره" : "الان: روشن", () -> {
            Ui.toggle(this);
            ui = new Ui(this);
            home();
        }));

        TextView ver = ui.t("نسخه ۳٫۰  •  کاملاً آفلاین  •  همگام‌سازی محلی", 10.5f, ui.muted, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(14), 0, dp(2));
        l.addView(ver);

        ScrollView s = new ScrollView(this);
        s.setBackgroundColor(ui.bg);
        s.addView(l);
        moreDialog = new AlertDialog.Builder(this, Ui.isDark(this) ? R.style.LuxSheetDark : R.style.LuxSheet)
                .setView(s).create();
        moreDialog.show();
    }

    LinearLayout headerRow(String icon, String title, String sub, Runnable act) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setPadding(dp(12), dp(11), dp(12), dp(11));
        r.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(ui.ripple()), ui.round(ui.surface, 16, ui.hairline), null));
        TextView ic = ui.t(icon, 14f, ui.primaryInk, true);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(ui.circle(ui.goldSoft));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
        ip.setMarginEnd(dp(10));
        r.addView(ic, ip);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(ui.t(title, 14f, ui.text, true));
        if (sub != null && !sub.isEmpty()) col.addView(ui.t(sub, 10.5f, ui.muted, false));
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = ui.t("‹", 16f, ui.muted, false);
        arrow.setGravity(Gravity.CENTER);
        r.addView(arrow);
        r.setOnClickListener(v -> {
            if (moreDialog != null) {
                try { moreDialog.dismiss(); } catch (Exception ignored) { }
                moreDialog = null;
            }
            act.run();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(5), 0, 0);
        r.setLayoutParams(p);
        return r;
    }

    // ------------------------------------------------------------------ home
    void home() {
        base("داشبورد", "همه حساب‌ها، یک‌جا و مرتب", 0);

        long sales = db.txns().sumKind("SALE");
        long pays = db.txns().sumKind("PAY");
        long due = sales - pays;
        long today = db.txns().sumOnDay("SALE", new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(new Date()) + "%");
        int custCount = (int) db.customers().countLive();

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(16));
        hero.setBackground(ui.heroBg());
        TextView eb = ui.t("NAKHODI  •  دفتر حساب روزانه", 9.5f, ui.heroSoft, true);
        eb.setLetterSpacing(0.18f);
        hero.addView(eb);
        TextView h1 = ui.t("حسابِ امروز، مرتب و بی‌دردسر", 18f, ui.heroInk, true);
        h1.setPadding(0, dp(4), 0, 0);
        hero.addView(h1);
        TextView h2 = ui.t("سفارش، دریافت وجه و مانده هر مشتری در یک نگاه", 11.5f, ui.heroSoft, false);
        h2.setPadding(0, dp(2), 0, dp(12));
        hero.addView(h2);

        LinearLayout heroRow = new LinearLayout(this);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        heroRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        heroRow.addView(heroStat("فروش امروز", money(today)), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView gap = new TextView(this);
        gap.setWidth(dp(10));
        heroRow.addView(gap);
        heroRow.addView(heroStat("طلب خالص", money(Math.max(0, due))), new LinearLayout.LayoutParams(0, -2, 1f));
        hero.addView(heroRow);

        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.setMargins(0, dp(2), 0, dp(4));
        body.addView(hero, hp);

        addSyncCard();

        body.addView(ui.sectionHeader("میان‌بُرها", null, null));
        LinearLayout g1 = grid();
        g1.addView(ui.tile("＋", "ثبت مشتری", "افزودن مشتری تازه", this::newCustomer), weight());
        g1.addView(ui.tile("☕", "سفارش", "چای، قلیان، املت", this::newSale), weight());
        body.addView(g1);
        LinearLayout g2 = grid();
        g2.addView(ui.tile("↙", "دریافت وجه", "تسویه حساب مشتری", this::newPayment), weight());
        g2.addView(ui.tile("▦", "محصولات", "قیمت‌ها و منو", this::products), weight());
        body.addView(g2);

        body.addView(ui.sectionHeader("دفتر حساب", fa(custCount + "") + " مشتری", this::customers));
        LinearLayout list = ui.card();
        list.setPadding(dp(6), dp(6), dp(6), dp(6));
        list.addView(ui.row("♙", "مشتری‌ها و ریزحساب", "مانده هر نفر و صورت‌حساب", this::customers));
        list.addView(spacer(4));
        list.addView(ui.row("▤", "گزارش فروش و بدهی", "نگاه کلی به حساب چایخانه", this::reports));
        list.addView(spacer(4));
        list.addView(ui.row("⇧", "فایل پشتیبان", "ذخیره و بازیابی اطلاعات", this::exportBackup));
        ui.add(body, list, 6);

        body.addView(ui.sectionHeader("ابزارها", null, null));
        LinearLayout tools = ui.card();
        tools.setPadding(dp(6), dp(6), dp(6), dp(6));
        tools.addView(ui.row("⟳", "همگام‌سازی گوشی‌ها", "جفت‌سازی با QR و اتصال Wi-Fi محلی", () -> startActivity(new Intent(this, PairingActivity.class))));
        tools.addView(spacer(4));
        tools.addView(ui.row("★", "نسخه حرفه‌ای", Pro.isUnlocked(this) ? "فعال است ✓" : "ثبت نامحدود حساب مشتری  •  " + Pro.PRICE_TEXT, this::proScreen));
        tools.addView(spacer(4));
        tools.addView(ui.row("✈", "ارتباط با سازنده", "تلگرام @" + Contact.TELEGRAM, () -> Contact.openTelegram(this)));
        tools.addView(spacer(4));
        tools.addView(ui.row("⋯", "همه امکانات", "پشتیبان، نسخه حرفه‌ای، تم", this::moreSheet));
        ui.add(body, tools, 6);

        LinearLayout note = ui.cardSoft();
        note.addView(ui.t("یادآوری", 12f, ui.primaryInk, true));
        note.addView(ui.t("این برنامه کاملاً آفلاین کار می‌کند. هر چند وقت یک‌بار فایل پشتیبان بگیر تا اطلاعات مشتری‌ها امن بماند.", 11.5f, ui.muted, false));
        ui.add(body, note, 14);

        TextView ver = ui.t("نسخه ۳٫۰  •  کاملاً آفلاین  •  دیتابیس Room", 10.5f, ui.muted, false);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(10), 0, 0);
        body.addView(ver);
    }

    LinearLayout grid() {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.HORIZONTAL);
        g.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, 0);
        g.setLayoutParams(p);
        return g;
    }

    LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(h)));
        return v;
    }

    LinearLayout heroStat(String label, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.setBackground(ui.round(Ui.isDark(this) ? 0x22000000 : 0x55FFFFFF, 14, Ui.isDark(this) ? 0x33E0875A : 0x55C8643B));
        c.addView(ui.t(label, 10f, ui.heroSoft, false));
        c.addView(ui.t(value, 13.5f, ui.heroInk, true));
        return c;
    }

    // ------------------------------------------------------------------ customers
    long balance(long id) { return Repo.balance(db, id); }

    /** id|name pairs for the customer pickers. */
    ArrayList<String> customerNames() {
        ArrayList<String> a = new ArrayList<>();
        for (Customer c : db.customers().live()) a.add(c.id + "|" + c.name);
        return a;
    }

    void customers() {
        base("مشتری‌ها", "دفتر حساب مشتریان چایخانه", 1);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        top.addView(ui.primaryBtn("＋  مشتری جدید", this::newCustomer), weight());
        top.addView(ui.ghostBtn("⇄  سفارش", this::newSale), weight());
        ui.add(body, top, 2);

        List<Customer> list = db.customers().live();
        if (list.isEmpty()) {
            emptyState("♙", "هنوز مشتری ثبت نشده", "اولین مشتری را اضافه کن تا دفتر حسابت آماده شود.", this::newCustomer, "＋  افزودن اولین مشتری");
        } else {
            int count = 0;
            for (Customer c : list) {
                final long id = c.id;
                final String name = c.name == null ? "" : c.name;
                String phone = c.phone;
                long bal = balance(id);
                LinearLayout card = ui.card();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView av = ui.t(name.trim().isEmpty() ? "؟" : name.trim().substring(0, 1), 15f, ui.primaryInk, true);
                av.setGravity(Gravity.CENTER);
                av.setBackground(ui.circle(ui.goldSoft));
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(42), dp(42));
                ap.setMarginEnd(dp(11));
                row.addView(av, ap);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(ui.t(name, 15f, ui.text, true));
                boolean noPhone = phone == null || phone.trim().isEmpty();
                TextView phoneView = ui.t(noPhone ? "شماره تماس ثبت نشده" : phone, 11f, ui.muted, false);
                if (!noPhone) phoneView.setTextDirection(View.TEXT_DIRECTION_LTR);
                info.addView(phoneView);
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(row);

                LinearLayout foot = new LinearLayout(this);
                foot.setOrientation(LinearLayout.HORIZONTAL);
                foot.setGravity(Gravity.CENTER_VERTICAL);
                foot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView amt = ui.t(money(bal), 15f, bal > 0 ? ui.danger : ui.green, true);
                foot.addView(amt, new LinearLayout.LayoutParams(0, -2, 1f));
                foot.addView(ui.chip(bal > 0 ? "بدهکار" : "تسویه", bal > 0 ? ui.danger : ui.green, bal > 0 ? ui.dangerSoft : ui.greenSoft));
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
                fp.setMargins(0, dp(12), 0, 0);
                card.addView(foot, fp);

                LinearLayout acts = new LinearLayout(this);
                acts.setOrientation(LinearLayout.HORIZONTAL);
                acts.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                Button l = ui.softBtn("ریزحساب", () -> ledger(id, name));
                Button r = ui.ghostBtn("عملیات", () -> customerMenu(id, name));
                acts.addView(l, weight());
                acts.addView(r, weight());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(11), 0, 0);
                card.addView(acts, lp);
                card.setOnClickListener(v -> customerMenu(id, name));

                ui.add(body, card, 8);
                count++;
            }
            TextView sum = ui.t(fa("" + count) + " مشتری در دفتر", 11f, ui.muted, false);
            sum.setGravity(Gravity.CENTER);
            sum.setPadding(0, dp(12), 0, 0);
            body.addView(sum);
        }
    }

    void emptyState(String icon, String title, String sub, Runnable act, String label) {
        LinearLayout card = ui.card();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(20), dp(26), dp(20), dp(22));
        TextView i = ui.t(icon, 28f, ui.primaryInk, true);
        i.setGravity(Gravity.CENTER);
        i.setBackground(ui.circle(ui.goldSoft));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(58), dp(58));
        ip.setMargins(0, 0, 0, dp(10));
        card.addView(i, ip);
        TextView t1 = ui.t(title, 15f, ui.text, true);
        t1.setGravity(Gravity.CENTER);
        card.addView(t1);
        TextView t2 = ui.t(sub, 11.5f, ui.muted, false);
        t2.setGravity(Gravity.CENTER);
        t2.setPadding(0, dp(4), 0, 0);
        card.addView(t2);
        if (act != null) ui.add(card, ui.primaryBtn(label, act), 14);
        ui.add(body, card, 10);
    }

    void customerMenu(final long id, String name) {
        String[] opts = {"ثبت سفارش برای " + name, "ثبت دریافت وجه", "مشاهده ریزحساب", "ویرایش نام و تماس", "حذف مشتری"};
        dlg().setTitle(name + "  •  " + money(balance(id))).setItems(opts, (d, w) -> {
            if (w == 0) newSaleFor(id);
            if (w == 1) paymentFor(id);
            if (w == 2) ledger(id, name);
            if (w == 3) editCustomer(id, name);
            if (w == 4) confirmDeleteCustomer(id, name);
        }).show();
    }

    void confirmDeleteCustomer(final long id, final String name) {
        long txCount = Repo.txCount(db, id);
        long bal = balance(id);
        String msg = "«" + name + "» و " + fa("" + txCount) + " تراکنش او از دفتر حذف می‌شود."
                + (bal != 0 ? "\n\nتوجه: مانده‌ی حساب این مشتری " + money(bal) + " است و با حذف از بین می‌رود." : "")
                + "\n\nاین کار قابل بازگشت نیست و در همگام‌سازی بعدی روی گوشی دیگر هم اعمال می‌شود. ادامه می‌دهید؟";
        dlg().setTitle("حذف مشتری").setMessage(msg)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف مشتری", (d, w) -> {
                    Repo.deleteCustomer(db, id);
                    toast("«" + name + "» حذف شد");
                    customers();
                }).show();
    }

    void newCustomer() {
        if (!Pro.isUnlocked(this) && db.customers().countLive() >= Pro.FREE_CUSTOMERS) {
            showPaywall();
            return;
        }
        LinearLayout l = form();
        EditText name = edit("نام مشتری (ضروری)"), phone = edit("شماره تماس (اختیاری)"), note = edit("توضیحات (اختیاری)");
        customerNameField = name; customerPhoneField = phone;
        l.addView(name); l.addView(phone);
        l.addView(ui.ghostBtn("👥  انتخاب از مخاطبین گوشی", this::pickContact));
        l.addView(note);
        dlg().setTitle("مشتری جدید").setView(l).setNegativeButton("انصراف", null).setPositiveButton("ثبت", (d, w) -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) { toast("نام مشتری را وارد کنید"); return; }
            Repo.addCustomer(db, n, phone.getText().toString().trim(), note.getText().toString().trim());
            toast("مشتری ثبت شد");
            customers();
        }).show();
    }

    void editCustomer(final long id, String old) {
        LinearLayout l = form();
        EditText e = edit("نام جدید"); e.setText(old);
        l.addView(e);
        dlg().setTitle("ویرایش مشتری").setView(l).setNegativeButton("انصراف", null).setPositiveButton("ذخیره", (d, w) -> {
            String n = e.getText().toString().trim();
            if (n.isEmpty()) { toast("نام مشتری را وارد کنید"); return; }
            Repo.renameCustomer(db, id, n);
            customers();
        }).show();
    }

    void pickContact() {
        try { startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ_CONTACT); }
        catch (Exception e) { toast("انتخاب مخاطب در این گوشی در دسترس نیست"); }
    }

    void ledger(final long id, String name) {
        base("ریزحساب", name, 1);
        LinearLayout bal = ui.card();
        bal.setBackground(ui.grad(ui.hero1, ui.hero2, 20));
        bal.addView(ui.t("مانده فعلی حساب", 11.5f, ui.muted, false));
        bal.addView(ui.t(money(balance(id)), 22f, ui.heroInk, true));
        ui.add(body, bal, 2);

        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        acts.addView(ui.primaryBtn("＋  سفارش", () -> newSaleFor(id)), weight());
        acts.addView(ui.ghostBtn("↙  دریافت وجه", () -> paymentFor(id)), weight());
        ui.add(body, acts, 10);

        body.addView(ui.sectionHeader("گردش حساب", null, null));
        body.addView(ui.t("با ✕ کنار هر ردیف می‌توانید کل تراکنش یا فقط چند عدد از آن را حذف کنید.", 10.5f, ui.muted, false));

        Customer c = db.customers().byId(id);
        List<Txn> rows = c == null || c.uid == null ? new ArrayList<>() : db.txns().ofCustomer(c.uid);
        if (rows.isEmpty()) {
            emptyState("▤", "هنوز تراکنشی نیست", "با ثبت سفارش یا دریافت وجه، گردش حساب اینجا نمایش داده می‌شود.", () -> newSaleFor(id), "＋  ثبت سفارش");
        } else {
            for (Txn t : rows) {
                boolean sale = "SALE".equals(t.kind);
                final long txId = t.id;
                final String txDesc = t.description == null ? "تراکنش" : t.description;
                final long txAmount = t.amount;
                LinearLayout card = ui.card();
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

                TextView mark = ui.t(sale ? "−" : "＋", 17f, sale ? ui.danger : ui.green, true);
                mark.setGravity(Gravity.CENTER);
                mark.setBackground(ui.circle(sale ? ui.dangerSoft : ui.greenSoft));
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(36), dp(36));
                mp.setMarginEnd(dp(11));
                row.addView(mark, mp);

                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(ui.t(txDesc, 13.5f, ui.text, true));
                info.addView(ui.t(shownDate(t.created), 10f, ui.muted, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(ui.t((sale ? "−" : "+") + money(txAmount), 12.5f, sale ? ui.danger : ui.green, true));
                TextView del = ui.t("✕", 15f, ui.danger, true);
                del.setGravity(Gravity.CENTER);
                del.setOnClickListener(v -> confirmDeleteTx(txId, txDesc, txAmount, sale, id, name));
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(40), dp(40));
                dlp.setMarginStart(dp(6));
                row.addView(del, dlp);
                card.addView(row);
                ui.add(body, card, 6);
            }
        }
    }

    void confirmDeleteTx(final long txId, final String desc, final long amount, final boolean sale, final long customerId, final String customerName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(.+) × (\\d+)$").matcher(desc);
        if (sale && m.matches() && Integer.parseInt(m.group(2)) > 1) {
            final String pname = m.group(1);
            final int q = Integer.parseInt(m.group(2));
            LinearLayout l = form();
            l.addView(ui.t(desc + "  •  " + money(amount), 13f, ui.muted, false));
            final EditText cnt = edit("چند عدد حذف شود؟ (از " + q + " عدد)");
            cnt.setInputType(2);
            cnt.setText("1");
            l.addView(cnt);
            dlg().setTitle("حذف از سفارش")
                    .setView(l)
                    .setNegativeButton("انصراف", null)
                    .setNeutralButton("حذف کل تراکنش", (d, w) -> deleteTx(txId, customerId, customerName))
                    .setPositiveButton("حذف این تعداد", (d, w) -> {
                        try {
                            int k = Integer.parseInt(cnt.getText().toString().trim());
                            if (k < 1) throw new Exception();
                            if (k >= q) { deleteTx(txId, customerId, customerName); return; }
                            int left = q - k;
                            Repo.reduceTx(db, txId, pname, left, amount * left / q);
                            toast(k + " عدد حذف شد؛ مانده حساب: " + money(balance(customerId)));
                            ledger(customerId, customerName);
                        } catch (Exception ex) { toast("تعداد معتبر وارد کنید"); }
                    }).show();
            return;
        }
        dlg().setTitle("حذف تراکنش")
                .setMessage(desc + "  •  " + money(amount) + "\n\nاین تراکنش از حساب مشتری حذف می‌شود و قابل بازگشت نیست. ادامه می‌دهید؟")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف", (d, w) -> deleteTx(txId, customerId, customerName)).show();
    }

    void deleteTx(long txId, long customerId, String customerName) {
        Repo.deleteTx(db, txId);
        toast("تراکنش حذف شد؛ مانده حساب: " + money(balance(customerId)));
        ledger(customerId, customerName);
    }

    // ------------------------------------------------------------------ sales / payments
    interface Pick { void go(long id); }

    void chooseCustomer(String title, ArrayList<String> a, Pick p) {
        String[] labels = new String[a.size()];
        for (int i = 0; i < a.size(); i++) labels[i] = a.get(i).split("\\|", 2)[1];
        dlg().setTitle(title).setItems(labels, (d, w) -> p.go(Long.parseLong(a.get(w).split("\\|", 2)[0]))).show();
    }

    void newSale() {
        ArrayList<String> a = customerNames();
        if (a.isEmpty()) { toast("اول مشتری ثبت کنید"); newCustomer(); return; }
        chooseCustomer("انتخاب مشتری", a, this::newSaleFor);
    }

    void newSaleFor(long customerId) { orderScreen(customerId); }

    /** One screen for a whole order: pick any number of products with quantities, then save them together. */
    void orderScreen(final long cid) {
        List<Product> menu = db.products().live();
        final ArrayList<String> pn = new ArrayList<>();
        final ArrayList<Long> pp = new ArrayList<>();
        for (Product p : menu) { pn.add(p.name); pp.add(p.price); }
        if (pn.isEmpty()) { toast("اول محصول و قیمت ثبت کنید"); products(); return; }
        Customer c = db.customers().byId(cid);
        final String customerName = c == null || c.name == null ? "" : c.name;
        final int n = pn.size();
        final int[] qty = new int[n];
        final long[] price = new long[n];
        for (int i = 0; i < n; i++) price[i] = pp.get(i);
        final TextView[] qv = new TextView[n];
        final TextView[] sub = new TextView[n];
        final TextView totalView = ui.t("", 18f, ui.primaryInk, true);
        final TextView countView = ui.t("", 11f, ui.muted, false);

        base("ثبت سفارش", customerName, 1);

        LinearLayout head = ui.card();
        head.addView(ui.t(customerName, 15f, ui.text, true));
        head.addView(ui.t("مانده فعلی: " + money(balance(cid)), 11.5f, ui.muted, false));
        ui.add(body, head, 2);
        body.addView(ui.t("با دکمه‌های + و − تعداد هر محصول را بزنید؛ همه با هم در یک بار ثبت می‌شوند. با لمس قیمت می‌توانید قیمت همین سفارش را عوض کنید.", 11f, ui.muted, false));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            long total = 0;
            int items = 0;
            for (int i = 0; i < n; i++) {
                qv[i].setText(String.valueOf(qty[i]));
                qv[i].setTextColor(qty[i] > 0 ? ui.primaryInk : ui.muted);
                if (qty[i] > 0) {
                    long lt = (long) qty[i] * price[i];
                    total += lt;
                    items++;
                    sub[i].setText(qty[i] + " × " + money(price[i]) + " = " + money(lt));
                    sub[i].setTextColor(ui.green);
                } else {
                    sub[i].setText("قیمت هر واحد: " + money(price[i]) + "  ✎");
                    sub[i].setTextColor(ui.muted);
                }
            }
            totalView.setText(money(total));
            countView.setText(items == 0 ? "هنوز محصولی انتخاب نشده" : "جمع سفارش  •  " + items + " نوع محصول");
        };

        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String name = pn.get(i);
            LinearLayout card = ui.card();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            TextView ic = ui.t(name.contains("چای") ? "☕" : name.contains("قلیان") ? "♨" : "✦", 17f, ui.primaryInk, true);
            ic.setGravity(Gravity.CENTER);
            ic.setBackground(ui.circle(ui.goldSoft));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
            ip.setMarginEnd(dp(10));
            row.addView(ic, ip);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(ui.t(name, 14.5f, ui.text, true));
            sub[i] = ui.t("", 10.5f, ui.muted, false);
            sub[i].setOnClickListener(v -> {
                LinearLayout l = form();
                EditText pe = edit("قیمت واحد برای این سفارش (تومان)");
                pe.setInputType(2);
                pe.setText("" + price[idx]);
                l.addView(pe);
                dlg().setTitle(name).setView(l).setNegativeButton("انصراف", null).setPositiveButton("تأیید", (d, w) -> {
                    try {
                        long pr = Long.parseLong(pe.getText().toString().trim());
                        if (pr < 0) throw new Exception();
                        price[idx] = pr;
                        refresh[0].run();
                    } catch (Exception ex) { toast("قیمت معتبر وارد کنید"); }
                }).show();
            });
            info.addView(sub[i]);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            LinearLayout stepper = new LinearLayout(this);
            stepper.setOrientation(LinearLayout.HORIZONTAL);
            stepper.setGravity(Gravity.CENTER_VERTICAL);
            stepper.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            TextView minus = ui.t("−", 20f, ui.primaryInk, true);
            minus.setGravity(Gravity.CENTER);
            minus.setBackground(ui.circle(ui.goldSoft));
            minus.setOnClickListener(v -> { if (qty[idx] > 0) { qty[idx]--; refresh[0].run(); } });
            qv[i] = ui.t("0", 16f, ui.muted, true);
            qv[i].setGravity(Gravity.CENTER);
            qv[i].setOnClickListener(v -> {
                LinearLayout l = form();
                EditText qe = edit("تعداد");
                qe.setInputType(2);
                qe.setText("" + qty[idx]);
                l.addView(qe);
                dlg().setTitle(name).setView(l).setNegativeButton("انصراف", null).setPositiveButton("تأیید", (d, w) -> {
                    try {
                        int q = Integer.parseInt(qe.getText().toString().trim());
                        if (q < 0 || q > 999) throw new Exception();
                        qty[idx] = q;
                        refresh[0].run();
                    } catch (Exception ex) { toast("تعداد بین ۰ تا ۹۹۹ وارد کنید"); }
                }).show();
            });
            TextView plus = ui.t("+", 20f, ui.onGold, true);
            plus.setGravity(Gravity.CENTER);
            plus.setBackground(ui.gold(18));
            plus.setOnClickListener(v -> { if (qty[idx] < 999) { qty[idx]++; refresh[0].run(); } });
            stepper.addView(minus, new LinearLayout.LayoutParams(dp(36), dp(36)));
            stepper.addView(qv[i], new LinearLayout.LayoutParams(dp(40), -2));
            stepper.addView(plus, new LinearLayout.LayoutParams(dp(36), dp(36)));
            row.addView(stepper);
            card.addView(row);
            ui.add(body, card, 8);
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        bar.setBackgroundColor(ui.surface);
        LinearLayout sum = new LinearLayout(this);
        sum.setOrientation(LinearLayout.VERTICAL);
        sum.addView(countView);
        sum.addView(totalView);
        bar.addView(sum, new LinearLayout.LayoutParams(0, -2, 1f));
        Button save = ui.primaryBtn("＋  ثبت در حساب", () -> {
            long total = 0;
            int items = 0;
            for (int i = 0; i < n; i++) if (qty[i] > 0) { total += (long) qty[i] * price[i]; items++; }
            if (items == 0) { toast("حداقل یک محصول را انتخاب کنید"); return; }
            for (int i = 0; i < n; i++) {
                if (qty[i] > 0) Repo.addTx(db, cid, "SALE", pn.get(i) + " × " + qty[i], (long) qty[i] * price[i]);
            }
            toast("سفارش ثبت شد: " + money(total));
            home();
        });
        save.setMinWidth(dp(150));
        bar.addView(save, new LinearLayout.LayoutParams(-2, -2));
        root.addView(ui.hairline(), 2, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));
        root.addView(bar, 3);

        refresh[0].run();
    }

    void newPayment() {
        ArrayList<String> a = customerNames();
        if (a.isEmpty()) { toast("مشتری‌ای وجود ندارد"); return; }
        chooseCustomer("دریافت وجه از مشتری", a, this::paymentFor);
    }

    void paymentFor(final long cid) {
        LinearLayout l = form();
        EditText amount = edit("مبلغ دریافتی (تومان)"); amount.setInputType(2);
        EditText desc = edit("توضیحات (اختیاری)");
        l.addView(ui.t("مانده فعلی: " + money(balance(cid)), 14f, ui.green, true));
        l.addView(amount); l.addView(desc);
        dlg().setTitle("ثبت دریافت وجه").setView(l).setNegativeButton("انصراف", null).setPositiveButton("ثبت", (d, w) -> {
            try {
                long n = Long.parseLong(amount.getText().toString());
                if (n <= 0) throw new Exception();
                String ds = desc.getText().toString().trim();
                Repo.addTx(db, cid, "PAY", ds.isEmpty() ? "دریافت وجه" : ds, n);
                toast("پرداخت ثبت شد");
                home();
            } catch (Exception e) { toast("مبلغ معتبر وارد کنید"); }
        }).show();
    }

    // ------------------------------------------------------------------ products
    void products() {
        base("محصولات", "منوی چایخانه و نرخ‌ها", 2);
        ui.add(body, ui.primaryBtn("＋  افزودن محصول جدید", () -> productDialog(0, "", 0)), 2);
        List<Product> menu = db.products().live();
        if (menu.isEmpty()) {
            emptyState("☕", "منو هنوز خالی است", "چای، قلیان، املت و سایر محصولات را اضافه کن.", () -> productDialog(0, "", 0), "＋  افزودن محصول");
        } else {
            for (Product p : menu) {
                final long id = p.id;
                String name = p.name == null ? "" : p.name;
                long price = p.price;
                LinearLayout card = ui.card();
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                TextView ic = ui.t(name.contains("چای") ? "☕" : name.contains("قلیان") ? "♨" : "✦", 17f, ui.primaryInk, true);
                ic.setGravity(Gravity.CENTER);
                ic.setBackground(ui.circle(ui.goldSoft));
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
                ip.setMarginEnd(dp(11));
                row.addView(ic, ip);
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(ui.t(name, 14.5f, ui.text, true));
                info.addView(ui.t("قیمت هر واحد", 10.5f, ui.muted, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(ui.t(money(price), 13f, ui.green, true));
                card.addView(row);
                Button e = ui.ghostBtn("ویرایش", () -> productDialog(id, name, price));
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
                ep.setMargins(0, dp(10), 0, 0);
                card.addView(e, ep);
                ui.add(body, card, 8);
            }
        }
    }

    void productDialog(final long id, String old, long oldPrice) {
        LinearLayout l = form();
        EditText name = edit("نام محصول"); name.setText(old);
        EditText price = edit("قیمت واحد به تومان"); price.setInputType(2);
        if (id != 0) price.setText("" + oldPrice);
        l.addView(name); l.addView(price);
        dlg().setTitle(id == 0 ? "محصول جدید" : "ویرایش محصول").setView(l).setNegativeButton("انصراف", null).setPositiveButton("ذخیره", (d, w) -> {
            try {
                String n = name.getText().toString().trim();
                long p = Long.parseLong(price.getText().toString());
                if (n.isEmpty() || p < 0) throw new Exception();
                if (id == 0) Repo.addProduct(db, n, p);
                else Repo.editProduct(db, id, n, p);
                products();
            } catch (Exception e) { toast("نام و قیمت معتبر وارد کنید"); }
        }).show();
    }

    // ------------------------------------------------------------------ reports
    void reports() {
        base("گزارش", "خلاصه مالی چایخانه", 3);
        long sales = db.txns().sumKind("SALE");
        long payments = db.txns().sumKind("PAY");
        long balance = sales - payments;
        int custCount = (int) db.customers().countLive();
        long txCount = db.txns().countAll();

        LinearLayout kpi = new LinearLayout(this);
        kpi.setOrientation(LinearLayout.HORIZONTAL);
        kpi.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        kpi.addView(kpiCard("مجموع فروش", money(sales), "↗", false), weight());
        kpi.addView(kpiCard("مجموع دریافت", money(payments), "✓", true), weight());
        ui.add(body, kpi, 2);

        LinearLayout total = ui.card();
        total.setBackground(ui.grad(ui.hero1, ui.hero2, 20));
        total.addView(ui.t("مانده خالص حساب‌ها", 11.5f, ui.muted, false));
        total.addView(ui.t(money(balance), 22f, ui.heroInk, true));
        total.addView(ui.t(fa(custCount + " مشتری") + "  •  " + fa(txCount + " تراکنش"), 10.5f, ui.muted, false));
        ui.add(body, total, 10);

        body.addView(ui.sectionHeader("مانده حساب مشتری‌ها", null, null));
        List<BalanceRow> rows = db.txns().balanceList();
        if (rows.isEmpty()) {
            emptyState("▤", "گزارشی برای نمایش نیست", "بعد از ثبت مشتری و سفارش، گزارش حساب‌ها اینجا دیده می‌شود.", this::customers, "مشاهده مشتری‌ها");
        } else {
            for (BalanceRow r : rows) {
                String n = r.name == null ? "" : r.name;
                long b = r.balance;
                LinearLayout card = ui.card();
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                LinearLayout info = new LinearLayout(this);
                info.setOrientation(LinearLayout.VERTICAL);
                info.addView(ui.t(n, 14.5f, ui.text, true));
                info.addView(ui.t(b > 0 ? "مانده بدهی مشتری" : "تسویه / بستانکار", 10.5f, ui.muted, false));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
                row.addView(ui.t(money(b), 13f, b > 0 ? ui.danger : ui.green, true));
                card.addView(row);
                ui.add(body, card, 6);
            }
        }

        body.addView(ui.sectionHeader("ابزار گزارش", null, null));
        ui.add(body, ui.ghostBtn("⇧  تهیه فایل پشتیبان", this::exportBackup), 6);
    }

    LinearLayout kpiCard(String label, String value, String symbol, boolean green) {
        LinearLayout c = ui.card();
        c.setPadding(dp(13), dp(13), dp(13), dp(13));
        c.setBackground(ui.round(green ? ui.greenSoft : ui.goldSoft, 18, ui.hairline));
        TextView s = ui.t(symbol, 15f, green ? ui.green : ui.primaryInk, true);
        c.addView(s);
        c.addView(ui.t(label, 10.5f, ui.muted, false));
        TextView v = ui.t(value, 13f, ui.text, true);
        v.setPadding(0, dp(3), 0, 0);
        c.addView(v);
        return c;
    }

    // ------------------------------------------------------------------ backup (offline JSON)
    void exportBackup() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "nakhodi-backup.json");
        startActivityForResult(i, REQ_EXPORT);
    }

    void importBackup() {
        dlg().setTitle("بازیابی پشتیبان")
                .setMessage("بازیابی، اطلاعات فایل را با اطلاعات فعلی ادغام می‌کند (اطلاعات فعلی پاک نمی‌شود). ادامه می‌دهید؟")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("انتخاب فایل", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_IMPORT);
                }).show();
    }

    private void writeBackup(Uri uri) throws Exception {
        String json = SyncStore.snapshot(db).toString();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new Exception("نوشتن فایل انجام نشد");
            out.write(json.getBytes("UTF-8"));
            out.flush();
        }
    }

    private int readBackup(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("فایل خوانده نشد");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return SyncStore.importAll(db, new org.json.JSONObject(sb.toString()));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BazaarBilling.REQ_BUY) {
            BazaarBilling.get(this).onPurchaseResult(resultCode, data, (ok, msg) -> {
                if (ok && Pro.isUnlocked(this)) {
                    toast("پرداخت موفق بود؛ نسخه حرفه‌ای فعال شد ✓");
                    proScreen();
                } else if (!ok) {
                    toast(msg);
                }
            });
            return;
        }
        if (requestCode == REQ_CONTACT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Cursor c = null;
                try {
                    c = getContentResolver().query(data.getData(), new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
                    if (c != null && c.moveToFirst()) {
                        String n = c.getString(0), ph = c.getString(1);
                        if (customerNameField != null) customerNameField.setText(n == null ? "" : n);
                        if (customerPhoneField != null) customerPhoneField.setText(ph == null ? "" : ph);
                    }
                } catch (Exception e) { toast("خواندن مخاطب انجام نشد"); }
                finally { if (c != null) c.close(); }
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                writeBackup(uri);
                toast("فایل پشتیبان ذخیره شد");
            } else if (requestCode == REQ_IMPORT) {
                int n = readBackup(uri);
                toast("پشتیبان بازیابی شد • " + n + " ردیف");
                home();
            }
        } catch (Exception e) {
            toast("خطا در فایل پشتیبان: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Pro (paid)
    void showPaywall() {
        dlg().setTitle("نسخه حرفه‌ای")
                .setMessage("در نسخه رایگان فقط تا " + fa("" + Pro.FREE_ACCOUNTS) + " حساب مشتری می‌توانید ثبت کنید.\n\nبا نسخه حرفه‌ای (" + Pro.PRICE_TEXT + "، یک‌بار برای همیشه) تعداد مشتری‌ها نامحدود می‌شود.")
                .setNegativeButton("بعداً", null)
                .setPositiveButton("مشاهده و خرید", (d, w) -> proScreen())
                .show();
    }

    LinearLayout featureRow(String label, String free, String pro, boolean header) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setPadding(0, dp(7), 0, dp(7));
        r.addView(ui.t(label, header ? 11f : 12.5f, header ? ui.muted : ui.text, header), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView f = ui.t(free, 12f, ui.muted, header);
        f.setGravity(Gravity.CENTER);
        TextView p = ui.t(pro, 12f, header ? ui.muted : ui.primaryInk, true);
        p.setGravity(Gravity.CENTER);
        r.addView(f, new LinearLayout.LayoutParams(dp(78), -2));
        r.addView(p, new LinearLayout.LayoutParams(dp(78), -2));
        return r;
    }

    void proScreen() {
        base("نسخه حرفه‌ای", "ثبت حساب نامحدود", 4);
        final boolean pro = Pro.isUnlocked(this);

        LinearLayout hero = ui.card();
        hero.setBackground(ui.grad(ui.hero1, ui.hero2, 22));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.addView(ui.t(pro ? "✓ نسخه حرفه‌ای فعال است" : "ارتقا به نسخه حرفه‌ای", 18f, ui.heroInk, true));
        hero.addView(ui.t(pro
                ? "تعداد مشتری‌ها نامحدود است. ممنون که حمایت کردی."
                : "برای چایخانه‌هایی که مشتری‌های زیادی دارند؛ یک‌بار پرداخت، همیشه.", 12f, ui.heroSoft, false));
        if (!pro) {
            TextView price = ui.t(Pro.PRICE_TEXT, 26f, ui.heroInk, true);
            price.setPadding(dp(6), dp(10), dp(6), dp(2));
            hero.addView(price);
            hero.addView(ui.t("پرداخت یک‌باره  •  از طریق کافه‌بازار", 11f, ui.heroSoft, false));
        }
        ui.add(body, hero, 2);

        body.addView(ui.sectionHeader("مقایسه", null, null));
        LinearLayout cmp = ui.card();
        cmp.addView(featureRow("", "رایگان", "حرفه‌ای", true));
        cmp.addView(featureRow("تعداد مشتری", fa("" + Pro.FREE_ACCOUNTS) + " نفر", "نامحدود", false));
        cmp.addView(featureRow("همگام‌سازی آفلاین با QR", "✓", "✓", false));
        cmp.addView(featureRow("پشتیبان‌گیری محلی", "✓", "✓", false));
        ui.add(body, cmp, 6);

        if (!pro) {
            ui.add(body, ui.btn("＋   خرید نسخه حرفه‌ای", () -> {
                BazaarBilling.get(this).buy(this, (ok, msg) -> {
                    if (!ok) toast(msg);
                    else { toast("نسخه حرفه‌ای فعال شد ✓"); proScreen(); }
                });
            }), 8);
        }
        ui.add(body, ui.ghostBtn("بازگشت", this::home), 6);
    }

    // ------------------------------------------------------------------ sync card
    TextView syncStatusText;

    void onSyncEvent(boolean changed) {
        if (changed) {
            if (onHome) home();
            else toast("اطلاعات تازه از گوشی دیگر دریافت شد");
        } else {
            refreshSyncCard();
        }
    }

    void refreshSyncCard() {
        if (syncStatusText == null) return;
        SyncManager sm = SyncManager.get(this);
        String st = sm.statusLine();
        if (st == null) {
            syncStatusText.setText("همگام‌سازی گوشی‌ها: هنوز جفت نشده — برای اتصال بزنید");
            syncStatusText.setTextColor(ui.muted);
            return;
        }
        syncStatusText.setText(st);
        int code = sm.stateCode();
        syncStatusText.setTextColor(code == 1 ? ui.green : code == 3 ? ui.danger : ui.muted);
    }

    void addSyncCard() {
        LinearLayout c = ui.card();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView dot = ui.t("●", 11f, ui.green, true);
        dot.setPadding(0, 0, dp(8), 0);
        row.addView(dot);
        syncStatusText = ui.t("", 11.5f, ui.muted, false);
        row.addView(syncStatusText, new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(row);
        c.setOnClickListener(v -> startActivity(new Intent(this, PairingActivity.class)));
        ui.add(body, c, 8);
        refreshSyncCard();
    }
}
