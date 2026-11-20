package kr.example.haliltalk.shop;

import kr.example.haliltalk.character.CharacterCatalog;

/** Presentation metadata only; stable item IDs, ownership and prices stay in their stores. */
public final class ShopCatalog {
    private static final String[][] THEMES = {
            {"beret","beanie","round_glasses","red_scarf","coffee_cup","gamepad"},
            {"straw_hat","baseball_cap","sunglasses","cheek_bandage","camera_necklace","sunflower","umbrella"},
            {"crown","cat_ears","bunny_ears","flower_crown","party_hat","star_glasses","moustache",
                    "bow_tie","striped_tie","star_necklace","angel_wings"},
            {"pirate_hat","eyepatch","suspenders","backpack","bat_wings","rocket_pack"}
    };
    private static final String[] SLOTS = {"","hat","face","body","back","prop"};

    private ShopCatalog() {}

    public static int themeFor(String id) {
        for (int theme = 0; theme < THEMES.length; theme++)
            for (String item : THEMES[theme]) if (item.equals(id)) return theme + 1;
        return 0;
    }

    /** Filters intersect. "Affordable" means an unowned item that can actually be purchased. */
    public static boolean matches(String id, int theme, int category, int ownership,
            boolean owned, int price, int balance) {
        String slot = CharacterCatalog.slotFor(id);
        if (slot == null || theme < 0 || theme > THEMES.length || category < 0
                || category >= SLOTS.length || ownership < 0 || ownership > 3) return false;
        return (theme == 0 || theme == themeFor(id))
                && (category == 0 || SLOTS[category].equals(slot))
                && (ownership == 0 || ownership == 1 && owned || ownership == 2 && !owned
                || ownership == 3 && !owned && price >= 0 && price <= balance);
    }
}
