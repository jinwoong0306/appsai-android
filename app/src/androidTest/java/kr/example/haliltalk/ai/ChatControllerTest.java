package kr.example.haliltalk.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import android.test.RenamingDelegatingContext;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.data.FriendsDatabase;

public class ChatControllerTest extends InstrumentationTestCase {
    Context context;
    FriendsDatabase helper;
    SQLiteDatabase db;
    ChatController controller;
    FakeService service;
    String databaseName;

    static final class FakeService implements AiChatService {
        boolean configured=true;
        int calls;
        String personality;
        List<Turn> turns;
        final List<SettableFuture<String>> replies=new ArrayList<>();
        @Override public boolean isConfigured(){return configured;}
        @Override public ListenableFuture<String> generate(String personality,List<Turn> turns){
            calls++;this.personality=personality;this.turns=turns;
            SettableFuture<String> future=SettableFuture.create();replies.add(future);return future;
        }
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context base=getInstrumentation().getTargetContext();
        String prefix="qa_chat_"+getName()+"_";
        databaseName=prefix+"friends.db";
        // RenamingDelegatingContext does not reliably delete a pre-existing DB namespace.
        base.deleteDatabase(databaseName);
        context=new RenamingDelegatingContext(base,prefix){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                return base.getSharedPreferences(prefix+name,mode);
            }
        };
        context.getSharedPreferences("ai_chat_consent",0).edit().clear().commit();
        helper=new FriendsDatabase(context);db=helper.getWritableDatabase();
        db.execSQL("INSERT INTO friends(id,package,name,personality,xp) VALUES(7,'private.package','Private name','calm',41)");
        db.execSQL("INSERT INTO friends(id,package,name,personality) VALUES(9,'other.package','Other','kind')");
        db.execSQL("INSERT INTO messages(friend,content,is_user,created) VALUES(7,'Old canned reply',0,1)");
        db.execSQL("INSERT INTO messages(friend,content,is_user,created) VALUES(9,'Other private chat',1,1)");
        service=new FakeService();
        getInstrumentation().runOnMainSync(()->controller=new ChatController(context,service));
    }

    @Override protected void tearDown() throws Exception {
        try {
            getInstrumentation().runOnMainSync(()->controller.close());
            helper.close();getInstrumentation().getTargetContext().deleteDatabase(databaseName);
            context.getSharedPreferences("ai_chat_consent",0).edit().clear().commit();
        } finally {super.tearDown();}
    }

    int count(String sql){
        try(Cursor cursor=db.rawQuery(sql,null)){assertTrue(cursor.moveToFirst());return cursor.getInt(0);}
    }

    public void testConsentConfigurationAndLengthGateTransmission() throws Throwable {
        runTestOnUiThread(()->{
            assertFalse(controller.send(7,"Synthetic message"));
            assertEquals(0,service.calls);
            controller.setConsent(true);service.configured=false;
            assertFalse(controller.send(7,"Synthetic message"));
            service.configured=true;
            assertFalse(controller.send(7,"x".repeat(ChatController.MAX_MESSAGE_CHARS+1)));
            assertFalse(controller.send(7,"   "));
            assertEquals(41,count("SELECT xp FROM friends WHERE id=7"));
            assertEquals(0,count("SELECT COUNT(*) FROM chat_requests"));
        });
    }

    public void testFailureRetrySuccessKeepsOneMessageAndOneXpIncrement() throws Throwable {
        runTestOnUiThread(()->{
            controller.setConsent(true);
            assertTrue(controller.send(7,"Synthetic message"));
            assertEquals(1,service.calls);
            assertEquals("calm",service.personality);
            assertEquals(1,service.turns.size());
            assertEquals("Synthetic message",service.turns.get(0).text);
            assertTrue(service.turns.get(0).user);
            assertFalse(controller.send(7,"Duplicate"));
            controller.retry(7);assertEquals(1,service.calls);
            service.replies.get(0).setException(new IllegalStateException("Secret provider details"));
            assertEquals("failed",controller.openRequest(7).state);
            assertEquals("provider",controller.openRequest(7).error);
            assertEquals(2,count("SELECT COUNT(*) FROM messages WHERE friend=7"));
            assertEquals(42,count("SELECT xp FROM friends WHERE id=7"));
            controller.setConsent(false);controller.retry(7);assertEquals(1,service.calls);
            controller.setConsent(true);controller.retry(7);controller.retry(7);
            assertEquals(2,service.calls);assertEquals(1,service.turns.size());
            service.replies.get(1).set("Synthetic reply");
            assertFalse(service.replies.get(1).set("Duplicate reply"));
            assertNull(controller.openRequest(7));controller.retry(7);
            assertEquals(2,service.calls);
            assertEquals(3,count("SELECT COUNT(*) FROM messages WHERE friend=7"));
            assertEquals(1,count("SELECT COUNT(*) FROM chat_requests WHERE state='complete'"));
            assertEquals(42,count("SELECT xp FROM friends WHERE id=7"));
        });
    }

    public void testInterruptedRequestNeedsExplicitRetryAndIgnoresOldResult() throws Throwable {
        runTestOnUiThread(()->{
            controller.setConsent(true);assertTrue(controller.send(7,"Synthetic interrupted request"));
            // A second process owner recovers durable pending state, without any automatic network request.
            try(ChatController recovered=new ChatController(context,service)){
                assertEquals("failed",recovered.openRequest(7).state);
                assertEquals("interrupted",recovered.openRequest(7).error);
                assertEquals(1,service.calls);
                recovered.retry(7);assertEquals(2,service.calls);
                service.replies.get(0).set("Stale old response");
                assertEquals("pending",recovered.openRequest(7).state);
                assertEquals(0,count("SELECT COUNT(*) FROM messages WHERE content='Stale old response'"));
                service.replies.get(1).set("Recovered response");
                assertNull(recovered.openRequest(7));
                assertEquals(1,count("SELECT COUNT(*) FROM messages WHERE content='Recovered response'"));
                assertEquals(42,count("SELECT xp FROM friends WHERE id=7"));
            }
        });
    }

    public void testEmptyResponseCancellationAndBoundedContext() throws Throwable {
        runTestOnUiThread(()->{
            controller.setConsent(true);assertTrue(controller.send(7,"Synthetic abandoned request"));
            service.replies.get(0).set("");
            assertEquals("failed",controller.openRequest(7).state);
            assertEquals(0,count("SELECT COUNT(*) FROM chat_requests WHERE state='complete'"));
            controller.cancelFailed(7);assertNull(controller.openRequest(7));
            for(int i=0;i<14;i++){
                assertTrue(controller.send(7,("Synthetic "+i+" ").repeat(90)));
                int chars=0;for(AiChatService.Turn turn:service.turns)chars+=turn.text.length();
                assertTrue(service.turns.size()<=ChatController.MAX_CONTEXT_TURNS);
                assertTrue(chars<=ChatController.MAX_CONTEXT_CHARS);
                assertTrue(service.turns.get(0).user);
                assertTrue(service.turns.get(service.turns.size()-1).user);
                for(AiChatService.Turn turn:service.turns){
                    assertFalse(turn.text.contains("Old canned reply"));
                    assertFalse(turn.text.contains("Other private chat"));
                    assertFalse(turn.text.contains("Synthetic abandoned request"));
                }
                service.replies.get(service.replies.size()-1).set(("Synthetic reply "+i+" ").repeat(90));
            }
            assertEquals(56,count("SELECT xp FROM friends WHERE id=7"));
            assertEquals(14,count("SELECT COUNT(*) FROM chat_requests WHERE state='complete'"));
        });
    }

    public void testBackgroundProviderCompletionNotifiesOnMainThread() throws Exception {
        CountDownLatch completed=new CountDownLatch(1);
        boolean[] main={false};
        getInstrumentation().runOnMainSync(()->{
            controller.setConsent(true);controller.send(7,"Synthetic background request");
            controller.addListener(()->{
                if(controller.openRequest(7)==null){
                    main[0]=android.os.Looper.myLooper()==android.os.Looper.getMainLooper();
                    completed.countDown();
                }
            });
        });
        // Test thread acts as the provider worker; the listener was subscribed before completion.
        service.replies.get(0).set("Synthetic background response");
        assertTrue(completed.await(5,TimeUnit.SECONDS));
        assertTrue(main[0]);
        assertEquals(1,count("SELECT COUNT(*) FROM chat_requests WHERE state='complete'"));
    }
}
