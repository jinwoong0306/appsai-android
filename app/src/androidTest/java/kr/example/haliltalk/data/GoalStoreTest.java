package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

public final class GoalStoreTest extends InstrumentationTestCase {
    SQLiteDatabase db;
    GoalStore store;
    @Override protected void setUp() throws Exception {
        super.setUp();
        db=SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(7,'qa.goals','Friend','calm',41)");
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(9,'qa.other','Other','kind',3)");
        store=new GoalStore(db);
    }
    @Override protected void tearDown() throws Exception { db.close();super.tearDown(); }

    public void testCreateEditCompleteReopenDeleteDoesNotAwardXp() {
        long id=store.save(7,0,"Read ten pages",null);
        assertEquals(1,store.list(7).size());
        assertNull(store.list(7).get(0).dueAt);
        store.save(7,id,"Read twenty pages",123456L);
        assertEquals(Long.valueOf(123456L),store.list(7).get(0).dueAt);
        store.complete(9,id,true);
        assertFalse(store.list(7).get(0).completed);
        store.complete(7,id,true);
        assertTrue(new GoalStore(db).list(7).get(0).completed);
        store.complete(7,id,false);
        assertFalse(store.list(7).get(0).completed);
        try(Cursor c=db.rawQuery("SELECT xp FROM friends WHERE id=7",null)) {
            assertTrue(c.moveToFirst());assertEquals(41,c.getInt(0));
        }
        store.delete(9,id);assertEquals(1,store.list(7).size());
        store.delete(7,id);assertTrue(store.list(7).isEmpty());
    }
    public void testInvalidTitlesAndFriendForeignKeyFailWithoutSaving() {
        for(String title:new String[]{"  ","x".repeat(121),"Read\nbook","bad\uD800"}) {
            try { store.save(7,0,title,null);fail("Invalid title accepted"); }
            catch(IllegalArgumentException expected) { }
        }
        for(long due:new long[]{-1,Long.MAX_VALUE}) {
            try {store.save(7,0,"Invalid due",due);fail("Invalid date accepted");}
            catch(IllegalArgumentException expected) { }
        }
        try { store.save(100,0,"Unknown friend",null);fail("Unknown friend accepted"); }
        catch(android.database.sqlite.SQLiteConstraintException expected) { }
        assertTrue(store.list(7).isEmpty());
    }
    public void testPurposeSelectionAndContextRequireExplicitConsentAndStayFriendScoped() {
        store.setPurposes(7,new boolean[]{true,false,true,false,false});
        store.save(7,0,"My goal",null);
        store.save(9,0,"Other goal",null);
        long done=store.save(7,0,"Finished goal",null);store.complete(7,done,true);
        assertEquals("",store.chatSummary(7));
        store.setSharing(7,true);
        assertTrue(store.chatSummary(7).contains("My goal"));
        assertTrue(store.chatSummary(7).contains(GoalStore.PURPOSES[0]));
        assertFalse(store.chatSummary(7).contains("Other goal"));
        assertFalse(store.chatSummary(7).contains("Finished goal"));
        store.setSharing(7,false);
        assertEquals("",store.chatSummary(7));
    }
    public void testVersionFiveMigrationPreservesExistingValuesAndAddsEmptyGoals() {
        try(SQLiteDatabase old=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            old.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY, package TEXT UNIQUE, name TEXT, personality TEXT, xp INTEGER DEFAULT 0)");
            old.execSQL("INSERT INTO friends VALUES(7,'qa.old','Kept','calm',42)");
            helper.onUpgrade(old,5,6);
            try(Cursor c=old.rawQuery("SELECT name,xp,purpose,share_goals FROM friends",null)) {
                assertTrue(c.moveToFirst());assertEquals("Kept",c.getString(0));
                assertEquals(42,c.getInt(1));assertEquals("",c.getString(2));assertEquals(0,c.getInt(3));
            }
            assertTrue(new GoalStore(old).list(7).isEmpty());
        }
    }
}
