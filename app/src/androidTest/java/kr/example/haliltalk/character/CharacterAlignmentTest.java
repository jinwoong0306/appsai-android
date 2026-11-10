package kr.example.haliltalk.character;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.test.InstrumentationTestCase;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class CharacterAlignmentTest extends InstrumentationTestCase {
    public void testEveryAccessoryFitsEveryCharacterAndRenderSheets() throws Throwable {
        Context context=getInstrumentation().getTargetContext();
        File output=new File(context.getExternalFilesDir(null),"alignment");
        assertTrue(output.isDirectory()||output.mkdirs());
        String[] items=CharacterCatalog.allAccessoryIds();
        for(String character:CharacterCatalog.CHARACTER_IDS){
            Bitmap sheet=Bitmap.createBitmap(960,1160,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(sheet);
            canvas.drawColor(Color.rgb(255,249,245));
            Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.rgb(50,19,77));text.setTextSize(13);
            canvas.drawText(character,12,18,text);
            for(int index=-1;index<items.length;index++){
                String item=index<0?"none":items[index];
                String[] outfit={character,"none","none","none","none","none"};
                int part=0,layer=1;
                String slot=CharacterCatalog.slotFor(item);
                if("hat".equals(slot)){part=1;layer=6;}
                else if("face".equals(slot)){part=2;layer=4;}
                else if("body".equals(slot)){part=3;layer=2;}
                else if("back".equals(slot)){part=4;layer=0;}
                else if("prop".equals(slot)){part=5;layer=7;}
                if(part>0){
                    outfit[part]=item;
                    String path=CharacterCatalog.layerPaths(outfit[0],outfit[1],outfit[2],outfit[3],outfit[4],outfit[5])[layer];
                    try(InputStream stream=context.getAssets().open(path)){
                        Bitmap bitmap=BitmapFactory.decodeStream(stream);
                        RectF bounds=visibleBounds(bitmap);
                        CharacterView.accessoryMatrix(character,layer,item).mapRect(bounds);
                        assertTrue(character+"/"+item+" clipped: "+bounds,
                                bounds.left>=0&&bounds.top>=0&&bounds.right<=160&&bounds.bottom<=160);
                        bitmap.recycle();
                    }
                }
                int x=((index+1)%6)*160,y=((index+1)/6)*190+24;
                final int drawX=x,drawY=y;
                runTestOnUiThread(()->{
                    CharacterView view=new CharacterView(context);
                    view.setAppearance(outfit[0],outfit[1],outfit[2],outfit[3],outfit[4],outfit[5]);
                    int measure=View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY);
                    view.measure(measure,measure);view.layout(0,0,160,160);
                    int save=canvas.save();canvas.translate(drawX,drawY);
                    view.draw(canvas);canvas.restoreToCount(save);
                });
                canvas.drawText(index<0?"base":item,x+4,y+174,text);
            }
            try(FileOutputStream stream=new FileOutputStream(new File(output,character+".png"))){
                assertTrue(sheet.compress(Bitmap.CompressFormat.PNG,100,stream));
            }
            sheet.recycle();
        }
    }

    public void testEyeAttachmentCentersFollowCharacterPose() {
        float[] centers={25,29.5f,39,29.5f};
        CharacterView.accessoryMatrix("mail",4,"round_glasses").mapPoints(centers);
        assertEquals(66.5f,centers[0],0.01f);
        assertEquals(74f,centers[1],0.01f);
        assertEquals(94f,centers[2],0.01f);
        assertEquals(67f,centers[3],0.01f);
    }

    private RectF visibleBounds(Bitmap bitmap){
        int left=bitmap.getWidth(),top=bitmap.getHeight(),right=0,bottom=0;
        for(int y=0;y<bitmap.getHeight();y++)for(int x=0;x<bitmap.getWidth();x++){
            if(Color.alpha(bitmap.getPixel(x,y))==0)continue;
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
        }
        return new RectF(left,top,right,bottom);
    }
}
