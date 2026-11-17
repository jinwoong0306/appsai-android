package kr.example.haliltalk.auth;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import kr.example.haliltalk.character.CharacterCatalog;

/** The cloud allow-list deliberately excludes messages, XP, money, ownership, and consent. */
final class FriendGoalSnapshot {
    static final int MAX_BYTES = 400000;
    private static final long MAX_DATE = 253402300799999L;
    private static final int MAX_FRIENDS = 100, MAX_GOALS = 500;
    private static final String[] SLOTS = {"character", "hat", "face", "body", "back", "prop"};

    private FriendGoalSnapshot() {}

    static String capture(SQLiteDatabase db) throws JSONException {
        db.beginTransaction();
        try {
            if (DatabaseUtils.queryNumEntries(db, "friends") > MAX_FRIENDS
                    || DatabaseUtils.queryNumEntries(db, "goals") > MAX_GOALS) {
                throw new IllegalArgumentException("snapshot_limit");
            }
            JSONArray friends = new JSONArray();
            try (Cursor rows = db.rawQuery("SELECT id,package,name,personality,purpose FROM friends ORDER BY id", null)) {
                while (rows.moveToNext()) {
                    JSONObject friend = new JSONObject();
                    friend.put("id", rows.getLong(0));
                    for (int i = 1; i < 5; i++) {
                        friend.put(new String[]{"id", "package", "name", "personality", "purpose"}[i],
                                rows.isNull(i) ? "" : rows.getString(i));
                    }
                    JSONObject appearance = null;
                    try (Cursor looks = db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",
                            new String[]{Long.toString(rows.getLong(0))})) {
                        if (looks.moveToFirst()) {
                            appearance = new JSONObject();
                            for (int i = 0; i < SLOTS.length; i++) appearance.put(SLOTS[i], looks.getString(i));
                        }
                    }
                    friend.put("appearance", appearance == null ? JSONObject.NULL : appearance);
                    friends.put(friend);
                }
            }
            JSONArray goals = new JSONArray();
            try (Cursor rows = db.rawQuery("SELECT id,friend,title,due_at,completed,created FROM goals ORDER BY id", null)) {
                while (rows.moveToNext()) {
                    goals.put(new JSONObject().put("id", rows.getLong(0)).put("friend", rows.getLong(1))
                            .put("title", rows.getString(2))
                            .put("due_at", rows.isNull(3) ? JSONObject.NULL : rows.getLong(3))
                            .put("completed", rows.getInt(4)).put("created", rows.getLong(5)));
                }
            }
            String snapshot = new JSONObject().put("friends", friends).put("goals", goals).toString();
            validate(snapshot);
            db.setTransactionSuccessful();
            return snapshot;
        } finally {
            db.endTransaction();
        }
    }

    static void restore(SQLiteDatabase db, String snapshot) throws JSONException {
        JSONObject data = validate(snapshot);
        // 검증이 끝난 뒤 하나의 트랜잭션으로 삽입한다. 기존 행은 삭제·덮어쓰지 않는다.
        db.beginTransaction();
        try {
            for (String table : new String[]{"friends", "goals", "messages", "appearance", "chat_requests"}) {
                if (DatabaseUtils.queryNumEntries(db, table) != 0) throw new IllegalStateException("not_empty");
            }
            JSONArray friends = data.getJSONArray("friends");
            for (int i = 0; i < friends.length(); i++) {
                JSONObject friend = friends.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put("id", friend.getLong("id"));
                for (String field : new String[]{"package", "name", "personality", "purpose"}) {
                    values.put(field, friend.getString(field));
                }
                // 경험치와 목표 공유 동의는 복원하지 않고 DB의 기본값 0을 사용한다.
                db.insertOrThrow("friends", null, values);
                if (!friend.isNull("appearance")) {
                    ContentValues appearance = new ContentValues();
                    appearance.put("friend", friend.getLong("id"));
                    JSONObject looks = friend.getJSONObject("appearance");
                    for (String slot : SLOTS) appearance.put(slot, looks.getString(slot));
                    db.insertOrThrow("appearance", null, appearance);
                }
            }
            JSONArray goals = data.getJSONArray("goals");
            for (int i = 0; i < goals.length(); i++) {
                JSONObject goal = goals.getJSONObject(i);
                ContentValues values = new ContentValues();
                for (String key : new String[]{"id", "friend", "completed", "created"}) {
                    values.put(key, goal.getLong(key));
                }
                values.put("title", goal.getString("title"));
                if (goal.isNull("due_at")) values.putNull("due_at");
                else values.put("due_at", goal.getLong("due_at"));
                db.insertOrThrow("goals", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    static JSONObject validate(String snapshot) throws JSONException {
        if (snapshot == null || snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("snapshot_limit");
        }
        JSONObject data = new JSONObject(snapshot);
        keys(data, "friends", "goals");
        JSONArray friends = data.getJSONArray("friends"), goals = data.getJSONArray("goals");
        if (friends.length() > MAX_FRIENDS || goals.length() > MAX_GOALS) {
            throw new IllegalArgumentException("snapshot_limit");
        }
        Set<Long> ids = new HashSet<>(), goalIds = new HashSet<>();
        Set<String> packages = new HashSet<>();
        for (int i = 0; i < friends.length(); i++) {
            JSONObject friend = friends.getJSONObject(i);
            keys(friend, "id", "package", "name", "personality", "purpose", "appearance");
            if (!ids.add(number(friend, "id", 1, Integer.MAX_VALUE))
                    || !packages.add(text(friend, "package", 255, false))) {
                throw new IllegalArgumentException("duplicate_friend");
            }
            text(friend, "name", 255, false);
            text(friend, "personality", 1000, true);
            text(friend, "purpose", 1000, true);
            if (!friend.isNull("appearance")) {
                JSONObject looks = friend.getJSONObject("appearance");
                keys(looks, SLOTS);
                for (String slot : SLOTS) text(looks, slot, 64, false);
                if (!CharacterCatalog.validCharacter(looks.getString("character"))
                        || !CharacterCatalog.validHat(looks.getString("hat"))
                        || !CharacterCatalog.validFace(looks.getString("face"))
                        || !CharacterCatalog.validBody(looks.getString("body"))
                        || !CharacterCatalog.validBack(looks.getString("back"))
                        || !CharacterCatalog.validProp(looks.getString("prop"))) {
                    throw new IllegalArgumentException("invalid_appearance");
                }
            }
        }
        for (int i = 0; i < goals.length(); i++) {
            JSONObject goal = goals.getJSONObject(i);
            keys(goal, "id", "friend", "title", "due_at", "completed", "created");
            if (!goalIds.add(number(goal, "id", 1, Integer.MAX_VALUE))
                    || !ids.contains(number(goal, "friend", 1, Integer.MAX_VALUE))) {
                throw new IllegalArgumentException("invalid_goal_reference");
            }
            kr.example.haliltalk.data.GoalStore.validateTitle(text(goal, "title", 120, false));
            number(goal, "completed", 0, 1);
            number(goal, "created", 0, MAX_DATE);
            if (!goal.isNull("due_at")) number(goal, "due_at", 0, MAX_DATE);
        }
        return data;
    }

    private static void keys(JSONObject value, String... fields) throws JSONException {
        if (value.length() != fields.length) throw new IllegalArgumentException("unknown_fields");
        for (String field : fields) if (!value.has(field)) throw new JSONException("Missing " + field);
    }

    private static String text(JSONObject value, String key, int max, boolean empty) throws JSONException {
        Object raw = value.get(key);
        if (!(raw instanceof String)) throw new IllegalArgumentException("invalid_text");
        String text = (String) raw;
        if (text.length() > max || (!empty && text.trim().isEmpty())) throw new IllegalArgumentException("invalid_text");
        return text;
    }

    private static long number(JSONObject value, String key, long min, long max) throws JSONException {
        Object raw = value.get(key);
        if (!(raw instanceof Integer) && !(raw instanceof Long)) throw new IllegalArgumentException("invalid_integer");
        long number = ((Number) raw).longValue();
        if (number < min || number > max) throw new IllegalArgumentException("invalid_integer");
        return number;
    }
}
