package kr.example.haliltalk;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ListView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.character.SceneBackgrounds;

/** Actual current-screen tutorial captures in the isolated app, never production user data. */
public final class GuideCaptureTest extends MainFlowTest {
    private FriendsDatabase guideHelper;
    private SQLiteDatabase guideDb;
    private CustomizationActivity wardrobe;
    private ShopActivity guideShop;
    private JourneyActivity journey;
    private AdventureActivity adventure;
    private GoalsActivity goals;
    private int guideFriend;
    private String guidePackage;
    private boolean purchased;
    private int startingBalance;
    private boolean walletTouched;
    private String rewardDay;

    @Override protected void setUp() throws Exception {
        assertEquals("Capture requires an explicitly disposable device", "true",
                ((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("isolatedCapture"));
        super.setUp();
        guideHelper=new FriendsDatabase(getInstrumentation().getTargetContext());
        guideDb=guideHelper.getWritableDatabase();
        guidePackage="qa.visual.guide."+System.nanoTime();
        ContentValues values=new ContentValues();
        values.put("package",guidePackage);values.put("name","독서 친구");
        values.put("personality","다정한 친구");values.put("xp",25);
        guideFriend=(int)guideDb.insertOrThrow("friends",null,values);
        try(android.database.Cursor check=guideDb.rawQuery(
                "SELECT COUNT(*) FROM messages WHERE friend=?",new String[]{""+guideFriend})){
            assertTrue(check.moveToFirst());assertEquals("Guide ID must not inherit old fixture messages",0,check.getInt(0));
        }
        message("오늘은 어떤 작은 일을 해볼까요?");
        message("한 쪽부터 천천히 시작해 봐요.");
    }

    private void message(String body){
        ContentValues values=new ContentValues();
        values.put("friend",guideFriend);values.put("is_user",0);
        values.put("content",body);values.put("created",System.currentTimeMillis());
        guideDb.insertOrThrow("messages",null,values);
    }

    @Override protected void tearDown() throws Exception {
        try{
            if(wardrobe!=null){
                getInstrumentation().runOnMainSync(()->wardrobe.finish());
                getInstrumentation().waitForIdleSync();
            }
            if(guideShop!=null){
                getInstrumentation().runOnMainSync(()->guideShop.finish());
                getInstrumentation().waitForIdleSync();
            }
            getInstrumentation().runOnMainSync(()->{
                if(adventure!=null)adventure.finish();
                if(journey!=null)journey.finish();
                if(goals!=null)goals.finish();
            });
            getInstrumentation().waitForIdleSync();
            getInstrumentation().getTargetContext().getSharedPreferences(
                    "wardrobe_scenes_"+AccountSession.databaseName(getInstrumentation().getTargetContext()),0)
                    .edit().remove(guidePackage).commit();
            super.tearDown();
        }finally{
            if(purchased&&guideDb!=null){
                guideDb.delete("purchases","item_id=?",new String[]{"crown"});
                guideDb.delete("owned_items","item_id=?",new String[]{"crown"});
                guideDb.delete("coin_ledger","event_key=?",new String[]{"purchase:crown"});
            }
            if(walletTouched&&guideDb!=null){
                if(rewardDay!=null){
                    guideDb.delete("quest_reward_receipts","day=?",new String[]{rewardDay});
                    guideDb.delete("coin_ledger","event_key=?",new String[]{"quest:"+rewardDay});
                    try(android.database.Cursor check=guideDb.rawQuery(
                            "SELECT COUNT(*) FROM quest_reward_receipts WHERE day=?",new String[]{rewardDay})){
                        assertTrue(check.moveToFirst());assertEquals("Owned guide reward fixture removed",0,check.getInt(0));
                    }
                }
                ContentValues wallet=new ContentValues();wallet.put("balance",startingBalance);
                guideDb.update("demo_wallet",wallet,"id=1",null);
            }
            if(guideDb!=null){
                // Legacy messages intentionally have no friend FK; clean the owned fixture explicitly.
                guideDb.delete("messages","friend=?",new String[]{""+guideFriend});
                guideDb.delete("friends","id=?",new String[]{""+guideFriend});
                try(android.database.Cursor check=guideDb.rawQuery(
                        "SELECT COUNT(*) FROM friends WHERE package=?",new String[]{guidePackage})){
                    assertTrue(check.moveToFirst());assertEquals("Owned guide friend fixture removed",0,check.getInt(0));
                }
            }
            if(guideHelper!=null)guideHelper.close();
        }
    }

    private void snap(String name) throws Exception {
        EnhancementCapture.captureDisplay(getInstrumentation(),
                wardrobe!=null?wardrobe.getWindow():activity.getWindow(),name,"guide-source");
    }

    private void show(View target) throws Throwable {
        View root=target.getRootView();
        CountDownLatch shown=new CountDownLatch(1);
        ViewTreeObserver.OnPreDrawListener listener=()->{
            Rect rect=new Rect();
            if(target.getGlobalVisibleRect(rect)&&rect.width()>=target.getWidth()-1
                    &&rect.height()>=target.getHeight()-1)shown.countDown();
            return true;
        };
        runTestOnUiThread(()->{
            root.getViewTreeObserver().addOnPreDrawListener(listener);
            target.requestRectangleOnScreen(new Rect(0,0,target.getWidth(),target.getHeight()),true);
            root.requestLayout();
        });
        try{assertTrue("Tutorial target is fully visible",shown.await(5,TimeUnit.SECONDS));}
        finally{runTestOnUiThread(()->root.getViewTreeObserver().removeOnPreDrawListener(listener));}
    }

    private void openWardrobe() throws Throwable {
        if(wardrobe!=null){
            runTestOnUiThread(()->wardrobe.finish());
            getInstrumentation().waitForIdleSync();
        }
        wardrobe=(CustomizationActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),CustomizationActivity.class)
                        .putExtra("friend_id",guideFriend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        awaitWindowFocus(wardrobe.getWindow());
    }

    public void testCurrentScreensForVisualGuides() throws Throwable {
        activity=launch();awaitFocus();
        show(activity.findViewById(R.id.home_journey));snap("home-action");
        show(activity.findViewById(R.id.home_new_friend));snap("home-friend");
        View row=activity.findViewById(R.id.friend_list).findViewWithTag(guideFriend);
        assertNotNull(row);
        show(row);snap("home-unread");
        runTestOnUiThread(()->activity.chat(guideFriend,"독서 친구"));
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->((EditText)activity.findViewById(R.id.message_input))
                .setText("오늘 책 한 쪽을 읽어볼게요."));
        snap("chat-input");
        fakeChat.immediate=false;
        CountDownLatch reply=new CountDownLatch(1);
        Runnable arrived=()->{
            try(android.database.Cursor cursor=guideDb.rawQuery(
                    "SELECT COUNT(*) FROM messages WHERE friend=? AND content=?",
                    new String[]{""+guideFriend,"좋아요. 한 쪽을 읽고 느낌을 남겨 봐요."})){
                if(cursor.moveToFirst()&&cursor.getInt(0)==1)reply.countDown();
            }
        };
        runTestOnUiThread(()->injectedChat.addListener(arrived));
        try{
            runTestOnUiThread(()->activity.findViewById(R.id.send_message).performClick());
            assertEquals(1,fakeChat.replies.size());
            fakeChat.replies.get(0).set("좋아요. 한 쪽을 읽고 느낌을 남겨 봐요.");
            assertTrue("Controlled example reply persisted",reply.await(5,TimeUnit.SECONDS));
            getInstrumentation().waitForIdleSync();snap("chat-reply");
        }finally{runTestOnUiThread(()->injectedChat.removeListener(arrived));}
        runTestOnUiThread(activity::home);getInstrumentation().waitForIdleSync();
        row=activity.findViewById(R.id.friend_list).findViewWithTag(guideFriend);
        show(row);snap("home-read");
        runTestOnUiThread(activity::apps);getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            String candidate=activity.appNames.containsKey("com.google.android.calendar")?
                    "com.google.android.calendar":"kr.appsai.pickerprobe";
            assertTrue("Controlled launcher fixture is installed",activity.appNames.containsKey(candidate));
            ((EditText)activity.findViewById(R.id.app_search)).setText(activity.appNames.get(candidate));
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            ListView list=activity.findViewById(R.id.app_list);
            list.performItemClick(list.getChildAt(0),0,0);
        });
        snap("friend-app");
        runTestOnUiThread(()->{
            activity.nickname="독서 친구";activity.configureCharacter();
        });
        getInstrumentation().waitForIdleSync();snap("friend-create");
        goals=(GoalsActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),GoalsActivity.class)
                        .putExtra("friend_id",guideFriend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();awaitWindowFocus(goals.getWindow());
        show(goals.findViewById(R.id.add_goal));
        EnhancementCapture.captureDisplay(getInstrumentation(),goals.getWindow(),"goals-start","guide-source");
        runTestOnUiThread(()->goals.findViewById(R.id.add_goal).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->((EditText)goals.editor.findViewById(R.id.goal_input)).setText("책 한 쪽 읽기"));
        EnhancementCapture.capture(getInstrumentation(),goals.editor.getWindow(),"goals-edit","guide-source");
        runTestOnUiThread(()->goals.editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        show(goals.findViewById(R.id.goals_list));
        EnhancementCapture.captureDisplay(getInstrumentation(),goals.getWindow(),"goals-result","guide-source");
        journey=(JourneyActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),JourneyActivity.class)
                        .putExtra("friend_id",guideFriend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();awaitWindowFocus(journey.getWindow());
        show(journey.findViewById(R.id.journey_plan));
        EnhancementCapture.captureDisplay(getInstrumentation(),journey.getWindow(),"action-start","guide-source");
        runTestOnUiThread(()->journey.findViewById(R.id.journey_plan).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->((EditText)journey.editor.findViewById(R.id.journey_action_input)).setText("책 한 쪽 읽기"));
        EnhancementCapture.capture(getInstrumentation(),journey.editor.getWindow(),"action-save","guide-source");
        runTestOnUiThread(()->journey.editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();show(journey.findViewById(R.id.journey_review));
        EnhancementCapture.captureDisplay(getInstrumentation(),journey.getWindow(),"review-start","guide-source");
        runTestOnUiThread(()->journey.findViewById(R.id.journey_review).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            journey.editor.findViewById(R.id.journey_done).performClick();
            ((EditText)journey.editor.findViewById(R.id.journey_note_input)).setText("한 쪽부터 시작하니 부담이 줄었어요.");
        });
        EnhancementCapture.capture(getInstrumentation(),journey.editor.getWindow(),"review-save","guide-source");
        runTestOnUiThread(()->journey.editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        EnhancementCapture.captureDisplay(getInstrumentation(),journey.getWindow(),"review-result","guide-source");
        adventure=(AdventureActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),AdventureActivity.class)
                        .putExtra("friend_id",guideFriend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();awaitWindowFocus(adventure.getWindow());
        runTestOnUiThread(()->{
            if(adventure.findViewById(R.id.adventure_route_details).getVisibility()==View.VISIBLE)
                adventure.findViewById(R.id.adventure_route_toggle).performClick();
        });
        getInstrumentation().waitForIdleSync();show(adventure.findViewById(R.id.adventure_route_toggle));
        EnhancementCapture.captureDisplay(getInstrumentation(),adventure.getWindow(),"adventure-open","guide-source");
        runTestOnUiThread(()->adventure.findViewById(R.id.adventure_route_toggle).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->adventure.findViewById(R.id.adventure_route_three).performClick());
        show(adventure.findViewById(R.id.adventure_choose));
        EnhancementCapture.captureDisplay(getInstrumentation(),adventure.getWindow(),"adventure-select","guide-source");
        runTestOnUiThread(()->adventure.findViewById(R.id.adventure_choose).performClick());
        getInstrumentation().waitForIdleSync();
        EnhancementCapture.capture(getInstrumentation(),adventure.prompt.getWindow(),"adventure-confirm","guide-source");
        runTestOnUiThread(()->adventure.prompt.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->adventure.finish());adventure=null;
        getInstrumentation().waitForIdleSync();awaitWindowFocus(journey.getWindow());
        show(journey.findViewById(R.id.journey_reward));
        rewardDay=java.time.LocalDate.now().toString();
        try(android.database.Cursor c=guideDb.rawQuery("SELECT COUNT(*) FROM quest_reward_receipts WHERE day=?",
                new String[]{rewardDay})){
            assertTrue(c.moveToFirst());assertEquals("Guide reward fixture must be unclaimed",0,c.getInt(0));
        }
        startingBalance=new kr.example.haliltalk.shop.DemoShopStore(guideDb).balance();walletTouched=true;
        EnhancementCapture.captureDisplay(getInstrumentation(),journey.getWindow(),"coins-start","guide-source");
        runTestOnUiThread(()->journey.findViewById(R.id.journey_reward).performClick());
        getInstrumentation().waitForIdleSync();
        EnhancementCapture.capture(getInstrumentation(),journey.rewardDialog.getWindow(),"coins-confirm","guide-source");
        runTestOnUiThread(()->journey.rewardDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();show(journey.findViewById(R.id.journey_reward_status));
        assertEquals(startingBalance+20,new kr.example.haliltalk.shop.DemoShopStore(guideDb).balance());
        guideShop=(ShopActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),ShopActivity.class)
                        .putExtra("friend_id",guideFriend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();awaitWindowFocus(guideShop.getWindow());
        show(guideShop.header.findViewById(R.id.shop_balance));
        EnhancementCapture.captureDisplay(getInstrumentation(),guideShop.getWindow(),"coins-result","guide-source");
        assertFalse("Guide purchase fixture must start unowned",guideShop.store.owns("crown"));
        runTestOnUiThread(()->{guideShop.selected="crown";guideShop.refresh();});
        EnhancementCapture.captureDisplay(getInstrumentation(),guideShop.getWindow(),"purchase-selected","guide-source");
        runTestOnUiThread(()->guideShop.findViewById(R.id.shop_buy).performClick());
        getInstrumentation().waitForIdleSync();
        EnhancementCapture.capture(getInstrumentation(),guideShop.purchaseDialog.getWindow(),"purchase-confirm","guide-source");
        runTestOnUiThread(()->guideShop.purchaseDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        purchased=true;assertTrue(guideShop.store.owns("crown"));
        android.app.Instrumentation.ActivityMonitor wear=getInstrumentation().addMonitor(
                CustomizationActivity.class.getName(),null,false);
        try{
            runTestOnUiThread(()->guideShop.findViewById(R.id.shop_buy).performClick());
            wardrobe=(CustomizationActivity)wear.waitForActivityWithTimeout(5000);
            assertNotNull(wardrobe);awaitWindowFocus(wardrobe.getWindow());
        }finally{getInstrumentation().removeMonitor(wear);}
        assertEquals("crown",wardrobe.appearance[1]);snap("purchase-wear");
        runTestOnUiThread(()->wardrobe.findViewById(R.id.wardrobe_save).performClick());
        runTestOnUiThread(()->wardrobe.finish());wardrobe=null;
        getInstrumentation().waitForIdleSync();awaitWindowFocus(guideShop.getWindow());
        EnhancementCapture.captureDisplay(getInstrumentation(),guideShop.getWindow(),"history-open","guide-source");
        runTestOnUiThread(()->guideShop.header.findViewById(R.id.shop_history).performClick());
        getInstrumentation().waitForIdleSync();
        EnhancementCapture.capture(getInstrumentation(),guideShop.historyDialog.getWindow(),"history-entries","guide-source");
        runTestOnUiThread(()->guideShop.historyDialog.dismiss());
        openWardrobe();
        for(String scene:new String[]{"study","bedroom","library","cafe"}){
            View target=wardrobe.findViewById(R.id.wardrobe_backgrounds).findViewWithTag(scene);
            assertNotNull(target);show(target);
            if("bedroom".equals(scene))snap("background-choices");
            runTestOnUiThread(target::performClick);getInstrumentation().waitForIdleSync();
            assertEquals(scene,wardrobe.scene);assertTrue(wardrobe.hasChanges());
            snap("space-"+scene);
            if("bedroom".equals(scene)){
                snap("background-preview");snap("background-save");
            }
            runTestOnUiThread(()->wardrobe.findViewById(R.id.wardrobe_save).performClick());
            assertFalse(wardrobe.hasChanges());
            openWardrobe();assertEquals(scene,wardrobe.scene);
        }
        assertEquals("study",SceneBackgrounds.normalize("study"));
        assertEquals("bedroom",SceneBackgrounds.normalize("bedroom"));
        assertEquals("none",SceneBackgrounds.normalize("invalid-scene"));
        try(android.database.Cursor cursor=guideDb.rawQuery("SELECT xp FROM friends WHERE id=?",
                new String[]{""+guideFriend})){
            assertTrue(cursor.moveToFirst());assertEquals("One sent message retains the existing +1 XP rule",26,cursor.getInt(0));
        }
    }
}
