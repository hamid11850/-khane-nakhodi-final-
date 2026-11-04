package ir.nakhodi.cafeledger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ir.nakhodi.cafeledger.db.AppDb;

/**
 * The manager phone's local sync server (plain TCP socket on port 8080, no internet involved).
 * <p>
 * A worker phone connects over the same Wi-Fi / hotspot, proves it knows the pairing token,
 * pushes its whole local ledger, receives the merged ledger back. Everything runs on background
 * threads; the SQLite (Room) access happens only here and in {@link SyncClient}.
 */
final class SyncServer {

    interface Listener { void onServerEvent(boolean dataChanged, String message); }

    private final AppDb db;
    private final String token;
    private final int port;
    private final Listener listener;
    private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "nakhodi-sync-conn");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger served = new AtomicInteger(0);
    private volatile ServerSocket socket;
    private volatile String lastError = "";

    SyncServer(AppDb db, String token, int port, Listener listener) {
        this.db = db;
        this.token = token == null ? "" : token;
        this.port = port;
        this.listener = listener;
    }

    boolean isRunning() { return running.get(); }

    int servedCount() { return served.get(); }

    String lastError() { return lastError; }

    void start() {
        if (!running.compareAndSet(false, true)) return;
        Thread t = new Thread(this::acceptLoop, "nakhodi-sync-server");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running.set(false);
        ServerSocket s = socket;
        socket = null;
        if (s != null) {
            try { s.close(); } catch (Exception ignored) { }
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        try {
            ServerSocket s = new ServerSocket();
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port), 8);
            socket = s;
            lastError = "";
            while (running.get()) {
                final Socket conn = s.accept();
                pool.execute(() -> handle(conn));
            }
        } catch (Throwable e) {
            if (running.get()) lastError = "پورت " + port + " باز نشد: " + describe(e);
        } finally {
            running.set(false);
            try { if (socket != null) socket.close(); } catch (Exception ignored) { }
            socket = null;
        }
    }

    private void handle(Socket conn) {
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            conn.setSoTimeout(25000);
            in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8), true);

            JSONObject hello = new JSONObject(read(in));
            if (!"hello".equals(hello.optString("t"))) {
                out.println(deny("درخواست نامعتبر"));
                return;
            }
            if (!tokenMatches(hello.optString("token"))) {
                out.println(deny("کد امنیتی نادرست است"));
                event(false, "یک گوشی با کد امنیتی اشتباه تلاش کرد وصل شود");
                return;
            }
            JSONObject welcome = new JSONObject();
            welcome.put("t", "welcome");
            welcome.put("port", port);
            out.println(welcome.toString());

            JSONObject push = new JSONObject(read(in));
            if (!"push".equals(push.optString("t"))) {
                out.println(deny("بسته داده دریافت نشد"));
                return;
            }
            int applied = SyncStore.merge(db, push.optJSONObject("data"));

            JSONObject reply = new JSONObject();
            reply.put("t", "data");
            reply.put("data", SyncStore.snapshot(db));
            reply.put("applied", applied);
            out.println(reply.toString());

            try { read(in); } catch (Exception ignored) { }   // optional "bye"
            served.incrementAndGet();
            event(applied > 0, applied > 0
                    ? "همگام‌سازی انجام شد؛ " + applied + " ردیف از گوشی شاگرد ادغام شد"
                    : "همگام‌سازی انجام شد؛ تغییر تازه‌ای نبود");
        } catch (Throwable e) {
            event(false, "خطا در همگام‌سازی: " + describe(e));
        } finally {
            try { if (conn != null) conn.close(); } catch (Exception ignored) { }
        }
    }

    private static String read(BufferedReader in) throws Exception {
        String line = in.readLine();
        if (line == null) throw new Exception("ارتباط قطع شد");
        return line;
    }

    private static String deny(String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "deny");
            o.put("msg", msg);
            return o.toString();
        } catch (Exception e) {
            return "{\"t\":\"deny\"}";
        }
    }

    /** Constant-time token comparison. An empty expected token never authorises anything. */
    private boolean tokenMatches(String given) {
        if (token.isEmpty() || given == null || given.isEmpty()) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    private void event(boolean changed, String msg) {
        if (listener != null) listener.onServerEvent(changed, msg);
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.getClass().getSimpleName() : m;
    }
}
