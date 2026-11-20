package kr.example.haliltalk.data;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.test.InstrumentationTestCase;
import kr.example.haliltalk.shop.CoinLedger;
import kr.example.haliltalk.shop.DemoShopStore;

/** Fixed dates, real SQLite transactions, no UI or wall-clock dependency. */
public final class QuestRewardStoreTest extends InstrumentationTestCase {
    private static final String DAY="2026-11-16";
    private SQLiteDatabase db;
    private JourneyStore journeys;
    private QuestRewardStore rewards;
    private DemoShopStore shop;

    @Override protected void setUp() throws Exception {
        super.setUp();
        db=SQLiteDatabase.create(null);db.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES"
                +"(7,'qa.reward','First',41),(9,'qa.other','Other',3)");
        journeys=new JourneyStore(db);rewards=new QuestRewardStore(db);shop=new DemoShopStore(db);
    }

    @Override protected void tearDown() throws Exception {
        try{db.close();}finally{super.tearDown();}
    }

    public void testOnlyActualReviewQualifiesAndReadsDoNotCredit() {
        assertEquals(QuestRewardStore.Status.NO_ATTEMPT,rewards.status(7,DAY));
        assertFalse(rewards.claim(7,DAY));
        journeys.savePlan(7,DAY,"Read one page",null,null,1);
        assertFalse(rewards.claim(7,DAY));
        journeys.review(7,DAY,"rest","",2);assertFalse(rewards.claim(7,DAY));
        journeys.review(7,DAY,"partial","",3);
        assertEquals(QuestRewardStore.Status.AVAILABLE,rewards.status(7,DAY));
        assertEquals(500,shop.balance());assertEquals(1,shop.history().size());
        assertTrue(rewards.claim(7,DAY));assertFalse(rewards.claim(7,DAY));
        assertEquals(520,shop.balance());assertEquals(2,shop.history().size());
        assertEquals(41L,DatabaseUtils.longForQuery(db,"SELECT xp FROM friends WHERE id=7",null));
        assertEquals(0L,DatabaseUtils.queryNumEntries(db,"purchases"));
    }

    public void testReceiptSurvivesOtherFriendReviewEditsDeletionAndStoreReopen() {
        attempt(7,DAY);attempt(9,DAY);assertTrue(rewards.claim(7,DAY));
        journeys.review(7,DAY,"rest","Edited",3);
        assertFalse(rewards.claim(9,DAY));
        journeys.delete(7,DAY);db.delete("friends","id=7",null);
        db.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.reused','New friend')");
        attempt(7,DAY);
        assertFalse(new QuestRewardStore(db).claim(7,DAY));
        assertFalse(new QuestRewardStore(db).claim(9,DAY));assertEquals(520,shop.balance());
        attempt(9,"2026-11-17");assertTrue(rewards.claim(9,"2026-11-17"));
        assertEquals(540,shop.balance());
    }

    public void testEveryFailedOrIgnoredWriteRollsBackAndCanRetry() {
        attempt(7,DAY);
        String[] tables={"quest_reward_receipts","demo_wallet","coin_ledger"};
        for(String table:tables)for(String failure:new String[]{"ABORT,'qa rejected'","IGNORE"}) {
            db.execSQL("CREATE TRIGGER qa_reward_fail BEFORE "
                    +("demo_wallet".equals(table)?"UPDATE":"INSERT")+" ON "+table
                    +" BEGIN SELECT RAISE("+failure+"); END");
            try {
                rewards.claim(7,DAY);fail("Failed or ignored "+table+" write must throw");
            }catch(SQLiteException expected) {}
            finally {db.execSQL("DROP TRIGGER qa_reward_fail");}
            assertFalse(db.inTransaction());assertEquals(500,shop.balance());
            assertEquals(0L,DatabaseUtils.queryNumEntries(db,"quest_reward_receipts"));
            assertEquals(1,shop.history().size());
            assertEquals(QuestRewardStore.Status.AVAILABLE,rewards.status(7,DAY));
        }
        long before=System.currentTimeMillis();
        assertTrue(rewards.claim(7,DAY));assertFalse(rewards.claim(7,DAY));
        CoinLedger.Entry event=shop.history().get(0);
        assertEquals("quest:"+DAY,event.eventKey);assertEquals("quest",event.source);
        assertEquals(DAY,event.day);assertNull(event.itemId);
        assertNotNull(event.createdAt);
        assertTrue(event.createdAt>=before&&event.createdAt<=System.currentTimeMillis());
        assertEquals(20,event.amount);assertEquals(520,event.balance);
        assertEquals(1L,DatabaseUtils.queryNumEntries(db,"quest_reward_receipts"));
    }

    public void testIntegerLimitMissingWalletAndInvalidDatesNeverCredit() {
        attempt(7,DAY);
        db.execSQL("UPDATE demo_wallet SET balance=?",new Object[]{Integer.MAX_VALUE-19});
        assertEquals(QuestRewardStore.Status.BALANCE_LIMIT,rewards.status(7,DAY));
        assertFalse(rewards.claim(7,DAY));assertEquals(Integer.MAX_VALUE-19,shop.balance());
        db.execSQL("UPDATE demo_wallet SET balance=?",new Object[]{Integer.MAX_VALUE-20});
        assertTrue(rewards.claim(7,DAY));assertEquals(Integer.MAX_VALUE,shop.balance());
        for(String invalid:new String[]{null,"bad","2026-02-30","2026-1-01"}) {
            try{rewards.claim(7,invalid);fail("Invalid date");}
            catch(IllegalArgumentException expected) {}
            assertFalse(db.inTransaction());
        }
        attempt(7,"2026-11-17");
        db.delete("demo_wallet","id=1",null);
        try{rewards.claim(7,"2026-11-17");fail("Missing wallet");}
        catch(SQLiteException expected) {}
        assertEquals(1L,DatabaseUtils.queryNumEntries(db,"quest_reward_receipts"));
    }

    public void testUniqueLedgerConflictRollsBackReceiptAndWallet() {
        attempt(7,DAY);
        db.execSQL("INSERT INTO coin_ledger(event_key,source,amount,balance,created_at,day)"
                +" VALUES('quest:2026-11-16','quest',20,520,123,'2026-11-16')");
        try{rewards.claim(7,DAY);fail("Unique event must prevent credit");}
        catch(SQLiteException expected) {}
        assertEquals(500,shop.balance());
        assertEquals(0L,DatabaseUtils.queryNumEntries(db,"quest_reward_receipts"));
        db.delete("coin_ledger","event_key=?",new String[]{"quest:"+DAY});
        assertTrue(rewards.claim(7,DAY));assertEquals(520,shop.balance());
    }

    public void testAccountDatabasesHaveIndependentReceipts() {
        attempt(7,DAY);assertTrue(rewards.claim(7,DAY));
        try(SQLiteDatabase other=SQLiteDatabase.create(null);
                FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(other);
            other.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.account','Other account')");
            JourneyStore separate=new JourneyStore(other);
            separate.savePlan(7,DAY,"Read",null,null,1);separate.review(7,DAY,"done","",2);
            assertTrue(new QuestRewardStore(other).claim(7,DAY));
            assertEquals(520,new DemoShopStore(other).balance());
        }
        assertFalse(new QuestRewardStore(db).claim(7,DAY));assertEquals(520,shop.balance());
    }

    private void attempt(int friend,String day) {
        journeys.savePlan(friend,day,"Read one page",null,null,1);
        journeys.review(friend,day,"done","",2);
    }
}
