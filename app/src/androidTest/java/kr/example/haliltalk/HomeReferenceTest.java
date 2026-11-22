package kr.example.haliltalk;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.data.FriendsDatabase;

/** Reference captures use only an isolated in-memory database. */
public final class HomeReferenceTest extends GuestActivityTestCase {
    private MainActivity activity;
    private SQLiteDatabase fixture;
    private SQLiteDatabase original;
    private SharedPreferences welcome;
    private boolean hadWelcome;
    private boolean welcomeValue;

    public void testHomeSyntheticDataOrderingTapsAndCaptures() throws Throwable {
        android.content.Context context=getInstrumentation().getTargetContext();
        welcome=context.getSharedPreferences("MainActivity",0);
        hadWelcome=welcome.contains("welcome_done");
        welcomeValue=welcome.getBoolean("welcome_done",false);
        assertTrue(welcome.edit().putBoolean("welcome_done",true).commit());
        try {
            activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(Intent.ACTION_MAIN)
                    .setClass(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            awaitWindowFocus(activity.getWindow());
            getInstrumentation().runOnMainSync(()->{
                original=activity.db;
                fixture=SQLiteDatabase.create(null);
                fixture.setForeignKeyConstraintsEnabled(true);
                new FriendsDatabase(context).onCreate(fixture);
                activity.db=fixture;
                activity.home();
                addFriend(11,"qa.reference.alpha","SQL 공부와 매일의 작은 습관을 함께하는 아주 긴 이름","차분한 친구",123456);
                addFriend(12,"qa.reference.beta","유튜브","장난기 많은 친구",1280);
                addFriend(13,"qa.reference.gamma","아직 대화가 없는 친구","다정한 친구",0);
                java.util.Calendar fixed=java.util.Calendar.getInstance();
                fixed.clear();fixed.set(2026,java.util.Calendar.NOVEMBER,6,9,47);
                long created=fixed.getTimeInMillis();
                addMessage(100,11,"이전 검증 메시지",0,created);
                addMessage(101,11,"오늘 배운 SQL을 함께 정리해요.",1,created+100);
                addMessage(102,12,"같은 시각의 앞선 메시지",0,created+1_500_200);
                addMessage(103,12,"오늘은 어떤 영상을 볼까요?",0,created+1_500_200);
                for(int i=14;i<34;i++){
                    addFriend(i,"qa.reference.friend."+i,i==33?"운동":i==32?"잠":"검증 친구 "+i,
                            "다정한 친구",i==33?2090:i==32?3720:i*1000);
                    addMessage(200+i,i,i==33?"오늘은 가볍게 몸을 움직여요.":i==32?
                            "푹 쉬고 다시 만나요.":"스크롤 검증 대화 "+i,0,created-3_600_000+i);
                }
                addAppearance(11,"reading");addAppearance(12,"video");
                addAppearance(33,"fitness");addAppearance(32,"chat");
                activity.renderHomeFriends();
                LinearLayout rows=activity.findViewById(R.id.friend_list);
                assertEquals(23,rows.getChildCount());
                assertEquals(12,((View)rows.getChildAt(0)).getTag());
                assertEquals(11,((View)rows.getChildAt(1)).getTag());
                assertEquals(33,((View)rows.getChildAt(2)).getTag());
                assertEquals("오늘은 어떤 영상을 볼까요?",((TextView)rows.getChildAt(0)
                        .findViewById(R.id.friend_preview)).getText().toString());
                assertEquals("다정한 친구",((TextView)rows.getChildAt(22)
                        .findViewById(R.id.friend_preview)).getText().toString());
                assertEquals(activity.getString(R.string.home_own_preview,"오늘 배운 SQL을 함께 정리해요."),((TextView)rows.getChildAt(1)
                        .findViewById(R.id.friend_preview)).getText().toString());
                assertTrue(((TextView)rows.getChildAt(0).findViewById(R.id.friend_time)).getText().toString()
                        .equals(activity.homeMessageTime(created+1_500_200)));
                assertEquals(View.VISIBLE,rows.getChildAt(0).findViewById(R.id.friend_unread).getVisibility());
                assertEquals("2",((TextView)rows.getChildAt(0)
                        .findViewById(R.id.friend_unread)).getText().toString());
                assertEquals("1",((TextView)rows.getChildAt(1)
                        .findViewById(R.id.friend_unread)).getText().toString());
                assertEquals(View.GONE,rows.getChildAt(22).findViewById(R.id.friend_unread).getVisibility());
                assertTrue(rows.getChildAt(1).performClick());
                assertFalse(activity.showingHome);
                assertEquals(11,activity.friendId);
                assertTrue(activity.findViewById(R.id.back_home).performClick());
                assertTrue(activity.showingHome);
                assertTrue(activity.findViewById(R.id.home_new_friend).performClick());
                assertTrue(activity.showingApps);
                assertEquals(24,fixtureRows("SELECT COUNT(*) FROM messages"));
                assertEquals(23,fixtureRows("SELECT COUNT(*) FROM friends"));
                assertEquals(123456,fixtureRows("SELECT xp FROM friends WHERE id=11"));
                activity.home();
            });
            capture("home-long-name-unread.png");
            getInstrumentation().runOnMainSync(()->{
                ScrollView scroll=activity.findViewById(R.id.home_scroll);
                scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
                assertTrue("The synthetic list must reach its scroll end",scroll.getScrollY()>0);
            });
            capture("home-scroll-end.png");
            getInstrumentation().runOnMainSync(()->{
                ScrollView scroll=activity.findViewById(R.id.home_scroll);
                scroll.scrollTo(0,0);
                fixture.delete("messages",null,null);
                fixture.delete("friends",null,null);
                activity.renderHomeFriends();
                assertEquals(1,((LinearLayout)activity.findViewById(R.id.friend_list)).getChildCount());
            });
            capture("home-empty.png");
        } finally {
            restoreAndFinish();
            if(welcome!=null){
                SharedPreferences.Editor editor=welcome.edit();
                if(hadWelcome)editor.putBoolean("welcome_done",welcomeValue);else editor.remove("welcome_done");
                assertTrue(editor.commit());
            }
        }
    }

    private void addFriend(int id,String pkg,String name,String personality,int xp) {
        ContentValues values=new ContentValues();
        values.put("id",id);values.put("package",pkg);values.put("name",name);
        values.put("personality",personality);values.put("xp",xp);
        fixture.insertOrThrow("friends",null,values);
    }

    private void addMessage(int id,int friend,String text,int user,long created) {
        ContentValues values=new ContentValues();
        values.put("id",id);values.put("friend",friend);values.put("content",text);
        values.put("is_user",user);values.put("created",created);
        fixture.insertOrThrow("messages",null,values);
    }

    private void addAppearance(int friend,String character){
        ContentValues values=new ContentValues();
        values.put("friend",friend);values.put("character",character);
        values.put("hat","none");values.put("face","none");
        values.put("body","none");values.put("back","none");values.put("prop","none");
        fixture.insertOrThrow("appearance",null,values);
    }

    private int fixtureRows(String sql) {
        try(Cursor cursor=fixture.rawQuery(sql,null)){assertTrue(cursor.moveToFirst());return cursor.getInt(0);}
    }

    private void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        CountDownLatch frame=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{
            View decor=activity.getWindow().getDecorView();
            if(android.os.Build.VERSION.SDK_INT>=29){
                decor.getViewTreeObserver().registerFrameCommitCallback(()->decor.post(
                        ()->android.view.Choreographer.getInstance().postFrameCallback(t->frame.countDown())));
                decor.invalidate();
            }else frame.countDown();
        });
        assertTrue("Screenshot waits for a committed frame",frame.await(5,TimeUnit.SECONDS));
        Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File directory=new File(activity.getExternalFilesDir(null),"reference-qa");
        assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream stream=new FileOutputStream(new File(directory,name))){
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,stream));
        }finally{screenshot.recycle();}
    }

    private void restoreAndFinish() throws Exception {
        if(activity==null)return;
        getInstrumentation().runOnMainSync(()->{
            if(original!=null)activity.db=original;
            if(!activity.isDestroyed())activity.finish();
        });
        getInstrumentation().waitForIdleSync();
        if(fixture!=null&&fixture.isOpen())fixture.close();
    }
}
