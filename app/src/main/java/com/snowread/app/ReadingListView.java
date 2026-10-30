package com.snowread.app;

import android.content.Context;
import android.view.*;
import android.widget.ListView;

/** Observe center taps without swallowing scrolling or the child text-selection gestures. */
final class ReadingListView extends ListView {
    private final GestureDetector detector;
    private final Runnable manualScroll;
    private boolean doubleTapped;
    ReadingListView(Context context,Runnable centerTap,Runnable manualScroll,Runnable bookmark,boolean doubleBookmark) {
        super(context);this.manualScroll=manualScroll;
        detector=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) {return true;}
            @Override public boolean onSingleTapUp(MotionEvent event) {
                float x=event.getX();
                if(!doubleBookmark&&x>getWidth()*.33f&&x<getWidth()*.67f) {centerTap.run();return true;}
                return false;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent event){if(doubleBookmark&&event.getX()>getWidth()*.33f&&event.getX()<getWidth()*.67f){centerTap.run();return true;}return false;}
            @Override public boolean onDoubleTap(MotionEvent event){if(doubleBookmark&&event.getX()>getWidth()*.33f&&event.getX()<getWidth()*.67f){doubleTapped=true;bookmark.run();return true;}return false;}
        });
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)doubleTapped=false;detector.onTouchEvent(event);if(doubleTapped){MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();return true;}return super.dispatchTouchEvent(event);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_PAGE_UP||key==KeyEvent.KEYCODE_PAGE_DOWN)manualScroll.run();return super.onKeyDown(key,event);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)manualScroll.run();return super.performAccessibilityAction(action,args);}
}
