package com.snowread.app;

import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Swipe reveals explicit actions; a tap also reveals them for keyboard/accessibility users. */
final class SwipeActionRow extends FrameLayout {
    private final LinearLayout front,actions;
    private final int width,slop;
    private float downX,downY,start;
    private boolean dragging,open;
    private final Runnable opened;
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    SwipeActionRow(Context context,String name,int count,boolean dark,Runnable rename,Runnable delete,Runnable opened) {
        super(context);this.opened=opened;width=dp(168);slop=ViewConfiguration.get(context).getScaledTouchSlop();
        actions=new LinearLayout(context);actions.setGravity(Gravity.CENTER);actions.setBackground(SnowTheme.rounded(dark?SnowTheme.NIGHT_BACKGROUND:SnowTheme.ACCENT_SOFT,dp(14),0,0));
        actions.addView(action("重命名",dark?SnowTheme.NIGHT_ACCENT:SnowTheme.ACCENT_DARK,rename),new LinearLayout.LayoutParams(0,-1,1));actions.addView(action("删除",dark?0xFFFFB9AB:0xFF9A493E,delete),new LinearLayout.LayoutParams(0,-1,1));
        addView(actions,new FrameLayout.LayoutParams(width,-1,Gravity.RIGHT));
        front=new LinearLayout(context);front.setGravity(Gravity.CENTER_VERTICAL);front.setPadding(dp(14),dp(14),dp(14),dp(14));front.setMinimumHeight(dp(64));front.setBackground(SnowTheme.ripple(dark?SnowTheme.NIGHT_SURFACE:SnowTheme.SURFACE,dp(14),0x20248FA9));
        TextView label=new TextView(context);label.setText(name);label.setTextSize(15);label.setTextColor(dark?SnowTheme.NIGHT_INK:SnowTheme.INK);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);front.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView amount=new TextView(context);amount.setText(count+" 本  ‹");amount.setTextSize(12);amount.setTextColor(dark?SnowTheme.NIGHT_MUTED:SnowTheme.MUTED);amount.setPadding(dp(10),0,0,0);front.addView(amount);
        front.setFocusable(true);front.setContentDescription(name+"，"+count+" 本，点击或左滑显示重命名和删除");front.setOnClickListener(v->settle(!open));addView(front,new FrameLayout.LayoutParams(-1,-2));setClipChildren(true);updateAccessibility();
    }
    private Button action(String label,int color,Runnable run){Button b=new Button(getContext());b.setText(label);b.setTextSize(13);b.setTextColor(color);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);b.setBackgroundTintList(null);b.setBackground(SnowTheme.ripple(Color.TRANSPARENT,dp(12),0x20248FA9));b.setOnClickListener(v->run.run());return b;}
    @Override protected void onMeasure(int w,int h){int size=MeasureSpec.getSize(w);front.measure(MeasureSpec.makeMeasureSpec(size,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int height=Math.max(dp(64),front.getMeasuredHeight());actions.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));setMeasuredDimension(size,height);}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();start=front.getTranslationX();dragging=false;}
        else if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getX()-downX,dy=event.getY()-downY;if(Math.abs(dx)>slop&&Math.abs(dx)>Math.abs(dy)*1.3f){dragging=true;front.animate().cancel();getParent().requestDisallowInterceptTouchEvent(true);return true;}}
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(!dragging)return super.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_MOVE){front.setTranslationX(Math.min(0,Math.max(-width,start+event.getX()-downX)));return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){boolean show=event.getActionMasked()==MotionEvent.ACTION_CANCEL?open:front.getTranslationX()<-width*.4f;dragging=false;settle(show);return true;}return true;}
    void close(){settle(false);}
    private void settle(boolean show){open=show;front.animate().cancel();if(Motion.enabled(getContext()))front.animate().translationX(show?-width:0).setDuration(Motion.BASE).setInterpolator(Motion.EASE_OUT).start();else front.setTranslationX(show?-width:0);updateAccessibility();if(show)opened.run();}
    private void updateAccessibility(){actions.setImportantForAccessibility(open?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
    @Override protected void onDetachedFromWindow(){front.animate().cancel();super.onDetachedFromWindow();}
}
