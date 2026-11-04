package ir.nakhodi.cafeledger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ir.nakhodi.cafeledger.db.AppDb;

/**
 * Owns the offline pairing state of this phone and drives the local sync.
 * <p>
 * Manager phone: keeps assistantship of the local {@link SyncServer} (TCP port 8080) alive.
 * Worker phone: connects to the manager address stored from the scanned QR code and calls
 * {@link SyncClient}. No internet, no account, no cloud service is involved at any point.
 */
final class SyncManager {

    static final String PREFS = "cafe_sync";
    static final String KEY_ROLE = "role";
    static final String KEY_TOKEN = "token";
    static final String KEY_IP = "manager_ip";
    static final String KEY_PORT = "port";
    static final String ROLE_MANAGER = "manager";
    static final String ROLE_WORKER = "worker";
    static final int DEFAULT_PORT = 8080;

    interface Listener { void onSync(boolean changed); }

    interface Cb { void done(boolean ok, String message); }

    private static SyncManager inst;

    static synchronized SyncManager get(Context c) {
        if (inst == null) inst = new SyncManager(c.getApplicationContext());
        return inst;
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private final Context app;
    private final AppDb db;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private volatile SyncServer server;
    private volatile Listener listener;
    private volatile String lastMsg = "";
    private volatile long lastSyncAt = 0;
    private volatile boolean busy = false;

    private SyncManager(Context app) {
        this.app = app;
        this.db = AppDb.get(app);
    }

    // ------------------------------------------------------------------ pairing state
    String role() { return prefs(app).getString(KEY_ROLE, ""); }

    String token() { return prefs(app).getString(KEY_TOKEN, ""); }

    String managerIp() { return prefs(app).getString(KEY_IP, ""); }

    int port() {
        int p = prefs(app).getInt(KEY_PORT, DEFAULT_PORT);
        return p <= 0 ? DEFAULT_PORT : p;
    }

    boolean isPaired() { return !role().isEmpty() && !token().isEmpty(); }

    boolean isManager() { return ROLE_MANAGER.equals(role()); }

    /** This phone hosts the data: it generates the token and serves the socket endpoint. */
    void becomeManager(String token, int port) {
        prefs(app).edit()
                .putString(KEY_ROLE, ROLE_MANAGER)
                .putString(KEY_TOKEN, token)
                .putInt(KEY_PORT, port)
                .remove(KEY_IP)
                .apply();
        lastMsg = "این گوشی مدیر است؛ سرور محلی روشن شد";
        startServer();
    }

    /** This phone follows the manager: the address + token come from the scanned QR code. */
    void becomeWorker(String ip, int port, String token) {
        prefs(app).edit()
                .putString(KEY_ROLE, ROLE_WORKER)
                .putString(KEY_TOKEN, token)
                .putString(KEY_IP, ip)
                .putInt(KEY_PORT, port)
                .apply();
        stopServer();
        lastMsg = "این گوشی شاگرد است؛ آماده همگام‌سازی";
    }

    void forget() {
        stopServer();
        prefs(app).edit().clear().apply();
        lastMsg = "";
        lastSyncAt = 0;
    }

    void setListener(Listener l) { listener = l; }

    // ------------------------------------------------------------------ server (manager)
    String myIp() {
        String ip = Net.localIp(app);
        return ip == null ? "" : ip;
    }

    String serverAddress() { return myIp() + ":" + port(); }

    synchronized void start() {
        if (isManager()) startServer();
    }

    private synchronized void startServer() {
        String t = token();
        if (t.isEmpty()) return;
        if (server != null && server.isRunning()) return;
        server = new SyncServer(db, t, port(), (changed, msg) -> {
            lastMsg = msg;
            lastSyncAt = System.currentTimeMillis();
            post(changed);
        });
        server.start();
    }

    synchronized void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    boolean serverRunning() { return server != null && server.isRunning(); }

    int servedCount() { SyncServer s = server; return s == null ? 0 : s.servedCount(); }

    String serverError() { SyncServer s = server; return s == null ? "" : s.lastError(); }

    // ------------------------------------------------------------------ sync now (worker)
    void syncNow(final Cb cb) {
        if (busy) {
            if (cb != null) cb.done(false, "همگام‌سازی در جریان است…");
            return;
        }
        if (!isPaired()) {
            if (cb != null) cb.done(false, "اول گوشی‌ها را جفت کنید");
            return;
        }
        if (isManager()) {
            lastMsg = "این گوشی مدیر است؛ برای همگام‌سازی دکمه «همگام‌سازی» را روی گوشی شاگرد بزنید";
            if (cb != null) cb.done(true, lastMsg);
            return;
        }
        busy = true;
        final String ip = managerIp(), tok = token();
        final int p = port();
        io.execute(() -> {
            SyncClient.run(db, ip, p, tok, (ok, msg, applied) -> {
                busy = false;
                lastMsg = msg + (ok && applied > 0 ? " • " + applied + " تغییر" : "");
                if (ok) lastSyncAt = System.currentTimeMillis();
                if (cb != null) main.post(() -> cb.done(ok, lastMsg));
                if (ok && applied > 0) post(true);
            });
        });
    }

    private void post(final boolean changed) {
        main.post(() -> {
            Listener l = listener;
            if (l != null) l.onSync(changed);
        });
    }

    // ------------------------------------------------------------------ status for the UI
    /** null when this phone is not paired yet. */
    String statusLine() {
        if (!isPaired()) return null;
        StringBuilder sb = new StringBuilder();
        if (isManager()) {
            String ip = myIp();
            sb.append("گوشی مدیر • سرور محلی ");
            sb.append(ip.isEmpty() ? "بدون IP" : ip + ":" + port());
            sb.append(serverRunning() ? " روشن ✓" : " خاموش ⏳");
            int n = servedCount();
            if (n > 0) sb.append("\n").append(n).append(" همگام‌سازی موفق با گوشی شاگرد");
            String err = serverError();
            if (!err.isEmpty()) sb.append("\n⚠ ").append(err);
        } else {
            sb.append("گوشی شاگرد • مدیر: ").append(managerIp()).append(":").append(port());
            if (lastSyncAt > 0) {
                sb.append("\nآخرین همگام‌سازی: ").append(clock(lastSyncAt));
            } else {
                sb.append("\nهنوز همگام‌سازی نشده ⏳");
            }
        }
        if (!lastMsg.isEmpty()) sb.append("\n").append(lastMsg);
        return sb.toString();
    }

    /** 0 = not paired, 1 = ready, 2 = waiting, 3 = problem */
    int stateCode() {
        if (!isPaired()) return 0;
        if (isManager()) {
            if (!serverError().isEmpty()) return 3;
            return serverRunning() ? 1 : 2;
        }
        if (lastSyncAt == 0) return 2;
        return lastMsg.startsWith("خطا") || lastMsg.contains("نشد") || lastMsg.contains("نادرست") ? 3 : 1;
    }

    private static String clock(long at) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
        return f.format(new java.util.Date(at));
    }

    /** A short, human-friendly pairing token (also embedded in the QR code). */
    static String newToken() {
        String abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(abc.charAt(r.nextInt(abc.length())));
        return sb.toString();
    }

    /** Payload written inside the manager QR code: carries address, port and the security token. */
    static String qrPayload(String ip, int port, String token) {
        return "nakhodi://pair?ip=" + ip + "&port=" + port + "&token=" + token;
    }
}
