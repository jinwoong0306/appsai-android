package kr.example.haliltalk.auth;

import android.content.Context;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.MemoryCacheSettings;
import com.google.firebase.firestore.Source;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONException;
import kr.example.haliltalk.R;
import kr.example.haliltalk.data.FriendsDatabase;

/** Only explicit, confirmed actions reach this service; no automatic synchronization. */
public final class CloudBackup {
    private static boolean configured;
    private static Task<Boolean> operation;

    private CloudBackup() {}

    public static Task<Boolean> operation() { return operation; }
    public static boolean isBusy() { return operation != null && !operation.isComplete(); }
    public static void consume(Task<Boolean> task) {
        if (operation == task && task.isComplete()) operation = null;
    }

    private static FirebaseFirestore database() {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        if (!configured) {
            // 계정 간 디스크 캐시를 남기지 않는다. 복원은 아래에서 서버 원본만 읽는다.
            firestore.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build());
            configured = true;
        }
        return firestore;
    }

    public static Task<Boolean> save(Context context) {
        if (isBusy()) return operation;
        Context app = context.getApplicationContext();
        String uid = AccountSession.currentUid(app);
        long generation = AccountSession.generation();
        if (uid == null) return operation = Tasks.forException(new Failure(R.string.auth_cloud_requires_account));
        try {
            final String snapshot;
            try (FriendsDatabase helper = new FriendsDatabase(app)) {
                snapshot = FriendGoalSnapshot.capture(helper.getReadableDatabase());
            }
            DocumentReference document = database().collection("users").document(uid).collection("backups").document("current");
            Map<String, Object> fields = new HashMap<>();
            fields.put("version", 1);
            fields.put("snapshot", snapshot);
            fields.put("savedAt", FieldValue.serverTimestamp());
            // 트랜잭션은 오프라인 쓰기 대기열에 저장하지 않는다. 실패를 성공으로 표시하지 않는다.
            operation = database().runTransaction(transaction -> {
                checkSession(app, uid, generation);
                transaction.get(document);
                transaction.set(document, fields);
                return false;
            }).continueWith(task -> {
                task.getResult();
                checkSession(app, uid, generation);
                return false;
            });
        } catch (Exception error) {
            operation = Tasks.forException(error);
        }
        return operation;
    }

    public static Task<Boolean> restore(Context context) {
        if (isBusy()) return operation;
        Context app = context.getApplicationContext();
        String uid = AccountSession.currentUid(app);
        long generation = AccountSession.generation();
        if (uid == null) return operation = Tasks.forException(new Failure(R.string.auth_cloud_requires_account));
        // 서버 응답을 기다리기 전에 대상 DB 이름을 고정해 새 계정 DB로 복원되지 않게 한다.
        FriendsDatabase target = new FriendsDatabase(app);
        try {
            operation = database().collection("users").document(uid).collection("backups").document("current")
                .get(Source.SERVER).continueWith(task -> {
                    try (FriendsDatabase helper = target) {
                    checkSession(app, uid, generation);
                    com.google.firebase.firestore.DocumentSnapshot document = task.getResult();
                    if (!document.exists()) throw new Failure(R.string.auth_no_backup);
                    if (!Long.valueOf(1).equals(document.getLong("version"))) {
                        throw new Failure(R.string.auth_invalid_backup);
                    }
                    try {
                        FriendGoalSnapshot.restore(helper.getWritableDatabase(), document.getString("snapshot"));
                    } catch (IllegalStateException notEmpty) {
                        throw new Failure(R.string.auth_restore_not_empty);
                    }
                    return true;
                    }
                });
        } catch (RuntimeException error) {
            target.close();
            operation = Tasks.forException(error);
        }
        return operation;
    }

    private static void checkSession(Context context, String uid, long generation) {
        if (generation != AccountSession.generation() || !uid.equals(AccountSession.currentUid(context))) {
            throw new IllegalStateException("account_changed");
        }
    }

    public static int errorMessage(Exception error) {
        if (error instanceof Failure) return ((Failure) error).messageId;
        if (error instanceof JSONException || error instanceof IllegalArgumentException) return R.string.auth_invalid_backup;
        if (error instanceof IllegalStateException) return R.string.auth_account_changed;
        return R.string.auth_cloud_failed;
    }

    private static final class Failure extends Exception {
        final int messageId;
        Failure(int messageId) { this.messageId = messageId; }
    }
}
