package ir.nakhodi.cafeledger.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProductDao {

    @Insert
    long insert(Product p);

    @Update
    void update(Product p);

    @Query("SELECT * FROM products WHERE deleted=0 ORDER BY name")
    java.util.List<Product> live();

    @Query("SELECT * FROM products") java.util.List<Product> all();

    @Query("SELECT * FROM products WHERE uid=:uid LIMIT 1")
    Product byUid(String uid);

    @Query("SELECT * FROM products WHERE id=:id LIMIT 1")
    Product byId(long id);

    @Query("SELECT COUNT(*) FROM products WHERE deleted=0")
    long countLive();
}
