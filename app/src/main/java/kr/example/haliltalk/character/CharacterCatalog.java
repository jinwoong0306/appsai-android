package kr.example.haliltalk.character;

/** Stable manifest IDs and the production assets selected for the app. */
public final class CharacterCatalog {
    private static final String[][] CHARACTERS = {
            {"mail", "메일", "01_메일"},
            {"video", "영상", "02_영상"},
            {"music", "음악", "03_음악"},
            {"photo", "사진", "04_사진"},
            {"chat", "채팅", "05_채팅"},
            {"map", "지도", "06_지도"},
            {"calendar", "일정", "07_일정"},
            {"reading", "독서", "08_독서"},
            {"fitness", "운동", "09_운동"}
    };
    private static final String[][] HATS = {
            {"none", "없음", null},
            {"straw_hat", "짚모자", "머리/01_짚모자.png"},
            {"baseball_cap", "야구모자", "머리/02_야구모자.png"},
            {"beret", "베레모", "머리/03_베레모.png"},
            {"beanie", "방울비니", "머리/04_방울비니.png"},
            {"crown", "왕관", "머리/05_왕관.png"},
            {"pirate_hat", "해적모자", "머리/06_해적모자.png"},
            {"cat_ears", "고양이귀머리띠", "머리/07_고양이귀머리띠.png"},
            {"bunny_ears", "토끼귀머리띠", "머리/08_토끼귀머리띠.png"},
            {"flower_crown", "꽃화관", "머리/09_꽃화관.png"},
            {"party_hat", "파티고깔", "머리/10_파티고깔.png"}
    };
    private static final String[][] FACES = {
            {"none", "없음", null},
            {"sunglasses", "선글라스", "얼굴/11_선글라스.png"},
            {"round_glasses", "동그란안경", "얼굴/12_동그란안경.png"},
            {"star_glasses", "별안경", "얼굴/13_별안경.png"},
            {"eyepatch", "해적안대", "얼굴/14_해적안대.png"},
            {"moustache", "콧수염", "얼굴/15_콧수염.png"},
            {"cheek_bandage", "볼반창고", "얼굴/16_볼반창고.png"}
    };
    private static final String[][] BODIES = {
            {"none", "없음", null},
            {"red_scarf", "빨간목도리", "목과몸/17_빨간목도리.png"},
            {"bow_tie", "나비넥타이", "목과몸/18_나비넥타이.png"},
            {"striped_tie", "줄무늬넥타이", "목과몸/19_줄무늬넥타이.png"},
            {"star_necklace", "별목걸이", "목과몸/20_별목걸이.png"},
            {"camera_necklace", "카메라목걸이", "목과몸/21_카메라목걸이.png"},
            {"suspenders", "멜빵", "목과몸/22_멜빵.png"}
    };
    private static final String[][] BACKS = {
            {"none", "없음", null},
            {"backpack", "여행배낭", "등/23_여행배낭.png"},
            {"angel_wings", "천사날개", "등/24_천사날개.png"},
            {"bat_wings", "박쥐날개", "등/25_박쥐날개.png"},
            {"rocket_pack", "로켓가방", "등/26_로켓가방.png"}
    };
    private static final String[][] PROPS = {
            {"none", "없음", null},
            {"sunflower", "해바라기", "손소품/28_해바라기.png"},
            {"coffee_cup", "커피컵", "손소품/29_커피컵.png"},
            {"gamepad", "게임패드", "손소품/30_게임패드.png"},
            {"umbrella", "우산", "손소품/27_우산.png"}
    };
    private static final String[][][] ACCESSORIES = {HATS, FACES, BODIES, BACKS, PROPS};

    public static final String[] CHARACTER_IDS = column(CHARACTERS, 0);
    public static final String[] CHARACTER_NAMES = column(CHARACTERS, 1);
    public static final String[] HAT_IDS = column(HATS, 0);
    public static final String[] HAT_NAMES = column(HATS, 1);
    public static final String[] FACE_IDS = column(FACES, 0);
    public static final String[] FACE_NAMES = column(FACES, 1);
    public static final String[] BODY_IDS = column(BODIES, 0);
    public static final String[] BODY_NAMES = column(BODIES, 1);
    public static final String[] BACK_IDS = column(BACKS, 0);
    public static final String[] BACK_NAMES = column(BACKS, 1);
    public static final String[] PROP_IDS = column(PROPS, 0);
    public static final String[] PROP_NAMES = column(PROPS, 1);

    private CharacterCatalog() {}

    public static boolean validCharacter(String id) {
        return find(CHARACTERS, id) != null;
    }

    public static boolean validHat(String id) {
        return find(HATS, id) != null;
    }

    public static boolean validFace(String id) {
        return find(FACES, id) != null;
    }

    public static boolean validBody(String id) {
        return find(BODIES, id) != null;
    }

    public static boolean validBack(String id) {
        return find(BACKS, id) != null;
    }

    public static boolean validProp(String id) {
        return find(PROPS, id) != null;
    }

    /** Unknown or null character IDs use the first character, mail. */
    public static String nameFor(String characterId) {
        return rowOrDefault(CHARACTERS, characterId)[1];
    }

    /** Unknown, null, and none accessory IDs use the empty-slot name. */
    public static String itemName(String id) {
        for (String[][] items : ACCESSORIES) {
            String[] item = find(items, id);
            if (item != null) return item[1];
        }
        return HATS[0][1];
    }

    /** All actual accessory IDs in slot order, preserving legacy option indexes. */
    public static String[] allAccessoryIds() {
        int count = 0;
        for (String[][] items : ACCESSORIES) count += items.length - 1;
        String[] ids = new String[count];
        int index = 0;
        for (String[][] items : ACCESSORIES) {
            for (int i = 1; i < items.length; i++) ids[index++] = items[i][0];
        }
        return ids;
    }

    /** Empty or unknown accessory IDs do not identify an equipped slot. */
    public static String slotFor(String id) {
        if ("none".equals(id)) return null;
        if (validHat(id)) return "hat";
        if (validFace(id)) return "face";
        if (validBody(id)) return "body";
        if (validBack(id)) return "back";
        if (validProp(id)) return "prop";
        return null;
    }

    static String[] layerPaths(String characterId, String hatId, String faceId, String propId) {
        return layerPaths(characterId, hatId, faceId, "none", "none", propId);
    }

    // Source origin is (0, 0) for every layer.
    static String[] layerPaths(String characterId, String hatId, String faceId,
            String bodyId, String backId, String propId) {
        String base = "characters/latest/" + rowOrDefault(CHARACTERS, characterId)[2] + ".png";
        return new String[] {
                accessoryPath(rowOrDefault(BACKS, backId)),
                base,
                accessoryPath(rowOrDefault(BODIES, bodyId)),
                null,
                accessoryPath(rowOrDefault(FACES, faceId)),
                null,
                accessoryPath(rowOrDefault(HATS, hatId)),
                accessoryPath(rowOrDefault(PROPS, propId))
        };
    }

    private static String accessoryPath(String[] item) {
        return item[2] == null ? null : "characters/accessories/" + item[2];
    }

    private static String[] find(String[][] items, String id) {
        for (String[] item : items) {
            if (item[0].equals(id)) return item;
        }
        return null;
    }

    private static String[] rowOrDefault(String[][] items, String id) {
        String[] item = find(items, id);
        return item == null ? items[0] : item;
    }

    private static String[] column(String[][] items, int index) {
        String[] values = new String[items.length];
        for (int i = 0; i < items.length; i++) values[i] = items[i][index];
        return values;
    }
}
