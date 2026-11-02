package kr.example.haliltalk;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

/** Insets and return routing shared by the settings, wardrobe and shop screens. */
public abstract class FeatureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    }

    @Override public void setContentView(int layout) {
        super.setContentView(layout);
        View root = ((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        int left=root.getPaddingLeft(), top=root.getPaddingTop();
        int right=root.getPaddingRight(), bottom=root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()
                        |WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(left+insets.getSystemWindowInsetLeft(),top+insets.getSystemWindowInsetTop(),
                    right+insets.getSystemWindowInsetRight(),bottom+insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.post(root::requestApplyInsets);
    }

    void openMain(boolean apps) {
        startActivity(new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("destination",apps?"apps":"home"));
        finish();
    }

    void openSettings() { startActivity(new Intent(this,SettingsActivity.class)); }
    void navigateFeature(int destination) {
        if(destination==R.id.nav_shop){startActivity(new Intent(this,ShopActivity.class));finish();}
        else if(destination==R.id.nav_customize){startActivity(new Intent(this,CustomizationActivity.class));finish();}
        else openMain(destination==R.id.nav_apps);
    }
}
