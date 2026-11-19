package kr.example.haliltalk;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.time.LocalDate;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.AdventureStore;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.JourneyStore;

/** Synthetic friend fixtures only. Dialog and activity events replace timed polling. */
public final class AdventureFlowTest extends GuestActivityTestCase {
    private FriendsDatabase helper;
    private SQLiteDatabase db;
    private JourneyStore journeys;
    private AdventureStore adventures;
    private AdventureActivity activity;
    private JourneyActivity journey;
    private int friend,other;
    private String day;

    @Override protected void setUp() throws Exception {
        super.setUp();
        helper=new FriendsDatabase(getInstrumentation().getTargetContext());
        db=helper.getWritableDatabase();
        friend=makeFriend("qa.adventure.first");other=makeFriend("qa.adventure.second");
        journeys=new JourneyStore(db);adventures=new AdventureStore(db);
        day=LocalDate.now().toString();
    }

    private int makeFriend(String packageName){
        ContentValues values=new ContentValues();
        values.put("package",packageName+"."+System.nanoTime());
        values.put("name","Adventure fixture");values.put("personality","calm");values.put("xp",23);
        return (int)db.insertOrThrow("friends",null,values);
    }

    @Override protected void tearDown() throws Exception {
        try{
            getInstrumentation().runOnMainSync(()->{
                if(journey!=null)journey.finish();
                if(activity!=null)activity.finish();
            });
            getInstrumentation().waitForIdleSync();
            if(db!=null){
                db.execSQL("DROP TRIGGER IF EXISTS qa_adventure_reject");
                db.delete("friends","id IN (?,?)",new String[]{""+friend,""+other});
            }
            if(helper!=null)helper.close();
        }finally{super.tearDown();}
    }

    private void launch(){
        activity=(AdventureActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),AdventureActivity.class)
                .putExtra("friend_id",friend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }

    private void click(int id) throws Throwable {
        runTestOnUiThread(()->activity.findViewById(id).performClick());
        getInstrumentation().waitForIdleSync();
    }

