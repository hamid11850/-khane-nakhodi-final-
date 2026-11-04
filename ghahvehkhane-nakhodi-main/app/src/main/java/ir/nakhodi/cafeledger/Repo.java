package ir.nakhodi.cafeledger;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import ir.nakhodi.cafeledger.db.AppDb;
import ir.nakhodi.cafeledger.db.Customer;
import ir.nakhodi.cafeledger.db.Product;
import ir.nakhodi.cafeledger.db.Txn;

/**
 * All local writes go through here so that every row always carries its sync identity
 * ({@code uid}) and a fresh {@code updated_at} stamp. That is what makes the offline
 * two-way merge possible later on.
 */
final class Repo {

    private Repo() {}

    static String stamp() {
        return new java.text.SimpleDateFormat("yyyy/MM/dd - HH:mm", java.util.Locale.US).format(new java.util.Date());
    }

    static String uid() {
        return java.util.UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------ customers
    static long addCustomer(AppDb db, String name, String phone, String note) {
        Customer c = new Customer();
        c.name = name;
        c.phone = phone;
        c.note = note;
        c.uid = uid();
        c.updated_at = System.currentTimeMillis();
        c.deleted = 0;
        return db.customers().insert(c);
    }

    static void renameCustomer(AppDb db, long id, String name) {
        Customer c = db.customers().byId(id);
        if (c == null) return;
        db.customers().edit(id, name, c.phone, c.note, System.currentTimeMillis());
    }

    /** Soft delete: the customer and every entry of the account are flagged, so the removal also syncs. */
    static void deleteCustomer(AppDb db, long id) {
        Customer c = db.customers().byId(id);
        if (c == null) return;
        long now = System.currentTimeMillis();
        if (c.uid != null) {
            db.txns().softDeleteByCustomer(c.uid, now);
            db.customers().softDelete(c.uid, now);
        } else {
            db.customers().hardDelete(id);   // ردیف قدیمی بدون شناسهٔ همگام‌سازی: حذف مستقیم
        }
    }

    static Customer customer(AppDb db, long id) {
        return db.customers().byId(id);
    }

    static long txCount(AppDb db, long customerId) {
        Customer c = db.customers().byId(customerId);
        return c == null || c.uid == null ? 0 : db.txns().countOf(c.uid);
    }

    static long balance(AppDb db, long customerId) {
        Customer c = db.customers().byId(customerId);
        return c == null || c.uid == null ? 0 : db.txns().balanceOf(c.uid);
    }

    // ------------------------------------------------------------------ transactions
    static void addTx(AppDb db, long customerId, String kind, String desc, long amount) {
        Customer c = db.customers().byId(customerId);
        if (c == null) return;
        if (c.uid == null) {                      // legacy row without identity: repair it first
            c.uid = uid();
            c.updated_at = System.currentTimeMillis();
            db.customers().update(c);
        }
        Txn t = new Txn();
        t.uid = uid();
        t.customerUid = c.uid;
        t.kind = kind;
        t.description = desc;
        t.amount = amount;
        t.created = stamp();
        t.updated_at = System.currentTimeMillis();
        t.deleted = 0;
        db.txns().insert(t);
    }

    static void deleteTx(AppDb db, long txId) {
        db.txns().softDelete(txId, System.currentTimeMillis());
    }

    /** Removes {@code left} units from a "name × qty" sale, keeping the row (and its sync id) alive. */
    static void reduceTx(AppDb db, long txId, String product, int left, long newAmount) {
        db.txns().edit(txId, product + " × " + left, newAmount, System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ products
    static void addProduct(AppDb db, String name, long price) {
        Product p = new Product();
        p.name = name;
        p.price = price;
        p.uid = uid();
        p.updated_at = System.currentTimeMillis();
        p.deleted = 0;
        db.products().insert(p);
    }

    static void editProduct(AppDb db, long id, String name, long price) {
        Product p = db.products().byId(id);
        if (p == null) return;
        p.name = name;
        p.price = price;
        p.updated_at = System.currentTimeMillis();
        db.products().update(p);
    }

    static boolean isMenuEmpty(AppDb db) {
        return db.products().live().isEmpty();
    }
}
