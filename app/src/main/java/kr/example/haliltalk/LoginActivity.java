package kr.example.haliltalk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import kr.example.haliltalk.character.CharacterView;

/** Local demo session only; no account creation or authentication. */
public final class LoginActivity extends FeatureActivity {
    static SharedPreferences session(Context context) {
        return context.getSharedPreferences("mock_session", MODE_PRIVATE);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (session(this).getBoolean("signed_in", false)) {
            openHome();
            return;
        }
        setContentView(R.layout.screen_login);
        ((CharacterView)findViewById(R.id.login_character))
                .setAppearance("chat", "none", "none", "none");
        findViewById(R.id.login_button).setOnClickListener(view -> {
            session(this).edit().putBoolean("signed_in", true).apply();
            openHome();
        });
        WelcomeNotifications.requestOnFirstStart(this);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == WelcomeNotifications.PERMISSION_REQUEST) {
            WelcomeNotifications.showOnce(this);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        WelcomeNotifications.showOnce(this);
    }
}
