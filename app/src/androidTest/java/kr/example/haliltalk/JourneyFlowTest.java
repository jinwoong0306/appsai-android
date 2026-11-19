package kr.example.haliltalk;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalStore;
import kr.example.haliltalk.data.JourneyStore;

/** [U6 | 검증] 실제 저장·회전·오류·재시도. 합성 친구만 생성하고 삭제한다. */
public final class JourneyFlowTest extends GuestActivityTestCase {
    private FriendsDatabase helper;
    private SQLiteDatabase db;
    private JourneyActivity activity;
    private int friend;
    private String day;
    private JourneyStore store;

    protected void awaitWindowFocus(android.view.Window window) throws Exception {
        java.util.concurrent.CountDownLatch focused=new java.util.concurrent.CountDownLatch(1);
        android.view.View decor=window.getDecorView();
        android.view.ViewTreeObserver.OnWindowFocusChangeListener listener=hasFocus->{
            if(hasFocus)focused.countDown();
        };
        getInstrumentation().runOnMainSync(()->{
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(listener);
            if(decor.hasWindowFocus())focused.countDown();
        });
        try {
            assertTrue("Window must receive focus",focused.await(5,java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            getInstrumentation().runOnMainSync(()->decor.getViewTreeObserver().removeOnWindowFocusChangeListener(listener));
        }
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        helper=new FriendsDatabase(getInstrumentation().getTargetContext());db=helper.getWritableDatabase();
        ContentValues values=new ContentValues();values.put("package","qa.journey."+System.nanoTime());
        values.put("name","나의 독서 친구");values.put("personality","차분한 친구");values.put("xp",19);
        friend=(int)db.insertOrThrow("friends",null,values);
        store=new JourneyStore(db);day=LocalDate.now().toString();
    }
    @Override protected void tearDown() throws Exception {
        try{
            if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());
            getInstrumentation().waitForIdleSync();
            db.execSQL("DROP TRIGGER IF EXISTS qa_journey_reject");
            db.delete("friends","id=?",new String[]{""+friend});helper.close();
        }finally{super.tearDown();}
    }
    private void launch(){
        activity=(JourneyActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),JourneyActivity.class)
                .putExtra("friend_id",friend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }
    private void click(int id) throws Throwable {
        runTestOnUiThread(()->activity.findViewById(id).performClick());
        getInstrumentation().waitForIdleSync();
    }
    private void save() throws Throwable {
        runTestOnUiThread(()->activity.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
    }
    private void plan(String text) throws Throwable {
        click(R.id.journey_plan);
        runTestOnUiThread(()->((EditText)activity.editor.findViewById(R.id.journey_action_input)).setText(text));
        save();
    }
    private void capture(String name,boolean end) throws Exception {
        getInstrumentation().waitForIdleSync();
        awaitWindowFocus(activity.editor!=null&&activity.editor.isShowing()?activity.editor.getWindow():activity.getWindow());
        CountDownLatch frame=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{
            if(end){
                android.widget.ScrollView scroll=activity.findViewById(R.id.journey_scroll);
                scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
            }
            View decor=activity.editor!=null&&activity.editor.isShowing()?
                    activity.editor.getWindow().getDecorView():activity.getWindow().getDecorView();
            decor.getViewTreeObserver().registerFrameCommitCallback(()->decor.post(
                    ()->android.view.Choreographer.getInstance().postFrameCallback(t->frame.countDown())));
            decor.invalidate();
        });
        assertTrue("UI frame committed",frame.await(5,TimeUnit.SECONDS));
        Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File dir=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"journey-qa");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){
            assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));
        }finally{image.recycle();}
    }
    private void recreate() throws Throwable {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(JourneyActivity.class.getName(),null,false);
        try{
            runTestOnUiThread(()->activity.recreate());
            activity=(JourneyActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(activity);getInstrumentation().waitForIdleSync();
        }finally{getInstrumentation().removeMonitor(monitor);}
    }
    public void testPlanReviewHistoryAndRecreation() throws Throwable {
        long goal=new GoalStore(db).save(friend,0,"이번 달 읽고 싶은 책 끝까지 읽기",null);
        String yesterday=LocalDate.parse(day).minusDays(1).toString();
        store.savePlan(friend,yesterday,"책 한 쪽 읽기","저녁 식사 뒤","",1);
        store.review(friend,yesterday,"partial","내일은 책상 위에 책을 먼저 꺼내두기",2);
        launch();capture("01-today-empty",false);
        click(R.id.journey_plan);
        runTestOnUiThread(()->{
            activity.editor.findViewById(R.id.journey_repeat).performClick();
            assertEquals("책 한 쪽 읽기",((EditText)activity.editor.findViewById(R.id.journey_action_input)).getText().toString());
            ((Spinner)activity.editor.findViewById(R.id.journey_goal_input)).setSelection(1);
        });
        capture("02-plan-draft",false);
        recreate();
        assertEquals("책 한 쪽 읽기",((EditText)activity.editor.findViewById(R.id.journey_action_input)).getText().toString());
        save();
        assertEquals("이번 달 읽고 싶은 책 끝까지 읽기",store.entry(friend,day).goalTitle);
        capture("03-planned",false);
        click(R.id.journey_review);save();
        assertTrue(activity.editor.isShowing());
        assertEquals("planned",store.entry(friend,day).outcome);
        runTestOnUiThread(()->{
            ((RadioGroup)activity.editor.findViewById(R.id.journey_outcome)).check(R.id.journey_done);
            ((EditText)activity.editor.findViewById(R.id.journey_note_input)).setText("한 쪽부터 시작하니 부담이 줄었어요");
            assertEquals(View.GONE,activity.editor.findViewById(R.id.journey_dialog_error).getVisibility());
        });
        capture("04-review",false);
        recreate();save();
        assertEquals("done",store.entry(friend,day).outcome);
        assertEquals(2,store.summary(friend,day).activeDays7);
        capture("05-reviewed",false);
        runTestOnUiThread(()->{
            android.widget.ScrollView scroll=activity.findViewById(R.id.journey_scroll);
            scroll.scrollTo(0,activity.findViewById(R.id.journey_content).getTop()
                    +activity.findViewById(R.id.journey_week).getTop());
        });
        capture("09-week",false);
        capture("06-history",true);
        runTestOnUiThread(()->activity.finish());getInstrumentation().waitForIdleSync();launch();
        assertEquals("done",store.entry(friend,day).outcome);
        click(R.id.journey_review);save();
        assertEquals(2,store.recent(friend,day,10).size());
        assertFalse(new GoalStore(db).list(friend).get(0).completed);
        try(Cursor c=db.rawQuery("SELECT xp FROM friends WHERE id=?",new String[]{""+friend})){
            assertTrue(c.moveToFirst());assertEquals(19,c.getInt(0));
        }
        runTestOnUiThread(()->((LinearLayout)activity.findViewById(R.id.journey_history)).getChildAt(0)
                .findViewById(R.id.journey_entry_delete).performClick());
        runTestOnUiThread(()->activity.prompt.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        assertNotNull(store.entry(friend,day));
        runTestOnUiThread(()->((LinearLayout)activity.findViewById(R.id.journey_history)).getChildAt(0)
                .findViewById(R.id.journey_entry_delete).performClick());
        runTestOnUiThread(()->activity.prompt.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        assertNull(store.entry(friend,day));assertEquals(1,store.summary(friend,day).activeDays7);
    }
    public void testStorageFailureKeepsDraftAndRetryWorks() throws Throwable {
        launch();click(R.id.journey_plan);
        runTestOnUiThread(()->((EditText)activity.editor.findViewById(R.id.journey_action_input)).setText("스트레칭 한 번"));
        db.execSQL("CREATE TRIGGER qa_journey_reject BEFORE INSERT ON journey_entries WHEN NEW.friend="+friend+
                " BEGIN SELECT RAISE(ABORT,'qa write rejected'); END");
        try{
            save();assertNull(store.entry(friend,day));assertTrue(activity.editor.isShowing());
            assertEquals("스트레칭 한 번",((EditText)activity.editor.findViewById(R.id.journey_action_input)).getText().toString());
            capture("07-save-error",false);
        }finally{db.execSQL("DROP TRIGGER qa_journey_reject");}
        save();assertNotNull(store.entry(friend,day));
        click(R.id.journey_review);
        runTestOnUiThread(()->((RadioGroup)activity.editor.findViewById(R.id.journey_outcome)).check(R.id.journey_rest));
        save();assertEquals(0,store.summary(friend,day).activeDays7);
        capture("08-rest",false);
    }
    public void testDiscardAndStaleDayCannotWrite() throws Throwable {
        launch();click(R.id.journey_plan);
        runTestOnUiThread(()->((EditText)activity.editor.findViewById(R.id.journey_action_input)).setText("보존할 초안"));
        recreate();
        runTestOnUiThread(()->activity.editor.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        assertTrue(activity.prompt.isShowing());assertNull(store.entry(friend,day));
        runTestOnUiThread(()->activity.prompt.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        assertEquals("보존할 초안",((EditText)activity.editor.findViewById(R.id.journey_action_input)).getText().toString());
        // 기기 전체 시각은 바꾸지 않고 자정 이전에 열린 편집기의 날짜만 재현한다.
        java.lang.reflect.Field editorDay=JourneyActivity.class.getDeclaredField("editorDay");
        editorDay.setAccessible(true);editorDay.set(activity,LocalDate.parse(day).minusDays(1).toString());
        save();assertTrue(activity.editor.isShowing());assertNull(store.entry(friend,day));
        assertNull(store.entry(friend,LocalDate.parse(day).minusDays(1).toString()));
    }
    public void testGoalEntryKeepsSelectedFriendAndReturns() throws Throwable {
        GoalsActivity goals=(GoalsActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),GoalsActivity.class)
                .putExtra("friend_id",friend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(JourneyActivity.class.getName(),null,false);
        try{
            runTestOnUiThread(()->goals.findViewById(R.id.goals_journey).performClick());
            activity=(JourneyActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(activity);getInstrumentation().waitForIdleSync();
            plan("친구에게 연결한 작은 행동");
            assertEquals("친구에게 연결한 작은 행동",store.entry(friend,day).action);
            click(R.id.journey_back);
            assertTrue(activity.isFinishing());
        }finally{
            getInstrumentation().removeMonitor(monitor);
            runTestOnUiThread(goals::finish);
        }
    }

    public void testExamplesAreDraftsAndWeekRowsUseRecordedOutcomes() throws Throwable {
        String yesterday=LocalDate.parse(day).minusDays(1).toString();
        store.savePlan(friend,yesterday,"Previous plan","","",1);
        store.review(friend,yesterday,"rest","",2);
        launch();
        LinearLayout days=activity.findViewById(R.id.journey_days);
        assertEquals(7,days.getChildCount());
        assertEquals("rest",days.findViewWithTag(yesterday).findViewById(R.id.journey_day_state).getTag());
        assertEquals("none",days.findViewWithTag(day).findViewById(R.id.journey_day_state).getTag());
        click(R.id.journey_plan);
        runTestOnUiThread(()->activity.editor.findViewById(R.id.journey_suggest).performClick());
        runTestOnUiThread(()->activity.suggestions.getListView().performItemClick(
                activity.suggestions.getListView().getChildAt(0),0,0));
        assertNull(store.entry(friend,day));
        String expected=activity.getResources().getStringArray(R.array.journey_examples)[0];
        assertEquals(expected,((EditText)activity.editor.findViewById(R.id.journey_action_input))
                .getText().toString());
        recreate();save();
        assertEquals(expected,store.entry(friend,day).action);
        assertEquals("planned",store.entry(friend,day).outcome);
        days=activity.findViewById(R.id.journey_days);
        assertEquals("planned",days.findViewWithTag(day).findViewById(R.id.journey_day_state).getTag());
    }

    public void testHomeContinuesTheFriendWithTodaysUnreviewedPlan() throws Throwable {
        android.content.Context context=getInstrumentation().getTargetContext();
        android.content.SharedPreferences welcome=context.getSharedPreferences("MainActivity",0);
        boolean hadWelcome=welcome.contains("welcome_done");
        boolean welcomed=welcome.getBoolean("welcome_done",false);
        assertTrue(welcome.edit().putBoolean("welcome_done",true).commit());
        ContentValues values=new ContentValues();
        values.put("package","qa.journey.next."+System.nanoTime());
        values.put("name","이어갈 친구");
        values.put("personality","calm");
        int next=(int)db.insertOrThrow("friends",null,values);
        MainActivity main=null;
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                JourneyActivity.class.getName(),null,false);
        try{
            store.savePlan(friend,day,"먼저 완료한 계획",null,null,1);
            store.review(friend,day,"done","",2);
            store.savePlan(next,day,"아직 시도할 행동",null,null,1);
            main=(MainActivity)getInstrumentation().startActivitySync(
                    new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getInstrumentation().waitForIdleSync();
            assertTrue(((android.widget.TextView)main.findViewById(R.id.home_journey_next))
                    .getText().toString().contains("이어갈 친구"));
            MainActivity screen=main;
            runTestOnUiThread(()->screen.findViewById(R.id.home_journey).performClick());
            activity=(JourneyActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(activity);
            assertEquals(next,activity.getIntent().getIntExtra("friend_id",0));
            assertEquals(next,((Integer)main.findViewById(R.id.home_journey).getTag()).intValue());
        }finally{
            getInstrumentation().removeMonitor(monitor);
            MainActivity screen=main;
            if(screen!=null)getInstrumentation().runOnMainSync(screen::finish);
            db.delete("friends","id=?",new String[]{""+next});
            android.content.SharedPreferences.Editor edit=welcome.edit();
            if(hadWelcome)edit.putBoolean("welcome_done",welcomed);
            else edit.remove("welcome_done");
            assertTrue(edit.commit());
        }
    }

    public void testStaleSessionKeepsUncommittedPlan() throws Throwable {
        launch();click(R.id.journey_plan);
        java.lang.reflect.Field generation=JourneyActivity.class.getDeclaredField("openedGeneration");
        generation.setAccessible(true);
        runTestOnUiThread(()->{
            ((EditText)activity.editor.findViewById(R.id.journey_action_input)).setText("Uncommitted");
            try{generation.setLong(activity,kr.example.haliltalk.auth.AccountSession.generation()-1);}
            catch(IllegalAccessException failure){throw new AssertionError(failure);}
        });
        save();
        assertTrue(activity.editor.isShowing());
        assertNull(store.entry(friend,day));
        assertEquals("Uncommitted",((EditText)activity.editor.findViewById(R.id.journey_action_input))
                .getText().toString());
    }
}
