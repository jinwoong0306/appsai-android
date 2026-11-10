package kr.example.haliltalk;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.data.FriendsDatabase;

public final class AccessoryGridTest extends InstrumentationTestCase {
    public void testEqualCardsWrapAtPhoneAndLargeFontWidths() throws Throwable {
        runTestOnUiThread(()->{
            assertGrid(320,1,3);
            assertGrid(280,1,2);
            assertGrid(320,1.3f,2);
            assertGrid(600,1,3);
        });
    }

    void assertGrid(int widthDp,float fontScale,int columns){
        Context target=getInstrumentation().getTargetContext();
        Configuration configuration=new Configuration(target.getResources().getConfiguration());
        configuration.fontScale=fontScale;
        Context context=new ContextThemeWrapper(target.createConfigurationContext(configuration),R.style.AppTheme);
        AccessoryGrid grid=new AccessoryGrid(context,null);
        for(int i=0;i<CharacterCatalog.HAT_IDS.length;i++){
            View card=LayoutInflater.from(context).inflate(R.layout.row_choice,grid,false);
            card.setTag(CharacterCatalog.HAT_IDS[i]);
            ((TextView)card.findViewById(R.id.choice_label)).setText(
                    context.getString(R.string.shop_locked,CharacterCatalog.HAT_NAMES[i],90));
            grid.addView(card);
        }
        int width=Math.round(widthDp*context.getResources().getDisplayMetrics().density);
        grid.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        grid.layout(0,0,width,grid.getMeasuredHeight());
        int firstWidth=grid.getChildAt(0).getWidth();
        assertEquals(columns,firstRowCount(grid));
        assertTrue(grid.getChildAt(columns).getTop()>grid.getChildAt(0).getBottom());
        for(int i=0;i<grid.getChildCount();i++){
            View card=grid.getChildAt(i);
            assertTrue("Equal cells may differ only by rounding",Math.abs(firstWidth-card.getWidth())<=1);
            assertTrue(card.getLeft()>=0);
            assertTrue(card.getRight()<=width);
            if(i%columns!=0)assertTrue(card.getLeft()>grid.getChildAt(i-1).getRight());
            if(i>=columns)assertTrue(card.getTop()>grid.getChildAt(i-columns).getBottom());
            TextView label=card.findViewById(R.id.choice_label);
            assertNotNull(label.getLayout());
            assertTrue("Locked labels must wrap without clipping",
                    label.getHeight()>=label.getLayout().getHeight()+label.getCompoundPaddingTop()+label.getCompoundPaddingBottom());
            assertTrue(label.getBottom()<=card.getHeight()-card.getPaddingBottom());
        }
    }

    int firstRowCount(AccessoryGrid grid){
        int count=0;
        while(count<grid.getChildCount()&&grid.getChildAt(count).getTop()==grid.getChildAt(0).getTop())count++;
        return count;
    }

