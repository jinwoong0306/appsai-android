package kr.example.haliltalk.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import kr.example.haliltalk.shop.DemoShopStore;

/** Additive migrations preserve existing friends, messages, experience, and appearance. */
public final class FriendsDatabase extends SQLiteOpenHelper {
    public FriendsDatabase(Context context) { super(context, "friends.db", null, 8); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE friends(id INTEGER PRIMARY KEY, package TEXT UNIQUE, name TEXT, personality TEXT, xp INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY, friend INTEGER, content TEXT, is_user INTEGER, created INTEGER)");
        createAppearance(db);
        createChatRequests(db);
        addAppearanceSlots(db);
        DemoShopStore.createTables(db);
        createGoals(db);
        JourneyStore.createTables(db);
        MessageReadStore.createTables(db);
    }
    private void createAppearance(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE appearance(friend INTEGER PRIMARY KEY REFERENCES friends(id) ON DELETE CASCADE, character TEXT NOT NULL, hat TEXT NOT NULL, face TEXT NOT NULL, prop TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) createAppearance(db);
        if (oldVersion < 3 && newVersion >= 3) createChatRequests(db);
        if (oldVersion < 4 && newVersion >= 4) addAppearanceSlots(db);
        if (oldVersion < 5 && newVersion >= 5) DemoShopStore.createTables(db);
        if (oldVersion < 6 && newVersion >= 6) createGoals(db);
        // [U2] Add local daily records without changing existing goals or rewards.
        if (oldVersion < 7 && newVersion >= 7) JourneyStore.createTables(db);
        // [U2] Existing incoming messages start read; later messages start unread.
        if (oldVersion < 8 && newVersion >= 8) {
            MessageReadStore.createTables(db);
            MessageReadStore.markLegacyRead(db);
        }
    }
    private void createGoals(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE friends ADD COLUMN purpose TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE friends ADD COLUMN share_goals INTEGER NOT NULL DEFAULT 0 CHECK(share_goals IN (0,1))");
        db.execSQL("CREATE TABLE goals(id INTEGER PRIMARY KEY, friend INTEGER NOT NULL REFERENCES friends(id) ON DELETE CASCADE,"
                +"title TEXT NOT NULL CHECK(length(trim(title)) BETWEEN 1 AND 120), due_at INTEGER,"
                +"completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)), created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX goals_by_friend ON goals(friend,completed,due_at)");
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
