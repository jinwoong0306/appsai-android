package kr.example.haliltalk;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;

/** Native screenshot viewer, with a fitted base image and bounded pinch/pan. */
public final class GuideImageView extends ImageView {
    private final Matrix transform=new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private float zoom=1;
    private boolean singleTap;

    public GuideImageView(Context context,AttributeSet attributes) {
        super(context,attributes);
        setScaleType(ScaleType.MATRIX);
        scaleDetector=new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        scale(detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY());
                        return true;
                    }
                });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) {return true;}
            @Override public boolean onSingleTapUp(MotionEvent event) {singleTap=true;return true;}
            @Override public boolean onScroll(MotionEvent first,MotionEvent current,float dx,float dy) {
                if(!scaleDetector.isInProgress()&&zoom>1) {
                    pan(-dx,-dy);
                }
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        resetZoom();
    }

    public void resetZoom() {
        boolean wasZoomed=zoom>1;
        zoom=1;
        if(getDrawable()==null||getWidth()==0||getHeight()==0)return;
        float width=getDrawable().getIntrinsicWidth(),height=getDrawable().getIntrinsicHeight();
        float fit=Math.min(getWidth()/width,getHeight()/height);
        transform.setScale(fit,fit);
        transform.postTranslate((getWidth()-width*fit)/2,(getHeight()-height*fit)/2);
        setImageMatrix(transform);
        if(wasZoomed)sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void zoomBy(float factor) {
        scale(factor,getWidth()/2f,getHeight()/2f);
    }

    float zoomLevel() {return zoom;}

    private void scale(float factor,float x,float y) {
        if(getDrawable()==null)return;
        boolean wasZoomed=zoom>1;
        float next=Math.max(1,Math.min(6,zoom*factor));
        transform.postScale(next/zoom,next/zoom,x,y);
        zoom=next;
        constrain();
        if(wasZoomed!=(zoom>1))sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    private boolean pan(float dx,float dy) {
        if(zoom<=1||getDrawable()==null)return false;
        float[] values=new float[9];
        transform.getValues(values);
        float x=values[Matrix.MTRANS_X],y=values[Matrix.MTRANS_Y];
        transform.postTranslate(dx,dy);
        constrain();
        transform.getValues(values);
        boolean moved=Math.abs(x-values[Matrix.MTRANS_X])>.5f||Math.abs(y-values[Matrix.MTRANS_Y])>.5f;
        if(moved)sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED);
        return moved;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if(getDrawable()==null||zoom<=1)return;
        RectF bounds=new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());
        transform.mapRect(bounds);
        info.setScrollable(bounds.width()>getWidth()+.5f||bounds.height()>getHeight()+.5f);
        if(bounds.left<-.5f)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId(),getContext().getString(R.string.help_pan_left)));
        if(bounds.right>getWidth()+.5f)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId(),getContext().getString(R.string.help_pan_right)));
        if(bounds.top<-.5f)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId(),getContext().getString(R.string.help_pan_up)));
        if(bounds.bottom>getHeight()+.5f)info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId(),getContext().getString(R.string.help_pan_down)));
    }

    @Override public boolean performAccessibilityAction(int action,Bundle arguments) {
        if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.getId())return pan(getWidth()/3f,0);
        if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId())return pan(-getWidth()/3f,0);
        if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.getId())return pan(0,getHeight()/3f);
        if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId())return pan(0,-getHeight()/3f);
        return super.performAccessibilityAction(action,arguments);
    }

    @Override public boolean onKeyDown(int keyCode,KeyEvent event) {
        boolean moved=false;
        switch(keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:moved=pan(getWidth()/3f,0);break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:moved=pan(-getWidth()/3f,0);break;
            case KeyEvent.KEYCODE_DPAD_UP:moved=pan(0,getHeight()/3f);break;
            case KeyEvent.KEYCODE_DPAD_DOWN:moved=pan(0,-getHeight()/3f);break;
        }
        return moved||super.onKeyDown(keyCode,event);
    }

    private void constrain() {
        RectF bounds=new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());
        transform.mapRect(bounds);
        float dx=bounds.width()<=getWidth()?(getWidth()-bounds.width())/2-bounds.left:
                bounds.left>0?-bounds.left:bounds.right<getWidth()?getWidth()-bounds.right:0;
        float dy=bounds.height()<=getHeight()?(getHeight()-bounds.height())/2-bounds.top:
                bounds.top>0?-bounds.top:bounds.bottom<getHeight()?getHeight()-bounds.bottom:0;
        transform.postTranslate(dx,dy);
        setImageMatrix(transform);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        singleTap=false;
        scaleDetector.onTouchEvent(event);
        gestures.onTouchEvent(event);
        if(singleTap)performClick();
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
