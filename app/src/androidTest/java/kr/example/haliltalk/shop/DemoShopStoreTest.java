package kr.example.haliltalk.shop;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

import kr.example.haliltalk.character.CharacterCatalog;

public class DemoShopStoreTest extends InstrumentationTestCase {
    private SQLiteDatabase database;
    private DemoShopStore store;

    @Override protected void setUp() throws Exception {
        super.setUp();
        database = SQLiteDatabase.create(null);
        database.setForeignKeyConstraintsEnabled(true);
        database.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY, character TEXT NOT NULL,"
                + "hat TEXT NOT NULL, face TEXT NOT NULL, body TEXT NOT NULL,"
                + "back TEXT NOT NULL, prop TEXT NOT NULL)");
        store = new DemoShopStore(database);
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (database != null) database.close();
        } finally {
            super.tearDown();
        }
    }

    public void testSeedAndCatalogPrices() {
        assertEquals(500, store.balance());
        assertEquals(1, count("demo_wallet"));
        assertEquals(8, count("owned_items"));
        assertEquals(0, count("purchases"));
        String[] free = {"straw_hat", "baseball_cap", "beret", "sunglasses",
                "round_glasses", "sunflower", "coffee_cup", "gamepad"};
        for (String id : free) {
            assertTrue(id, store.owns(id));
            assertEquals(id, 0, store.price(id));
            assertEquals(DemoShopStore.PurchaseResult.ALREADY_OWNED, store.purchase(id));
        }
        assertTrue(store.owns("none"));
        assertEquals(0, store.price("none"));
        assertEquals(DemoShopStore.PurchaseResult.INVALID_ITEM, store.purchase("none"));
        assertEquals(30, CharacterCatalog.allAccessoryIds().length);
        int paid = 0;
        for (String id : CharacterCatalog.allAccessoryIds()) {
            if (store.owns(id)) continue;
            paid++;
            String slot = CharacterCatalog.slotFor(id);
            int expected = "face".equals(slot) ? 60 : "back".equals(slot) ? 150
                    : "prop".equals(slot) ? 120 : 90;
            assertEquals(id, expected, store.price(id));
        }
        assertEquals(22, paid);
        assertEquals(500, store.balance());
        assertEquals(8, count("owned_items"));
        assertEquals(0, count("purchases"));
    }

    public void testPurchaseAndDuplicateDoNotDebitTwice() {
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("crown"));
        assertEquals(410, store.balance());
        assertTrue(store.owns("crown"));
        assertEquals(9, count("owned_items"));
        assertEquals(1, count("purchases"));
        try (Cursor cursor = database.rawQuery(
                "SELECT item_id,price FROM purchases", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("crown", cursor.getString(0));
            assertEquals(90, cursor.getInt(1));
        }
        assertEquals(DemoShopStore.PurchaseResult.ALREADY_OWNED,
                new DemoShopStore(database).purchase("crown"));
        assertEquals(410, store.balance());
        assertEquals(9, count("owned_items"));
        assertEquals(1, count("purchases"));
    }

    public void testInsufficientFundsPreserveBalanceOwnershipAndReceipts() {
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("backpack"));
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("angel_wings"));
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("bat_wings"));
        assertEquals(50, store.balance());
        assertEquals(DemoShopStore.PurchaseResult.INSUFFICIENT_FUNDS,
                store.purchase("star_glasses"));
        assertEquals(50, store.balance());
        assertFalse(store.owns("star_glasses"));
        assertEquals(11, count("owned_items"));
        assertEquals(3, count("purchases"));
        assertEquals(4, count("coin_ledger"));
    }

    public void testExactBalanceCanReachZero() {
        database.execSQL("UPDATE demo_wallet SET balance=60 WHERE id=1");
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("star_glasses"));
        assertEquals(0, store.balance());
        assertEquals(DemoShopStore.PurchaseResult.INSUFFICIENT_FUNDS, store.purchase("crown"));
        assertEquals(0, store.balance());
        assertEquals(9, count("owned_items"));
        assertEquals(1, count("purchases"));
    }

    public void testUnknownItemsCannotBeOwnedPricedOrPurchased() {
        String[] invalid = {null, "", "unknown", "mail", "CROWN", "crown' OR 1=1 --"};
        for (String id : invalid) {
            assertFalse(store.owns(id));
            assertEquals(-1, store.price(id));
            assertEquals(DemoShopStore.PurchaseResult.INVALID_ITEM, store.purchase(id));
        }
        assertEquals(500, store.balance());
        assertEquals(8, count("owned_items"));
        assertEquals(0, count("purchases"));
        assertEquals(1, count("coin_ledger"));
    }

    public void testStarterAndPurchaseLedgerAreRecordedOnceWithSignedAmounts() {
        CoinLedger.Entry starter=store.history().get(0);
        assertEquals("opening",starter.eventKey);assertEquals("starter",starter.source);
        assertEquals(500,starter.amount);assertEquals(500,starter.balance);
        assertNotNull(starter.createdAt);assertNull(starter.itemId);
        new DemoShopStore(database);
        assertEquals(1,count("coin_ledger"));
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED,store.purchase("crown"));
        CoinLedger.Entry purchase=store.history().get(0);
        assertEquals("purchase:crown",purchase.eventKey);assertEquals("purchase",purchase.source);
        assertEquals("crown",purchase.itemId);
        assertEquals(-90,purchase.amount);assertEquals(410,purchase.balance);assertNotNull(purchase.createdAt);
        assertEquals(DemoShopStore.PurchaseResult.ALREADY_OWNED,new DemoShopStore(database).purchase("crown"));
        assertEquals(2,count("coin_ledger"));assertEquals(410,store.balance());
    }

    public void testLedgerFailureRollsBackPurchaseOwnershipReceiptAndDebit() {
        database.execSQL("CREATE TRIGGER reject_coin_purchase BEFORE INSERT ON coin_ledger "
                +"WHEN NEW.source='purchase' BEGIN SELECT RAISE(ABORT,'ledger failure'); END");
        try{store.purchase("crown");fail("Ledger failure must abort the whole purchase");}
        catch(SQLiteConstraintException expected){}
        assertEquals(500,store.balance());assertFalse(store.owns("crown"));
        assertEquals(8,count("owned_items"));assertEquals(0,count("purchases"));assertEquals(1,count("coin_ledger"));
        assertFalse(database.inTransaction());
        database.execSQL("DROP TRIGGER reject_coin_purchase");
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED,store.purchase("crown"));
        assertEquals(410,store.balance());assertEquals(1,count("purchases"));assertEquals(2,count("coin_ledger"));
    }

    public void testLedgerEventKeysRejectDuplicateEvents() {
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED,store.purchase("crown"));
        database.beginTransaction();
        try {
            try{CoinLedger.purchase(database,"crown",90,320);fail("Event key must be unique");}
            catch(SQLiteConstraintException expected){}
        } finally {database.endTransaction();}
        assertEquals(2,count("coin_ledger"));assertEquals(410,store.balance());
        assertEquals(-90,store.history().get(0).amount);assertEquals(410,store.history().get(0).balance);
    }

    public void testHistoryReadDoesNotWriteOrMergeAccountDatabases() {
        try(SQLiteDatabase other=SQLiteDatabase.create(null)) {
            other.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY,hat TEXT,face TEXT,prop TEXT)");
            DemoShopStore otherAccount=new DemoShopStore(other);
            store.purchase("crown");
            otherAccount.purchase("umbrella");
            database.execSQL("CREATE TRIGGER reject_history_write BEFORE INSERT ON coin_ledger "
                    +"BEGIN SELECT RAISE(ABORT,'history must be read only'); END");
            assertEquals("purchase:crown",store.history().get(0).eventKey);
            assertEquals(2,store.history().size());assertEquals(410,store.balance());
            assertEquals("purchase:umbrella",otherAccount.history().get(0).eventKey);
            assertEquals(2,otherAccount.history().size());assertEquals(380,otherAccount.balance());
            assertFalse(otherAccount.owns("crown"));assertFalse(store.owns("umbrella"));
        }
    }

    public void testStarterLedgerFailureRollsBackWalletAndFreeItemsThenRetries() {
        try(SQLiteDatabase fresh=SQLiteDatabase.create(null)) {
            fresh.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY,hat TEXT,face TEXT,prop TEXT)");
            DemoShopStore.createTables(fresh);
            CoinLedger.createTables(fresh);
            fresh.execSQL("DELETE FROM coin_ledger");
            fresh.execSQL("DELETE FROM demo_wallet");
            fresh.execSQL("DELETE FROM owned_items");
            fresh.execSQL("CREATE TRIGGER reject_starter BEFORE INSERT ON coin_ledger "
                    +"BEGIN SELECT RAISE(ABORT,'starter failure'); END");
            try{new DemoShopStore(fresh);fail("Starter history failure must roll back the seed");}
            catch(SQLiteConstraintException expected){}
            assertEquals(0L,android.database.DatabaseUtils.queryNumEntries(fresh,"demo_wallet"));
            assertEquals(0L,android.database.DatabaseUtils.queryNumEntries(fresh,"owned_items"));
            assertEquals(0L,android.database.DatabaseUtils.queryNumEntries(fresh,"coin_ledger"));
            assertFalse(fresh.inTransaction());
            fresh.execSQL("DROP TRIGGER reject_starter");
            DemoShopStore retried=new DemoShopStore(fresh);
            assertEquals(500,retried.balance());
            assertEquals(8L,android.database.DatabaseUtils.queryNumEntries(fresh,"owned_items"));
            assertEquals(1,retried.history().size());
            assertEquals("starter",retried.history().get(0).source);
        }
    }

    public void testReinitializationDoesNotRefillOrReseed() {
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("umbrella"));
        database.execSQL("INSERT INTO appearance VALUES("
                + "1,'mail','crown','eyepatch','red_scarf','rocket_pack','none')");
        DemoShopStore.createTables(database);
        DemoShopStore reopened = new DemoShopStore(database);
        assertEquals(380, reopened.balance());
        assertTrue(reopened.owns("umbrella"));
        assertFalse(reopened.owns("crown"));
        assertFalse(reopened.owns("eyepatch"));
        assertFalse(reopened.owns("red_scarf"));
        assertFalse(reopened.owns("rocket_pack"));
        assertEquals(1, count("demo_wallet"));
        assertEquals(9, count("owned_items"));
        assertEquals(1, count("purchases"));
    }

    public void testFirstSeedPreservesEquippedItemsInEverySlot() {
        try (SQLiteDatabase migration = SQLiteDatabase.create(null)) {
            migration.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY, character TEXT,"
                    + "hat TEXT, face TEXT, body TEXT, back TEXT, prop TEXT)");
            migration.execSQL("INSERT INTO appearance VALUES("
                    + "1,'mail','crown','star_glasses','red_scarf','backpack','umbrella')");
            migration.execSQL("INSERT INTO appearance VALUES("
                    + "2,'video','beanie','none','bow_tie','angel_wings','umbrella')");
            DemoShopStore migrated = new DemoShopStore(migration);
            String[] equipped = {"crown", "star_glasses", "red_scarf", "backpack", "umbrella",
                    "beanie", "bow_tie", "angel_wings"};
            for (String id : equipped) {
                assertTrue(id, migrated.owns(id));
                assertEquals(DemoShopStore.PurchaseResult.ALREADY_OWNED, migrated.purchase(id));
            }
            assertEquals(500, migrated.balance());
            try (Cursor cursor = migration.rawQuery("SELECT COUNT(*) FROM owned_items", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(16, cursor.getInt(0));
            }
            try (Cursor cursor = migration.rawQuery("SELECT COUNT(*) FROM purchases", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0, cursor.getInt(0));
            }
            try (Cursor cursor = migration.rawQuery("SELECT * FROM appearance ORDER BY friend", null)) {
                assertEquals(2, cursor.getCount());
                assertTrue(cursor.moveToFirst());
                assertEquals("crown", cursor.getString(cursor.getColumnIndexOrThrow("hat")));
                assertEquals("star_glasses", cursor.getString(cursor.getColumnIndexOrThrow("face")));
                assertEquals("red_scarf", cursor.getString(cursor.getColumnIndexOrThrow("body")));
                assertEquals("backpack", cursor.getString(cursor.getColumnIndexOrThrow("back")));
                assertEquals("umbrella", cursor.getString(cursor.getColumnIndexOrThrow("prop")));
                assertTrue(cursor.moveToNext());
                assertEquals("beanie", cursor.getString(cursor.getColumnIndexOrThrow("hat")));
                assertEquals("bow_tie", cursor.getString(cursor.getColumnIndexOrThrow("body")));
                assertEquals("angel_wings", cursor.getString(cursor.getColumnIndexOrThrow("back")));
            }
        }
    }

    public void testLegacyAppearanceWithoutNewSlotsIsPreserved() {
        try (SQLiteDatabase migration = SQLiteDatabase.create(null)) {
            migration.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY,"
                    + "hat TEXT, face TEXT, prop TEXT)");
            migration.execSQL("INSERT INTO appearance VALUES(1,'crown','eyepatch','umbrella')");
            DemoShopStore migrated = new DemoShopStore(migration);
            assertTrue(migrated.owns("crown"));
            assertTrue(migrated.owns("eyepatch"));
            assertTrue(migrated.owns("umbrella"));
            assertEquals(500, migrated.balance());
        }
    }

    public void testReceiptFailureRollsBackDebitAndOwnership() {
        database.execSQL("CREATE TRIGGER reject_purchase BEFORE INSERT ON purchases "
                + "BEGIN SELECT RAISE(ABORT,'injected receipt failure'); END");
        try {
            store.purchase("crown");
            fail("The receipt failure must propagate");
        } catch (SQLiteConstraintException expected) {
            // The real SQLite trigger fires after the debit and ownership insertion.
        }
        assertEquals(500, store.balance());
        assertFalse(store.owns("crown"));
        assertEquals(8, count("owned_items"));
        assertEquals(0, count("purchases"));
        assertEquals(1, count("coin_ledger"));
        assertFalse(database.inTransaction());
        database.execSQL("DROP TRIGGER reject_purchase");
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("crown"));
        assertEquals(410, store.balance());
        assertTrue(store.owns("crown"));
        assertEquals(9, count("owned_items"));
        assertEquals(1, count("purchases"));
        assertEquals(2, count("coin_ledger"));
    }

    public void testDatabaseConstraintsProtectWalletAndUniqueReceipt() {
        try {
            database.execSQL("UPDATE demo_wallet SET balance=-1 WHERE id=1");
            fail("Negative balances must be rejected");
        } catch (SQLiteConstraintException expected) { }
        try {
            database.execSQL("INSERT INTO demo_wallet VALUES(2,500)");
            fail("Only one wallet may exist");
        } catch (SQLiteConstraintException expected) { }
        assertEquals(DemoShopStore.PurchaseResult.PURCHASED, store.purchase("crown"));
        try {
            database.execSQL("INSERT INTO purchases VALUES('crown',90)");
            fail("An item can only have one receipt");
        } catch (SQLiteConstraintException expected) { }
        try {
            database.execSQL("INSERT INTO owned_items VALUES('crown')");
            fail("Ownership must be unique");
        } catch (SQLiteConstraintException expected) { }
        assertEquals(410, store.balance());
        assertEquals(1, count("demo_wallet"));
        assertEquals(9, count("owned_items"));
        assertEquals(1, count("purchases"));
    }

    private int count(String table) {
        try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