    private void confirm() throws Throwable {
        runTestOnUiThread(()->activity.prompt.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
    }

    private void recreate() throws Throwable {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                AdventureActivity.class.getName(),null,false);
        try{
            runTestOnUiThread(activity::recreate);
            activity=(AdventureActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(activity);getInstrumentation().waitForIdleSync();
        }finally{getInstrumentation().removeMonitor(monitor);}
    }

    private void record(int owner,String date,String outcome,String note){
        journeys.savePlan(owner,date,"Read one page","","",1);
        if(!"planned".equals(outcome))journeys.review(owner,date,outcome,note,2);
    }

    public void testConfirmedRouteDraftRotationAndWriteFailureRetry() throws Throwable {
        launch();
        runTestOnUiThread(()->((RadioGroup)activity.findViewById(R.id.adventure_routes))
                .check(R.id.adventure_route_seven));
        click(R.id.adventure_choose);
        assertNull(adventures.choice(friend));
        recreate();assertTrue(activity.prompt.isShowing());
        runTestOnUiThread(()->activity.prompt.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        assertNull(adventures.choice(friend));
        click(R.id.adventure_choose);
        db.execSQL("CREATE TRIGGER qa_adventure_reject BEFORE INSERT ON adventure_choices"
                +" WHEN NEW.friend="+friend+" BEGIN SELECT RAISE(ABORT,'qa'); END");
        try{
            confirm();assertTrue(activity.prompt.isShowing());assertNull(adventures.choice(friend));
            assertEquals(View.VISIBLE,activity.findViewById(R.id.adventure_error).getVisibility());
        }finally{db.execSQL("DROP TRIGGER qa_adventure_reject");}
        confirm();
        assertEquals(7,adventures.choice(friend).days);
        assertEquals(day,adventures.choice(friend).startedDay);
        assertNull(journeys.entry(friend,day));
        runTestOnUiThread(()->((RadioGroup)activity.findViewById(R.id.adventure_routes))
                .check(R.id.adventure_route_fourteen));
        click(R.id.adventure_choose);confirm();
        assertEquals(14,adventures.choice(friend).days);
    }

    public void testProgressQuestPostcardsRecalculateAndFriendsStaySeparate() throws Throwable {
        String start=LocalDate.parse(day).minusDays(5).toString();
        adventures.choose(friend,3,start);
        record(friend,start,"done","Remember");
        record(friend,LocalDate.parse(day).minusDays(3).toString(),"partial","");
        record(friend,day,"rest","Rest today");
        record(other,day,"done","Other friend");
        launch();
        assertEquals(2,((ProgressBar)activity.findViewById(R.id.adventure_progress)).getProgress());
        assertEquals(Boolean.TRUE,activity.findViewById(R.id.adventure_quest_plan).getTag());
        assertEquals(Boolean.TRUE,activity.findViewById(R.id.adventure_quest_review).getTag());
        assertEquals(Boolean.TRUE,activity.findViewById(R.id.adventure_quest_reflection).getTag());
        LinearLayout gallery=activity.findViewById(R.id.adventure_gallery);
        assertTrue(gallery.getChildAt(0).isActivated());
        assertFalse(gallery.getChildAt(1).isActivated());
        assertEquals(View.GONE,gallery.getChildAt(1).findViewById(R.id.adventure_postcard_art).getVisibility());
        final View locked=gallery.getChildAt(1);
        runTestOnUiThread(()->locked.findViewById(R.id.adventure_postcard_open).performClick());
        assertTrue(activity.prompt.isShowing());confirm();
        journeys.review(friend,day,"done","Now attempted",3);
        recreate();
        assertEquals(3,((ProgressBar)activity.findViewById(R.id.adventure_progress)).getProgress());
        gallery=activity.findViewById(R.id.adventure_gallery);
        assertTrue(gallery.getChildAt(1).isActivated());
        final View earned=gallery.getChildAt(1);
        runTestOnUiThread(()->earned.findViewById(R.id.adventure_postcard_open).performClick());
        assertEquals(View.VISIBLE,activity.prompt.findViewById(R.id.adventure_postcard_art).getVisibility());
        confirm();
        journeys.delete(friend,start);recreate();
        assertEquals(2,((ProgressBar)activity.findViewById(R.id.adventure_progress)).getProgress());
        Spinner picker=activity.findViewById(R.id.adventure_friend);
        runTestOnUiThread(()->{
            for(int i=0;i<picker.getCount();i++){
                // Fixture IDs are ordered exactly like the activity's friend query.
                try(Cursor c=db.rawQuery("SELECT id FROM friends ORDER BY id LIMIT 1 OFFSET ?",
                        new String[]{""+i})){
                    assertTrue(c.moveToFirst());
                    if(c.getInt(0)==other){picker.setSelection(i);break;}
                }
            }
        });
        getInstrumentation().waitForIdleSync();
        assertNull(adventures.choice(other));
        assertEquals(0,((ProgressBar)activity.findViewById(R.id.adventure_progress)).getProgress());
        assertEquals(1,journeys.summary(other,day).totalActiveDays);
    }

    public void testSuggestionIsEditableUncommittedAndDoesNotReplaceExistingPlan() throws Throwable {
        launch();click(R.id.adventure_suggestions_toggle);
        runTestOnUiThread(()->((RadioGroup)activity.findViewById(R.id.adventure_suggestions))
                .check(R.id.adventure_suggestion_two));
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                JourneyActivity.class.getName(),null,false);
        try{
            click(R.id.adventure_primary);
            journey=(JourneyActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(journey);getInstrumentation().waitForIdleSync();
            assertNotNull(journey.editor);assertTrue(journey.editor.isShowing());
            assertNull(journeys.entry(friend,day));
            String suggested=activity.getResources().getStringArray(R.array.adventure_actions_first)[1];
            assertEquals(suggested,((EditText)journey.editor.findViewById(R.id.journey_action_input))
                    .getText().toString());
            runTestOnUiThread(()->{
                ((EditText)journey.editor.findViewById(R.id.journey_action_input)).setText("My edited draft");
                journey.editor.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            assertEquals("My edited draft",journeys.entry(friend,day).action);
            assertEquals("planned",journeys.entry(friend,day).outcome);
            runTestOnUiThread(journey::finish);getInstrumentation().waitForIdleSync();journey=null;
            getInstrumentation().removeMonitor(monitor);
            monitor=getInstrumentation().addMonitor(JourneyActivity.class.getName(),null,false);
            click(R.id.adventure_primary);
            journey=(JourneyActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(journey);getInstrumentation().waitForIdleSync();
            assertNull(journey.editor);
            assertEquals("My edited draft",journeys.entry(friend,day).action);
        }finally{getInstrumentation().removeMonitor(monitor);}
    }

    public void testStaleRouteDayAndSessionCannotWrite() throws Throwable {
        launch();click(R.id.adventure_choose);
        java.lang.reflect.Field pending=AdventureActivity.class.getDeclaredField("pendingDay");
        pending.setAccessible(true);
        runTestOnUiThread(()->{
            try{pending.set(activity,LocalDate.parse(day).minusDays(1).toString());}
            catch(IllegalAccessException failure){throw new AssertionError(failure);}
        });
        confirm();assertNull(adventures.choice(friend));assertTrue(activity.prompt.isShowing());
        runTestOnUiThread(()->activity.prompt.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        click(R.id.adventure_choose);
        java.lang.reflect.Field generation=AdventureActivity.class.getDeclaredField("openedGeneration");
        generation.setAccessible(true);
        runTestOnUiThread(()->{
            try{generation.setLong(activity,AccountSession.generation()-1);}
            catch(IllegalAccessException failure){throw new AssertionError(failure);}
        });
        confirm();assertNull(adventures.choice(friend));assertTrue(activity.prompt.isShowing());
        try(Cursor c=db.rawQuery("SELECT xp FROM friends WHERE id=?",new String[]{""+friend})){
            assertTrue(c.moveToFirst());assertEquals(23,c.getInt(0));
        }
    }
}
