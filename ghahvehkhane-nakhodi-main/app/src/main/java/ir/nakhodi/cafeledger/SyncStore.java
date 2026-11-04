package ir.nakhodi.cafeledger;

import org.json.JSONArray;
import org.json.JSONObject;

import ir.nakhodi.cafeledger.db.AppDb;
import ir.nakhodi.cafeledger.db.Customer;
import ir.nakhodi.cafeledger.db.Product;
import ir.nakhodi.cafeledger.db.Txn;

/**
 * JSON view of the local Room data plus the merge rules of the offline two-way sync.
 * <p>
 * Every row is identified by its {@code uid} and carries {@code updated_at} + {@code deleted},
 * which is everything a last-write-wins merge on two phones needs. This class never touches a
 * network by itself: the socket server and the socket client both call it.
 */
final class SyncStore {

    private SyncStore() {}

    // ------------------------------------------------------------------ local -> JSON
    static JSONObject snapshot(AppDb db) {
        JSONObject root = new JSONObject();
        try {
            JSONArray cs = new JSONArray();
            for (Customer c : db.customers().all()) {
                if (c.uid == null || c.uid.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("uid", c.uid);
                o.put("name", nz(c.name));
                o.put("phone", nz(c.phone));
                o.put("note", nz(c.note));
                o.put("updated_at", c.updated_at == null ? 0L : c.updated_at.longValue());
                o.put("deleted", c.deleted == null ? 0 : c.deleted.intValue());
                cs.put(o);
            }
            root.put("customers", cs);

            JSONArray ps = new JSONArray();
            for (Product p : db.products().all()) {
                if (p.uid == null || p.uid.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("uid", p.uid);
                o.put("name", nz(p.name));
                o.put("price", p.price);
                o.put("updated_at", p.updated_at == null ? 0L : p.updated_at.longValue());
                o.put("deleted", p.deleted == null ? 0 : p.deleted.intValue());
                ps.put(o);
            }
            root.put("products", ps);

            JSONArray ts = new JSONArray();
            for (Txn t : db.txns().all()) {
                if (t.uid == null || t.uid.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("uid", t.uid);
                o.put("customer_uid", nz(t.customerUid));
                o.put("kind", nz(t.kind).isEmpty() ? "SALE" : t.kind);
                o.put("description", nz(t.description));
                o.put("amount", t.amount);
                o.put("created", nz(t.created));
                o.put("updated_at", t.updated_at == null ? 0L : t.updated_at.longValue());
                o.put("deleted", t.deleted == null ? 0 : t.deleted.intValue());
                ts.put(o);
            }
            root.put("tx", ts);
        } catch (Exception ignored) {
        }
        return root;
    }

    // ------------------------------------------------------------------ JSON -> local
    /** Two-way merge: for each uid the newest {@code updated_at} wins. Returns how many rows changed. */
    static int merge(AppDb db, JSONObject data) {
        return absorb(db, data, false);
    }

    /** Restore from a backup file: the file always wins. */
    static int importAll(AppDb db, JSONObject data) {
        return absorb(db, data, true);
    }

    private static int absorb(AppDb db, JSONObject data, boolean force) {
        if (data == null) return 0;
        int changed = 0;
        try {
            JSONArray cs = data.optJSONArray("customers");
            if (cs != null) {
                for (int i = 0; i < cs.length(); i++) {
                    JSONObject o = cs.optJSONObject(i);
                    if (o == null) continue;
                    String uid = o.optString("uid", "");
                    if (uid.isEmpty()) continue;
                    long ts = o.optLong("updated_at", 0L);
                    int del = o.optInt("deleted", 0);
                    Customer ex = db.customers().byUid(uid);
                    if (ex == null) {
                        Customer c = new Customer();
                        c.uid = uid;
                        c.name = o.optString("name", "");
                        c.phone = o.optString("phone", "");
                        c.note = o.optString("note", "");
                        c.updated_at = ts;
                        c.deleted = del;
                        db.customers().insert(c);
                        changed++;
                    } else if (force || ts > last(ex.updated_at)) {
                        ex.name = o.optString("name", ex.name);
                        ex.phone = o.optString("phone", "");
                        ex.note = o.optString("note", "");
                        ex.updated_at = ts;
                        ex.deleted = del;
                        db.customers().update(ex);
                        changed++;
                    }
                }
            }

            JSONArray ps = data.optJSONArray("products");
            if (ps != null) {
                for (int i = 0; i < ps.length(); i++) {
                    JSONObject o = ps.optJSONObject(i);
                    if (o == null) continue;
                    String uid = o.optString("uid", "");
                    if (uid.isEmpty()) continue;
                    long ts = o.optLong("updated_at", 0L);
                    int del = o.optInt("deleted", 0);
                    Product ex = db.products().byUid(uid);
                    if (ex == null) {
                        Product p = new Product();
                        p.uid = uid;
                        p.name = o.optString("name", "");
                        p.price = o.optLong("price", 0);
                        p.updated_at = ts;
                        p.deleted = del;
                        db.products().insert(p);
                        changed++;
                    } else if (force || ts > last(ex.updated_at)) {
                        ex.name = o.optString("name", ex.name);
                        ex.price = o.optLong("price", ex.price);
                        ex.updated_at = ts;
                        ex.deleted = del;
                        db.products().update(ex);
                        changed++;
                    }
                }
            }

            JSONArray tsArr = data.optJSONArray("tx");
            if (tsArr != null) {
                for (int i = 0; i < tsArr.length(); i++) {
                    JSONObject o = tsArr.optJSONObject(i);
                    if (o == null) continue;
                    String uid = o.optString("uid", "");
                    if (uid.isEmpty()) continue;
                    long ts = o.optLong("updated_at", 0L);
                    int del = o.optInt("deleted", 0);
                    Txn ex = db.txns().byUid(uid);
                    if (ex == null) {
                        Txn t = new Txn();
                        t.uid = uid;
                        t.customerUid = o.optString("customer_uid", "");
                        t.kind = o.optString("kind", "SALE");
                        t.description = o.optString("description", "");
                        t.amount = o.optLong("amount", 0);
                        t.created = o.optString("created", "");
                        t.updated_at = ts;
                        t.deleted = del;
                        db.txns().insert(t);
                        changed++;
                    } else if (force || ts > last(ex.updated_at)) {
                        ex.customerUid = o.optString("customer_uid", ex.customerUid);
                        ex.kind = o.optString("kind", ex.kind);
                        ex.description = o.optString("description", "");
                        ex.amount = o.optLong("amount", ex.amount);
                        ex.created = o.optString("created", ex.created);
                        ex.updated_at = ts;
                        ex.deleted = del;
                        db.txns().update(ex);
                        changed++;
                    }
                }
            }
        } catch (Exception e) {
            // a malformed payload must never break the local database
        }
        return changed;
    }

    private static long last(Long v) { return v == null ? 0L : v.longValue(); }

    private static String nz(String s) { return s == null ? "" : s; }
}
