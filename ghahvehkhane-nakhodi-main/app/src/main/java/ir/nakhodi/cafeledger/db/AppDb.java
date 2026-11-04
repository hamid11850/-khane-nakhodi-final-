package ir.nakhodi.cafeledger.db;

import android.content.ContentValues;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.UUID;

/**
 * Room database: the single local source of truth of the app. Nothing here needs a network,
 * a Google account or Play Services — the data lives on the phone and travels phone-to-phone
 * over the local Wi-Fi socket sync.
 */
@androidx.room.Database(entities = {Customer.class, Product.class, Txn.class}, version = 1, exportSchema = false)
public abstract class AppDb extends androidx.room.RoomDatabase {

    public static final String NAME = "nakhodi-local.db";

    public abstract CustomerDao customers();

    public abstract ProductDao products();

    public abstract TxnDao txns();

    private static volatile AppDb inst;

    public static AppDb get(android.content.Context c) {
        if (inst == null) {
            synchronized (AppDb.class) {
                if (inst == null) {
                    inst = androidx.room.Room.databaseBuilder(c.getApplicationContext(), AppDb.class, NAME)
                            // The UI reads this very small local ledger synchronously; the offline sync
                            // engine talks to the same instance from its own background thread.
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .addCallback(SEED)
                            .build();
                }
            }
        }
        return inst;
    }

    /** First launch (or first run after a restore): a small starter menu for the café. */
    private static final RoomDatabase.Callback SEED = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            seed(db, "قلیان", 150000);
            seed(db, "املت", 90000);
            seed(db, "لیموناد", 45000);
            seed(db, "چای", 20000);
        }

        private void seed(SupportSQLiteDatabase db, String name, long price) {
            long now = System.currentTimeMillis();
            ContentValues v = new ContentValues();
            v.put("name", name);
            v.put("price", price);
            v.put("uid", UUID.randomUUID().toString());
            v.put("updated_at", now);
            v.put("deleted", 0);
            db.insert("products", android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE, v);
        }
    };
}
