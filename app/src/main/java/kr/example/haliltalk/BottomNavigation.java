package kr.example.haliltalk;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Add future destinations here once their screens and actions exist. */
final class BottomNavigation {
    private static final int[][] ITEMS = {
            {R.id.nav_friends, R.string.nav_friends_label, R.drawable.ic_friends},
            {R.id.nav_apps, R.string.nav_apps_label, R.drawable.ic_search},
            {R.id.nav_customize, R.string.nav_customize_label, R.drawable.ic_customize},
            {R.id.nav_shop, R.string.shop_title, R.drawable.ic_shop}
    };

    interface OnDestinationSelected { void select(int destination); }

    static void bind(Activity activity, int selected, OnDestinationSelected listener) {
        LinearLayout bar = activity.findViewById(R.id.bottom_navigation);
        for (int[] item : ITEMS) {
            TextView tab = (TextView) activity.getLayoutInflater()
                    .inflate(R.layout.item_navigation, bar, false);
            tab.setId(item[0]);
            tab.setText(item[1]);
            tab.setSelected(item[0] == selected);
            int color = activity.getColor(tab.isSelected() ? R.color.ink : R.color.muted);
            tab.setTextColor(color);
            Drawable icon = activity.getDrawable(item[2]).mutate();
            icon.setTint(color);
            int size = Math.round(24 * activity.getResources().getDisplayMetrics().density);
            icon.setBounds(0, 0, size, size);
            tab.setCompoundDrawables(null, icon, null, null);
            tab.setOnClickListener(view -> {
                if (item[0] != selected) listener.select(item[0]);
            });
            bar.addView(tab);
        }
    }
}
