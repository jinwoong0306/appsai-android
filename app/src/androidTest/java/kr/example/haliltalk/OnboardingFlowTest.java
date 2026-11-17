package kr.example.haliltalk;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import java.util.Map;
import kr.example.haliltalk.auth.AccountSession;

public final class OnboardingFlowTest extends InstrumentationTestCase {
    Context context;
    SharedPreferences session, welcome, main;
    Map<String, ?> savedSession, savedWelcome;
    boolean hadWelcome, welcomeDone;
    Activity activity;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        assertNull("Do not alter a signed-in account", AccountSession.user(context));
        session = context.getSharedPreferences("account_session", 0);
        welcome = WelcomeNotifications.preferences(context);
        main = context.getSharedPreferences("MainActivity", 0);
        savedSession = session.getAll();
        savedWelcome = welcome.getAll();
        hadWelcome = main.contains("welcome_done");
        welcomeDone = main.getBoolean("welcome_done", false);
        assertTrue(session.edit().putBoolean("guest_entered", false).commit());
        assertTrue(welcome.edit().putBoolean("permission_requested", true)
                .putBoolean("welcome_sent", true).commit());
        assertTrue(main.edit().putBoolean("welcome_done", true).commit());
    }

    @Override protected void tearDown() throws Exception {
        try {
            getInstrumentation().runOnMainSync(() -> {
                if (activity != null) activity.finish();
            });
            getInstrumentation().waitForIdleSync();
            restoreBoolean(session, savedSession, "guest_entered");
            restoreBoolean(welcome, savedWelcome, "permission_requested");
            restoreBoolean(welcome, savedWelcome, "welcome_sent");
            SharedPreferences.Editor editor = main.edit();
            if (hadWelcome) editor.putBoolean("welcome_done", welcomeDone);
            else editor.remove("welcome_done");
            assertTrue(editor.commit());
        } finally { super.tearDown(); }
    }

    private void restoreBoolean(SharedPreferences preferences, Map<String, ?> saved, String key) {
        SharedPreferences.Editor editor = preferences.edit();
        if (saved.containsKey(key)) editor.putBoolean(key, (Boolean)saved.get(key));
        else editor.remove(key);
        assertTrue(editor.commit());
    }

    public void testExplicitGuestButtonAndRememberedSession() throws Exception {
        activity = getInstrumentation().startActivitySync(new Intent(context, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(activity instanceof LoginActivity);
        Instrumentation.ActivityMonitor monitor = getInstrumentation()
                .addMonitor(MainActivity.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(() ->
                    activity.findViewById(R.id.auth_guest).performClick());
            activity = getInstrumentation().waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("Login must open home", activity);
            getInstrumentation().waitForIdleSync();
            assertTrue(session.getBoolean("guest_entered", false));
            getInstrumentation().runOnMainSync(() -> assertNotNull(activity.findViewById(R.id.home_character)));
            getInstrumentation().runOnMainSync(() -> activity.finish());
            getInstrumentation().waitForIdleSync();
            getInstrumentation().removeMonitor(monitor);
            monitor = getInstrumentation().addMonitor(MainActivity.class.getName(), null, false);
            context.startActivity(new Intent(context, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            activity = getInstrumentation().waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("Remembered session must open home without another login", activity);
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(() -> assertNotNull(activity.findViewById(R.id.home_character)));
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    public void testInvalidCredentialsDoNotEnterSession() throws Exception {
        activity = getInstrumentation().startActivitySync(new Intent(context, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        assertTrue(activity instanceof LoginActivity);
        getInstrumentation().runOnMainSync(() -> {
            if (AccountSession.isConfigured(context)) {
                activity.findViewById(R.id.login_button).performClick();
                assertEquals(context.getString(R.string.auth_invalid_input),
                        ((android.widget.TextView)activity.findViewById(R.id.auth_status)).getText().toString());
            } else {
                assertFalse(activity.findViewById(R.id.login_button).isEnabled());
            }
        });
        assertFalse(session.getBoolean("guest_entered", false));
    }

    public void testWelcomeNotificationPostedOnlyOnce() {
        SharedPreferences fixture = context.getSharedPreferences("onboarding_notification_test", 0);
        fixture.edit().clear().commit();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        try {
            assertTrue("Grant notification permission before this test", WelcomeNotifications.enabled(context));
            assertTrue(WelcomeNotifications.showOnce(context, fixture));
            assertTrue(fixture.getBoolean("welcome_sent", false));
            assertFalse(WelcomeNotifications.showOnce(context, fixture));
        } finally {
            manager.cancel(WelcomeNotifications.NOTIFICATION_ID);
            fixture.edit().clear().commit();
        }
    }
}
