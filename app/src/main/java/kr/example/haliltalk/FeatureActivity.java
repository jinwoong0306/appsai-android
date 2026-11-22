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
    @Override protected void attachBaseContext(android.content.Context base){
        super.attachBaseContext(KoreanUi.context(base));
    }
    private String openedDatabase;
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        openedDatabase=kr.example.haliltalk.auth.AccountSession.databaseName(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    }
    @Override protected void onStart() {
        super.onStart();
        if(!(this instanceof LoginActivity)&&(!kr.example.haliltalk.auth.AccountSession.isEntered(this)
                ||!openedDatabase.equals(kr.example.haliltalk.auth.AccountSession.databaseName(this)))) {
            startActivity(new Intent(this,LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
        }
    }

    @Override public void setContentView(int layout) {
        super.setContentView(layout);
        View root = ((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        AdaptiveLayout.bind(root,findViewById(R.id.bottom_navigation));
        View help=findViewById(R.id.feature_help);
        if(help!=null){
            String topic=layout==R.layout.screen_customization?"wardrobe":
                    layout==R.layout.screen_goals?"goals":layout==R.layout.screen_journey?"journey":
                    layout==R.layout.screen_adventure?"adventure":"settings";
            help.setOnClickListener(v->HelpGuide.show(this,topic));
        }
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
