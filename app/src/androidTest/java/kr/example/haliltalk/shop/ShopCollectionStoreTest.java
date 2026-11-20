package kr.example.haliltalk.shop;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.test.InstrumentationTestCase;
import java.io.File;
import java.lang.reflect.Method;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;

public final class ShopCollectionStoreTest extends InstrumentationTestCase {
    private SQLiteDatabase fixture() {
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try(FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
            helper.onCreate(db);
        }
        return db;
    }

    public void testSetReplaceAndClearNeverPurchaseOrEquip() {
        try(SQLiteDatabase db=fixture()){
            ShopCollectionStore store=new ShopCollectionStore(db);
            DemoShopStore wallet=new DemoShopStore(db);
            assertNull(store.target());
            store.setTarget("crown");assertEquals("crown",store.target());
            store.setTarget("rocket_pack");assertEquals("rocket_pack",store.target());
            assertEquals(1L,DatabaseUtils.queryNumEntries(db,"shop_wishlist"));
            assertEquals(500,wallet.balance());assertFalse(wallet.owns("rocket_pack"));
            assertFalse(wallet.owns("crown"));
            assertEquals(0L,DatabaseUtils.queryNumEntries(db,"purchases"));
            assertEquals(0L,DatabaseUtils.queryNumEntries(db,"appearance"));
            store.clear();store.clear();assertNull(store.target());
            assertEquals(500,wallet.balance());
        }
    }

    public void testInvalidTargetsAndFailTriggersPreservePreviousTarget() {
        try(SQLiteDatabase db=fixture()){
            ShopCollectionStore store=new ShopCollectionStore(db);
            store.setTarget("crown");
            for(String invalid:new String[]{null,"none","unknown",""}){
                try{store.setTarget(invalid);fail("Invalid target must be rejected");}
                catch(IllegalArgumentException expected){assertEquals("crown",store.target());}
            }
            db.execSQL("CREATE TRIGGER reject_target AFTER INSERT ON shop_wishlist"
                    +" BEGIN SELECT RAISE(FAIL,'synthetic write failure'); END");
            try{store.setTarget("rocket_pack");fail("Write must fail");}
            catch(SQLiteException expected){assertEquals("crown",store.target());}
            db.execSQL("DROP TRIGGER reject_target");
            db.execSQL("CREATE TRIGGER reject_clear AFTER DELETE ON shop_wishlist"
                    +" BEGIN SELECT RAISE(FAIL,'synthetic delete failure'); END");
            try{store.clear();fail("Delete must fail");}
            catch(SQLiteException expected){assertEquals("crown",store.target());}
            assertEquals(500,new DemoShopStore(db).balance());
        }
    }

    public void testGuestAndUidDatabaseFilesKeepIndependentTargetsAcrossReopen() throws Exception {
        // Use the real U4 filename resolver, without signing into or changing any account.
        Method filename=AccountSession.class.getDeclaredMethod("databaseNameForUid",String.class);
        filename.setAccessible(true);
        File directory=new File(getInstrumentation().getTargetContext().getCacheDir(),
                "wishlist-"+java.util.UUID.randomUUID());
        assertTrue(directory.mkdir());
        String[] uids={null,"qa/wishlist-a","qa/wishlist-b"};
        String[] targets={"crown","rocket_pack","beanie"};
        try{
            for(int i=0;i<uids.length;i++){
                File file=new File(directory,(String)filename.invoke(null,uids[i]));
                try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null);
                        FriendsDatabase helper=new FriendsDatabase(getInstrumentation().getTargetContext())){
                    helper.onCreate(db);
                    ShopCollectionStore store=new ShopCollectionStore(db);
                    assertNull(store.target());store.setTarget(targets[i]);
                    assertEquals(500,new DemoShopStore(db).balance());
                }
            }
            for(int i=0;i<uids.length;i++){
                File file=new File(directory,(String)filename.invoke(null,uids[i]));
                try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(file,null)){
                    assertEquals(targets[i],new ShopCollectionStore(db).target());
                    assertEquals(0L,DatabaseUtils.queryNumEntries(db,"purchases"));
                }
            }
        }finally{
            for(String uid:uids)SQLiteDatabase.deleteDatabase(new File(directory,(String)filename.invoke(null,uid)));
            assertTrue(directory.delete());
        }
    }
}
