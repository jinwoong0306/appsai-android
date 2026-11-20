package kr.example.haliltalk.shop;

import android.test.InstrumentationTestCase;
import java.util.HashSet;
import kr.example.haliltalk.character.CharacterCatalog;

public final class ShopCatalogTest extends InstrumentationTestCase {
    public void testEveryStableAccessoryHasExactlyOneTheme() {
        HashSet<String> ids=new HashSet<>();
        int[] counts=new int[5];
        for(String id:CharacterCatalog.allAccessoryIds()){
            assertTrue(ids.add(id));
            int theme=ShopCatalog.themeFor(id);
            assertTrue(theme>=1&&theme<=4);
            counts[theme]++;
            for(int filter=1;filter<=4;filter++)
                assertEquals(filter==theme,ShopCatalog.matches(id,filter,0,0,false,90,500));
        }
        assertEquals(30,ids.size());
        assertEquals(6,counts[1]);assertEquals(7,counts[2]);
        assertEquals(11,counts[3]);assertEquals(6,counts[4]);
        assertEquals(0,ShopCatalog.themeFor(null));
        assertEquals(0,ShopCatalog.themeFor("unknown"));
    }

    public void testThemeSlotOwnershipAndAffordabilityIntersect() {
        assertTrue(ShopCatalog.matches("crown",3,1,2,false,90,500));
        assertFalse(ShopCatalog.matches("crown",1,1,2,false,90,500));
        assertFalse(ShopCatalog.matches("crown",3,2,2,false,90,500));
        assertFalse(ShopCatalog.matches("crown",3,1,2,true,90,500));
        assertTrue(ShopCatalog.matches("crown",3,1,1,true,90,0));
        assertTrue(ShopCatalog.matches("crown",3,1,3,false,90,90));
        assertFalse(ShopCatalog.matches("crown",3,1,3,false,90,89));
        assertFalse(ShopCatalog.matches("crown",3,1,3,true,90,500));
        assertFalse(ShopCatalog.matches("none",0,0,0,true,0,500));
        assertFalse(ShopCatalog.matches("unknown",0,0,0,false,-1,500));
        assertFalse(ShopCatalog.matches("crown",5,0,0,false,90,500));
        assertFalse(ShopCatalog.matches("crown",0,6,0,false,90,500));
        assertFalse(ShopCatalog.matches("crown",0,0,4,false,90,500));
    }
}
