package ir.nakhodi.cafeledger.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface CustomerDao {

    @Insert
    long insert(Customer c);

    @Update
    void update(Customer c);

    @Query("SELECT * FROM customers WHERE deleted = 0 ORDER BY name")
    java.util.List<Customer> live();

    @Query("SELECT * FROM customers ORDER BY id")
    java.util.List<Customer> all();

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    Customer byId(long id);

    @Query("SELECT * FROM customers WHERE uid = :uid LIMIT 1")
    Customer byUid(String uid);

    @Query("SELECT COUNT(*) FROM customers WHERE deleted = 0")
    long countLive();

    @Query("UPDATE customers SET name=:name, phone=:phone, note=:note, updated_at=:ts WHERE id=:id")
    void edit(long id, String name, String phone, String note, long ts);

    @Query("UPDATE customers SET deleted=1, updated_at=:ts WHERE uid=:uid")
    void softDelete(String uid, long ts);

    @Query("DELETE FROM customers WHERE id=:id")
    void hardDelete(long id);

    @Query("DELETE FROM customers")
    void wipe();
}
