package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;
import java.util.*;

/** Bookmark dots have their own touch strip; normal seeking remains native and accessible. */
final class BookmarkSeekBar extends SeekBar {
    interface Actions {void bookmark(int offset);void chapters();}
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Handler main=new Handler(Looper.getMainLooper());private final Actions actions;
    private final ArrayList<Integer> marks=new ArrayList<>();private int length=1,candidate=-1;private float downX,downY;private boolean longPressed;
    private float emphasis;private int emphasized=-1;
    private void emphasize(int offset,boolean shown){emphasized=offset;Motion.interpolate(this,emphasis,shown?1:0,Motion.FAST,v->{emphasis=v;invalidate();});}
    private final Runnable longPress=()->openChapters();
    private void openChapters(){longPressed=true;candidate=-1;emphasize(emphasized,false);MotionEvent cancel=MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,downX,downY,0);super.onTouchEvent(cancel);cancel.recycle();actions.chapters();}
    BookmarkSeekBar(Context context,Actions actions){super(context);this.actions=actions;setPadding(dp(12),dp(14),dp(12),dp(2));paint.setColor(SnowTheme.ACCENT_DARK);setContentDescription("阅读进度，顶部圆点为书签；长按跳转章号");}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    void bookmarks(Collection<Integer> positions,int length,boolean dark){this.length=Math.max(1,length-1);marks.clear();marks.addAll(positions);Collections.sort(marks);paint.setColor(dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK);invalidate();}
    private float x(int offset){return getPaddingLeft()+(getWidth()-getPaddingLeft()-getPaddingRight())*offset/(float)length;}
    @Override protected synchronized void onDraw(Canvas canvas){super.onDraw(canvas);for(Integer p:marks){boolean near=p==emphasized&&emphasis>0;canvas.drawCircle(x(p),dp(5),dp(2)+(near?dp(1.5f)*emphasis:0),paint);if(near){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setAlpha(Math.round(160*emphasis));canvas.drawCircle(x(p),dp(5),dp(5),paint);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);}}}
    @Override public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();candidate=-1;longPressed=false;float closest=dp(12);if(downY<=dp(15))for(Integer p:marks){float distance=Math.abs(x(p)-downX);if(distance<closest){closest=distance;candidate=p;}}emphasize(candidate,candidate>=0);main.postDelayed(longPress,ViewConfiguration.getLongPressTimeout());}
        if(action==MotionEvent.ACTION_MOVE&&Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){main.removeCallbacks(longPress);candidate=-1;emphasize(emphasized,false);}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){main.removeCallbacks(longPress);emphasize(emphasized,false);if(action==MotionEvent.ACTION_CANCEL)candidate=-1;if(longPressed)return true;if(action==MotionEvent.ACTION_UP&&candidate>=0){int target=candidate;candidate=-1;actions.bookmark(target);performClick();return true;}}
        if(candidate>=0)return true;return super.onTouchEvent(e);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.addAction(new AccessibilityNodeInfo.AccessibilityAction(0x7f0f0201,"跳到下一书签"));info.addAction(new AccessibilityNodeInfo.AccessibilityAction(0x7f0f0202,"跳转章节"));}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==0x7f0f0202){actions.chapters();return true;}if(action==0x7f0f0201){int offset=(int)(length*getProgress()/(double)Math.max(1,getMax()));for(Integer p:marks)if(p>offset){actions.bookmark(p);return true;}return false;}return super.performAccessibilityAction(action,args);}
    @Override protected void onDetachedFromWindow(){main.removeCallbacks(longPress);Motion.cancel(this);emphasis=0;candidate=-1;super.onDetachedFromWindow();}
}
