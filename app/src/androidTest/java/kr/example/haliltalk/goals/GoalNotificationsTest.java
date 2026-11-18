package kr.example.haliltalk.goals;

import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;

/** Calls the receiver directly and uses fixed times; no sleeps or actual notification delivery. */
public final class GoalNotificationsTest extends InstrumentationTestCase {
    private Context context;
    private SharedPreferences preferences;
    private SharedPreferences session;
    private long due;
    private boolean completed;
    private boolean friendPresent;
    private int opened;
    private boolean exitOnOpen;
    private static final String FIXTURE = "qa_goal_reminder_regression.db";

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context base = getInstrumentation().getTargetContext();
        preferences = base.getSharedPreferences("goal_reminders_regression", 0);
        session = base.getSharedPreferences("goal_reminders_session_regression", 0);
        assertTrue(preferences.edit().clear().commit());
        assertTrue(session.edit().clear().putBoolean("guest_entered", true).commit());
        due = 1000;
        completed = false;
        friendPresent = true;
        context = new ContextWrapper(base) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return GoalNotifications.PREFERENCES.equals(name) ? preferences
                        : "account_session".equals(name) ? session
                        : super.getSharedPreferences(name, mode);
            }
            @Override public Object getSystemService(String name) {
                return Context.NOTIFICATION_SERVICE.equals(name) ? null : super.getSystemService(name);
            }
            @Override public java.io.File getDatabasePath(String name) {
                opened++;
                if (exitOnOpen) {
                    assertTrue(session.edit().putBoolean("guest_entered", false).commit());
                }
                return base.getDatabasePath(FIXTURE);
            }
        };
        assertTrue(AccountSession.isEntered(context));
    }

    @Override protected void tearDown() throws Exception {
        try {
            GoalNotifications.cancelAll(context);
            assertTrue(preferences.edit().clear().commit());
            assertTrue(session.edit().clear().commit());
            getInstrumentation().getTargetContext().deleteDatabase(FIXTURE);
        } finally { super.tearDown(); }
    }

    private SQLiteDatabase memory() {
        opened++;
        SQLiteDatabase db = SQLiteDatabase.create(null);
        db.setForeignKeyConstraintsEnabled(true);
        try (FriendsDatabase helper = new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        if (friendPresent) {
            db.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.reminder.app','Fixture')");
            db.execSQL("INSERT INTO goals(id,friend,title,due_at,completed,created) VALUES(11,7,'Fixture',?,?,0)",
                    new Object[]{due, completed ? 1 : 0});
        }
        db.setVersion(8);
        return db;
    }

    private Uri identity(String account) {
        return Uri.parse(GoalNotifications.key(account, 11, due)).buildUpon()
                .appendQueryParameter("run", "regression").build();
    }

    private Uri pending() {
        Uri identity = identity(AccountSession.databaseName(context));
        assertTrue(preferences.edit().putStringSet(GoalNotifications.PENDING,
                new HashSet<>(Collections.singleton(identity.toString()))).commit());
        return identity;
    }

    private void receive(Uri identity) {
        prepareFixture();
        new GoalReminderReceiver().onReceive(context, GoalNotifications.alarmIntent(context, identity));
    }

    private void prepareFixture() {
        Context base=getInstrumentation().getTargetContext();
        // 최근 SQLiteOpenHelper는 openOrCreateDatabase 대신 getDatabasePath를 사용한다.
        // 별도 실제 DB를 사용해 친구·목표 조회 자체를 대체하지 않는다.
        base.deleteDatabase(FIXTURE);
        try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(base.getDatabasePath(FIXTURE),null);
                FriendsDatabase helper=new FriendsDatabase(base)) {
            helper.onCreate(db);
            if(friendPresent) {
                db.execSQL("INSERT INTO friends(id,package,name) VALUES(7,'qa.reminder.app','Fixture')");
                db.execSQL("INSERT INTO goals(id,friend,title,due_at,completed,created) VALUES(11,7,'Fixture',?,?,0)",
                        new Object[]{due,completed?1:0});
            }
            db.setVersion(8);
        }
    }
    private void reschedule() {
        prepareFixture();
        GoalNotifications.reschedule(context);
    }

    private Set<String> pendingValues() {
        return preferences.getStringSet(GoalNotifications.PENDING, new HashSet<>());
    }

    public void testOverduePendingSurvivesRescheduleAndKeepsTheSameIdentity() {
        Uri identity = pending();
        reschedule();
        assertEquals(Collections.singleton(identity.toString()), pendingValues());
        reschedule();
        assertEquals(Collections.singleton(identity.toString()), pendingValues());
        assertNotNull(PendingIntent.getBroadcast(context, 0, GoalNotifications.alarmIntent(context, identity),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE));
    }

    public void testConsumedReminderIsNotRecreatedEvenIfTheClockMovesBack() {
        Uri identity = pending();
        receive(identity);
        assertTrue(pendingValues().isEmpty());
        assertTrue(preferences.getStringSet(GoalNotifications.CONSUMED, new HashSet<>())
                .contains(GoalNotifications.key(identity)));
        reschedule();
        assertTrue(pendingValues().isEmpty());
        assertFalse(GoalNotifications.shouldSchedule(1000, 500, false, true));
        int after = opened;
        receive(identity);
        assertEquals("A duplicate must be rejected before reopening any database", after, opened);
    }

    public void testNewOverdueGoalsAreNotNotifiedButFutureGoalsAreScheduled() {
        reschedule();
        assertTrue(pendingValues().isEmpty());
        due = 253402000000000L;
        reschedule();
        assertEquals(1, pendingValues().size());
        assertTrue(preferences.getStringSet(GoalNotifications.KNOWN, new HashSet<>())
                .contains(GoalNotifications.key(AccountSession.databaseName(context), 11, due)));
    }

    public void testCompletedOrDeletedGoalsLosePendingAlarms() {
        pending();
        completed = true;
        reschedule();
        assertTrue(pendingValues().isEmpty());
        completed = false;
        pending();
        friendPresent = false;
        reschedule();
        assertTrue(pendingValues().isEmpty());
    }

    public void testChangedDeadlineRejectsOldBroadcastWithoutConsumingTheCurrentGoal() {
        Uri identity = pending();
        due = 2000;
        receive(identity);
        assertEquals(Collections.singleton(identity.toString()), pendingValues());
        assertTrue(preferences.getStringSet(GoalNotifications.CONSUMED, new HashSet<>()).isEmpty());
    }

    public void testOldAccountAndUnexpectedIntentsNeverOpenDatabase() {
        Uri identity = identity("different-account.db");
        assertTrue(preferences.edit().putStringSet(GoalNotifications.PENDING,
                new HashSet<>(Collections.singleton(identity.toString()))).commit());
        receive(identity);
        new GoalReminderReceiver().onReceive(context, null);
        new GoalReminderReceiver().onReceive(context, new Intent().setAction("unexpected").setData(identity));
        new GoalReminderReceiver().onReceive(context, new Intent().setAction(GoalNotifications.ACTION));
        assertEquals(0, opened);
    }

    public void testCancelAllInvalidatesIdentityAndPreservesConsumedMarker() {
        Uri identity = pending();
        PendingIntent.getBroadcast(context, 0, GoalNotifications.alarmIntent(context, identity),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String consumed = GoalNotifications.key("prior.db", 2, 500);
        assertTrue(preferences.edit().putStringSet(GoalNotifications.CONSUMED,
                new HashSet<>(Collections.singleton(consumed))).commit());
        GoalNotifications.cancelAll(context);
        assertTrue(pendingValues().isEmpty());
        assertNull(PendingIntent.getBroadcast(context, 0, GoalNotifications.alarmIntent(context, identity),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE));
        receive(identity);
        assertEquals(0, opened);
        assertEquals(Collections.singleton(consumed),
                preferences.getStringSet(GoalNotifications.CONSUMED, new HashSet<>()));
    }

    public void testMissingNotificationServiceIsDisabledAndConsumesOnlyOnce() {
        assertFalse(GoalNotifications.enabled(context));
        Uri identity = pending();
        receive(identity);
        assertTrue(pendingValues().isEmpty());
        assertEquals("Receiver lookup and next-alarm refill", 2, opened);
        receive(identity);
        assertEquals("Duplicate does not reopen a database", 2, opened);
    }

    public void testCurrentGuardRejectsStaleGenerationForTheSameAccount() {
        String account = AccountSession.databaseName(context);
        long generation = AccountSession.generation();
        assertTrue(GoalNotifications.isCurrent(context, account, generation));
        assertFalse(GoalNotifications.isCurrent(context, account, generation + 1));
    }

    public void testLogoutDuringLookupDoesNotConsumeReminder() {
        Uri identity = pending();
        exitOnOpen = true;
        receive(identity);
        assertFalse(AccountSession.isEntered(context));
        assertEquals(Collections.singleton(identity.toString()), pendingValues());
        assertTrue(preferences.getStringSet(GoalNotifications.CONSUMED, new HashSet<>()).isEmpty());
    }

    public void testLogoutDuringRescheduleDoesNotPublishNewAlarms() {
        due = 253402000000000L;
        exitOnOpen = true;
        reschedule();
        assertFalse(AccountSession.isEntered(context));
        assertTrue(pendingValues().isEmpty());
    }

    public void testManyKnownDeadlinesStayQueuedAndRefillAfterDelivery() {
        prepareFixture();
        String account = AccountSession.databaseName(context);
        Set<String> known = new HashSet<>();
        known.add(GoalNotifications.key(account, 11, 1000));
        try (SQLiteDatabase file = SQLiteDatabase.openOrCreateDatabase(
                getInstrumentation().getTargetContext().getDatabasePath(FIXTURE), null)) {
            for (int i = 1; i <= 100; i++) {
                long goal = 11 + i, deadline = 1000 + i;
                file.execSQL("INSERT INTO goals(id,friend,title,due_at,completed,created)"
                                + " VALUES(?,7,'Queued',?,0,0)",
                        new Object[]{goal, deadline});
                known.add(GoalNotifications.key(account, goal, deadline));
            }
        }
        assertTrue(preferences.edit().putStringSet(GoalNotifications.KNOWN, known).commit());
        GoalNotifications.reschedule(context);
        assertEquals(GoalNotifications.MAX_SCHEDULED, pendingValues().size());
        assertEquals(101, preferences.getStringSet(GoalNotifications.KNOWN,
                new HashSet<>()).size());
        String next = GoalNotifications.key(account, 75, 1064);
        assertFalse(pendingValues().stream().anyMatch(
                value -> next.equals(GoalNotifications.key(Uri.parse(value)))));
        Uri delivered = Uri.parse(pendingValues().stream().filter(
                value -> GoalNotifications.key(account, 11, 1000)
                        .equals(GoalNotifications.key(Uri.parse(value)))).findFirst().get());
        new GoalReminderReceiver().onReceive(context, GoalNotifications.alarmIntent(context, delivered));
        assertEquals(GoalNotifications.MAX_SCHEDULED, pendingValues().size());
        assertTrue(pendingValues().stream().anyMatch(
                value -> next.equals(GoalNotifications.key(Uri.parse(value)))));
    }

    public void testFixedTimeEligibilityRejectsEarlyChangedCompletedAndRemovedRows() {
        try (SQLiteDatabase db = memory()) {
            assertNull(GoalNotifications.findDue(db, 11, 1000, 999));
            assertNotNull(GoalNotifications.findDue(db, 11, 1000, 1000));
            assertNull(GoalNotifications.findDue(db, 11, 999, 1000));
            db.execSQL("UPDATE goals SET completed=1 WHERE id=11");
            assertNull(GoalNotifications.findDue(db, 11, 1000, 1000));
            db.execSQL("UPDATE goals SET completed=0,due_at=NULL WHERE id=11");
            assertNull(GoalNotifications.findDue(db, 11, 1000, 1000));
            db.execSQL("DELETE FROM friends WHERE id=7");
            assertNull(GoalNotifications.findDue(db, 11, 1000, 1000));
        }
    }
}
