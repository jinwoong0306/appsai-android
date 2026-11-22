package kr.example.haliltalk;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import kr.example.haliltalk.data.FriendsDatabase;

/** [U6 | 검증] Same actual screens at compact, landscape and wide window sizes. */
public final class FinalUiFlowTest extends GuestActivityTestCase {
    private Activity activity;
    private FriendsDatabase helper;
    private SQLiteDatabase db;
    private int friend;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context=getInstrumentation().getTargetContext();
        helper=new FriendsDatabase(context);db=helper.getWritableDatabase();
        ContentValues values=new ContentValues();
        values.put("package","qa.final.ui."+System.nanoTime());
        values.put("name","하루의 작은 실천을 함께하는 독서 친구");
        values.put("personality","다정한 친구");values.put("xp",12);
        friend=(int)db.insertOrThrow("friends",null,values);
    }

    @Override protected void tearDown() throws Exception {
        try {
            finish();
            db.delete("friends","id=?",new String[]{""+friend});
            helper.close();
        } finally { super.tearDown(); }
    }

    private void finish() {
        if(activity==null)return;
        getInstrumentation().runOnMainSync(()->activity.finish());
        getInstrumentation().waitForIdleSync();
        activity=null;
    }

    private void launch(Class<? extends Activity> type) throws Exception {
        finish();
        activity=getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),type).putExtra("friend_id",friend)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        awaitWindowFocus(activity.getWindow());
    }

    private void visible(int id) {
        View view=activity.findViewById(id);
        assertNotNull(view);
        Rect rect=new Rect();
        assertTrue("Control must be visible: "+id,view.getGlobalVisibleRect(rect));
        assertTrue("Control has visible area",rect.width()>0&&rect.height()>0);
        assertTrue("Control must not be clipped horizontally: "+id,rect.width()>=view.getWidth()-1);
    }

    private void capture(String name) throws Exception {
        EnhancementCapture.captureDisplay(getInstrumentation(),activity.getWindow(),name,"final-ui-qa");
    }

    public void testAdaptiveScreensAndReopenableHelpDoNotChangeBalance() throws Throwable {
        launch(MainActivity.class);
        runTestOnUiThread(()->{
            visible(R.id.home_help);visible(R.id.bottom_navigation);
            View root=((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
            int available=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();
            assertTrue("Wide content stays within reading width",
                    available<=activity.getResources().getDimensionPixelSize(R.dimen.content_max_width));
        });
        capture("01-home");
        int balance=new kr.example.haliltalk.shop.DemoShopStore(db).balance();
        getInstrumentation().runOnMainSync(()->activity.findViewById(R.id.home_help).performClick());
        getInstrumentation().waitForIdleSync();
        DialogFragment guide=null;
        for(Fragment fragment:activity.getFragmentManager().getFragments())
            if(fragment instanceof DialogFragment)guide=(DialogFragment)fragment;
        assertNotNull("Help opens a native dialog",guide);
        EnhancementCapture.capture(getInstrumentation(),guide.getDialog().getWindow(),"02-home-help","final-ui-qa");
        DialogFragment current=guide;
        runTestOnUiThread(()->current.getDialog().findViewById(R.id.help_questions).findViewWithTag("action").performClick());
        getInstrumentation().waitForIdleSync();
        EnhancementCapture.capture(getInstrumentation(),current.getDialog().getWindow(),"02-home-image-1","final-ui-qa");
        for(int step=2;step<=3;step++){
            runTestOnUiThread(()->current.getDialog().findViewById(R.id.help_next).performClick());
            getInstrumentation().waitForIdleSync();
            EnhancementCapture.capture(getInstrumentation(),current.getDialog().getWindow(),
                    "02-home-help-"+step,"final-ui-qa");
        }
        getInstrumentation().runOnMainSync(current::dismiss);
        getInstrumentation().waitForIdleSync();
        assertEquals("Reading help must not change coins",balance,
                new kr.example.haliltalk.shop.DemoShopStore(db).balance());
        getInstrumentation().runOnMainSync(()->((MainActivity)activity).apps());
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{visible(R.id.app_list);visible(R.id.apps_continue);});
        capture("09-app-picker");
        getInstrumentation().runOnMainSync(()->((MainActivity)activity).chat(friend,"독서 친구"));
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{visible(R.id.message_input);visible(R.id.chat_scroll);visible(R.id.chat_help);});
        capture("10-chat");

        launch(AdventureActivity.class);
        runTestOnUiThread(()->visible(R.id.adventure_primary));
        capture("03-adventure");
        launch(JourneyActivity.class);
        runTestOnUiThread(()->visible(R.id.journey_back));
        capture("04-journey");
        launch(ShopActivity.class);
        runTestOnUiThread(()->{visible(R.id.shop_buy);visible(R.id.shop_list);});
        capture("05-shop");
        runTestOnUiThread(()->((ShopActivity)activity).header.findViewById(R.id.shop_history).performClick());
        getInstrumentation().waitForIdleSync();
        android.app.AlertDialog history=((ShopActivity)activity).historyDialog;
        assertNotNull(history);assertTrue(history.isShowing());
        assertTrue(((ShopActivity)activity).store.history().size()>0);
        EnhancementCapture.capture(getInstrumentation(),history.getWindow(),"11-coin-history","final-ui-qa");
        runTestOnUiThread(history::dismiss);
        launch(CustomizationActivity.class);
        runTestOnUiThread(()->{
            visible(R.id.wardrobe_save);visible(R.id.wardrobe_scroll);visible(R.id.feature_help);
        });
        capture("06-wardrobe");
        launch(GoalsActivity.class);
        runTestOnUiThread(()->visible(R.id.feature_help));
        capture("07-goals");
        launch(SettingsActivity.class);
        runTestOnUiThread(()->visible(R.id.feature_help));
        capture("08-settings");
    }
}
