package kr.example.haliltalk.ai;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;

/** The only external input is selected personality and consented chat text. */
public interface AiChatService {
    boolean isConfigured();
    ListenableFuture<String> generate(String personality, List<Turn> turns);

    final class Turn {
        public final boolean user;
        public final String text;

        public Turn(boolean user, String text) {
            this.user = user;
            this.text = text;
        }
    }
}
