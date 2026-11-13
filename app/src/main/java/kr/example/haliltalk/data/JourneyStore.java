package kr.example.haliltalk.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** [U2 | 이어가기] 계정 DB의 로컬 기록이다. AI·백업·XP·코인과 연결하지 않는다. */
public final class JourneyStore {
    private final SQLiteDatabase db;

    public JourneyStore(SQLiteDatabase db) { this.db=db; }

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE journey_entries("
                +"friend INTEGER NOT NULL REFERENCES friends(id) ON DELETE CASCADE,"
                +"day TEXT NOT NULL CHECK(length(day)=10),"
                +"action TEXT NOT NULL CHECK(length(trim(action)) BETWEEN 1 AND 120),"
                +"cue TEXT NOT NULL DEFAULT '' CHECK(length(cue)<=80),"
                +"goal_title TEXT NOT NULL DEFAULT '' CHECK(length(goal_title)<=120),"
                +"outcome TEXT NOT NULL DEFAULT 'planned' CHECK(outcome IN ('planned','done','partial','rest')),"
                +"note TEXT NOT NULL DEFAULT '' CHECK(length(note)<=160),"
                +"updated_at INTEGER NOT NULL, PRIMARY KEY(friend,day))");
        db.execSQL("CREATE INDEX journey_by_day ON journey_entries(day)");
    }

    public static final class Entry {
        public final int friend;
        public final String day,action,cue,goalTitle,outcome,note;
        public final long updatedAt;

        Entry(Cursor c) {
            friend=c.getInt(0); day=c.getString(1); action=c.getString(2);
            cue=c.getString(3); goalTitle=c.getString(4); outcome=c.getString(5);
            note=c.getString(6); updatedAt=c.getLong(7);
        }
    }

    public static final class Summary {
        public final int plannedToday,reviewedToday,activeDays7,done7,partial7,rest7,totalActiveDays;

        Summary(Cursor c) {
            plannedToday=c.getInt(0); reviewedToday=c.getInt(1); activeDays7=c.getInt(2);
            done7=c.getInt(3); partial7=c.getInt(4); rest7=c.getInt(5); totalActiveDays=c.getInt(6);
        }
    }

    public Entry entry(int friend,String day) {
        validateFriend(friend);
        validateDay(day);
        try(Cursor c=db.rawQuery("SELECT friend,day,action,cue,goal_title,outcome,note,updated_at"
                +" FROM journey_entries WHERE friend=? AND day=?",new String[]{""+friend,day})) {
            return c.moveToFirst()?new Entry(c):null;
        }
    }

    public List<Entry> recent(int friend,String throughDay,int limit) {
        validateFriend(friend);
        validateDay(throughDay);
        if(limit<1||limit>10000) throw new IllegalArgumentException("기록 개수는 1~10000개로 확인해 주세요.");
        List<Entry> entries=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT friend,day,action,cue,goal_title,outcome,note,updated_at"
                +" FROM journey_entries WHERE friend=? AND day<=? ORDER BY day DESC LIMIT ?",
                new String[]{""+friend,throughDay,""+limit})) {
            while(c.moveToNext()) entries.add(new Entry(c));
        }
        return entries;
    }

    public void savePlan(int friend,String day,String action,String cue,String goalTitle,long now) {
        validateFriend(friend);
        validateDay(day);
        if(action==null) throw new IllegalArgumentException("오늘의 행동을 입력해 주세요.");
        action=GoalStore.validateTitle(action);
        cue=validateOptional(cue,80);
        goalTitle=validateOptional(goalTitle,120);
        ContentValues values=new ContentValues();
        values.put("action",action); values.put("cue",cue); values.put("goal_title",goalTitle);
        values.put("updated_at",now);
        db.beginTransaction();
        try {
            requireFriend(friend);
            // [U2 | 이어가기] 같은 날 수정해도 이미 저장한 돌아보기는 지우지 않는다.
            if(entry(friend,day)==null) {
                values.put("friend",friend); values.put("day",day);
                if(db.insertOrThrow("journey_entries",null,values)==-1) throw missingEntry();
            } else if(db.update("journey_entries",values,"friend=? AND day=?",
                    new String[]{""+friend,day})!=1) throw missingEntry();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void review(int friend,String day,String outcome,String note,long now) {
        validateFriend(friend);
        validateDay(day);
        if(!"done".equals(outcome)&&!"partial".equals(outcome)&&!"rest".equals(outcome))
            throw new IllegalArgumentException("돌아보기 결과를 선택해 주세요.");
        ContentValues values=new ContentValues();
        values.put("outcome",outcome); values.put("note",validateOptional(note,160));
        values.put("updated_at",now);
        db.beginTransaction();
        try {
            if(db.update("journey_entries",values,"friend=? AND day=?",
                    new String[]{""+friend,day})!=1) throw missingEntry();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void delete(int friend,String day) {
        validateFriend(friend);
        validateDay(day);
        db.beginTransaction();
        try {
            if(db.delete("journey_entries","friend=? AND day=?",new String[]{""+friend,day})!=1)
                throw missingEntry();
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public Summary summary(int friend,String throughDay) {
        if(friend!=0) validateFriend(friend);
        String fromDay=validateDay(throughDay).minusDays(6).toString();
        // [U2 | 이어가기] 계획 수는 완료 포함, 활동일은 한 날의 done·partial만 한 번 센다.
        String query="SELECT COUNT(CASE WHEN day=? THEN 1 END),"
                +"COUNT(CASE WHEN day=? AND outcome!='planned' THEN 1 END),"
                +"COUNT(DISTINCT CASE WHEN day>=? AND outcome IN ('done','partial') THEN day END),"
                +"COUNT(CASE WHEN day>=? AND outcome='done' THEN 1 END),"
                +"COUNT(CASE WHEN day>=? AND outcome='partial' THEN 1 END),"
                +"COUNT(CASE WHEN day>=? AND outcome='rest' THEN 1 END),"
                +"COUNT(DISTINCT CASE WHEN outcome IN ('done','partial') THEN day END)"
                +" FROM journey_entries WHERE day<=?"+(friend==0?"":" AND friend=?");
        String[] args=friend==0
                ?new String[]{throughDay,throughDay,fromDay,fromDay,fromDay,fromDay,throughDay}
                :new String[]{throughDay,throughDay,fromDay,fromDay,fromDay,fromDay,throughDay,""+friend};
        try(Cursor c=db.rawQuery(query,args)) {
            c.moveToFirst();
            return new Summary(c);
        }
    }

    private static LocalDate validateDay(String day) {
        if(day==null||!day.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("날짜를 YYYY-MM-DD 형식으로 확인해 주세요.");
        try { return LocalDate.parse(day); }
        catch(DateTimeException invalid) { throw new IllegalArgumentException("날짜를 확인해 주세요.",invalid); }
    }

    private static void validateFriend(int friend) {
        if(friend<=0) throw new IllegalArgumentException("친구를 확인해 주세요.");
    }

    private void requireFriend(int friend) {
        try(Cursor c=db.rawQuery("SELECT 1 FROM friends WHERE id=?",new String[]{""+friend})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("친구가 없어졌어요. 목록을 다시 확인해 주세요.");
        }
    }

    private static String validateOptional(String value,int max) {
        if(value==null) return "";
        if(value.length()>max) throw new IllegalArgumentException("입력은 "+max+"자 이내로 적어 주세요.");
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(Character.isISOControl(ch)) throw new IllegalArgumentException("줄바꿈 없이 입력해 주세요.");
            if(Character.isHighSurrogate(ch)) {
                if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("입력한 문자를 확인해 주세요.");
            } else if(Character.isLowSurrogate(ch)) throw new IllegalArgumentException("입력한 문자를 확인해 주세요.");
        }
        return value.trim();
    }

    private static IllegalArgumentException missingEntry() {
        return new IllegalArgumentException("계획이 없어졌어요. 목록을 다시 확인해 주세요.");
    }
}
