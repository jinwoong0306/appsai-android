package kr.example.haliltalk;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import android.widget.LinearLayout;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.character.CharacterView;

public final class CustomizationFlowTest extends InstrumentationTestCase {
    public void testAllSlotsCancelSaveAndReopen() throws Throwable {
        SQLiteDatabase db=new FriendsDatabase(getInstrumentation().getTargetContext()).getWritableDatabase();
        int id=0;
        java.util.ArrayList<String> fixtureItems=new java.util.ArrayList<>();
        CustomizationActivity[] activity={null};
        try {
            ContentValues values=new ContentValues();
            values.put("package","test.appsai.wardrobe."+System.nanoTime());
            values.put("name","꾸미기 검증 친구");values.put("personality","다정한 친구");values.put("xp",7);
            id=(int)db.insertOrThrow("friends",null,values);
            final int friend=id;
            activity[0]=(CustomizationActivity)getInstrumentation().startActivitySync(new Intent()
                    .setClassName(getInstrumentation().getTargetContext(),CustomizationActivity.class.getName())
                    .putExtra("friend_id",friend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertEquals(30,CharacterCatalog.allAccessoryIds().length);
                CharacterView preview=activity[0].findViewById(R.id.wardrobe_preview);
                for(String item:CharacterCatalog.allAccessoryIds()){
                    String slot=CharacterCatalog.slotFor(item);
                    preview.setAppearance("chat","hat".equals(slot)?item:"none","face".equals(slot)?item:"none",
                            "body".equals(slot)?item:"none","back".equals(slot)?item:"none","prop".equals(slot)?item:"none");
                }
                activity[0].appearance=new String[]{"video","crown","star_glasses","red_scarf","angel_wings","gamepad"};
                activity[0].render();
                activity[0].findViewById(R.id.wardrobe_cancel).performClick();
                assertEquals("none",activity[0].appearance[1]);
            });
            try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM appearance WHERE friend=?",new String[]{""+id})){
                assertTrue(c.moveToFirst());assertEquals(0,c.getInt(0));
            }
            runTestOnUiThread(()->{
                // This fixture owns the outfit; purchase atomicity is tested independently.
                for(String item:new String[]{"crown","star_glasses","red_scarf","angel_wings"}){
                    ContentValues owned=new ContentValues();owned.put("item_id",item);
                    if(activity[0].db.insertWithOnConflict("owned_items",null,owned,SQLiteDatabase.CONFLICT_IGNORE)!=-1)fixtureItems.add(item);
                }
                activity[0].appearance=new String[]{"video","crown","star_glasses","red_scarf","angel_wings","gamepad"};
                activity[0].render();
                activity[0].findViewById(R.id.wardrobe_save).performClick();
            });
            try(Cursor c=db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",new String[]{""+id})){
                assertTrue(c.moveToFirst());
                String[] expected={"video","crown","star_glasses","red_scarf","angel_wings","gamepad"};
                for(int i=0;i<6;i++)assertEquals(expected[i],c.getString(i));
            }
            runTestOnUiThread(()->activity[0].finish());getInstrumentation().waitForIdleSync();
            activity[0]=(CustomizationActivity)getInstrumentation().startActivitySync(new Intent()
                    .setClassName(getInstrumentation().getTargetContext(),CustomizationActivity.class.getName())
                    .putExtra("friend_id",friend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertEquals("angel_wings",activity[0].appearance[4]);
                assertEquals("gamepad",activity[0].appearance[5]);
                assertFalse(activity[0].findViewById(R.id.wardrobe_save).isEnabled());
            });
            try(Cursor c=db.rawQuery("SELECT xp FROM friends WHERE id=?",new String[]{""+id})){
                assertTrue(c.moveToFirst());assertEquals(7,c.getInt(0));
            }
        } finally {
            if(activity[0]!=null)runTestOnUiThread(()->activity[0].finish());
            if(id>0)db.delete("friends","id=?",new String[]{""+id});
            for(String item:fixtureItems)db.delete("owned_items","item_id=?",new String[]{item});
            db.close();
        }
    }

    public void testSettingsActionsAreMovedOffHome() throws Throwable {
        SettingsActivity activity=(SettingsActivity)getInstrumentation().startActivitySync(new Intent()
                .setClassName(getInstrumentation().getTargetContext(),SettingsActivity.class.getName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        try {
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertNotNull(activity.findViewById(R.id.usage_settings));
                activity.findViewById(R.id.add_friend).performClick();
            });
            MainActivity main=(MainActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,5000);
            assertNotNull(main);getInstrumentation().waitForIdleSync();
            runTestOnUiThread(()->{
                assertTrue(main.showingApps);main.home();
                assertNull(main.findViewById(R.id.add_friend));
                assertNull(main.findViewById(R.id.usage_settings));
                assertNotNull(main.findViewById(R.id.open_settings));main.finish();
            });
        } finally {
            getInstrumentation().removeMonitor(monitor);
            runTestOnUiThread(activity::finish);
        }
    }
}
