package kr.example.haliltalk.auth;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.test.InstrumentationTestCase;
import org.json.JSONObject;
import kr.example.haliltalk.data.FriendsDatabase;

/** Pure local fixtures: no production auth changes, remote writes, or user database deletion. */
public final class AccountIsolationTest extends InstrumentationTestCase {
    private SQLiteDatabase database() {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        try (FriendsDatabase helper = new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        return db;
    }

    private void seed(SQLiteDatabase db) {
        db.execSQL("INSERT INTO friends(id,package,name,personality,purpose,xp,share_goals)"
                + " VALUES(7,'qa.snapshot','친구','kind','독서',41,1)");
        db.execSQL("INSERT INTO appearance(friend,character,hat,face,body,back,prop)"
                + " VALUES(7,'reading','beret','round_glasses','red_scarf','angel_wings','gamepad')");
        db.execSQL("INSERT INTO goals(id,friend,title,due_at,completed,created)"
                + " VALUES(3,7,'책 읽기',1234567890,1,1000)");
        db.execSQL("INSERT INTO goals(id,friend,title,due_at,completed,created)"
                + " VALUES(4,7,'기한 없는 목표',NULL,0,2000)");
        db.execSQL("INSERT INTO messages(id,friend,content,is_user,created)"
                + " VALUES(9,7,'PRIVATE_MESSAGE_SENTINEL',1,1000)");
        db.execSQL("UPDATE demo_wallet SET balance=123 WHERE id=1");
    }

    public void testGuestAndUidFilesAreDistinctBoundedAndPathSafe() {
        assertEquals("friends.db", AccountSession.databaseNameForUid(null));
        String first = AccountSession.databaseNameForUid("account/../one");
        String second = AccountSession.databaseNameForUid("account_.._one");
        assertFalse(first.equals(second));
        assertEquals(first, AccountSession.databaseNameForUid("account/../one"));
        assertTrue(first.matches("account_[0-9a-f]{64}\\.db"));
        assertEquals(75, AccountSession.databaseNameForUid("한".repeat(128)).length());
    }

    public void testSnapshotContainsOnlyFriendsAppearanceAndGoals() throws Exception {
        try (SQLiteDatabase db = database()) {
            seed(db);
            String encoded = FriendGoalSnapshot.capture(db);
            JSONObject snapshot = new JSONObject(encoded);
            assertEquals(2, snapshot.length());
            assertEquals(1, snapshot.getJSONArray("friends").length());
            assertEquals(2, snapshot.getJSONArray("goals").length());
            JSONObject friend = snapshot.getJSONArray("friends").getJSONObject(0);
            assertEquals(7, friend.getInt("id"));
            assertEquals("독서", friend.getString("purpose"));
            assertFalse(friend.has("xp"));
            assertFalse(friend.has("share_goals"));
            assertFalse(snapshot.has("messages"));
            assertFalse(snapshot.has("demo_wallet"));
            assertFalse(encoded.contains("PRIVATE_MESSAGE_SENTINEL"));
        }
    }

    public void testRestorePreservesAppearanceGoalsButNotEconomyOrConsent() throws Exception {
        try (SQLiteDatabase source = database(); SQLiteDatabase target = database()) {
            seed(source);
            FriendGoalSnapshot.restore(target, FriendGoalSnapshot.capture(source));
            try (Cursor friend = target.rawQuery("SELECT xp,share_goals,purpose FROM friends WHERE id=7", null)) {
                assertTrue(friend.moveToFirst());
                assertEquals(0, friend.getInt(0));
                assertEquals(0, friend.getInt(1));
                assertEquals("독서", friend.getString(2));
            }
            try (Cursor look = target.rawQuery("SELECT character,hat,body,back FROM appearance WHERE friend=7", null)) {
                assertTrue(look.moveToFirst());
                assertEquals("reading", look.getString(0));
                assertEquals("beret", look.getString(1));
                assertEquals("red_scarf", look.getString(2));
                assertEquals("angel_wings", look.getString(3));
            }
            assertEquals(2L, DatabaseUtils.queryNumEntries(target, "goals"));
            assertEquals(1L, DatabaseUtils.longForQuery(target, "SELECT completed FROM goals WHERE id=3", null));
            assertEquals(1L, DatabaseUtils.longForQuery(target, "SELECT due_at IS NULL FROM goals WHERE id=4", null));
            assertEquals(500L, DatabaseUtils.longForQuery(target, "SELECT balance FROM demo_wallet WHERE id=1", null));
            assertEquals(0L, DatabaseUtils.queryNumEntries(target, "messages"));
            assertEquals(0L, DatabaseUtils.queryNumEntries(target, "purchases"));
        }
    }

    public void testRestoreNeverOverwritesExistingRows() throws Exception {
        try (SQLiteDatabase source = database(); SQLiteDatabase target = database()) {
            seed(source);
            seed(target);
            try {
                FriendGoalSnapshot.restore(target, FriendGoalSnapshot.capture(source));
                fail("Existing local rows must block restore");
            } catch (IllegalStateException expected) {
                assertEquals(1L, DatabaseUtils.queryNumEntries(target, "friends"));
                assertEquals(41L, DatabaseUtils.longForQuery(target, "SELECT xp FROM friends WHERE id=7", null));
                assertEquals(1L, DatabaseUtils.queryNumEntries(target, "messages"));
            }
        }
    }

    public void testOrphanedLocalMessageAlsoBlocksRestore() throws Exception {
        try (SQLiteDatabase source = database(); SQLiteDatabase target = database()) {
            seed(source);
            target.execSQL("INSERT INTO messages(friend,content,is_user,created) VALUES(77,'keep',1,0)");
            try {
                FriendGoalSnapshot.restore(target, FriendGoalSnapshot.capture(source));
                fail("Orphaned local data must be preserved");
            } catch (IllegalStateException expected) {
                assertEquals(0L, DatabaseUtils.queryNumEntries(target, "friends"));
                assertEquals(1L, DatabaseUtils.queryNumEntries(target, "messages"));
            }
        }
    }

    public void testInvalidReferenceAndExtraConsentFieldsAreRejected() throws Exception {
        try (SQLiteDatabase source = database(); SQLiteDatabase target = database()) {
            seed(source);
            JSONObject data = new JSONObject(FriendGoalSnapshot.capture(source));
            data.getJSONArray("goals").getJSONObject(0).put("friend", 99);
            assertRejected(target, data.toString());
            data = new JSONObject(FriendGoalSnapshot.capture(source));
            data.getJSONArray("friends").getJSONObject(0).put("share_goals", 1);
            assertRejected(target, data.toString());
            data = new JSONObject(FriendGoalSnapshot.capture(source));
            data.getJSONArray("goals").getJSONObject(0).put("due_at", 1.5);
            assertRejected(target, data.toString());
        }
    }

    public void testSqlFailureRollsBackAllInsertedFriends() throws Exception {
        try (SQLiteDatabase source = database(); SQLiteDatabase target = database()) {
            seed(source);
            target.execSQL("CREATE TRIGGER reject_goal BEFORE INSERT ON goals"
                    + " BEGIN SELECT RAISE(ABORT,'synthetic rejection'); END");
            try {
                FriendGoalSnapshot.restore(target, FriendGoalSnapshot.capture(source));
                fail("Synthetic SQL failure must propagate");
            } catch (SQLiteException expected) {
                assertEquals(0L, DatabaseUtils.queryNumEntries(target, "friends"));
                assertEquals(0L, DatabaseUtils.queryNumEntries(target, "appearance"));
                assertEquals(0L, DatabaseUtils.queryNumEntries(target, "goals"));
                assertEquals(500L, DatabaseUtils.longForQuery(target, "SELECT balance FROM demo_wallet WHERE id=1", null));
            }
        }
    }

    public void testGoalTitleUsesVersionSixLimit() throws Exception {
        try (SQLiteDatabase source = database(); SQLiteDatabase target = database();
                SQLiteDatabase emptyTarget = database()) {
            seed(source);
            JSONObject data = new JSONObject(FriendGoalSnapshot.capture(source));
            data.getJSONArray("goals").getJSONObject(0).put("title", "a".repeat(120));
            FriendGoalSnapshot.restore(target, data.toString());
            assertEquals(120L, DatabaseUtils.longForQuery(target, "SELECT length(title) FROM goals WHERE id=3", null));
            data.getJSONArray("goals").getJSONObject(0).put("title", "a".repeat(121));
            assertRejected(emptyTarget, data.toString());
        }
    }

    public void testOversizedPayloadIsRejectedBeforeWriting() throws Exception {
        try (SQLiteDatabase target = database()) {
            assertRejected(target, " ".repeat(FriendGoalSnapshot.MAX_BYTES + 1));
        }
    }

    private void assertRejected(SQLiteDatabase target, String input) throws Exception {
        try {
            FriendGoalSnapshot.restore(target, input);
            fail("Invalid snapshot must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(0L, DatabaseUtils.queryNumEntries(target, "friends"));
            assertEquals(0L, DatabaseUtils.queryNumEntries(target, "goals"));
        }
    }
}
