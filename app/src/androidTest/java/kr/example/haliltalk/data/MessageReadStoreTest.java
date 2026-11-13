package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;

/** [U2 | 읽음 저장] 실제 메모리 SQLite로 읽음 경계·격리·마이그레이션을 검사한다. */
public final class MessageReadStoreTest extends InstrumentationTestCase {
    private SQLiteDatabase db;
    private MessageReadStore store;

    @Override protected void setUp() throws Exception {
        super.setUp();
        db=SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(7,'qa.read','Friend','calm',41)");
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(9,'qa.other','Other','kind',3)");
        store=new MessageReadStore(db);
    }

    @Override protected void tearDown() throws Exception {
        try { db.close(); } finally { super.tearDown(); }
    }

    public void testNewIncomingCountsButOutgoingNeverGetsReceipts() {
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads"));
        assertEquals(0,store.unreadCount(7));
        message(db,10,7,1);
        store.markReadThrough(7,10);
        assertEquals(0,store.unreadCount(7));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads"));
        message(db,11,7,0);
        message(db,12,7,1);
        message(db,13,7,0);
        String before=legacySnapshot(db);
        assertEquals(2,store.unreadCount(7));
        store.markReadThrough(7,13);
        assertEquals(0,store.unreadCount(7));
        assertEquals(2,scalar(db,"SELECT COUNT(*) FROM message_reads"));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message IN (10,12)"));
        message(db,14,7,0);
        assertEquals(1,new MessageReadStore(db).unreadCount(7));
        db.delete("messages","id=14",null);
        assertEquals(before,legacySnapshot(db));
    }

