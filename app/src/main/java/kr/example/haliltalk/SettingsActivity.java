package kr.example.haliltalk;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

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
    }
}
