package kr.example.haliltalk;

import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;
import kr.example.haliltalk.auth.AccountSession;

/** 화면 회귀는 명시적 게스트 상태에서만 실행하고 원래 진입 설정을 복원한다. */
abstract class GuestActivityTestCase extends InstrumentationTestCase {
    private SharedPreferences guest;
    private boolean hadGuest, entered;
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
    @Override protected void setUp() throws Exception {
        super.setUp();
        assertNull("Do not switch a real signed-in account for UI tests",
                AccountSession.user(getInstrumentation().getTargetContext()));
        guest=getInstrumentation().getTargetContext().getSharedPreferences("account_session",0);
        hadGuest=guest.contains("guest_entered");entered=guest.getBoolean("guest_entered",false);
        assertTrue(guest.edit().putBoolean("guest_entered",true).commit());
    }
    @Override protected void tearDown() throws Exception {
        try {
            SharedPreferences.Editor edit=guest.edit();
            if(hadGuest)edit.putBoolean("guest_entered",entered);else edit.remove("guest_entered");
            assertTrue(edit.commit());
        } finally {super.tearDown();}
    }
}
