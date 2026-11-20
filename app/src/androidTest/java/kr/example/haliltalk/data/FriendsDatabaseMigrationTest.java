package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

public class FriendsDatabaseMigrationTest extends InstrumentationTestCase {
    public void testActualVersionElevenAddsNullableDayAndIndependentRewardReceipts() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            db.setForeignKeyConstraintsEnabled(true);
            db.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,"
                    +"name TEXT,personality TEXT,xp INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,"
                    +"content TEXT,is_user INTEGER,created INTEGER)");
            helper.onUpgrade(db,1,10);
            // Actual shipped v11 schema, deliberately without v12's day column.
            db.execSQL("CREATE TABLE coin_ledger(id INTEGER PRIMARY KEY,event_key TEXT NOT NULL UNIQUE,"
                    +"source TEXT NOT NULL CHECK(source IN ('opening','starter','purchase','quest')),"
                    +"amount INTEGER NOT NULL CHECK(amount BETWEEN -2147483647 AND 2147483647),"
                    +"balance INTEGER NOT NULL CHECK(balance BETWEEN 0 AND 2147483647),"
                    +"created_at INTEGER,item_id TEXT,"
                    +"CHECK((source='opening' AND amount=0 AND created_at IS NULL)"
                    +" OR (source!='opening' AND created_at IS NOT NULL)))");
            db.execSQL("INSERT INTO coin_ledger VALUES(1,'opening','opening',0,500,NULL,NULL)");
            db.execSQL("INSERT INTO coin_ledger VALUES(2,'purchase:crown','purchase',-90,410,123,'crown')");
            db.execSQL("UPDATE demo_wallet SET balance=410 WHERE id=1");
            db.execSQL("INSERT INTO owned_items VALUES('crown')");
            db.execSQL("INSERT INTO purchases VALUES('crown',90)");
            db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES(7,'qa.v12','Keep',41)");
            db.execSQL("INSERT INTO messages VALUES(11,7,'Keep message',0,1000)");
            db.execSQL("INSERT INTO appearance(friend,character,hat,face,body,back,prop)"
                    +" VALUES(7,'reading','beret','none','none','none','none')");
            new kr.example.haliltalk.shop.ShopCollectionStore(db).setTarget("rocket_pack");
            new AdventureStore(db).choose(7,7,"2026-11-16");
            JourneyStore journeys=new JourneyStore(db);
            journeys.savePlan(7,"2026-11-16","Keep action",null,null,1);
            journeys.review(7,"2026-11-16","done","Keep review",2);
            long goal=new GoalStore(db).save(7,0,"Keep goal",123456L);
            java.util.Set<String> before=tableNames(db);
            helper.onUpgrade(db,11,12);helper.onUpgrade(db,11,12);
            java.util.Set<String> added=tableNames(db);added.removeAll(before);
            assertEquals(java.util.Collections.singleton("quest_reward_receipts"),added);
            try(Cursor foreignKeys=db.rawQuery("PRAGMA foreign_key_list(quest_reward_receipts)",null)) {
                assertEquals(0,foreignKeys.getCount());
            }
            try(Cursor columns=db.rawQuery("PRAGMA table_info(coin_ledger)",null)) {
                int found=0;
                while(columns.moveToNext())if("day".equals(columns.getString(1))) {
                    found++;assertEquals("TEXT",columns.getString(2));assertEquals(0,columns.getInt(3));
                }
                assertEquals(1,found);
            }
            kr.example.haliltalk.shop.DemoShopStore wallet=new kr.example.haliltalk.shop.DemoShopStore(db);
            assertEquals(410,wallet.balance());assertTrue(wallet.owns("crown"));
            assertEquals(1L,android.database.DatabaseUtils.queryNumEntries(db,"purchases"));
            assertEquals(2,wallet.history().size());
            assertEquals("purchase:crown",wallet.history().get(0).eventKey);
            assertEquals(Long.valueOf(123),wallet.history().get(0).createdAt);
            assertNull(wallet.history().get(0).day);assertNull(wallet.history().get(1).createdAt);
            assertEquals("rocket_pack",new kr.example.haliltalk.shop.ShopCollectionStore(db).target());
            assertEquals(7,new AdventureStore(db).choice(7).days);
            assertEquals(goal,new GoalStore(db).list(7).get(0).id);
            assertFalse(new GoalStore(db).list(7).get(0).completed);
            assertEquals("Keep review",journeys.entry(7,"2026-11-16").note);
            assertEquals(41L,android.database.DatabaseUtils.longForQuery(db,"SELECT xp FROM friends WHERE id=7",null));
            assertEquals("Keep message",android.database.DatabaseUtils.stringForQuery(db,"SELECT content FROM messages WHERE id=11",null));
            assertEquals("beret",android.database.DatabaseUtils.stringForQuery(db,"SELECT hat FROM appearance WHERE friend=7",null));
            assertTrue(new QuestRewardStore(db).claim(7,"2026-11-16"));
            assertEquals(430,wallet.balance());assertEquals(3,wallet.history().size());
            journeys.delete(7,"2026-11-16");db.delete("friends","id=7",null);
            assertEquals(1L,android.database.DatabaseUtils.queryNumEntries(db,"quest_reward_receipts"));
        }
    }

    public void testVersionTenAddsOnlyLedgerWithOpeningSnapshotAndKeepsExistingState() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            db.setForeignKeyConstraintsEnabled(true);
            db.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,"
                    +"name TEXT,personality TEXT,xp INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,"
                    +"content TEXT,is_user INTEGER,created INTEGER)");
            helper.onUpgrade(db,1,10);
            assertFalse(tableNames(db).contains("coin_ledger"));
            db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES(7,'qa.ledger.migration','Kept',41)");
            db.execSQL("INSERT INTO messages VALUES(11,7,'Keep message',0,1000)");
            db.execSQL("INSERT INTO appearance(friend,character,hat,face,body,back,prop)"
                    +" VALUES(7,'reading','beret','none','none','none','none')");
            db.execSQL("UPDATE demo_wallet SET balance=410 WHERE id=1");
            db.execSQL("INSERT INTO owned_items VALUES('crown')");
            db.execSQL("INSERT INTO purchases VALUES('crown',90)");
            AdventureStore adventure=new AdventureStore(db);adventure.choose(7,7,"2026-11-16");
            kr.example.haliltalk.shop.ShopCollectionStore wishlist=new kr.example.haliltalk.shop.ShopCollectionStore(db);
            wishlist.setTarget("rocket_pack");
            java.util.Set<String> before=tableNames(db);
            helper.onUpgrade(db,10,11);
            java.util.Set<String> added=tableNames(db);added.removeAll(before);
            assertEquals(java.util.Collections.singleton("coin_ledger"),added);
            kr.example.haliltalk.shop.DemoShopStore wallet=new kr.example.haliltalk.shop.DemoShopStore(db);
            assertEquals(410,wallet.balance());assertTrue(wallet.owns("crown"));
            assertEquals(9L,android.database.DatabaseUtils.queryNumEntries(db,"owned_items"));
            assertEquals(1L,android.database.DatabaseUtils.queryNumEntries(db,"purchases"));
            assertEquals(90L,android.database.DatabaseUtils.longForQuery(db,"SELECT price FROM purchases WHERE item_id='crown'",null));
            assertEquals("rocket_pack",wishlist.target());assertEquals(7,adventure.choice(7).days);
            assertEquals(41L,android.database.DatabaseUtils.longForQuery(db,"SELECT xp FROM friends WHERE id=7",null));
            try(Cursor c=db.rawQuery("SELECT content FROM messages WHERE id=11",null)){
                assertTrue(c.moveToFirst());assertEquals("Keep message",c.getString(0));
            }
            try(Cursor c=db.rawQuery("SELECT character,hat FROM appearance WHERE friend=7",null)){
                assertTrue(c.moveToFirst());assertEquals("reading",c.getString(0));assertEquals("beret",c.getString(1));
            }
            assertEquals(1,wallet.history().size());
            kr.example.haliltalk.shop.CoinLedger.Entry opening=wallet.history().get(0);
            assertEquals("opening",opening.source);assertEquals("opening",opening.eventKey);
            assertEquals(0,opening.amount);assertEquals(410,opening.balance);
            assertNull(opening.createdAt);assertNull(opening.itemId);
            helper.onUpgrade(db,10,11);
            assertEquals(1,wallet.history().size());
            assertEquals(kr.example.haliltalk.shop.DemoShopStore.PurchaseResult.PURCHASED,wallet.purchase("umbrella"));
            assertEquals(290,wallet.balance());assertEquals(2,wallet.history().size());
            assertEquals("purchase:umbrella",wallet.history().get(0).eventKey);
            assertEquals("opening",wallet.history().get(1).source);
        }
    }

    public void testVersionNineAddsOnlyWishlistAndPreservesEconomyAndAdventure() {
        try(SQLiteDatabase db=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            db.setForeignKeyConstraintsEnabled(true);
            db.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,"
                    +"name TEXT,personality TEXT,xp INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,"
                    +"content TEXT,is_user INTEGER,created INTEGER)");
            helper.onUpgrade(db,1,9);
            db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES(7,'qa.wishlist.migration','Kept',41)");
            db.execSQL("INSERT INTO messages VALUES(11,7,'Keep message',0,1000)");
            db.execSQL("INSERT INTO appearance(friend,character,hat,face,body,back,prop)"
                    +" VALUES(7,'reading','beret','none','none','none','none')");
            AdventureStore adventure=new AdventureStore(db);adventure.choose(7,7,"2026-11-16");
            db.execSQL("UPDATE demo_wallet SET balance=410 WHERE id=1");
            db.execSQL("INSERT INTO owned_items VALUES('crown')");
            db.execSQL("INSERT INTO purchases VALUES('crown',90)");
            assertFalse(tableNames(db).contains("coin_ledger"));
            java.util.Set<String> before=tableNames(db);
            helper.onUpgrade(db,9,10);
            java.util.Set<String> added=tableNames(db);added.removeAll(before);
            assertEquals(java.util.Collections.singleton("shop_wishlist"),added);
            assertFalse(tableNames(db).contains("coin_ledger"));
            kr.example.haliltalk.shop.ShopCollectionStore wishlist=new kr.example.haliltalk.shop.ShopCollectionStore(db);
            assertNull(wishlist.target());wishlist.setTarget("rocket_pack");
            assertEquals(410L,android.database.DatabaseUtils.longForQuery(
                    db,"SELECT balance FROM demo_wallet WHERE id=1",null));
            assertEquals(1L,android.database.DatabaseUtils.longForQuery(
                    db,"SELECT COUNT(*) FROM owned_items WHERE item_id='crown'",null));
            assertEquals(0L,android.database.DatabaseUtils.longForQuery(
                    db,"SELECT COUNT(*) FROM owned_items WHERE item_id='rocket_pack'",null));
            assertEquals(7,adventure.choice(7).days);
            assertEquals(1L,android.database.DatabaseUtils.queryNumEntries(db,"purchases"));
            assertEquals(90L,android.database.DatabaseUtils.longForQuery(db,"SELECT price FROM purchases WHERE item_id='crown'",null));
            assertEquals(41L,android.database.DatabaseUtils.longForQuery(db,"SELECT xp FROM friends WHERE id=7",null));
            try(Cursor c=db.rawQuery("SELECT content FROM messages WHERE id=11",null)){
                assertTrue(c.moveToFirst());assertEquals("Keep message",c.getString(0));
            }
            try(Cursor c=db.rawQuery("SELECT character,hat FROM appearance WHERE friend=7",null)){
                assertTrue(c.moveToFirst());assertEquals("reading",c.getString(0));assertEquals("beret",c.getString(1));
            }
        }
    }

    public void testFreshSchemaAndSequentialUpgradeHaveSameTables() {
        try(SQLiteDatabase fresh=SQLiteDatabase.create(null);SQLiteDatabase upgraded=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            helper.onCreate(fresh);
            upgraded.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,"
                    +"name TEXT,personality TEXT,xp INTEGER DEFAULT 0)");
            upgraded.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,"
                    +"content TEXT,is_user INTEGER,created INTEGER)");
            helper.onUpgrade(upgraded,1,8);
            helper.onUpgrade(upgraded,8,9);
            helper.onUpgrade(upgraded,9,10);
            helper.onUpgrade(upgraded,10,11);
            helper.onUpgrade(upgraded,11,12);
            assertEquals(tableNames(fresh),tableNames(upgraded));
            assertTrue(tableNames(fresh).contains("coin_ledger"));
            assertTrue(tableNames(fresh).contains("quest_reward_receipts"));
            for(String table:tableNames(fresh)) {
                try(Cursor expected=fresh.rawQuery("PRAGMA table_info("+table+")",null);
                        Cursor actual=upgraded.rawQuery("PRAGMA table_info("+table+")",null)) {
                    assertEquals(table,expected.getCount(),actual.getCount());
                    while(expected.moveToNext()) {
                        assertTrue(actual.moveToNext());
                        for(int column=0;column<expected.getColumnCount();column++)
                            assertEquals(table,expected.getString(column),actual.getString(column));
                    }
                }
            }
            assertFalse(tableNames(fresh).contains("quest_rewards"));
            assertEquals("starter",new kr.example.haliltalk.shop.DemoShopStore(fresh).history().get(0).source);
            assertEquals("opening",new kr.example.haliltalk.shop.DemoShopStore(upgraded).history().get(0).source);
            assertNull(new kr.example.haliltalk.shop.ShopCollectionStore(fresh).target());
            assertNull(new kr.example.haliltalk.shop.ShopCollectionStore(upgraded).target());
        }
    }

    public void testVersionEightAddsOnlyAdventureChoicesAndKeepsExistingRows() {
        try(SQLiteDatabase database=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            database.setForeignKeyConstraintsEnabled(true);
            // Build an actual v8 fixture, even after a later onCreate adds shop v10 tables.
            database.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,"
                    +"name TEXT,personality TEXT,xp INTEGER DEFAULT 0)");
            database.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,"
                    +"content TEXT,is_user INTEGER,created INTEGER)");
            helper.onUpgrade(database,1,8);
            database.execSQL("INSERT INTO friends(id,package,name,xp) VALUES(7,'qa.migration.adventure','Kept',41)");
            long goal=new GoalStore(database).save(7,0,"Keep reminder goal",123456L);
            JourneyStore journeys=new JourneyStore(database);
            journeys.savePlan(7,"2026-11-16","Read one page","","",1);
            journeys.review(7,"2026-11-16","partial","Keep reflection",2);
            java.util.Set<String> before=tableNames(database);
            helper.onUpgrade(database,8,9);
            java.util.Set<String> added=tableNames(database);added.removeAll(before);
            assertEquals(java.util.Collections.singleton("adventure_choices"),added);
            AdventureStore adventures=new AdventureStore(database);
            assertNull(adventures.choice(7));
            adventures.choose(7,7,"2026-11-16");
            assertEquals(1,adventures.activeDays(7,"2026-11-16"));
            // A later migration must not attempt to create the v9 table again.
            helper.onUpgrade(database,9,10);
            assertEquals(7,adventures.choice(7).days);
            assertEquals("partial",journeys.entry(7,"2026-11-16").outcome);
            assertEquals("Keep reflection",journeys.entry(7,"2026-11-16").note);
            assertEquals(goal,new GoalStore(database).list(7).get(0).id);
            assertEquals(Long.valueOf(123456L),new GoalStore(database).list(7).get(0).dueAt);
            try(Cursor c=database.rawQuery("SELECT xp FROM friends WHERE id=7",null)){
                assertTrue(c.moveToFirst());assertEquals(41,c.getInt(0));
            }
            assertEquals(500,new kr.example.haliltalk.shop.DemoShopStore(database).balance());
            database.delete("friends","id=7",null);
            assertNull(adventures.choice(7));
        }
    }

    private java.util.Set<String> tableNames(SQLiteDatabase database) {
        java.util.Set<String> names=new java.util.HashSet<>();
        try(Cursor c=database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null)){
            while(c.moveToNext())names.add(c.getString(0));
        }
        return names;
    }

    public void testVersionFourSeedsDemoWalletAndKeepsEquippedItems() {
        try(SQLiteDatabase database=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            database.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY,character TEXT,hat TEXT,face TEXT,prop TEXT,body TEXT,back TEXT)");
            database.execSQL("INSERT INTO appearance VALUES(7,'chat','crown','round_glasses','gamepad','red_scarf','angel_wings')");
            helper.onUpgrade(database,4,5);
            kr.example.haliltalk.shop.DemoShopStore store=new kr.example.haliltalk.shop.DemoShopStore(database);
            assertEquals(500,store.balance());
            assertTrue(store.owns("crown"));assertTrue(store.owns("red_scarf"));assertTrue(store.owns("angel_wings"));
            try(Cursor cursor=database.rawQuery("SELECT character,hat,body,back FROM appearance WHERE friend=7",null)){
                assertTrue(cursor.moveToFirst());assertEquals("chat",cursor.getString(0));assertEquals("crown",cursor.getString(1));
                assertEquals("red_scarf",cursor.getString(2));assertEquals("angel_wings",cursor.getString(3));
            }
        }
    }
    public void testVersionThreeAddsEmptyBodyAndBackWithoutReplacingAppearance() {
        try(SQLiteDatabase database=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            database.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY,character TEXT,hat TEXT,face TEXT,prop TEXT)");
            database.execSQL("INSERT INTO appearance VALUES(7,'chat','beret','round_glasses','gamepad')");
            helper.onUpgrade(database,3,4);
            try(Cursor cursor=database.rawQuery("SELECT friend,character,hat,face,prop,body,back FROM appearance",null)){
                assertTrue(cursor.moveToFirst());
                assertEquals(7,cursor.getInt(0));assertEquals("chat",cursor.getString(1));
                assertEquals("beret",cursor.getString(2));assertEquals("round_glasses",cursor.getString(3));
                assertEquals("gamepad",cursor.getString(4));
                assertEquals("none",cursor.getString(5));assertEquals("none",cursor.getString(6));
                assertFalse(cursor.moveToNext());
            }
        }
    }
    public void testVersionOneUpgradePreservesFriendsAndMessages() {
        // The helper is never opened: onUpgrade receives only this isolated in-memory database.
        try(SQLiteDatabase database=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            database.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY, package TEXT UNIQUE, name TEXT,"
                    +" personality TEXT, xp INTEGER DEFAULT 0)");
            database.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY, friend INTEGER, content TEXT,"
                    +" is_user INTEGER, created INTEGER)");
            database.execSQL("INSERT INTO friends VALUES(7,'qa.migration.first','First friend','kind',41)");
            database.execSQL("INSERT INTO friends VALUES(9,'qa.migration.second','Second friend','calm',0)");
            database.execSQL("INSERT INTO messages VALUES(11,7,'Original greeting',0,1000)");
            database.execSQL("INSERT INTO messages VALUES(12,7,'Original user message',1,2000)");
            database.execSQL("INSERT INTO messages VALUES(13,9,'Other friend greeting',0,3000)");
            database.setVersion(1);

            database.beginTransaction();
            try {
                helper.onUpgrade(database,1,3);
                database.setVersion(3);
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }

            assertEquals(3,database.getVersion());
            try(Cursor cursor=database.rawQuery("SELECT id,package,name,personality,xp FROM friends ORDER BY id",null)){
                assertEquals(2,cursor.getCount());
                assertTrue(cursor.moveToNext());
                assertEquals(7,cursor.getInt(0));
                assertEquals("qa.migration.first",cursor.getString(1));
                assertEquals("First friend",cursor.getString(2));
                assertEquals("kind",cursor.getString(3));
                assertEquals(41,cursor.getInt(4));
                assertTrue(cursor.moveToNext());
                assertEquals(9,cursor.getInt(0));
                assertEquals("qa.migration.second",cursor.getString(1));
                assertEquals("Second friend",cursor.getString(2));
                assertEquals("calm",cursor.getString(3));
                assertEquals(0,cursor.getInt(4));
            }
            try(Cursor cursor=database.rawQuery("SELECT id,friend,content,is_user,created FROM messages ORDER BY id",null)){
                int[] ids={11,12,13},friends={7,7,9},users={0,1,0};
                String[] contents={"Original greeting","Original user message","Other friend greeting"};
                assertEquals(3,cursor.getCount());
                for(int i=0;i<ids.length;i++){
                    assertTrue(cursor.moveToNext());
                    assertEquals(ids[i],cursor.getInt(0));
                    assertEquals(friends[i],cursor.getInt(1));
                    assertEquals(contents[i],cursor.getString(2));
                    assertEquals(users[i],cursor.getInt(3));
                    assertEquals((i+1)*1000L,cursor.getLong(4));
                }
            }
            try(Cursor cursor=database.rawQuery("SELECT * FROM appearance",null)){
                assertEquals("Legacy friends keep a missing appearance for the UI fallback",0,cursor.getCount());
            }
            try(Cursor cursor=database.rawQuery("SELECT * FROM chat_requests",null)){
                assertEquals(0,cursor.getCount());
            }
            database.execSQL("INSERT INTO appearance(friend,character,hat,face,prop)"
                    +" VALUES(7,'calendar','beret','round_glasses','gamepad')");
            try(Cursor cursor=database.rawQuery("SELECT friend,character,hat,face,prop FROM appearance",null)){
                assertTrue(cursor.moveToFirst());
                assertEquals(7,cursor.getInt(0));
                assertEquals("calendar",cursor.getString(1));
                assertEquals("beret",cursor.getString(2));
                assertEquals("round_glasses",cursor.getString(3));
                assertEquals("gamepad",cursor.getString(4));
                assertFalse(cursor.moveToNext());
            }
        }
    }

    public void testVersionTwoUpgradeKeepsAppearanceAndAddsRequestUniqueness() {
        try(SQLiteDatabase database=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            database.setForeignKeyConstraintsEnabled(true);
            database.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY, package TEXT UNIQUE, name TEXT,"
                    +" personality TEXT, xp INTEGER DEFAULT 0)");
            database.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY, friend INTEGER, content TEXT,"
                    +" is_user INTEGER, created INTEGER)");
            helper.onUpgrade(database,1,2);
            database.execSQL("INSERT INTO friends VALUES(7,'qa.migration','Kept','calm',9)");
            database.execSQL("INSERT INTO messages VALUES(11,7,'Kept message',1,123)");
            database.execSQL("INSERT INTO appearance VALUES(7,'calendar','beret','round_glasses','gamepad')");
            helper.onUpgrade(database,2,3);
            try(Cursor cursor=database.rawQuery("SELECT character,hat,face,prop FROM appearance WHERE friend=7",null)){
                assertTrue(cursor.moveToFirst());
                assertEquals("calendar",cursor.getString(0));assertEquals("beret",cursor.getString(1));
                assertEquals("round_glasses",cursor.getString(2));assertEquals("gamepad",cursor.getString(3));
            }
            database.execSQL("INSERT INTO chat_requests(user_message,friend,state) VALUES(11,7,'failed')");
            database.execSQL("INSERT INTO messages VALUES(12,7,'Another message',1,124)");
            try {
                database.execSQL("INSERT INTO chat_requests(user_message,friend,state) VALUES(12,7,'pending')");
                fail("Two unresolved requests for one friend must not be stored");
            } catch(android.database.sqlite.SQLiteConstraintException expected) { }
            database.delete("messages","id=11",null);
            try(Cursor cursor=database.rawQuery("SELECT COUNT(*) FROM chat_requests",null)){
                assertTrue(cursor.moveToFirst());assertEquals(0,cursor.getInt(0));
            }
        }
    }
}
