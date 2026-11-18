package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

/** In-memory fixtures only: no account database or actual wallet is opened. */
public final class GoalBackupTest extends InstrumentationTestCase {
    private SQLiteDatabase database;

    @Override protected void setUp() throws Exception {
        super.setUp();
        database = fixture(7);
    }

    @Override protected void tearDown() throws Exception {
        try { database.close(); } finally { super.tearDown(); }
    }

    private SQLiteDatabase fixture(long friend) {
        SQLiteDatabase db = SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        try (FriendsDatabase helper = new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES(?,'qa.goal.app','Fixture',41)",
                new Object[]{friend});
        db.execSQL("INSERT INTO messages(friend,content,is_user,created) VALUES(?,'Private fixture',1,55)",
                new Object[]{friend});
        return db;
    }

    private String goal(String pkg, String title, String due, boolean complete, long created) {
        return "{\"package\":\"" + pkg + "\",\"title\":\"" + title + "\",\"due_at\":" + due
                + ",\"completed\":" + complete + ",\"created\":" + created + "}";
    }

    private String record(String title) { return goal("qa.goal.app", title, "2000", false, 1000); }
    private String file(String rows) { return "{\"version\":1,\"goals\":[" + rows + "]}"; }
    private GoalBackup.Document read(String json) throws Exception {
        return GoalBackup.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }
    private long scalar(SQLiteDatabase db, String sql) {
        try (Cursor result = db.rawQuery(sql, null)) { result.moveToFirst(); return result.getLong(0); }
    }
    private long count() { return scalar(database, "SELECT COUNT(*) FROM goals"); }
    private void reject(String json) throws Exception {
        try { read(json); fail("Malformed files must be rejected before any write"); }
        catch (GoalBackup.InvalidBackupException expected) { assertEquals(0L, count()); }
    }

    public void testRoundTripBindsPackagesRatherThanLocalFriendIdsAndPreservesNoRewards() throws Exception {
        database.execSQL("INSERT INTO goals(friend,title,due_at,completed,created)"
                + " VALUES(7,'Plan',2000,0,1000),(7,'Done',NULL,1,900)");
        byte[] exported = GoalBackup.exportData(database);
        JSONObject root = new JSONObject(new String(exported, StandardCharsets.UTF_8));
        assertEquals(2, root.length());
        JSONArray rows = root.getJSONArray("goals");
        assertEquals(5, rows.getJSONObject(0).length());
        assertFalse(rows.getJSONObject(0).has("friend"));
        try (SQLiteDatabase destination = fixture(99)) {
            GoalBackup.Document document = GoalBackup.read(new ByteArrayInputStream(exported));
            GoalBackup.Preview preview = GoalBackup.preview(destination, document);
            assertEquals(2, preview.additions);
            assertEquals(2, GoalBackup.importConfirmed(destination, preview));
            assertEquals(2L, scalar(destination, "SELECT COUNT(*) FROM goals WHERE friend=99"));
            assertEquals(1L, scalar(destination, "SELECT COUNT(*) FROM goals WHERE completed=1 AND due_at IS NULL"));
            assertEquals(41L, scalar(destination, "SELECT xp FROM friends WHERE id=99"));
            assertEquals(500L, scalar(destination, "SELECT balance FROM demo_wallet"));
            assertEquals(1L, scalar(destination, "SELECT COUNT(*) FROM messages"));
            assertEquals(0L, scalar(destination, "SELECT COUNT(*) FROM purchases"));
            GoalBackup.Preview repeated = GoalBackup.preview(destination, document);
            assertEquals(0, repeated.additions);
            assertEquals(2, repeated.alreadyPresent);
            assertEquals(0, GoalBackup.importConfirmed(destination, repeated));
        }
    }

    public void testUnknownFriendsAreCountedAndSkippedWithoutCreatingFriends() throws Exception {
        GoalBackup.Preview preview = GoalBackup.preview(database, read(file(record("Known") + ","
                + goal("qa.unknown.app", "Unknown", "null", true, 1000))));
        assertEquals(2, preview.total);
        assertEquals(1, preview.additions);
        assertEquals(1, preview.unknownFriends);
        assertEquals(1, GoalBackup.importConfirmed(database, preview));
        assertEquals(1L, count());
        assertEquals(1L, scalar(database, "SELECT COUNT(*) FROM friends"));
        assertEquals(41L, scalar(database, "SELECT xp FROM friends"));
        assertEquals(500L, scalar(database, "SELECT balance FROM demo_wallet"));
    }

    public void testMergeNeverOverwritesAnExistingGoal() throws Exception {
        database.execSQL("INSERT INTO goals(id,friend,title,due_at,completed,created)"
                + " VALUES(12,7,'Original',NULL,1,1000)");
        GoalBackup.Preview preview = GoalBackup.preview(database, read(file(record("New"))));
        assertEquals(1, GoalBackup.importConfirmed(database, preview));
        assertEquals(2L, count());
        assertEquals(1L, scalar(database,
                "SELECT COUNT(*) FROM goals WHERE id=12 AND title='Original' AND completed=1 AND due_at IS NULL"));
    }

