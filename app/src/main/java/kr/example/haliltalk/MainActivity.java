package kr.example.haliltalk;

import android.app.*;
import android.app.usage.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.util.*;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.ai.ChatController;
import kr.example.haliltalk.shop.DemoShopStore;

public class MainActivity extends Activity {
    SQLiteDatabase db; LinearLayout root, body; int friendId; String friendName; boolean showingHome, showingApps;
    final List<ResolveInfo> allApps=new ArrayList<>(), visibleApps=new ArrayList<>();
    BaseAdapter appsAdapter;
    AlertDialog personalityDialog;
    boolean showingCharacter;
    String selectedPackage="",selectedLabel="",query="",nickname="",character="chat",hat="none",face="none",prop="none";
    int personality=0,accessoryTab=0;
    ChatController chatController;
    DemoShopStore shopStore;
    final Runnable chatChanged=()->{if(!showingHome&&!showingApps&&!showingCharacter)refreshChat();};
    AlertDialog consentDialog;
    final String[] personalities={"다정한 친구","장난기 많은 친구","차분한 친구"};
    @Override public void onCreate(Bundle b){
        setTheme(R.style.AppTheme);super.onCreate(b);
        if(android.os.Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        db=new FriendsDatabase(this).getWritableDatabase();
        shopStore=new DemoShopStore(db);
        chatController=((ChatApplication)getApplication()).chat();
        if(b!=null){
            selectedPackage=b.getString("selected_package","");selectedLabel=b.getString("selected_label","");
            query=b.getString("query","");nickname=b.getString("nickname","");
            character=b.getString("character","chat");hat=b.getString("hat","none");
            face=b.getString("face","none");prop=b.getString("prop","none");
            personality=b.getInt("personality");accessoryTab=b.getInt("accessory_tab");
        }
        String page=b==null?"home":b.getString("page","home");
        if(page.equals("character"))configureCharacter();
        else if(page.equals("apps")){
            apps();((EditText)findViewById(R.id.app_search)).setText(b.getString("query",""));
        }else if(page.equals("chat")){
            chat(b.getInt("friend_id"),b.getString("friend_name"));
            ((EditText)findViewById(R.id.message_input)).setText(b.getString("draft",""));
        }else if(b==null&&"new_friend".equals(getIntent().getStringExtra("destination")))startNewFriend();
        else if(b==null&&"apps".equals(getIntent().getStringExtra("destination")))apps();
        else home();
        showWelcomeIfNeeded();
    }
    @Override public void setContentView(int layout){
        super.setContentView(layout);
        View content=((ViewGroup)findViewById(android.R.id.content)).getChildAt(0);
        int left=content.getPaddingLeft(),top=content.getPaddingTop(),right=content.getPaddingRight(),bottom=content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()
                        |WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                view.setPadding(left+safe.left,top+safe.top,right+safe.right,bottom+safe.bottom);
                View navigation=findViewById(R.id.bottom_navigation);
                if(navigation!=null)navigation.setVisibility(insets.isVisible(WindowInsets.Type.ime())?View.GONE:View.VISIBLE);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(left+insets.getSystemWindowInsetLeft(),top+insets.getSystemWindowInsetTop(),
                    right+insets.getSystemWindowInsetRight(),bottom+insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        content.post(content::requestApplyInsets);
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("page",showingHome?"home":showingApps?"apps":showingCharacter?"character":"chat");
        if(showingCharacter)nickname=((EditText)findViewById(R.id.friend_name)).getText().toString();
        state.putString("selected_package",selectedPackage);state.putString("selected_label",selectedLabel);
        state.putString("query",query);state.putString("nickname",nickname);
        state.putString("character",character);state.putString("hat",hat);state.putString("face",face);state.putString("prop",prop);
        state.putInt("personality",personality);state.putInt("accessory_tab",accessoryTab);
        if(showingApps)state.putString("query",((EditText)findViewById(R.id.app_search)).getText().toString());
        else if(!showingHome&&!showingCharacter){
            state.putInt("friend_id",friendId);state.putString("friend_name",friendName);
            state.putString("draft",((EditText)findViewById(R.id.message_input)).getText().toString());
        }
        super.onSaveInstanceState(state);
    }
    TextView text(String s,int size){ TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(getColor(R.color.muted));t.setPadding(0,dp(16),0,dp(16));return t; }
    Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b;}
    void screen(String title){showingHome=false;setContentView(R.layout.activity_main);root=findViewById(R.id.screen_root);body=findViewById(R.id.screen_body);TextView heading=findViewById(R.id.screen_title);heading.setText(title);}
    void home(){
        showingHome=true;showingApps=false;showingCharacter=false;setContentView(R.layout.screen_home);
        BottomNavigation.bind(this,R.id.nav_friends,this::navigateTo);
        ((CharacterView)findViewById(R.id.home_character)).setAppearance("chat","beret","none","none");
        LinearLayout friends=findViewById(R.id.friend_list);
        findViewById(R.id.open_settings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        Cursor c=db.rawQuery("SELECT id,name,personality,xp FROM friends ORDER BY id DESC",null);
        if(c.getCount()==0) friends.addView(text("아직 친구가 없어요. 앱을 선택해 첫 친구를 만들어봐요!",15));
        while(c.moveToNext()){
            int id=c.getInt(0);String name=c.getString(1),person=c.getString(2);int xp=c.getInt(3);
            View row=getLayoutInflater().inflate(R.layout.row_friend,friends,false);
            ((TextView)row.findViewById(R.id.friend_label)).setText(name);
            ((TextView)row.findViewById(R.id.friend_detail)).setText(getString(R.string.friend_detail_format,person,xp));
            showAppearance(row.findViewById(R.id.friend_character),id);
            row.setOnClickListener(v->chat(id,name));friends.addView(row);
        }
        c.close();
    }
    void showWelcomeIfNeeded(){
        if(getPreferences(MODE_PRIVATE).getBoolean("welcome_done",false)) return;
        new AlertDialog.Builder(this).setTitle("앱사이에 온 걸 환영해요!")
            .setMessage("설치된 실행 앱을 친구로 추가하고 성격을 골라보세요.\n\n사용 시간 조회는 설정에서 사용 정보 접근을 허용한 경우에만 가능해요. 어떤 콘텐츠를 봤는지는 알 수 없어요.\n\nAI 대화는 별도 동의 후 Google Gemini에 전송돼요. 개인정보 대신 가상 내용으로 테스트해 주세요.")
            .setPositiveButton("시작하기",(dialog,which)->getPreferences(MODE_PRIVATE).edit().putBoolean("welcome_done",true).apply())
            .setCancelable(false).show();
    }
    void apps(){
        showingHome=false;showingApps=true;showingCharacter=false;setContentView(R.layout.screen_apps);
        BottomNavigation.bind(this,R.id.nav_apps,this::navigateTo);
        findViewById(R.id.open_settings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        ((CharacterView)findViewById(R.id.picker_character)).setAppearance("chat","none","none","none");
        findViewById(R.id.apps_back).setOnClickListener(v->home());
        findViewById(R.id.apps_continue).setOnClickListener(v->choose(selectedPackage,selectedLabel));
        findViewById(R.id.apps_continue).setEnabled(!selectedPackage.isEmpty());
        ListView appList=findViewById(R.id.app_list);
        Intent intent=new Intent(Intent.ACTION_MAIN);intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> results=getPackageManager().queryIntentActivities(intent,0);
        results.sort(Comparator.comparing(x->x.loadLabel(getPackageManager()).toString(),String.CASE_INSENSITIVE_ORDER));
        allApps.clear();Set<String> seen=new HashSet<>();
        for(ResolveInfo info:results){
            String pkg=info.activityInfo.packageName;
            if(pkg.equals(getPackageName())||!seen.add(pkg))continue;
            allApps.add(info);
        }
        appsAdapter=new BaseAdapter(){
            @Override public int getCount(){return visibleApps.size();}
            @Override public ResolveInfo getItem(int position){return visibleApps.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View recycled,ViewGroup parent){
                View row=recycled==null?getLayoutInflater().inflate(R.layout.row_app,parent,false):recycled;
                ResolveInfo info=getItem(position);
                ((ImageView)row.findViewById(R.id.app_icon)).setImageDrawable(info.loadIcon(getPackageManager()));
                ((TextView)row.findViewById(R.id.app_label)).setText(info.loadLabel(getPackageManager()));
                ((TextView)row.findViewById(R.id.app_package)).setText(info.activityInfo.packageName);
                boolean selected=info.activityInfo.packageName.equals(selectedPackage);
                row.setSelected(selected);
                row.setBackgroundResource(selected?R.drawable.bg_selected_card:R.drawable.bg_card);
                ((ImageView)row.findViewById(R.id.app_selected)).setImageResource(selected?R.drawable.ic_check:R.drawable.ic_circle);
                row.setContentDescription(info.loadLabel(getPackageManager())+(selected?" 선택됨":""));
                return row;
            }
        };
        appList.setAdapter(appsAdapter);appList.setEmptyView(findViewById(R.id.apps_empty));
        appList.setOnItemClickListener((parent,row,position,id)->{
            ResolveInfo info=visibleApps.get(position);
            selectedPackage=info.activityInfo.packageName;selectedLabel=info.loadLabel(getPackageManager()).toString();
            appsAdapter.notifyDataSetChanged();findViewById(R.id.apps_continue).setEnabled(true);
        });
        EditText search=findViewById(R.id.app_search);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){filterApps(s.toString());}
            public void afterTextChanged(Editable s){}
        });
        search.setText(query);filterApps(query);
    }
    void filterApps(String query){
        this.query=query;
        String term=query.trim().toLowerCase(Locale.ROOT);visibleApps.clear();
        for(ResolveInfo info:allApps){
            if(info.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).contains(term)
                    ||info.activityInfo.packageName.toLowerCase(Locale.ROOT).contains(term))visibleApps.add(info);
        }
        appsAdapter.notifyDataSetChanged();
        ((TextView)findViewById(R.id.apps_count)).setText(getString(R.string.apps_count,allApps.size(),visibleApps.size()));
    }
    void navigateTo(int destination){
        if(destination==R.id.nav_friends)home();
        else if(destination==R.id.nav_apps)apps();
        else if(destination==R.id.nav_customize)startActivity(new Intent(this,CustomizationActivity.class));
        else if(destination==R.id.nav_shop)startActivity(new Intent(this,ShopActivity.class));
    }
    void startNewFriend(){selectedPackage="";selectedLabel="";query="";apps();}
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        String destination=intent.getStringExtra("destination");
        if("new_friend".equals(destination))startNewFriend();
        else if("apps".equals(destination))apps();
        else if("home".equals(destination))home();
    }
    void choose(String pkg,String name){
        Cursor c=db.rawQuery("SELECT id,name FROM friends WHERE package=?",new String[]{pkg});
        if(c.moveToFirst()){int id=c.getInt(0);String savedName=c.getString(1);c.close();chat(id,savedName);return;}c.close();
        if(pkg.isEmpty())return;
        selectedPackage=pkg;selectedLabel=name;nickname=name;
        character="chat";hat="none";face="none";prop="none";personality=0;accessoryTab=0;
        configureCharacter();
    }
    void configureCharacter(){
        showingHome=false;showingApps=false;showingCharacter=true;
        setContentView(R.layout.screen_character);
        ((TextView)findViewById(R.id.linked_app)).setText(getString(R.string.linked_app_format,selectedLabel));
        EditText name=findViewById(R.id.friend_name);name.setText(nickname);
        name.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){name.setError(null);}
            public void afterTextChanged(Editable s){}
        });
        findViewById(R.id.character_back).setOnClickListener(v->{nickname=name.getText().toString();apps();});
        findViewById(R.id.create_friend).setOnClickListener(v->createFriend());
        renderChoices();
    }
    void renderChoices(){
        ((CharacterView)findViewById(R.id.character_preview)).setAppearance(character,hat,face,prop);
        LinearLayout bases=findViewById(R.id.character_choices);bases.removeAllViews();
        for(int i=0;i<CharacterCatalog.CHARACTER_IDS.length;i++){
            String id=CharacterCatalog.CHARACTER_IDS[i];
            addChoice(bases,id,CharacterCatalog.CHARACTER_NAMES[i],id.equals(character),id,hat,face,prop,()->{character=id;renderChoices();});
        }
        LinearLayout tabs=findViewById(R.id.accessory_tabs);tabs.removeAllViews();
        String[] tabNames={"모자","안경","소품"};
        for(int i=0;i<tabNames.length;i++){
            final int index=i;addChip(tabs,tabNames[i],accessoryTab==i,()->{accessoryTab=index;renderChoices();});
        }
        String[] ids=accessoryTab==0?CharacterCatalog.HAT_IDS:accessoryTab==1?CharacterCatalog.FACE_IDS:CharacterCatalog.PROP_IDS;
        String[] names=accessoryTab==0?CharacterCatalog.HAT_NAMES:accessoryTab==1?CharacterCatalog.FACE_NAMES:CharacterCatalog.PROP_NAMES;
        String selected=accessoryTab==0?hat:accessoryTab==1?face:prop;
        AccessoryGrid accessories=findViewById(R.id.accessory_choices);
        if(!Integer.valueOf(accessoryTab).equals(accessories.getTag())){accessories.removeAllViews();accessories.setTag(accessoryTab);}
        for(int i=0;i<ids.length;i++){
            String id=ids[i];
            addChoice(accessories,id,names[i],id.equals(selected),character,accessoryTab==0?id:hat,
                    accessoryTab==1?id:face,accessoryTab==2?id:prop,()->{
                        if(accessoryTab==0)hat=id;else if(accessoryTab==1)face=id;else prop=id;renderChoices();
                    });
        }
        LinearLayout people=findViewById(R.id.personality_choices);people.removeAllViews();
        for(int i=0;i<personalities.length;i++){
            final int index=i;addChip(people,personalities[i],personality==i,()->{personality=index;renderChoices();});
        }
    }
    void addChip(LinearLayout parent,String label,boolean selected,Runnable action){
        Button chip=new Button(this,null,0,R.style.SecondaryButton);
        chip.setText(selected?getString(R.string.selected_choice_format,label):label);chip.setSelected(selected);
        chip.setTextColor(getColor(selected?R.color.on_primary:R.color.ink));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(8));chip.setLayoutParams(params);chip.setOnClickListener(v->action.run());parent.addView(chip);
    }
    void addChoice(ViewGroup parent,String id,String label,boolean selected,String base,String h,String f,String p,Runnable action){
        View row=parent.findViewWithTag(id);
        if(row==null){row=getLayoutInflater().inflate(R.layout.row_choice,parent,false);parent.addView(row);}
        row.setTag(id);row.setSelected(selected);row.setContentDescription(label+(selected?" 선택됨":""));
        ((CharacterView)row.findViewById(R.id.choice_character)).setAppearance(base,h,f,p);
        boolean owned=CharacterCatalog.slotFor(id)==null||shopStore.owns(id);
        ((TextView)row.findViewById(R.id.choice_label)).setText(owned?label:getString(R.string.shop_locked,label,shopStore.price(id)));
        row.findViewById(R.id.choice_check).setVisibility(selected?View.VISIBLE:View.INVISIBLE);
        row.setOnClickListener(v->{
            if(CharacterCatalog.slotFor(id)==null||shopStore.owns(id))action.run();
            else new AlertDialog.Builder(this).setMessage(getString(R.string.shop_locked,label,shopStore.price(id)))
                    .setPositiveButton(R.string.shop_open,(dialog,which)->startActivity(new Intent(this,ShopActivity.class).putExtra("item_id",id)))
                    .setNegativeButton(android.R.string.cancel,null).show();
        });
    }
    void createFriend(){
        EditText field=findViewById(R.id.friend_name);String name=field.getText().toString().trim();
        if(name.isEmpty()){field.setError("친구 이름을 입력해 주세요.");field.requestFocus();return;}
        if(name.codePointCount(0,name.length())>20){field.setError("이름은 20자까지 입력해 주세요.");return;}
        if(!shopStore.owns(hat)||!shopStore.owns(face)||!shopStore.owns(prop)){
            Toast.makeText(this,R.string.shop_ownership_needed,Toast.LENGTH_SHORT).show();return;
        }
        try(Cursor existing=db.rawQuery("SELECT id,name FROM friends WHERE package=?",new String[]{selectedPackage})){
            if(existing.moveToFirst()){chat(existing.getInt(0),existing.getString(1));return;}
        }
        ContentValues values=new ContentValues();values.put("package",selectedPackage);
        values.put("name",name);values.put("personality",personalities[personality]);
        int id;
        db.beginTransaction();
        try{
            id=(int)db.insertOrThrow("friends",null,values);
            ContentValues appearance=new ContentValues();appearance.put("friend",id);appearance.put("character",character);
            appearance.put("hat",hat);appearance.put("face",face);appearance.put("prop",prop);
            db.insertOrThrow("appearance",null,appearance);
            save(id,"안녕! 이제 우리 친구야.\n오늘은 어땠어?",false);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(field.getWindowToken(),0);
        chat(id,name);
    }
    void showAppearance(CharacterView view,int id){
        try(Cursor cursor=db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",new String[]{""+id})){
            if(cursor.moveToFirst())view.setAppearance(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3),cursor.getString(4),cursor.getString(5));
            else view.setAppearance("chat","none","none","none");
        }
    }
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void save(int id,String s,boolean user){ContentValues v=new ContentValues();v.put("friend",id);v.put("content",s);v.put("is_user",user?1:0);v.put("created",System.currentTimeMillis());db.insertOrThrow("messages",null,v);}
    boolean hasUsage(){return ((AppOpsManager)getSystemService(APP_OPS_SERVICE)).checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;}
    void usage(){
        if(!hasUsage()){
            new AlertDialog.Builder(this).setTitle("사용 시간 조회 권한")
                    .setMessage("선택한 앱의 사용 시간 조회에 필요해요.\n권한 없이도 채팅할 수 있어요.")
                    .setPositiveButton("설정 열기",(d,w)->startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                    .setNegativeButton("나중에",null).show();return;
        }
        Cursor c=db.rawQuery("SELECT package FROM friends WHERE id=?",new String[]{""+friendId});
        if(!c.moveToFirst()){c.close();return;}String pkg=c.getString(0);c.close();
        Calendar start=Calendar.getInstance();start.set(Calendar.HOUR_OF_DAY,0);start.set(Calendar.MINUTE,0);
        start.set(Calendar.SECOND,0);start.set(Calendar.MILLISECOND,0);
        UsageStatsManager manager=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
        Map<String,UsageStats> data=manager.queryAndAggregateUsageStats(start.getTimeInMillis(),System.currentTimeMillis());
        UsageStats stats=data==null?null:data.get(pkg);
        String result=stats==null?"조회 가능한 사용 기록이 없어요. 사용하지 않았다고 단정할 수는 없어요."
                :friendName+" 사용 시간: 약 "+stats.getTotalTimeInForeground()/60000+"분";
        new AlertDialog.Builder(this).setTitle("오늘의 사용 시간")
                .setMessage(result+"\n\n시스템 집계는 실제와 다를 수 있어요.\n사용한 내용은 알 수 없어요.")
                .setPositiveButton("닫기",null).show();
    }
    void chat(int id,String name){
        friendId=id;friendName=name;showingHome=false;showingApps=false;showingCharacter=false;setContentView(R.layout.screen_chat);
        ((TextView)findViewById(R.id.chat_title)).setText(name);
        showAppearance(findViewById(R.id.chat_character),id);
        try(Cursor person=db.rawQuery("SELECT personality FROM friends WHERE id=?",new String[]{""+id})){
            if(person.moveToFirst())((TextView)findViewById(R.id.chat_personality)).setText(person.getString(0));
        }
        findViewById(R.id.back_home).setOnClickListener(v->home());
        findViewById(R.id.check_usage).setOnClickListener(v->usage());
        findViewById(R.id.ai_notice).setOnClickListener(v->showAiConsent(null));
        findViewById(R.id.retry_message).setOnClickListener(v->withAiConsent(()->chatController.retry(id)));
        findViewById(R.id.cancel_request).setOnClickListener(v->chatController.cancelFailed(id));
        EditText input=findViewById(R.id.message_input);
        findViewById(R.id.send_message).setOnClickListener(v->{
            String message=input.getText().toString().trim();if(message.isEmpty())return;
            if(message.length()>ChatController.MAX_MESSAGE_CHARS){input.setError(getString(R.string.ai_message_too_long));return;}
            withAiConsent(()->{if(chatController.send(id,message)){input.setText("");refreshChat();}});
        });
        refreshChat();
    }
    void withAiConsent(Runnable action){
        if(!chatController.isConfigured()){refreshChat();return;}
        if(chatController.hasConsent())action.run();else showAiConsent(action);
    }
    void showAiConsent(Runnable action){
        if(consentDialog!=null&&consentDialog.isShowing())return;
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(R.string.ai_consent_title)
                .setMessage(R.string.ai_consent_body)
                .setNegativeButton(R.string.ai_later,null);
        if(chatController.hasConsent()){
            dialog.setPositiveButton(R.string.ai_close,null)
                    .setNeutralButton(R.string.ai_revoke,(d,w)->chatController.setConsent(false));
        }else dialog.setPositiveButton(R.string.ai_agree,(d,w)->{
            chatController.setConsent(true);if(action!=null)action.run();
        });
        consentDialog=dialog.create();consentDialog.show();
    }
    void refreshChat(){
        LinearLayout messages=findViewById(R.id.message_list);if(messages==null)return;
        messages.removeAllViews();
        try(Cursor c=db.rawQuery("SELECT m.content,m.is_user,"
                +"EXISTS(SELECT 1 FROM chat_requests r WHERE r.reply_message=m.id AND r.state='complete')"
                +" FROM messages m WHERE m.friend=? ORDER BY m.id",new String[]{""+friendId})){
            while(c.moveToNext())addMessage(messages,c.getString(0),c.getInt(1)==1,
                    c.getInt(2)==1?friendName:friendName+getString(R.string.ai_local_history));
        }
        ChatController.Request request=chatController.openRequest(friendId);
        boolean pending=request!=null&&request.state.equals("pending");
        boolean failed=request!=null&&request.state.equals("failed");
        int status=!chatController.isConfigured()?R.string.ai_not_configured:pending?R.string.ai_loading:
                failed?("interrupted".equals(request.error)?R.string.ai_interrupted:
                        "timeout".equals(request.error)?R.string.ai_timeout:R.string.ai_failed):
                chatController.hasConsent()?R.string.ai_ready:R.string.ai_consent_needed;
        ((TextView)findViewById(R.id.ai_status)).setText(status);
        findViewById(R.id.ai_progress).setVisibility(pending?View.VISIBLE:View.GONE);
        findViewById(R.id.retry_message).setVisibility(failed?View.VISIBLE:View.GONE);
        findViewById(R.id.retry_message).setEnabled(chatController.isConfigured());
        findViewById(R.id.cancel_request).setVisibility(failed?View.VISIBLE:View.GONE);
        findViewById(R.id.send_message).setEnabled(request==null&&chatController.isConfigured());
        ScrollView scroll=findViewById(R.id.chat_scroll);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    void addMessage(LinearLayout parent,String message,boolean user,String name){
        android.widget.FrameLayout row=(android.widget.FrameLayout)getLayoutInflater().inflate(R.layout.row_message,parent,false);
        TextView bubble=row.findViewById(R.id.message_text);bubble.setText(getString(R.string.message_format,user?getString(R.string.message_sender_me):name,message));
        bubble.setBackgroundResource(user?R.drawable.bg_own_bubble:R.drawable.bg_bubble);
        bubble.setTextColor(getColor(user?R.color.on_primary:R.color.ink));
        android.widget.FrameLayout.LayoutParams params=(android.widget.FrameLayout.LayoutParams)bubble.getLayoutParams();
        params.gravity=user?Gravity.END:Gravity.START;
        bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels-dp(80));
        parent.addView(row);
    }
    @Override public void onBackPressed(){if(showingHome) super.onBackPressed(); else if(showingCharacter)apps();else home();}
    @Override protected void onStart(){
        super.onStart();
        if(showingHome)home();
        else if(showingCharacter)renderChoices();
        else if(!showingApps&&!showingCharacter)showAppearance(findViewById(R.id.chat_character),friendId);
        chatController.addListener(chatChanged);chatChanged.run();
    }
    @Override protected void onStop(){chatController.removeListener(chatChanged);super.onStop();}
    @Override protected void onDestroy(){
        if(personalityDialog!=null)personalityDialog.dismiss();
        if(consentDialog!=null)consentDialog.dismiss();
        db.close();super.onDestroy();
    }
}
