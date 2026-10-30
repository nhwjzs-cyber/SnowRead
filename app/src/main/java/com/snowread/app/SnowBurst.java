package com.snowread.app;

import android.animation.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.View;
import java.util.WeakHashMap;

/** One bounded overlay per control; never adds layout children or intercepts input. */
final class SnowBurst extends Drawable implements View.OnAttachStateChangeListener,View.OnLayoutChangeListener {
    private static final WeakHashMap<View,SnowBurst> active=new WeakHashMap<>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private final View anchor;private final boolean night;private final float density;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path crystal=SnowCrystalView.crystalPath();
    private ValueAnimator animator;private float phase;private boolean added;
    private final Runnable expire=()->finish();
    static void play(View anchor,boolean night){
        stop(anchor);if(!Motion.enabled(anchor.getContext()))return;
        SnowBurst burst=new SnowBurst(anchor,night);active.put(anchor,burst);anchor.addOnAttachStateChangeListener(burst);anchor.addOnLayoutChangeListener(burst);
        main.postDelayed(burst.expire,1400);burst.ready();
    }
    static void stop(View anchor){SnowBurst burst=active.get(anchor);if(burst!=null)burst.finish();}
    static boolean playing(View anchor){return active.containsKey(anchor);}
    private SnowBurst(View anchor,boolean night){this.anchor=anchor;this.night=night;density=anchor.getResources().getDisplayMetrics().density;}
    private void ready(){
        if(active.get(anchor)!=this||animator!=null)return;
        if(!Motion.enabled(anchor.getContext())){finish();return;}
        if(!anchor.isAttachedToWindow()||anchor.getWidth()==0||anchor.getHeight()==0)return;
        if(!anchor.isShown()||anchor.getWindowVisibility()!=View.VISIBLE){finish();return;}
        main.removeCallbacks(expire);setBounds(0,0,anchor.getWidth(),anchor.getHeight());anchor.getOverlay().add(this);added=true;
        animator=ValueAnimator.ofFloat(0,1);animator.setDuration(Motion.scaled(anchor.getContext(),1100));animator.setInterpolator(new android.view.animation.LinearInterpolator());
        animator.addUpdateListener(value->{if(active.get(anchor)!=this)return;if(!anchor.isShown()||anchor.getWindowVisibility()!=View.VISIBLE||!Motion.enabled(anchor.getContext())){finish();return;}phase=(Float)value.getAnimatedValue();invalidateSelf();});
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator value){finish();}});animator.start();
    }
    private void finish(){
        if(active.get(anchor)!=this)return;active.remove(anchor);main.removeCallbacks(expire);
        anchor.removeOnAttachStateChangeListener(this);anchor.removeOnLayoutChangeListener(this);
        ValueAnimator old=animator;animator=null;if(old!=null){old.removeAllListeners();old.removeAllUpdateListeners();old.cancel();}
        if(added){anchor.getOverlay().remove(this);added=false;}
    }
    @Override public void draw(Canvas canvas){
        float cx=getBounds().centerX(),cy=getBounds().centerY();float radius=Math.min(3.4f*density,Math.min(cx,cy)*.16f);
        if(radius<=0)return;
        float reach=.70f+.30f*phase,rx=Math.max(0,cx-radius-2*density)*reach,ry=Math.max(0,cy-radius-2*density)*reach;
        for(int i=0;i<8;i++){double angle=Math.PI*(i/4.0-.5);float size=radius*(i%2==0?1:.76f);float x=cx+(float)Math.cos(angle)*rx,y=cy+(float)Math.sin(angle)*ry,alpha=SnowCrystalView.opacity(phase)*(night?.30f:.42f);if(i%3==0)SnowCrystalView.drawCrystal(canvas,paint,crystal,x,y,size,i*15,density,night,alpha);else SnowCrystalView.drawGrain(canvas,paint,x,y,size*.42f,i*15,density,night,alpha);}
    }
    @Override public void onViewAttachedToWindow(View view){ready();}
    @Override public void onViewDetachedFromWindow(View view){finish();}
    @Override public void onLayoutChange(View view,int l,int t,int r,int b,int ol,int ot,int or,int ob){if(added)setBounds(0,0,view.getWidth(),view.getHeight());else ready();}
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
