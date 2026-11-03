package kr.example.haliltalk;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

public final class DelayNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !DelayNotificationTest.ACTION.equals(intent.getAction())) return;
        synchronized (DelayNotificationTest.class) {
            SharedPreferences preferences = DelayNotificationTest.preferences(context);
            long startedElapsed = preferences.getLong("started_elapsed", -1L);
            if (!"pending".equals(preferences.getString("state", ""))
                    || startedElapsed < 0L
                    || intent.getLongExtra("started_elapsed", -1L) != startedElapsed) return;
            long firedWall = System.currentTimeMillis();
            long elapsed = SystemClock.elapsedRealtime() - startedElapsed;
            String pkg = preferences.getString("package", "");
            long before = preferences.getLong("before_usage", -1L);
            long after = DelayNotificationTest.usageMillis(context, pkg,
                    preferences.getLong("query_begin", firedWall), firedWall);
            long delta = before < 0L || after < 0L || after < before ? -1L : after - before;
            preferences.edit().putLong("fired_wall", firedWall)
                    .putLong("elapsed_ms", elapsed)
                    .putLong("after_usage", after)
                    .putLong("usage_delta", delta)
                    .putString("state", "blocked").commit();
            String state = "blocked";
            if (DelayNotificationTest.notificationsEnabled(context)) {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                manager.createNotificationChannel(new NotificationChannel(
                        DelayNotificationTest.CHANNEL_ID, "1분 알림 테스트",
                        NotificationManager.IMPORTANCE_DEFAULT));
                PendingIntent openTest = PendingIntent.getActivity(context,
                        DelayNotificationTest.NOTIFICATION_ID,
                        new Intent().setClassName(context, "kr.example.haliltalk.DelayTestActivity")
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                Notification notification = new Notification.Builder(
                        context, DelayNotificationTest.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("1분 알림 테스트")
                        .setContentText(after < 0L
                                ? "선택한 앱의 사용량을 확인할 수 없습니다. 테스트 결과를 열어 보세요."
                                : "선택한 앱의 사용량을 확인했습니다. 테스트 결과를 열어 보세요.")
                        .setContentIntent(openTest)
                        .setAutoCancel(true)
                        .build();
                try {
                    manager.notify(DelayNotificationTest.NOTIFICATION_ID, notification);
                    state = "posted";
                    preferences.edit().putString("state", state).commit();
                } catch (SecurityException revoked) {
                    // The recorded result remains blocked if permission changed during delivery.
                }
            }
            Log.i("AppsaiDelay", "DELAY_FIRED elapsed_ms=" + elapsed
                    + " before_usage=" + before + " after_usage=" + after
                    + " usage_delta=" + delta + " state=" + state);
        }
    }
}
