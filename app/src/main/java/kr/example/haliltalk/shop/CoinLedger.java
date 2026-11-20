package kr.example.haliltalk.shop;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

/** Account-local coin history. The wallet remains canonical; old receipts have no known time. */
public final class CoinLedger {
    public static final class Entry {
        public final String eventKey, source, itemId, day;
        public final int amount, balance;
        public final Long createdAt;

        private Entry(Cursor cursor) {
            eventKey=cursor.getString(0);source=cursor.getString(1);
            amount=cursor.getInt(2);balance=cursor.getInt(3);
            createdAt=cursor.isNull(4)?null:cursor.getLong(4);
            itemId=cursor.getString(5);
            day=cursor.getString(6);
        }
    }

    private final SQLiteDatabase database;

    public CoinLedger(SQLiteDatabase database) {
        this.database=database;
    }

    /** v11 adds one opening snapshot, never fabricated past purchase events. */
    public static void createTables(SQLiteDatabase database) {
        initialize(database,false);
    }

    /** Runs inside wallet initialization so a new wallet and its starter grant are atomic. */
    static void initialize(SQLiteDatabase database,boolean starter) {
        database.beginTransaction();
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS coin_ledger("
                    +"id INTEGER PRIMARY KEY,"
                    +"event_key TEXT NOT NULL UNIQUE,"
                    +"source TEXT NOT NULL CHECK(source IN ('opening','starter','purchase','quest')),"
                    +"amount INTEGER NOT NULL CHECK(amount BETWEEN -2147483647 AND 2147483647),"
                    +"balance INTEGER NOT NULL CHECK(balance BETWEEN 0 AND 2147483647),"
                    +"created_at INTEGER, item_id TEXT, day TEXT,"
                    +"CHECK((source='opening' AND amount=0 AND created_at IS NULL)"
                    +" OR (source!='opening' AND created_at IS NOT NULL)))");
            try(Cursor existing=database.rawQuery("SELECT 1 FROM coin_ledger WHERE event_key='opening'",null)) {
                if(!existing.moveToFirst()) {
                    try(Cursor wallet=database.rawQuery("SELECT balance FROM demo_wallet WHERE id=1",null)) {
                        if(!wallet.moveToFirst())throw new IllegalStateException("Coin wallet is missing");
                        insert(database,"opening",starter?"starter":"opening",starter?500:0,
                                wallet.getInt(0),starter?System.currentTimeMillis():null,null);
                    }
                }
            }
            database.setTransactionSuccessful();
        } finally {database.endTransaction();}
    }

    /** Debit, ownership, receipt and ledger entry must share the caller's transaction. */
    static void purchase(SQLiteDatabase database,String item,int cost,int balance) {
        if(!database.inTransaction())throw new IllegalStateException("Coin changes require a transaction");
        insert(database,"purchase:"+item,"purchase",-cost,balance,System.currentTimeMillis(),item);
    }

    /** v12 is additive for an actual v11 ledger; safe if the column already exists. */
    public static void addRewardDay(SQLiteDatabase database) {
        try(Cursor columns=database.rawQuery("PRAGMA table_info(coin_ledger)",null)) {
            while(columns.moveToNext())
                if("day".equals(columns.getString(columns.getColumnIndexOrThrow("name"))))return;
        }
        database.execSQL("ALTER TABLE coin_ledger ADD COLUMN day TEXT");
    }

    public static void reward(SQLiteDatabase database,String day,int amount,int balance) {
        if(!database.inTransaction())throw new IllegalStateException("Coin changes require a transaction");
        ContentValues values=new ContentValues();
        values.put("event_key","quest:"+day);values.put("source","quest");
        values.put("amount",amount);values.put("balance",balance);
        values.put("created_at",System.currentTimeMillis());values.put("day",day);
        if(database.insertOrThrow("coin_ledger",null,values)==-1)
            throw new android.database.sqlite.SQLiteException("Reward ledger was not inserted");
    }

    public List<Entry> entries() {
        ArrayList<Entry> entries=new ArrayList<>();
        try(Cursor cursor=database.rawQuery(
                "SELECT event_key,source,amount,balance,created_at,item_id,day FROM coin_ledger ORDER BY id DESC",null)) {
            while(cursor.moveToNext())entries.add(new Entry(cursor));
        }
        return entries;
    }

    private static void insert(SQLiteDatabase database,String key,String source,int amount,int balance,
            Long createdAt,String item) {
        ContentValues values=new ContentValues();
        values.put("event_key",key);values.put("source",source);values.put("amount",amount);
        values.put("balance",balance);values.put("created_at",createdAt);values.put("item_id",item);
        database.insertOrThrow("coin_ledger",null,values);
    }
}
