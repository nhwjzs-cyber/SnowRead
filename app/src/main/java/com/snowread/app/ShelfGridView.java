package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.widget.GridView;

/** A single quiet shelf line per visible row, rather than one board under every book. */
final class ShelfGridView extends GridView {
    private final Paint board=new Paint(Paint.ANTI_ALIAS_FLAG);private final float density;
    ShelfGridView(Context context,boolean night){super(context);density=getResources().getDisplayMetrics().density;board.setColor(night?0x66294853:0x88DED3BE);}
    @Override protected void dispatchDraw(Canvas canvas){super.dispatchDraw(canvas);int columns=Math.max(1,getNumColumns());for(int i=0;i<getChildCount();i++){int item=getFirstVisiblePosition()+i;if((item+1)%columns==0||item==getCount()-1){float y=getChildAt(i).getBottom()+density;canvas.drawRoundRect(getPaddingLeft()+4*density,y,getWidth()-getPaddingRight()-4*density,y+2*density,density,density,board);}}}
}
