package kr.example.haliltalk;

import android.app.AlertDialog;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.shop.DemoShopStore;
import kr.example.haliltalk.shop.ShopCatalog;
import kr.example.haliltalk.shop.ShopCollectionStore;
import kr.example.haliltalk.shop.CoinLedger;

public final class ShopActivity extends FeatureActivity {
    SQLiteDatabase db;
    DemoShopStore store;
    ShopCollectionStore collectionStore;
    String collectionTarget, openedDatabase;
    long openedGeneration;
    boolean filtersOpen, collectionWishlistOpen;
    View header;
    String selected="crown", base="chat";
    int category, theme, ownership;
    final ArrayList<String> visibleItems=new ArrayList<>();
    BaseAdapter adapter;
    AlertDialog purchaseDialog, collectionDialog, characterDialog, historyDialog;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        openedDatabase=AccountSession.databaseName(this);openedGeneration=AccountSession.generation();
        db=new FriendsDatabase(this).getWritableDatabase();store=new DemoShopStore(db);
        collectionStore=new ShopCollectionStore(db);
        if(state!=null){
            selected=state.getString("selected","crown");base=state.getString("base","chat");
            category=state.getInt("category");theme=state.getInt("theme");ownership=state.getInt("ownership");
            filtersOpen=state.getBoolean("filters_open");collectionWishlistOpen=state.getBoolean("wishlist_open");
        }
        else if(CharacterCatalog.slotFor(getIntent().getStringExtra("item_id"))!=null)selected=getIntent().getStringExtra("item_id");
        setContentView(R.layout.screen_shop);
        findViewById(R.id.open_settings).setOnClickListener(v->openSettings());
        findViewById(R.id.shop_help).setOnClickListener(v->HelpGuide.show(this,"shop"));
        findViewById(R.id.shop_back).setOnClickListener(v->finish());
        BottomNavigation.bind(this,R.id.nav_shop,destination->{
            if(destination==R.id.nav_customize&&getIntent().getBooleanExtra("wardrobe_return",false))finish();
            else navigateFeature(destination);
        });
        ListView list=findViewById(R.id.shop_list);
        header=getLayoutInflater().inflate(R.layout.view_shop_header,list,false);
        header.findViewById(R.id.shop_stage).setClipToOutline(true);
        list.addHeaderView(header,null,false);
        Button history=new Button(this,null,0,R.style.SecondaryButton);
        history.setId(R.id.shop_history);history.setText(R.string.shop_history_title);
        ((LinearLayout)header).addView(history,2);
        history.setOnClickListener(v->showHistory());
        header.findViewById(R.id.shop_character).setOnClickListener(v->characterDialog=new AlertDialog.Builder(this)
                .setTitle(R.string.character_selection).setItems(CharacterCatalog.CHARACTER_NAMES,(dialog,index)->{
                    base=CharacterCatalog.CHARACTER_IDS[index];refresh();
                }).setNegativeButton(android.R.string.cancel,null).show());
        findViewById(R.id.shop_buy).setOnClickListener(v->{
            if(selected!=null&&store.owns(selected))tryOn();else confirmPurchase();
        });
        header.findViewById(R.id.shop_filters_toggle).setOnClickListener(v->{
            filtersOpen=!filtersOpen;renderFilters();
        });
        header.findViewById(R.id.collection_wishlist_toggle).setOnClickListener(v->{
            collectionWishlistOpen=!collectionWishlistOpen;renderCollection();
        });
        header.findViewById(R.id.collection_set).setOnClickListener(v->changeCollectionTarget(selected));
        header.findViewById(R.id.collection_clear).setOnClickListener(v->changeCollectionTarget(null));
        header.findViewById(R.id.collection_reveal).setOnClickListener(v->revealCollectionTarget());
        bindFilter(R.id.shop_theme,R.array.shop_themes,theme,true);
        bindFilter(R.id.shop_filter,R.array.shop_filters,ownership,false);
        adapter=new BaseAdapter(){
            public int getCount(){return visibleItems.size();}
            public String getItem(int position){return visibleItems.get(position);}
            public long getItemId(int position){return position;}
            public View getView(int position,View recycled,ViewGroup parent){
                View row=recycled==null?getLayoutInflater().inflate(R.layout.row_shop,parent,false):recycled;
                String id=getItem(position);row.setTag(id);
                row.setBackgroundResource(id.equals(selected)?R.drawable.bg_selected_card:R.drawable.bg_card);
                preview(row.findViewById(R.id.shop_row_character),id);
                ((TextView)row.findViewById(R.id.shop_row_name)).setText(CharacterCatalog.itemName(id));
                ((TextView)row.findViewById(R.id.shop_row_price)).setText(store.owns(id)?getString(R.string.shop_owned):getString(R.string.shop_price,store.price(id)));
                row.findViewById(R.id.shop_row_check).setVisibility(id.equals(selected)?View.VISIBLE:View.INVISIBLE);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent,row,position,id)->{
            int index=position-list.getHeaderViewsCount();
            if(index>=0&&index<visibleItems.size()){selected=visibleItems.get(index);refresh();}
        });
        renderCategories();refresh();
    }

    void bindFilter(int id,int entries,int selection,boolean themes){
        Spinner spinner=header.findViewById(id);
        ArrayAdapter<CharSequence> choices=ArrayAdapter.createFromResource(this,entries,android.R.layout.simple_spinner_item);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(choices);spinner.setSelection(selection);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long itemId){
                if(themes?theme==position:ownership==position)return;
                if(themes)theme=position;else ownership=position;
                refresh();
            }
            public void onNothingSelected(AdapterView<?> parent){}
        });
    }

    void preview(CharacterView view,String item){
        String slot=CharacterCatalog.slotFor(item);
        view.setAppearance(base,"hat".equals(slot)?item:"none","face".equals(slot)?item:"none",
                "body".equals(slot)?item:"none","back".equals(slot)?item:"none","prop".equals(slot)?item:"none");
    }

    void renderCategories(){
        LinearLayout tabs=header.findViewById(R.id.shop_categories);tabs.removeAllViews();
        String[] names=getResources().getStringArray(R.array.shop_categories);
        for(int i=0;i<names.length;i++){
            int index=i;Button tab=new Button(this,null,0,R.style.SecondaryButton);
            tab.setText(category==i?getString(R.string.selected_choice_format,names[i]):names[i]);
            tab.setSelected(category==i);tab.setTextColor(getColor(category==i?R.color.on_primary:R.color.ink));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
            params.setMarginEnd(Math.round(8*getResources().getDisplayMetrics().density));tabs.addView(tab,params);
            tab.setOnClickListener(v->{category=index;renderCategories();refresh();});
        }
    }

    void refresh(){
        renderFilters();
        int balance=store.balance();
        visibleItems.clear();
        for(String id:CharacterCatalog.allAccessoryIds())
            if(ShopCatalog.matches(id,theme,category,ownership,store.owns(id),store.price(id),balance))visibleItems.add(id);
        if(!visibleItems.contains(selected))selected=visibleItems.isEmpty()?null:visibleItems.get(0);
        ((TextView)header.findViewById(R.id.shop_balance)).setText(getString(R.string.shop_balance,balance));
        ((Button)header.findViewById(R.id.shop_character)).setText(getString(R.string.shop_preview_character,CharacterCatalog.nameFor(base)));
        ((TextView)header.findViewById(R.id.shop_selected_name)).setText(selected==null?getString(R.string.shop_select_item):CharacterCatalog.itemName(selected));
        preview(header.findViewById(R.id.shop_preview),selected);
        ((TextView)header.findViewById(R.id.shop_results)).setText(getString(R.string.shop_results,visibleItems.size()));
        header.findViewById(R.id.shop_empty).setVisibility(visibleItems.isEmpty()?View.VISIBLE:View.GONE);
        renderCollection();
        adapter.notifyDataSetChanged();
        TextView actionItem=findViewById(R.id.shop_action_item);
        actionItem.setText(selected==null?"":getString(R.string.shop_action_item,CharacterCatalog.itemName(selected),
                store.owns(selected)?getString(R.string.shop_owned):getString(R.string.shop_price,store.price(selected))));
        actionItem.setTag(selected);
        actionItem.setVisibility(selected==null?View.GONE:View.VISIBLE);
        Button buy=findViewById(R.id.shop_buy);
        if(selected==null){buy.setEnabled(false);buy.setText(R.string.shop_select_item);return;}
        boolean owned=store.owns(selected),affordable=store.balance()>=store.price(selected);
        buy.setEnabled(owned||affordable);
        buy.setText(owned?getString(R.string.shop_try_on):!affordable?getString(R.string.shop_insufficient):
                getString(R.string.shop_buy,store.price(selected)));
    }

    void renderFilters(){
        header.findViewById(R.id.shop_filters_panel).setVisibility(filtersOpen?View.VISIBLE:View.GONE);
        int count=(theme==0?0:1)+(category==0?0:1)+(ownership==0?0:1);
        ((Button)header.findViewById(R.id.shop_filters_toggle)).setText(filtersOpen?
                getString(R.string.shop_filters_close):count==0?getString(R.string.shop_filters_open):
                        getString(R.string.shop_filters_active,count));
    }

    void renderCollection(){
        header.findViewById(R.id.collection_wishlist).setVisibility(collectionWishlistOpen?View.VISIBLE:View.GONE);
        ((Button)header.findViewById(R.id.collection_wishlist_toggle)).setText(
                collectionWishlistOpen?R.string.collection_wishlist_close:R.string.collection_wishlist_open);
        boolean loaded=true;
        try{collectionTarget=collectionStore.target();}
        catch(SQLiteException|IllegalArgumentException failure){loaded=false;collectionMessage(R.string.collection_read_failed);}
        boolean hasTarget=loaded&&collectionTarget!=null;
        Button set=header.findViewById(R.id.collection_set);
        set.setEnabled(loaded&&selected!=null&&!selected.equals(collectionTarget));
        set.setText(selected!=null&&selected.equals(collectionTarget)?R.string.collection_selected:R.string.collection_set);
        TextView target=header.findViewById(R.id.collection_target);
        if(!hasTarget)target.setText(loaded?R.string.collection_empty:R.string.collection_read_failed);
        else if(store.owns(collectionTarget))target.setText(getString(R.string.collection_owned,CharacterCatalog.itemName(collectionTarget)));
        else{
            int price=store.price(collectionTarget),balance=store.balance();
            target.setText(balance>=price?getString(R.string.collection_affordable,CharacterCatalog.itemName(collectionTarget),price,balance):
                    getString(R.string.collection_shortfall,CharacterCatalog.itemName(collectionTarget),price,balance,price-balance));
        }
        header.findViewById(R.id.collection_reveal).setVisibility(hasTarget?View.VISIBLE:View.GONE);
        header.findViewById(R.id.collection_clear).setVisibility(hasTarget?View.VISIBLE:View.GONE);
    }

    void changeCollectionTarget(String item){
        if(!currentAccount()){collectionMessage(R.string.collection_changed);return;}
        if(item!=null&&(!item.equals(selected)||!visibleItems.contains(item)))return;
        if(collectionDialog!=null&&collectionDialog.isShowing())return;
        final String previous;
        try{previous=collectionStore.target();}
        catch(SQLiteException|IllegalArgumentException failure){collectionMessage(R.string.collection_read_failed);return;}
        if(java.util.Objects.equals(item,previous))return;
        final ShopCollectionStore confirmedStore=collectionStore;
        final SQLiteDatabase confirmedDatabase=db;
        Runnable save=()->{
            if(!currentAccount()||confirmedDatabase!=db||confirmedStore!=collectionStore
                    ||item!=null&&(!item.equals(selected)||!visibleItems.contains(item))){
                collectionMessage(R.string.collection_changed);return;
            }
            try{
                if(!java.util.Objects.equals(previous,confirmedStore.target())){
                    collectionMessage(R.string.collection_changed);renderCollection();return;
                }
                if(item==null)confirmedStore.clear();else confirmedStore.setTarget(item);
                collectionWishlistOpen=true;
                collectionMessage(item==null?R.string.collection_cleared:R.string.collection_saved);
                renderCollection();
            }catch(SQLiteException|IllegalArgumentException failure){collectionMessage(R.string.collection_failed);}
        };
        if(previous==null){save.run();return;}
        collectionDialog=new AlertDialog.Builder(this)
                .setTitle(item==null?R.string.collection_clear_title:R.string.collection_replace_title)
                .setMessage(item==null?getString(R.string.collection_clear_message,CharacterCatalog.itemName(previous)):
                        getString(R.string.collection_replace_message,CharacterCatalog.itemName(previous),CharacterCatalog.itemName(item)))
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(item==null?R.string.collection_clear:R.string.collection_replace,(d,w)->save.run()).show();
    }

    void revealCollectionTarget(){
        if(!currentAccount())return;
        try{collectionTarget=collectionStore.target();}
        catch(SQLiteException|IllegalArgumentException failure){collectionMessage(R.string.collection_read_failed);return;}
        if(collectionTarget==null)return;
        theme=0;category=0;ownership=0;selected=collectionTarget;
        ((Spinner)header.findViewById(R.id.shop_theme)).setSelection(0);
        ((Spinner)header.findViewById(R.id.shop_filter)).setSelection(0);
        renderCategories();refresh();
        ((ListView)findViewById(R.id.shop_list)).smoothScrollToPosition(0);
    }

    void collectionMessage(int text){
        TextView message=header.findViewById(R.id.collection_message);
        message.setText(text);message.setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.shop_message)).setText(text);
    }

    boolean currentAccount(){
        return db.isOpen()&&AccountSession.isEntered(this)&&openedGeneration==AccountSession.generation()
                &&openedDatabase.equals(AccountSession.databaseName(this));
    }

    void showHistory(){
        if(!currentAccount()){
            ((TextView)findViewById(R.id.shop_message)).setText(R.string.shop_account_changed);return;
        }
        if(historyDialog!=null&&historyDialog.isShowing())return;
        final List<CoinLedger.Entry> entries;
        try{entries=store.history();}
        catch(SQLiteException failure){
            ((TextView)findViewById(R.id.shop_message)).setText(R.string.shop_history_failed);return;
        }
        ListView history=(ListView)getLayoutInflater().inflate(R.layout.dialog_shop_history,null);
        history.setAdapter(new BaseAdapter(){
            public int getCount(){return entries.size();}
            public CoinLedger.Entry getItem(int position){return entries.get(position);}
            public long getItemId(int position){return position;}
            public boolean areAllItemsEnabled(){return false;}
            public boolean isEnabled(int position){return false;}
            public View getView(int position,View recycled,ViewGroup parent){
                View row=recycled==null?getLayoutInflater().inflate(android.R.layout.simple_list_item_2,parent,false):recycled;
                CoinLedger.Entry entry=getItem(position);row.setTag(entry.eventKey);
                String source="purchase".equals(entry.source)?
                        getString(R.string.shop_history_purchase,CharacterCatalog.itemName(entry.itemId)):
                        "quest".equals(entry.source)?getString(R.string.shop_history_quest,entry.day):
                        getString("starter".equals(entry.source)?R.string.shop_history_starter:R.string.shop_history_opening);
                ((TextView)row.findViewById(android.R.id.text1)).setText(source);
                TextView detail=row.findViewById(android.R.id.text2);detail.setSingleLine(false);
                String amount="opening".equals(entry.source)?getString(R.string.shop_history_opening_balance,entry.balance):
                        getString(R.string.shop_history_change,entry.amount,entry.balance);
                String date=entry.createdAt==null?getString(R.string.shop_history_unknown_date):
                        new SimpleDateFormat("yyyy년 M월 d일 a h:mm",Locale.KOREAN).format(new Date(entry.createdAt));
                detail.setText(getString(R.string.shop_history_detail,amount,date));
                return row;
            }
        });
        historyDialog=new AlertDialog.Builder(this).setTitle(R.string.shop_history_title)
                .setMessage(R.string.shop_history_notice).setView(history)
                .setPositiveButton(android.R.string.ok,null).show();
    }

    void confirmPurchase(){
        final String item=selected;
        if(item==null||!visibleItems.contains(item)||!currentAccount()||store.owns(item)||store.balance()<store.price(item))return;
        if(purchaseDialog!=null&&purchaseDialog.isShowing())return;
        final DemoShopStore confirmedStore=store;
        final SQLiteDatabase confirmedDatabase=db;
        purchaseDialog=new AlertDialog.Builder(this).setTitle(R.string.shop_confirm_title)
                .setMessage(getString(R.string.shop_confirm,CharacterCatalog.itemName(item),
                        store.price(item),store.balance(),store.balance()-store.price(item)))
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.shop_confirm_buy,(dialog,which)->{
                    if(!currentAccount()||confirmedDatabase!=db||confirmedStore!=store){
                        ((TextView)findViewById(R.id.shop_message)).setText(R.string.shop_account_changed);return;
                    }
                    if(!item.equals(selected)||!visibleItems.contains(item))return;
                    try{
                    DemoShopStore.PurchaseResult result=confirmedStore.purchase(item);
                    int message=result==DemoShopStore.PurchaseResult.PURCHASED?R.string.shop_purchased:
                            result==DemoShopStore.PurchaseResult.ALREADY_OWNED?R.string.shop_owned:
                            result==DemoShopStore.PurchaseResult.INSUFFICIENT_FUNDS?R.string.shop_insufficient:R.string.shop_invalid;
                    ((TextView)findViewById(R.id.shop_message)).setText(message);refresh();
                    }catch(SQLiteException failure){
                        ((TextView)findViewById(R.id.shop_message)).setText(R.string.shop_purchase_failed);
                    }
                }).show();
    }
    void tryOn(){
        if(!currentAccount()||selected==null||!visibleItems.contains(selected)||!store.owns(selected))return;
        int friend=getIntent().getIntExtra("friend_id",0);
        if(getIntent().getBooleanExtra("wardrobe_return",false)){
            setResult(RESULT_OK,new android.content.Intent().putExtra("item_id",selected).putExtra("friend_id",friend)
                    .putExtra("shop_database",openedDatabase).putExtra("shop_generation",openedGeneration));
            finish();return;
        }
        startActivity(new android.content.Intent(this,CustomizationActivity.class)
                .putExtra("item_id",selected).putExtra("friend_id",friend));
    }

    @Override protected void onResume(){
        super.onResume();if(adapter!=null&&currentAccount())refresh();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("selected",selected);state.putString("base",base);state.putInt("category",category);
        state.putInt("theme",theme);state.putInt("ownership",ownership);
        state.putBoolean("filters_open",filtersOpen);state.putBoolean("wishlist_open",collectionWishlistOpen);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){
        if(purchaseDialog!=null)purchaseDialog.dismiss();
        if(collectionDialog!=null)collectionDialog.dismiss();
        if(characterDialog!=null)characterDialog.dismiss();
        if(historyDialog!=null)historyDialog.dismiss();
        db.close();super.onDestroy();
    }
}
