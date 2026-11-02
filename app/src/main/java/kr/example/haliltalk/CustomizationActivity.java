package kr.example.haliltalk;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import kr.example.haliltalk.character.CharacterCatalog;
import kr.example.haliltalk.character.CharacterView;
import kr.example.haliltalk.data.FriendsDatabase;

public final class CustomizationActivity extends FeatureActivity {
    SQLiteDatabase db;
    int friendId, slot;
    String friendName="";
    String[] appearance={"chat","none","none","none","none","none"};
    String[] saved=appearance.clone();
    final ArrayList<Integer> friendIds=new ArrayList<>();
    final ArrayList<String> friendNames=new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db=new FriendsDatabase(this).getWritableDatabase();
        setContentView(R.layout.screen_customization);
        findViewById(R.id.open_settings).setOnClickListener(v->confirmDiscard(this::openSettings));
        findViewById(R.id.wardrobe_friend).setOnClickListener(v->chooseFriend());
        findViewById(R.id.wardrobe_save).setOnClickListener(v->saveAppearance());
        findViewById(R.id.wardrobe_cancel).setOnClickListener(v->{appearance=saved.clone();render();});
        BottomNavigation.bind(this,R.id.nav_customize,destination->confirmDiscard(()->openMain(destination==R.id.nav_apps)));
        try(Cursor cursor=db.rawQuery("SELECT id,name FROM friends ORDER BY id DESC",null)){
            while(cursor.moveToNext()){friendIds.add(cursor.getInt(0));friendNames.add(cursor.getString(1));}
        }
        if(friendIds.isEmpty()){
            findViewById(R.id.wardrobe_empty).setVisibility(View.VISIBLE);
            findViewById(R.id.wardrobe_content).setVisibility(View.GONE);
            findViewById(R.id.wardrobe_friend).setVisibility(View.GONE);
            findViewById(R.id.wardrobe_actions).setVisibility(View.GONE);
            return;
        }
        int restored=state==null?getIntent().getIntExtra("friend_id",friendIds.get(0)):state.getInt("friend_id");
        loadFriend(friendIds.contains(restored)?restored:friendIds.get(0));
        if(state!=null){
            String[] draft=state.getStringArray("appearance");
            if(draft!=null){appearance=draft;slot=state.getInt("slot");render();}
        }
    }

    void chooseFriend() {
        new AlertDialog.Builder(this).setTitle(R.string.customize_select)
                .setItems(friendNames.toArray(new String[0]),(dialog,index)->
                        confirmDiscard(()->loadFriend(friendIds.get(index))))
                .setNegativeButton(android.R.string.cancel,null).show();
    }

    void loadFriend(int id) {
        friendId=id;friendName=friendNames.get(friendIds.indexOf(id));
        appearance=new String[]{"chat","none","none","none","none","none"};
        try(Cursor cursor=db.rawQuery("SELECT character,hat,face,body,back,prop FROM appearance WHERE friend=?",new String[]{""+id})){
            if(cursor.moveToFirst())for(int i=0;i<6;i++)appearance[i]=cursor.getString(i);
        }
        saved=appearance.clone();
        ((Button)findViewById(R.id.wardrobe_friend)).setText(friendName);
        render();
    }

    void render() {
        ((CharacterView)findViewById(R.id.wardrobe_preview)).setAppearance(
                appearance[0],appearance[1],appearance[2],appearance[3],appearance[4],appearance[5]);
        LinearLayout bases=findViewById(R.id.wardrobe_characters);bases.removeAllViews();
        for(int i=0;i<CharacterCatalog.CHARACTER_IDS.length;i++){
            String id=CharacterCatalog.CHARACTER_IDS[i];
            addChoice(bases,id,CharacterCatalog.CHARACTER_NAMES[i],0);
        }
        LinearLayout tabs=findViewById(R.id.wardrobe_tabs);tabs.removeAllViews();
        String[] names=getResources().getStringArray(R.array.accessory_slot_names);
        for(int i=0;i<names.length;i++){
            int index=i;
            Button tab=new Button(this,null,0,R.style.SecondaryButton);
            tab.setText(slot==i?getString(R.string.selected_choice_format,names[i]):names[i]);
            tab.setSelected(slot==i);tab.setTextColor(getColor(slot==i?R.color.on_primary:R.color.ink));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.setMarginEnd(dp(8));tabs.addView(tab,p);
            tab.setOnClickListener(v->{slot=index;render();});
        }
        String[][] ids={CharacterCatalog.HAT_IDS,CharacterCatalog.FACE_IDS,CharacterCatalog.BODY_IDS,CharacterCatalog.BACK_IDS,CharacterCatalog.PROP_IDS};
        String[][] labels={CharacterCatalog.HAT_NAMES,CharacterCatalog.FACE_NAMES,CharacterCatalog.BODY_NAMES,CharacterCatalog.BACK_NAMES,CharacterCatalog.PROP_NAMES};
        LinearLayout items=findViewById(R.id.wardrobe_items);items.removeAllViews();
        for(int i=0;i<ids[slot].length;i++)addChoice(items,ids[slot][i],labels[slot][i],slot+1);
        findViewById(R.id.wardrobe_save).setEnabled(!Arrays.equals(saved,appearance));
        findViewById(R.id.wardrobe_cancel).setEnabled(!Arrays.equals(saved,appearance));
    }

    void addChoice(LinearLayout parent,String id,String label,int part) {
        View row=getLayoutInflater().inflate(R.layout.row_choice,parent,false);
        row.setTag(id);boolean selected=id.equals(appearance[part]);row.setSelected(selected);
        String[] preview=appearance.clone();preview[part]=id;
        ((CharacterView)row.findViewById(R.id.choice_character)).setAppearance(preview[0],preview[1],preview[2],preview[3],preview[4],preview[5]);
        ((TextView)row.findViewById(R.id.choice_label)).setText(label);
        row.findViewById(R.id.choice_check).setVisibility(selected?View.VISIBLE:View.INVISIBLE);
        row.setContentDescription(selected?getString(R.string.selected_choice_format,label):label);
        row.setOnClickListener(v->{appearance[part]=id;render();});
        parent.addView(row);
    }

    void saveAppearance() {
        ContentValues values=new ContentValues();values.put("friend",friendId);
        String[] columns={"character","hat","face","body","back","prop"};
        for(int i=0;i<columns.length;i++)values.put(columns[i],appearance[i]);
        db.insertWithOnConflict("appearance",null,values,SQLiteDatabase.CONFLICT_REPLACE);
        saved=appearance.clone();render();
        Toast.makeText(this,R.string.customize_saved,Toast.LENGTH_SHORT).show();
    }

    void confirmDiscard(Runnable action) {
        if(Arrays.equals(saved,appearance)){action.run();return;}
        new AlertDialog.Builder(this).setMessage(R.string.customize_unsaved)
                .setPositiveButton(R.string.customize_discard,(d,w)->action.run())
                .setNegativeButton(R.string.customize_keep,null).show();
    }

    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("friend_id",friendId);state.putStringArray("appearance",appearance);state.putInt("slot",slot);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed(){confirmDiscard(this::finish);}
    @Override protected void onDestroy(){db.close();super.onDestroy();}
}
