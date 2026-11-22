package kr.example.haliltalk.character;

import kr.example.haliltalk.R;

/** Free starter scenes. Persistence belongs to the wardrobe's captured account and package. */
public final class SceneBackgrounds {
    public static final String[] IDS={"none","study","park","bedroom","library","cafe"};
    private static final int[] LABELS={R.string.wardrobe_reference_none,R.string.wardrobe_reference_study,
            R.string.wardrobe_reference_park,R.string.wardrobe_reference_bedroom,
            R.string.scene_library_label,R.string.scene_cafe_label};
    private static final int[] DRAWABLES={R.drawable.bg_reference_preview,R.drawable.scene_study,
            R.drawable.scene_garden,R.drawable.scene_bedroom,R.drawable.scene_library,R.drawable.scene_cafe};

    private SceneBackgrounds() {}

    public static String normalize(String id) { return IDS[index(id)]; }
    public static int label(String id) { return LABELS[index(id)]; }
    public static int drawable(String id) { return DRAWABLES[index(id)]; }

    private static int index(String id) {
        for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;
        return 0;
    }
}
