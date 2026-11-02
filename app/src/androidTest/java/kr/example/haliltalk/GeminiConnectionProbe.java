package kr.example.haliltalk;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.FirebaseAppCheck;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.ai.AiChatService;
import kr.example.haliltalk.ai.FirebaseAiChatService;

/** Manually invoked network diagnostic, never discovered by the regression test runner. */
public final class GeminiConnectionProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        String stage = "app_check";
        try {
            Tasks.await(FirebaseAppCheck.getInstance().getAppCheckToken(false),
                    25, TimeUnit.SECONDS);
            Bundle progress = new Bundle();
            progress.putString("stage", "APP_CHECK_ACCEPTED");
            sendStatus(0, progress);
            stage = "gemini";
            String reply = new FirebaseAiChatService(getTargetContext())
                    .generate("다정한 친구", Collections.singletonList(
                            new AiChatService.Turn(true,
                                    "This is a synthetic connection test. Say hello in Korean.")))
                    .get(65, TimeUnit.SECONDS);
            result.putString("stage", "GEMINI_RESPONSE_RECEIVED");
            result.putInt("response_characters", reply.length());
            finish(Activity.RESULT_OK, result);
        } catch (Exception error) {
            StringBuilder types = new StringBuilder();
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (types.length() > 0) types.append(" -> ");
                types.append(cause.getClass().getSimpleName());
            }
            result.putString("stage", stage);
            result.putString("failure_types", types.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
