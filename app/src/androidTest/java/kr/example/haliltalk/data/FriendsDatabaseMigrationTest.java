package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

public class FriendsDatabaseMigrationTest extends InstrumentationTestCase {
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
