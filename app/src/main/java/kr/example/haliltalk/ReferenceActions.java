package kr.example.haliltalk;

import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.widget.Button;

/** Keep the reference icon and label centered together, including wrapped large text. */
final class ReferenceActions {
    private ReferenceActions() {}

    static void dismissOnEscape(android.app.AlertDialog dialog) {
        dialog.setOnKeyListener((target,key,event)->{
            if(key!=android.view.KeyEvent.KEYCODE_ESCAPE)return false;
            if(event.getAction()==android.view.KeyEvent.ACTION_UP)dialog.cancel();
            return true;
        });
    }

    static void systemBars(android.app.Activity activity) {
        if(android.os.Build.VERSION.SDK_INT>=30){
            int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    |android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            activity.getWindow().getInsetsController().setSystemBarsAppearance(
                    activity.getResources().getBoolean(R.bool.light_system_bars)?mask:0,mask);
        }
    }

    static void label(Button button,int text,int icon) {
        String title=button.getContext().getString(text);
        Drawable drawable=button.getContext().getDrawable(icon).mutate();
        int size=Math.round(24*button.getResources().getDisplayMetrics().density);
        drawable.setBounds(0,0,size,size);
        drawable.setTint(button.getCurrentTextColor());
        SpannableString content=new SpannableString("  "+title);
        content.setSpan(new ImageSpan(drawable,ImageSpan.ALIGN_BOTTOM),0,1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(content);button.setContentDescription(title);
    }
}
