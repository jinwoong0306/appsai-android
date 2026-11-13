package kr.example.haliltalk.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** [U2 | 읽음 저장] 현재 계정 DB의 받은 대화만 기록하며 보상·네트워크와 분리한다. */
public final class MessageReadStore {
    private final SQLiteDatabase db;

    public MessageReadStore(SQLiteDatabase db) { this.db=db; }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE message_reads("
                +"message INTEGER PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX messages_by_friend_sender_id ON messages(friend,is_user,id)");
    }

    public static void markLegacyRead(SQLiteDatabase db) {
        db.execSQL("INSERT INTO message_reads(message) SELECT id FROM messages"
                +" WHERE is_user=0 AND NOT EXISTS"
                +" (SELECT 1 FROM message_reads WHERE message=messages.id)");
    }

    public int unreadCount(int friend) {
        try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM messages WHERE friend=? AND is_user=0"
                +" AND NOT EXISTS (SELECT 1 FROM message_reads WHERE message=messages.id)",
                new String[]{""+friend})) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }

    public void markReadThrough(int friend,long lastDisplayedMessageId) {
        db.beginTransaction();
        try {
            // [U2 | 읽음 저장] 화면에 표시한 경계 뒤의 새 대화는 읽음으로 바꾸지 않는다.
            db.execSQL("INSERT INTO message_reads(message) SELECT id FROM messages"
                    +" WHERE friend=? AND is_user=0 AND id<=?"
                    +" AND NOT EXISTS (SELECT 1 FROM message_reads WHERE message=messages.id)",
                    new Object[]{friend,lastDisplayedMessageId});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
