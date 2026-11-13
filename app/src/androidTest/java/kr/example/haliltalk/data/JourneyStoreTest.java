package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import java.util.List;

/** [U2 | 이어가기] 실제 계정 DB·시계·네트워크를 사용하지 않는 저장 회귀. */
public final class JourneyStoreTest extends InstrumentationTestCase {
    private SQLiteDatabase db;
    private JourneyStore store;

    @Override protected void setUp() throws Exception {
        super.setUp();
        db=SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(7,'qa.journey','Friend','calm',41)");
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(9,'qa.other','Other','kind',3)");
        store=new JourneyStore(db);
    }

    @Override protected void tearDown() throws Exception {
        try { db.close(); } finally { super.tearDown(); }
    }

    public void testPlanAndReviewAreIdempotentAndKeepReviewsWithoutRewards() {
        String before=legacySnapshot(db);
        store.savePlan(7,"2026-11-09"," Read ten pages "," After dinner "," Reading ",100);
        JourneyStore.Entry planned=store.entry(7,"2026-11-09");
        assertEquals(7,planned.friend);
        assertEquals("2026-11-09",planned.day);
        assertEquals("Read ten pages",planned.action);
        assertEquals("After dinner",planned.cue);
        assertEquals("Reading",planned.goalTitle);
        assertEquals("planned",planned.outcome);
        assertEquals("",planned.note);
        assertEquals(100L,planned.updatedAt);
        store.savePlan(7,"2026-11-09","Read twenty pages",null,null,200);
        store.review(7,"2026-11-09","partial"," Ten was enough ",300);
        store.review(7,"2026-11-09","partial"," Ten was enough ",300);
        store.savePlan(7,"2026-11-09","Read five pages","After tea","Reading",400);
        JourneyStore.Entry reopened=new JourneyStore(db).entry(7,"2026-11-09");
        assertEquals("Read five pages",reopened.action);
        assertEquals("After tea",reopened.cue);
        assertEquals("Reading",reopened.goalTitle);
        assertEquals("partial",reopened.outcome);
        assertEquals("Ten was enough",reopened.note);
        assertEquals(400L,reopened.updatedAt);
        assertEquals(1,store.recent(7,"2026-11-09",10).size());
        JourneyStore.Summary summary=store.summary(7,"2026-11-09");
        assertSummary(summary,1,1,1,0,1,0,1);
        assertEquals(before,legacySnapshot(db));
    }

    public void testWeeklyBoundaryDistinctActivityDatesAndFutureExclusion() {
        reviewed(7,"2026-11-02","done");
        reviewed(7,"2026-11-03","done");
        reviewed(9,"2026-11-03","partial");
        reviewed(7,"2026-11-04","partial");
        reviewed(7,"2026-11-05","rest");
        store.savePlan(7,"2026-11-06","Read",null,null,10);
        store.savePlan(7,"2026-11-09","Read",null,null,10);
        reviewed(9,"2026-11-09","done");
        reviewed(7,"2026-11-10","done");
        assertSummary(store.summary(0,"2026-11-09"),2,1,3,2,2,1,4);
        assertSummary(store.summary(7,"2026-11-09"),1,0,2,1,1,1,3);
        assertSummary(store.summary(9,"2026-11-09"),1,1,2,1,1,0,2);
        assertSummary(store.summary(0,"2026-11-01"),0,0,0,0,0,0,0);
    }

    public void testRestIsReviewedButNotAnActiveDay() {
        reviewed(7,"2026-11-09","rest");
        assertSummary(store.summary(0,"2026-11-09"),1,1,0,0,0,1,0);
        store.review(7,"2026-11-09","done",null,12);
        assertSummary(store.summary(0,"2026-11-09"),1,1,1,1,0,0,1);
        store.review(7,"2026-11-09","rest","Rest chosen",13);
        assertSummary(store.summary(0,"2026-11-09"),1,1,0,0,0,1,0);
        assertEquals("Rest chosen",store.entry(7,"2026-11-09").note);
    }

