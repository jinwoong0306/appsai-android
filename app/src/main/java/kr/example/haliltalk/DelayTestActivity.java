package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Explicit, one-shot verification tool; not an automatic usage reminder. */
public final class DelayTestActivity extends FeatureActivity {
    private final List<String> packages = new ArrayList<>(), labels = new ArrayList<>();
    private Spinner appChoice;
    private final SharedPreferences.OnSharedPreferenceChangeListener changed = (preferences, key) -> render();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.screen_delay_test);
        findViewById(R.id.delay_back).setOnClickListener(view -> finish());
        appChoice = findViewById(R.id.delay_app);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        apps.sort((a, b) -> a.loadLabel(getPackageManager()).toString()
                .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        Set<String> seen = new HashSet<>();
        for (ResolveInfo app : apps) {
            String pkg = app.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            packages.add(pkg);
            labels.add(app.loadLabel(getPackageManager()).toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        appChoice.setAdapter(adapter);
        int selected = packages.indexOf(DelayNotificationTest.preferences(this).getString("package", ""));
        if (selected >= 0) appChoice.setSelection(selected);
        findViewById(R.id.delay_permission).setOnClickListener(view -> openMissingPermission());
        findViewById(R.id.delay_start).setOnClickListener(view -> confirmStart());
        findViewById(R.id.delay_cancel).setOnClickListener(view -> {
            DelayNotificationTest.cancel(this);
            render();
        });
        findViewById(R.id.delay_open_app).setOnClickListener(view -> {
            String pkg = DelayNotificationTest.preferences(this).getString("package", "");
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) startActivity(intent);
        });
        findViewById(R.id.delay_refresh).setOnClickListener(view -> render());
    }

    private void openMissingPermission() {
        if (!DelayNotificationTest.notificationsEnabled(this)) {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } else if (!DelayNotificationTest.hasUsageAccess(this)) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        } else if (Build.VERSION.SDK_INT >= 31 && !DelayNotificationTest.canSchedule(this)) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    private void confirmStart() {
        int selected = appChoice.getSelectedItemPosition();
        if (selected < 0 || selected >= packages.size()) return;
        new AlertDialog.Builder(this).setTitle(R.string.delay_confirm_title)
                .setMessage(getString(R.string.delay_confirm_body, labels.get(selected)))
                .setNegativeButton(R.string.cancel_action, null)
                .setPositiveButton(R.string.delay_start, (dialog, which) -> {
                    try {
                        DelayNotificationTest.schedule(this, packages.get(selected), labels.get(selected));
                        render();
                    } catch (SecurityException | IllegalStateException error) {
                        new AlertDialog.Builder(this).setMessage(R.string.delay_permission_changed)
                                .setPositiveButton(android.R.string.ok, null).show();
                        render();
                    }
                }).show();
    }

    @Override protected void onResume() {
        super.onResume();
        DelayNotificationTest.preferences(this).registerOnSharedPreferenceChangeListener(changed);
        render();
    }

    @Override protected void onPause() {
        DelayNotificationTest.preferences(this).unregisterOnSharedPreferenceChangeListener(changed);
        super.onPause();
    }

    private void render() {
        if (appChoice == null) return;
        boolean notifications = DelayNotificationTest.notificationsEnabled(this);
        boolean usage = DelayNotificationTest.hasUsageAccess(this);
        boolean alarm = DelayNotificationTest.canSchedule(this);
        boolean ready = notifications && usage && alarm;
        TextView permission = findViewById(R.id.delay_permission);
        permission.setText(!notifications ? R.string.delay_allow_notifications
                : !usage ? R.string.delay_allow_usage : !alarm ? R.string.delay_allow_alarm : R.string.delay_ready);
        permission.setEnabled(!ready);
        SharedPreferences record = DelayNotificationTest.preferences(this);
        String state = record.getString("state", "idle");
        boolean pending = "pending".equals(state);
        findViewById(R.id.delay_start).setEnabled(ready && !pending && !packages.isEmpty());
        findViewById(R.id.delay_cancel).setEnabled(pending);
        findViewById(R.id.delay_open_app).setEnabled(record.contains("package"));
        appChoice.setEnabled(!pending);
        String result = getString(R.string.delay_no_record);
        if (record.contains("started_wall")) {
            String status = "pending".equals(state) ? "예약됨" : "posted".equals(state) ? "알림 발송"
                    : "cancelled".equals(state) ? "취소됨" : "blocked".equals(state) ? "알림 권한 차단" : state;
            result = "상태: " + status + "\n선택 앱: " + record.getString("label", "")
                    + "\n시작: " + time(record.getLong("started_wall", 0))
                    + "\n예약: " + time(record.getLong("due_wall", 0))
                    + "\n시작 사용시간: " + duration(record.getLong("before_usage", -1));
            if (record.contains("fired_wall")) {
                result += "\n실행: " + time(record.getLong("fired_wall", 0))
                        + "\n실제 경과: " + duration(record.getLong("elapsed_ms", -1))
                        + "\n백그라운드 조회: " + duration(record.getLong("after_usage", -1))
                        + "\n사용시간 증가: " + duration(record.getLong("usage_delta", -1));
            }
        }
        ((TextView)findViewById(R.id.delay_result)).setText(result);
    }

    private String time(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date(millis));
    }

    private String duration(long millis) {
        if (millis < 0) return "확인 불가";
        return String.format(Locale.KOREA, "%.1f초", millis / 1000.0);
    }
}
