package kr.example.haliltalk;

import android.app.Dialog;
import android.app.Fragment;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;

/** Exercise the real native fragment without changing the guest's business records. */
public final class HelpGuideTest extends GuestActivityTestCase {
    private Context context;
    private SettingsActivity activity;
    private SharedPreferences preferences,welcome;
    private boolean hadComplete,complete,hadWelcome,welcomeSent;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context=getInstrumentation().getTargetContext();
        preferences=context.getSharedPreferences(HelpGuide.PREFERENCES,0);
        hadComplete=preferences.contains(HelpGuide.HOME_COMPLETE);
        complete=preferences.getBoolean(HelpGuide.HOME_COMPLETE,false);
        assertTrue(preferences.edit().remove(HelpGuide.HOME_COMPLETE).commit());
        welcome=WelcomeNotifications.preferences(context);
        hadWelcome=welcome.contains("welcome_sent");
        welcomeSent=welcome.getBoolean("welcome_sent",false);
        assertTrue(welcome.edit().putBoolean("welcome_sent",true).commit());
        activity=(SettingsActivity)getInstrumentation().startActivitySync(
                new Intent(context,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }

    @Override protected void tearDown() throws Exception {
        try {
            getInstrumentation().runOnMainSync(()->{
                if(activity!=null)activity.finish();
            });
            getInstrumentation().waitForIdleSync();
            SharedPreferences.Editor edit=preferences.edit();
            if(hadComplete)edit.putBoolean(HelpGuide.HOME_COMPLETE,complete);
            else edit.remove(HelpGuide.HOME_COMPLETE);
            assertTrue(edit.commit());
            edit=welcome.edit();
            if(hadWelcome)edit.putBoolean("welcome_sent",welcomeSent);
            else edit.remove("welcome_sent");
            assertTrue(edit.commit());
        } finally {super.tearDown();}
    }

    private HelpGuide guide() {
        return (HelpGuide)activity.getFragmentManager().findFragmentByTag(HelpGuide.TAG);
    }

    private Dialog dialog() {
        return guide().getDialog();
    }

    private void settle() {
        getInstrumentation().waitForIdleSync();
        try {
            EnhancementCapture.awaitFrame(getInstrumentation(),
                    guide()==null?activity.getWindow():dialog().getWindow());
        } catch(Exception error) {
            throw new AssertionError("The guide transition must reach a committed frame",error);
        }
    }

    private void open(String topic) {
        getInstrumentation().runOnMainSync(()->HelpGuide.show(activity,topic));
        settle();
        getInstrumentation().runOnMainSync(()->{
            assertNotNull(guide());
            assertTrue(dialog().isShowing());
        });
    }

    private void click(int id) {
        String[] failure={null};
        getInstrumentation().runOnMainSync(()->{
            View button=dialog().findViewById(id);
            if(!button.isShown())failure[0]="Control must be visible: "
                    +context.getResources().getResourceEntryName(id)+"; "+guide().getArguments();
            else if(!button.performClick())failure[0]="Control did not handle its click: "+id;
        });
        assertNull(failure[0],failure[0]);
        settle();
    }

    private void select(String tutorial) {
        getInstrumentation().runOnMainSync(()->{
            View question=dialog().findViewById(R.id.help_questions).findViewWithTag(tutorial);
            assertNotNull(question);
            assertTrue(question.isShown());
            assertTrue(question.performClick());
        });
        settle();
    }

    private void assertMenu(String... expected) {
        getInstrumentation().runOnMainSync(()->{
            assertNull(guide().getArguments().getString("tutorial"));
            assertFalse(guide().getArguments().getBoolean("zoom"));
            LinearLayout questions=dialog().findViewById(R.id.help_questions);
            assertTrue(questions.isShown());
            assertEquals(expected.length,questions.getChildCount());
            for(int index=0;index<expected.length;index++) {
                assertEquals(expected[index],questions.getChildAt(index).getTag());
                assertTrue(questions.getChildAt(index) instanceof android.widget.Button);
                assertTrue(((TextView)questions.getChildAt(index)).getText().length()>0);
            }
            assertEquals(View.GONE,dialog().findViewById(R.id.help_controls).getVisibility());
        });
    }

    private void assertStep(String tutorial,int expected) {
        getInstrumentation().runOnMainSync(()->{
            assertEquals(tutorial,guide().getArguments().getString("tutorial"));
            assertEquals(expected,dialog().findViewById(R.id.help_progress).getTag());
            assertEquals(expected,guide().getArguments().getInt("step"));
            View image=dialog().findViewById(R.id.help_image);
            int imageId=(Integer)image.getTag();
            assertEquals("guide_"+tutorial+"_"+(expected+1),
                    context.getResources().getResourceEntryName(imageId));
            assertNotNull(context.getDrawable(imageId));
            assertTrue(image.getContentDescription().length()>0);
            assertEquals(View.VISIBLE,dialog().findViewById(R.id.help_controls).getVisibility());
        });
    }

    public void testZoomSupportsNamedDirectionalActionsAndKeyboard() throws Exception {
        open("home");select("action");click(R.id.help_image);
        EnhancementCapture.awaitFrame(getInstrumentation(),dialog().getWindow());
        getInstrumentation().runOnMainSync(()->{
            GuideImageView viewer=dialog().findViewById(R.id.help_zoom_image);
            int[] actions={
                    android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId(),
                    android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId(),
                    android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId(),
                    android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()};
            int[] keys={android.view.KeyEvent.KEYCODE_DPAD_LEFT,android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_DPAD_UP,android.view.KeyEvent.KEYCODE_DPAD_DOWN};
            for(int i=0;i<actions.length;i++){
                viewer.resetZoom();viewer.zoomBy(2);
                android.view.accessibility.AccessibilityNodeInfo node=android.view.accessibility.AccessibilityNodeInfo.obtain();
                viewer.onInitializeAccessibilityNodeInfo(node);
                boolean named=false;
                for(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action:node.getActionList())
                    if(action.getId()==actions[i])named=action.getLabel()!=null&&action.getLabel().length()>0;
                node.recycle();
                assertTrue("Available movement has an accessibility name",named);
                float[] before=new float[9],after=new float[9];
                viewer.getImageMatrix().getValues(before);
                assertTrue(viewer.performAccessibilityAction(actions[i],null));
                viewer.getImageMatrix().getValues(after);
                int axis=i<2?android.graphics.Matrix.MTRANS_X:android.graphics.Matrix.MTRANS_Y;
                assertTrue("Accessibility action moves image",Math.abs(before[axis]-after[axis])>.5f);
                viewer.resetZoom();viewer.zoomBy(2);
                viewer.getImageMatrix().getValues(before);
                assertTrue(viewer.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,keys[i])));
                viewer.getImageMatrix().getValues(after);
                assertTrue("Directional key moves image",Math.abs(before[axis]-after[axis])>.5f);
            }
            viewer.resetZoom();
            assertFalse("Fitted image does not claim to pan",viewer.performAccessibilityAction(actions[0],null));
        });
    }

    public void testEveryContextOpensItsOwnQuestionButtons() {
        String[] topics={"home","journey","adventure","shop","wardrobe","goals","chat","settings"};
        String[][] expected={{"action","friend","unread","coins"},{"action","review","adventure"},
                {"adventure","review","coins"},{"purchase","coins","history"},
                {"background","purchase"},{"goals","action","files"},{"chat","unread","goals"},
                {"files","friend","history"}};
        for(int index=0;index<topics.length;index++) {
            open(topics[index]);
            assertMenu(expected[index]);
            click(R.id.help_back);
            getInstrumentation().runOnMainSync(()->assertNull(guide()));
        }
        assertFalse(HelpGuide.isHomeComplete(context));
    }

    public void testCatalogHasTwelveTutorialsAndThirtyFourRealImages() {
        Set<Integer> images=new HashSet<>();
        Set<String> tutorials=new HashSet<>();
        for(String topic:new String[]{"home","journey","adventure","shop","wardrobe","goals","chat","settings"}) {
            for(String id:GuideCatalog.menu(topic)) {
                if(!tutorials.add(id))continue;
                GuideCatalog.Tutorial tutorial=GuideCatalog.get(id);
                assertEquals(id,tutorial.id);
                int count=("history".equals(id)||"unread".equals(id))?2:3;
                assertEquals(count,tutorial.images.length);
                assertEquals(count*3,context.getResources().getStringArray(tutorial.steps).length);
                assertTrue(context.getText(tutorial.details).length()>0);
                for(int index=0;index<count;index++) {
                    int image=tutorial.images[index];
                    assertTrue(images.add(image));
                    assertEquals("guide_"+id+"_"+(index+1),
                            context.getResources().getResourceEntryName(image));
                    assertNotNull(context.getDrawable(image));
                }
            }
        }
        assertEquals(12,tutorials.size());
        assertEquals(34,images.size());
    }

    public void testNextPreviousDoneAndMenuBack() {
        open("shop");
        select("purchase");
        assertStep("purchase",0);
        click(R.id.help_next);
        assertStep("purchase",1);
        click(R.id.help_previous);
        assertStep("purchase",0);
        click(R.id.help_previous);
        assertMenu("purchase","coins","history");
        select("history");
        assertStep("history",0);
        click(R.id.help_next);
        assertStep("history",1);
        click(R.id.help_next);
        assertMenu("purchase","coins","history");
        select("coins");
        click(R.id.help_next);
        click(R.id.help_next);
        assertStep("coins",2);
        click(R.id.help_next);
        assertMenu("purchase","coins","history");
        select("purchase");
        click(R.id.help_back);
        assertMenu("purchase","coins","history");
        assertFalse(HelpGuide.isHomeComplete(context));
    }

    private void recreate() {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                SettingsActivity.class.getName(),null,false);
        try {
            getInstrumentation().runOnMainSync(()->activity.recreate());
            activity=(SettingsActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("Recreated host must resume",activity);
            getInstrumentation().waitForIdleSync();
        } finally {getInstrumentation().removeMonitor(monitor);}
    }

    public void testDetailsZoomAndFrameworkRestoration() {
        open("shop");
        select("purchase");
        click(R.id.help_next);
        click(R.id.help_details_toggle);
        getInstrumentation().runOnMainSync(()->{
            assertEquals(View.VISIBLE,dialog().findViewById(R.id.help_details).getVisibility());
            assertTrue(guide().getArguments().getBoolean("details"));
        });
        click(R.id.help_image);
        getInstrumentation().runOnMainSync(()->{
            assertTrue(guide().getArguments().getBoolean("zoom"));
            assertEquals(View.VISIBLE,dialog().findViewById(R.id.help_zoom).getVisibility());
        });
        click(R.id.help_zoom_in);
        getInstrumentation().runOnMainSync(()->assertTrue(
                ((GuideImageView)dialog().findViewById(R.id.help_zoom_image)).zoomLevel()>1));
        click(R.id.help_zoom_reset);
        getInstrumentation().runOnMainSync(()->assertEquals(1f,
                ((GuideImageView)dialog().findViewById(R.id.help_zoom_image)).zoomLevel(),0f));
        recreate();
        assertStep("purchase",1);
        getInstrumentation().runOnMainSync(()->{
            assertEquals("shop",guide().getArguments().getString("topic"));
            assertTrue(guide().getArguments().getBoolean("details"));
            assertTrue(guide().getArguments().getBoolean("zoom"));
            assertEquals(View.VISIBLE,dialog().findViewById(R.id.help_zoom).getVisibility());
            assertEquals(1,guideCount());
        });
        click(R.id.help_zoom_close);
        getInstrumentation().runOnMainSync(()->assertEquals(View.VISIBLE,
                dialog().findViewById(R.id.help_details).getVisibility()));
        click(R.id.help_details_toggle);
        getInstrumentation().runOnMainSync(()->assertEquals(View.GONE,
                dialog().findViewById(R.id.help_details).getVisibility()));
        click(R.id.help_image);
        getInstrumentation().runOnMainSync(()->{
            Dialog dialog=dialog();
            assertTrue(dialog.dispatchKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BACK)));
            assertTrue(dialog.dispatchKeyEvent(new android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BACK)));
            assertFalse(guide().getArguments().getBoolean("zoom"));
            assertEquals("purchase",guide().getArguments().getString("tutorial"));
        });
        click(R.id.help_back);
        recreate();
        assertMenu("purchase","coins","history");
        assertFalse(HelpGuide.isHomeComplete(context));
    }

    public void testHomeDoneReturnsMenuWithoutCompletionAndCloseAllowsReplay() {
        open("home");
        assertFalse(HelpGuide.isHomeComplete(context));
        select("action");
        click(R.id.help_next);
        click(R.id.help_next);
        click(R.id.help_next);
        assertMenu("action","friend","unread","coins");
        assertFalse(HelpGuide.isHomeComplete(context));
        click(R.id.help_close);
        assertTrue(HelpGuide.isHomeComplete(context));
        getInstrumentation().runOnMainSync(()->assertNull(guide()));
        open("home");
        assertMenu("action","friend","unread","coins");
        assertTrue(HelpGuide.isHomeComplete(context));
    }

    public void testCancelBackAndOtherTopicCloseDoNotCompleteHome() {
        open("home");
        getInstrumentation().runOnMainSync(()->dialog().cancel());
        getInstrumentation().waitForIdleSync();
        assertFalse(HelpGuide.isHomeComplete(context));
        open("journey");
        click(R.id.help_close);
        assertFalse(HelpGuide.isHomeComplete(context));
        open("home");
        click(R.id.help_back);
        assertFalse(HelpGuide.isHomeComplete(context));
        open("home");
        click(R.id.help_skip);
        assertTrue(HelpGuide.isHomeComplete(context));
        open("home");
        assertMenu("action","friend","unread","coins");
    }

    public void testSkipHomeCardDoesNotOpenGuide() {
        getInstrumentation().runOnMainSync(()->{
            assertNull(guide());
            HelpGuide.skipHome(context);
            assertNull(guide());
        });
        assertTrue(HelpGuide.isHomeComplete(context));
        open("home");
        assertMenu("action","friend","unread","coins");
    }

    private int guideCount() {
        int count=0;
        for(Fragment fragment:activity.getFragmentManager().getFragments())
            if(fragment instanceof HelpGuide)count++;
        return count;
    }

    public void testDuplicateRequestsKeepOneGuideAndOriginalTopic() {
        getInstrumentation().runOnMainSync(()->{
            HelpGuide.show(activity,"adventure");
            HelpGuide first=guide();
            HelpGuide.show(activity,"home");
            HelpGuide.show(activity,"adventure");
            assertSame(first,guide());
            assertEquals(1,guideCount());
            assertEquals("adventure",guide().getArguments().getString("topic"));
        });
        getInstrumentation().waitForIdleSync();
        assertMenu("adventure","review","coins");
        select("adventure");
        click(R.id.help_next);
        getInstrumentation().runOnMainSync(()->HelpGuide.show(activity,"home"));
        getInstrumentation().waitForIdleSync();
        assertStep("adventure",1);
        assertFalse(HelpGuide.isHomeComplete(context));
    }

    public void testAllTopicsLeaveBusinessDataAndConsentUnchanged() {
        SharedPreferences consent=context.getSharedPreferences(
                AccountSession.preferencesName(context,"ai_chat_consent"),0);
        Map<String,?> savedConsent=consent.getAll();
        Map<String,?> savedSession=context.getSharedPreferences("account_session",0).getAll();
        try(FriendsDatabase helper=new FriendsDatabase(context)) {
            SQLiteDatabase db=helper.getReadableDatabase();
            String before=snapshot(db);
            for(String topic:new String[]{"journey","adventure","shop","wardrobe","goals","chat","settings","home"}) {
                open(topic);
                for(String id:GuideCatalog.menu(topic)) {
                    select(id);
                    for(int index=0;index<GuideCatalog.get(id).images.length;index++) {
                        assertStep(id,index);
                        click(R.id.help_details_toggle);
                        click(R.id.help_image);
                        click(R.id.help_zoom_close);
                        click(R.id.help_next);
                    }
                    assertMenu(GuideCatalog.menu(topic));
                }
                click(R.id.help_back);
                assertEquals("Education cannot change business data",before,snapshot(db));
                assertEquals(savedConsent,consent.getAll());
                assertEquals(savedSession,context.getSharedPreferences("account_session",0).getAll());
                assertFalse(HelpGuide.isHomeComplete(context));
            }
        }
    }

    private String snapshot(SQLiteDatabase db) {
        StringBuilder result=new StringBuilder();
        try(Cursor tables=db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",null)) {
            while(tables.moveToNext()) {
                String table=tables.getString(0);
                result.append(table).append('\n');
                try(Cursor rows=db.rawQuery("SELECT * FROM \""+table.replace("\"","\"\"")+"\" ORDER BY rowid",null)) {
                    while(rows.moveToNext()) {
                        for(int column=0;column<rows.getColumnCount();column++) {
                            int type=rows.getType(column);
                            String value=type==Cursor.FIELD_TYPE_NULL?"":
                                    type==Cursor.FIELD_TYPE_BLOB?Base64.encodeToString(rows.getBlob(column),Base64.NO_WRAP):
                                    rows.getString(column);
                            result.append(type).append(':').append(value.length()).append(':').append(value).append(';');
                        }
                        result.append('\n');
                    }
                }
            }
        }
        return result.toString();
    }
}
