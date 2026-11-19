package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalStore;
import kr.example.haliltalk.data.JourneyStore;
import kr.example.haliltalk.auth.AccountSession;

/** [U3 | 동작] 사용자가 선택한 작은 행동과 회고. AI 추측·자동 완료·출석 보상 없음. */
public final class JourneyActivity extends FeatureActivity {
    private FriendsDatabase helper;
    private android.database.sqlite.SQLiteDatabase db;
    private JourneyStore store;
    private final List<Integer> friends=new ArrayList<>();
    private int friend;
    String day;
    AlertDialog editor, prompt, suggestions;
    private View fields;
    private String editorKind="", editorDay="", originalDraft="";
    private List<String> goalTitles;
    private boolean discarding;
    private int historyLimit=7;
    private String openedDatabase,adventureAction="";
    private long openedGeneration;
    private boolean openedEntered,destroyed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openedDatabase=state==null?AccountSession.databaseName(this):state.getString("opened_database");
        openedGeneration=state==null?AccountSession.generation():state.getLong("opened_generation");
        openedEntered=state==null?AccountSession.isEntered(this):state.getBoolean("opened_entered");
        helper=new FriendsDatabase(this);db=helper.getWritableDatabase();store=new JourneyStore(db);
        setContentView(R.layout.screen_journey);
        findViewById(R.id.journey_back).setOnClickListener(v->finish());
        List<String> names=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,name FROM friends ORDER BY id",null)) {
            while(c.moveToNext()){friends.add(c.getInt(0));names.add(c.getString(1));}
        }
        int requested=state==null?getIntent().getIntExtra("friend_id",0):state.getInt("friend");
        friend=friends.contains(requested)?requested:friends.isEmpty()?0:friends.get(0);
        if(state!=null)historyLimit=state.getInt("history_limit",7);
        Spinner chooser=findViewById(R.id.journey_friend);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);
        chooser.setAdapter(adapter);
        if(friend!=0)chooser.setSelection(friends.indexOf(friend));
        chooser.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                int next=friends.get(position);
                if(next!=friend){friend=next;historyLimit=7;render();}
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        findViewById(R.id.journey_plan).setOnClickListener(v->editPlan(null));
        findViewById(R.id.journey_review).setOnClickListener(v->editReview(null));
        findViewById(R.id.journey_adventure).setOnClickListener(v->startActivity(
                new Intent(this,AdventureActivity.class).putExtra("friend_id",friend)));
        findViewById(R.id.journey_goals).setOnClickListener(v->startActivity(
                new Intent(this,GoalsActivity.class).putExtra("friend_id",friend)));
        render();
        if(state!=null&&!state.getString("editor","").isEmpty()) {
            if(state.getString("editor").equals("plan"))editPlan(state);else editReview(state);
        }
        // A suggestion is consumed once, for the requested friend and only an empty day.
        if(state==null&&requested==friend&&friend!=0&&getIntent().hasExtra("adventure_action")
                &&store.entry(friend,day)==null){
            adventureAction=getIntent().getStringExtra("adventure_action");
            editPlan(null);adventureAction="";
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(store!=null&&editorKind.isEmpty())render();
    }

    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused){
            if(store!=null&&editorKind.isEmpty()&&!LocalDate.now().toString().equals(day))render();
        }
    }

    private String formatDay(String iso){
        return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)",Locale.KOREAN));
    }

    private String status(String outcome){
        return getString(outcome.equals("done")?R.string.journey_done:
                outcome.equals("partial")?R.string.journey_partial:
                outcome.equals("rest")?R.string.journey_rest:R.string.journey_planned);
    }

    private String details(JourneyStore.Entry entry){
        return entry.action+(entry.cue.isEmpty()?"":"\n시작할 때: "+entry.cue)
                +(entry.goalTitle.isEmpty()?"":"\n연결한 목표: "+entry.goalTitle)
                +(entry.note.isEmpty()?"":"\n기억할 한 줄: "+entry.note);
    }

    private void render(){
        day=LocalDate.now().toString();
        ((TextView)findViewById(R.id.journey_date)).setText(getString(R.string.journey_date,formatDay(day)));
        findViewById(R.id.journey_content).setVisibility(friend==0?View.GONE:View.VISIBLE);
        findViewById(R.id.journey_empty).setVisibility(friend==0?View.VISIBLE:View.GONE);
        if(friend==0)return;
        CharacterView character=findViewById(R.id.journey_character);
        try(Cursor c=db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",new String[]{""+friend})){
            if(c.moveToFirst())character.setAppearance(c.getString(0),c.getString(1),c.getString(2),
                    c.getString(3),c.getString(4),c.getString(5));
            else character.setAppearance("chat","none","none","none");
        }
        JourneyStore.Entry today=store.entry(friend,day);
        ((TextView)findViewById(R.id.journey_today_text)).setText(today==null?
                getString(R.string.journey_empty):status(today.outcome)+"\n"+details(today));
        boolean reviewed=today!=null&&!today.outcome.equals("planned");
        Button plan=findViewById(R.id.journey_plan);
        plan.setVisibility(reviewed?View.GONE:View.VISIBLE);
        plan.setText(today==null?R.string.journey_plan:R.string.journey_edit);
        Button review=findViewById(R.id.journey_review);
        review.setVisibility(today==null?View.GONE:View.VISIBLE);
        review.setText(reviewed?R.string.journey_review_edit:R.string.journey_review);
        String feedback=today==null?"계획은 작게, 시작은 가볍게.":
                today.outcome.equals("done")?"직접 해낸 한 걸음이 남았어요. 다음에는 무엇을 이어갈까요?":
                today.outcome.equals("partial")?"조금 한 것도 시도예요. 다음 행동은 더 작게 정해도 좋아요.":
                today.outcome.equals("rest")?"쉬었다는 기록도 남겼어요. 준비될 때 다시 시작해요.":
                "실제로 시도한 뒤 돌아봐 주세요. 큰 목표의 완료 여부는 따로 정해요.";
        ((TextView)findViewById(R.id.journey_feedback)).setText(feedback);
        JourneyStore.Summary summary=store.summary(friend,day);
        ((TextView)findViewById(R.id.journey_week)).setText(getString(R.string.journey_week_summary,
                summary.activeDays7,summary.done7,summary.partial7,summary.rest7));
        LinearLayout days=findViewById(R.id.journey_days);days.removeAllViews();
        for(int offset=6;offset>=0;offset--){
            String date=LocalDate.parse(day).minusDays(offset).toString();
            JourneyStore.Entry entry=store.entry(friend,date);
            View cell=getLayoutInflater().inflate(R.layout.row_journey_day,days,false);
            cell.setTag(date);
            ((TextView)cell.findViewById(R.id.journey_day_date)).setText(
                    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M월 d일 (E)",Locale.KOREAN)));
            TextView outcome=cell.findViewById(R.id.journey_day_state);
            outcome.setText(entry==null?getString(R.string.journey_day_none):status(entry.outcome));
            outcome.setTag(entry==null?"none":entry.outcome);
            cell.setContentDescription(formatDay(date)+" · "+
                    (entry==null?getString(R.string.journey_day_unrecorded):status(entry.outcome)));
            days.addView(cell);
        }
        int next=1;
        for(int milestone:new int[]{1,3,7,14,30})if(milestone>summary.totalActiveDays){next=milestone;break;}
        ((TextView)findViewById(R.id.journey_milestone)).setText(summary.totalActiveDays>=30?
                getString(R.string.journey_milestone_complete,summary.totalActiveDays):
                getString(R.string.journey_milestone,summary.totalActiveDays,next,next-summary.totalActiveDays));
        LinearLayout history=findViewById(R.id.journey_history);history.removeAllViews();
        List<JourneyStore.Entry> entries=store.recent(friend,day,historyLimit);
        if(entries.isEmpty()){
            TextView empty=new TextView(this);empty.setTextAppearance(R.style.BodyText);
            empty.setText(R.string.journey_history_empty);history.addView(empty);
        }
        for(JourneyStore.Entry entry:entries){
            View row=getLayoutInflater().inflate(R.layout.row_journey,history,false);
            ((TextView)row.findViewById(R.id.journey_entry_heading)).setText(
                    getString(R.string.journey_entry_heading,formatDay(entry.day),status(entry.outcome)));
            ((TextView)row.findViewById(R.id.journey_entry_body)).setText(details(entry));
            row.findViewById(R.id.journey_entry_delete).setOnClickListener(v->confirmDelete(entry));
            history.addView(row);
        }
        if(entries.size()==historyLimit&&historyLimit<10000){
            Button more=new Button(this,null,0,R.style.SecondaryButton);
            more.setText("이전 발자국 더 보기");
            more.setOnClickListener(v->{historyLimit=Math.min(10000,historyLimit+7);render();});
            history.addView(more);
        }
    }

    private void confirmDelete(JourneyStore.Entry entry){
        prompt=new AlertDialog.Builder(this).setTitle("이 기록을 삭제할까요?")
                .setMessage(formatDay(entry.day)+"의 계획과 회고가 삭제되고 집계에서도 빠져요.")
                .setNegativeButton("유지",null).setPositiveButton("삭제",null).create();
        prompt.show();
        prompt.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{requireSession();store.delete(friend,entry.day);prompt.dismiss();render();}
            catch(IllegalArgumentException error){prompt.setMessage(error.getMessage());}
            catch(SQLiteException error){prompt.setMessage("삭제하지 못했어요. 저장 공간을 확인한 뒤 다시 시도해 주세요.");}
        });
    }

    private void editPlan(Bundle saved){
        editorKind="plan";editorDay=saved==null?day:saved.getString("editor_day",day);
        JourneyStore.Entry current=store.entry(friend,editorDay);
        fields=getLayoutInflater().inflate(R.layout.dialog_journey_plan,new android.widget.FrameLayout(this),false);
        EditText action=fields.findViewById(R.id.journey_action_input);
        EditText cue=fields.findViewById(R.id.journey_cue_input);
        action.setText(saved!=null?saved.getString("action",""):current==null?adventureAction:current.action);
        cue.setText(saved!=null?saved.getString("cue",""):current==null?"":current.cue);
        fields.findViewById(R.id.journey_suggest).setOnClickListener(v->{
            String[] examples=getResources().getStringArray(R.array.journey_examples);
            suggestions=new AlertDialog.Builder(this).setTitle(R.string.journey_suggest)
                    .setItems(examples,(dialog,index)->{
                        action.setText(examples[index]);action.setSelection(action.length());
                        fields.findViewById(R.id.journey_dialog_error).setVisibility(View.GONE);
                    }).setNegativeButton(android.R.string.cancel,null).show();
        });
        goalTitles=new ArrayList<>();goalTitles.add("");
        for(GoalStore.Goal goal:new GoalStore(db).list(friend))if(!goal.completed)goalTitles.add(goal.title);
        String goal=saved!=null?saved.getString("goal",""):current==null?"":current.goalTitle;
        if(!goalTitles.contains(goal))goalTitles.add(goal);
        List<String> labels=new ArrayList<>(goalTitles);labels.set(0,"목표 연결 없이 시작");
        Spinner goals=fields.findViewById(R.id.journey_goal_input);
        goals.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        goals.setSelection(goalTitles.indexOf(goal));
        List<JourneyStore.Entry> previous=store.recent(friend,LocalDate.parse(editorDay).minusDays(1).toString(),1);
        if(!previous.isEmpty()){
            fields.findViewById(R.id.journey_repeat).setVisibility(View.VISIBLE);
            fields.findViewById(R.id.journey_repeat).setOnClickListener(v->{
                JourneyStore.Entry last=previous.get(0);action.setText(last.action);cue.setText(last.cue);
                // 목표가 삭제·완료됐으면 과거 연결을 자동으로 되살리지 않는다.
                goals.setSelection(Math.max(0,goalTitles.indexOf(last.goalTitle)));
            });
        }
        showEditor(R.string.journey_today,R.string.journey_save,saved,()->{
            store.savePlan(friend,editorDay,action.getText().toString(),cue.getText().toString(),
                    goalTitles.get(goals.getSelectedItemPosition()),System.currentTimeMillis());
        });
    }

    private void editReview(Bundle saved){
        editorKind="review";editorDay=saved==null?day:saved.getString("editor_day",day);
        JourneyStore.Entry current=store.entry(friend,editorDay);
        if(current==null){editorKind="";render();return;}
        fields=getLayoutInflater().inflate(R.layout.dialog_journey_review,new android.widget.FrameLayout(this),false);
        RadioGroup outcomes=fields.findViewById(R.id.journey_outcome);
        String selected=saved==null?current.outcome:saved.getString("outcome","");
        outcomes.check(selected.equals("done")?R.id.journey_done:selected.equals("partial")?
                R.id.journey_partial:selected.equals("rest")?R.id.journey_rest:View.NO_ID);
        EditText note=fields.findViewById(R.id.journey_note_input);
        note.setText(saved==null?current.note:saved.getString("note",""));
        showEditor(R.string.journey_review_question,R.string.journey_record,saved,()->{
            String outcome=selectedOutcome();
            if(outcome.isEmpty())throw new IllegalArgumentException("상태를 선택해 주세요.");
            store.review(friend,editorDay,outcome,note.getText().toString(),System.currentTimeMillis());
        });
        outcomes.setOnCheckedChangeListener((group,id)->
                fields.findViewById(R.id.journey_dialog_error).setVisibility(View.GONE));
    }

    private String selectedOutcome(){
        int id=((RadioGroup)fields.findViewById(R.id.journey_outcome)).getCheckedRadioButtonId();
        return id==R.id.journey_done?"done":id==R.id.journey_partial?"partial":id==R.id.journey_rest?"rest":"";
    }

    private String draft(){
        if(editorKind.equals("plan"))return ((EditText)fields.findViewById(R.id.journey_action_input)).getText()
                +"\n"+((EditText)fields.findViewById(R.id.journey_cue_input)).getText()
                +"\n"+goalTitles.get(((Spinner)fields.findViewById(R.id.journey_goal_input)).getSelectedItemPosition());
        return selectedOutcome()+"\n"+((EditText)fields.findViewById(R.id.journey_note_input)).getText();
    }

    private void showEditor(int title,int save,Bundle saved,Runnable persist){
        originalDraft=saved==null?draft():saved.getString("original",draft());
        editor=new AlertDialog.Builder(this).setTitle(title).setView(fields)
                .setNegativeButton(R.string.journey_cancel,null).setPositiveButton(save,null).create();
        editor.setCanceledOnTouchOutside(false);
        editor.setOnCancelListener(d->cancelDraft());
        editor.show();
        editor.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->cancelDraft());
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            TextView error=fields.findViewById(R.id.journey_dialog_error);
            try{
                requireSession();
                if(!LocalDate.now().toString().equals(editorDay))
                    throw new IllegalArgumentException("날짜가 바뀌었어요. 내용을 확인하고 닫은 뒤 오늘의 계획을 다시 열어 주세요.");
                persist.run();editorKind="";editor.dismiss();render();
            }catch(IllegalArgumentException failure){error.setText(failure.getMessage());error.setVisibility(View.VISIBLE);}
            catch(SQLiteException failure){error.setText("저장하지 못했어요. 내용은 유지돼요. 저장 공간을 확인하고 다시 시도해 주세요.");error.setVisibility(View.VISIBLE);}
        });
    }

    private void cancelDraft(){
        if(draft().equals(originalDraft)){editorKind="";editor.dismiss();render();return;}
        discarding=true;
        if(editor.isShowing())editor.dismiss();
        prompt=new AlertDialog.Builder(this).setTitle("작성 내용 버리기")
                .setMessage("아직 저장하지 않은 내용이 있어요.")
                .setNegativeButton("계속 작성",(d,w)->{discarding=false;editor.show();})
                .setPositiveButton("버리기",(d,w)->{discarding=false;editorKind="";render();})
                .setOnCancelListener(d->{discarding=false;editor.show();}).show();
        prompt.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("opened_database",openedDatabase);state.putLong("opened_generation",openedGeneration);
        state.putBoolean("opened_entered",openedEntered);
        state.putInt("friend",friend);
        state.putInt("history_limit",historyLimit);
        if(!editorKind.isEmpty()&&editor!=null&&(editor.isShowing()||discarding)){
            state.putString("editor",editorKind);state.putString("editor_day",editorDay);
            state.putString("original",originalDraft);
            if(editorKind.equals("plan")){
                state.putString("action",((EditText)fields.findViewById(R.id.journey_action_input)).getText().toString());
                state.putString("cue",((EditText)fields.findViewById(R.id.journey_cue_input)).getText().toString());
                state.putString("goal",goalTitles.get(((Spinner)fields.findViewById(R.id.journey_goal_input)).getSelectedItemPosition()));
            }else{
                state.putString("outcome",selectedOutcome());
                state.putString("note",((EditText)fields.findViewById(R.id.journey_note_input)).getText().toString());
            }
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy(){
        destroyed=true;
        if(editor!=null)editor.dismiss();if(prompt!=null)prompt.dismiss();
        if(suggestions!=null)suggestions.dismiss();
        if(helper!=null)helper.close();super.onDestroy();
    }

    private void requireSession(){
        if(destroyed||!openedEntered||!AccountSession.isEntered(this)
                ||openedGeneration!=AccountSession.generation()
                ||!openedDatabase.equals(AccountSession.databaseName(this)))
            throw new IllegalArgumentException(getString(R.string.adventure_error_session));
    }
}
