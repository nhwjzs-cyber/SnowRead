package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.widget.LinearLayout;

/** Cover lift changes only transforms; book title and layout are never remeasured per frame. */
final class ShelfTile extends LinearLayout {
    private float lift;private final Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);private final float density;private final int color;private final boolean compact;
    ShelfTile(Context context,boolean night){this(context,night,false);}
    /** Compact horizontal variant for the shelf's list view: fixed-height cover, no width-driven cover resize. */
    ShelfTile(Context context,boolean night,boolean compact){super(context);this.compact=compact;density=getResources().getDisplayMetrics().density;color=night?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT;setClipChildren(false);}
    void lift(boolean raised){Motion.interpolate(this,lift,raised?1:0,Motion.FAST,value->{lift=value;setTranslationY(-4*density*value);setScaleX(1+.015f*value);setScaleY(1+.015f*value);setTranslationZ(6*density*value);invalidate();});}
    void resetLift(){Motion.cancel(this);lift=0;setTranslationY(0);setScaleX(1);setScaleY(1);setTranslationZ(0);invalidate();}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
        if(!compact&&getChildCount()>0){android.view.ViewGroup.LayoutParams cover=getChildAt(0).getLayoutParams();int height=Math.min(Math.round(280*density),Math.round(width*1.45f));if(cover.height!=height){cover.height=height;getChildAt(0).setLayoutParams(cover);}}
        super.onMeasure(widthSpec,heightSpec);
    }
    @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);if(lift<=0||getChildCount()==0)return;android.view.View cover=getChildAt(0);glow.setStyle(Paint.Style.STROKE);for(int layer=3;layer>=1;layer--){glow.setStrokeWidth(layer*density);glow.setColor((Math.round(lift*(layer==1?130:24))<<24)|(color&0xFFFFFF));canvas.drawRoundRect(cover.getLeft(),cover.getTop(),cover.getRight(),cover.getBottom(),8*density,8*density,glow);}}
    @Override protected void onDetachedFromWindow(){resetLift();super.onDetachedFromWindow();}
}
