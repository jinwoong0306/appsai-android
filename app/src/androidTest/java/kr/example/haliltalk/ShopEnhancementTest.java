package kr.example.haliltalk;

import android.app.Instrumentation;
import android.content.Intent;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.shop.DemoShopStore;
import kr.example.haliltalk.shop.ShopCollectionStore;

public final class ShopEnhancementTest extends ShopFixtureTest {
    public void testIntersectingFiltersEmptyStateAndHeaderRowSelection() throws Throwable {
        runTestOnUiThread(()->activity.header.findViewById(R.id.shop_filters_toggle).performClick());
        filter(R.id.shop_theme,4);
        runTestOnUiThread(()->((LinearLayout)activity.header.findViewById(R.id.shop_categories))
                .getChildAt(4).performClick());
        filter(R.id.shop_filter,1);
        runTestOnUiThread(()->{
            assertTrue(activity.visibleItems.isEmpty());
            assertNull(activity.selected);
            assertFalse(activity.findViewById(R.id.shop_buy).isEnabled());
            assertEquals(View.VISIBLE,activity.header.findViewById(R.id.shop_empty).getVisibility());
            activity.confirmPurchase();assertNull(activity.purchaseDialog);
        });
        filter(R.id.shop_filter,3);
        runTestOnUiThread(()->{
            assertEquals(3,activity.visibleItems.size());
            assertEquals("backpack",activity.selected);
            ListView list=activity.findViewById(R.id.shop_list);
            int index=activity.visibleItems.indexOf("rocket_pack");
            list.performItemClick(activity.adapter.getView(index,null,list),
                    index+list.getHeaderViewsCount(),index);
            assertEquals("rocket_pack",activity.selected);
            assertEquals("rocket_pack",activity.findViewById(R.id.shop_action_item).getTag());
            assertTrue(activity.findViewById(R.id.shop_buy).isEnabled());
            assertEquals(500,wallet.balance());
            assertEquals(0L,DatabaseUtils.queryNumEntries(fixture,"appearance"));
        });
    }

    public void testFiltersBaseAndSelectionSurviveRecreationWithoutPurchase() throws Throwable {
        filter(R.id.shop_theme,1);
        filter(R.id.shop_filter,1);
        runTestOnUiThread(()->{
            activity.base="reading";activity.selected="beret";
            activity.filtersOpen=true;activity.collectionWishlistOpen=true;activity.refresh();
        });
        recreateShop();
        runTestOnUiThread(()->{
            assertEquals(1,activity.theme);assertEquals(1,activity.ownership);
            assertEquals("reading",activity.base);assertEquals("beret",activity.selected);
            assertTrue(activity.filtersOpen);assertTrue(activity.collectionWishlistOpen);
            assertEquals(1,((Spinner)activity.header.findViewById(R.id.shop_theme)).getSelectedItemPosition());
            assertEquals(1,((Spinner)activity.header.findViewById(R.id.shop_filter)).getSelectedItemPosition());
            assertEquals(500,wallet.balance());
            assertEquals(0L,DatabaseUtils.queryNumEntries(fixture,"purchases"));
        });
    }

    public void testPurchaseStillRequiresConfirmationAfterFiltering() throws Throwable {
        filter(R.id.shop_filter,2);
        runTestOnUiThread(()->{
            activity.selected="crown";activity.refresh();
            activity.findViewById(R.id.shop_buy).performClick();
            assertTrue(activity.purchaseDialog.isShowing());
            activity.purchaseDialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(500,wallet.balance());assertFalse(wallet.owns("crown"));
        runTestOnUiThread(()->{
            activity.findViewById(R.id.shop_buy).performClick();
            activity.purchaseDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            assertEquals(410,wallet.balance());assertTrue(wallet.owns("crown"));
            assertFalse(activity.visibleItems.contains("crown"));
            assertEquals(1L,DatabaseUtils.queryNumEntries(fixture,"purchases"));
        });
    }
}

/** Real controls and SQLite, with only the stores redirected away from user purchases. */
abstract class ShopFixtureTest extends GuestActivityTestCase {
    ShopActivity activity;
    SQLiteDatabase fixture;
    DemoShopStore wallet;
    ShopCollectionStore wishlist;

    @Override protected void setUp() throws Exception {
        super.setUp();
        fixture=SQLiteDatabase.create(null);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            helper.onCreate(fixture);
        }
        wallet=new DemoShopStore(fixture);wishlist=new ShopCollectionStore(fixture);
        activity=(ShopActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),ShopActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        installStores();
    }

    void installStores() {
        getInstrumentation().runOnMainSync(()->{
            activity.store=wallet;activity.collectionStore=wishlist;activity.refresh();
        });
        getInstrumentation().waitForIdleSync();
    }

    void filter(int id,int value) throws Throwable {
        runTestOnUiThread(()->{
            if(!activity.filtersOpen)activity.header.findViewById(R.id.shop_filters_toggle).performClick();
            ((ListView)activity.findViewById(R.id.shop_list)).setSelection(0);
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->((Spinner)activity.header.findViewById(id)).setSelection(value));
        getInstrumentation().waitForIdleSync();
    }

    void clickHeader(int id) throws Throwable {
        runTestOnUiThread(()->assertTrue(activity.header.findViewById(id).performClick()));
        getInstrumentation().waitForIdleSync();
    }

    void recreateShop() throws Throwable {
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(
                ShopActivity.class.getName(),null,false);
        try{
            runTestOnUiThread(()->activity.recreate());
            activity=(ShopActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull("Shop recreation must finish",activity);
            installStores();
        }finally{getInstrumentation().removeMonitor(monitor);}
    }

    @Override protected void tearDown() throws Exception {
        try{
            if(activity!=null){
                getInstrumentation().runOnMainSync(()->{
                    ((ListView)activity.findViewById(R.id.shop_list)).setAdapter(null);
                    activity.store=new DemoShopStore(activity.db);
                    activity.collectionStore=new ShopCollectionStore(activity.db);
                    activity.finish();
                });
                getInstrumentation().waitForIdleSync();
            }
            if(fixture!=null)fixture.close();
        }finally{super.tearDown();}
    }
}
