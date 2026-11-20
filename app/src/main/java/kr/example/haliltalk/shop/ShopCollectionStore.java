package kr.example.haliltalk.shop;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import kr.example.haliltalk.character.CharacterCatalog;

/** One local wishlist target per account database; never changes wallet or appearance. */
public final class ShopCollectionStore {
    private final SQLiteDatabase database;

    /** The database owner creates the schema and retains ownership of the connection. */
    public ShopCollectionStore(SQLiteDatabase database) {
        this.database = database;
    }

    public static void createTables(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS shop_wishlist("
                + "singleton INTEGER PRIMARY KEY CHECK(singleton=1),item TEXT NOT NULL)");
    }

    public String target() {
        try (Cursor cursor = database.rawQuery(
                "SELECT item FROM shop_wishlist WHERE singleton=1", null)) {
            if (!cursor.moveToFirst()) return null;
            String item = cursor.getString(0);
            validate(item);
            return item;
        }
    }

    /** A failed write, including a FAIL trigger, must leave the previous target intact. */
    public void setTarget(String item) {
        validate(item);
        database.beginTransaction();
        try {
            database.execSQL("INSERT OR REPLACE INTO shop_wishlist(singleton,item) VALUES(1,?)",
                    new Object[] {item});
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public void clear() {
        database.beginTransaction();
        try {
            database.execSQL("DELETE FROM shop_wishlist WHERE singleton=1");
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static void validate(String item) {
        for (String id : CharacterCatalog.allAccessoryIds()) if (id.equals(item)) return;
        throw new IllegalArgumentException("Unknown collection item");
    }
}
