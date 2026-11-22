package kr.example.haliltalk;

/** Explicit resource references make missing tutorial captures a build error, never a blank slide. */
final class GuideCatalog {
    private GuideCatalog() {}

    static final class Tutorial {
        final String id;
        final int question,steps,details;
        final int[] images;

        Tutorial(String id,int question,int steps,int details,int... images) {
            this.id=id;this.question=question;this.steps=steps;this.details=details;this.images=images;
        }
    }

    static String[] menu(String topic) {
        switch(topic) {
            case "home":return new String[]{"action","friend","unread","coins"};
            case "journey":return new String[]{"action","review","adventure"};
            case "adventure":return new String[]{"adventure","review","coins"};
            case "shop":return new String[]{"purchase","coins","history"};
            case "wardrobe":return new String[]{"background","purchase"};
            case "goals":return new String[]{"goals","action","files"};
            case "chat":return new String[]{"chat","unread","goals"};
            case "settings":return new String[]{"files","friend","history"};
            default:throw new IllegalArgumentException("Unknown help topic: "+topic);
        }
    }

    static int title(String topic) {
        switch(topic) {
            case "home":return R.string.help_home_title;
            case "journey":return R.string.help_journey_title;
            case "adventure":return R.string.help_adventure_title;
            case "shop":return R.string.help_shop_title;
            case "wardrobe":return R.string.help_wardrobe_title;
            case "goals":return R.string.help_goals_title;
            case "chat":return R.string.help_chat_title;
            case "settings":return R.string.help_settings_title;
            default:throw new IllegalArgumentException("Unknown help topic: "+topic);
        }
    }

    static Tutorial get(String id) {
        switch(id) {
            case "action":return new Tutorial(id,R.string.help_action_question,R.array.help_action_steps,
                    R.string.help_action_details,R.drawable.guide_action_1,R.drawable.guide_action_2,R.drawable.guide_action_3);
            case "review":return new Tutorial(id,R.string.help_review_question,R.array.help_review_steps,
                    R.string.help_review_details,R.drawable.guide_review_1,R.drawable.guide_review_2,R.drawable.guide_review_3);
            case "friend":return new Tutorial(id,R.string.help_friend_question,R.array.help_friend_steps,
                    R.string.help_friend_details,R.drawable.guide_friend_1,R.drawable.guide_friend_2,R.drawable.guide_friend_3);
            case "chat":return new Tutorial(id,R.string.help_chat_question,R.array.help_chat_steps,
                    R.string.help_chat_details,R.drawable.guide_chat_1,R.drawable.guide_chat_2,R.drawable.guide_chat_3);
            case "unread":return new Tutorial(id,R.string.help_unread_question,R.array.help_unread_steps,
                    R.string.help_unread_details,R.drawable.guide_unread_1,R.drawable.guide_unread_2);
            case "adventure":return new Tutorial(id,R.string.help_adventure_question,R.array.help_adventure_steps,
                    R.string.help_adventure_details,R.drawable.guide_adventure_1,R.drawable.guide_adventure_2,R.drawable.guide_adventure_3);
            case "coins":return new Tutorial(id,R.string.help_coins_question,R.array.help_coins_steps,
                    R.string.help_coins_details,R.drawable.guide_coins_1,R.drawable.guide_coins_2,R.drawable.guide_coins_3);
            case "purchase":return new Tutorial(id,R.string.help_purchase_question,R.array.help_purchase_steps,
                    R.string.help_purchase_details,R.drawable.guide_purchase_1,R.drawable.guide_purchase_2,R.drawable.guide_purchase_3);
            case "background":return new Tutorial(id,R.string.help_background_question,R.array.help_background_steps,
                    R.string.help_background_details,R.drawable.guide_background_1,R.drawable.guide_background_2,R.drawable.guide_background_3);
            case "goals":return new Tutorial(id,R.string.help_goals_question,R.array.help_goals_steps,
                    R.string.help_goals_details,R.drawable.guide_goals_1,R.drawable.guide_goals_2,R.drawable.guide_goals_3);
            case "history":return new Tutorial(id,R.string.help_history_question,R.array.help_history_steps,
                    R.string.help_history_details,R.drawable.guide_history_1,R.drawable.guide_history_2);
            case "files":return new Tutorial(id,R.string.help_files_question,R.array.help_files_steps,
                    R.string.help_files_details,R.drawable.guide_files_1,R.drawable.guide_files_2,R.drawable.guide_files_3);
            default:throw new IllegalArgumentException("Unknown help tutorial: "+id);
        }
    }
}
