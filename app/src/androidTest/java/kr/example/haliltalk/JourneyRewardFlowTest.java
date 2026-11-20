package kr.example.haliltalk;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalStore;
import kr.example.haliltalk.data.JourneyStore;
import kr.example.haliltalk.data.QuestRewardStore;
import kr.example.haliltalk.shop.DemoShopStore;

/** Real controls and SQLite, using a borrowed fixture like ShopFlowTest, never guest coins. */
public final class JourneyRewardFlowTest extends GuestActivityTestCase {
    private SQLiteDatabase db;
    private JourneyActivity activity;
    private JourneyStore journeys;
    private DemoShopStore wallet;
    private String day;

    @Override protected void setUp() throws Exception {
        super.setUp();
        db=SQLiteDatabase.create(null);db.setForeignKeyConstraintsEnabled(true);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())) {
            helper.onCreate(db);
        }
        db.execSQL("INSERT INTO friends(id,package,name,xp) VALUES"
                +"(7,'qa.reward.flow','First',41),(9,'qa.reward.other','Other',3)");
        journeys=new JourneyStore(db);wallet=new DemoShopStore(db);day=LocalDate.now().toString();
    }

    @Override protected void tearDown() throws Exception {
        try{
            if(activity!=null)getInstrumentation().runOnMainSync(activity::finish);
            getInstrumentation().waitForIdleSync();db.close();
        }finally{super.tearDown();}
    }

    private void set(String name,Object value) {
        try {
            Field field=JourneyActivity.class.getDeclaredField(name);
            field.setAccessible(true);field.set(activity,value);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }

    private void render() {
        try {
            Method method=JourneyActivity.class.getDeclaredMethod("render");
            method.setAccessible(true);method.invoke(activity);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }

    private void borrowFixture() {
        set("db",db);set("store",journeys);set("rewards",new QuestRewardStore(db));set("friend",7);
        set("friends",new ArrayList<>(Arrays.asList(7,9)));
        Spinner spinner=activity.findViewById(R.id.journey_friend);
        spinner.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"First","Other"}));
        spinner.setSelection(0);render();
    }

    private void launch() {
        activity=(JourneyActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),JourneyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().runOnMainSync(this::borrowFixture);
        getInstrumentation().waitForIdleSync();
    }

    private void click(int id) {
        getInstrumentation().runOnMainSync(()->activity.findViewById(id).performClick());
        getInstrumentation().waitForIdleSync();
    }

    private void dismiss(AlertDialog dialog,int button) throws Exception {
        CountDownLatch dismissed=new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()->{
            dialog.setOnDismissListener(d->dismissed.countDown());
            dialog.getButton(button).performClick();
        });
        assertTrue("Dialog dismissal must finish",dismissed.await(5,TimeUnit.SECONDS));
        getInstrumentation().waitForIdleSync();
    }

    private void planAndReview(int outcome) throws Exception {
        click(R.id.journey_plan);
        getInstrumentation().runOnMainSync(()->((EditText)activity.editor
                .findViewById(R.id.journey_action_input)).setText("Read one page"));
        dismiss(activity.editor,AlertDialog.BUTTON_POSITIVE);
        assertEquals(500,wallet.balance());
        review(outcome);
    }

    private void review(int outcome) throws Exception {
        click(R.id.journey_review);
        getInstrumentation().runOnMainSync(()->((RadioGroup)activity.editor
                .findViewById(R.id.journey_outcome)).check(outcome));
        dismiss(activity.editor,AlertDialog.BUTTON_POSITIVE);
    }

    private void selectOtherFriend() throws Exception {
        CountDownLatch rendered=new CountDownLatch(1);
        TextView status=activity.findViewById(R.id.journey_reward_status);
        TextWatcher listener=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable text){rendered.countDown();}
        };
        getInstrumentation().runOnMainSync(()->{
            status.addTextChangedListener(listener);
            ((Spinner)activity.findViewById(R.id.journey_friend)).setSelection(1);
        });
        try{assertTrue("Selected friend must render",rendered.await(5,TimeUnit.SECONDS));}
        finally{getInstrumentation().runOnMainSync(()->status.removeTextChangedListener(listener));}
        getInstrumentation().waitForIdleSync();
    }

    private void attempt(int friend) {
        journeys.savePlan(friend,day,"Read one page",null,null,1);
        journeys.review(friend,day,"done","",2);
    }

    private void assertUnpaid() {
        assertEquals(500,wallet.balance());
        assertEquals(0L,DatabaseUtils.queryNumEntries(db,"quest_reward_receipts"));
        assertEquals(1,wallet.history().size());
    }

    public void testReviewCancelConfirmDeleteOtherFriendAndReopenPayOnlyOnce() throws Exception {
        long goal=new GoalStore(db).save(7,0,"Keep unfinished goal",null);
        launch();assertFalse(activity.findViewById(R.id.journey_reward).isEnabled());assertUnpaid();
        planAndReview(R.id.journey_rest);
        assertFalse(activity.findViewById(R.id.journey_reward).isEnabled());assertUnpaid();
        review(R.id.journey_partial);
        assertTrue(activity.findViewById(R.id.journey_reward).isEnabled());assertUnpaid();
        click(R.id.journey_reward);
        AlertDialog first=activity.rewardDialog;
        assertTrue(first.isShowing());assertUnpaid();
        click(R.id.journey_reward);assertSame(first,activity.rewardDialog);
        dismiss(first,AlertDialog.BUTTON_NEGATIVE);assertUnpaid();
        click(R.id.journey_reward);dismiss(activity.rewardDialog,AlertDialog.BUTTON_POSITIVE);
        assertEquals(520,wallet.balance());assertEquals(2,wallet.history().size());
        assertEquals("quest:"+day,wallet.history().get(0).eventKey);
        assertFalse(activity.findViewById(R.id.journey_reward).isEnabled());
        review(R.id.journey_done);assertEquals(520,wallet.balance());
        getInstrumentation().runOnMainSync(()->((LinearLayout)activity.findViewById(R.id.journey_history))
                .getChildAt(0).findViewById(R.id.journey_entry_delete).performClick());
        dismiss(activity.prompt,AlertDialog.BUTTON_POSITIVE);
        assertNull(journeys.entry(7,day));assertEquals(520,wallet.balance());
        attempt(9);selectOtherFriend();
        assertFalse(activity.findViewById(R.id.journey_reward).isEnabled());
        assertEquals(QuestRewardStore.Status.ALREADY_CLAIMED,new QuestRewardStore(db).status(9,day));
        getInstrumentation().runOnMainSync(activity::finish);getInstrumentation().waitForIdleSync();
        launch();assertEquals(520,wallet.balance());
        assertFalse(activity.findViewById(R.id.journey_reward).isEnabled());
        assertEquals(goal,new GoalStore(db).list(7).get(0).id);
        assertFalse(new GoalStore(db).list(7).get(0).completed);
        assertEquals(41L,DatabaseUtils.longForQuery(db,"SELECT xp FROM friends WHERE id=7",null));
        assertEquals(0L,DatabaseUtils.queryNumEntries(db,"purchases"));
    }

    public void testStaleFriendAndDateConfirmationCannotCredit() throws Exception {
        attempt(7);attempt(9);launch();click(R.id.journey_reward);
        selectOtherFriend();
        getInstrumentation().runOnMainSync(()->activity.rewardDialog
                .getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        assertTrue(activity.rewardDialog.isShowing());assertUnpaid();
        dismiss(activity.rewardDialog,AlertDialog.BUTTON_NEGATIVE);
        click(R.id.journey_reward);
        getInstrumentation().runOnMainSync(()->{
            activity.day=LocalDate.parse(day).minusDays(1).toString();
            activity.rewardDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        assertTrue(activity.rewardDialog.isShowing());assertUnpaid();
    }

    public void testStaleAccountConfirmationAndReentryCannotCreditOldSession() throws Exception {
        attempt(7);launch();click(R.id.journey_reward);
        getInstrumentation().runOnMainSync(()->{
            set("openedGeneration",AccountSession.generation()-1);
            activity.rewardDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        assertTrue(activity.rewardDialog.isShowing());assertUnpaid();
        dismiss(activity.rewardDialog,AlertDialog.BUTTON_NEGATIVE);
        click(R.id.journey_reward);
        assertFalse(activity.rewardDialog.isShowing());assertUnpaid();
    }

    public void testChangedReviewIsRecheckedInsideConfirmationTransaction() throws Exception {
        attempt(7);launch();click(R.id.journey_reward);
        journeys.review(7,day,"rest","Changed while dialog was open",3);
        dismiss(activity.rewardDialog,AlertDialog.BUTTON_POSITIVE);
        assertUnpaid();assertFalse(activity.findViewById(R.id.journey_reward).isEnabled());
    }

    public void testFailedLedgerKeepsConfirmationAndRetryCreditsOnce() throws Exception {
        attempt(7);launch();click(R.id.journey_reward);
        db.execSQL("CREATE TRIGGER qa_reward_fail BEFORE INSERT ON coin_ledger "
                +"WHEN NEW.source='quest' BEGIN SELECT RAISE(ABORT,'qa failure'); END");
        getInstrumentation().runOnMainSync(()->activity.rewardDialog
                .getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        assertTrue(activity.rewardDialog.isShowing());assertUnpaid();assertFalse(db.inTransaction());
        db.execSQL("DROP TRIGGER qa_reward_fail");
        dismiss(activity.rewardDialog,AlertDialog.BUTTON_POSITIVE);
        assertEquals(520,wallet.balance());assertEquals(2,wallet.history().size());
        assertFalse(new QuestRewardStore(db).claim(7,day));
    }

    public void testRotationDoesNotRestoreOrAcceptPendingRewardConfirmation() throws Exception {
        attempt(7);launch();click(R.id.journey_reward);assertUnpaid();
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                JourneyActivity.class.getName(),null,false);
        try {
            getInstrumentation().runOnMainSync(activity::recreate);
            activity=(JourneyActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(activity);
            getInstrumentation().runOnMainSync(this::borrowFixture);
            getInstrumentation().waitForIdleSync();
            assertNull(activity.rewardDialog);assertUnpaid();
            click(R.id.journey_reward);dismiss(activity.rewardDialog,AlertDialog.BUTTON_POSITIVE);
            assertEquals(520,wallet.balance());
        }finally{getInstrumentation().removeMonitor(monitor);}
    }

    public void testShopHistoryShowsRewardLabelAmountAndActualDayWithoutWriting() throws Exception {
        attempt(7);assertTrue(new QuestRewardStore(db).claim(7,day));
        ShopActivity shop=(ShopActivity)getInstrumentation().startActivitySync(new Intent(
                getInstrumentation().getTargetContext(),ShopActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        DemoShopStore original=shop.store;
        try {
            getInstrumentation().runOnMainSync(()->{
                shop.store=wallet;shop.showHistory();
                ListView list=(ListView)shop.historyDialog.findViewById(R.id.shop_history_list);
                assertNotNull(list);
                View row=list.getAdapter().getView(0,null,list);
                assertEquals("quest:"+day,row.getTag());
                assertEquals(shop.getString(R.string.shop_history_quest,day),
                        ((TextView)row.findViewById(android.R.id.text1)).getText().toString());
                String detail=((TextView)row.findViewById(android.R.id.text2)).getText().toString();
                assertTrue(detail.contains(shop.getString(R.string.shop_history_change,20,520)));
                assertFalse(detail.contains(shop.getString(R.string.shop_history_unknown_date)));
            });
            dismiss(shop.historyDialog,AlertDialog.BUTTON_POSITIVE);
            assertEquals(520,wallet.balance());assertEquals(2,wallet.history().size());
        }finally{
            getInstrumentation().runOnMainSync(()->{shop.store=original;shop.finish();});
            getInstrumentation().waitForIdleSync();
        }
    }
}
