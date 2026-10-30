package com.snowread.app;

import android.view.*;

/** Consume the remaining gesture after either finger lifts; never turn a page on release. */
final class BrightnessGesture {
    boolean consuming,started,finished;float value;
    private int first,second;private float startY,base;
    void reset(){consuming=started=finished=false;}
    boolean handle(MotionEvent e,View body,float current){started=finished=false;int action=e.getActionMasked();if(!consuming&&action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()==2&&body!=null){int[] at=new int[2];body.getLocationInWindow(at);for(int i=0;i<2;i++)if(e.getX(i)<at[0]||e.getX(i)>at[0]+body.getWidth()||e.getY(i)<at[1]||e.getY(i)>at[1]+body.getHeight())return false;consuming=started=true;first=e.getPointerId(0);second=e.getPointerId(1);startY=(e.getY(0)+e.getY(1))/2;base=value=current;}
        if(!consuming)return false;if(action==MotionEvent.ACTION_MOVE){int a=e.findPointerIndex(first),b=e.findPointerIndex(second);if(a>=0&&b>=0)value=ReaderPolicy.brightness(false,base+(startY-(e.getY(a)+e.getY(b))/2)/Math.max(1,body.getHeight())*1.5f);}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){consuming=false;finished=true;}return true;
    }
}
