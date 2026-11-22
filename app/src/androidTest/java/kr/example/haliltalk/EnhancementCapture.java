package kr.example.haliltalk;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;

/** [U6 | 검증] 전환 중 다른 창이 찍히지 않도록 실제 포커스 창의 완료된 프레임을 복사한다. */
final class EnhancementCapture {
    static void capture(Instrumentation instrumentation,Window window,String name) throws Exception {
        capture(instrumentation,window,name,"enhancement-qa");
    }

    static void awaitFrame(Instrumentation instrumentation,Window window) throws Exception {
        View decor=window.getDecorView();
        CountDownLatch focused=new CountDownLatch(1);
        ViewTreeObserver.OnWindowFocusChangeListener focus=hasFocus->{
            if(hasFocus)focused.countDown();
        };
        instrumentation.runOnMainSync(()->{
            decor.getViewTreeObserver().addOnWindowFocusChangeListener(focus);
            if(decor.hasWindowFocus())focused.countDown();
        });
        try { Assert.assertTrue("Capture window focused",focused.await(5,TimeUnit.SECONDS)); }
        finally { instrumentation.runOnMainSync(()->decor.getViewTreeObserver().removeOnWindowFocusChangeListener(focus)); }
        CountDownLatch frame=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->{
            decor.getViewTreeObserver().registerFrameCommitCallback(frame::countDown);
            decor.invalidate();
        });
        Assert.assertTrue("Capture frame committed",frame.await(5,TimeUnit.SECONDS));
    }

    static void captureDisplay(Instrumentation instrumentation,Window window,String name,String folder) throws Exception {
        awaitFrame(instrumentation,window);
        Bitmap image=instrumentation.getUiAutomation().takeScreenshot();
        Assert.assertNotNull("Actual display screenshot",image);
        try{
            File dir=new File(instrumentation.getTargetContext().getExternalFilesDir(null),folder);
            Assert.assertTrue(dir.isDirectory()||dir.mkdirs());
            try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){
                Assert.assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));
            }
            writeTargets(instrumentation,window,name,folder,true);
        }finally{image.recycle();}
    }

    static void capture(Instrumentation instrumentation,Window window,String name,String folder) throws Exception {
        awaitFrame(instrumentation,window);
        View decor=window.getDecorView();
        CountDownLatch copied=new CountDownLatch(1);
        Bitmap[] image={null};
        int[] result={-1};
        instrumentation.runOnMainSync(()->{
            image[0]=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
            PixelCopy.request(window,image[0],code->{result[0]=code;copied.countDown();},new Handler(Looper.getMainLooper()));
        });
        try {
            Assert.assertTrue("Window copy completed",copied.await(5,TimeUnit.SECONDS));
            Assert.assertEquals("Window copy succeeded",PixelCopy.SUCCESS,result[0]);
            File dir=new File(instrumentation.getTargetContext().getExternalFilesDir(null),folder);
            Assert.assertTrue(dir.isDirectory()||dir.mkdirs());
            try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){
                Assert.assertTrue(image[0].compress(Bitmap.CompressFormat.PNG,100,out));
            }
            writeTargets(instrumentation,window,name,folder,false);
        } finally { if(image[0]!=null)image[0].recycle(); }
    }

    static void writeTargets(Instrumentation instrumentation,Window window,String name,String folder,boolean display) throws Exception {

        org.json.JSONObject data=new org.json.JSONObject();
        org.json.JSONObject targets=new org.json.JSONObject();
        int[] origin={0,0};
        Throwable[] error={null};
        instrumentation.runOnMainSync(()->{
            try{
                View decor=window.getDecorView();
                if(display)decor.getLocationOnScreen(origin);
                collectTargets(decor,origin,targets);
                data.put("display",display);data.put("targets",targets);
            }catch(Throwable failure){error[0]=failure;}
        });
        if(error[0]!=null)throw new AssertionError(error[0]);
        File directory=new File(instrumentation.getTargetContext().getExternalFilesDir(null),folder);
        Assert.assertTrue(directory.isDirectory()||directory.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".json"))){
            out.write(data.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static void collectTargets(View view,int[] origin,org.json.JSONObject targets) throws org.json.JSONException {
        android.graphics.Rect rect=new android.graphics.Rect();
        if(!view.getGlobalVisibleRect(rect)||rect.width()<4||rect.height()<4)return;
        String key=null;
        if(view.getId()!=View.NO_ID&&view.getId()!=0){
            try{key=view.getResources().getResourceEntryName(view.getId());}
            catch(android.content.res.Resources.NotFoundException generated){key=null;}
        }
        String text=view instanceof android.widget.TextView&&!(view instanceof android.widget.EditText)?
                ((android.widget.TextView)view).getText().toString():"";
        if(key==null&&view.getTag() instanceof String)key="tag:"+view.getTag();
        if(key==null&&!text.isEmpty()&&text.length()<100)key="text:"+text;
        if(key!=null){
            org.json.JSONArray found=targets.optJSONArray(key);
            if(found==null){found=new org.json.JSONArray();targets.put(key,found);}
            org.json.JSONObject item=new org.json.JSONObject();
            item.put("rect",new org.json.JSONArray(new int[]{rect.left+origin[0],rect.top+origin[1],
                    rect.right+origin[0],rect.bottom+origin[1]}));
            item.put("text",text);item.put("width",view.getWidth());item.put("height",view.getHeight());
            found.put(item);
        }
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)collectTargets(group.getChildAt(i),origin,targets);
        }
    }

    private EnhancementCapture() {}
}
