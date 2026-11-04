package ir.nakhodi.cafeledger.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A customer account.
 * <p>
 * {@code uid} is the offline sync identity: a random id that is generated once when the row is created
 * and stays identical on the manager phone and on the worker phone. The sync engine merges rows by
 * {@code uid} (never by the local integer id), {@code updated_at} is the last-write-wins stamp and
 * {@code deleted} is a soft-delete tombstone so deletions also travel between the two phones.
 */
@Entity(tableName = "customers", indices = {@Index(value = "uid", unique = true)})
public class Customer {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "note")
    public String note;

    @ColumnInfo(name = "uid")
    public String uid;

    @ColumnInfo(name = "updated_at")
    public Long updated_at;

    @ColumnInfo(name = "deleted")
    public Integer deleted;
}
