package kr.example.haliltalk;

import android.app.AlertDialog;
import android.database.DatabaseUtils;
import android.widget.LinearLayout;
import android.widget.Spinner;

public final class CollectionFlowTest extends ShopFixtureTest {
    public void testWishlistSaveReplaceCancelRevealClearNeverSpendsCoins() throws Throwable {
        clickHeader(R.id.collection_set);assertEquals("crown",wishlist.target());
        runTestOnUiThread(()->{activity.selected="rocket_pack";activity.refresh();});
        clickHeader(R.id.collection_set);
        assertEquals("crown",wishlist.target());
        runTestOnUiThread(()->activity.collectionDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        getInstrumentation().waitForIdleSync();
        assertEquals("crown",wishlist.target());assertEquals("rocket_pack",activity.selected);
        clickHeader(R.id.collection_set);
        runTestOnUiThread(()->activity.collectionDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        assertEquals("rocket_pack",wishlist.target());
        filter(R.id.shop_theme,1);filter(R.id.shop_filter,1);
        runTestOnUiThread(()->((LinearLayout)activity.header.findViewById(R.id.shop_categories))
                .getChildAt(1).performClick());
        clickHeader(R.id.collection_reveal);
        runTestOnUiThread(()->{
            assertEquals("rocket_pack",activity.selected);
            assertEquals(0,activity.theme);assertEquals(0,activity.category);assertEquals(0,activity.ownership);
            assertEquals(0,((Spinner)activity.header.findViewById(R.id.shop_filter)).getSelectedItemPosition());
            assertEquals(500,wallet.balance());assertFalse(wallet.owns("rocket_pack"));
        });
        clickHeader(R.id.collection_clear);
        runTestOnUiThread(()->activity.collectionDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        getInstrumentation().waitForIdleSync();assertEquals("rocket_pack",wishlist.target());
        clickHeader(R.id.collection_clear);
        runTestOnUiThread(()->activity.collectionDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();assertNull(wishlist.target());
        assertEquals(500,wallet.balance());
        assertEquals(0L,DatabaseUtils.queryNumEntries(fixture,"purchases"));
        assertEquals(0L,DatabaseUtils.queryNumEntries(fixture,"appearance"));
    }

    public void testWriteFailureAndStaleAccountConfirmationKeepPreviousTarget() throws Throwable {
        clickHeader(R.id.collection_set);
        fixture.execSQL("CREATE TRIGGER reject_wishlist AFTER INSERT ON shop_wishlist"
                +" BEGIN SELECT RAISE(FAIL,'synthetic UI failure'); END");
        runTestOnUiThread(()->{activity.selected="rocket_pack";activity.refresh();});
        clickHeader(R.id.collection_set);
        runTestOnUiThread(()->activity.collectionDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        getInstrumentation().waitForIdleSync();
        assertEquals("crown",wishlist.target());assertEquals("rocket_pack",activity.selected);
        fixture.execSQL("DROP TRIGGER reject_wishlist");
        clickHeader(R.id.collection_set);
        long generation=activity.openedGeneration;
        java.util.concurrent.CountDownLatch dismissed=new java.util.concurrent.CountDownLatch(1);
        runTestOnUiThread(()->{
            // Invalidate only this screen's captured epoch, not the real user's session.
            activity.collectionDialog.setOnDismissListener(dialog->dismissed.countDown());
            activity.openedGeneration=generation-1;
            activity.collectionDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        try {
            assertTrue(dismissed.await(5,java.util.concurrent.TimeUnit.SECONDS));
            getInstrumentation().waitForIdleSync();
            assertEquals("crown",wishlist.target());assertEquals(500,wallet.balance());
        } finally {
            runTestOnUiThread(()->activity.openedGeneration=generation);
        }
        assertEquals(0L,DatabaseUtils.queryNumEntries(fixture,"purchases"));
    }

    public void testWishlistSurvivesRecreationAndOwnedTargetCanBePreviewed() throws Throwable {
        runTestOnUiThread(()->{activity.selected="beret";activity.refresh();});
        clickHeader(R.id.collection_set);
        recreateShop();
        assertEquals("beret",wishlist.target());
        assertEquals("beret",activity.collectionTarget);
        clickHeader(R.id.collection_reveal);
        runTestOnUiThread(()->{
            assertEquals("beret",activity.selected);
            assertTrue(activity.findViewById(R.id.shop_buy).isEnabled());
            assertEquals(activity.getString(R.string.shop_try_on),
                    ((android.widget.Button)activity.findViewById(R.id.shop_buy)).getText().toString());
            assertEquals(500,wallet.balance());
        });
    }
}
