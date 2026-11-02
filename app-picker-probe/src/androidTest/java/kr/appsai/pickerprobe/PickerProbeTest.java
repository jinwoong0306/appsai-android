package kr.appsai.pickerprobe;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.test.InstrumentationTestCase;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;

public class PickerProbeTest extends InstrumentationTestCase {
    public void testInstalledAppsSearchIconsAndSelectionWithoutUsagePermission() throws Throwable {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .setClassName("kr.appsai.pickerprobe", ProbeActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ProbeActivity activity = (ProbeActivity)getInstrumentation().startActivitySync(intent);
        try {
            runTestOnUiThread(() -> {
                assertFalse("Test device must contain at least one other launcher app", activity.all.isEmpty());
                Set<String> packages = new HashSet<>();
                for (ResolveInfo info : activity.all) {
                    assertTrue(packages.add(info.activityInfo.packageName));
                    assertNotNull(info.loadIcon(activity.getPackageManager()));
                    assertTrue(info.loadLabel(activity.getPackageManager()).length() > 0);
                }
                String expected = activity.all.get(0).activityInfo.packageName;
                EditText search = activity.findViewById(R.id.search);
                search.setText(expected);
                assertTrue(activity.visible.size() > 0);
                assertEquals(expected, activity.visible.get(0).activityInfo.packageName);
                ListView list = activity.findViewById(R.id.apps);
                list.performItemClick(activity.adapter.getView(0, null, list), 0, 0);
                assertEquals(expected, activity.selectedPackage);
                assertTrue(((TextView)activity.findViewById(R.id.selected)).getText().toString().contains(expected));
                search.setText("no.such.appsai.package.000000");
                assertEquals(0, activity.visible.size());
                search.setText("");
                assertEquals(activity.all.size(), activity.visible.size());
            });
        } finally {
            runTestOnUiThread(activity::finish);
        }
    }
}
