package kr.example.haliltalk;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.app.AlertDialog;
import android.widget.TextView;

public final class SettingsActivity extends FeatureActivity {
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
        findViewById(R.id.mock_logout).setOnClickListener(view ->
                new AlertDialog.Builder(this).setTitle(R.string.mock_logout)
                        .setMessage(R.string.mock_logout_message)
                        .setNegativeButton(R.string.cancel_action, null)
                        .setPositiveButton(R.string.mock_logout, (dialog, which) -> {
                            LoginActivity.session(this).edit().putBoolean("signed_in", false).apply();
                            startActivity(new Intent(this, LoginActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        }).show());
    }

    @Override protected void onResume() {
        super.onResume();
        WelcomeNotifications.showOnce(this);
        ((TextView)findViewById(R.id.notification_status)).setText(
                WelcomeNotifications.enabled(this) ? R.string.notifications_enabled : R.string.notifications_disabled);
    }
}
