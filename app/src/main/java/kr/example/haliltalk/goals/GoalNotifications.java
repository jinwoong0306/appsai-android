package kr.example.haliltalk.goals;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;

/** One-shot, inexact reminders. The database name binds every alarm to its account. */
public final class GoalNotifications {
    static final String ACTION = "kr.example.haliltalk.action.GOAL_REMINDER";
    static final String CHANNEL_ID = "goal_reminders";
    static final String PREFERENCES = "goal_reminders";
    static final String PENDING = "pending";
    static final String CONSUMED = "consumed";
    static final String KNOWN = "known";
    static final int NOTIFICATION_ID = 705;
    static final int MAX_SCHEDULED = 64;

    private GoalNotifications() {}

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static Intent alarmIntent(Context context, Uri identity) {
        return new Intent(context, GoalReminderReceiver.class).setAction(ACTION).setData(identity);
    }

    static String key(String account, long goal, long due) {
        return new Uri.Builder().scheme("appsai-goal").authority("reminder")
                .appendQueryParameter("account", account)
                .appendQueryParameter("goal", Long.toString(goal))
                .appendQueryParameter("due", Long.toString(due)).build().toString();
    }

    static String key(Uri identity) {
        return new Uri.Builder().scheme("appsai-goal").authority("reminder")
                .appendQueryParameter("account", identity.getQueryParameter("account"))
                .appendQueryParameter("goal", identity.getQueryParameter("goal"))
                .appendQueryParameter("due", identity.getQueryParameter("due")).build().toString();
    }

    /** Call after goal changes, account changes, and notification permission changes. */
    public static synchronized void reschedule(Context context) {
        Context app = context.getApplicationContext();
        if (!AccountSession.isEntered(app)) {
            cancelAll(app);
            return;
        }
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        long generation = AccountSession.generation();
        String account = AccountSession.databaseName(app);
        long now = System.currentTimeMillis();
        Set<String> old = new HashSet<>(preferences(app).getStringSet(PENDING, new HashSet<>()));
        Set<String> consumed = new HashSet<>(preferences(app).getStringSet(CONSUMED, new HashSet<>()));
        Set<String> known = new HashSet<>(preferences(app).getStringSet(KNOWN, new HashSet<>()));
        Map<String, String> previous = new HashMap<>();
        for (String value : old) previous.put(key(Uri.parse(value)), value);
        Set<String> active = new HashSet<>();
        Set<String> nextKnown = new HashSet<>();
        Set<String> identities = new HashSet<>();
        List<Scheduled> candidates = new ArrayList<>();
        try (FriendsDatabase helper = new FriendsDatabase(app);
                Cursor rows = helper.getReadableDatabase().rawQuery(
                        "SELECT g.id,g.due_at FROM goals g JOIN friends f ON f.id=g.friend"
                                + " WHERE g.completed=0 AND g.due_at IS NOT NULL", null)) {
            while (rows.moveToNext()) {
                long goal = rows.getLong(0), due = rows.getLong(1);
                String key = key(account, goal, due);
                active.add(key);
                String retained = previous.get(key);
                // 처음 발견한 미래 목표는 대기열에 기록해 슬롯이 차기 전 기한이 지나도 놓치지 않는다.
                // 새로 가져온 과거 목표는 대기열에 없으므로 울리지 않는다.
                if (shouldSchedule(due, now, retained != null || known.contains(key),
                        consumed.contains(key))) {
                    nextKnown.add(key);
                    candidates.add(new Scheduled(due, goal, retained != null ? retained
                            : Uri.parse(key).buildUpon()
                                    .appendQueryParameter("run", UUID.randomUUID().toString())
                                    .build().toString()));
                }
            }
        }
        if (!isCurrent(app, account, generation)) return;
        candidates.sort(Comparator.comparingLong((Scheduled item) -> item.due)
                .thenComparingLong(item -> item.goal));
        for (int i = 0; i < Math.min(MAX_SCHEDULED, candidates.size()); i++) {
            identities.add(candidates.get(i).identity);
        }
        for (String value : old) cancelAlarm(app, value);
        consumed.removeIf(value -> account.equals(Uri.parse(value).getQueryParameter("account"))
                && !active.contains(value));
        // 먼저 식별자를 보관하므로 예약 중 프로세스가 종료돼도 다음 실행에서 취소할 수 있다.
        if (!preferences(app).edit().putStringSet(PENDING, identities)
                .putStringSet(CONSUMED, consumed).putStringSet(KNOWN, nextKnown).commit()) return;
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager != null) {
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                if (CHANNEL_ID.equals(notification.getNotification().getChannelId())
                        && notification.getTag() != null
                        && !active.contains(key(Uri.parse(notification.getTag())))) {
                    manager.cancel(notification.getTag(), notification.getId());
                }
            }
        }
        for (String value : identities) {
            Uri identity = Uri.parse(value);
            PendingIntent alarm = PendingIntent.getBroadcast(app, 0, alarmIntent(app, identity),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    Math.max(now, Long.parseLong(identity.getQueryParameter("due"))), alarm);
        }
    }

    static boolean isCurrent(Context context, String account, long generation) {
        return generation == AccountSession.generation() && AccountSession.isEntered(context)
                && AccountSession.databaseName(context).equals(account);
    }

    private static final class Scheduled {
        final long due;
        final long goal;
        final String identity;
        Scheduled(long due, long goal, String identity) {
            this.due = due;
            this.goal = goal;
            this.identity = identity;
        }
    }

    static boolean shouldSchedule(long due, long now, boolean pending, boolean consumed) {
        return due >= 0 && !consumed && (due > now || pending);
    }

    /** Call before changing the active account; also removes that account's visible reminders. */
    public static synchronized void cancelAll(Context context) {
        for (String value : new HashSet<>(preferences(context)
                .getStringSet(PENDING, new HashSet<>()))) {
            cancelAlarm(context, value);
        }
        preferences(context).edit().remove(PENDING).remove(KNOWN).apply();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            for (StatusBarNotification notification : manager.getActiveNotifications()) {
                if (CHANNEL_ID.equals(notification.getNotification().getChannelId())) {
                    manager.cancel(notification.getTag(), notification.getId());
                }
            }
        }
    }

    static void cancelAlarm(Context context, String value) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent alarm = PendingIntent.getBroadcast(context, 0,
                alarmIntent(context, Uri.parse(value)),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (alarm != null) {
            if (alarms != null) alarms.cancel(alarm);
            alarm.cancel();
        }
    }

    /** Includes this feature's channel setting, not just the app-wide notification permission. */
    public static boolean enabled(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static Reminder findDue(SQLiteDatabase db, long goal, long due, long now) {
        if (goal <= 0 || due < 0 || due > now) return null;
        try (Cursor row = db.rawQuery("SELECT g.friend,g.title FROM goals g"
                + " JOIN friends f ON f.id=g.friend WHERE g.id=? AND g.completed=0 AND g.due_at=?",
                new String[]{Long.toString(goal), Long.toString(due)})) {
            return row.moveToFirst() ? new Reminder(row.getLong(0), row.getString(1)) : null;
        }
    }

    static final class Reminder {
        final long friend;
        final String title;
        Reminder(long friend, String title) { this.friend = friend; this.title = title; }
    }
}
