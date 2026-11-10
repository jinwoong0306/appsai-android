package kr.example.haliltalk;

import android.app.Application;
import com.google.firebase.FirebaseApp;
import kr.example.haliltalk.ai.ChatController;
import kr.example.haliltalk.ai.FirebaseAiChatService;

public final class ChatApplication extends Application {
    ChatController chatController;

    @Override public void onCreate() {
        super.onCreate();
        if (BuildConfig.FIREBASE_CONFIGURED && !FirebaseApp.getApps(this).isEmpty()) {
            AppCheckProvider.install();
        }
    }

    public ChatController chat() {
        if (chatController == null) {
            chatController = new ChatController(this, new FirebaseAiChatService(this));
        }
        return chatController;
    }
}
