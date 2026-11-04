package ir.nakhodi.cafeledger.db;

import androidx.room.ColumnInfo;

/** Result row of the per-customer balance report. */
public class BalanceRow {

    @androidx.room.ColumnInfo(name = "id")
    public long id;

    @androidx.room.ColumnInfo(name = "name")
    public String name;

    @androidx.room.ColumnInfo(name = "uid")
    public String uid;

    @androidx.room.ColumnInfo(name = "balance")
    public long balance;
}
