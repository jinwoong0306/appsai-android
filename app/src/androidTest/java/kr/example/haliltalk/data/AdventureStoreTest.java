package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

/** [U2 | 모험 검증] 고정 날짜·실제 SQLite로 추가 마이그레이션과 재집계를 검사한다. */
public final class AdventureStoreTest extends InstrumentationTestCase {
    private SQLiteDatabase db;
    private AdventureStore store;
    private JourneyStore journeys;

    @Override protected void setUp() throws Exception {
        super.setUp();db=SQLiteDatabase.create(null);db.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {helper.onCreate(db);}
        db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES(7,'qa.adventure','모험',41),(9,'qa.other','다른 친구',3)");
        store=new AdventureStore(db);journeys=new JourneyStore(db);
    }
    @Override protected void tearDown() throws Exception {try {db.close();} finally {super.tearDown();}}

    public void testChoiceReopensAndProgressCountsOnlyDistinctActualDatesWithinBounds() {
        assertNull(store.choice(7));assertEquals(0,store.activeDays(7,"2026-11-16"));
        store.choose(7,3,"2026-11-12");
        record(7,"2026-11-11","done");record(7,"2026-11-12","done");record(7,"2026-11-13","rest");
        record(7,"2026-11-14","partial");record(7,"2026-11-15","planned");record(7,"2026-11-17","done");
        record(9,"2026-11-16","done");
        assertEquals(2,store.activeDays(7,"2026-11-16"));
        journeys.review(7,"2026-11-12","partial","변경",99);
        assertEquals(2,new AdventureStore(db).activeDays(7,"2026-11-16"));
        assertEquals(3,new AdventureStore(db).choice(7).days);
        assertEquals("2026-11-12",store.choice(7).startedDay);
        journeys.delete(7,"2026-11-14");assertEquals(1,store.activeDays(7,"2026-11-16"));
        journeys.review(7,"2026-11-12","rest","휴식",100);assertEquals(0,store.activeDays(7,"2026-11-16"));
        assertEquals(500,new kr.example.haliltalk.shop.DemoShopStore(db).balance());
    }

    public void testChangingRouteKeepsHistoryAndLifetimePostcardsAndCascadesOnFriendDeletion() {
        record(7,"2026-11-12","done");store.choose(7,3,"2026-11-12");
        store.choose(7,14,"2026-11-16");
        assertEquals(14,store.choice(7).days);assertEquals(0,store.activeDays(7,"2026-11-16"));
        assertEquals(1,journeys.summary(7,"2026-11-16").totalActiveDays);
        assertNotNull(journeys.entry(7,"2026-11-12"));assertNull(store.choice(9));
        db.delete("friends","id=7",null);assertNull(store.choice(7));
        db.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.reused','새 친구')");
        assertNull(store.choice(7));assertEquals(0,store.activeDays(7,"2026-11-16"));
    }

    public void testChoiceFailureRollsBackAndAllowsDirectRetryWithoutChangingEconomy() {
        store.choose(7,3,"2026-11-16");
        db.execSQL("CREATE TRIGGER reject_adventure BEFORE UPDATE ON adventure_choices BEGIN SELECT RAISE(ABORT,'qa');END");
        try {store.choose(7,7,"2026-11-16");fail("Actual write must fail");}
        catch(android.database.sqlite.SQLiteException expected) {}
        assertEquals(3,store.choice(7).days);
        db.execSQL("DROP TRIGGER reject_adventure");store.choose(7,7,"2026-11-16");assertEquals(7,store.choice(7).days);
        db.execSQL("CREATE TRIGGER ignore_adventure BEFORE UPDATE ON adventure_choices"
                +" BEGIN SELECT RAISE(IGNORE); END");
        try {store.choose(7,14,"2026-11-16");fail("Ignored write must not report success");}
        catch(android.database.sqlite.SQLiteException expected) {}
        assertEquals(7,store.choice(7).days);
        db.execSQL("DROP TRIGGER ignore_adventure");
        try(Cursor c=db.rawQuery("SELECT xp FROM friends WHERE id=7",null)){c.moveToFirst();assertEquals(41,c.getInt(0));}
    }

    public void testInvalidChoicesAndSeparateDatabaseDoNotCreateSelection() {
        for(int days:new int[]{0,1,4,15}) {
            try {store.choose(7,days,"2026-11-16");fail("Unsupported route");}catch(IllegalArgumentException expected){}
        }
        for(String day:new String[]{"2026-02-30","2026-1-01","bad"}) {
            try {store.choose(7,3,day);fail("Invalid day");}catch(IllegalArgumentException expected){}
        }
        try {store.choose(88,3,"2026-11-16");fail("Missing friend");}
        catch(android.database.sqlite.SQLiteConstraintException expected){}
        assertNull(store.choice(7));store.choose(7,3,"2026-11-16");
        try(SQLiteDatabase other=SQLiteDatabase.create(null)){
            AdventureStore.createTables(other);assertNull(new AdventureStore(other).choice(7));
        }
    }

    private void record(int friend,String day,String outcome) {
        journeys.savePlan(friend,day,"책 한 쪽",null,null,1);
        if(!outcome.equals("planned"))journeys.review(friend,day,outcome,"",2);
    }
}
