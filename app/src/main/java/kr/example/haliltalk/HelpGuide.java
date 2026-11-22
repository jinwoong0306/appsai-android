package kr.example.haliltalk;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Screenshot education only. Explicit home Close/Skip is the sole preference write. */
public final class HelpGuide extends DialogFragment {
    static final String TAG="help_guide";
    static final String PREFERENCES="help_guide";
    static final String HOME_COMPLETE="home_complete";
    private String topic,tutorial;
    private int step;
    private boolean details,zoom;
    private View content;

    public static void show(Activity activity,String topic) {
        GuideCatalog.menu(topic);
        if(activity.isFinishing()||activity.isDestroyed())return;
        FragmentManager manager=activity.getFragmentManager();
        if(manager.isStateSaved()||manager.findFragmentByTag(TAG)!=null)return;
        HelpGuide guide=new HelpGuide();
        Bundle arguments=new Bundle();
        arguments.putString("topic",topic);
        guide.setArguments(arguments);
        guide.show(manager,TAG);
        // Make the tag visible before another click can enqueue a second fragment.
        manager.executePendingTransactions();
    }

    public static boolean isHomeComplete(Context context) {
        return context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
                .getBoolean(HOME_COMPLETE,false);
    }

    public static void skipHome(Context context) {
        context.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
                .edit().putBoolean(HOME_COMPLETE,true).apply();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Bundle saved=state==null?getArguments():state;
        topic=saved.getString("topic");
        tutorial=saved.getString("tutorial");
        step=saved.getInt("step",0);
        details=saved.getBoolean("details",false);
        zoom=saved.getBoolean("zoom",false);
    }

