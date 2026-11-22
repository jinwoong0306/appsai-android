package kr.example.haliltalk;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.app.AlertDialog;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.auth.CloudBackup;

public final class SettingsActivity extends FeatureActivity {
    private Task<Boolean> observed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.screen_settings);
        findViewById(R.id.settings_back).setOnClickListener(view->finish());
        findViewById(R.id.add_friend).setOnClickListener(view->{
            startActivity(new Intent(this,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("destination","new_friend"));
            finish();
        });
        findViewById(R.id.usage_settings).setOnClickListener(view->
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        findViewById(R.id.notification_settings).setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        findViewById(R.id.delay_test).setOnClickListener(view ->
                startActivity(new Intent(this, DelayTestActivity.class)));
        findViewById(R.id.auth_file_transfer).setOnClickListener(view ->
                startActivity(new Intent(this, TransferActivity.class)));
        findViewById(R.id.auth_cloud_save).setOnClickListener(view -> confirmCloud(false));
        findViewById(R.id.auth_cloud_restore).setOnClickListener(view -> confirmCloud(true));
        findViewById(R.id.font_licenses).setOnClickListener(view->{
            StringBuilder notice=new StringBuilder("제목·버튼: 주아\n대화·본문: 고운돋움\n\n");
            try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(
                    getAssets().open("font_licenses.txt"),java.nio.charset.StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null)notice.append(line).append('\n');
                new AlertDialog.Builder(this).setTitle(R.string.font_licenses)
                        .setMessage(notice.toString()).setPositiveButton("닫기",null).show();
            }catch(java.io.IOException error){Toast.makeText(this,"글꼴 정보를 불러오지 못했어요.",Toast.LENGTH_LONG).show();}
        });
        findViewById(R.id.mock_logout).setOnClickListener(view ->
                new AlertDialog.Builder(this).setTitle(R.string.auth_logout)
                        .setMessage(R.string.auth_logout_confirm)
                        .setNegativeButton(R.string.cancel_action, null)
                        .setPositiveButton(R.string.auth_logout, (dialog, which) -> {
                            AccountSession.signOut(this);
                            startActivity(new Intent(this, LoginActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        }).show());
    }

    private void confirmCloud(boolean restore) {
        new AlertDialog.Builder(this)
                .setTitle(restore ? R.string.auth_cloud_restore : R.string.auth_cloud_save)
                .setMessage(restore ? R.string.auth_cloud_restore_confirm : R.string.auth_cloud_save_confirm)
                .setNegativeButton(R.string.cancel_action, null)
                .setPositiveButton(restore ? R.string.auth_cloud_restore : R.string.auth_cloud_save,
                        (dialog, which) -> observe(restore ? CloudBackup.restore(this) : CloudBackup.save(this)))
                .show();
    }

    private void observe(Task<Boolean> task) {
        renderAccount();
        if (observed == task) return;
        observed = task;
        task.addOnCompleteListener(this, result -> {
            if (CloudBackup.operation() != task) return;
            CloudBackup.consume(task);
            renderAccount();
            TextView status = findViewById(R.id.auth_cloud_status);
            if (!result.isSuccessful()) {
                status.setText(CloudBackup.errorMessage(result.getException()));
            } else if (Boolean.TRUE.equals(result.getResult())) {
                Toast.makeText(this, R.string.auth_cloud_restored, Toast.LENGTH_LONG).show();
                // 복원 전 MainActivity가 쥔 DB·컨트롤러를 재사용하지 않는다.
                ((ChatApplication)getApplication()).resetChat();
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            } else {
                status.setText(R.string.auth_cloud_saved);
            }
        });
    }

    private void renderAccount() {
        FirebaseUser user = AccountSession.user(this);
        boolean busy = CloudBackup.isBusy();
        ((TextView)findViewById(R.id.auth_account_status)).setText(user == null
                ? getString(R.string.auth_guest_status)
                : getString(R.string.auth_signed_in_status, user.getEmail()));
        findViewById(R.id.auth_cloud_save).setEnabled(user != null && !busy);
        findViewById(R.id.auth_cloud_restore).setEnabled(user != null && !busy);
        findViewById(R.id.mock_logout).setEnabled(!busy);
        ((TextView)findViewById(R.id.auth_cloud_status)).setText(busy
                ? R.string.auth_cloud_working : user == null ? R.string.auth_cloud_requires_account
                : R.string.auth_account_notice);
    }

    @Override protected void onStart() {
        super.onStart();
        renderAccount();
        if (CloudBackup.operation() != null) observe(CloudBackup.operation());
    }

    @Override protected void onStop() {
        observed = null;
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        WelcomeNotifications.showOnce(this);
        ((TextView)findViewById(R.id.notification_status)).setText(
                WelcomeNotifications.enabled(this) ? R.string.notifications_enabled : R.string.notifications_disabled);
    }
}
