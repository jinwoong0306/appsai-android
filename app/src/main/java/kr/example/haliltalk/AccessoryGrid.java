package kr.example.haliltalk;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/** Equal-width choice cards; the surrounding screen owns vertical scrolling. */
public final class AccessoryGrid extends ViewGroup {
    private int columns=1;
    private int[] rowHeights=new int[1];
    private final boolean horizontal;

    public AccessoryGrid(Context context,AttributeSet attrs){
        super(context,attrs);
        TypedArray direction=context.obtainStyledAttributes(attrs,new int[]{android.R.attr.orientation});
        horizontal=direction.getInt(0,1)==0;
        direction.recycle();
    }

    @Override public void onViewAdded(View child){
        super.onViewAdded(child);
        if(getChildCount()>rowHeights.length)rowHeights=new int[getChildCount()];
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);
        int gap=Math.round(8*getResources().getDisplayMetrics().density);
        java.util.Arrays.fill(rowHeights,0);
        if(horizontal){
            columns=Math.max(1,getChildCount());
            int cell=Math.round(80*getResources().getDisplayMetrics().density
                    *Math.max(1,getResources().getConfiguration().fontScale));
            for(int i=0;i<getChildCount();i++){
                View child=getChildAt(i);
                child.measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
                rowHeights[0]=Math.max(rowHeights[0],child.getMeasuredHeight());
            }
            for(int i=0;i<getChildCount();i++)getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(rowHeights[0],MeasureSpec.EXACTLY));
            int content=getPaddingLeft()+getPaddingRight()+getChildCount()*cell
                    +Math.max(0,getChildCount()-1)*gap;
            setMeasuredDimension(resolveSize(content,widthSpec),
                    resolveSize(getPaddingTop()+getPaddingBottom()+rowHeights[0],heightSpec));
            return;
        }
        int available=Math.max(0,width-getPaddingLeft()-getPaddingRight());
        int minimum=Math.round(100*getResources().getDisplayMetrics().density
                *Math.max(1,getResources().getConfiguration().fontScale));
        columns=Math.max(1,Math.min(3,(available+gap)/(minimum+gap)));
        int usable=Math.max(0,available-gap*(columns-1));
        int rows=(getChildCount()+columns-1)/columns;
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);
            int column=i%columns;
            int cellWidth=(column+1)*usable/columns-column*usable/columns;
            child.measure(MeasureSpec.makeMeasureSpec(cellWidth,MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            rowHeights[i/columns]=Math.max(rowHeights[i/columns],child.getMeasuredHeight());
        }
        int height=getPaddingTop()+getPaddingBottom()+Math.max(0,rows-1)*gap;
        for(int i=0;i<rows;i++)height+=rowHeights[i];
        for(int i=0;i<getChildCount();i++){
            View child=getChildAt(i);
            child.measure(MeasureSpec.makeMeasureSpec(child.getMeasuredWidth(),MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(rowHeights[i/columns],MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        int gap=Math.round(8*getResources().getDisplayMetrics().density);
        int usable=Math.max(0,right-left-getPaddingLeft()-getPaddingRight()-gap*(columns-1));
        int y=getPaddingTop();
        for(int i=0;i<getChildCount();i++){
            int column=i%columns;
            int x=getPaddingLeft()+column*usable/columns+column*gap;
            View child=getChildAt(i);
            if(getLayoutDirection()==LAYOUT_DIRECTION_RTL)
                x=right-left-getPaddingRight()-column*usable/columns-column*gap-child.getMeasuredWidth();
            child.layout(x,y,x+child.getMeasuredWidth(),y+rowHeights[i/columns]);
            if(column==columns-1)y+=rowHeights[i/columns]+gap;
        }
    }

    @Override protected LayoutParams generateDefaultLayoutParams(){
        return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
    }
}
