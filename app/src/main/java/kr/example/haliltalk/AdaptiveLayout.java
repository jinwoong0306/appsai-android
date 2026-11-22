package kr.example.haliltalk;

import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/** [U6 | 화면] Actual window width and safe/IME space, shared by native screens. */
final class AdaptiveLayout {
    private AdaptiveLayout() {}

    static void bind(View root, View navigation) {
        int[] base={root.getPaddingLeft(),root.getPaddingTop(),root.getPaddingRight(),root.getPaddingBottom()};
        int[] safe={0,0,0,0};
        Runnable layout=()->{
            int maximum=root.getResources().getDimensionPixelSize(R.dimen.content_max_width);
            int extra=Math.max(0,(root.getWidth()-safe[0]-safe[2]-maximum)/2);
            int left=base[0]+safe[0]+extra,right=base[2]+safe[2]+extra;
            int top=base[1]+safe[1],bottom=base[3]+safe[3];
            if(root.getPaddingLeft()!=left||root.getPaddingRight()!=right
                    ||root.getPaddingTop()!=top||root.getPaddingBottom()!=bottom)
                root.setPadding(left,top,right,bottom);
        };
        root.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob)->{
            // A padding request during layout can clip old child positions until another pass.
            if(r-l!=or-ol)root.post(layout);
        });
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets value=insets.getInsets(WindowInsets.Type.systemBars()
                        |WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                safe[0]=value.left;safe[1]=value.top;safe[2]=value.right;safe[3]=value.bottom;
                if(navigation!=null)navigation.setVisibility(
                        insets.isVisible(WindowInsets.Type.ime())?View.GONE:View.VISIBLE);
                layout.run();
                return WindowInsets.CONSUMED;
            }
            safe[0]=insets.getSystemWindowInsetLeft();safe[1]=insets.getSystemWindowInsetTop();
            safe[2]=insets.getSystemWindowInsetRight();safe[3]=insets.getSystemWindowInsetBottom();
            layout.run();
            return insets.consumeSystemWindowInsets();
        });
        root.post(root::requestApplyInsets);
    }
}
