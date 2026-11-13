package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalStore;

/** 실제 데이터를 지우지 않고 이 테스트가 만든 친구만 정리한다. */
public final class GoalsFlowTest extends InstrumentationTestCase {
    private FriendsDatabase helper;
    private SQLiteDatabase db;
    private GoalsActivity activity;
    private int friend;
    @Override protected void setUp() throws Exception {
        super.setUp();
        helper=new FriendsDatabase(getInstrumentation().getTargetContext()); db=helper.getWritableDatabase();
        ContentValues values=new ContentValues();values.put("package","qa.goals.flow."+System.nanoTime());
        values.put("name","독서 친구");values.put("personality","차분한 친구");
        friend=(int)db.insertOrThrow("friends",null,values);
    }
    @Override protected void tearDown() throws Exception {
        try {
            if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());
            getInstrumentation().waitForIdleSync();
            db.delete("friends","id=?",new String[]{""+friend});
            helper.close();
        } finally {super.tearDown();}
    }
    private void launch() {
        activity=(GoalsActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),GoalsActivity.class)
                        .putExtra("friend_id",friend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }
    private void capture(String name) throws Exception {
        capture(name,activity);
    }
    private void capture(String name,android.app.Activity screen) throws Exception {
        getInstrumentation().waitForIdleSync();
        CountDownLatch frame=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{
            android.app.Dialog front=screen!=activity?null
                    :activity.picker!=null&&activity.picker.isShowing()?activity.picker
                    :activity.prompt!=null&&activity.prompt.isShowing()?activity.prompt
                    :activity.editor!=null&&activity.editor.isShowing()?activity.editor:null;
            View decor=front==null?screen.getWindow().getDecorView():front.getWindow().getDecorView();
            decor.getViewTreeObserver().registerFrameCommitCallback(()->decor.post(
                    ()->android.view.Choreographer.getInstance().postFrameCallback(t->frame.countDown())));
            decor.invalidate();
        });
        assertTrue("Committed UI frame",frame.await(5,TimeUnit.SECONDS));
        Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File dir=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"goals-qa");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(dir,name+".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,output));
        } finally {screenshot.recycle();}
    }
    private void clickDialogText(String text) {
        android.view.accessibility.AccessibilityNodeInfo root=
                getInstrumentation().getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);
        for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)) {
            if(text.contentEquals(node.getText())&&node.isClickable()) {
                assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
                getInstrumentation().waitForIdleSync();return;
            }
        }
        fail("Dialog action not found: "+text);
    }
    private void captureEnd(String name) throws Exception {
        android.widget.ScrollView scroll=(android.widget.ScrollView)
                activity.findViewById(R.id.goals_list).getParent().getParent();
        getInstrumentation().runOnMainSync(()->scroll.scrollTo(0,scroll.getChildAt(0).getHeight()));
        capture(name);
        getInstrumentation().runOnMainSync(()->scroll.scrollTo(0,0));
        getInstrumentation().waitForIdleSync();
    }
    public void testChatEntryOpensSelectedFriendsGoals() throws Throwable {
        android.content.Context context=getInstrumentation().getTargetContext();
        String localName=MainActivity.class.getName();
        String prefix=context.getPackageName()+".";
        if(localName.startsWith(prefix))localName=localName.substring(prefix.length());
        android.content.SharedPreferences welcome=context.getSharedPreferences(localName,0);
        boolean hadWelcome=welcome.contains("welcome_done");
        boolean wasWelcomed=welcome.getBoolean("welcome_done",false);
        assertTrue(welcome.edit().putBoolean("welcome_done",true).commit());
        MainActivity main=(MainActivity)getInstrumentation().startActivitySync(
                new Intent(context,MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                GoalsActivity.class.getName(),null,false);
        try {
            runTestOnUiThread(()->main.chat(friend,"독서 친구"));
            getInstrumentation().waitForIdleSync();
            capture("chat",main);
            runTestOnUiThread(()->assertTrue(main.findViewById(R.id.open_goals).performClick()));
            activity=(GoalsActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("Chat goal navigation",activity);
            getInstrumentation().waitForIdleSync();
            assertEquals(friend,activity.getIntent().getIntExtra("friend_id",0));
            capture("chat-entry");
        } finally {
            getInstrumentation().removeMonitor(monitor);
            runTestOnUiThread(main::finish);
            android.content.SharedPreferences.Editor editor=welcome.edit();
            if(hadWelcome)editor.putBoolean("welcome_done",wasWelcomed);
            else editor.remove("welcome_done");
            assertTrue(editor.commit());
        }
    }
    public void testGoalCreateEditAndExplicitCompletionSurviveActivityReopen() throws Throwable {
        launch();capture("empty");
        runTestOnUiThread(()->{
            activity.findViewById(R.id.add_goal).performClick();
            activity.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(activity.editor.isShowing());
            assertNotNull(((EditText)activity.editor.findViewById(R.id.goal_input)).getError());
        });
        capture("invalid-title");
        runTestOnUiThread(()->{
            ((EditText)activity.editor.findViewById(R.id.goal_input)).setText("책 10쪽 읽기");
            activity.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(1,new GoalStore(db).list(friend).size());
        capture("saved");
        captureEnd("saved-end");
        runTestOnUiThread(()->{
            LinearLayout list=activity.findViewById(R.id.goals_list);
            list.getChildAt(0).findViewById(R.id.goal_edit).performClick();
            ((EditText)activity.editor.findViewById(R.id.goal_input)).setText("책 20쪽 읽기");
        });
        capture("editor");
        runTestOnUiThread(()->activity.editor.findViewById(R.id.goal_pick_due).performClick());
        capture("date-picker");
        runTestOnUiThread(()->{
            android.app.DatePickerDialog date=(android.app.DatePickerDialog)activity.picker;
            date.getDatePicker().updateDate(2026,10,20);
            date.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        getInstrumentation().waitForIdleSync();
        capture("time-picker");
        runTestOnUiThread(()->((android.app.TimePickerDialog)activity.picker)
                .getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->activity.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        assertEquals("책 20쪽 읽기",new GoalStore(db).list(friend).get(0).title);
        assertNotNull(new GoalStore(db).list(friend).get(0).dueAt);
        runTestOnUiThread(()->((LinearLayout)activity.findViewById(R.id.goals_list)).getChildAt(0)
                .findViewById(R.id.goal_complete).performClick());
        capture("complete-confirm");
        assertFalse(new GoalStore(db).list(friend).get(0).completed);
        clickDialogText("완료");
        assertTrue(new GoalStore(db).list(friend).get(0).completed);
        runTestOnUiThread(()->activity.finish());getInstrumentation().waitForIdleSync();
        launch();capture("completed");
        captureEnd("completed-end");
        assertTrue(new GoalStore(db).list(friend).get(0).completed);
        runTestOnUiThread(()->((LinearLayout)activity.findViewById(R.id.goals_list)).getChildAt(0)
                .findViewById(R.id.goal_delete).performClick());
        capture("delete-confirm");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        assertEquals(1,new GoalStore(db).list(friend).size());
        runTestOnUiThread(()->((LinearLayout)activity.findViewById(R.id.goals_list)).getChildAt(0)
                .findViewById(R.id.goal_delete).performClick());
        getInstrumentation().waitForIdleSync();
        clickDialogText("삭제");
        assertTrue(new GoalStore(db).list(friend).isEmpty());
        try(Cursor c=db.rawQuery("SELECT xp FROM friends WHERE id=?",new String[]{""+friend})) {
            assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));
        }
    }
    public void testDraftBackRequiresConfirmationAfterRotation() throws Throwable {
        launch();
        runTestOnUiThread(()->{
            activity.findViewById(R.id.add_goal).performClick();
            ((EditText)activity.editor.findViewById(R.id.goal_input)).setText("보관할 초안");
        });
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                GoalsActivity.class.getName(),null,false);
        try {
            runTestOnUiThread(()->activity.recreate());
            GoalsActivity restored=(GoalsActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("Goal editor recreation",restored);activity=restored;
            getInstrumentation().waitForIdleSync();
        } finally {getInstrumentation().removeMonitor(monitor);}
        runTestOnUiThread(()->assertEquals("보관할 초안",
                ((EditText)activity.editor.findViewById(R.id.goal_input)).getText().toString()));
        capture("draft-restored");
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        capture("discard-confirm");
        assertTrue(new GoalStore(db).list(friend).isEmpty());
        clickDialogText("계속 편집");
        runTestOnUiThread(()->{
            assertTrue(activity.editor.isShowing());
            assertEquals("보관할 초안",((EditText)activity.editor.findViewById(R.id.goal_input)).getText().toString());
        });
        getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        getInstrumentation().waitForIdleSync();
        clickDialogText("버리기");
        runTestOnUiThread(()->assertFalse(activity.editor.isShowing()));
        assertTrue(new GoalStore(db).list(friend).isEmpty());
    }
}
