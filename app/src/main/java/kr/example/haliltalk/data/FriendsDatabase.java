package kr.example.haliltalk.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Additive migrations preserve existing friends, messages, experience, and appearance. */
public final class FriendsDatabase extends SQLiteOpenHelper {
    public FriendsDatabase(Context context) { super(context, "friends.db", null, 4); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY, package TEXT UNIQUE, name TEXT, personality TEXT, xp INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY, friend INTEGER, content TEXT, is_user INTEGER, created INTEGER)");
        createAppearance(db);
        createChatRequests(db);
        addAppearanceSlots(db);
    }
    private void createAppearance(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY REFERENCES friends(id) ON DELETE CASCADE, character TEXT NOT NULL, hat TEXT NOT NULL, face TEXT NOT NULL, prop TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) createAppearance(db);
        if (oldVersion < 3 && newVersion >= 3) createChatRequests(db);
        if (oldVersion < 4 && newVersion >= 4) addAppearanceSlots(db);
    }
    private void addAppearanceSlots(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE appearance ADD COLUMN body TEXT NOT NULL DEFAULT 'none'");
        db.execSQL("ALTER TABLE appearance ADD COLUMN back TEXT NOT NULL DEFAULT 'none'");
    }
    private void createChatRequests(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE chat_requests(user_message INTEGER PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,"
                +" friend INTEGER NOT NULL REFERENCES friends(id) ON DELETE CASCADE,"
                +" state TEXT NOT NULL CHECK(state IN ('pending','failed','complete','cancelled')),"
                +" attempt INTEGER NOT NULL DEFAULT 0,"
                +" reply_message INTEGER UNIQUE REFERENCES messages(id) ON DELETE CASCADE, error TEXT)");
        db.execSQL("CREATE UNIQUE INDEX one_open_chat_request ON chat_requests(friend)"
                +" WHERE state IN ('pending','failed')");
    }
}
