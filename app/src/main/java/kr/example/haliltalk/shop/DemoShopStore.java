package kr.example.haliltalk.shop;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import kr.example.haliltalk.character.CharacterCatalog;

/** Local demo coins only: no real rewards, daily claims, payments, or server. */
public final class DemoShopStore {
    private static final String[] FREE_ITEMS = {
            "straw_hat", "baseball_cap", "beret", "sunglasses",
            "round_glasses", "sunflower", "coffee_cup", "gamepad"
    };
    private static final String[] APPEARANCE_SLOTS = {"hat", "face", "body", "back", "prop"};

    public enum PurchaseResult {
        PURCHASED, ALREADY_OWNED, INSUFFICIENT_FUNDS, INVALID_ITEM
    }

    private final SQLiteDatabase database;

    /** The caller retains ownership of the open database, including its appearance table. */
    public DemoShopStore(SQLiteDatabase database) {
        this.database = database;
        createTables(database);
    }

    /** Idempotent; the wallet row marks the single atomic seed for this database. */
    public static void createTables(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS demo_wallet("
                    + "id INTEGER PRIMARY KEY CHECK(id=1),"
                    + "balance INTEGER NOT NULL CHECK(balance>=0))");
            database.execSQL("CREATE TABLE IF NOT EXISTS owned_items("
                    + "item_id TEXT NOT NULL PRIMARY KEY)");
            database.execSQL("CREATE TABLE IF NOT EXISTS purchases("
                    + "item_id TEXT NOT NULL PRIMARY KEY REFERENCES owned_items(item_id),"
                    + "price INTEGER NOT NULL CHECK(price>0))");
            ContentValues wallet = new ContentValues();
            wallet.put("id", 1);
            wallet.put("balance", 500);
            if (database.insertWithOnConflict("demo_wallet", null, wallet,
                    SQLiteDatabase.CONFLICT_IGNORE) != -1) {
                for (String id : FREE_ITEMS) addOwned(database, id);
                try (Cursor cursor = database.rawQuery("SELECT * FROM appearance", null)) {
                    while (cursor.moveToNext()) {
                        for (String slot : APPEARANCE_SLOTS) {
                            int column = cursor.getColumnIndex(slot);
                            if (column >= 0) {
                                String id = cursor.getString(column);
                                if (CharacterCatalog.slotFor(id) != null) addOwned(database, id);
                            }
                        }
                    }
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public int balance() {
        try (Cursor cursor = database.rawQuery("SELECT balance FROM demo_wallet WHERE id=1", null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    /** The empty slot is always wearable; unknown IDs are never owned. */
    public boolean owns(String id) {
        if ("none".equals(id)) return true;
        if (CharacterCatalog.slotFor(id) == null) return false;
        try (Cursor cursor = database.rawQuery(
                "SELECT 1 FROM owned_items WHERE item_id=?", new String[] {id})) {
            return cursor.moveToFirst();
        }
    }

    /** Returns 0 for free items/none, -1 for unknown IDs, or a fixed demo price. */
    public int price(String id) {
        if ("none".equals(id)) return 0;
        String slot = CharacterCatalog.slotFor(id);
        if (slot == null) return -1;
        for (String free : FREE_ITEMS) {
            if (free.equals(id)) return 0;
        }
        switch (slot) {
            case "face": return 60;
            case "hat": return 90;
            case "body": return 90;
            case "prop": return 120;
            case "back": return 150;
            default: return -1;
        }
    }

    /**
     * Call only after UI confirmation. Storage errors propagate after rolling back all changes.
     * The transaction serializes ownership checks, debit, and the unique purchase receipt.
     */
    public PurchaseResult purchase(String id) {
        int cost = price(id);
        if (cost < 0 || "none".equals(id)) return PurchaseResult.INVALID_ITEM;
        database.beginTransaction();
        try {
            if (owns(id)) return PurchaseResult.ALREADY_OWNED;
            int currentBalance = balance();
            if (currentBalance < cost) return PurchaseResult.INSUFFICIENT_FUNDS;
            database.execSQL("UPDATE demo_wallet SET balance=balance-? WHERE id=1",
                    new Object[] {cost});
            ContentValues item = new ContentValues();
            item.put("item_id", id);
            database.insertOrThrow("owned_items", null, item);
            ContentValues receipt = new ContentValues();
            receipt.put("item_id", id);
            receipt.put("price", cost);
            database.insertOrThrow("purchases", null, receipt);
            database.setTransactionSuccessful();
            return PurchaseResult.PURCHASED;
        } finally {
            database.endTransaction();
        }
    }

    private static void addOwned(SQLiteDatabase database, String id) {
        ContentValues item = new ContentValues();
        item.put("item_id", id);
        database.insertWithOnConflict("owned_items", null, item, SQLiteDatabase.CONFLICT_IGNORE);
    }
}
