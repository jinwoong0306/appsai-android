package kr.example.haliltalk;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.ai.AiChatService;
import kr.example.haliltalk.ai.ChatController;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;

public class MainFlowTest extends InstrumentationTestCase {
    MainActivity activity;
    static final String NAME="QA calendar friend";
    static final String CHARACTER=CharacterCatalog.CHARACTER_IDS[6];
    static final String HAT=CharacterCatalog.HAT_IDS[3];
    static final String FACE=CharacterCatalog.FACE_IDS[2];
    static final String PROP=CharacterCatalog.PROP_IDS[3];
    ChatController previousChat,injectedChat;
    FakeChat fakeChat;
    SharedPreferences consentPreferences;
    boolean hadConsent,consent;

    static final class FakeChat implements AiChatService {
        boolean immediate=true,configured=true;
        int calls;
        final List<SettableFuture<String>> replies=new ArrayList<>();
        @Override public boolean isConfigured(){return configured;}
        @Override public ListenableFuture<String> generate(String personality,List<Turn> turns){
            calls++;SettableFuture<String> reply=SettableFuture.create();replies.add(reply);
            if(immediate)reply.set("Synthetic instrumented reply");
            return reply;
        }
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        android.content.Context context=getInstrumentation().getTargetContext();
        consentPreferences=context.getSharedPreferences("ai_chat_consent",0);
        hadConsent=consentPreferences.contains("external_chat_v1");
        consent=consentPreferences.getBoolean("external_chat_v1",false);
        fakeChat=new FakeChat();
        getInstrumentation().runOnMainSync(()->{
            ChatApplication app=(ChatApplication)context.getApplicationContext();
            previousChat=app.chatController;
            injectedChat=new ChatController(context,fakeChat);
            injectedChat.setConsent(true);app.chatController=injectedChat;
        });
    }

    @Override protected void tearDown() throws Exception {
        try {
            getInstrumentation().runOnMainSync(()->{
                if(activity!=null&&!activity.isDestroyed())activity.finish();
            });
            getInstrumentation().waitForIdleSync();
            getInstrumentation().runOnMainSync(()->{
                ((ChatApplication)getInstrumentation().getTargetContext().getApplicationContext()).chatController=previousChat;
                injectedChat.close();
            });
            SharedPreferences.Editor editor=consentPreferences.edit();
            if(hadConsent)editor.putBoolean("external_chat_v1",consent);else editor.remove("external_chat_v1");
            assertTrue(editor.commit());
        } finally {super.tearDown();}
    }

    void awaitFocus() throws Exception {
        CountDownLatch focused=new CountDownLatch(1);
        android.view.ViewTreeObserver.OnWindowFocusChangeListener listener=hasFocus->{
            if(hasFocus)focused.countDown();
        };
        getInstrumentation().runOnMainSync(()->{
            activity.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(listener);
            if(activity.hasWindowFocus())focused.countDown();
        });
        try {
            assertTrue("Window must receive focus",focused.await(5,TimeUnit.SECONDS));
        } finally {
            getInstrumentation().runOnMainSync(()->activity.getWindow().getDecorView().getViewTreeObserver()
                    .removeOnWindowFocusChangeListener(listener));
        }
    }

    MainActivity launch() {
        return (MainActivity)getInstrumentation().startActivitySync(new Intent(Intent.ACTION_MAIN)
                .setClassName("kr.example.haliltalk", MainActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        if(android.os.Build.VERSION.SDK_INT>=29){
            CountDownLatch frame=new CountDownLatch(1);
            getInstrumentation().runOnMainSync(()->{
                activity.getWindow().getDecorView().getViewTreeObserver().registerFrameCommitCallback(()->
                        activity.getWindow().getDecorView().post(()->android.view.Choreographer.getInstance()
                                .postFrameCallback(time->frame.countDown())));
                activity.getWindow().getDecorView().invalidate();
            });
            assertTrue("Screenshot needs a committed frame",frame.await(5,TimeUnit.SECONDS));
        }
        getInstrumentation().runOnMainSync(()->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.view.View root=((android.view.ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);
                int top=activity.getWindow().getDecorView().getRootWindowInsets()
                        .getInsets(android.view.WindowInsets.Type.systemBars()).top;
                assertTrue("Content must stay below the status bar",root.getPaddingTop()>=top);
            }
        });
        Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory=new File(activity.getExternalFilesDir(null),"main-qa");
        assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream stream=new FileOutputStream(new File(directory,name+".png"))){
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
        }finally{bitmap.recycle();}
    }

