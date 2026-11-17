package kr.example.haliltalk;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.util.Patterns;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.character.CharacterView;

/** Explicit offline guest entry and real Firebase email/password authentication. */
public final class LoginActivity extends FeatureActivity {
    private EditText email, password;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (AccountSession.isEntered(this)) {
            openHome();
            return;
        }
        setContentView(R.layout.screen_login);
        ((CharacterView)findViewById(R.id.login_character))
                .setAppearance("chat", "none", "none", "none");
        email = findViewById(R.id.auth_email);
        password = findViewById(R.id.auth_password);
        status = findViewById(R.id.auth_status);
        findViewById(R.id.login_button).setOnClickListener(view -> authenticate(false));
        findViewById(R.id.auth_register).setOnClickListener(view -> authenticate(true));
        findViewById(R.id.auth_guest).setOnClickListener(view -> {
            cancelAutofill();
            AccountSession.enterGuest(this);
            openHome();
        });
        renderBusy(AccountSession.isAuthenticating());
        WelcomeNotifications.requestOnFirstStart(this);
    }

    @Override protected void onStart() {
        super.onStart();
        if (status == null) return;
        if (AccountSession.isEntered(this)) {
            openHome();
        } else if (AccountSession.authentication() != null) {
            observe(AccountSession.authentication());
        }
    }

    private void authenticate(boolean create) {
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches() || secret.isEmpty()) {
            cancelAutofill();
            status.setText(R.string.auth_invalid_input);
            return;
        }
        if (create && secret.length() < 6) {
            cancelAutofill();
            status.setText(R.string.auth_weak_password);
            return;
        }
        renderBusy(true);
        // 비밀번호는 Firebase SDK에만 전달하고 파일·로그·화면 저장 상태에는 남기지 않는다.
        observe(AccountSession.authenticate(this, address, secret, create));
        password.setText("");
    }

    private void observe(Task<AuthResult> task) {
        renderBusy(!task.isComplete());
        task.addOnCompleteListener(this, result -> {
            renderBusy(false);
            if (result.isSuccessful()) {
                openHome();
            } else {
                cancelAutofill();
                Exception error = result.getException();
                status.setText(error instanceof FirebaseNetworkException ? R.string.auth_network_error
                        : error instanceof FirebaseAuthWeakPasswordException ? R.string.auth_weak_password
                        : R.string.auth_failed);
            }
        });
    }

    private void renderBusy(boolean busy) {
        boolean configured = AccountSession.isConfigured(this);
        findViewById(R.id.login_button).setEnabled(configured && !busy);
        findViewById(R.id.auth_register).setEnabled(configured && !busy);
        findViewById(R.id.auth_guest).setEnabled(!busy);
        email.setEnabled(!busy);
        password.setEnabled(!busy);
        status.setText(busy ? R.string.auth_working
                : configured ? R.string.auth_account_notice : R.string.auth_unconfigured);
    }

    private void cancelAutofill() {
        android.view.autofill.AutofillManager manager=getSystemService(android.view.autofill.AutofillManager.class);
        if(manager!=null)manager.cancel();
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