    public void testRecentIsNewestFirstBoundedAndFriendScoped() {
        store.savePlan(7,"2026-11-07","Older",null,null,999);
        store.savePlan(7,"2026-11-09","Today",null,null,1);
        store.savePlan(7,"2026-11-08","Yesterday",null,null,2);
        store.savePlan(7,"2026-11-10","Future",null,null,3);
        store.savePlan(9,"2026-11-09","Other friend",null,null,4);
        List<JourneyStore.Entry> recent=store.recent(7,"2026-11-09",2);
        assertEquals(2,recent.size());
        assertEquals("2026-11-09",recent.get(0).day);
        assertEquals("2026-11-08",recent.get(1).day);
        assertEquals("Today",recent.get(0).action);
        assertEquals(7,recent.get(1).friend);
        assertNull(store.entry(7,"2026-11-06"));
        store.delete(7,"2026-11-09");
        assertNull(store.entry(7,"2026-11-09"));
        assertEquals("Other friend",store.entry(9,"2026-11-09").action);
        assertEquals(3,store.recent(7,"2026-11-10",10).size());
        assertEquals(3,store.recent(7,"2026-11-10",1000).size());
        assertEquals(3,store.recent(7,"2026-11-10",10000).size());
    }

    public void testInvalidDaysRejectNormalizationAndSqlInput() {
        for(String day:new String[]{null,"","2026-2-03","2026-02-30","2025-02-29",
                "2026-13-01","2026-11-00","2026-11-09 ","2026-11-09T00:00",
                "2026-11-09' OR 1=1","99999-01-01"}) {
            invalid(()->store.savePlan(7,day,"Read",null,null,10));
            invalid(()->store.entry(7,day));
            invalid(()->store.recent(7,day,1));
            invalid(()->store.summary(0,day));
            invalid(()->store.review(7,day,"done",null,11));
            invalid(()->store.delete(7,day));
        }
        store.savePlan(7,"2024-02-29","Leap day",null,null,10);
        assertEquals("Leap day",store.entry(7,"2024-02-29").action);
        reviewed(7,"2026-12-31","done");
        reviewed(7,"2027-01-06","partial");
        assertSummary(store.summary(7,"2027-01-06"),1,1,2,1,1,0,2);
    }

    public void testInvalidTextAndOutcomesLeaveExistingEntryUnchanged() {
        store.savePlan(7,"2026-11-09","Original","Cue","Goal",10);
        for(String action:new String[]{null," ","x".repeat(121),"Read\nbook","bad\uD800"}) {
            invalid(()->store.savePlan(7,"2026-11-09",action,null,null,20));
        }
        for(String cue:new String[]{"x".repeat(81),"bad\ncue","\tbad","bad\uD800"}) {
            invalid(()->store.savePlan(7,"2026-11-09","Read",cue,null,20));
        }
        for(String title:new String[]{"x".repeat(121),"bad\rtitle","bad\uDC00"}) {
            invalid(()->store.savePlan(7,"2026-11-09","Read",null,title,20));
        }
        for(String note:new String[]{"x".repeat(161),"bad\nnote","bad\uD800"}) {
            invalid(()->store.review(7,"2026-11-09","done",note,20));
        }
        for(String outcome:new String[]{null,"","planned","DONE","done' OR 1=1"}) {
            invalid(()->store.review(7,"2026-11-09",outcome,null,20));
        }
        invalid(()->store.recent(7,"2026-11-09",0));
        invalid(()->store.recent(7,"2026-11-09",-1));
        invalid(()->store.recent(7,"2026-11-09",10001));
        JourneyStore.Entry entry=store.entry(7,"2026-11-09");
        assertEquals("Original",entry.action);
        assertEquals("Cue",entry.cue);
        assertEquals("Goal",entry.goalTitle);
        assertEquals("planned",entry.outcome);
        assertEquals("",entry.note);
        assertEquals(10L,entry.updatedAt);
        store.savePlan(7,"2026-11-09","a".repeat(120),"c".repeat(80),"g".repeat(120),30);
        store.review(7,"2026-11-09","done","n".repeat(160),40);
        assertEquals(160,store.entry(7,"2026-11-09").note.length());
    }

    public void testMissingFriendsAndMissingPlansNeverSilentlyWrite() {
        for(int friend:new int[]{-1,0,100}) {
            invalid(()->store.savePlan(friend,"2026-11-09","Read",null,null,10));
            invalid(()->store.review(friend,"2026-11-09","done",null,10));
            invalid(()->store.delete(friend,"2026-11-09"));
        }
        invalid(()->store.review(7,"2026-11-09","done",null,10));
        invalid(()->store.delete(7,"2026-11-09"));
        invalid(()->store.entry(0,"2026-11-09"));
        invalid(()->store.recent(0,"2026-11-09",1));
        invalid(()->store.summary(-1,"2026-11-09"));
        assertNull(store.entry(100,"2026-11-09"));
        assertTrue(store.recent(100,"2026-11-09",10).isEmpty());
        assertSummary(store.summary(100,"2026-11-09"),0,0,0,0,0,0,0);
        assertTrue(store.recent(7,"2026-11-09",10).isEmpty());
    }

