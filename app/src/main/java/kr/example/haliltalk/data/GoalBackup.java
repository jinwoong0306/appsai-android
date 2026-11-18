package kr.example.haliltalk.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.JsonReader;
import android.util.JsonToken;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Goals only: no account IDs, messages, credentials, settings, XP, or wallet data. */
public final class GoalBackup {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_GOALS = 1000;
    public static final int MAX_TITLE = 120;
    public static final long MAX_DATE = 253402300799999L;

    private GoalBackup() {}

    public static final class InvalidBackupException extends IOException {
        public InvalidBackupException(String message) { super(message); }
    }

    public static final class Document {
        private final List<Goal> goals;
        private Document(List<Goal> goals) {
            this.goals = Collections.unmodifiableList(new ArrayList<>(goals));
        }
        public int size() { return goals.size(); }
    }

    public static final class Preview {
        public final int total;
        public final int additions;
        public final int unknownFriends;
        public final int alreadyPresent;
        private final Document document;
        private final List<Resolved> resolved;
        private Preview(Document document, List<Resolved> resolved, int unknown, int present) {
            this.document = document;
            this.resolved = resolved;
            total = document.size();
            additions = resolved.size();
            unknownFriends = unknown;
            alreadyPresent = present;
        }
    }

    private static final class Goal {
        String pkg;
        String title;
        Long due;
        boolean completed;
        long created;

        String key() {
            // JSON 배열은 구분자·따옴표를 이스케이프하므로 서로 다른 텍스트가 충돌하지 않는다.
            return new JSONArray().put(pkg).put(title)
                    .put(due == null ? JSONObject.NULL : due).put(completed).put(created).toString();
        }
    }

    private static final class Resolved {
        final Goal goal;
        final long friend;
        Resolved(Goal goal, long friend) { this.goal = goal; this.friend = friend; }
    }

