package ir.nakhodi.cafeledger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import ir.nakhodi.cafeledger.db.AppDb;

/** The worker phone's sync client: connects to the manager's local server and merges both ways. */
final class SyncClient {

    interface Done { void done(boolean ok, String message, int applied); }

    private SyncClient() {}

    /** Blocking call — always run it off the UI thread. */
    static void run(AppDb db, String ip, int port, String token, Done cb) {
        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(ip, port), 6000);
            s.setSoTimeout(25000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);

            JSONObject hello = new JSONObject();
            hello.put("t", "hello");
            hello.put("token", token);
            hello.put("role", "worker");
            hello.put("v", 1);
            out.println(hello.toString());

            JSONObject welcome = new JSONObject(read(in));
            if (!"welcome".equals(welcome.optString("t"))) {
                cb.done(false, welcome.optString("msg", "گوشی مدیر اتصال را نپذیرفت"), 0);
                return;
            }

            JSONObject push = new JSONObject();
            push.put("t", "push");
            push.put("data", SyncStore.snapshot(db));
            out.println(push.toString());

            JSONObject data = new JSONObject(read(in));
            if (!"data".equals(data.optString("t"))) {
                cb.done(false, "پاسخ نامعتبر از گوشی مدیر", 0);
                return;
            }
            int applied = SyncStore.merge(db, data.optJSONObject("data"));

            JSONObject bye = new JSONObject();
            bye.put("t", "bye");
            out.println(bye.toString());

            cb.done(true, "همگام‌سازی انجام شد", applied);
        } catch (SocketTimeoutException e) {
            cb.done(false, "زمان اتصال تمام شد؛ گوشی مدیر را بررسی کنید", 0);
        } catch (ConnectException e) {
            cb.done(false, "اتصال برقرار نشد؛ هر دو گوشی روی یک Wi-Fi/هات‌اسپات باشند و سرور مدیر روشن باشد", 0);
        } catch (Throwable t) {
            cb.done(false, "خطا: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()), 0);
        } finally {
            try { if (s != null) s.close(); } catch (Exception ignored) { }
        }
    }

    private static String read(BufferedReader in) throws Exception {
        String line = in.readLine();
        if (line == null) throw new Exception("ارتباط قطع شد");
        return line;
    }
}
