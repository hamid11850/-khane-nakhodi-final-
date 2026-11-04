package ir.nakhodi.cafeledger;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cafe Bazaar in-app billing (API v3 over AIDL) for the one-time "Pro" product.
 * The purchase is never consumed, so it stays owned and can be restored after a reinstall.
 */
final class BazaarBilling {
    static final String BAZAAR = "com.farsitel.bazaar";
    static final String ACTION = "ir.cafebazaar.pardakht.InAppBillingService.BIND";
    static final int API = 3;
    static final int REQ_BUY = 9051;

    interface Callback { void done(boolean ok, String message); }

    private static BazaarBilling inst;

    static synchronized BazaarBilling get(Context c) {
        if (inst == null) inst = new BazaarBilling(c.getApplicationContext());
        return inst;
    }

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(BAZAAR, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Runnable> waiting = new ArrayList<>();
    private volatile IInAppBillingService service;
    private volatile boolean binding;

    private BazaarBilling(Context c) { app = c; }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IInAppBillingService.Stub.asInterface(binder);
            binding = false;
            List<Runnable> run;
            synchronized (waiting) {
                run = new ArrayList<>(waiting);
                waiting.clear();
            }
            for (Runnable r : run) io.execute(r);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
        }
    };

    private void post(final Callback cb, final boolean ok, final String msg) {
        if (cb != null) main.post(() -> cb.done(ok, msg));
    }

    /** Runs r on the worker thread as soon as the Bazaar billing service is bound. */
    private void withService(final Runnable r, final Callback onFail) {
        if (service != null) {
            io.execute(r);
            return;
        }
        synchronized (waiting) {
            waiting.add(r);
        }
        if (binding) return;
        Intent i = new Intent(ACTION);
        i.setPackage(BAZAAR);
        boolean ok = false;
        try {
            ok = app.bindService(i, conn, Context.BIND_AUTO_CREATE);
        } catch (Exception ignored) {
        }
        if (!ok) {
            synchronized (waiting) {
                waiting.clear();
            }
            post(onFail, false, "اتصال به بازار برقرار نشد؛ برنامه‌ی بازار باید نصب و به‌روز باشد.");
            return;
        }
        binding = true;
        main.postDelayed(() -> {
            if (service == null) {
                binding = false;
                synchronized (waiting) {
                    waiting.clear();
                }
                post(onFail, false, "بازار پاسخ نداد؛ اینترنت را بررسی و دوباره تلاش کنید.");
            }
        }, 15000);
    }

    /** Checks the purchases of the signed-in Bazaar account and stores the Pro state. */
    void refreshOwned(final Callback cb) {
        if (!installed(app)) {
            post(cb, false, "برنامه‌ی بازار روی این گوشی نصب نیست.");
            return;
        }
        withService(() -> {
            try {
                Bundle b = service.getPurchases(API, app.getPackageName(), "inapp", null);
                int rc = b.getInt("RESPONSE_CODE", -1);
                if (rc != 0) {
                    post(cb, false, "پاسخ بازار: کد " + rc);
                    return;
                }
                ArrayList<String> data = b.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
                ArrayList<String> sig = b.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
                boolean owned = false;
                if (data != null) {
                    for (int i = 0; i < data.size(); i++) {
                        String s = (sig != null && i < sig.size()) ? sig.get(i) : null;
                        if (validPurchase(data.get(i), s)) {
                            owned = true;
                            break;
                        }
                    }
                }
                Pro.setUnlocked(app, owned);
                post(cb, true, owned ? "owned" : "none");
            } catch (Throwable t) {
                post(cb, false, "خطا در ارتباط با بازار: " + t.getClass().getSimpleName());
            }
        }, cb);
    }

    /** Opens Bazaar's payment screen. callback(ok=true) only when the item was already owned. */
    void buy(final Activity a, final Callback cb) {
        if (!installed(app)) {
            post(cb, false, "برای خرید، برنامه‌ی بازار باید روی گوشی نصب باشد.");
            return;
        }
        withService(() -> {
            try {
                Bundle b = service.getBuyIntent(API, app.getPackageName(), Pro.SKU, "inapp", UUID.randomUUID().toString());
                int rc = b.getInt("RESPONSE_CODE", -1);
                if (rc == 7) {            // already owned
                    refreshOwned(cb);
                    return;
                }
                final PendingIntent pi = b.getParcelable("BUY_INTENT");
                if (rc != 0 || pi == null) {
                    post(cb, false, "بازار خرید را شروع نکرد (کد " + rc + "). محصول «" + Pro.SKU + "» باید در پنل بازار تعریف شده باشد.");
                    return;
                }
                main.post(() -> {
                    try {
                        a.startIntentSenderForResult(pi.getIntentSender(), REQ_BUY, new Intent(), 0, 0, 0);
                    } catch (Exception e) {
                        if (cb != null) cb.done(false, "باز کردن صفحه‌ی پرداخت بازار ممکن نشد.");
                    }
                });
            } catch (Throwable t) {
                post(cb, false, "خطا در ارتباط با بازار: " + t.getClass().getSimpleName());
            }
        }, cb);
    }

    /** Call from Activity.onActivityResult for REQ_BUY. */
    void onPurchaseResult(int resultCode, Intent data, Callback cb) {
        if (data == null) {
            cb.done(false, "پرداخت لغو شد.");
            return;
        }
        int rc = data.getIntExtra("RESPONSE_CODE", 0);
        String purchase = data.getStringExtra("INAPP_PURCHASE_DATA");
        String signature = data.getStringExtra("INAPP_DATA_SIGNATURE");
        if (resultCode == Activity.RESULT_OK && rc == 0 && purchase != null && validPurchase(purchase, signature)) {
            Pro.setUnlocked(app, true);
            cb.done(true, "ok");
        } else if (rc == 7) {
            refreshOwned(cb);
        } else {
            cb.done(false, resultCode == Activity.RESULT_CANCELED ? "پرداخت لغو شد." : "پرداخت انجام نشد (کد " + rc + ").");
        }
    }

    /** product id + purchased state + RSA signature (when the key is configured). */
    private static boolean validPurchase(String data, String signature) {
        try {
            JSONObject o = new JSONObject(data);
            if (!Pro.SKU.equals(o.optString("productId"))) return false;
            if (o.optInt("purchaseState", 0) != 0) return false;
            if (Pro.RSA_KEY.isEmpty()) return true;      // key not configured yet (testing only)
            if (signature == null) return false;
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(Pro.RSA_KEY, Base64.DEFAULT)));
            Signature s = Signature.getInstance("SHA1withRSA");
            s.initVerify(key);
            s.update(data.getBytes(StandardCharsets.UTF_8));
            return s.verify(Base64.decode(signature, Base64.DEFAULT));
        } catch (Exception e) {
            return false;
        }
    }
}
