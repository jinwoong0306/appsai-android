package kr.example.haliltalk.ai;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.RequestOptions;
import java.util.List;
import java.util.Arrays;
import kr.example.haliltalk.BuildConfig;

public final class FirebaseAiChatService implements AiChatService {
    private final Context context;

    public FirebaseAiChatService(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override public boolean isConfigured() {
        return BuildConfig.FIREBASE_CONFIGURED && !FirebaseApp.getApps(context).isEmpty();
    }

    @Override public ListenableFuture<String> generate(String personality, List<Turn> turns) {
        Content instructions = new Content.Builder().addText(
                "너는 앱사이의 가상 친구 캐릭터다. 한국어로 짧고 자연스럽게 대화한다. "
                +"선택한 성격: "+personality+". "
                +"실제 사람이나 설치된 앱 자체라고 주장하지 않는다. 앱 사용량, 활동 내용, "
                +"기기 데이터는 제공되지 않았으므로 안다고 추측하지 않는다. "
                +"경험치, 목표 완료, 재화나 기기 데이터를 변경했다고 말하지 않는다.").build();
        GenerativeModel ai = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                .generativeModel("gemini-3.1-flash-lite", null, null,
                        null, null, instructions, new RequestOptions());
        GenerativeModelFutures model = GenerativeModelFutures.from(ai);
        Content[] contents = new Content[turns.size()];
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            contents[i] = new Content.Builder().setRole(turn.user ? "user" : "model")
                    .addText(turn.text).build();
        }
        return Futures.transform(model.generateContent(contents[0], Arrays.copyOfRange(contents, 1, contents.length)), response -> {
            String text = response.getText();
            if (text == null || text.trim().isEmpty()) throw new IllegalStateException("No text response");
            return text.trim();
        }, MoreExecutors.directExecutor());
    }
}
