package kr.example.haliltalk;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;
import java.util.Calendar;
import java.util.List;

public final class DelayNotificationTest {
    static final String ACTION = "kr.example.haliltalk.action.DELAY_NOTIFICATION_TEST";
    static final String CHANNEL_ID = "delay_verification";
    static final int NOTIFICATION_ID = 702;
    static final long DELAY_MILLIS = 60000L;

    private DelayNotificationTest() {}

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("delay_notification_test", Context.MODE_PRIVATE);
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager manager = context.getSystemService(AppOpsManager.class);
        return manager != null && manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    public static boolean canSchedule(Context context) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return manager != null && manager.canScheduleExactAlarms();
    }

    public static long usageMillis(Context context, String pkg, long begin, long end) {
        if (!hasUsageAccess(context)) return -1L;
        UserManager user = context.getSystemService(UserManager.class);
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        if (manager == null || user == null || !user.isUserUnlocked()) return -1L;
        try {
            List<UsageStats> usage = manager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, begin, end);
            if (usage == null) return -1L;
            long total = 0L;
            for (UsageStats item : usage) {
                if (pkg.equals(item.getPackageName())) total += item.getTotalTimeInForeground();
            }
            return total;
        } catch (SecurityException unavailable) {
            return -1L;
        }
    }

    static boolean notificationsEnabled(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    public static synchronized void schedule(Context context, String pkg, String label) {
        if (pkg == null || pkg.isEmpty()) {
            throw new IllegalArgumentException("Select an app before starting the test");
        }
        if (!notificationsEnabled(context)) {
            throw new SecurityException("Notification permission is required");
        }
        if (!hasUsageAccess(context)) {
            throw new SecurityException("Usage access is required");
        }
        if (!canSchedule(context)) {
            throw new SecurityException("Exact alarm access is required");
        }
        long startedWall = System.currentTimeMillis();
        long startedElapsed = SystemClock.elapsedRealtime();
        Calendar midnight = Calendar.getInstance();
        midnight.setTimeInMillis(startedWall);
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        long queryBegin = midnight.getTimeInMillis();
        long beforeUsage = usageMillis(context, pkg, queryBegin, startedWall);
        cancelAlarm(context);
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        preferences(context).edit().clear()
                .putLong("started_wall", startedWall)
                .putLong("started_elapsed", startedElapsed)
                .putLong("due_wall", startedWall + DELAY_MILLIS)
                .putLong("query_begin", queryBegin)
                .putString("package", pkg)
                .putString("label", label)
                .putLong("before_usage", beforeUsage)
                .putString("state", "pending")
                .commit();
        PendingIntent alarm = PendingIntent.getBroadcast(context, NOTIFICATION_ID,
                new Intent(context, DelayNotificationReceiver.class).setAction(ACTION)
                        .putExtra("started_elapsed", startedElapsed),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            context.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, startedElapsed + DELAY_MILLIS, alarm);
            Log.i("AppsaiDelay", "DELAY_SCHEDULED started_wall=" + startedWall
                    + " due_wall=" + (startedWall + DELAY_MILLIS)
                    + " before_usage=" + beforeUsage);
        } catch (SecurityException revoked) {
            alarm.cancel();
            preferences(context).edit().putString("state", "blocked").commit();
            throw revoked;
        }
    }

    public static synchronized void cancel(Context context) {
        cancelAlarm(context);
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        preferences(context).edit().putString("state", "cancelled").commit();
    }

    private static void cancelAlarm(Context context) {
        PendingIntent alarm = PendingIntent.getBroadcast(context, NOTIFICATION_ID,
                new Intent(context, DelayNotificationReceiver.class).setAction(ACTION),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (alarm != null) {
            context.getSystemService(AlarmManager.class).cancel(alarm);
            alarm.cancel();
        }
    }
}