    /** Caller owns the stream. The byte bound is checked before decoding or parsing. */
    public static Document read(InputStream input) throws IOException {
        if (input == null) throw invalid("파일을 열 수 없습니다.");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw invalid("파일은 1 MiB 이하여야 합니다.");
            bytes.write(buffer, 0, count);
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException badEncoding) {
            throw invalid("UTF-8 JSON 파일을 선택해 주세요.");
        }
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) throw invalid("올바른 목표 파일이 아닙니다.");
            reader.beginObject();
            Set<String> fields = new HashSet<>();
            List<Goal> goals = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!fields.add(name)) throw invalid("중복 필드가 있습니다.");
                if ("version".equals(name)) {
                    if (integer(reader) != 1) throw invalid("지원하지 않는 파일 버전입니다.");
                } else if ("goals".equals(name)) {
                    if (reader.peek() != JsonToken.BEGIN_ARRAY) throw invalid("목표 목록 형식이 다릅니다.");
                    reader.beginArray();
                    while (reader.hasNext()) {
                        if (goals.size() >= MAX_GOALS) throw invalid("목표는 최대 1,000개까지 가져올 수 있습니다.");
                        Goal goal = readGoal(reader);
                        if (!unique.add(goal.key())) throw invalid("파일에 동일한 목표가 중복돼 있습니다.");
                        goals.add(goal);
                    }
                    reader.endArray();
                } else {
                    throw invalid("허용되지 않은 데이터가 있습니다. 목표 전용 파일을 선택해 주세요.");
                }
            }
            reader.endObject();
            if (fields.size() != 2 || !fields.contains("version") || !fields.contains("goals")
                    || reader.peek() != JsonToken.END_DOCUMENT) throw invalid("파일 구조가 올바르지 않습니다.");
            return new Document(goals);
        } catch (InvalidBackupException invalid) {
            throw invalid;
        } catch (IOException | IllegalStateException | NumberFormatException invalid) {
            throw invalid("JSON 형식 오류예요.\n파일 내용을 확인해 주세요.");
        }
    }

    private static Goal readGoal(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) throw invalid("목표 항목 형식이 다릅니다.");
        reader.beginObject();
        Goal goal = new Goal();
        Set<String> fields = new HashSet<>();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!fields.add(name)) throw invalid("중복된 목표 필드가 있습니다.");
            switch (name) {
                case "package":
                    goal.pkg = string(reader);
                    if (goal.pkg.length() > 255
                            || !goal.pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")) {
                        throw invalid("앱 패키지 식별자가 올바르지 않습니다.");
                    }
                    break;
                case "title":
                    goal.title = string(reader);
                    if (goal.title.trim().isEmpty() || goal.title.length() > MAX_TITLE) {
                        throw invalid("목표 제목은 1~120자여야 합니다.");
                    }
                    for (int i = 0; i < goal.title.length(); i++) {
                        char c = goal.title.charAt(i);
                        if (Character.isISOControl(c)) throw invalid("제목에 제어 문자를 사용할 수 없습니다.");
                        if (Character.isHighSurrogate(c)) {
                            if (++i >= goal.title.length()
                                    || !Character.isLowSurrogate(goal.title.charAt(i))) {
                                throw invalid("제목의 문자 인코딩이 올바르지 않습니다.");
                            }
                        } else if (Character.isLowSurrogate(c)) {
                            throw invalid("제목의 문자 인코딩이 올바르지 않습니다.");
                        }
                    }
                    break;
                case "due_at":
                    if (reader.peek() == JsonToken.NULL) reader.nextNull();
                    else goal.due = date(reader);
                    break;
                case "completed":
                    if (reader.peek() != JsonToken.BOOLEAN) throw invalid("완료 값은 true/false여야 합니다.");
                    goal.completed = reader.nextBoolean();
                    break;
                case "created":
                    goal.created = date(reader);
                    break;
                default:
                    throw invalid("목표에 허용되지 않은 데이터가 있습니다.");
            }
        }
        reader.endObject();
        if (fields.size() != 5 || !fields.contains("package") || !fields.contains("title")
                || !fields.contains("due_at") || !fields.contains("completed")
                || !fields.contains("created")) throw invalid("필수 목표 값이 없습니다.");
        return goal;
    }

    private static String string(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw invalid("문자열 값이 필요합니다.");
        return reader.nextString();
    }

    private static long integer(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) throw invalid("정수 값이 필요합니다.");
        String value = reader.nextString();
        if (!value.matches("0|[1-9][0-9]{0,17}")) throw invalid("음수·소수·지수 표기는 허용하지 않습니다.");
        try { return Long.parseLong(value); }
        catch (NumberFormatException overflow) { throw invalid("정수 범위를 초과했습니다."); }
    }

    private static long date(JsonReader reader) throws IOException {
        long value = integer(reader);
        if (value > MAX_DATE) throw invalid("날짜는 1970년~9999년 범위여야 합니다.");
        return value;
    }

    public static byte[] exportData(SQLiteDatabase database) throws IOException {
        JSONArray goals = new JSONArray();
        try (Cursor rows = database.rawQuery("SELECT f.package,g.title,g.due_at,g.completed,g.created"
                + " FROM goals g LEFT JOIN friends f ON f.id=g.friend ORDER BY g.id", null)) {
            while (rows.moveToNext()) {
                if (goals.length() >= MAX_GOALS) throw invalid("내보내기는 목표 1,000개까지 지원합니다.");
                if (rows.getInt(3) != 0 && rows.getInt(3) != 1) throw invalid("목표 완료 값이 올바르지 않습니다.");
                JSONObject goal = new JSONObject();
                goal.put("package", rows.isNull(0) ? JSONObject.NULL : rows.getString(0));
                goal.put("title", rows.isNull(1) ? JSONObject.NULL : rows.getString(1));
                goal.put("due_at", rows.isNull(2) ? JSONObject.NULL : rows.getLong(2));
                goal.put("completed", rows.getInt(3) == 1);
                goal.put("created", rows.getLong(4));
                goals.put(goal);
            }
            byte[] bytes = new JSONObject().put("version", 1).put("goals", goals)
                    .toString().getBytes(StandardCharsets.UTF_8);
            // 내보낸 파일도 동일한 경계 검증을 통과해야 다시 가져올 수 있다.
            read(new ByteArrayInputStream(bytes));
            return bytes;
        } catch (JSONException impossible) {
            throw invalid("목표 파일을 만들 수 없습니다.");
        }
    }

    /** Unknown packages are skipped; ambiguous package-to-friend mappings reject the whole file. */
    public static Preview preview(SQLiteDatabase database, Document document) throws IOException {
        Map<String, Long> friends = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        try (Cursor rows = database.rawQuery("SELECT id,package FROM friends", null)) {
            while (rows.moveToNext()) {
                String pkg = rows.getString(1);
                if (friends.put(pkg, rows.getLong(0)) != null) ambiguous.add(pkg);
            }
        }
        List<Resolved> additions = new ArrayList<>();
        int unknown = 0, present = 0;
        for (Goal goal : document.goals) {
            if (ambiguous.contains(goal.pkg)) throw invalid("동일한 앱에 여러 친구가 연결돼 있어 가져올 수 없습니다.");
            Long friend = friends.get(goal.pkg);
            if (friend == null) { unknown++; continue; }
            String dueClause = goal.due == null ? "due_at IS NULL" : "due_at=?";
            List<String> args = new ArrayList<>();
            args.add(Long.toString(friend));
            args.add(goal.title);
            args.add(goal.completed ? "1" : "0");
            args.add(Long.toString(goal.created));
            if (goal.due != null) args.add(Long.toString(goal.due));
            try (Cursor exists = database.rawQuery("SELECT 1 FROM goals WHERE friend=? AND title=?"
                    + " AND completed=? AND created=? AND " + dueClause + " LIMIT 1",
                    args.toArray(new String[0]))) {
                if (exists.moveToFirst()) present++;
                else additions.add(new Resolved(goal, friend));
            }
        }
        return new Preview(document, additions, unknown, present);
    }

    /** Confirm this preview first. No overwrites or reward side effects; any failure rolls back all rows. */
    public static int importConfirmed(SQLiteDatabase database, Preview confirmed) throws IOException {
        return importConfirmed(database, confirmed, () -> true);
    }

    /** The activity invalidates this guard on finish or account transition, not on rotation. */
    public static int importConfirmed(SQLiteDatabase database, Preview confirmed,
            BooleanSupplier stillCurrent) throws IOException {
        database.beginTransaction();
        try {
            if (!stillCurrent.getAsBoolean()) throw changed();
            Preview current = preview(database, confirmed.document);
            if (current.unknownFriends != confirmed.unknownFriends
                    || current.alreadyPresent != confirmed.alreadyPresent
                    || current.additions != confirmed.additions) throw changed();
            for (int i = 0; i < current.resolved.size(); i++) {
                Resolved row = current.resolved.get(i), expected = confirmed.resolved.get(i);
                if (row.friend != expected.friend || !row.goal.key().equals(expected.goal.key())) throw changed();
            }
            for (Resolved row : current.resolved) {
                if (!stillCurrent.getAsBoolean()) throw changed();
                ContentValues values = new ContentValues();
                values.put("friend", row.friend);
                values.put("title", row.goal.title);
                if (row.goal.due == null) values.putNull("due_at");
                else values.put("due_at", row.goal.due);
                values.put("completed", row.goal.completed ? 1 : 0);
                values.put("created", row.goal.created);
                database.insertOrThrow("goals", null, values);
            }
            if (!stillCurrent.getAsBoolean()) throw changed();
            database.setTransactionSuccessful();
            return current.additions;
        } finally { database.endTransaction(); }
    }

    private static InvalidBackupException changed() {
        return invalid("확인 후 친구 또는 목표 목록이 바뀌었습니다. 파일을 다시 선택해 확인해 주세요.");
    }

    private static InvalidBackupException invalid(String message) {
        return new InvalidBackupException(message);
    }
}