    void recreate() throws Throwable {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        try {
            runTestOnUiThread(()->activity.recreate());
            MainActivity restored=(MainActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("Activity recreation must complete",restored);
            activity=restored;
            getInstrumentation().waitForIdleSync();
            awaitFocus();
            getInstrumentation().runOnMainSync(()->{
                if(android.os.Build.VERSION.SDK_INT>=30)assertEquals("Status icons must match the active theme",
                        activity.getResources().getBoolean(R.bool.light_system_bars),
                        (activity.getWindow().getInsetsController().getSystemBarsAppearance()
                                &android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0);
            });
        }finally{getInstrumentation().removeMonitor(monitor);}
    }

    int count(String sql,String value) {
        try(Cursor cursor=activity.db.rawQuery(sql,value==null?null:new String[]{value})){
            assertTrue(cursor.moveToFirst());return cursor.getInt(0);
        }
    }

    int[] totals(SQLiteDatabase database) {
        try(Cursor cursor=database.rawQuery("SELECT (SELECT COUNT(*) FROM friends),"
                +"(SELECT COUNT(*) FROM messages),(SELECT COUNT(*) FROM appearance)",null)){
            assertTrue(cursor.moveToFirst());
            return new int[]{cursor.getInt(0),cursor.getInt(1),cursor.getInt(2)};
        }
    }

    void assertTotals(int[] expected,SQLiteDatabase database) {
        int[] actual=totals(database);
        for(int i=0;i<expected.length;i++)assertEquals("Table count "+i,expected[i],actual[i]);
    }

    void selectAppRow() {
        ListView list=activity.findViewById(R.id.app_list);
        assertEquals(1,activity.appsAdapter.getCount());
        View row=list.getChildAt(0);
        assertNotNull("The filtered app row must be laid out",row);
        assertTrue(list.performItemClick(row,0,activity.appsAdapter.getItemId(0)));
        assertTrue(activity.findViewById(R.id.apps_continue).isEnabled());
        assertTrue(activity.appsAdapter.getView(0,null,list).isSelected());
        assertTrue("Selecting a row must not skip setup",activity.showingApps);
    }

    void choice(int group,String id) {
        View selected=activity.findViewById(group).findViewWithTag(id);
        assertNotNull("Choice must exist: "+id,selected);
        assertTrue(selected.performClick());
        assertTrue(activity.findViewById(group).findViewWithTag(id).isSelected());
    }

    void tab(int index) {
        LinearLayout tabs=activity.findViewById(R.id.accessory_tabs);
        assertTrue(tabs.getChildAt(index).performClick());
        assertTrue(((LinearLayout)activity.findViewById(R.id.accessory_tabs)).getChildAt(index).isSelected());
    }

    void assertDraft() {
        assertTrue(activity.showingCharacter);
        assertEquals(NAME,((EditText)activity.findViewById(R.id.friend_name)).getText().toString());
        assertEquals(CHARACTER,activity.character);
        assertEquals(HAT,activity.hat);
        assertEquals(FACE,activity.face);
        assertEquals(PROP,activity.prop);
        assertEquals(2,activity.personality);
        assertEquals(2,activity.accessoryTab);
        assertTrue(activity.findViewById(R.id.character_choices).findViewWithTag(CHARACTER).isSelected());
        assertTrue(activity.findViewById(R.id.accessory_choices).findViewWithTag(PROP).isSelected());
        assertTrue(((LinearLayout)activity.findViewById(R.id.personality_choices)).getChildAt(2).isSelected());
        assertPreview(activity.findViewById(R.id.character_preview));
    }

    void assertStoredFriend(int id,String pkg) {
        try(Cursor cursor=activity.db.rawQuery("SELECT f.package,f.name,f.personality,a.character,a.hat,a.face,a.prop"
                +" FROM friends f JOIN appearance a ON a.friend=f.id WHERE f.id=?",new String[]{""+id})){
            assertTrue(cursor.moveToFirst());
            assertEquals(pkg,cursor.getString(0));
            assertEquals(NAME,cursor.getString(1));
            assertEquals(activity.personalities[2],cursor.getString(2));
            assertEquals(CHARACTER,cursor.getString(3));
            assertEquals(HAT,cursor.getString(4));
            assertEquals(FACE,cursor.getString(5));
            assertEquals(PROP,cursor.getString(6));
            assertFalse(cursor.moveToNext());
        }
    }

    void assertPreview(CharacterView actual) {
        assertTrue("Preview must be laid out",actual.getWidth()>0&&actual.getHeight()>0);
        CharacterView expected=new CharacterView(activity);
        expected.setAppearance(CHARACTER,HAT,FACE,PROP);
        if(actual.getBackground()!=null){
            assertNotNull(actual.getBackground().getConstantState());
            expected.setBackground(actual.getBackground().getConstantState()
                    .newDrawable(activity.getResources(),activity.getTheme()));
        }
        expected.setPadding(actual.getPaddingLeft(),actual.getPaddingTop(),actual.getPaddingRight(),actual.getPaddingBottom());
        expected.setLayoutDirection(actual.getLayoutDirection());
        expected.measure(View.MeasureSpec.makeMeasureSpec(actual.getWidth(),View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(actual.getHeight(),View.MeasureSpec.EXACTLY));
        expected.layout(0,0,actual.getWidth(),actual.getHeight());
        Bitmap wanted=Bitmap.createBitmap(actual.getWidth(),actual.getHeight(),Bitmap.Config.ARGB_8888);
        Bitmap shown=Bitmap.createBitmap(actual.getWidth(),actual.getHeight(),Bitmap.Config.ARGB_8888);
        try {
            expected.draw(new Canvas(wanted));
            actual.draw(new Canvas(shown));
            assertTrue("Preview must render the saved character and every accessory slot",wanted.sameAs(shown));
        } finally {
            wanted.recycle();shown.recycle();
        }
    }

    View ownFriendRow(int id) {
        LinearLayout rows=activity.findViewById(R.id.friend_list);
        int position=count("SELECT COUNT(*) FROM friends WHERE id>?",String.valueOf(id));
        View row=rows.getChildAt(position);
        assertNotNull("Created friend must appear on home",row);
        assertEquals(NAME,((TextView)row.findViewById(R.id.friend_label)).getText().toString());
        return row;
    }

    void hideKeyboard() throws Exception {
        if(android.os.Build.VERSION.SDK_INT<30){
            getInstrumentation().runOnMainSync(()->((android.view.inputmethod.InputMethodManager)
                    activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0));
            return;
        }
        CountDownLatch hidden=new CountDownLatch(1);
        View decor=activity.getWindow().getDecorView();
        android.view.ViewTreeObserver.OnGlobalLayoutListener listener=()->{
            android.view.WindowInsets insets=decor.getRootWindowInsets();
            if(insets!=null&&!insets.isVisible(android.view.WindowInsets.Type.ime()))hidden.countDown();
        };
        getInstrumentation().runOnMainSync(()->{
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            activity.getWindow().getInsetsController().hide(android.view.WindowInsets.Type.ime());
            listener.onGlobalLayout();
        });
        try {
            assertTrue("Keyboard must be hidden before screenshots",hidden.await(5,TimeUnit.SECONDS));
        } finally {
            getInstrumentation().runOnMainSync(()->decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener));
        }
    }

    void setupPosition(boolean bottom) throws Throwable {
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            ScrollView scroll=activity.findViewById(R.id.character_scroll);
            scroll.setFocusableInTouchMode(true);
            scroll.requestFocus();
            scroll.scrollTo(0,bottom?scroll.getChildAt(0).getHeight():0);
            if(bottom)assertTrue("Setup bottom must be scrolled into view",scroll.getScrollY()>0);
        });
    }

