package kr.example.haliltalk;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** [U6 | 한국어 표시] 저장값·기기 설정을 바꾸지 않고 사용자 화면의 언어만 맞춘다. */
final class KoreanUi {
    private KoreanUi(){}

    static Context context(Context base){
        // 일부 기본 날짜 선택기의 제목은 Context 대신 프로세스 기본 Locale을 참조한다.
        // 앱 안에서만 한국어로 맞추며 기기의 언어·시간대 설정은 바꾸지 않는다.
        Locale.setDefault(Locale.KOREA);
        Configuration configuration=new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.KOREA);
        return base.createConfigurationContext(configuration);
    }

    static String appName(Context context,ResolveInfo info){
        if(info.nonLocalizedLabel!=null)return info.nonLocalizedLabel.toString();
        if(info.labelRes==0&&info.activityInfo.nonLocalizedLabel!=null)
            return info.activityInfo.nonLocalizedLabel.toString();
        int resource=info.labelRes!=0?info.labelRes:info.activityInfo.labelRes!=0?
                info.activityInfo.labelRes:info.activityInfo.applicationInfo.labelRes;
        String pkg=info.resolvePackageName!=null?info.resolvePackageName:info.activityInfo.packageName;
        if(resource!=0){
            try{
                Context app=context.createPackageContext(pkg,0);
                return context(app).getResources().getText(resource).toString();
            }catch(PackageManager.NameNotFoundException|android.content.res.Resources.NotFoundException removed){
                // 목록 조회 직후 제거된 앱 또는 번역 리소스가 없는 앱은 원래 표시 이름을 유지한다.
            }
        }
        return info.loadLabel(context.getPackageManager()).toString();
    }

    static String day(String iso){
        return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)",Locale.KOREAN));
    }

    static String dateTime(long millis){
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm",Locale.KOREAN));
    }

    static String messageTime(long millis,long now,ZoneId zone,boolean twentyFourHour){
        ZonedDateTime time=Instant.ofEpochMilli(millis).atZone(zone);
        LocalDate today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        String clock=time.format(DateTimeFormatter.ofPattern(twentyFourHour?"HH:mm":"a h:mm",Locale.KOREAN));
        if(time.toLocalDate().equals(today))return clock;
        if(time.toLocalDate().equals(today.minusDays(1)))return "어제 "+clock;
        String pattern=time.getYear()==today.getYear()?"M월 d일":"yyyy년 M월 d일";
        return time.format(DateTimeFormatter.ofPattern(pattern,Locale.KOREAN))+" · "+clock;
    }
}
