package kr.example.haliltalk.goals;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.HashSet;
import java.util.Set;
import kr.example.haliltalk.R;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;

public final class GoalReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !GoalNotifications.ACTION.equals(intent.getAction())
                || intent.getData() == null) return;
        synchronized (GoalNotifications.class) {
            Uri identity = intent.getData();
            String account = identity.getQueryParameter("account");
            long generation = AccountSession.generation();
            Set<String> pending = new HashSet<>(GoalNotifications.preferences(context)
                    .getStringSet(GoalNotifications.PENDING, new HashSet<>()));
            if (!pending.contains(identity.toString())
                    || !GoalNotifications.isCurrent(context, account, generation)) return;
            long goal, due;
            try {
                goal = Long.parseLong(identity.getQueryParameter("goal"));
                due = Long.parseLong(identity.getQueryParameter("due"));
            } catch (NumberFormatException invalid) { return; }
            GoalNotifications.Reminder reminder;
            try (FriendsDatabase helper = new FriendsDatabase(context)) {
                reminder = GoalNotifications.findDue(helper.getReadableDatabase(), goal, due,
                        System.currentTimeMillis());
            }
            if (reminder == null || !GoalNotifications.isCurrent(context, account, generation)) return;
            // 완료/삭제/기한 변경/계정 전환을 다시 확인한 뒤 1회만 소비한다. 보상은 변경하지 않는다.
            pending.remove(identity.toString());
            Set<String> consumed = new HashSet<>(GoalNotifications.preferences(context)
                    .getStringSet(GoalNotifications.CONSUMED, new HashSet<>()));
            if (!consumed.add(GoalNotifications.key(identity))) return;
            if (!GoalNotifications.preferences(context).edit()
                    .putStringSet(GoalNotifications.PENDING, pending)
                    .putStringSet(GoalNotifications.CONSUMED, consumed).commit()) return;
            GoalNotifications.cancelAlarm(context, identity.toString());
            // 소비한 알람의 슬롯을 채운다. 대기하던 목표는 기한이 지나도 별도 기록으로 보존한다.
            GoalNotifications.reschedule(context);
            if (!GoalNotifications.enabled(context)) return;
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(GoalNotifications.CHANNEL_ID,
                    context.getString(R.string.transfer_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
            Intent open = new Intent().setClassName(context, "kr.example.haliltalk.GoalsActivity")
                    .setData(identity).putExtra("friend_id", reminder.friend)
                    .putExtra("goal_account", identity.getQueryParameter("account"))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent destination = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(context, GoalNotifications.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.transfer_reminder_title))
                    .setContentText(reminder.title)
                    .setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setContentIntent(destination).setAutoCancel(true).build();
            if (!GoalNotifications.isCurrent(context, account, generation)) return;
            try {
                manager.notify(identity.toString(), GoalNotifications.NOTIFICATION_ID, notification);
            } catch (SecurityException revoked) {
                // 검사 직후 권한이 철회돼도 재요청하거나 목표 내용을 로그에 남기지 않는다.
            }
        }
    }
}
