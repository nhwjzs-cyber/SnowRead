package com.snowread.app;

import android.content.Context;
import android.view.*;
import android.view.animation.*;
import android.widget.FrameLayout;

/** Downward drag only at the start of a scrollable surface; horizontal controls keep their gestures. */
final class SheetDragLayout extends FrameLayout {
    private final View scrim;
    private final Runnable dismiss;
    private Runnable closing;
    private final int slop;
    private final boolean enabled;
    private final Window window;
    private float downX,downY,start,baseDim=Motion.SCRIM;
    private boolean eligible,dragging,settling;
    private Runnable decorated;
    private VelocityTracker velocity;
    SheetDragLayout(Context context,View scrim,Window window,boolean enabled,Runnable dismiss){super(context);this.scrim=scrim;this.window=window;this.dismiss=dismiss;this.enabled=enabled;slop=ViewConfiguration.get(context).getScaledTouchSlop();}
    private boolean scrollsUpAt(View view,float x,float y) {
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--){View child=group.getChildAt(i);float cx=x-child.getLeft()+view.getScrollX()-child.getTranslationX(),cy=y-child.getTop()+view.getScrollY()-child.getTranslationY();if(child.getVisibility()==VISIBLE&&cx>=0&&cy>=0&&cx<child.getWidth()&&cy<child.getHeight()&&scrollsUpAt(child,cx,cy))return true;}}
        return view.canScrollVertically(-1);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(settling)return true;if(!enabled)return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getRawX();downY=event.getRawY();start=getTranslationY();dragging=false;eligible=!scrollsUpAt(this,event.getX(),event.getY());if(velocity!=null)velocity.recycle();velocity=VelocityTracker.obtain();velocity.addMovement(event);}
        else if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(velocity!=null)velocity.addMovement(event);float dy=event.getRawY()-downY,dx=event.getRawX()-downX;if(eligible&&dy>slop&&dy>Math.abs(dx)*1.2f){start=getTranslationY();downY=event.getRawY();baseDim=window.getAttributes().dimAmount;Motion.cancelWindow(window);Motion.cancel(this);Motion.interpolate(this,getAlpha(),1,Motion.FAST,value->setAlpha(value));dragging=true;return true;}}
        else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){recycle();}
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(settling)return true;if(!enabled||!dragging)return super.onTouchEvent(event);
        if(velocity!=null)velocity.addMovement(event);
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){setTranslationY(Math.max(0,start+event.getRawY()-downY));fade();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){float speed=0;if(velocity!=null){velocity.computeCurrentVelocity(1000);speed=velocity.getYVelocity();}float density=getResources().getDisplayMetrics().density;boolean close=event.getActionMasked()==MotionEvent.ACTION_UP&&(getTranslationY()>Math.min(getHeight()*.24f,96*density)||(getTranslationY()>24*density&&speed>900*density));recycle();dragging=false;if(close)close();else Motion.settleSheet(this,window,decorated);return true;}
        return true;
    }
    void onClosing(Runnable action){closing=action;}
    void close(){if(settling)return;settling=true;if(closing!=null)closing.run();Motion.sheetOut(this,window,dismiss);}
    /** decorated runs only when the enter (or post-drag settle) animation completes naturally;
     *  a drag threshold hit clears the pending end action via Motion.cancel, so it cannot misfire. */
    void enter(Runnable onSettled){settling=false;decorated=onSettled;Motion.sheetIn(this,window,onSettled);}
    void snap(){decorated=null;Motion.finishTree(this);if(getWindowToken()!=null){Motion.reset(this);window.setDimAmount(Motion.SCRIM);}dragging=false;recycle();}
    private void fade(){window.setDimAmount(baseDim*Math.max(0,Math.min(1,1-(getTranslationY()-start)/Math.max(1,getHeight()))));}
    private void recycle(){if(velocity!=null){velocity.recycle();velocity=null;}}
    @Override protected void onDetachedFromWindow(){Motion.cancel(this);Motion.finishWindow(window);recycle();super.onDetachedFromWindow();}
}
