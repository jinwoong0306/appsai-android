package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.character.SceneBackgrounds;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.shop.DemoShopStore;

public final class CustomizationActivity extends FeatureActivity {
    private static final int SHOP_REQUEST=41;
    SQLiteDatabase db;
    DemoShopStore store;
    int friendId, slot;
    String friendName="";
    String openedDatabase, friendPackage="", scene="none", savedScene="none";
    SharedPreferences scenePreferences;
    AlertDialog discardDialog, ownershipDialog;
    String[] appearance={"chat","none","none","none","none","none"};
    String[] saved=appearance.clone();
    final ArrayList<Integer> friendIds=new ArrayList<>();
    final ArrayList<String> friendNames=new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        openedDatabase=AccountSession.databaseName(this);
        scenePreferences=getSharedPreferences("wardrobe_scenes_"+openedDatabase,MODE_PRIVATE);
        db=new FriendsDatabase(this).getWritableDatabase();
        store=new DemoShopStore(db);
        setContentView(R.layout.screen_customization);
        findViewById(R.id.wardrobe_scene).setClipToOutline(true);
        findViewById(R.id.wardrobe_back).setOnClickListener(v->onBackPressed());
        findViewById(R.id.open_settings).setOnClickListener(v->confirmDiscard(this::openSettings));
        findViewById(R.id.wardrobe_friend).setOnClickListener(v->chooseFriend());
        findViewById(R.id.wardrobe_save).setOnClickListener(v->saveAppearance());
        findViewById(R.id.wardrobe_cancel).setOnClickListener(v->{
            findViewById(R.id.wardrobe_save_error).setVisibility(View.GONE);
            appearance=saved.clone();scene=savedScene;render();
        });
        findViewById(R.id.bottom_navigation).setTag("reference");
        findViewById(R.id.bottom_navigation).setBackgroundResource(R.drawable.bg_reference_navbar);
        BottomNavigation.bind(this,R.id.nav_customize,destination->{
            if(destination==R.id.nav_shop)openShop(appearance[slot+1]);
            else confirmDiscard(()->navigateFeature(destination));
        });
        try(Cursor cursor=db.rawQuery("SELECT id,name FROM friends ORDER BY id DESC",null)){
            while(cursor.moveToNext()){friendIds.add(cursor.getInt(0));friendNames.add(cursor.getString(1));}
        }
        if(friendIds.isEmpty()){
            render();
            return;
        }
        int restored=state==null?getIntent().getIntExtra("friend_id",friendIds.get(0)):state.getInt("friend_id");
        loadFriend(friendIds.contains(restored)?restored:friendIds.get(0));
        if(state!=null&&openedDatabase.equals(state.getString("wardrobe_database"))
                &&friendPackage.equals(state.getString("wardrobe_package"))){
            String[] draft=state.getStringArray("appearance");
            if(draft!=null){
                appearance=draft;slot=state.getInt("slot");
                scene=SceneBackgrounds.normalize(state.getString("wardrobe_scene"));
                render();
            }
        }
        if(state==null)applyShopItem(getIntent().getStringExtra("item_id"));
    }

    void openShop(String item){
        startActivityForResult(new android.content.Intent(this,ShopActivity.class)
                .putExtra("item_id",item).putExtra("friend_id",friendId)
                .putExtra("wardrobe_return",true),SHOP_REQUEST);
    }

    void applyShopItem(String item){
        if(friendId<=0||!openedDatabase.equals(AccountSession.databaseName(this))||!store.owns(item))return;
        String part=CharacterCatalog.slotFor(item);
        String[] slots={"hat","face","body","back","prop"};
        for(int i=0;i<slots.length;i++)if(slots[i].equals(part)){
            appearance[i+1]=item;slot=i;render();return;
        }
    }

    @Override protected void onActivityResult(int request,int result,android.content.Intent data){
        super.onActivityResult(request,result,data);
        if(request==SHOP_REQUEST&&result==RESULT_OK&&data!=null
                &&friendId==data.getIntExtra("friend_id",0)
                &&openedDatabase.equals(data.getStringExtra("shop_database"))
                &&AccountSession.generation()==data.getLongExtra("shop_generation",-1))
            applyShopItem(data.getStringExtra("item_id"));
    }

    void chooseFriend() {
        new AlertDialog.Builder(this).setTitle(R.string.customize_select)
                .setItems(friendNames.toArray(new String[0]),(dialog,index)->
                        confirmDiscard(()->loadFriend(friendIds.get(index))))
                .setNegativeButton(android.R.string.cancel,null).show();
    }

    void loadFriend(int id) {
        findViewById(R.id.wardrobe_save_error).setVisibility(View.GONE);
        friendId=id;friendName=friendNames.get(friendIds.indexOf(id));
        appearance=new String[]{"chat","none","none","none","none","none"};
        try(Cursor cursor=db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",new String[]{""+id})){
            if(cursor.moveToFirst())for(int i=0;i<6;i++)appearance[i]=cursor.getString(i);
        }
        saved=appearance.clone();
        try(Cursor cursor=db.rawQuery("SELECT package FROM friends WHERE id=?",new String[]{""+id})){
            cursor.moveToFirst();friendPackage=cursor.getString(0);
        }
        scene=SceneBackgrounds.normalize(scenePreferences.getString(friendPackage,"none"));
        savedScene=scene;
        ((Button)findViewById(R.id.wardrobe_friend)).setText(friendName);
        render();
    }

    void render() {
        boolean empty=friendIds.isEmpty();
        findViewById(R.id.wardrobe_empty).setVisibility(empty?View.VISIBLE:View.GONE);
        for(int id:new int[]{R.id.wardrobe_content,R.id.wardrobe_friend,R.id.wardrobe_cancel,R.id.wardrobe_actions})
            findViewById(id).setVisibility(empty?View.GONE:View.VISIBLE);
        if(empty){
            TextView message=findViewById(R.id.wardrobe_empty);
            message.setText(getString(R.string.shop_friendless_body,
                    getString(R.string.shop_add_friend_notice),getString(R.string.shop_add_friend)));
            message.setOnClickListener(v->openMain(true));message.setFocusable(true);
            return;
        }
        ((CharacterView)findViewById(R.id.wardrobe_preview)).setAppearance(
                appearance[0],appearance[1],appearance[2],appearance[3],appearance[4],appearance[5]);
        ((ImageView)findViewById(R.id.wardrobe_scene_art)).setImageResource(
                "none".equals(scene)?0:SceneBackgrounds.drawable(scene));
        findViewById(R.id.wardrobe_scene).setContentDescription(
                getString(R.string.wardrobe_reference_scene_selected,getString(SceneBackgrounds.label(scene))));
        LinearLayout bases=findViewById(R.id.wardrobe_characters);
        for(int i=0;i<CharacterCatalog.CHARACTER_IDS.length;i++){
            String id=CharacterCatalog.CHARACTER_IDS[i];
            addChoice(bases,id,CharacterCatalog.CHARACTER_NAMES[i],0);
        }
        LinearLayout tabs=findViewById(R.id.wardrobe_tabs);
        String[] names=getResources().getStringArray(R.array.accessory_slot_names);
        for(int i=0;i<names.length;i++){
            int index=i;
            if(tabs.getChildCount()<=i){
                Button tab=new Button(this,null,0,R.style.WardrobeReferenceTab);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);
                p.setMarginEnd(dp(8));tabs.addView(tab,p);
                tab.setOnClickListener(v->{slot=index;render();});
            }
            Button tab=(Button)tabs.getChildAt(i);
            tab.setText(names[i]);tab.setSelected(slot==i);
            tab.setTextColor(getColor(slot==i?R.color.on_primary:R.color.reference_purple_ink));
            tab.setContentDescription(slot==i?getString(R.string.selected_choice_format,names[i]):names[i]);
        }
        String[][] ids={CharacterCatalog.HAT_IDS,CharacterCatalog.FACE_IDS,CharacterCatalog.BODY_IDS,CharacterCatalog.BACK_IDS,CharacterCatalog.PROP_IDS};
        String[][] labels={CharacterCatalog.HAT_NAMES,CharacterCatalog.FACE_NAMES,CharacterCatalog.BODY_NAMES,CharacterCatalog.BACK_NAMES,CharacterCatalog.PROP_NAMES};
        AccessoryGrid items=findViewById(R.id.wardrobe_items);
        if(!Integer.valueOf(slot).equals(items.getTag())){
            items.removeAllViews();items.setTag(slot);
            findViewById(R.id.wardrobe_inventory_scroll).post(()->
                    ((android.widget.HorizontalScrollView)findViewById(R.id.wardrobe_inventory_scroll)).scrollTo(0,0));
        }
        for(int i=0;i<ids[slot].length;i++)addChoice(items,ids[slot][i],labels[slot][i],slot+1);
        int owned=0;
        for(String id:ids[slot])if(!"none".equals(id)&&store.owns(id))owned++;
        ((TextView)findViewById(R.id.wardrobe_inventory_title)).setText(
                getString(R.string.wardrobe_reference_inventory,names[slot]));
        ((TextView)findViewById(R.id.wardrobe_owned)).setText(getString(R.string.wardrobe_reference_owned,owned));
        StringBuilder equipped=new StringBuilder(CharacterCatalog.nameFor(appearance[0]));
        for(int i=0;i<names.length;i++)equipped.append("\n").append(
                getString(R.string.wardrobe_reference_slot_item,names[i],CharacterCatalog.itemName(appearance[i+1])));
        equipped.append("\n").append(getString(R.string.wardrobe_reference_slot_item,
                getString(R.string.wardrobe_reference_background),getString(SceneBackgrounds.label(scene))));
        ((TextView)findViewById(R.id.wardrobe_equipped)).setText(equipped);
        renderScenes();
        findViewById(R.id.wardrobe_save).setEnabled(hasChanges());
        findViewById(R.id.wardrobe_cancel).setEnabled(hasChanges());
        ReferenceActions.label(findViewById(R.id.wardrobe_save),
                hasChanges()?R.string.wardrobe_reference_save:R.string.wardrobe_reference_saved,
                R.drawable.ic_reference_save);
    }

    void addChoice(ViewGroup parent,String id,String label,int part) {
        View row=parent.findViewWithTag(id);
        if(row==null){
            row=getLayoutInflater().inflate(R.layout.row_wardrobe_choice,parent,false);parent.addView(row);
            if(part>0){
                row.findViewById(R.id.choice_character).setVisibility(View.GONE);
                ImageView image=row.findViewById(R.id.wardrobe_item_image);image.setVisibility(View.VISIBLE);
                if("none".equals(id))image.setImageResource(R.drawable.wardrobe_none);
                else loadAccessoryIcon(image,label,part);
            }
        }
        row.setTag(id);boolean selected=id.equals(appearance[part]);row.setSelected(selected);
        if(part==0){
            String[] preview=appearance.clone();preview[part]=id;
            ((CharacterView)row.findViewById(R.id.choice_character)).setAppearance(
                    preview[0],preview[1],preview[2],preview[3],preview[4],preview[5]);
        }
        boolean owned=part==0||store.owns(id);
        ((TextView)row.findViewById(R.id.choice_label)).setText(label);
        row.findViewById(R.id.choice_lock).setVisibility(owned?View.GONE:View.VISIBLE);
        row.findViewById(R.id.choice_check).setVisibility(selected?View.VISIBLE:View.INVISIBLE);
        String description=owned?label:getString(R.string.shop_locked,label,store.price(id));
        row.setContentDescription(selected?getString(R.string.selected_choice_format,description):description);
        row.setOnClickListener(v->{
            if(owned){appearance[part]=id;render();}
            else {
                ownershipDialog=new AlertDialog.Builder(this).setTitle(R.string.wardrobe_reference_locked_title)
                    .setMessage(getString(R.string.shop_locked,label,store.price(id)))
                    .setPositiveButton(R.string.shop_open,(dialog,which)->
                            openShop(id))
                    .setNegativeButton(android.R.string.cancel,null).create();
                ReferenceActions.dismissOnEscape(ownershipDialog);
                ownershipDialog.show();
            }
        });
    }

    void loadAccessoryIcon(ImageView image,String label,int part) {
        String[] folders={"머리","얼굴","목과몸","등","손소품"};
        String folder="characters/accessories/"+folders[part-1];
        try{
            for(String file:getAssets().list(folder)){
                if(!file.endsWith("_"+label+".png"))continue;
                try(InputStream stream=getAssets().open(folder+"/"+file)){
                    BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;
                    Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);
                    bitmap.setDensity(Bitmap.DENSITY_NONE);
                    BitmapDrawable drawable=new BitmapDrawable(getResources(),bitmap);
                    drawable.setFilterBitmap(false);image.setImageDrawable(drawable);return;
                }
            }
            throw new IllegalStateException("Missing wardrobe icon: "+label);
        }catch(IOException error){throw new IllegalStateException("Cannot open wardrobe icon: "+label,error);}
    }

    void renderScenes() {
        ViewGroup backgrounds=findViewById(R.id.wardrobe_backgrounds);
        for(String id:SceneBackgrounds.IDS){
            View row=backgrounds.findViewWithTag(id);
            if(row==null){
                row=getLayoutInflater().inflate(R.layout.row_wardrobe_scene,backgrounds,false);
                row.setTag(id);backgrounds.addView(row);
                ImageView image=row.findViewById(R.id.wardrobe_scene_image);
                image.setImageResource("none".equals(id)?R.drawable.wardrobe_none:SceneBackgrounds.drawable(id));
                if("none".equals(id))image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                ((TextView)row.findViewById(R.id.choice_label)).setText(SceneBackgrounds.label(id));
                row.setOnClickListener(v->{scene=id;render();});
            }
            boolean selected=id.equals(scene);row.setSelected(selected);
            row.findViewById(R.id.choice_check).setVisibility(selected?View.VISIBLE:View.INVISIBLE);
            String label=getString(SceneBackgrounds.label(id));
            row.setContentDescription(selected?getString(R.string.selected_choice_format,label):label);
        }
    }

    boolean hasChanges() { return !Arrays.equals(saved,appearance)||!savedScene.equals(scene); }

    void saveAppearance() {
        if(!openedDatabase.equals(AccountSession.databaseName(this))){finish();return;}
        TextView error=findViewById(R.id.wardrobe_save_error);
        error.setVisibility(View.GONE);
        for(int i=1;i<appearance.length;i++)if(!store.owns(appearance[i])){
            error.setText(R.string.shop_ownership_needed);error.setVisibility(View.VISIBLE);return;
        }
        ContentValues values=new ContentValues();values.put("friend",friendId);
        String[] columns={"character","hat","face","body","back","prop"};
        for(int i=0;i<columns.length;i++)values.put(columns[i],appearance[i]);
        if(db.insertWithOnConflict("appearance",null,values,SQLiteDatabase.CONFLICT_REPLACE)==-1){
            error.setText(R.string.wardrobe_reference_save_failed);error.setVisibility(View.VISIBLE);return;
        }
        scenePreferences.edit().putString(friendPackage,scene).apply();
        saved=appearance.clone();savedScene=scene;render();
        findViewById(R.id.wardrobe_save).announceForAccessibility(getString(R.string.customize_saved));
    }

    void confirmDiscard(Runnable action) {
        if(!hasChanges()){action.run();return;}
        if(discardDialog!=null&&discardDialog.isShowing())return;
        discardDialog=new AlertDialog.Builder(this).setTitle(R.string.wardrobe_reference_discard_title)
                .setMessage(R.string.customize_unsaved)
                .setPositiveButton(R.string.customize_discard,(d,w)->{
                    appearance=saved.clone();scene=savedScene;render();action.run();
                })
                .setNegativeButton(R.string.customize_keep,null).create();
        ReferenceActions.dismissOnEscape(discardDialog);
        discardDialog.show();
        discardDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getColor(R.color.reference_danger));
        discardDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setFocusableInTouchMode(true);
        discardDialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }

    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("friend_id",friendId);state.putStringArray("appearance",appearance);state.putInt("slot",slot);
        state.putString("wardrobe_database",openedDatabase);state.putString("wardrobe_package",friendPackage);
        state.putString("wardrobe_scene",scene);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed(){confirmDiscard(this::finish);}
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(focused)ReferenceActions.systemBars(this);
    }
    @Override protected void onResume(){super.onResume();if(friendId>0)render();}
    @Override protected void onDestroy(){
        if(discardDialog!=null)discardDialog.dismiss();
        if(ownershipDialog!=null)ownershipDialog.dismiss();
        if(db!=null)db.close();super.onDestroy();
    }
}