    @Override public Dialog onCreateDialog(Bundle state) {
        Dialog dialog=new Dialog(getActivity(),R.style.HelpGuideTheme) {
            @Override public void onBackPressed() {back();}
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        content=LayoutInflater.from(dialog.getContext()).inflate(R.layout.dialog_help_guide,
                (android.view.ViewGroup)dialog.getWindow().getDecorView().findViewById(android.R.id.content),false);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(false);
        content.findViewById(R.id.help_back).setOnClickListener(v->back());
        content.findViewById(R.id.help_close).setOnClickListener(v->closeGuide());
        content.findViewById(R.id.help_skip).setOnClickListener(v->closeGuide());
        content.findViewById(R.id.help_previous).setOnClickListener(v->{
            if(step==0)showMenu();
            else {step--;details=false;render();}
        });
        content.findViewById(R.id.help_next).setOnClickListener(v->{
            if(step==GuideCatalog.get(tutorial).images.length-1)showMenu();
            else {step++;details=false;render();}
        });
        content.findViewById(R.id.help_details_toggle).setOnClickListener(v->{
            details=!details;renderDetails();saveState(getArguments());
        });
        content.findViewById(R.id.help_image).setOnClickListener(v->{
            zoom=true;render();
        });
        content.findViewById(R.id.help_zoom_close).setOnClickListener(v->{
            zoom=false;render();
        });
        GuideImageView image=content.findViewById(R.id.help_zoom_image);
        content.findViewById(R.id.help_zoom_reset).setOnClickListener(v->image.resetZoom());
        content.findViewById(R.id.help_zoom_in).setOnClickListener(v->image.zoomBy(1.5f));
        content.findViewById(R.id.help_zoom_out).setOnClickListener(v->image.zoomBy(1/1.5f));
        LinearLayout questions=content.findViewById(R.id.help_questions);
        for(String id:GuideCatalog.menu(topic)) {
            Button question=(Button)LayoutInflater.from(dialog.getContext())
                    .inflate(R.layout.row_help_question,questions,false);
            question.setText(GuideCatalog.get(id).question);
            question.setTag(id);
            question.setOnClickListener(v->{
                tutorial=id;step=0;details=false;zoom=false;render();
            });
            questions.addView(question);
        }
        if(android.os.Build.VERSION.SDK_INT>=28) {
            content.findViewById(R.id.help_title).setAccessibilityHeading(true);
            content.findViewById(R.id.help_headline).setAccessibilityHeading(true);
        }
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        render();
    }

    private void sizeWindow() {
        Rect available=new Rect();
        getActivity().getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        int inset=zoom?0:dp(16);
        int width=zoom?available.width():Math.min(dp(640),available.width()-inset*2);
        int height=available.height()-inset*2;
        Window window=getDialog().getWindow();
        window.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.surface,null)));
        window.setLayout(width,height);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        ImageView image=content.findViewById(R.id.help_image);
        // A portrait screenshot stays large; landscape can scroll it without moving controls.
        image.getLayoutParams().height=Math.max(dp(240),Math.min(dp(480),
                Math.round(available.height()*0.58f)));
        image.requestLayout();
    }

    private int dp(int value) {
        return Math.round(value*getResources().getDisplayMetrics().density);
    }

    private void render() {
        boolean menu=tutorial==null;
        content.findViewById(R.id.help_normal).setVisibility(zoom?View.GONE:View.VISIBLE);
        content.findViewById(R.id.help_zoom).setVisibility(zoom?View.VISIBLE:View.GONE);
        content.findViewById(R.id.help_questions).setVisibility(menu?View.VISIBLE:View.GONE);
        content.findViewById(R.id.help_tutorial).setVisibility(menu?View.GONE:View.VISIBLE);
        content.findViewById(R.id.help_controls).setVisibility(menu?View.GONE:View.VISIBLE);
        content.findViewById(R.id.help_skip).setVisibility(
                menu&&"home".equals(topic)?View.VISIBLE:View.GONE);
        ((TextView)content.findViewById(R.id.help_title)).setText(menu?
                GuideCatalog.title(topic):GuideCatalog.get(tutorial).question);
        ((TextView)content.findViewById(R.id.help_back)).setText(menu?
                R.string.help_back:R.string.help_menu);
        if(!menu) {
            GuideCatalog.Tutorial guide=GuideCatalog.get(tutorial);
            String[] words=getResources().getStringArray(guide.steps);
            ((TextView)content.findViewById(R.id.help_progress)).setText(
                    getString(R.string.help_progress,step+1,guide.images.length));
            content.findViewById(R.id.help_progress).setTag(step);
            ((TextView)content.findViewById(R.id.help_headline)).setText(words[step*3]);
            ((TextView)content.findViewById(R.id.help_body)).setText(words[step*3+1]);
            String description=getString(R.string.help_image_description,words[step*3+2]);
            ImageView image=content.findViewById(R.id.help_image);
            image.setImageResource(guide.images[step]);
            image.setTag(guide.images[step]);
            image.setContentDescription(description);
            GuideImageView enlarged=content.findViewById(R.id.help_zoom_image);
            if(!Integer.valueOf(guide.images[step]).equals(enlarged.getTag())) {
                enlarged.setImageResource(guide.images[step]);
                enlarged.setTag(guide.images[step]);
                enlarged.resetZoom();
            }
            enlarged.setContentDescription(description);
            ((TextView)content.findViewById(R.id.help_zoom_title)).setText(words[step*3]);
            ((TextView)content.findViewById(R.id.help_details)).setText(guide.details);
            ((Button)content.findViewById(R.id.help_previous)).setText(
                    step==0?R.string.help_menu:R.string.help_previous);
            ((Button)content.findViewById(R.id.help_next)).setText(
                    step==guide.images.length-1?R.string.help_done:R.string.help_next);
            renderDetails();
        }
        ((ScrollView)content.findViewById(R.id.help_scroll)).scrollTo(0,0);
        saveState(getArguments());
        if(getDialog()!=null&&getDialog().isShowing())sizeWindow();
    }

    private void renderDetails() {
        content.findViewById(R.id.help_details).setVisibility(details?View.VISIBLE:View.GONE);
        Button toggle=content.findViewById(R.id.help_details_toggle);
        toggle.setText(details?R.string.help_details_hide:R.string.help_details_show);
        toggle.setContentDescription(getString(details?
                R.string.help_details_hide:R.string.help_details_show));
    }

    private void back() {
        if(zoom){zoom=false;render();}
        else if(tutorial!=null)showMenu();
        else dismiss();
    }

    private void showMenu() {
        tutorial=null;step=0;details=false;zoom=false;render();
    }

    private void closeGuide() {
        if("home".equals(topic))skipHome(getActivity());
        dismiss();
    }

    private void saveState(Bundle state) {
        state.putString("topic",topic);
        state.putString("tutorial",tutorial);
        state.putInt("step",step);
        state.putBoolean("details",details);
        state.putBoolean("zoom",zoom);
    }

    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        saveState(state);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        content=null;
    }
}
