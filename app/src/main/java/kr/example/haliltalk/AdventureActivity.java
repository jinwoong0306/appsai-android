package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.data.AdventureStore;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.JourneyStore;

/** Explicit route choices and local daily records; no automatic completion or coin rewards. */
public final class AdventureActivity extends FeatureActivity {
    private static final int[] STAMPS={1,3,7,14};
    private static final int[] SCENES={R.drawable.scene_garden,R.drawable.scene_study,
            R.drawable.scene_bedroom,R.drawable.scene_library};
    private static final int[] SUGGESTIONS={R.id.adventure_suggestion_one,
            R.id.adventure_suggestion_two,R.id.adventure_suggestion_three};
    private FriendsDatabase helper;
    private SQLiteDatabase db;
    private AdventureStore adventures;
    private JourneyStore journeys;
    private final List<Integer> friends=new ArrayList<>();
    private final List<String> names=new ArrayList<>();
    private int friend,selectedDays=3,suggestionIndex,activeRouteDays;
    private String day="",openedDatabase;
    private long openedGeneration;
    private boolean openedEntered,binding,loaded,destroyed,routeSelectionKnown;
    private boolean routeExpanded,suggestionsExpanded,galleryExpanded,routeExpansionKnown;
    private String[] actions;
    private String dialogKind="",pendingDay="",dialogError="";
    private int pendingFriend,pendingDays,pendingCard;
    // The owned dialog remains observable for deterministic interaction and recreation tests.
    AlertDialog prompt;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openedDatabase=state==null?AccountSession.databaseName(this):state.getString("opened_database");
        openedGeneration=state==null?AccountSession.generation():state.getLong("opened_generation");
        openedEntered=state==null?AccountSession.isEntered(this):state.getBoolean("opened_entered");
        friend=state==null?getIntent().getIntExtra("friend_id",0):state.getInt("friend");
        if(state!=null){
            routeExpanded=state.getBoolean("route_expanded");
            suggestionsExpanded=state.getBoolean("suggestions_expanded");
            galleryExpanded=state.getBoolean("gallery_expanded");
            routeExpansionKnown=state.getBoolean("route_expansion_known");
            routeSelectionKnown=true;
            selectedDays=state.getInt("selected_days",3);
            suggestionIndex=state.getInt("suggestion",0);
            dialogKind=state.getString("dialog_kind","");
            pendingFriend=state.getInt("pending_friend");
            pendingDays=state.getInt("pending_days");
            pendingDay=state.getString("pending_day","");
            pendingCard=state.getInt("pending_card");
            dialogError=state.getString("dialog_error","");
        }
        setContentView(R.layout.screen_adventure);
        findViewById(R.id.adventure_back).setOnClickListener(v->finish());
        findViewById(R.id.adventure_route_toggle).setOnClickListener(v->{
            routeExpanded=!routeExpanded;routeExpansionKnown=true;renderSections();
        });
        findViewById(R.id.adventure_suggestions_toggle).setOnClickListener(v->{
            suggestionsExpanded=!suggestionsExpanded;renderSections();
        });
        findViewById(R.id.adventure_gallery_toggle).setOnClickListener(v->{
            galleryExpanded=!galleryExpanded;renderSections();
        });
        renderSections();
        findViewById(R.id.adventure_primary).setOnClickListener(v->primary());
        findViewById(R.id.adventure_choose).setOnClickListener(v->confirmRoute());
        ((Spinner)findViewById(R.id.adventure_friend)).setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener(){
                    @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                        if(binding||position<0||position>=friends.size())return;
                        int next=friends.get(position);
                        if(next!=friend){
                            friend=next;selectedDays=3;suggestionIndex=0;loaded=false;routeSelectionKnown=false;
                            routeExpansionKnown=false;
                            clearDialog();render();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent){}
                });
        ((RadioGroup)findViewById(R.id.adventure_routes)).setOnCheckedChangeListener((group,id)->{
            if(binding)return;
            selectedDays=id==R.id.adventure_route_fourteen?14:id==R.id.adventure_route_seven?7:3;
            if(activeRouteDays==0){suggestionIndex=0;renderSuggestions(selectedDays);renderRoute(null);}
        });
        ((RadioGroup)findViewById(R.id.adventure_suggestions)).setOnCheckedChangeListener((group,id)->{
            if(!binding)for(int i=0;i<SUGGESTIONS.length;i++)if(SUGGESTIONS[i]==id)suggestionIndex=i;
        });
        render();
        if(!dialogKind.isEmpty())showPendingDialog();
    }

    @Override protected void onResume(){
        super.onResume();
        if(findViewById(R.id.adventure_primary)!=null)render();
    }

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused){
            if(findViewById(R.id.adventure_primary)!=null&&!day.equals(LocalDate.now().toString()))render();
        }
    }

    private boolean sameSession(){
        return !destroyed&&openedEntered&&AccountSession.isEntered(this)
                &&openedGeneration==AccountSession.generation()
                &&openedDatabase.equals(AccountSession.databaseName(this));
    }

    private void error(String message){
        TextView view=findViewById(R.id.adventure_error);
        view.setText(message);view.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE);
    }

    private void render(){
        if(!sameSession()){
            error(getString(R.string.adventure_error_session));
            findViewById(R.id.adventure_primary).setEnabled(false);
            findViewById(R.id.adventure_choose).setEnabled(false);
            return;
        }
        day=LocalDate.now().toString();
        try{
            if(db==null){
                if(helper==null)helper=new FriendsDatabase(this);
                db=helper.getWritableDatabase();
                adventures=new AdventureStore(db);journeys=new JourneyStore(db);
            }
            binding=true;
            friends.clear();names.clear();
            try(Cursor c=db.rawQuery("SELECT id,name FROM friends ORDER BY id",null)){
                while(c.moveToNext()){friends.add(c.getInt(0));names.add(c.getString(1));}
            }
            if(!friends.contains(friend)){
                friend=friends.isEmpty()?0:friends.get(0);
                selectedDays=3;suggestionIndex=0;loaded=false;routeSelectionKnown=false;
                routeExpansionKnown=false;
            }
            Spinner picker=findViewById(R.id.adventure_friend);
            picker.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            picker.setEnabled(friend!=0);
            picker.setVisibility(friend==0?View.GONE:View.VISIBLE);
            findViewById(R.id.adventure_friend_label).setVisibility(friend==0?View.GONE:View.VISIBLE);
            if(friend!=0)picker.setSelection(friends.indexOf(friend));
            findViewById(R.id.adventure_content).setVisibility(friend==0?View.GONE:View.VISIBLE);
            findViewById(R.id.adventure_empty).setVisibility(friend==0?View.VISIBLE:View.GONE);
            Button primary=findViewById(R.id.adventure_primary);
            primary.setText(friend==0?R.string.adventure_add_friend:R.string.adventure_primary);
            primary.setEnabled(true);findViewById(R.id.adventure_choose).setEnabled(true);
            error(dialogError);
            if(friend==0){loaded=true;return;}
            appearance(findViewById(R.id.adventure_character),friend);
            AdventureStore.Choice choice=adventures.choice(friend);
            if(!routeExpansionKnown){routeExpanded=choice==null;routeExpansionKnown=true;}
            renderSections();
            activeRouteDays=choice==null?0:choice.days;
            if(!routeSelectionKnown&&choice!=null)selectedDays=choice.days;
            routeSelectionKnown=true;
            ((RadioGroup)findViewById(R.id.adventure_routes)).check(selectedDays==14?
                    R.id.adventure_route_fourteen:selectedDays==7?R.id.adventure_route_seven:R.id.adventure_route_three);
            renderRoute(choice);
            renderToday(journeys.entry(friend,day));
            renderSuggestions(choice==null?selectedDays:choice.days);
            renderGallery();
            loaded=true;
        }catch(SQLiteException failure){
            loaded=false;error(getString(R.string.adventure_error_read));
            ((Button)findViewById(R.id.adventure_primary)).setText(R.string.adventure_retry);
            findViewById(R.id.adventure_primary).setEnabled(true);
            findViewById(R.id.adventure_choose).setEnabled(false);
        }finally{binding=false;}
    }

    private void renderSections(){
        findViewById(R.id.adventure_route_details).setVisibility(routeExpanded?View.VISIBLE:View.GONE);
        findViewById(R.id.adventure_suggestion_details).setVisibility(suggestionsExpanded?View.VISIBLE:View.GONE);
        findViewById(R.id.adventure_gallery_details).setVisibility(galleryExpanded?View.VISIBLE:View.GONE);
        ((Button)findViewById(R.id.adventure_route_toggle)).setText(routeExpanded?
                R.string.adventure_route_close:R.string.adventure_route_open);
        ((Button)findViewById(R.id.adventure_suggestions_toggle)).setText(suggestionsExpanded?
                R.string.adventure_suggestions_close:R.string.adventure_suggestions_open);
        ((Button)findViewById(R.id.adventure_gallery_toggle)).setText(galleryExpanded?
                R.string.adventure_gallery_close:R.string.adventure_gallery_open);
    }

    private void appearance(CharacterView character,int owner){
        try(Cursor c=db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",
                new String[]{""+owner})){
            if(c.moveToFirst())character.setAppearance(c.getString(0),c.getString(1),c.getString(2),
                    c.getString(3),c.getString(4),c.getString(5));
            else character.setAppearance("chat","none","none","none");
        }
    }

    private String name(int owner){
        int index=friends.indexOf(owner);
        return index<0?"":names.get(index);
    }

    private String formatDay(String iso){
        return LocalDate.parse(iso).format(java.time.format.DateTimeFormatter
                .ofPattern("yyyy년 M월 d일",java.util.Locale.KOREAN));
    }

    private String routeName(int days){
        return getResources().getStringArray(R.array.adventure_route_names)[days==14?2:days==7?1:0];
    }

    private void renderRoute(AdventureStore.Choice choice){
        int target=choice==null?selectedDays:choice.days;
        int progress=choice==null?0:adventures.activeDays(friend,day);
        TextView route=findViewById(R.id.adventure_route);
        route.setText(choice==null?getString(R.string.adventure_no_route):
                getString(progress>=target?R.string.adventure_route_complete:R.string.adventure_route_progress,
                        routeName(target),progress,target,formatDay(choice.startedDay)));
        ProgressBar bar=findViewById(R.id.adventure_progress);
        bar.setMax(target);bar.setProgress(Math.min(progress,target));
        bar.setContentDescription(getString(R.string.adventure_route_progress,routeName(target),
                progress,target,choice==null?formatDay(day):formatDay(choice.startedDay)));
        LinearLayout stages=findViewById(R.id.adventure_stages);stages.removeAllViews();
        for(int checkpoint:new int[]{1,(target+1)/2,target}){
            TextView stage=(TextView)getLayoutInflater().inflate(R.layout.row_adventure_stage,stages,false);
            stage.setTag(checkpoint);
            stage.setActivated(progress>=checkpoint);
            stage.setText(getString(R.string.adventure_stage,checkpoint,progress>=checkpoint?
                    getString(R.string.adventure_stage_reached):
                    getString(R.string.adventure_stage_remaining,checkpoint-progress)));
            stages.addView(stage);
        }
    }

    private void renderToday(JourneyStore.Entry entry){
        boolean reviewed=entry!=null&&!"planned".equals(entry.outcome);
        boolean reflection=reviewed&&!entry.note.trim().isEmpty();
        TextView plan=findViewById(R.id.adventure_quest_plan);
        plan.setText(entry==null?R.string.adventure_quest_plan_open:R.string.adventure_quest_plan_done);
        plan.setTag(entry!=null);
        TextView review=findViewById(R.id.adventure_quest_review);
        int status=entry==null?R.string.journey_planned:"done".equals(entry.outcome)?R.string.journey_done:
                "partial".equals(entry.outcome)?R.string.journey_partial:R.string.journey_rest;
        review.setText(reviewed?getString(R.string.adventure_quest_review_done,getString(status)):
                getString(R.string.adventure_quest_review_open));
        review.setTag(reviewed);
        TextView note=findViewById(R.id.adventure_quest_reflection);
        note.setText(reflection?R.string.adventure_quest_reflection_done:R.string.adventure_quest_reflection_open);
        note.setTag(reflection);
        ((TextView)findViewById(R.id.adventure_today_action)).setText(entry==null?
                getString(R.string.adventure_today_none):getString(R.string.adventure_today_action,entry.action));
        ((TextView)findViewById(R.id.adventure_date)).setText(formatDay(day));
        int feedback=entry==null?R.string.adventure_feedback_none:"done".equals(entry.outcome)?
                R.string.adventure_feedback_done:"partial".equals(entry.outcome)?R.string.adventure_feedback_partial:
                "rest".equals(entry.outcome)?R.string.adventure_feedback_rest:R.string.adventure_feedback_planned;
        ((TextView)findViewById(R.id.adventure_feedback)).setText(
                getString(R.string.adventure_feedback_named,name(friend),getString(feedback)));
    }

    private void renderSuggestions(int days){
        boolean previous=binding;binding=true;
        actions=getResources().getStringArray(days==14?R.array.adventure_actions_challenge:
                days==7?R.array.adventure_actions_routine:R.array.adventure_actions_first);
        ((TextView)findViewById(R.id.adventure_suggestion_theme)).setText(
                getString(R.string.adventure_suggestion_theme,routeName(days)));
        for(int i=0;i<SUGGESTIONS.length;i++)((RadioButton)findViewById(SUGGESTIONS[i])).setText(actions[i]);
        ((RadioGroup)findViewById(R.id.adventure_suggestions)).check(SUGGESTIONS[suggestionIndex]);
        binding=previous;
    }

    private View postcard(int owner,int index,int total,boolean reveal){
        View card=getLayoutInflater().inflate(R.layout.row_adventure_postcard,
                (LinearLayout)findViewById(R.id.adventure_gallery),false);
        String title=getResources().getStringArray(R.array.adventure_postcard_names)[index];
        boolean earned=total>=STAMPS[index];
        card.setTag(STAMPS[index]);card.setActivated(earned);
        ((TextView)card.findViewById(R.id.adventure_postcard_title)).setText(title);
        ((ImageView)card.findViewById(R.id.adventure_postcard_scene)).setImageResource(SCENES[index]);
        appearance(card.findViewById(R.id.adventure_postcard_character),owner);
        // Locked artwork is not revealed before its real recorded milestone.
        card.findViewById(R.id.adventure_postcard_art).setVisibility(earned?View.VISIBLE:View.GONE);
        ((TextView)card.findViewById(R.id.adventure_postcard_state)).setText(reveal?
                getString(R.string.adventure_postcard_message,name(owner),STAMPS[index]):
                earned?getString(R.string.adventure_postcard_earned,STAMPS[index]):
                getString(R.string.adventure_postcard_locked,STAMPS[index],STAMPS[index]-total));
        Button open=card.findViewById(R.id.adventure_postcard_open);
        open.setVisibility(reveal?View.GONE:View.VISIBLE);
        open.setText(earned?R.string.adventure_postcard_open:R.string.adventure_postcard_requirement);
        open.setOnClickListener(v->{
            pendingFriend=owner;pendingCard=index;dialogKind="postcard";dialogError="";
            showPendingDialog();
        });
        return card;
    }

    private void renderGallery(){
        int total=journeys.summary(friend,day).totalActiveDays;
        ((TextView)findViewById(R.id.adventure_stamps)).setText(getString(R.string.adventure_stamps,total));
        LinearLayout gallery=findViewById(R.id.adventure_gallery);gallery.removeAllViews();
        for(int i=0;i<STAMPS.length;i++)gallery.addView(postcard(friend,i,total,false));
    }

    private void primary(){
        if(!sameSession()){error(getString(R.string.adventure_error_session));return;}
        if(!loaded){render();return;}
        if(friend==0){openMain(true);return;}
        startActivity(new Intent(this,JourneyActivity.class).putExtra("friend_id",friend)
                .putExtra("adventure_action",actions[suggestionIndex]));
    }

    private void confirmRoute(){
        if(!sameSession()){error(getString(R.string.adventure_error_session));return;}
        if(!loaded||friend==0)return;
        pendingFriend=friend;pendingDays=selectedDays;pendingDay=LocalDate.now().toString();
        dialogKind="route";dialogError="";showPendingDialog();
    }

    private String confirmation(){
        return getString(R.string.adventure_confirm,name(pendingFriend),routeName(pendingDays),
                pendingDays,formatDay(pendingDay))+(dialogError.isEmpty()?"":"\n\n"+dialogError);
    }

    private void showPendingDialog(){
        if(prompt!=null){prompt.setOnDismissListener(null);prompt.dismiss();}
        try{
            AlertDialog.Builder builder=new AlertDialog.Builder(this);
            if("route".equals(dialogKind)){
                builder.setTitle(R.string.adventure_confirm_title).setMessage(confirmation())
                        .setNegativeButton(R.string.journey_cancel,null)
                        .setPositiveButton(R.string.adventure_confirm_save,null);
            }else{
                if(!sameSession())throw new IllegalArgumentException(getString(R.string.adventure_error_session));
                int total=journeys.summary(pendingFriend,LocalDate.now().toString()).totalActiveDays;
                builder.setTitle(getResources().getStringArray(R.array.adventure_postcard_names)[pendingCard]);
                if(total>=STAMPS[pendingCard]){
                    android.widget.ScrollView scroll=new android.widget.ScrollView(this);
                    scroll.addView(postcard(pendingFriend,pendingCard,total,true));builder.setView(scroll);
                }else builder.setMessage(getString(R.string.adventure_postcard_locked,
                        STAMPS[pendingCard],STAMPS[pendingCard]-total));
                builder.setPositiveButton(android.R.string.ok,null);
            }
            prompt=builder.create();
            prompt.setOnDismissListener(dialog->{
                dialogKind="";dialogError="";
                error(sameSession()?"":getString(R.string.adventure_error_session));
            });
            prompt.setCanceledOnTouchOutside(false);
            prompt.show();
            if("route".equals(dialogKind)){
                prompt.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
                prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->saveRoute());
            }
        }catch(SQLiteException failure){dialogKind="";error(getString(R.string.adventure_error_read));}
        catch(IllegalArgumentException failure){dialogKind="";error(failure.getMessage());}
    }

    private void saveRoute(){
        try{
            if(!sameSession())throw new IllegalArgumentException(getString(R.string.adventure_error_session));
            if(!pendingDay.equals(LocalDate.now().toString()))
                throw new IllegalArgumentException(getString(R.string.adventure_error_day));
            adventures.choose(pendingFriend,pendingDays,pendingDay);
            selectedDays=pendingDays;suggestionIndex=0;prompt.dismiss();render();
        }catch(IllegalArgumentException failure){routeError(failure.getMessage());}
        catch(SQLiteException failure){routeError(getString(R.string.adventure_error_write));}
    }

    private void routeError(String message){
        dialogError=message;prompt.setMessage(confirmation());error(message);
    }

    private void clearDialog(){
        if(prompt!=null)prompt.dismiss();
        dialogKind="";dialogError="";
    }

    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("opened_database",openedDatabase);state.putLong("opened_generation",openedGeneration);
        state.putBoolean("opened_entered",openedEntered);state.putInt("friend",friend);
        state.putInt("selected_days",selectedDays);state.putInt("suggestion",suggestionIndex);
        state.putString("dialog_kind",dialogKind);state.putInt("pending_friend",pendingFriend);
        state.putInt("pending_days",pendingDays);state.putString("pending_day",pendingDay);
        state.putInt("pending_card",pendingCard);state.putString("dialog_error",dialogError);
        state.putBoolean("route_expanded",routeExpanded);
        state.putBoolean("suggestions_expanded",suggestionsExpanded);
        state.putBoolean("gallery_expanded",galleryExpanded);
        state.putBoolean("route_expansion_known",routeExpansionKnown);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy(){
        destroyed=true;
        if(prompt!=null){prompt.setOnDismissListener(null);prompt.dismiss();}
        if(helper!=null)helper.close();
        super.onDestroy();
    }
}