    public void testFriendDeletionCascadesOnlyItsOwnJourney() {
        reviewed(7,"2026-11-09","done");
        reviewed(9,"2026-11-09","partial");
        db.delete("friends","id=?",new String[]{"7"});
        assertNull(store.entry(7,"2026-11-09"));
        assertTrue(store.recent(7,"2026-11-09",10).isEmpty());
        assertEquals("partial",store.entry(9,"2026-11-09").outcome);
        assertSummary(store.summary(0,"2026-11-09"),1,1,1,0,1,0,1);
        invalid(()->store.savePlan(7,"2026-11-09","Read",null,null,20));
    }

    public void testTriggerFailureRollsBackPlanReviewAndDelete() {
        reviewed(7,"2026-11-09","partial");
        db.execSQL("CREATE TABLE journey_failure_probe(value INTEGER)");
        db.execSQL("CREATE TRIGGER reject_journey_update AFTER UPDATE ON journey_entries BEGIN "
                +"INSERT INTO journey_failure_probe VALUES(1); SELECT RAISE(FAIL,'qa rejected'); END");
        rejected(()->store.savePlan(7,"2026-11-09","Changed","Changed","Changed",50));
        rejected(()->store.review(7,"2026-11-09","done","Changed",60));
        JourneyStore.Entry entry=store.entry(7,"2026-11-09");
        assertEquals("Read",entry.action);
        assertEquals("",entry.cue);
        assertEquals("",entry.goalTitle);
        assertEquals("partial",entry.outcome);
        assertEquals("",entry.note);
        assertEquals(11L,entry.updatedAt);
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM journey_failure_probe",null)) {
            assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0));
        }
        db.execSQL("DROP TRIGGER reject_journey_update");
        db.execSQL("CREATE TRIGGER reject_journey_insert AFTER INSERT ON journey_entries "
                +"BEGIN SELECT RAISE(FAIL,'qa rejected'); END");
        rejected(()->store.savePlan(7,"2026-11-08","New",null,null,70));
        assertNull(store.entry(7,"2026-11-08"));
        db.execSQL("DROP TRIGGER reject_journey_insert");
        db.execSQL("CREATE TRIGGER reject_journey_delete AFTER DELETE ON journey_entries "
                +"BEGIN SELECT RAISE(FAIL,'qa rejected'); END");
        rejected(()->store.delete(7,"2026-11-09"));
        assertNotNull(store.entry(7,"2026-11-09"));
        db.execSQL("DROP TRIGGER reject_journey_delete");
        store.review(7,"2026-11-09","done",null,80);
        assertEquals("done",store.entry(7,"2026-11-09").outcome);
    }

    public void testIgnoredWritesAreReportedInsteadOfSuccess() {
        db.execSQL("CREATE TRIGGER ignore_journey_insert BEFORE INSERT ON journey_entries "
                +"BEGIN SELECT RAISE(IGNORE); END");
        invalid(()->store.savePlan(7,"2026-11-09","Read",null,null,10));
        assertNull(store.entry(7,"2026-11-09"));
        db.execSQL("DROP TRIGGER ignore_journey_insert");
        store.savePlan(7,"2026-11-09","Read",null,null,10);
        db.execSQL("CREATE TRIGGER ignore_journey_update BEFORE UPDATE ON journey_entries "
                +"BEGIN SELECT RAISE(IGNORE); END");
        invalid(()->store.savePlan(7,"2026-11-09","Changed",null,null,20));
        invalid(()->store.review(7,"2026-11-09","done",null,20));
        assertEquals("Read",store.entry(7,"2026-11-09").action);
        assertEquals("planned",store.entry(7,"2026-11-09").outcome);
        db.execSQL("CREATE TRIGGER ignore_journey_delete BEFORE DELETE ON journey_entries "
                +"BEGIN SELECT RAISE(IGNORE); END");
        invalid(()->store.delete(7,"2026-11-09"));
        assertNotNull(store.entry(7,"2026-11-09"));
    }

    public void testSeparateDatabaseIsolationEvenWithMatchingFriendIds() {
        reviewed(7,"2026-11-09","done");
        try(SQLiteDatabase other=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            other.setForeignKeyConstraintsEnabled(true);
            helper.onCreate(other);
            other.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.isolated','Other account')");
            JourneyStore isolated=new JourneyStore(other);
            assertNull(isolated.entry(7,"2026-11-09"));
            assertSummary(isolated.summary(0,"2026-11-09"),0,0,0,0,0,0,0);
            isolated.savePlan(7,"2026-11-09","Separate",null,null,100);
            isolated.review(7,"2026-11-09","rest","Separate note",101);
            assertEquals("done",store.entry(7,"2026-11-09").outcome);
            assertEquals("rest",isolated.entry(7,"2026-11-09").outcome);
        }
    }

    public void testVersionSixMigrationAddsOnlyJourneyAndPreservesLegacyData() {
        try(SQLiteDatabase old=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            old.setForeignKeyConstraintsEnabled(true);
            old.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,name TEXT,"
                    +"personality TEXT,xp INTEGER DEFAULT 0)");
            old.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,content TEXT,"
                    +"is_user INTEGER,created INTEGER)");
            old.execSQL("INSERT INTO friends VALUES(7,'qa.legacy','Kept','calm',42)");
            old.execSQL("INSERT INTO messages VALUES(11,7,'Kept message',1,123)");
            helper.onUpgrade(old,1,6);
            old.execSQL("UPDATE friends SET purpose='독서',share_goals=1 WHERE id=7");
            old.execSQL("INSERT INTO appearance VALUES(7,'chat','beret','round_glasses','gamepad','none','none')");
            old.execSQL("INSERT INTO chat_requests(user_message,friend,state) VALUES(11,7,'failed')");
            old.execSQL("INSERT INTO goals VALUES(17,7,'Kept goal',123456,1,99)");
            old.execSQL("UPDATE demo_wallet SET balance=321 WHERE id=1");
            old.execSQL("INSERT INTO owned_items VALUES('crown')");
            old.execSQL("INSERT INTO purchases VALUES('crown',179)");
            old.setVersion(6);
            String before=legacySnapshot(old);
            old.beginTransaction();
            try {
                helper.onUpgrade(old,6,7);
                old.setVersion(7);
                old.setTransactionSuccessful();
            } finally { old.endTransaction(); }
            assertEquals(7,old.getVersion());
            assertEquals(before,legacySnapshot(old));
            JourneyStore migrated=new JourneyStore(old);
            assertTrue(migrated.recent(7,"2026-11-09",10).isEmpty());
            migrated.savePlan(7,"2026-11-09","New local plan",null,null,100);
            assertEquals("planned",migrated.entry(7,"2026-11-09").outcome);
            assertEquals(before,legacySnapshot(old));
            try(Cursor c=old.rawQuery("PRAGMA foreign_key_check",null)) { assertEquals(0,c.getCount()); }
        }
    }

    private void reviewed(int friend,String day,String outcome) {
        store.savePlan(friend,day,"Read",null,null,10);
        store.review(friend,day,outcome,null,11);
    }

    private void invalid(Runnable action) {
        try { action.run(); fail("Invalid or missing input accepted"); }
        catch(IllegalArgumentException expected) { }
    }

    private void rejected(Runnable action) {
        try { action.run(); fail("SQLite failure was hidden"); }
        catch(SQLiteConstraintException expected) { }
    }

    private void assertSummary(JourneyStore.Summary summary,int planned,int reviewed,int active,
            int done,int partial,int rest,int total) {
        assertEquals(planned,summary.plannedToday); assertEquals(reviewed,summary.reviewedToday);
        assertEquals(active,summary.activeDays7); assertEquals(done,summary.done7);
        assertEquals(partial,summary.partial7); assertEquals(rest,summary.rest7);
        assertEquals(total,summary.totalActiveDays);
    }

    private String legacySnapshot(SQLiteDatabase database) {
        StringBuilder snapshot=new StringBuilder();
        for(String table:new String[]{"friends","messages","appearance","chat_requests","goals",
                "demo_wallet","owned_items","purchases"}) {
            snapshot.append(table);
            try(Cursor c=database.rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)) {
                while(c.moveToNext()) for(int i=0;i<c.getColumnCount();i++)
                    snapshot.append('|').append(c.isNull(i)?"<null>":c.getString(i));
            }
        }
        return snapshot.toString();
    }
}
