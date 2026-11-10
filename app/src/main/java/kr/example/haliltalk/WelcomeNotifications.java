package kr.example.haliltalk;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

final class WelcomeNotifications {
    static final int PERMISSION_REQUEST = 120;
    static final int NOTIFICATION_ID = 701;
    static final String CHANNEL_ID = "app_friends";

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("welcome_notification", Context.MODE_PRIVATE);
    }

    static void requestOnFirstStart(Activity activity) {
        SharedPreferences preferences = preferences(activity);
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !preferences.getBoolean("permission_requested", false)) {
            preferences.edit().putBoolean("permission_requested", true).apply();
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST);
        } else {
            showOnce(activity);
        }
    }

    static boolean enabled(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        return manager.areNotificationsEnabled()
                && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static boolean showOnce(Context context) {
        return showOnce(context, preferences(context));
    }

    static boolean showOnce(Context context, SharedPreferences preferences) {
        if (preferences.getBoolean("welcome_sent", false) || !enabled(context)) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent openApp = PendingIntent.getActivity(context, NOTIFICATION_ID,
                new Intent(context, LoginActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.welcome_notification_title))
                .setContentText(context.getString(R.string.welcome_notification_body))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
        preferences.edit().putBoolean("welcome_sent", true).apply();
        return true;
    }
}
