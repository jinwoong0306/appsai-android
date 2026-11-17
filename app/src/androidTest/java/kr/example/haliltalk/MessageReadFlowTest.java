package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.MessageReadStore;

/** [U6 | 표시 검증] 합성 DB로 한국어·글꼴·읽음 생명주기를 검사한다. 실제 대화는 변경하지 않는다. */
public final class MessageReadFlowTest extends GuestActivityTestCase {
    private MainActivity activity;
    private SQLiteDatabase fixture,original;
    private File file;
    private SharedPreferences welcome;
    private boolean hadWelcome,welcomeValue;
    private final int friend=190000001,other=190000002;

    @Override protected void setUp() throws Exception{
        super.setUp();
        android.content.Context context=getInstrumentation().getTargetContext();
        String localName=MainActivity.class.getName();
        String prefix=context.getPackageName()+".";
        if(localName.startsWith(prefix))localName=localName.substring(prefix.length());
        welcome=context.getSharedPreferences(localName,0);
        hadWelcome=welcome.contains("welcome_done");welcomeValue=welcome.getBoolean("welcome_done",false);
        assertTrue(welcome.edit().putBoolean("welcome_done",true).commit());
        file=context.getDatabasePath("qa_user_ui_"+System.nanoTime()+".db");
        fixture=SQLiteDatabase.openOrCreateDatabase(file,null);fixture.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(context)){helper.onCreate(fixture);}
        fixture.setVersion(8);
        fixture.execSQL("INSERT INTO friends(id,package,name,personality) VALUES(?,?,?,?)",
                new Object[]{friend,"qa.ui.read","하루 친구","다정한 친구"});
        fixture.execSQL("INSERT INTO friends(id,package,name,personality) VALUES(?,?,?,?)",
                new Object[]{other,"qa.ui.other","독서 친구","차분한 친구"});
    }
    @Override protected void tearDown() throws Exception{
        try{
            finishMain();
            fixture.close();getInstrumentation().getTargetContext().deleteDatabase(file.getName());
            SharedPreferences.Editor edit=welcome.edit();
            if(hadWelcome)edit.putBoolean("welcome_done",welcomeValue);else edit.remove("welcome_done");
            assertTrue(edit.commit());
        }finally{super.tearDown();}
    }
    private void finishMain() throws Exception{
        if(activity!=null){
            getInstrumentation().runOnMainSync(()->{activity.db=original;activity.finish();});
            getInstrumentation().waitForIdleSync();activity=null;
        }
    }
    private void launch() throws Exception{
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        awaitWindowFocus(activity.getWindow());
        getInstrumentation().runOnMainSync(()->{original=activity.db;activity.db=fixture;activity.home();});
        getInstrumentation().waitForIdleSync();
    }
    private long message(int target,boolean own,String text){
        ContentValues values=new ContentValues();values.put("friend",target);values.put("is_user",own?1:0);
        values.put("content",text);values.put("created",System.currentTimeMillis());
        return fixture.insertOrThrow("messages",null,values);
    }
    private TextView badge(int target){
        LinearLayout rows=activity.findViewById(R.id.friend_list);
        for(int i=0;i<rows.getChildCount();i++)if(Integer.valueOf(target).equals(rows.getChildAt(i).getTag()))
            return rows.getChildAt(i).findViewById(R.id.friend_unread);
        throw new AssertionError("Friend row missing");
    }
    private void awaitFrame() throws Exception{
        getInstrumentation().waitForIdleSync();awaitWindowFocus(activity.getWindow());
        CountDownLatch frame=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{
            View decor=activity.getWindow().getDecorView();
            decor.getViewTreeObserver().registerFrameCommitCallback(()->decor.post(
                    ()->android.view.Choreographer.getInstance().postFrameCallback(t->frame.countDown())));
            decor.invalidate();
        });
        assertTrue(frame.await(5,TimeUnit.SECONDS));
    }
    private void capture(String name) throws Exception{
        getInstrumentation().waitForIdleSync();
        if(activity.showingHome)getInstrumentation().runOnMainSync(()->{
            android.widget.ScrollView scroll=activity.findViewById(R.id.home_scroll);
            scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
        });
        awaitFrame();
        File dir=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"user-ui-qa");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream output=new FileOutputStream(new File(dir,name+".png"))){
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
        }finally{bitmap.recycle();}
    }
    public void testUnreadBadgesVisibleChatAndReopen() throws Throwable{
        message(friend,false,"오늘은 무엇을 해볼까요?");
        message(friend,false,"작은 시작을 응원해요.");
        message(friend,true,"내가 보낸 메시지는 세지 않아요");
        message(other,false,"다른 친구의 읽지 않은 답장");
        launch();
        assertEquals("2",badge(friend).getText().toString());capture("home-unread");
        runTestOnUiThread(()->activity.chat(friend,"하루 친구"));
        awaitFrame();
        assertEquals(0,new MessageReadStore(fixture).unreadCount(friend));
        assertEquals(1,new MessageReadStore(fixture).unreadCount(other));
        capture("chat-read");
        runTestOnUiThread(activity::home);getInstrumentation().waitForIdleSync();
        assertEquals(View.GONE,badge(friend).getVisibility());
        message(friend,false,"다음에 다시 확인할 답장이에요");
        runTestOnUiThread(activity::renderHomeFriends);capture("home-new-reply");
        assertEquals("1",badge(friend).getText().toString());
        finishMain();fixture.close();
        fixture=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READWRITE);
        fixture.setForeignKeyConstraintsEnabled(true);launch();
        assertEquals("1",badge(friend).getText().toString());
    }
    public void testCoveredAndBackgroundChatRemainUnreadUntilVisible() throws Throwable{
        launch();runTestOnUiThread(()->activity.chat(friend,"하루 친구"));
        final AlertDialog[] cover={null};
        runTestOnUiThread(()->cover[0]=new AlertDialog.Builder(activity).setTitle("확인")
                .setMessage("읽음 상태 검증").setPositiveButton("닫기",null).show());
        awaitWindowFocus(cover[0].getWindow());
        message(friend,false,"가려진 대화의 새 답장");
        runTestOnUiThread(activity::refreshChat);getInstrumentation().waitForIdleSync();
        assertEquals(1,new MessageReadStore(fixture).unreadCount(friend));
        runTestOnUiThread(()->cover[0].dismiss());awaitWindowFocus(activity.getWindow());
        awaitFrame();
        assertEquals(0,new MessageReadStore(fixture).unreadCount(friend));
        JourneyActivity background=(JourneyActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),JourneyActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            awaitWindowFocus(background.getWindow());
            message(friend,false,"다른 화면을 보는 동안 도착한 답장");
            runTestOnUiThread(activity::refreshChat);getInstrumentation().waitForIdleSync();
            assertEquals(1,new MessageReadStore(fixture).unreadCount(friend));
        }finally{runTestOnUiThread(background::finish);}
        awaitWindowFocus(activity.getWindow());awaitFrame();
        assertEquals(0,new MessageReadStore(fixture).unreadCount(friend));
    }
    public void testLargeUnreadBadgeKeepsExactAccessibilityCount() throws Throwable{
        for(int i=0;i<105;i++)message(friend,false,"Unread synthetic reply "+i);
        launch();assertEquals("99+",badge(friend).getText().toString());
        assertTrue(badge(friend).getContentDescription().toString().contains("105"));
        capture("home-unread-large");
    }
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
}
