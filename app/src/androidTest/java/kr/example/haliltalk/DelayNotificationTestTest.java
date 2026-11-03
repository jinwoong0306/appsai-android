package kr.example.haliltalk;

import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import java.util.Map;

public final class DelayNotificationTestTest extends InstrumentationTestCase {
    private Context context;
    private Context fixtureContext;
    private Context unavailableContext;
    private SharedPreferences fixture;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        assertFalse("Finish or cancel the manual test before running regression tests",
                "pending".equals(DelayNotificationTest.preferences(context).getString("state", "")));
        fixture = context.getSharedPreferences("delay_notification_test_regression", 0);
        assertTrue(fixture.edit().clear().commit());
        fixtureContext = new ContextWrapper(context) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return "delay_notification_test".equals(name) ? fixture
                        : super.getSharedPreferences(name, mode);
            }
        };
        unavailableContext = new ContextWrapper(fixtureContext) {
            @Override public Object getSystemService(String name) {
                if (Context.APP_OPS_SERVICE.equals(name)
                        || Context.NOTIFICATION_SERVICE.equals(name)) return null;
                return super.getSystemService(name);
            }
        };
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (fixtureContext != null) DelayNotificationTest.cancel(fixtureContext);
            if (fixture != null) assertTrue(fixture.edit().clear().commit());
        } finally {
            super.tearDown();
        }
    }

    private long pendingRun() {
        long started = SystemClock.elapsedRealtime();
        assertTrue(fixture.edit().putString("state", "pending")
                .putLong("started_elapsed", started)
                .putLong("started_wall", System.currentTimeMillis())
                .putLong("query_begin", System.currentTimeMillis() - 1000L)
                .putString("package", context.getPackageName())
                .putString("label", "Regression fixture")
                .putLong("before_usage", 123L).commit());
        return started;
    }

    private Intent alarmIntent(long started) {
        return new Intent(context, DelayNotificationReceiver.class)
                .setAction(DelayNotificationTest.ACTION).putExtra("started_elapsed", started);
    }

    public void testCancelInvalidatesAlarmAndPreservesResult() {
        long started = pendingRun();
        assertTrue(fixture.edit().putLong("fired_wall", 123L)
                .putLong("usage_delta", 45L).commit());
        PendingIntent.getBroadcast(context, DelayNotificationTest.NOTIFICATION_ID,
                alarmIntent(started), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        DelayNotificationTest.cancel(fixtureContext);
        assertEquals("cancelled", fixture.getString("state", ""));
        assertEquals(123L, fixture.getLong("fired_wall", -1L));
        assertEquals(45L, fixture.getLong("usage_delta", -1L));
        assertNull(PendingIntent.getBroadcast(context, DelayNotificationTest.NOTIFICATION_ID,
                alarmIntent(started), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE));
    }

    public void testStaleRunIsIgnored() {
        long started = pendingRun();
        Map<String, ?> before = fixture.getAll();
        new DelayNotificationReceiver().onReceive(unavailableContext, alarmIntent(started + 1L));
        assertEquals(before, fixture.getAll());
    }

    public void testDuplicateRunIsIgnored() {
        long started = pendingRun();
        assertTrue(fixture.edit().putString("state", "posted").putLong("fired_wall", 123L).commit());
        Map<String, ?> before = fixture.getAll();
        new DelayNotificationReceiver().onReceive(unavailableContext, alarmIntent(started));
        assertEquals(before, fixture.getAll());
    }

    public void testCancelledRunIsIgnored() {
        long started = pendingRun();
        DelayNotificationTest.cancel(fixtureContext);
        Map<String, ?> before = fixture.getAll();
        new DelayNotificationReceiver().onReceive(unavailableContext, alarmIntent(started));
        assertEquals(before, fixture.getAll());
    }

    public void testUnexpectedActionAndMissingIdentityAreIgnored() {
        long started = pendingRun();
        Map<String, ?> before = fixture.getAll();
        DelayNotificationReceiver receiver = new DelayNotificationReceiver();
        receiver.onReceive(unavailableContext, alarmIntent(started).setAction("unexpected"));
        receiver.onReceive(unavailableContext, new Intent(context, DelayNotificationReceiver.class)
                .setAction(DelayNotificationTest.ACTION));
        receiver.onReceive(unavailableContext, null);
        assertEquals(before, fixture.getAll());
    }

    public void testAcceptedRunStoresUnknownUsageAndOnlyFiresOnce() {
        long started = pendingRun();
        DelayNotificationReceiver receiver = new DelayNotificationReceiver();
        receiver.onReceive(unavailableContext, alarmIntent(started));
        assertEquals("blocked", fixture.getString("state", ""));
        assertTrue(fixture.getLong("fired_wall", -1L) > 0L);
        assertTrue(fixture.getLong("elapsed_ms", -1L) >= 0L);
        assertEquals(123L, fixture.getLong("before_usage", -1L));
        assertEquals(-1L, fixture.getLong("after_usage", 0L));
        assertEquals(-1L, fixture.getLong("usage_delta", 0L));
        Map<String, ?> after = fixture.getAll();
        receiver.onReceive(unavailableContext, alarmIntent(started));
        assertEquals(after, fixture.getAll());
    }

    public void testMissingUsageServiceReturnsUnknown() {
        assertFalse(DelayNotificationTest.hasUsageAccess(unavailableContext));
        assertEquals(-1L, DelayNotificationTest.usageMillis(unavailableContext,
                context.getPackageName(), 0L, System.currentTimeMillis()));
    }

    public void testUsageAccessMatchesAppOpsAndDenialReturnsUnknown() {
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
        boolean allowed = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        assertEquals(allowed, DelayNotificationTest.hasUsageAccess(context));
        if (!allowed) {
            assertEquals(-1L, DelayNotificationTest.usageMillis(context,
                    context.getPackageName(), 0L, System.currentTimeMillis()));
        }
    }

    public void testScheduleRejectsNotificationsUnavailableBeforeChangingRun() {
        pendingRun();
        Map<String, ?> before = fixture.getAll();
        try {
            DelayNotificationTest.schedule(unavailableContext, context.getPackageName(), "Fixture");
            fail("Scheduling requires notifications");
        } catch (SecurityException expected) {
            assertEquals(before, fixture.getAll());
        }
    }
}
