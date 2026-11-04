package ir.nakhodi.cafeledger.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A menu item with its unit price (synced by {@code uid}, soft deleted). */
@Entity(tableName = "products", indices = {@Index(value = "uid", unique = true)})
public class Product {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "price")
    public long price;

    @ColumnInfo(name = "uid")
    public String uid;

    @ColumnInfo(name = "updated_at")
    public Long updated_at;

    @ColumnInfo(name = "deleted")
    public Integer deleted;
}
