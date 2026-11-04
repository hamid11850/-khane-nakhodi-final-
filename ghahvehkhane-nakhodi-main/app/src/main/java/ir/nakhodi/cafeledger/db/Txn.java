package ir.nakhodi.cafeledger.db;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One ledger entry (kind = SALE or PAY) of a customer.
 * It points at the customer through {@code customer_uid} instead of a local id, so a transaction
 * created offline on the worker phone still lands on the right customer after the merge.
 */
@Entity(tableName = "tx", indices = {@Index(value = "uid", unique = true), @Index(value = "customer_uid")})
public class Txn {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "uid")
    public String uid;

    @ColumnInfo(name = "customer_uid")
    public String customerUid;

    @ColumnInfo(name = "kind")
    public String kind;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "amount")
    public long amount;

    @ColumnInfo(name = "created")
    public String created;

    @ColumnInfo(name = "updated_at")
    public Long updated_at;

    @ColumnInfo(name = "deleted")
    public Integer deleted;
}
