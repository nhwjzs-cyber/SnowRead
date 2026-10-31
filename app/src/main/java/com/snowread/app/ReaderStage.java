package com.snowread.app;
import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
/** Overlay controls enter only after their bottom/top gravity has been laid out. */
final class ReaderStage extends FrameLayout {
    private View top,bottom;private boolean ready,targetVisible=true;
    ReaderStage(Context context){super(context);}
    void controls(View top,View bottom){this.top=top;this.bottom=bottom;ready=false;top.setVisibility(INVISIBLE);bottom.setVisibility(INVISIBLE);}
    boolean controlsReady(){return ready;}
    private int stableWidth,stableHeight;private ViewTreeObserver observer;
    private final ViewTreeObserver.OnPreDrawListener settle=()->{
        if(ready)return true;
        if(top==null||bottom==null||!hasWindowFocus()||getWidth()<=0||getHeight()<=0){stableWidth=stableHeight=0;return true;}
        LayoutParams tp=(LayoutParams)top.getLayoutParams(),bp=(LayoutParams)bottom.getLayoutParams();
        boolean anchored=top.getHeight()>0&&bottom.getHeight()>0&&top.getTop()==getPaddingTop()+tp.topMargin&&bottom.getBottom()==getHeight()-getPaddingBottom()-bp.bottomMargin;
        if(!anchored||isLayoutRequested()||stableWidth!=getWidth()||stableHeight!=getHeight()){stableWidth=getWidth();stableHeight=getHeight();postInvalidateOnAnimation();return true;}
        ready=true;snap(top);snap(bottom);return true;
    };
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();observer=getViewTreeObserver();observer.addOnPreDrawListener(settle);}
    @Override protected void onDetachedFromWindow(){if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(settle);observer=null;super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!ready){stableWidth=stableHeight=0;if(focus)postInvalidateOnAnimation();}}
    private void snap(View bar){Motion.reset(bar);bar.setVisibility(targetVisible?VISIBLE:GONE);}
    void showControls(boolean shown){
        targetVisible=shown;if(!ready)return;
        View[] bars={top,bottom};for(int i=0;i<bars.length;i++){View bar=bars[i];float nudge=i==0?-Motion.NUDGE:Motion.NUDGE;
            if(shown){boolean hidden=bar.getVisibility()!=VISIBLE||bar.getAlpha()<1;bar.setVisibility(VISIBLE);if(hidden)Motion.enter(bar,nudge);else Motion.reset(bar);}
            else Motion.exit(bar,nudge,Motion.BASE,()->{if(!targetVisible)bar.setVisibility(GONE);});
        }
    }
}