    void showNameKeyboard() throws Exception {
        if(android.os.Build.VERSION.SDK_INT<30)return;
        CountDownLatch shown=new CountDownLatch(1);
        View decor=activity.getWindow().getDecorView();
        android.view.ViewTreeObserver.OnGlobalLayoutListener listener=()->{
            android.view.WindowInsets insets=decor.getRootWindowInsets();
            if(insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime()))shown.countDown();
        };
        getInstrumentation().runOnMainSync(()->{
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            activity.findViewById(R.id.friend_name).requestFocus();
            activity.getWindow().getInsetsController().show(android.view.WindowInsets.Type.ime());
        });
        try {
            assertTrue("Name keyboard must open",shown.await(5,TimeUnit.SECONDS));
            capture("14-setup-keyboard");
            getInstrumentation().runOnMainSync(()->{
                View preview=activity.findViewById(R.id.character_preview);
                Rect visible=new Rect();
                assertTrue(preview.getGlobalVisibleRect(visible));
                assertEquals(preview.getHeight(),visible.height());
                View settings=activity.findViewById(R.id.character_scroll);
                assertTrue("Settings retain space above the keyboard",settings.getHeight()>0);
                View create=activity.findViewById(R.id.create_friend);
                assertTrue(create.getGlobalVisibleRect(visible));
                assertEquals(create.getHeight(),visible.height());
            });
        } finally {
            getInstrumentation().runOnMainSync(()->decor.getViewTreeObserver().removeOnGlobalLayoutListener(listener));
        }
    }

    void captureAccessory(int index,String id,String name) throws Throwable {
        runTestOnUiThread(()->tab(index));
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            View selected=activity.findViewById(R.id.accessory_choices).findViewWithTag(id);
            assertTrue(selected.isSelected());
            selected.requestRectangleOnScreen(new Rect(0,0,selected.getWidth(),selected.getHeight()),true);
        });
        capture(name);
    }

    public void testPickerFriendChatAndRecreationKeepExistingData() throws Throwable {
        SharedPreferences preferences=getInstrumentation().getTargetContext().getSharedPreferences("MainActivity",0);
        boolean hadWelcome=preferences.contains("welcome_done");
        boolean welcome=preferences.getBoolean("welcome_done",false);
        assertTrue(preferences.edit().putBoolean("welcome_done",true).commit());
        int[] created={0};
        String[] selected={null};
        int[][] originalCounts={null};
        try {
            activity=launch();
            awaitFocus();
            runTestOnUiThread(()->originalCounts[0]=totals(activity.db));
            capture("01-home");
            runTestOnUiThread(()->{
                assertTrue(activity.findViewById(R.id.nav_friends).isSelected());
                activity.findViewById(R.id.nav_apps).performClick();
                assertTrue(activity.showingApps);
                assertTrue(activity.findViewById(R.id.nav_apps).isSelected());
                ((EditText)activity.findViewById(R.id.app_search)).setText("calendar");
                activity.findViewById(R.id.nav_apps).performClick();
                assertEquals("calendar",((EditText)activity.findViewById(R.id.app_search)).getText().toString());
                activity.findViewById(R.id.nav_friends).performClick();
                assertTrue(activity.showingHome);
                activity.findViewById(R.id.nav_apps).performClick();
                assertEquals("calendar",((EditText)activity.findViewById(R.id.app_search)).getText().toString());
                activity.findViewById(R.id.nav_friends).performClick();
                assertTotals(originalCounts[0],activity.db);
            });
            runTestOnUiThread(()->activity.startNewFriend());
            capture("02-picker");
            runTestOnUiThread(()->{
                assertFalse(activity.findViewById(R.id.apps_continue).isEnabled());
                activity.findViewById(R.id.apps_continue).performClick();
                assertTrue(activity.showingApps);
                assertFalse(activity.allApps.isEmpty());
                Set<String> packages=new HashSet<>();
                for(ResolveInfo info:activity.allApps){
                    String pkg=info.activityInfo.packageName;
                    assertTrue(packages.add(pkg));assertNotNull(info.loadIcon(activity.getPackageManager()));
                    assertTrue(info.loadLabel(activity.getPackageManager()).length()>0);
                    if(selected[0]==null&&count("SELECT COUNT(*) FROM friends WHERE package=?",pkg)==0)selected[0]=pkg;
                }
                assertNotNull("Device needs a launcher app not already added as a friend",selected[0]);
                ((EditText)activity.findViewById(R.id.app_search)).setText("no.such.appsai.package.000000");
                assertEquals(0,activity.appsAdapter.getCount());
            });
            capture("03-empty-search");
            runTestOnUiThread(()->{
                View bar=activity.findViewById(R.id.bottom_navigation);
                Rect visible=new Rect();
                assertTrue(bar.getGlobalVisibleRect(visible));
                assertEquals("Empty search must keep the whole navigation visible",bar.getHeight(),visible.height());
                View page=(View)bar.getParent();
                assertEquals("Navigation remains at the bottom with no search results",
                        page.getHeight()-page.getPaddingBottom(),bar.getBottom());
                for(int tabId:new int[]{R.id.nav_friends,R.id.nav_apps}){
                    View tab=activity.findViewById(tabId);
                    assertTrue(tab.getGlobalVisibleRect(visible));
                    assertEquals(tab.getHeight(),visible.height());
                }
            });
            runTestOnUiThread(()->{
                EditText search=activity.findViewById(R.id.app_search);
                search.setText("");assertEquals(activity.allApps.size(),activity.appsAdapter.getCount());
                search.setText(selected[0]);
            });
            recreate();
            runTestOnUiThread(()->{
                assertTrue(activity.showingApps);
                assertEquals(selected[0],((EditText)activity.findViewById(R.id.app_search)).getText().toString());
                assertEquals(1,activity.appsAdapter.getCount());
                assertEquals(selected[0],activity.visibleApps.get(0).activityInfo.packageName);
                selectAppRow();
            });
            recreate();
            runTestOnUiThread(()->{
                assertTrue(activity.showingApps);
                assertEquals(selected[0],activity.selectedPackage);
                assertEquals(selected[0],((EditText)activity.findViewById(R.id.app_search)).getText().toString());
                assertTrue(activity.findViewById(R.id.apps_continue).isEnabled());
                ListView list=activity.findViewById(R.id.app_list);
                assertTrue(activity.appsAdapter.getView(0,null,list).isSelected());
                assertTotals(originalCounts[0],activity.db);
            });
            capture("04-picker-selected");
            runTestOnUiThread(()->{
                activity.findViewById(R.id.apps_continue).performClick();
                assertTrue(activity.showingCharacter);
                ((EditText)activity.findViewById(R.id.friend_name)).setText("Cancelled draft");
                choice(R.id.character_choices,CHARACTER);
                choice(R.id.accessory_choices,HAT);
                activity.findViewById(R.id.character_back).performClick();
                assertTrue(activity.showingApps);
                assertEquals(selected[0],activity.selectedPackage);
                assertTotals(originalCounts[0],activity.db);
                activity.findViewById(R.id.apps_continue).performClick();
                for(String invalidName:new String[]{"","   "}){
                    ((EditText)activity.findViewById(R.id.friend_name)).setText(invalidName);
                    activity.findViewById(R.id.create_friend).performClick();
                    assertTrue(activity.showingCharacter);
                    assertNotNull(((EditText)activity.findViewById(R.id.friend_name)).getError());
                    assertTotals(originalCounts[0],activity.db);
                }
                ((EditText)activity.findViewById(R.id.friend_name)).setText(NAME);
                assertNull(((EditText)activity.findViewById(R.id.friend_name)).getError());
                choice(R.id.character_choices,CHARACTER);
                tab(0);choice(R.id.accessory_choices,HAT);
                tab(1);choice(R.id.accessory_choices,FACE);
                tab(2);choice(R.id.accessory_choices,PROP);
                assertTrue(((LinearLayout)activity.findViewById(R.id.personality_choices)).getChildAt(2).performClick());
            });
            recreate();
            runTestOnUiThread(()->{
                assertEquals(selected[0],activity.selectedPackage);
                assertDraft();
                assertTotals(originalCounts[0],activity.db);
            });
            showNameKeyboard();
            hideKeyboard();
            setupPosition(false);
            capture("05-setup-top");
            Rect previewBefore=new Rect();
            runTestOnUiThread(()->{
                assertNull(activity.findViewById(R.id.bottom_navigation));
                assertTrue(activity.findViewById(R.id.character_preview).getGlobalVisibleRect(previewBefore));
            });
            setupPosition(true);
            capture("06-setup-bottom");
            runTestOnUiThread(()->{
                Rect after=new Rect();
                View preview=activity.findViewById(R.id.character_preview);
                assertTrue(preview.getGlobalVisibleRect(after));
                assertEquals("Preview must not move when settings scroll",previewBefore,after);
                assertEquals("Preview must remain fully visible",preview.getHeight(),after.height());
                assertTrue(((ScrollView)activity.findViewById(R.id.character_scroll)).getScrollY()>0);
            });
            captureAccessory(0,HAT,"07-setup-hat");
            captureAccessory(1,FACE,"08-setup-face");
            captureAccessory(2,PROP,"09-setup-prop");
            runTestOnUiThread(()->{
                activity.findViewById(R.id.create_friend).performClick();
                created[0]=activity.friendId;
                assertTrue(created[0]>0);
                assertEquals(1,count("SELECT COUNT(*) FROM friends WHERE package=?",selected[0]));
                assertStoredFriend(created[0],selected[0]);
                int messages=count("SELECT COUNT(*) FROM messages WHERE friend=?",String.valueOf(created[0]));
                assertEquals(1,messages);
                assertEquals(0,count("SELECT xp FROM friends WHERE id=?",""+created[0]));
                activity.findViewById(R.id.send_message).performClick();
                ((EditText)activity.findViewById(R.id.message_input)).setText("   ");
                activity.findViewById(R.id.send_message).performClick();
                assertEquals(messages,count("SELECT COUNT(*) FROM messages WHERE friend=?",String.valueOf(created[0])));
                assertEquals(0,count("SELECT xp FROM friends WHERE id=?",""+created[0]));
                ((EditText)activity.findViewById(R.id.message_input)).setText("Integration test message");
                activity.findViewById(R.id.send_message).performClick();
                assertEquals(messages+2,count("SELECT COUNT(*) FROM messages WHERE friend=?",String.valueOf(created[0])));
                assertEquals(1,count("SELECT xp FROM friends WHERE id=?",String.valueOf(created[0])));
                ((EditText)activity.findViewById(R.id.message_input)).setText("Unsent draft");
            });
            recreate();
            hideKeyboard();
            runTestOnUiThread(()->{
                assertEquals(created[0],activity.friendId);
                assertEquals("Unsent draft",((EditText)activity.findViewById(R.id.message_input)).getText().toString());
                assertEquals(NAME,((TextView)activity.findViewById(R.id.chat_title)).getText().toString());
                assertEquals(activity.personalities[2],((TextView)activity.findViewById(R.id.chat_personality)).getText().toString());
                assertStoredFriend(created[0],selected[0]);
                assertPreview(activity.findViewById(R.id.chat_character));
                assertEquals(3,((LinearLayout)activity.findViewById(R.id.message_list)).getChildCount());
            });
            capture("10-chat-restored");
            runTestOnUiThread(()->activity.findViewById(R.id.check_usage).performClick());
            capture("11-usage");
            getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            awaitFocus();
            runTestOnUiThread(()->activity.findViewById(R.id.back_home).performClick());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->assertPreview(ownFriendRow(created[0]).findViewById(R.id.friend_character)));
            capture("12-home-created");
            runTestOnUiThread(()->{
                activity.startNewFriend();
                ((EditText)activity.findViewById(R.id.app_search)).setText(selected[0]);
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                selectAppRow();
                activity.findViewById(R.id.apps_continue).performClick();
                assertFalse(activity.showingCharacter);
                assertEquals(created[0],activity.friendId);
                assertEquals(NAME,activity.friendName);
                assertEquals(1,count("SELECT COUNT(*) FROM friends WHERE package=?",selected[0]));
                assertStoredFriend(created[0],selected[0]);
                assertEquals(3,count("SELECT COUNT(*) FROM messages WHERE friend=?",""+created[0]));
                assertEquals(1,count("SELECT xp FROM friends WHERE id=?",""+created[0]));
            });
            runTestOnUiThread(()->activity.finish());
            getInstrumentation().waitForIdleSync();
            activity=launch();
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertEquals(1,count("SELECT COUNT(*) FROM friends WHERE package=?",selected[0]));
                assertEquals(3,count("SELECT COUNT(*) FROM messages WHERE friend=?",String.valueOf(created[0])));
                assertEquals(1,count("SELECT xp FROM friends WHERE id=?",String.valueOf(created[0])));
                assertStoredFriend(created[0],selected[0]);
                // Fresh Activity draft defaults differ from the saved appearance.
                assertEquals("chat",activity.character);
                assertEquals("none",activity.hat);
                assertPreview(ownFriendRow(created[0]).findViewById(R.id.friend_character));
                assertTrue(ownFriendRow(created[0]).performClick());
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertEquals(created[0],activity.friendId);
                assertEquals(NAME,((TextView)activity.findViewById(R.id.chat_title)).getText().toString());
                assertEquals(activity.personalities[2],((TextView)activity.findViewById(R.id.chat_personality)).getText().toString());
                assertPreview(activity.findViewById(R.id.chat_character));
                assertEquals(3,((LinearLayout)activity.findViewById(R.id.message_list)).getChildCount());
                assertEquals(1,count("SELECT COUNT(*) FROM messages WHERE friend=? AND is_user=1"
                        +" AND content='Integration test message'",""+created[0]));
            });
            capture("13-chat-reopened");
        } finally {
            try {
                if(activity!=null)runTestOnUiThread(()->activity.finish());
                getInstrumentation().waitForIdleSync();
                try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
                    SQLiteDatabase database=helper.getWritableDatabase();
                    if(selected[0]!=null){
                        // Only this initially absent package can belong to this test, even if creation failed mid-assertion.
                        try(Cursor cursor=database.rawQuery("SELECT id FROM friends WHERE package=?",new String[]{selected[0]})){
                            if(cursor.moveToFirst()){
                                String[] id={cursor.getString(0)};
                                database.beginTransaction();
                                try {
                                    database.delete("appearance","friend=?",id);
                                    database.delete("messages","friend=?",id);
                                    database.delete("friends","id=?",id);
                                    database.setTransactionSuccessful();
                                } finally { database.endTransaction(); }
                            }
                        }
                    }
                    if(originalCounts[0]!=null)assertTotals(originalCounts[0],database);
                }
            } finally {
                SharedPreferences.Editor editor=preferences.edit();
                if(hadWelcome)editor.putBoolean("welcome_done",welcome);else editor.remove("welcome_done");
                assertTrue(editor.commit());
            }
        }
    }

    public void testAiConsentPendingFailureRetryAndNavigation() throws Throwable {
        SharedPreferences preferences=getInstrumentation().getTargetContext().getSharedPreferences("MainActivity",0);
        boolean hadWelcome=preferences.contains("welcome_done"),welcome=preferences.getBoolean("welcome_done",false);
        assertTrue(preferences.edit().putBoolean("welcome_done",true).commit());
        int[] friend={0};
        CountDownLatch consentRevoked=new CountDownLatch(1);
        Runnable revoked=()->{if(!injectedChat.hasConsent())consentRevoked.countDown();};
        try {
            activity=launch();awaitFocus();
            runTestOnUiThread(()->{
                fakeChat.immediate=false;injectedChat.setConsent(false);
                android.content.ContentValues values=new android.content.ContentValues();
                values.put("package","qa.gemini.synthetic."+System.nanoTime());
                values.put("name","Synthetic AI friend");values.put("personality","calm");
                friend[0]=(int)activity.db.insertOrThrow("friends",null,values);
                activity.chat(friend[0],"Synthetic AI friend");
                fakeChat.configured=false;activity.refreshChat();
                assertFalse(activity.findViewById(R.id.send_message).isEnabled());
                assertEquals(0,fakeChat.calls);
                fakeChat.configured=true;activity.refreshChat();
                ((EditText)activity.findViewById(R.id.message_input)).setText("Synthetic consent test");
                activity.findViewById(R.id.send_message).performClick();
                assertNotNull(activity.consentDialog);
                activity.consentDialog.getWindow().setWindowAnimations(0);
                assertEquals(0,fakeChat.calls);
                assertEquals(0,count("SELECT COUNT(*) FROM messages WHERE friend=?",""+friend[0]));
                activity.consentDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertFalse(injectedChat.hasConsent());
                assertEquals("Synthetic consent test",((EditText)activity.findViewById(R.id.message_input)).getText().toString());
                activity.findViewById(R.id.send_message).performClick();
                activity.consentDialog.getWindow().setWindowAnimations(0);
                activity.consentDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            });
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertEquals(1,fakeChat.calls);
                assertFalse(activity.findViewById(R.id.send_message).isEnabled());
                assertEquals(View.VISIBLE,activity.findViewById(R.id.ai_progress).getVisibility());
                assertEquals(1,count("SELECT COUNT(*) FROM messages WHERE friend=?",""+friend[0]));
                assertEquals(1,count("SELECT xp FROM friends WHERE id=?",""+friend[0]));
                ((EditText)activity.findViewById(R.id.message_input)).setText("Unsent AI draft");
            });
            capture("14-ai-pending");
            recreate();
            runTestOnUiThread(()->{
                assertEquals(1,fakeChat.calls);
                assertEquals("Unsent AI draft",((EditText)activity.findViewById(R.id.message_input)).getText().toString());
                assertFalse(activity.findViewById(R.id.send_message).isEnabled());
                activity.home();
                fakeChat.replies.get(0).setException(new IllegalStateException("Sensitive transport failure"));
                activity.chat(friend[0],"Synthetic AI friend");
                assertEquals(View.VISIBLE,activity.findViewById(R.id.retry_message).getVisibility());
                assertEquals(View.GONE,activity.findViewById(R.id.ai_progress).getVisibility());
                assertEquals(1,count("SELECT COUNT(*) FROM messages WHERE friend=?",""+friend[0]));
            });
            capture("15-ai-failed");
            runTestOnUiThread(()->{
                activity.findViewById(R.id.retry_message).performClick();
                activity.findViewById(R.id.retry_message).performClick();
                assertEquals(2,fakeChat.calls);
            });
            recreate();
            runTestOnUiThread(()->{
                assertEquals(2,fakeChat.calls);
                fakeChat.replies.get(1).set("Synthetic retry response");
                assertEquals(2,count("SELECT COUNT(*) FROM messages WHERE friend=?",""+friend[0]));
                assertEquals(1,count("SELECT xp FROM friends WHERE id=?",""+friend[0]));
                assertTrue(activity.findViewById(R.id.send_message).isEnabled());
                assertEquals(View.GONE,activity.findViewById(R.id.retry_message).getVisibility());
                injectedChat.addListener(revoked);
                activity.findViewById(R.id.ai_notice).performClick();
                activity.consentDialog.getWindow().setWindowAnimations(0);
                activity.consentDialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick();
            });
            try {
                assertTrue("Consent revocation must be applied",consentRevoked.await(5,TimeUnit.SECONDS));
            } finally {runTestOnUiThread(()->injectedChat.removeListener(revoked));}
            getInstrumentation().waitForIdleSync();
            capture("16-ai-retry-complete");
        } finally {
            if(activity!=null){
                runTestOnUiThread(()->{
                    if(friend[0]>0){
                        activity.db.delete("messages","friend=?",new String[]{""+friend[0]});
                        activity.db.delete("friends","id=?",new String[]{""+friend[0]});
                    }
                    activity.finish();
                });
                getInstrumentation().waitForIdleSync();
            }
            SharedPreferences.Editor editor=preferences.edit();
            if(hadWelcome)editor.putBoolean("welcome_done",welcome);else editor.remove("welcome_done");
            assertTrue(editor.commit());
        }
    }
}