    public void testWrongTypesDatesUnknownFieldsAndDuplicateKeysRejectWholeFile() throws Exception {
        String row = record("Valid");
        reject("{");
        reject(file(row).replace("\"version\":1", "\"version\":\"1\""));
        reject(file(row).replace("\"version\":1", "\"version\":2"));
        reject(file(row).replace("\"completed\":false", "\"completed\":0"));
        reject(file(row).replace("\"due_at\":2000", "\"due_at\":\"2000\""));
        reject(file(row).replace("\"due_at\":2000", "\"due_at\":2000.5"));
        reject(file(row).replace("\"due_at\":2000", "\"due_at\":-1"));
        reject(file(row).replace("\"due_at\":2000", "\"due_at\":253402300800000"));
        reject(file(row).replace("\"created\":1000", "\"created\":null"));
        reject(file(row).replace("\"created\":1000", "\"created\":1000,\"xp\":900"));
        reject(file(row).replace("\"title\":\"Valid\"", "\"title\":\"Valid\",\"title\":\"Other\""));
        reject(file(row).replace("\"package\":\"qa.goal.app\"", "\"package\":\"qa.bad/package\""));
        reject(file(row + "," + row));
        reject(file(row) + "{}");
        reject(file(row + "," + row.replace("\"title\":\"Valid\"", "\"title\":null")));
        reject(file(row.replace("Valid", "x".repeat(GoalBackup.MAX_TITLE + 1))));
        reject(file(row.replace("Valid", "\\n")));
        reject(file(row.replace("Valid", "\\uD800")));
    }

    public void testByteCountGoalCountAndEncodingBounds() throws Exception {
        try {
            GoalBackup.read(new ByteArrayInputStream(new byte[GoalBackup.MAX_BYTES + 1]));
            fail("Oversized input must be rejected");
        } catch (GoalBackup.InvalidBackupException expected) { }
        try {
            GoalBackup.read(new ByteArrayInputStream(new byte[]{(byte) 0xc3, (byte) 0x28}));
            fail("Malformed UTF-8 must be rejected");
        } catch (GoalBackup.InvalidBackupException expected) { }
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i <= GoalBackup.MAX_GOALS; i++) {
            if (i > 0) rows.append(',');
            rows.append(record("Goal " + i));
        }
        reject(file(rows.toString()));
    }

    public void testInsertionFailureRollsBackAllGoalsAndRewards() throws Exception {
        GoalBackup.Preview preview = GoalBackup.preview(database,
                read(file(record("First") + "," + record("Blocked"))));
        database.execSQL("CREATE TRIGGER fail_second BEFORE INSERT ON goals"
                + " WHEN NEW.title='Blocked' BEGIN SELECT RAISE(ABORT,'fixture'); END");
        try { GoalBackup.importConfirmed(database, preview); fail("A failed insert must roll back"); }
        catch (SQLiteConstraintException expected) { }
        assertEquals(0L, count());
        assertEquals(41L, scalar(database, "SELECT xp FROM friends"));
        assertEquals(500L, scalar(database, "SELECT balance FROM demo_wallet"));
        assertEquals(1L, scalar(database, "SELECT COUNT(*) FROM messages"));
    }

    public void testAccountOrLifecycleInvalidationRollsBackMidImport() throws Exception {
        GoalBackup.Preview preview = GoalBackup.preview(database,
                read(file(record("First") + "," + record("Second"))));
        AtomicInteger checks = new AtomicInteger();
        try {
            GoalBackup.importConfirmed(database, preview, () -> checks.incrementAndGet() < 3);
            fail("An invalidated operation must not commit partial rows");
        } catch (GoalBackup.InvalidBackupException expected) { }
        assertEquals(0L, count());
    }

    public void testChangedFriendMappingRejectsPreviouslyConfirmedCounts() throws Exception {
        GoalBackup.Preview preview = GoalBackup.preview(database, read(file(record("Known"))));
        database.execSQL("UPDATE friends SET package='qa.replaced.app' WHERE id=7");
        try { GoalBackup.importConfirmed(database, preview); fail("Counts require a fresh confirmation"); }
        catch (GoalBackup.InvalidBackupException expected) { }
        assertEquals(0L, count());
    }

    public void testPackageCollisionsAreRejectedRatherThanChoosingWrongFriend() throws Exception {
        try (SQLiteDatabase collision = SQLiteDatabase.create(null)) {
            collision.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY,package TEXT)");
            collision.execSQL("CREATE TABLE goals(id INTEGER PRIMARY KEY,friend INTEGER,title TEXT,"
                    + "due_at INTEGER,completed INTEGER,created INTEGER)");
            collision.execSQL("INSERT INTO friends VALUES(1,'qa.goal.app'),(2,'qa.goal.app')");
            try {
                GoalBackup.preview(collision, read(file(record("Known"))));
                fail("Ambiguous package mappings must never pick a friend");
            } catch (GoalBackup.InvalidBackupException expected) { }
            assertEquals(0L, scalar(collision, "SELECT COUNT(*) FROM goals"));
        }
    }
}
