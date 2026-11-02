package kr.appsai.pickerprobe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProbeActivity extends Activity {
    final List<ResolveInfo> all = new ArrayList<>();
    final List<ResolveInfo> visible = new ArrayList<>();
    String selectedPackage;
    BaseAdapter adapter;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_probe);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        findViewById(R.id.probe_root).setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ((TextView)findViewById(R.id.environment)).setText(getString(R.string.environment,
                Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, getApplicationInfo().targetSdkVersion));
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : getPackageManager().queryIntentActivities(launcher, 0)) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName()) && seen.add(pkg)) all.add(info);
        }
        all.sort(Comparator.comparing(info -> info.loadLabel(getPackageManager()).toString(),
                String.CASE_INSENSITIVE_ORDER));
        if (state != null) selectedPackage = state.getString("selected_package");
        ListView list = findViewById(R.id.apps);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public ResolveInfo getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                View row = recycled == null ? getLayoutInflater().inflate(R.layout.row_app, parent, false) : recycled;
                ResolveInfo info = getItem(position);
                ((ImageView)row.findViewById(R.id.icon)).setImageDrawable(info.loadIcon(getPackageManager()));
                ((TextView)row.findViewById(R.id.label)).setText(info.loadLabel(getPackageManager()));
                ((TextView)row.findViewById(R.id.package_name)).setText(info.activityInfo.packageName);
                row.setActivated(info.activityInfo.packageName.equals(selectedPackage));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(android.R.id.empty));
        list.setOnItemClickListener((parent, view, position, id) -> {
            selectedPackage = visible.get(position).activityInfo.packageName;
            showSelection();
            adapter.notifyDataSetChanged();
        });
        ((EditText)findViewById(R.id.search)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        filter("");
        showSelection();
    }

    void filter(String query) {
        String term = query.trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (ResolveInfo info : all) {
            if (info.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).contains(term)
                    || info.activityInfo.packageName.toLowerCase(Locale.ROOT).contains(term)) visible.add(info);
        }
        adapter.notifyDataSetChanged();
        ((TextView)findViewById(R.id.count)).setText(getString(R.string.count, all.size(), visible.size()));
    }

    void showSelection() {
        ((TextView)findViewById(R.id.selected)).setText(selectedPackage == null
                ? getString(R.string.none_selected) : getString(R.string.selected, selectedPackage));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("selected_package", selectedPackage);
        super.onSaveInstanceState(state);
    }
}
