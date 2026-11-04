package ir.nakhodi.cafeledger;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Developer contact (Telegram). */
final class Contact {
    static final String TELEGRAM = "hamid11850";

    private Contact() {}

    static void openTelegram(Activity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/" + TELEGRAM)));
        } catch (Exception e) {
            Toast.makeText(a, "تلگرام یا مرورگر پیدا نشد؛ آیدی سازنده: @" + TELEGRAM, Toast.LENGTH_LONG).show();
        }
    }
}
