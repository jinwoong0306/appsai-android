package kr.example.haliltalk;

import android.app.AlertDialog;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.shop.DemoShopStore;

public final class ShopActivity extends FeatureActivity {
    SQLiteDatabase db;
    DemoShopStore store;
    String selected="crown", base="chat";
    int category;
    final ArrayList<String> visibleItems=new ArrayList<>();
    BaseAdapter adapter;
    AlertDialog purchaseDialog;
    final String[] slots={"","hat","face","body","back","prop"};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        db=new FriendsDatabase(this).getWritableDatabase();store=new DemoShopStore(db);
        if(state!=null){selected=state.getString("selected","crown");base=state.getString("base","chat");category=state.getInt("category");}
        else if(CharacterCatalog.slotFor(getIntent().getStringExtra("item_id"))!=null)selected=getIntent().getStringExtra("item_id");
        setContentView(R.layout.screen_shop);
        findViewById(R.id.open_settings).setOnClickListener(v->openSettings());
        BottomNavigation.bind(this,R.id.nav_shop,this::navigateFeature);
        findViewById(R.id.shop_character).setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle(R.string.character_selection).setItems(CharacterCatalog.CHARACTER_NAMES,(dialog,index)->{
                    base=CharacterCatalog.CHARACTER_IDS[index];refresh();
                }).setNegativeButton(android.R.string.cancel,null).show());
        findViewById(R.id.shop_buy).setOnClickListener(v->confirmPurchase());
        ListView list=findViewById(R.id.shop_list);
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
        list.setOnItemClickListener((parent,row,position,id)->{selected=visibleItems.get(position);refresh();});
        renderCategories();refresh();
    }

    void preview(CharacterView view,String item){
        String slot=CharacterCatalog.slotFor(item);
        view.setAppearance(base,"hat".equals(slot)?item:"none","face".equals(slot)?item:"none",
                "body".equals(slot)?item:"none","back".equals(slot)?item:"none","prop".equals(slot)?item:"none");
    }

    void renderCategories(){
        LinearLayout tabs=findViewById(R.id.shop_categories);tabs.removeAllViews();
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
        ((TextView)findViewById(R.id.shop_balance)).setText(getString(R.string.shop_balance,store.balance()));
        ((Button)findViewById(R.id.shop_character)).setText(getString(R.string.shop_preview_character,CharacterCatalog.nameFor(base)));
        ((TextView)findViewById(R.id.shop_selected_name)).setText(CharacterCatalog.itemName(selected));
        preview(findViewById(R.id.shop_preview),selected);
        visibleItems.clear();
        for(String id:CharacterCatalog.allAccessoryIds())if(category==0||slots[category].equals(CharacterCatalog.slotFor(id)))visibleItems.add(id);
        adapter.notifyDataSetChanged();
        Button buy=findViewById(R.id.shop_buy);
        boolean owned=store.owns(selected),affordable=store.balance()>=store.price(selected);
        buy.setEnabled(!owned&&affordable);
        buy.setText(owned?getString(R.string.shop_owned):!affordable?getString(R.string.shop_insufficient):
                getString(R.string.shop_buy,store.price(selected)));
    }

    void confirmPurchase(){
        final String item=selected;
        if(store.owns(item)||store.balance()<store.price(item))return;
        purchaseDialog=new AlertDialog.Builder(this).setTitle(R.string.shop_confirm_title)
                .setMessage(getString(R.string.shop_confirm,CharacterCatalog.itemName(item),store.price(item)))
                .setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.shop_confirm_buy,(dialog,which)->{
                    DemoShopStore.PurchaseResult result=store.purchase(item);
                    int message=result==DemoShopStore.PurchaseResult.PURCHASED?R.string.shop_purchased:
                            result==DemoShopStore.PurchaseResult.ALREADY_OWNED?R.string.shop_owned:
                            result==DemoShopStore.PurchaseResult.INSUFFICIENT_FUNDS?R.string.shop_insufficient:R.string.shop_invalid;
                    ((TextView)findViewById(R.id.shop_message)).setText(message);refresh();
                }).show();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("selected",selected);state.putString("base",base);state.putInt("category",category);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy(){
        if(purchaseDialog!=null)purchaseDialog.dismiss();
        db.close();super.onDestroy();
    }
}
