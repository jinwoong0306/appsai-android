package kr.example.haliltalk.goals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

/** Epoch deadlines remain unchanged on timezone changes; only their alarms are rebuilt. */
public final class GoalBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        UserManager user = context.getSystemService(UserManager.class);
        if (user != null && user.isUserUnlocked()) GoalNotifications.reschedule(context);
    }
}
