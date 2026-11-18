package kr.example.haliltalk;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.goals.GoalNotifications;
import kr.example.haliltalk.ai.ChatController;
import kr.example.haliltalk.ai.FirebaseAiChatService;

public final class ChatApplication extends Application {
    ChatController chatController;
    String chatDatabase;
    private String accountDatabase;

    @Override public void onCreate() {
        super.onCreate();
        if (BuildConfig.FIREBASE_CONFIGURED && !FirebaseApp.getApps(this).isEmpty()) {
            AppCheckProvider.install();
            accountDatabase = AccountSession.databaseName(this);
            FirebaseAuth.getInstance().addAuthStateListener(auth -> {
                String selected = AccountSession.databaseName(this);
                if (!selected.equals(accountDatabase)) {
                    AccountSession.beforeTransition(this);
                    accountDatabase = selected;
                    if (AccountSession.isEntered(this)) GoalNotifications.reschedule(this);
                }
            });
        }
    }

    public ChatController chat() {
        String selected = AccountSession.databaseName(this);
        if (chatController != null && !selected.equals(chatDatabase)) resetChat();
        if (chatController == null) {
            chatController = new ChatController(this, new FirebaseAiChatService(this));
            chatDatabase = selected;
        }
        return chatController;
    }

    /** Close old account requests and reopen storage for the selected account. */
    public void resetChat() {
        if (chatController != null) chatController.close();
        chatController = null;
        chatDatabase = null;
    }
}
