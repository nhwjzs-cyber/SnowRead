package com.snowread.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.*;
import android.widget.*;

/** Long-task progress, coalesced to at most one main-thread render per 120ms. */
final class TaskProgress extends LinearLayout {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ProgressBar bar;
    private final TextView label;
    private int completed,total;
    private String message="正在准备…";
    private boolean queued,disposed;
    private long rendered;
    TaskProgress(Context context,boolean dark){super(context);setOrientation(VERTICAL);label=new TextView(context);label.setTextSize(14);label.setTextColor(dark?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED);label.setPadding(0,0,0,dp(12));addView(label);bar=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgressTintList(ColorStateList.valueOf(dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK));bar.setIndeterminateTintList(ColorStateList.valueOf(dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK));addView(bar,new LayoutParams(-1,dp(3)));report(0,0,"正在准备…");}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    synchronized void report(int done,int count,String title){completed=done;total=count;message=title;if(!queued&&!disposed){queued=true;main.postDelayed(this::render,Math.max(0,Motion.FAST-(SystemClock.uptimeMillis()-rendered)));}}
    private void render(){int done,count;String title;synchronized(this){queued=false;if(disposed)return;done=completed;count=total;title=message;}rendered=SystemClock.uptimeMillis();boolean animate=getWindowVisibility()==VISIBLE&&Motion.enabled(getContext());int height=dp(count>0?3:2);if(bar.getLayoutParams().height!=height){bar.getLayoutParams().height=height;bar.requestLayout();}bar.setIndeterminate(count<=0&&animate);if(count>0){int value=(int)(1000L*done/Math.max(1,count));if(animate)Motion.progress(bar,value);else bar.setProgress(value);}else if(!animate)bar.setProgress(0);String value=(count>0?Math.min(100,100L*done/count)+"% · ":"")+title;if(animate)Motion.text(label,value);else{Motion.reset(label);label.setText(value);}label.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);}
    void dispose(){synchronized(this){disposed=true;}main.removeCallbacksAndMessages(null);Motion.cancel(bar);Motion.cancel(label);}
    @Override protected void onDetachedFromWindow(){dispose();super.onDetachedFromWindow();}
}
