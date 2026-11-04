package ir.nakhodi.cafeledger;

import android.content.Context;
import android.content.SharedPreferences;

/** Free / Pro (one-time paid, sold through Cafe Bazaar in-app billing). */
final class Pro {
    /** free version: how many customer accounts can be registered (Pro = unlimited) */
    static final int FREE_CUSTOMERS = 5;
    /** همان سقف، با نامی که صفحه‌های تازه استفاده می‌کنند */
    static final int FREE_ACCOUNTS = FREE_CUSTOMERS;
    /** product id that must exist in the Bazaar developer panel (in-app product, one-time purchase) */
    static final String SKU = "pro_unlock";
    /** shown in the app; the real price is the one set in the Bazaar panel (keep both the same) */
    static final String PRICE_TEXT = "۱۲۰٬۰۰۰ تومان";
    /** Bazaar RSA public key for in-app billing (a public key, safe to keep in the app). */
    static final String RSA_KEY =
            "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDxzrQ6CSNqYCHzyccAESSqdWl7xAxMYJgc89vR/i2qqLrFHtHzcNNxnnCDSEG8hQ3igm0KEzKxm/Hwj5Goi5D/H1MeS9esIHrL5lOY"
            + "+ZI2LbJ/B0gG8YR/1x4mVH+0gvNXU5EzO/Fa0sjSKLoE6KbSxGB3fwqL2pQoto5c3We3mS++3iNiAL0QXcVyOMD34fQCJ2yFLLO9OluHCAjcmKXmCmm0S6Wg8LTpy6lanpsCAwEAAQ==";

    private Pro() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("cafe_pro", Context.MODE_PRIVATE);
    }

    static boolean isUnlocked(Context c) { return prefs(c).getBoolean("pro", false); }

    static void setUnlocked(Context c, boolean v) { prefs(c).edit().putBoolean("pro", v).apply(); }
}
