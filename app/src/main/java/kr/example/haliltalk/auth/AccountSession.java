package kr.example.haliltalk.auth;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import kr.example.haliltalk.BuildConfig;
import kr.example.haliltalk.ChatApplication;

/** Firebase identity selects storage; local guest data is never migrated into an account. */
public final class AccountSession {
    private static Task<AuthResult> authentication;
    private static volatile long generation;

    private AccountSession() {}

    public static boolean isConfigured(Context context) {
        return BuildConfig.FIREBASE_CONFIGURED && !FirebaseApp.getApps(context).isEmpty();
    }

    public static FirebaseUser user(Context context) {
        return isConfigured(context) ? FirebaseAuth.getInstance().getCurrentUser() : null;
    }

    public static String currentUid(Context context) {
        FirebaseUser user = user(context);
        return user == null ? null : user.getUid();
    }

    public static boolean isEntered(Context context) {
        return currentUid(context) != null || context.getSharedPreferences("account_session", 0)
                .getBoolean("guest_entered", false);
    }

    public static String databaseName(Context context) {
        return databaseNameForUid(currentUid(context));
    }

    static String databaseNameForUid(String uid) {
        if (uid == null) return "friends.db";
        try {
            // UID 원문을 경로에 넣지 않는다. 긴 UID·슬래시도 고정 길이 파일명으로 분리한다.
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder("account_");
            for (byte value : hash) {
                name.append(Character.forDigit((value >>> 4) & 15, 16));
                name.append(Character.forDigit(value & 15, 16));
            }
            return name.append(".db").toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static String preferencesName(Context context, String base) {
        return currentUid(context) == null ? base : base + "_" + databaseName(context);
    }

    public static long generation() { return generation; }

    /** Main-thread boundary: invalidate callbacks before any identity change. */
    public static void beforeTransition(Context context) {
        generation++;
        ((ChatApplication) context.getApplicationContext()).resetChat();
    }

    public static Task<AuthResult> authentication() { return authentication; }

    public static boolean isAuthenticating() {
        return authentication != null && !authentication.isComplete();
    }

    public static Task<AuthResult> authenticate(Context context, String email, String password, boolean create) {
        if (isAuthenticating()) return authentication;
        if (!isConfigured(context)) throw new IllegalStateException("Firebase is not configured");
        Context app = context.getApplicationContext();
        beforeTransition(app);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        authentication = create ? auth.createUserWithEmailAndPassword(email, password)
                : auth.signInWithEmailAndPassword(email, password);
        authentication.addOnSuccessListener(result ->
                app.getSharedPreferences("account_session", 0).edit().putBoolean("guest_entered", false).apply());
        return authentication;
    }

    public static void enterGuest(Context context) {
        if (isAuthenticating()) throw new IllegalStateException("Authentication is in progress");
        beforeTransition(context);
        if (isConfigured(context)) FirebaseAuth.getInstance().signOut();
        authentication = null;
        context.getSharedPreferences("account_session", 0).edit().putBoolean("guest_entered", true).apply();
    }

    public static void signOut(Context context) {
        if (isAuthenticating()) throw new IllegalStateException("Authentication is in progress");
        beforeTransition(context);
        if (isConfigured(context)) FirebaseAuth.getInstance().signOut();
        authentication = null;
        // 로그아웃은 진입 상태만 지운다. 계정별 DB와 기존 게스트 DB는 보존한다.
        context.getSharedPreferences("account_session", 0).edit().putBoolean("guest_entered", false).apply();
    }
}
