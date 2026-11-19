package kr.example.haliltalk.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.time.LocalDate;

/** [U2 | 모험] 친구별 선택만 저장하고 진척은 실제 일일 기록에서 재계산한다. */
public final class AdventureStore {
    private final SQLiteDatabase db;

    public AdventureStore(SQLiteDatabase db) { this.db=db; }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE adventure_choices("
                +"friend INTEGER PRIMARY KEY REFERENCES friends(id) ON DELETE CASCADE,"
                +"days INTEGER NOT NULL CHECK(days IN (3,7,14)),"
                +"started_day TEXT NOT NULL CHECK(length(started_day)=10))");
    }

    public static final class Choice {
        public final int days;
        public final String startedDay;
        Choice(Cursor c) { days=c.getInt(0);startedDay=c.getString(1); }
    }

    public Choice choice(int friend) {
        if(friend<=0)throw new IllegalArgumentException("함께할 친구를 선택해 주세요.");
        try(Cursor c=db.rawQuery("SELECT days,started_day FROM adventure_choices WHERE friend=?",
                new String[]{""+friend})) {
            return c.moveToFirst()?new Choice(c):null;
        }
    }

    public void choose(int friend,int days,String day) {
        if(friend<=0)throw new IllegalArgumentException("함께할 친구를 선택해 주세요.");
        if(days!=3&&days!=7&&days!=14)throw new IllegalArgumentException("3·7·14일 모험 중 선택해 주세요.");
        validateDay(day);
        ContentValues values=new ContentValues();
        values.put("friend",friend);values.put("days",days);values.put("started_day",day);
        db.beginTransaction();
        try {
            if(choice(friend)==null) {
                if(db.insertOrThrow("adventure_choices",null,values)==-1)
                    throw new SQLiteException("모험길을 저장하지 못했어요.");
            } else if(db.update("adventure_choices",values,"friend=?",
                    new String[]{""+friend})!=1) {
                throw new SQLiteException("모험길을 저장하지 못했어요.");
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public int activeDays(int friend,String throughDay) {
        validateDay(throughDay);
        Choice choice=choice(friend);
        if(choice==null)return 0;
        try(Cursor c=db.rawQuery("SELECT COUNT(DISTINCT day) FROM journey_entries"
                +" WHERE friend=? AND day>=? AND day<=? AND outcome IN ('done','partial')",
                new String[]{""+friend,choice.startedDay,throughDay})) {
            c.moveToFirst();return c.getInt(0);
        }
    }

    private static void validateDay(String day) {
        if(day==null||!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("날짜를 확인해 주세요.");
        try { LocalDate.parse(day); }
        catch(java.time.DateTimeException failure) {
            throw new IllegalArgumentException("날짜를 확인해 주세요.",failure);
        }
    }
}
