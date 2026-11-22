package kr.example.haliltalk;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalBackup;
import kr.example.haliltalk.data.GoalStore;

/** 실제 화면과 파일 처리를 사용하고 시스템 문서 선택기의 반환값만 제어한다. */
public final class FeatureScreensTest extends GuestActivityTestCase {
    private Activity activity;
    private Context context;
    @Override protected void setUp() throws Exception {
        super.setUp();context=getInstrumentation().getTargetContext();
    }
    @Override protected void tearDown() throws Exception {
        try {
            if(activity!=null)getInstrumentation().runOnMainSync(()->activity.finish());
            getInstrumentation().waitForIdleSync();
        } finally {super.tearDown();}
    }
    private void launch(Class<? extends Activity> type) throws Exception {
        activity=getInstrumentation().startActivitySync(new Intent(context,type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        awaitWindowFocus(activity.getWindow());
    }
    private void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        CountDownLatch frame=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{
            View decor=activity.getWindow().getDecorView();
            decor.getViewTreeObserver().registerFrameCommitCallback(()->decor.post(
                    ()->android.view.Choreographer.getInstance().postFrameCallback(t->frame.countDown())));
            decor.invalidate();
        });
        assertTrue("Committed frame",frame.await(5,TimeUnit.SECONDS));
        Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File directory=new File(context.getExternalFilesDir(null),"feature-qa");
        assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG,100,out));
        } finally {screenshot.recycle();}
        EnhancementCapture.writeTargets(getInstrumentation(),activity.getWindow(),name,"feature-qa",true);
    }
    private ScrollView scroll(View view) {
        if(view instanceof ScrollView)return (ScrollView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            ScrollView found=scroll(((ViewGroup)view).getChildAt(i));
            if(found!=null)return found;
        }
        return null;
    }
    private void captureEnd(String name) throws Exception {
        ScrollView scroller=scroll(activity.getWindow().getDecorView());
        getInstrumentation().runOnMainSync(()->{
            scroller.setSmoothScrollingEnabled(false);
            scroller.fullScroll(View.FOCUS_DOWN);
        });
        capture(name);
        getInstrumentation().runOnMainSync(()->assertEquals("Scroll end must stay visible",
                Math.max(0,scroller.getChildAt(0).getBottom()+scroller.getPaddingBottom()-scroller.getHeight()),
                scroller.getScrollY()));
    }
    private IntentFilter pickerFilter() throws IntentFilter.MalformedMimeTypeException {
        IntentFilter filter=new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE);
        filter.addDataType("application/json");
        return filter;
    }
    public void testLoginValidationSettingsAndFileScreens() throws Exception {
        assertTrue(context.getSharedPreferences("account_session",0).edit().putBoolean("guest_entered",false).commit());
        launch(LoginActivity.class);capture("login-top");captureEnd("login-end");
        getInstrumentation().runOnMainSync(()->{
            ((EditText)activity.findViewById(R.id.auth_email)).setText("invalid-email");
            ((EditText)activity.findViewById(R.id.auth_password)).setText("invalid");
            activity.findViewById(R.id.login_button).performClick();
        });
        captureEnd("login-invalid");
        assertFalse(kr.example.haliltalk.auth.AccountSession.isEntered(context));
        getInstrumentation().runOnMainSync(()->activity.finish());getInstrumentation().waitForIdleSync();
        assertTrue(context.getSharedPreferences("account_session",0).edit().putBoolean("guest_entered",true).commit());
        launch(SettingsActivity.class);capture("settings-top");captureEnd("settings-end");
        assertFalse(activity.findViewById(R.id.auth_cloud_save).isEnabled());
        getInstrumentation().runOnMainSync(()->activity.finish());getInstrumentation().waitForIdleSync();
        launch(ShopActivity.class);capture("shop-top");
        getInstrumentation().runOnMainSync(()->activity.finish());getInstrumentation().waitForIdleSync();
        launch(TransferActivity.class);capture("transfer-top");captureEnd("transfer-end");
        Instrumentation.ActivityMonitor picker=getInstrumentation().addMonitor(
                pickerFilter(),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
        try {
            getInstrumentation().runOnMainSync(()->activity.findViewById(R.id.transfer_import).performClick());
            getInstrumentation().waitForIdleSync();
            assertEquals(1,picker.getHits());
            assertTrue(activity.findViewById(R.id.transfer_export).isEnabled());
            capture("transfer-cancelled");
            // 파일 선택 취소 후의 안정된 상단으로 SystemUI 첫 프레임의 아이콘 전환을 제외한다.
            getInstrumentation().runOnMainSync(()->{
                ScrollView scroller=scroll(activity.getWindow().getDecorView());
                scroller.setSmoothScrollingEnabled(false);
                scroller.fullScroll(View.FOCUS_UP);
            });
            capture("transfer-top");
        } finally {getInstrumentation().removeMonitor(picker);}
    }
    private void onStatus(BooleanSupplier ready,Runnable action) throws Exception {
        CountDownLatch changed=new CountDownLatch(1);
        TextView status=activity.findViewById(R.id.transfer_status);
        TextWatcher listener=new TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) { }
            public void onTextChanged(CharSequence text,int start,int before,int count) {
                if(ready.getAsBoolean())changed.countDown();
            }
            public void afterTextChanged(Editable text) { }
        };
        getInstrumentation().runOnMainSync(()->{status.addTextChangedListener(listener);action.run();});
        try {assertTrue("File operation state change",changed.await(10,TimeUnit.SECONDS));}
        finally {getInstrumentation().runOnMainSync(()->status.removeTextChangedListener(listener));}
        getInstrumentation().waitForIdleSync();
    }
    public void testFilePreviewDoesNotWriteUntilConfirmedAndRejectsCorruptFile() throws Exception {
        String pkg="qa.transfer.flow.n"+System.nanoTime();
        File document=new File(context.getCacheDir(),"qa-transfer-"+System.nanoTime()+".json");
        try(FriendsDatabase helper=new FriendsDatabase(context)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            ContentValues friendValues=new ContentValues();
            friendValues.put("package",pkg);friendValues.put("name","파일 검증 친구");
            friendValues.put("personality","차분한 친구");
            int friend=(int)db.insertOrThrow("friends",null,friendValues);
            try {
                byte[] bytes;
                try(SQLiteDatabase fixture=SQLiteDatabase.create(null)) {
                    helper.onCreate(fixture);
                    fixture.insertOrThrow("friends",null,friendValues);
                    try(android.database.Cursor c=fixture.rawQuery("SELECT id FROM friends",null)) {
                        assertTrue(c.moveToFirst());new GoalStore(fixture).save(c.getInt(0),0,"가져오기 검증 목표",null);
                    }
                    bytes=GoalBackup.exportData(fixture);
                }
                try(FileOutputStream out=new FileOutputStream(document)){out.write(bytes);}
                launch(TransferActivity.class);
                Instrumentation.ActivityMonitor picker=getInstrumentation().addMonitor(
                        pickerFilter(),
                        new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(document))),true);
                try {
                    onStatus(()->activity.findViewById(R.id.transfer_confirm).getVisibility()==View.VISIBLE,
                            ()->activity.findViewById(R.id.transfer_import).performClick());
                    assertEquals(1,picker.getHits());
                    assertTrue(new GoalStore(db).list(friend).isEmpty());
                    capture("transfer-preview");
                    captureEnd("transfer-preview-end");
                    onStatus(()->new GoalStore(db).list(friend).size()==1,
                            ()->activity.findViewById(R.id.transfer_confirm).performClick());
                    capture("transfer-imported");
                } finally {getInstrumentation().removeMonitor(picker);}
                try(FileOutputStream out=new FileOutputStream(document)){out.write("{invalid".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                picker=getInstrumentation().addMonitor(pickerFilter(),
                        new Instrumentation.ActivityResult(Activity.RESULT_OK,new Intent().setData(Uri.fromFile(document))),true);
                try {
                    onStatus(()->activity.findViewById(R.id.transfer_export).isEnabled(),
                            ()->activity.findViewById(R.id.transfer_import).performClick());
                    capture("transfer-invalid");
                    assertEquals(1,new GoalStore(db).list(friend).size());
                } finally {getInstrumentation().removeMonitor(picker);}
            } finally {
                getInstrumentation().runOnMainSync(()->{if(activity!=null)activity.finish();});
                getInstrumentation().waitForIdleSync();
                db.delete("friends","id=?",new String[]{""+friend});
                kr.example.haliltalk.goals.GoalNotifications.reschedule(context);
            }
        } finally {assertTrue(!document.exists()||document.delete());}
    }
}
