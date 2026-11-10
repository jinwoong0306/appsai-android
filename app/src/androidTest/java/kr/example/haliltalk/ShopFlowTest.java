package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import kr.example.haliltalk.shop.DemoShopStore;

public final class ShopFlowTest extends InstrumentationTestCase {
    public void testPreviewCancelConfirmAndOwnedState() throws Throwable {
        try(SQLiteDatabase fixture=SQLiteDatabase.create(null)){
            fixture.execSQL("CREATE TABLE appearance(hat TEXT,face TEXT,body TEXT,back TEXT,prop TEXT)");
            DemoShopStore store=new DemoShopStore(fixture);
            ShopActivity activity=(ShopActivity)getInstrumentation().startActivitySync(new Intent()
                    .setClassName(getInstrumentation().getTargetContext(),ShopActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            DemoShopStore originalStore=activity.store;
            try {
                getInstrumentation().waitForIdleSync();
                runTestOnUiThread(()->{
                    // Exercise real controls + real SQLite transactions without spending user coins.
                    activity.store=store;activity.selected="crown";activity.refresh();
                    assertEquals(30,activity.adapter.getCount());
                    assertTrue(activity.findViewById(R.id.shop_buy).isEnabled());
                    activity.findViewById(R.id.shop_buy).performClick();
                    assertTrue(activity.purchaseDialog.isShowing());
                    activity.purchaseDialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
                });
                getInstrumentation().waitForIdleSync();
                runTestOnUiThread(()->{
                    assertEquals(500,store.balance());assertFalse(store.owns("crown"));
                    activity.findViewById(R.id.shop_buy).performClick();
                    activity.purchaseDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                });
                getInstrumentation().waitForIdleSync();
                runTestOnUiThread(()->{
                    assertEquals(410,store.balance());assertTrue(store.owns("crown"));
                    assertFalse(activity.findViewById(R.id.shop_buy).isEnabled());
                    activity.confirmPurchase();
                    assertEquals(410,store.balance());
                    activity.selected="rocket_pack";activity.refresh();
                    assertEquals(410,store.balance());assertFalse(store.owns("rocket_pack"));
                });
            } finally {
                runTestOnUiThread(()->{
                    // Remove every UI consumer before the borrowed fixture is closed.
                    ((android.widget.ListView)activity.findViewById(R.id.shop_list)).setAdapter(null);
                    activity.store=originalStore;
                    activity.finish();
                });
            }
        }
    }
}
