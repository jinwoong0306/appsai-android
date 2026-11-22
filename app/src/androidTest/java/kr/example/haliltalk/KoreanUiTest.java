package kr.example.haliltalk;

import android.test.InstrumentationTestCase;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

public final class KoreanUiTest extends InstrumentationTestCase {
    private long at(int year,int month,int day,int hour,int minute,ZoneId zone){
        return ZonedDateTime.of(year,month,day,hour,minute,0,0,zone).toInstant().toEpochMilli();
    }
    public void testKoreanTimesDoNotDependOnEnglishDeviceLocale(){
        Locale original=Locale.getDefault();
        try{
            Locale.setDefault(Locale.US);
            ZoneId zone=ZoneId.of("Asia/Seoul");long now=at(2026,11,9,18,0,zone);
            assertEquals("오전 9:38",KoreanUi.messageTime(at(2026,11,9,9,38,zone),now,zone,false));
            assertEquals("어제 오후 9:38",KoreanUi.messageTime(at(2026,11,8,21,38,zone),now,zone,false));
            assertEquals("11월 2일 · 오전 9:38",KoreanUi.messageTime(at(2026,11,2,9,38,zone),now,zone,false));
            assertEquals("2025년 11월 2일 · 21:38",KoreanUi.messageTime(at(2025,11,2,21,38,zone),now,zone,true));
            assertEquals("2026년 11월 9일 (월)",KoreanUi.day("2026-11-09"));
        }finally{Locale.setDefault(original);}
    }
    public void testLocalCalendarBoundaryAndKoreanResourceContext(){
        ZoneId zone=ZoneId.of("Asia/Seoul");
        assertEquals("어제 오후 11:59",KoreanUi.messageTime(
                at(2026,12,31,23,59,zone),at(2027,1,1,0,1,zone),zone,false));
        Locale original=Locale.getDefault();
        try{
            Locale.setDefault(Locale.US);
            assertEquals("ko",KoreanUi.context(getInstrumentation().getTargetContext())
                    .getResources().getConfiguration().getLocales().get(0).getLanguage());
            // 기본 날짜 선택기 제목이 참조하는 로케일도 한국어여야 한다.
            assertEquals(Locale.KOREA,Locale.getDefault());
        }finally{Locale.setDefault(original);}
    }
    public void testBundledFontsHaveHangulGlyphsAndNotices() throws Exception{
        android.content.Context context=getInstrumentation().getTargetContext();
        android.graphics.Paint paint=new android.graphics.Paint();
        for(int font:new int[]{R.font.jua_regular,R.font.gowun_dodum_regular}){
            paint.setTypeface(context.getResources().getFont(font));
            for(String glyph:new String[]{"한","글","읽","음","0","A"})assertTrue(paint.hasGlyph(glyph));
        }
        try(java.io.InputStream notice=context.getAssets().open("font_licenses.txt")){
            assertTrue(notice.read()!=-1);
        }
    }
}
