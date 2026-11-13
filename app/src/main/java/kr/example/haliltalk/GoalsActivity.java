package kr.example.haliltalk;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Calendar;
import java.util.List;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalStore;

/** 친구별 목표 화면. 저장·완료·삭제는 명시적인 사용자 동작으로만 실행한다. */
public final class GoalsActivity extends FeatureActivity {
    private FriendsDatabase helper;
    private SQLiteDatabase db;
    private GoalStore store;
    private int friend;
    AlertDialog editor;
    AlertDialog prompt;
    android.app.Dialog picker;
    private Long draftDue;
    private long draftId;
    private String originalTitle;
    private Long originalDue;
    private boolean confirmingDiscard;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        helper=new FriendsDatabase(this); db=helper.getWritableDatabase(); store=new GoalStore(db);
        Object friendExtra=getIntent().getExtras()==null?null:getIntent().getExtras().get("friend_id");
        long friendValue=friendExtra instanceof Number?((Number)friendExtra).longValue():0;
        if(friendValue<=0||friendValue>Integer.MAX_VALUE) { finish();return; }
        friend=(int)friendValue;
        String name;
        try(Cursor c=db.rawQuery("SELECT name FROM friends WHERE id=?",new String[]{""+friend})) {
            if(!c.moveToFirst()) { finish(); return; }
            name=c.getString(0);
        }
        setContentView(R.layout.screen_goals);
        ((TextView)findViewById(R.id.goals_title)).setText(getString(R.string.goals_friend_title,name));
        findViewById(R.id.goals_back).setOnClickListener(v->finish());
        findViewById(R.id.add_goal).setOnClickListener(v->edit(0,"",null));
        render();
        if(state!=null&&state.getBoolean("editing")) {
            edit(state.getLong("draft_id"),state.getString("draft_title",""),
                    state.containsKey("draft_due")?state.getLong("draft_due"):null);
            originalTitle=state.getString("original_title","");
            originalDue=state.containsKey("original_due")?state.getLong("original_due"):null;
        }
    }
    private String dueText(Long value) {
        return value==null?"기한 없음":java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(value));
    }
    private void edit(long id,String title,Long due) {
        draftId=id; originalTitle=title; draftDue=due; originalDue=due;
        View fields=getLayoutInflater().inflate(R.layout.dialog_goal,null);
        EditText input=fields.findViewById(R.id.goal_input); input.setText(title);
        TextView dueLabel=fields.findViewById(R.id.goal_due_value); dueLabel.setText(dueText(draftDue));
        fields.findViewById(R.id.goal_clear_due).setOnClickListener(v->{draftDue=null;dueLabel.setText(dueText(null));});
        fields.findViewById(R.id.goal_pick_due).setOnClickListener(v->{
            Calendar time=Calendar.getInstance();
            if(draftDue!=null) time.setTimeInMillis(draftDue);
            picker=new DatePickerDialog(this,(date,y,m,day)->{
                time.set(y,m,day);
                picker=new TimePickerDialog(this,(clock,h,min)->{
                    time.set(Calendar.HOUR_OF_DAY,h); time.set(Calendar.MINUTE,min);
                    time.set(Calendar.SECOND,0); time.set(Calendar.MILLISECOND,0);
                    draftDue=time.getTimeInMillis(); dueLabel.setText(dueText(draftDue));
                },time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE),true);
                picker.show();
            },time.get(Calendar.YEAR),time.get(Calendar.MONTH),time.get(Calendar.DAY_OF_MONTH));
            picker.show();
        });
        editor=new AlertDialog.Builder(this).setTitle(id==0?"목표 추가":"목표 수정").setView(fields)
                .setNegativeButton("취소",(dialog,which)->editor.cancel()).setPositiveButton("저장",null).create();
        editor.setCanceledOnTouchOutside(false);
        // 뒤로 가기·취소는 미저장 초안을 먼저 확인한다. 회전해도 최초 값과 비교한다.
        editor.setOnCancelListener(d->{
            if(input.getText().toString().equals(originalTitle)
                    &&java.util.Objects.equals(draftDue,originalDue)) return;
            confirmingDiscard=true;
            prompt=new AlertDialog.Builder(this).setTitle("수정 내용 버리기")
                    .setMessage("저장하지 않은 내용이 있어요.")
                    .setNegativeButton("계속 편집",(dialog,which)->{
                        confirmingDiscard=false;editor.show();
                    })
                    .setPositiveButton("버리기",(dialog,which)->confirmingDiscard=false)
                    .setOnCancelListener(dialog->{confirmingDiscard=false;editor.show();}).show();
        });
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                store.save(friend,id,input.getText().toString(),draftDue);
                render(); editor.dismiss();
            } catch(IllegalArgumentException error) { input.setError(error.getMessage()); }
            catch(android.database.sqlite.SQLiteException error) { input.setError("저장하지 못했어요. 저장 공간을 확인한 뒤 다시 시도해 주세요."); }
        });
    }
    private void render() {
        LinearLayout list=findViewById(R.id.goals_list); list.removeAllViews();
        List<GoalStore.Goal> goals=store.list(friend);
        if(goals.isEmpty()) {
            TextView empty=new TextView(this);empty.setText("목표가 없어요. 목표 추가를 눌러 시작해 보세요.");
            empty.setTextAppearance(R.style.BodyText);list.addView(empty);
        }
        for(GoalStore.Goal goal:goals) {
            View row=getLayoutInflater().inflate(R.layout.row_goal,list,false);
            ((TextView)row.findViewById(R.id.goal_title)).setText(goal.title);
            ((TextView)row.findViewById(R.id.goal_due)).setText(
                    getString(R.string.goals_status_due,goal.completed?"완료":"진행 중",dueText(goal.dueAt)));
            Button complete=row.findViewById(R.id.goal_complete); complete.setText(goal.completed?"다시 진행":"완료 처리");
            complete.setOnClickListener(v->prompt=new AlertDialog.Builder(this)
                    .setTitle(goal.completed?"다시 진행할까요?":"목표를 완료했나요?")
                    .setMessage(goal.title).setNegativeButton("취소",null)
                    .setPositiveButton(goal.completed?"다시 진행":"완료",(d,w)->{
                        mutate(()->store.complete(friend,goal.id,!goal.completed));
                    }).show());
            row.findViewById(R.id.goal_edit).setOnClickListener(v->edit(goal.id,goal.title,goal.dueAt));
            row.findViewById(R.id.goal_delete).setOnClickListener(v->prompt=new AlertDialog.Builder(this)
                    .setTitle("목표를 삭제할까요?").setMessage(goal.title)
                    .setNegativeButton("취소",null).setPositiveButton("삭제",(d,w)->{
                        mutate(()->store.delete(friend,goal.id));
                    }).show());
            list.addView(row);
        }
    }
    private void mutate(Runnable action) {
        TextView error=findViewById(R.id.goals_error);
        try {
            action.run();error.setVisibility(View.GONE);
            render();
        } catch(android.database.sqlite.SQLiteException failure) {
            error.setText("변경하지 못했어요. 저장 공간을 확인한 뒤 다시 시도해 주세요.");
            error.setVisibility(View.VISIBLE);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if(store==null||isFinishing()) return;
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if(editor!=null&&(editor.isShowing()||confirmingDiscard)) {
            state.putBoolean("editing",true);state.putLong("draft_id",draftId);
            state.putString("draft_title",((EditText)editor.findViewById(R.id.goal_input)).getText().toString());
            if(draftDue!=null)state.putLong("draft_due",draftDue);
            state.putString("original_title",originalTitle);
            if(originalDue!=null)state.putLong("original_due",originalDue);
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if(editor!=null)editor.dismiss();
        if(prompt!=null)prompt.dismiss();
        if(picker!=null)picker.dismiss();
        if(helper!=null)helper.close();
        super.onDestroy();
    }
}