    public void testDisplayedBoundaryIsInclusiveIdempotentAndFriendScoped() {
        message(db,10,7,0);
        message(db,11,9,0);
        message(db,12,7,1);
        message(db,13,7,0);
        message(db,14,9,0);
        message(db,15,7,0);
        String before=legacySnapshot(db);
        store.markReadThrough(7,0);
        assertEquals(3,store.unreadCount(7));
        store.markReadThrough(7,13);
        store.markReadThrough(7,13);
        store.markReadThrough(7,10);
        assertEquals(1,store.unreadCount(7));
        assertEquals(2,store.unreadCount(9));
        assertEquals(2,scalar(db,"SELECT COUNT(*) FROM message_reads"));
        assertEquals(1,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message=13"));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message IN (11,12,14,15)"));
        store.markReadThrough(9,11);
        assertEquals(1,store.unreadCount(7));
        assertEquals(1,store.unreadCount(9));
        store.markReadThrough(100,Long.MAX_VALUE);
        assertEquals(0,store.unreadCount(100));
        assertEquals(before,legacySnapshot(db));
    }

    public void testMessageBoundaryKeepsLongIdsWithoutTruncation() {
        message(db,2147483650L,7,0);
        message(db,2147483651L,7,0);
        store.markReadThrough(7,2147483650L);
        assertEquals(1,store.unreadCount(7));
        assertEquals(1,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message=2147483650"));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message=2147483651"));
    }

    public void testMessageDeletionCascadesAndReusedIdStartsUnread() {
        message(db,11,7,0);
        message(db,12,9,0);
        store.markReadThrough(7,11);
        store.markReadThrough(9,12);
        assertEquals(1,db.delete("messages","id=11",null));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message=11"));
        assertEquals(1,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message=12"));
        message(db,11,7,0);
        assertEquals(1,store.unreadCount(7));
        assertEquals(0,store.unreadCount(9));
        store.markReadThrough(7,11);
        assertEquals(0,store.unreadCount(7));
        assertEquals(2,db.delete("messages",null,null));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM message_reads"));
        try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)) { assertEquals(0,c.getCount()); }
    }

    public void testSeparateDatabasesKeepMatchingFriendAndMessageIdsIsolated() {
        message(db,11,7,0);
        store.markReadThrough(7,11);
        try(SQLiteDatabase other=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            other.setForeignKeyConstraintsEnabled(true);
            helper.onCreate(other);
            other.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.isolated','Other account')");
            message(other,11,7,0);
            MessageReadStore isolated=new MessageReadStore(other);
            assertEquals(1,isolated.unreadCount(7));
            assertEquals(0,store.unreadCount(7));
            isolated.markReadThrough(7,11);
            message(db,12,7,0);
            assertEquals(1,store.unreadCount(7));
            assertEquals(0,isolated.unreadCount(7));
            assertEquals(1,scalar(other,"SELECT COUNT(*) FROM message_reads"));
        }
    }

    public void testVersionSevenUpgradePreservesAllLegacyTablesAndReadsOnlyIncoming() {
        try(SQLiteDatabase old=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            old.setForeignKeyConstraintsEnabled(true);
            old.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT UNIQUE,name TEXT,"
                    +"personality TEXT,xp INTEGER DEFAULT 0)");
            old.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,friend INTEGER,content TEXT,"
                    +"is_user INTEGER,created INTEGER)");
            old.execSQL("INSERT INTO friends VALUES(7,'qa.legacy','Kept','calm',42)");
            old.execSQL("INSERT INTO friends VALUES(9,'qa.legacy.other','Other','kind',3)");
            helper.onUpgrade(old,1,7);
            old.execSQL("UPDATE friends SET purpose='Reading',share_goals=1 WHERE id=7");
            message(old,11,7,1);
            message(old,12,7,0);
            message(old,13,9,0);
            old.execSQL("INSERT INTO messages VALUES(14,7,NULL,NULL,NULL)");
            old.execSQL("INSERT INTO appearance VALUES(7,'chat','beret','round_glasses','gamepad','none','none')");
            old.execSQL("INSERT INTO chat_requests(user_message,friend,state,attempt,reply_message,error)"
                    +" VALUES(11,7,'complete',2,12,NULL)");
            old.execSQL("INSERT INTO goals VALUES(17,7,'Kept goal',123456,1,99)");
            old.execSQL("UPDATE demo_wallet SET balance=321 WHERE id=1");
            old.execSQL("INSERT INTO owned_items VALUES('crown')");
            old.execSQL("INSERT INTO purchases VALUES('crown',179)");
            new JourneyStore(old).savePlan(7,"2026-11-09","Kept plan","After tea","Kept goal",100);
            new JourneyStore(old).review(7,"2026-11-09","partial","Kept note",101);
            old.setVersion(7);
            String before=legacySnapshot(old);
            old.beginTransaction();
            try {
                helper.onUpgrade(old,7,8);
                old.setVersion(8);
                old.setTransactionSuccessful();
            } finally { old.endTransaction(); }
            assertEquals(8,old.getVersion());
            assertEquals(before,legacySnapshot(old));
            MessageReadStore migrated=new MessageReadStore(old);
            assertEquals(0,migrated.unreadCount(7));
            assertEquals(0,migrated.unreadCount(9));
            assertEquals(2,scalar(old,"SELECT COUNT(*) FROM message_reads"));
            assertEquals(2,scalar(old,"SELECT COUNT(*) FROM message_reads WHERE message IN (12,13)"));
            assertEquals(0,scalar(old,"SELECT COUNT(*) FROM message_reads WHERE message IN (11,14)"));
            MessageReadStore.markLegacyRead(old);
            assertEquals(2,scalar(old,"SELECT COUNT(*) FROM message_reads"));
            message(old,15,7,0);
            assertEquals(1,migrated.unreadCount(7));
            assertEquals(0,migrated.unreadCount(9));
            old.delete("messages","id=15",null);
            assertEquals(before,legacySnapshot(old));
            try(Cursor c=old.rawQuery("PRAGMA foreign_key_check",null)) { assertEquals(0,c.getCount()); }
        }
    }

    public void testTriggerFailureRollsBackReceiptsAndSideEffectsAndAllowsRetry() {
        message(db,10,7,0);
        store.markReadThrough(7,10);
        message(db,11,7,0);
        message(db,12,7,0);
        String before=legacySnapshot(db);
        db.execSQL("CREATE TABLE read_failure_probe(value INTEGER)");
        db.execSQL("CREATE TRIGGER reject_read AFTER INSERT ON message_reads BEGIN "
                +"INSERT INTO read_failure_probe VALUES(1); SELECT RAISE(FAIL,'qa rejected'); END");
        try {
            store.markReadThrough(7,12);
            fail("SQLite failure was hidden");
        } catch(SQLiteConstraintException expected) { }
        assertFalse(db.inTransaction());
        assertEquals(2,store.unreadCount(7));
        assertEquals(1,scalar(db,"SELECT COUNT(*) FROM message_reads"));
        assertEquals(1,scalar(db,"SELECT COUNT(*) FROM message_reads WHERE message=10"));
        assertEquals(0,scalar(db,"SELECT COUNT(*) FROM read_failure_probe"));
        assertEquals(before,legacySnapshot(db));
        db.execSQL("DROP TRIGGER reject_read");
        store.markReadThrough(7,12);
        assertEquals(0,store.unreadCount(7));
        assertEquals(3,scalar(db,"SELECT COUNT(*) FROM message_reads"));
    }

    private void message(SQLiteDatabase database,long id,int friend,int isUser) {
        database.execSQL("INSERT INTO messages(id,friend,content,is_user,created) VALUES(?,?,'Kept message',?,123)",
                new Object[]{id,friend,isUser});
    }

    private int scalar(SQLiteDatabase database,String query) {
        try(Cursor c=database.rawQuery(query,null)) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    private String legacySnapshot(SQLiteDatabase database) {
        StringBuilder snapshot=new StringBuilder();
        for(String table:new String[]{"friends","messages","appearance","chat_requests","goals",
                "demo_wallet","owned_items","purchases","journey_entries"}) {
            try(Cursor schema=database.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{table})) {
                assertTrue(schema.moveToFirst());
                snapshot.append(table).append(':').append(schema.getString(0));
            }
            try(Cursor c=database.rawQuery("SELECT * FROM "+table+" ORDER BY rowid",null)) {
                while(c.moveToNext()) {
                    snapshot.append('\n');
                    for(int i=0;i<c.getColumnCount();i++)
                        snapshot.append('|').append(c.getType(i)).append(':')
                                .append(c.isNull(i)?"<null>":c.getString(i));
                }
            }
        }
        return snapshot.toString();
    }
}