    public void testNewFriendGridScrollKeepsPreviewActionsAndSelectionPosition() throws Throwable {
        Context context=getInstrumentation().getTargetContext();
        SharedPreferences preferences=context.getSharedPreferences("MainActivity",0);
        boolean hadWelcome=preferences.contains("welcome_done");
        boolean welcome=preferences.getBoolean("welcome_done",false);
        assertTrue(preferences.edit().putBoolean("welcome_done",true).commit());
        MainActivity activity=null;
        try {
            activity=(MainActivity)getInstrumentation().startActivitySync(new Intent()
                    .setClassName(context,MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity main=activity;
            runTestOnUiThread(()->{
                main.selectedLabel="Grid fixture";main.nickname="Grid fixture";main.hat="beret";
                main.configureCharacter();
            });
            assertScrollingScreen(main,R.id.accessory_choices,R.id.character_scroll,
                    R.id.character_preview,R.id.create_friend);
            runTestOnUiThread(()->assertEquals("none",main.hat));
        } finally {
            if(activity!=null){
                MainActivity main=activity;
                runTestOnUiThread(main::finish);
                getInstrumentation().waitForIdleSync();
            }
            SharedPreferences.Editor editor=preferences.edit();
            if(hadWelcome)editor.putBoolean("welcome_done",welcome);else editor.remove("welcome_done");
            assertTrue(editor.commit());
        }
    }

    public void testWardrobeGridScrollKeepsPreviewActionsAndSelectionPosition() throws Throwable {
        Context context=getInstrumentation().getTargetContext();
        SQLiteDatabase db=new FriendsDatabase(context).getWritableDatabase();
        int friend=0;
        CustomizationActivity activity=null;
        try {
            ContentValues values=new ContentValues();
            values.put("package","test.appsai.grid."+System.nanoTime());
            values.put("name","Grid fixture");values.put("personality","다정한 친구");values.put("xp",0);
            friend=(int)db.insertOrThrow("friends",null,values);
            activity=(CustomizationActivity)getInstrumentation().startActivitySync(new Intent()
                    .setClassName(context,CustomizationActivity.class.getName()).putExtra("friend_id",friend)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            CustomizationActivity wardrobe=activity;
            runTestOnUiThread(()->{wardrobe.appearance[1]="beret";wardrobe.render();});
            assertScrollingScreen(wardrobe,R.id.wardrobe_items,R.id.wardrobe_scroll,
                    R.id.wardrobe_preview,R.id.wardrobe_actions);
            runTestOnUiThread(()->{
                assertEquals("none",wardrobe.appearance[1]);
                assertFalse(wardrobe.findViewById(R.id.wardrobe_save).isEnabled());
                assertFalse(wardrobe.findViewById(R.id.wardrobe_cancel).isEnabled());
            });
        } finally {
            if(activity!=null){
                CustomizationActivity wardrobe=activity;
                runTestOnUiThread(wardrobe::finish);
                getInstrumentation().waitForIdleSync();
            }
            if(friend>0)db.delete("friends","id=?",new String[]{""+friend});
            db.close();
        }
    }

    void assertScrollingScreen(Activity activity,int gridId,int scrollId,int previewId,int actionsId) throws Throwable {
        View root=activity.findViewById(android.R.id.content);
        AccessoryGrid grid=activity.findViewById(gridId);
        ScrollView scroll=activity.findViewById(scrollId);
        Rect previewBefore=new Rect(),actionsBefore=new Rect();
        afterLayout(root,()->{
            scroll.setFocusableInTouchMode(true);scroll.requestFocus();scroll.scrollTo(0,0);
        });
        runTestOnUiThread(()->{
            assertTrue(activity.findViewById(previewId).getGlobalVisibleRect(previewBefore));
            assertTrue(activity.findViewById(actionsId).getGlobalVisibleRect(actionsBefore));
            assertSame("The existing ScrollView owns the grid",scroll,grid.getParent().getParent());
            assertEquals(CharacterCatalog.HAT_IDS.length,grid.getChildCount());
            assertTrue(grid.getChildAt(grid.getChildCount()-1).getTop()>grid.getChildAt(0).getTop());
        });
        afterLayout(root,()->scroll.scrollTo(0,scroll.getChildAt(0).getHeight()));
        int[] position={0};
        View none=grid.findViewWithTag("none");
        runTestOnUiThread(()->{
            assertTrue("Accessory rows must be vertically reachable",scroll.getScrollY()>0);
            position[0]=scroll.getScrollY();
        });
        afterLayout(root,()->assertTrue(none.performClick()));
        runTestOnUiThread(()->{
            assertSame("Selecting an item keeps its card mounted",none,grid.findViewWithTag("none"));
            assertTrue(none.isSelected());
            assertEquals(View.VISIBLE,none.findViewById(R.id.choice_check).getVisibility());
            assertEquals("Selection must not reset vertical scroll",position[0],scroll.getScrollY());
            Rect bounds=new Rect();
            assertTrue(activity.findViewById(previewId).getGlobalVisibleRect(bounds));
            assertEquals(previewBefore,bounds);
            assertTrue(activity.findViewById(actionsId).getGlobalVisibleRect(bounds));
            assertEquals(actionsBefore,bounds);
            assertEquals(0,scroll.getScrollX());
        });
    }

    void afterLayout(View root,Runnable action) throws Throwable {
        CountDownLatch drawn=new CountDownLatch(1);
        ViewTreeObserver.OnPreDrawListener listener=()->{drawn.countDown();return true;};
        runTestOnUiThread(()->{
            root.getViewTreeObserver().addOnPreDrawListener(listener);
            action.run();root.requestLayout();
        });
        try {assertTrue("Layout must reach pre-draw",drawn.await(5,TimeUnit.SECONDS));}
        finally {runTestOnUiThread(()->root.getViewTreeObserver().removeOnPreDrawListener(listener));}
    }
}
