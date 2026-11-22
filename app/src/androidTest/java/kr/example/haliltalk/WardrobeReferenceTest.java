package kr.example.haliltalk;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.view.ViewGroup;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.data.FriendsDatabase;

public final class WardrobeReferenceTest extends GuestActivityTestCase {
    private CustomizationActivity activity;
    private FriendsDatabase helper;
    private SQLiteDatabase database;
    private int fixtureId;
    private String fixturePackage,databaseName;
    private SharedPreferences scenes;
    private boolean hadScene;
    private String oldScene;
    private int originalBalance;

    public void testSceneDraftSaveReopenRecreationAndOwnershipGate() throws Throwable {
        android.content.Context context=getInstrumentation().getTargetContext();
        databaseName=AccountSession.databaseName(context);
        helper=new FriendsDatabase(context);database=helper.getWritableDatabase();
        fixturePackage="qa.reference.wardrobe.n"+UUID.randomUUID().toString().replace("-","");
        scenes=context.getSharedPreferences("wardrobe_scenes_"+databaseName,0);
        hadScene=scenes.contains(fixturePackage);oldScene=scenes.getString(fixturePackage,"none");
        ContentValues friend=new ContentValues();
        friend.put("package",fixturePackage);friend.put("name","SQL 공부");
        friend.put("personality","차분한 친구");friend.put("xp",987654);
        fixtureId=(int)database.insertOrThrow("friends",null,friend);
        ContentValues outfit=new ContentValues();
        outfit.put("friend",fixtureId);outfit.put("character","reading");
        outfit.put("hat","none");outfit.put("face","none");outfit.put("body","none");
        outfit.put("back","none");outfit.put("prop","none");
        database.insertOrThrow("appearance",null,outfit);
        try(Cursor cursor=database.rawQuery("SELECT balance FROM demo_wallet WHERE id=1",null)){
            assertTrue(cursor.moveToFirst());originalBalance=cursor.getInt(0);
        }
        try {
            launch();awaitWindowFocus(activity.getWindow());capture("wardrobe-initial.png");
            getInstrumentation().runOnMainSync(()->{
                android.widget.Button cancel=activity.findViewById(R.id.wardrobe_cancel);
                assertFalse(cancel.isEnabled());
                assertEquals(activity.getColor(R.color.reference_muted),cancel.getCurrentTextColor());
            });
            for(int slot=1;slot<5;slot++){
                int selected=slot;
                getInstrumentation().runOnMainSync(()->{activity.slot=selected;activity.render();});
                capture("wardrobe-slot-"+slot+".png");
            }
            getInstrumentation().runOnMainSync(()->{
                activity.slot=0;activity.render();
            });
            CountDownLatch endLayout=new CountDownLatch(1);
            getInstrumentation().runOnMainSync(()->{
                android.widget.ScrollView scroll=activity.findViewById(R.id.wardrobe_scroll);
                scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                    @Override public boolean onPreDraw(){
                        scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                        scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
                        endLayout.countDown();return true;
                    }
                });
                scroll.requestLayout();
            });
            assertTrue("Inventory layout reaches its measured end",endLayout.await(5,TimeUnit.SECONDS));
            capture("wardrobe-scroll-end.png");
            getInstrumentation().runOnMainSync(()->assertTrue(
                    ((android.widget.ScrollView)activity.findViewById(R.id.wardrobe_scroll)).getScrollY()>0));
            getInstrumentation().runOnMainSync(()->{
                ((android.widget.ScrollView)activity.findViewById(R.id.wardrobe_scroll)).scrollTo(0,0);
            });
            getInstrumentation().runOnMainSync(()->{
                assertEquals(fixtureId,activity.friendId);chooseScene("study");
                assertTrue(activity.hasChanges());assertEquals("none",scenes.getString(fixturePackage,"none"));
                assertTrue(activity.findViewById(R.id.wardrobe_save).isEnabled());
                android.widget.Button cancel=activity.findViewById(R.id.wardrobe_cancel);
                assertTrue(cancel.isEnabled());
                assertEquals(activity.getColor(R.color.reference_purple_ink),cancel.getCurrentTextColor());
            });
            capture("wardrobe-scene-draft.png");
            getInstrumentation().runOnMainSync(()->{
                activity.findViewById(R.id.wardrobe_cancel).performClick();
                assertEquals("none",activity.scene);assertFalse(activity.hasChanges());
                assertEquals("none",scenes.getString(fixturePackage,"none"));
                chooseScene("study");
            });
            recreateActivity();
            getInstrumentation().runOnMainSync(()->{
                assertEquals("study",activity.scene);assertEquals("none",scenes.getString(fixturePackage,"none"));
                activity.db.execSQL("CREATE TEMP TRIGGER reference_reject_appearance BEFORE INSERT ON appearance"
                        +" WHEN NEW.friend="+fixtureId+" BEGIN SELECT RAISE(IGNORE); END");
                try {
                    activity.findViewById(R.id.wardrobe_save).performClick();
                    assertEquals(View.VISIBLE,activity.findViewById(R.id.wardrobe_save_error).getVisibility());
                    assertTrue(activity.hasChanges());
                    assertEquals("none",scenes.getString(fixturePackage,"none"));
                    try(Cursor row=activity.db.rawQuery("SELECT character FROM appearance WHERE friend=?",
                            new String[]{String.valueOf(fixtureId)})){
                        assertTrue(row.moveToFirst());assertEquals("reading",row.getString(0));
                    }
                }finally{activity.db.execSQL("DROP TRIGGER reference_reject_appearance");}
            });
            capture("wardrobe-save-error.png");
            getInstrumentation().runOnMainSync(()->{
                activity.findViewById(R.id.wardrobe_save).performClick();
                assertEquals("study",scenes.getString(fixturePackage,"none"));assertFalse(activity.hasChanges());
                assertEquals(View.GONE,activity.findViewById(R.id.wardrobe_save_error).getVisibility());
            });
            recreateActivity();
            getInstrumentation().runOnMainSync(()->{
                assertEquals("study",activity.scene);chooseScene("park");activity.onBackPressed();
                assertTrue(activity.hasChanges());assertFalse(activity.isFinishing());
                assertNotNull(activity.discardDialog);
                assertTrue(activity.discardDialog.isShowing());
            });
            capture("wardrobe-discard-dialog.png");
            awaitWindowFocus(activity.discardDialog.getWindow());
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER);
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(()->{
                assertFalse(activity.discardDialog.isShowing());
                assertEquals("park",activity.scene);assertTrue(activity.hasChanges());
                activity.onBackPressed();
                activity.discardDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            });
            getInstrumentation().waitForIdleSync();
            assertTrue("Discarding the scene draft finishes wardrobe",activity.isFinishing());
            activity=null;
            launch();awaitWindowFocus(activity.getWindow());capture("wardrobe-reopened-saved-scene.png");
            getInstrumentation().runOnMainSync(()->{
                assertEquals("study",activity.scene);
                // Ownership tests use a disposable wallet, even if the real user owns every item.
                SQLiteDatabase isolated=SQLiteDatabase.create(null);
                new FriendsDatabase(context).onCreate(isolated);
                kr.example.haliltalk.shop.DemoShopStore originalStore=activity.store;
                try {
                    activity.store=new kr.example.haliltalk.shop.DemoShopStore(isolated);
                    activity.render();
                    int balance=activity.store.balance();
                    String locked=null;
                    for(String id:CharacterCatalog.HAT_IDS)if(!activity.store.owns(id)){locked=id;break;}
                    assertNotNull(locked);
                    String previous=activity.appearance[1];
                    View row=((ViewGroup)activity.findViewById(R.id.wardrobe_items)).findViewWithTag(locked);
                    assertNotNull(row);assertTrue(row.performClick());
                    assertEquals(View.VISIBLE,row.findViewById(R.id.choice_lock).getVisibility());
                    assertNotNull(activity.ownershipDialog);assertTrue(activity.ownershipDialog.isShowing());
                    assertFalse(activity.store.owns(locked));assertEquals(previous,activity.appearance[1]);
                    assertEquals(balance,activity.store.balance());
                    assertEquals("study",scenes.getString(fixturePackage,"none"));
                }finally{activity.store=originalStore;isolated.close();}
            });
            capture("wardrobe-locked-dialog.png");
            awaitWindowFocus(activity.ownershipDialog.getWindow());
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ESCAPE);
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(()->{
                assertFalse(activity.ownershipDialog.isShowing());
                activity.render();
            });
            getInstrumentation().runOnMainSync(()->{
                chooseScene("none");activity.findViewById(R.id.wardrobe_save).performClick();
                assertEquals("none",scenes.getString(fixturePackage,"none"));
                assertEquals(originalBalance,activity.store.balance());
            });
            capture("wardrobe-reset-saved.png");
            getInstrumentation().runOnMainSync(()->{
                activity.friendIds.clear();activity.friendNames.clear();activity.render();
                assertEquals(View.VISIBLE,activity.findViewById(R.id.wardrobe_empty).getVisibility());
                assertEquals(View.GONE,activity.findViewById(R.id.wardrobe_actions).getVisibility());
            });
            capture("wardrobe-empty.png");
        } finally {cleanup();}
    }

    private void launch() {
        activity=(CustomizationActivity)getInstrumentation().startActivitySync(new Intent(Intent.ACTION_MAIN)
                .setClass(getInstrumentation().getTargetContext(),CustomizationActivity.class)
                .putExtra("friend_id",fixtureId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void chooseScene(String id) {
        View row=((ViewGroup)activity.findViewById(R.id.wardrobe_backgrounds)).findViewWithTag(id);
        assertNotNull("Scene choice is rendered: "+id,row);assertTrue(row.performClick());assertEquals(id,activity.scene);
    }

    private void recreateActivity() throws Throwable {
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation()
                .addMonitor(CustomizationActivity.class.getName(),null,false);
        try {
            runTestOnUiThread(()->activity.recreate());
            CustomizationActivity replacement=(CustomizationActivity)getInstrumentation()
                    .waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("Wardrobe recreation completes",replacement);activity=replacement;
            getInstrumentation().waitForIdleSync();awaitWindowFocus(activity.getWindow());
        } finally {getInstrumentation().removeMonitor(monitor);}
    }

    private void capture(String name) throws Exception {
        android.view.Window window=activity.discardDialog!=null&&activity.discardDialog.isShowing()?
                activity.discardDialog.getWindow():activity.ownershipDialog!=null&&activity.ownershipDialog.isShowing()?
                activity.ownershipDialog.getWindow():activity.getWindow();
        EnhancementCapture.capture(getInstrumentation(),window,name.substring(0,name.length()-4),"reference-qa");
    }

    private void cleanup() throws Exception {
        if(activity!=null){
            getInstrumentation().runOnMainSync(()->{if(!activity.isDestroyed())activity.finish();});
            getInstrumentation().waitForIdleSync();activity=null;
        }
        if(database!=null&&database.isOpen()&&fixtureId>0){
            String[] id={String.valueOf(fixtureId)};
            try(Cursor xp=database.rawQuery("SELECT xp FROM friends WHERE id=?",id)){
                assertTrue(xp.moveToFirst());assertEquals(987654,xp.getInt(0));
            }
            database.beginTransaction();
            try {
                database.delete("appearance","friend=?",id);database.delete("messages","friend=?",id);
                database.delete("goals","friend=?",id);
                database.delete("friends","id=? AND package=?",new String[]{id[0],fixturePackage});
                database.setTransactionSuccessful();
            } finally {database.endTransaction();}
        }
        if(scenes!=null&&fixturePackage!=null){
            SharedPreferences.Editor edit=scenes.edit();
            if(hadScene)edit.putString(fixturePackage,oldScene);else edit.remove(fixturePackage);
            assertTrue(edit.commit());
        }
        if(database!=null&&database.isOpen())try(Cursor cursor=database.rawQuery(
                "SELECT balance FROM demo_wallet WHERE id=1",null)){
            assertTrue(cursor.moveToFirst());assertEquals(originalBalance,cursor.getInt(0));
        }
        if(helper!=null)helper.close();
    }
}
