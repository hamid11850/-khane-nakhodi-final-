package ir.nakhodi.cafeledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * اتصال آفلاین دو گوشی (مدیر / شاگرد) — فقط Wi-Fi محلی، بدون اینترنت و بدون هیچ سرویس ابری.
 * <p>
 * گوشی مدیر: یک توکن امنیتی می‌سازد، سرور محلی (Socket روی پورت ۸۰۸۰) را روشن می‌کند و کد QR شامل
 * IP محلی + پورت + توکن را نشان می‌دهد. گوشی شاگرد: با دوربین (ZXing) همان QR را اسکن می‌کند و
 * بلافاصله با مدیر همگام می‌شود.
 */
public class PairingActivity extends Activity {

    static final int REQ_CAMERA = 811;

    Ui ui;
    LinearLayout body;
    SyncManager sync;
    TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        sync = SyncManager.get(this);
        sync.start();
        showStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sync.isManager()) sync.start();
    }

    // ------------------------------------------------------------------ screens
    void showStart() {
        body = ui.page("جفت‌سازی آفلاین", "بدون اینترنت • فقط Wi-Fi");
        if (sync.isPaired()) showPaired();
        else showChooser();
    }

    void showChooser() {
        LinearLayout info = ui.card();
        info.addView(ui.t("دو گوشی، یک دفتر حساب", 15f, ui.text, true));
        info.addView(ui.t("۱) روی گوشی «مدیر» دکمه اول را بزنید تا کد QR روی صفحه بیاید.", 12.5f, ui.muted, false));
        info.addView(ui.t("۲) روی گوشی «شاگرد» دکمه دوم را بزنید و همان QR را اسکن کنید.", 12.5f, ui.muted, false));
        info.addView(ui.t("۳) از آن به بعد هر بار که به هم وصل شوید، اطلاعات دو گوشی با هم ادغام می‌شود.", 12.5f, ui.muted, false));
        info.addView(ui.t("هر دو گوشی روی یک Wi-Fi باشند (یا گوشی مدیر هات‌اسپات روشن کند). اینترنت لازم نیست.", 11.5f, ui.primaryInk, false));
        body.addView(info);

        ui.add(body, ui.btn("＋   این گوشی «مدیر» است", this::startManager));
        ui.add(body, ui.btn("این گوشی «شاگرد» است (اسکن QR)", this::chooseWorkerScan));
    }

    void showPaired() {
        LinearLayout c = ui.card();
        c.addView(ui.t(sync.isManager() ? "✓ این گوشی مدیر است" : "✓ این گوشی شاگرد است", 16f, ui.green, true));
        status = ui.t("", 12f, ui.muted, false);
        c.addView(status);
        body.addView(c);
        refreshStatus();
        if (sync.isManager()) {
            ui.add(body, ui.btn("نمایش کد QR برای گوشی شاگرد", this::showManagerQr));
        } else {
            ui.add(body, ui.btn("＋   همگام‌سازی با گوشی مدیر", this::syncNow));
            ui.add(body, ui.btn("اسکن دوباره کد QR / تغییر مدیر", this::chooseWorkerScan));
        }
        ui.add(body, ui.btn("قطع جفت‌سازی این گوشی", this::confirmForget));
    }

    void refreshStatus() {
        if (status == null) return;
        String s = sync.statusLine();
        status.setText(s == null ? "جفت‌سازی نشده" : s);
        int code = sync.stateCode();
        status.setTextColor(code == 1 ? ui.green : code == 3 ? ui.danger : ui.muted);
    }

    // ------------------------------------------------------------------ manager
    void startManager() {
        sync.becomeManager(SyncManager.newToken(), SyncManager.DEFAULT_PORT);
        showManagerQr();
    }

    void showManagerQr() {
        body = ui.page("گوشی مدیر", "کد QR را روی گوشی شاگرد اسکن کنید");
        String ip = sync.myIp();
        if (!Net.looksLikeIp(ip)) {
            LinearLayout warn = ui.card();
            warn.addView(ui.t("⚠ آدرس Wi-Fi پیدا نشد", 14f, ui.danger, true));
            warn.addView(ui.t("Wi-Fi یا هات‌اسپات را روشن کنید و دوباره تلاش کنید.", 12f, ui.muted, false));
            body.addView(warn);
            ui.add(body, ui.btn("تلاش دوباره", this::showManagerQr));
            return;
        }
        String payload = SyncManager.qrPayload(ip, sync.port(), sync.token());

        LinearLayout box = ui.card();
        box.setGravity(Gravity.CENTER);
        ImageView qrView = new ImageView(this);
        Bitmap bmp = Qr.make(payload, 720);
        if (bmp != null) {
            qrView.setImageBitmap(bmp);
            qrView.setAdjustViewBounds(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(10));
            box.addView(qrView, lp);
        } else {
            box.addView(ui.t("ساخت کد QR انجام نشد؛ از ورود دستی استفاده کنید.", 12f, ui.danger, false));
        }
        TextView addr = ui.t(ip + ":" + sync.port(), 16f, ui.primaryInk, true);
        addr.setGravity(Gravity.CENTER);
        box.addView(addr);
        TextView tk = ui.t("کد امنیتی: " + sync.token(), 13f, ui.text, true);
        tk.setGravity(Gravity.CENTER);
        box.addView(tk);
        body.addView(box);

        LinearLayout st = ui.card();
        status = ui.t("", 12f, ui.muted, false);
        st.addView(status);
        body.addView(st);
        refreshStatus();

        ui.add(body, ui.btn("ورود دستی برای گوشی شاگرد", () -> manualHint(ip, sync.port(), sync.token())));
        ui.add(body, ui.btn("بستن", this::finish));
    }

    void manualHint(String ip, int port, String token) {
        new AlertDialog.Builder(this, Ui.isDark(this) ? R.style.LuxDialogDark : R.style.LuxDialog)
                .setTitle("ورود دستی")
                .setMessage("روی گوشی شاگرد «شاگرد» را بزنید و «ورود دستی» را انتخاب کنید:\n\nآدرس: "
                        + ip + "\nپورت: " + port + "\nکد امنیتی: " + token)
                .setPositiveButton("باشه", null)
                .show();
    }

    // ------------------------------------------------------------------ worker
    void chooseWorkerScan() {
        new AlertDialog.Builder(this, Ui.isDark(this) ? R.style.LuxDialogDark : R.style.LuxDialog)
                .setTitle("اتصال به گوشی مدیر")
                .setItems(new String[]{"اسکن کد QR", "ورود دستی آدرس و کد"}, (d, w) -> {
                    if (w == 0) askCamera();
                    else manualEntry();
                })
                .show();
    }

    void askCamera() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        launchScanner();
    }

    void launchScanner() {
        new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("کد QR روی گوشی مدیر را داخل کادر بگیرید")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code != REQ_CAMERA) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) launchScanner();
        else toast("برای اسکن کد QR اجازه دوربین لازم است");
    }

    void manualEntry() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        f.setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(2));
        EditText ip = ui.edit("آدرس گوشی مدیر مثل 192.168.1.5");
        ip.setInputType(InputType.TYPE_CLASS_TEXT);
        EditText port = ui.edit("پورت (پیش‌فرض ۸۰۸۰)");
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(SyncManager.DEFAULT_PORT));
        EditText token = ui.edit("کد امنیتی");
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        f.addView(ip);
        f.addView(port);
        f.addView(token);
        new AlertDialog.Builder(this, Ui.isDark(this) ? R.style.LuxDialogDark : R.style.LuxDialog)
                .setTitle("ورود دستی")
                .setView(f)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("اتصال", (d, w) -> {
                    String a = ip.getText().toString().trim();
                    int p;
                    try { p = Integer.parseInt(port.getText().toString().trim()); }
                    catch (Exception e) { p = SyncManager.DEFAULT_PORT; }
                    String t = token.getText().toString().trim().toUpperCase();
                    if (!Net.looksLikeIp(a) || t.isEmpty()) { toast("آدرس یا کد امنیتی معتبر نیست"); return; }
                    join(a, p, t);
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult r = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (r != null) {
            String text = r.getContents();
            if (text == null || text.trim().isEmpty()) { toast("اسکن لغو شد"); return; }
            parsePayload(text.trim());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** قالب QR: nakhodi://pair?ip=..&port=..&token=.. */
    void parsePayload(String text) {
        try {
            String ip = null, token = null;
            int port = SyncManager.DEFAULT_PORT;
            if (text.toLowerCase().startsWith("nakhodi://")) {
                Uri u = Uri.parse(text);
                ip = u.getQueryParameter("ip");
                String p = u.getQueryParameter("port");
                token = u.getQueryParameter("token");
                if (p != null) port = Integer.parseInt(p.trim());
            } else if (text.contains("|")) {
                String[] a = text.split("\\|");
                ip = a[0].trim();
                if (a.length > 1) port = Integer.parseInt(a[1].trim());
                if (a.length > 2) token = a[2].trim();
            }
            if (!Net.looksLikeIp(ip) || token == null || token.trim().isEmpty()) {
                toast("کد QR معتبر نیست");
                return;
            }
            join(ip, port, token.trim().toUpperCase());
        } catch (Exception e) {
            toast("کد QR خوانده نشد؛ دوباره تلاش کنید");
        }
    }

    void join(String ip, int port, String token) {
        sync.becomeWorker(ip, port, token);
        body = ui.page("گوشی شاگرد", "اتصال به " + ip + ":" + port);
        LinearLayout c = ui.card();
        c.addView(ui.t("✓ جفت‌سازی انجام شد", 16f, ui.green, true));
        c.addView(ui.t("این گوشی شاگرد است؛ اطلاعاتش با گوشی مدیر ادغام می‌شود.", 12f, ui.muted, false));
        status = ui.t("", 12f, ui.muted, false);
        c.addView(status);
        body.addView(c);
        refreshStatus();
        syncNow();
        ui.add(body, ui.btn("پایان", this::finish));
    }

    void syncNow() {
        if (status != null) status.setText("در حال همگام‌سازی با گوشی مدیر…");
        sync.syncNow((ok, msg) -> {
            toast(msg);
            if (status != null) {
                status.setText(msg);
                status.setTextColor(ok ? ui.green : ui.danger);
            }
        });
    }

    void confirmForget() {
        new AlertDialog.Builder(this, Ui.isDark(this) ? R.style.LuxDialogDark : R.style.LuxDialog)
                .setTitle("قطع جفت‌سازی")
                .setMessage("جفت‌سازی این گوشی حذف می‌شود. اطلاعات داخل برنامه پاک نمی‌شود.")
                .setNegativeButton("انصراف", null)
                .setPositiveButton("قطع", (d, w) -> {
                    sync.forget();
                    showStart();
                })
                .show();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
