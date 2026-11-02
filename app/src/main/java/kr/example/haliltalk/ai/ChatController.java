package kr.example.haliltalk.ai;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import kr.example.haliltalk.data.FriendsDatabase;

/** Application-scoped requests survive Activity rotation and navigation. Call on the main thread. */
public final class ChatController implements AutoCloseable {
    public static final int MAX_MESSAGE_CHARS = 2000;
    public static final int MAX_CONTEXT_TURNS = 13;
    public static final int MAX_CONTEXT_CHARS = 12000;
    private final SQLiteDatabase db;
    private final FriendsDatabase helper;
    private final AiChatService service;
    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Runnable> listeners = new HashSet<>();
    private final Executor main = command -> {
        if (Looper.myLooper() == Looper.getMainLooper()) command.run();
        else handler.post(command);
    };

    public ChatController(Context context, AiChatService service) {
        helper = new FriendsDatabase(context.getApplicationContext());
        db = helper.getWritableDatabase();
        this.service = service;
        preferences = context.getSharedPreferences("ai_chat_consent", Context.MODE_PRIVATE);
        // Process death cannot prove whether a remote response arrived. Never silently resend.
        db.execSQL("UPDATE chat_requests SET state='failed',error='interrupted' WHERE state='pending'");
    }

    public boolean isConfigured() { return service.isConfigured(); }
    public boolean hasConsent() { return preferences.getBoolean("external_chat_v1", false); }
    public void setConsent(boolean allowed) {
        preferences.edit().putBoolean("external_chat_v1", allowed).apply();
        changed();
    }
    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void changed() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }

    public static final class Request {
        public final long messageId;
        public final String state, error;
        Request(long messageId, String state, String error) {
            this.messageId = messageId; this.state = state; this.error = error;
        }
    }

    public Request openRequest(int friend) {
        try (Cursor cursor = db.rawQuery("SELECT user_message,state,error FROM chat_requests"
                +" WHERE friend=? AND state IN ('pending','failed')", new String[]{""+friend})) {
            return cursor.moveToFirst() ? new Request(cursor.getLong(0), cursor.getString(1), cursor.getString(2)) : null;
        }
    }

    public boolean send(int friend, String text) {
        text = text.trim();
        if (!hasConsent() || !isConfigured() || text.isEmpty() || text.length() > MAX_MESSAGE_CHARS
                || openRequest(friend) != null) return false;
        long message;
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("friend", friend); values.put("content", text); values.put("is_user", 1);
            values.put("created", System.currentTimeMillis());
            message = db.insertOrThrow("messages", null, values);
            ContentValues request = new ContentValues();
            request.put("user_message", message); request.put("friend", friend); request.put("state", "failed");
            db.insertOrThrow("chat_requests", null, request);
            db.execSQL("UPDATE friends SET xp=xp+1 WHERE id=?", new Object[]{friend});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        retry(friend);
        return true;
    }

    public void retry(int friend) {
        Request request = openRequest(friend);
        if (!hasConsent() || !isConfigured() || request == null || !request.state.equals("failed")) return;
        db.execSQL("UPDATE chat_requests SET state='pending',attempt=attempt+1,error=NULL WHERE user_message=?",
                new Object[]{request.messageId});
        int attempt;
        try (Cursor cursor = db.rawQuery("SELECT attempt FROM chat_requests WHERE user_message=?",
                new String[]{""+request.messageId})) {
            cursor.moveToFirst(); attempt = cursor.getInt(0);
        }
        changed();
        ListenableFuture<String> future;
        try {
            String personality;
            try (Cursor cursor = db.rawQuery("SELECT personality FROM friends WHERE id=?", new String[]{""+friend})) {
                cursor.moveToFirst(); personality = cursor.getString(0);
            }
            future = service.generate(personality, context(friend, request.messageId));
        } catch (RuntimeException error) {
            finish(request.messageId, attempt, friend, null, "provider");
            return;
        }
        Runnable timeout = () -> {
            finish(request.messageId, attempt, friend, null, "timeout");
            future.cancel(true);
        };
        handler.postDelayed(timeout, 60000);
        Futures.addCallback(future, new FutureCallback<String>() {
            @Override public void onSuccess(String text) {
                handler.removeCallbacks(timeout);
                finish(request.messageId, attempt, friend, text,
                        text == null || text.trim().isEmpty() ? "empty" : null);
            }
            @Override public void onFailure(Throwable error) {
                handler.removeCallbacks(timeout);
                finish(request.messageId, attempt, friend, null, "provider");
            }
        }, main);
    }

    public void cancelFailed(int friend) {
        db.execSQL("UPDATE chat_requests SET state='cancelled' WHERE friend=? AND state='failed'",
                new Object[]{friend});
        changed();
    }

    List<AiChatService.Turn> context(int friend, long message) {
        List<AiChatService.Turn> turns = new ArrayList<>();
        int chars = 0;
        // Only real completed AI turns, plus this user message. Legacy examples stay local.
        try (Cursor cursor = db.rawQuery("SELECT m.content,m.is_user FROM messages m WHERE m.friend=? AND m.id<=?"
                +" AND (m.id=? OR EXISTS(SELECT 1 FROM chat_requests r WHERE r.friend=m.friend"
                +" AND r.state='complete' AND (r.user_message=m.id OR r.reply_message=m.id)))"
                +" ORDER BY m.id DESC LIMIT "+MAX_CONTEXT_TURNS, new String[]{""+friend, ""+message, ""+message})) {
            while (cursor.moveToNext()) {
                String text = cursor.getString(0);
                if (text.length() + chars > MAX_CONTEXT_CHARS) break;
                chars += text.length();
                turns.add(new AiChatService.Turn(cursor.getInt(1) == 1, text));
            }
        }
        Collections.reverse(turns);
        // A clipped history must begin with a user turn, not an orphaned model response.
        if (!turns.isEmpty() && !turns.get(0).user) turns.remove(0);
        return Collections.unmodifiableList(turns);
    }

    private void finish(long message, int attempt, int friend, String text, String error) {
        db.beginTransaction();
        try {
            ContentValues update = new ContentValues();
            update.put("state", error == null ? "complete" : "failed");
            update.put("error", error);
            int changed = db.update("chat_requests", update, "user_message=? AND attempt=? AND state='pending'",
                    new String[]{""+message, ""+attempt});
            if (changed == 1 && error == null) {
                ContentValues reply = new ContentValues();
                reply.put("friend", friend); reply.put("content", text.trim()); reply.put("is_user", 0);
                reply.put("created", System.currentTimeMillis());
                long replyId = db.insertOrThrow("messages", null, reply);
                db.execSQL("UPDATE chat_requests SET reply_message=? WHERE user_message=?", new Object[]{replyId, message});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        changed();
    }

    @Override public void close() {
        handler.removeCallbacksAndMessages(null);
        listeners.clear();
        helper.close();
    }
}
