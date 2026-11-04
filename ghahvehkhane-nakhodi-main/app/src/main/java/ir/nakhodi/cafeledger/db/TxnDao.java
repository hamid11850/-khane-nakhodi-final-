package ir.nakhodi.cafeledger.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface TxnDao {

    @Insert
    long insert(Txn t);

    @Update
    void update(Txn t);

    @Query("SELECT * FROM tx WHERE deleted=0 AND customer_uid=:uid ORDER BY id DESC")
    List<Txn> ofCustomer(String uid);

    @Query("SELECT * FROM tx")
    List<Txn> all();

    @Query("SELECT * FROM tx WHERE id=:id LIMIT 1")
    Txn byId(long id);

    @Query("SELECT * FROM tx WHERE uid=:uid LIMIT 1")
    Txn byUid(String uid);

    @Query("SELECT COALESCE(SUM(amount),0) FROM tx WHERE kind=:k AND deleted=0")
    long sumKind(String k);

    @Query("SELECT COALESCE(SUM(amount),0) FROM tx WHERE kind=:k AND deleted=0 AND created LIKE :prefix")
    long sumOnDay(String k, String prefix);

    @Query("SELECT COUNT(*) FROM tx WHERE customer_uid=:uid AND deleted=0")
    long countOf(String uid);

    @Query("SELECT COUNT(*) FROM tx WHERE deleted=0")
    long countAll();

    @Query("SELECT COALESCE(SUM(CASE WHEN kind='SALE' THEN amount ELSE -amount END),0) FROM tx WHERE customer_uid=:uid AND deleted=0")
    long balanceOf(String uid);

    @Query("SELECT c.id AS id, c.name AS name, c.uid AS uid, " +
            "COALESCE(SUM(CASE WHEN t.kind='SALE' THEN t.amount ELSE -t.amount END),0) AS balance " +
            "FROM customers c LEFT JOIN tx t ON t.customer_uid = c.uid AND t.deleted = 0 " +
            "WHERE c.deleted = 0 GROUP BY c.uid ORDER BY balance DESC")
    List<BalanceRow> balanceList();

    @Query("UPDATE tx SET description=:d, amount=:a, updated_at=:ts WHERE id=:id")
    void edit(long id, String d, long a, long ts);

    @Query("UPDATE tx SET deleted=1, updated_at=:ts WHERE id=:id")
    void softDelete(long id, long ts);

    @Query("UPDATE tx SET deleted=1, updated_at=:ts WHERE customer_uid=:uid")
    void softDeleteByCustomer(String uid, long ts);

    @Query("DELETE FROM tx")
    void wipe();
}
