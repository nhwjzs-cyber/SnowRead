package com.snowread.app;

import android.animation.*;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.*;
import android.widget.*;
import java.util.*;

/** Motion v1 tokens and shared primitives. Only transforms/alpha and determinate progress animate. */
final class Motion {
    static final long FAST=120,BASE=180,SHEET=260,SHEET_EXIT=220,PAGE=240;
    static final float NUDGE=8,SHEET_IN=24,SHEET_OUT=18,TOAST_IN=12,TOAST_OUT=6,CONTENT_NUDGE=4,SCRIM=.32f,PRESS_SCALE=.97f;
    static final DecelerateInterpolator EASE_OUT=new DecelerateInterpolator(1.4f);
    private static final DecelerateInterpolator IN=EASE_OUT;
    private static final AccelerateInterpolator OUT=new AccelerateInterpolator(1.2f);
    /** Spec §2 promised a third curve; it was never implemented. */
    private static final PathInterpolator STD=new PathInterpolator(.4f,0f,.2f,1f);
    private static final PathInterpolator SHEET_CURVE=new PathInterpolator(.2f,0f,.2f,1f);
    /** Tiny overshoot (1.06) for gesture release only; decorative bounce stays out. */
    private static final OvershootInterpolator BACK=new OvershootInterpolator(1.06f);
    private static final WeakHashMap<View,Task> running=new WeakHashMap<>();
    private static final WeakHashMap<Window,ValueAnimator> dimmers=new WeakHashMap<>();
    private static final WeakHashMap<ProgressBar,ValueAnimator> progress=new WeakHashMap<>();
    private static final WeakHashMap<View,ValueAnimator> frames=new WeakHashMap<>();
    private static final WeakHashMap<View,android.graphics.drawable.Drawable> glowOriginals=new WeakHashMap<>();
    private static final class Task {final float alpha,y,scale;final Runnable end;Task(float a,float y,float s,Runnable end){alpha=a;this.y=y;scale=s;this.end=end;}}
    /** Callback for animating a float on a self-drawn control. */
    interface Frame {void apply(float value);}
    /**
     * Cached accessibility state. This used to be queried on every animation start, which put a
     * cross-process binder call exactly at the moment the animation had to begin on time.
     */
    private static volatile boolean cachedTouchExploration;
    private static volatile float cachedScale=1f;
    private static volatile long cachedAt=0L;
    private static final long SCALE_TTL_MS=30_000L;
    private static synchronized void refreshAccessibility(Context context){
        AccessibilityManager manager=(AccessibilityManager)context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        cachedTouchExploration=manager!=null&&manager.isTouchExplorationEnabled();
        // Only needed below API 26, where areAnimatorsEnabled() does not exist.
        if(Build.VERSION.SDK_INT<26){
            float value=1f;
            try{value=Settings.Global.getFloat(context.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f);}catch(Exception ignored){}
            cachedScale=value<0f?0f:value;
        }
        cachedAt=System.currentTimeMillis();
    }
    /** Called on resume and on the theme tick so a changed system setting is picked up promptly. */
    static void invalidateCache(Context context){refreshAccessibility(context);}
    /**
     * Base duration in milliseconds. The framework applies the system animation scale on top of
     * this, so this value must stay unscaled:
     * ValueAnimator.getScaledDuration() == mDuration * sDurationScale, and WindowManagerGlobal
     * pushes the system value into this process via ValueAnimator.setDurationScale().
     * Pre-multiplying here produced a squared scale (a 2x setting turned 240ms into 960ms).
     */
    static long scaled(Context context,long duration){return duration;}
    static boolean enabled(Context context){
        if(context.getSharedPreferences("reader",Context.MODE_PRIVATE).getBoolean("reduceMotion",false))return false;
        // areAnimatorsEnabled() reflects the system scale (including the 0 = off case) without a
        // binder call, and it stays in sync with the framework's own duration scaling.
        if(Build.VERSION.SDK_INT>=26){if(!ValueAnimator.areAnimatorsEnabled())return false;}
        else if(cachedScale<=0f)return false;
        if(cachedAt==0L||System.currentTimeMillis()-cachedAt>SCALE_TTL_MS)refreshAccessibility(context);
        return !cachedTouchExploration;
    }
    static float dp(View view,float value){return value*view.getResources().getDisplayMetrics().density;}
    static void cancel(View view){SnowBurst.stop(view);running.remove(view);ValueAnimator frame=frames.remove(view);if(frame!=null)frame.cancel();if(glowOriginals.containsKey(view))view.setForeground(glowOriginals.remove(view));view.animate().setListener(null).withEndAction(null).cancel();view.setLayerType(View.LAYER_TYPE_NONE,null);if(view instanceof ProgressBar){ValueAnimator old=progress.remove(view);if(old!=null)old.cancel();}}
    static void flash(View view){
        if(!enabled(view.getContext()))return;
        if(glowOriginals.containsKey(view))view.setForeground(glowOriginals.remove(view));
        android.graphics.drawable.Drawable original=view.getForeground();glowOriginals.put(view,original);
        SoftGlowDrawable glow=new SoftGlowDrawable(SnowTheme.ACCENT,view.getResources().getDisplayMetrics().density,14);view.setForeground(glow);
        interpolate(view,1,0,420,value->{glow.strength(value);if(value<=0&&view.getForeground()==glow){view.setForeground(original);glowOriginals.remove(view);}});
    }
    private static final WeakHashMap<View,Boolean> visibilityTargets=new WeakHashMap<>();
    /** Repeated playback snapshots do not restart a transition; reversals keep current alpha. */
    static void visible(View view,boolean shown){
        Boolean old=visibilityTargets.get(view);
        if(old!=null&&old==shown)return;
        visibilityTargets.put(view,shown);
        if(old==null&&((view.getVisibility()==View.VISIBLE)==shown)){reset(view);return;}
        if(shown)enter(view,NUDGE);
        else exit(view,NUDGE,()->{if(Boolean.FALSE.equals(visibilityTargets.get(view))){view.setVisibility(View.GONE);view.setAlpha(1);view.setTranslationY(0);}});
    }
    static void reset(View view){cancel(view);view.setAlpha(1);view.setTranslationY(0);view.setScaleX(1);view.setScaleY(1);}
    private static int forcedCompletionDepth;
    static boolean forcedCompletion(){return forcedCompletionDepth>0;}
    static void finishTree(View view){forcedCompletionDepth++;try{finishTreeImpl(view);}finally{forcedCompletionDepth--;}}
    private static void finishTreeImpl(View view){if(view==null)return;SnowBurst.stop(view);if(view instanceof PagedReaderView)((PagedReaderView)view).finishMotion();if(view instanceof AtmosphereView)((AtmosphereView)view).pause();if(view instanceof SnowCrystalView)((SnowCrystalView)view).stop();if(view instanceof SpringSproutView)((SpringSproutView)view).stop();if(view instanceof ProgressBar){ValueAnimator animation=progress.remove(view);if(animation!=null)animation.end();}if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=parent.getChildCount()-1;i>=0;i--)finishTree(parent.getChildAt(i));}ValueAnimator custom=frames.remove(view);if(custom!=null)custom.end();Task task=running.get(view);if(task!=null){cancel(view);view.setAlpha(task.alpha);view.setTranslationY(task.y);view.setScaleX(task.scale);view.setScaleY(task.scale);if(task.end!=null)task.end.run();}}
    private static void to(View view,float alpha,float y,float scale,long duration,Interpolator curve,Runnable end){
        cancel(view);
        if(!enabled(view.getContext())){view.setAlpha(alpha);view.setTranslationY(y);view.setScaleX(scale);view.setScaleY(scale);if(end!=null)end.run();return;}
        long actual=scaled(view.getContext(),duration);
        Task task=new Task(alpha,y,scale,end);running.put(view,task);
        // Spec §8.5: promote the animating view to a hardware layer so its subtree is not
        // re-rasterized on every frame. Restored to NONE when the animation ends.
        view.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        view.animate().alpha(alpha).translationY(y).scaleX(scale).scaleY(scale).setDuration(actual).setInterpolator(curve).withEndAction(()->{if(running.get(view)==task){running.remove(view);view.setLayerType(View.LAYER_TYPE_NONE,null);if(end!=null)end.run();}}).start();
    }
    static void enter(View view,float fromYDp){enter(view,fromYDp,BASE);}
    static void enter(View view,float fromYDp,long duration){boolean interrupted=running.containsKey(view);cancel(view);view.setVisibility(View.VISIBLE);if(enabled(view.getContext())&&!interrupted){view.setAlpha(0);view.setTranslationY(dp(view,fromYDp));}to(view,1,0,1,duration,IN,null);}
    static void exit(View view,float toYDp,Runnable end){exit(view,toYDp,BASE,end);}
    static void exit(View view,float toYDp,long duration,Runnable end){to(view,0,dp(view,toYDp),1,duration,OUT,end);}
    static void press(View view){to(view,1,0,PRESS_SCALE,FAST,OUT,null);}
    static void release(View view){to(view,1,0,1,FAST,IN,null);}
    static void confirm(View view){cancel(view);if(enabled(view.getContext())){view.setScaleX(.8f);view.setScaleY(.8f);view.setAlpha(0);}to(view,1,0,1,BASE,IN,null);}
    static void badge(View view){cancel(view);if(enabled(view.getContext())){view.setScaleX(.9f);view.setScaleY(.9f);view.setAlpha(0);}to(view,1,0,1,FAST,IN,null);}
    /** Content swap: the outgoing view leaves while the incoming one settles in. */
    static void crossFade(View out,View in,long duration){
        if(out==in){if(in!=null)to(in,1,0,1,duration,STD,null);return;}
        if(out!=null)to(out,0,0,1,duration,OUT,null);
        if(in!=null){
            in.setVisibility(View.VISIBLE);
            if(enabled(in.getContext())){in.setAlpha(0);in.setTranslationY(dp(in,CONTENT_NUDGE));}
            to(in,1,0,1,duration,STD,null);
        }
    }
    static void progress(ProgressBar bar,int value){value=Math.max(0,Math.min(bar.getMax(),value));ValueAnimator old=progress.remove(bar);if(old!=null)old.cancel();if(!enabled(bar.getContext())||bar.isIndeterminate()){bar.setProgress(value);return;}ValueAnimator animator=ValueAnimator.ofInt(bar.getProgress(),value);progress.put(bar,animator);animator.setDuration(scaled(bar.getContext(),FAST));animator.setInterpolator(OUT);animator.addUpdateListener(a->bar.setProgress((Integer)a.getAnimatedValue()));animator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){if(progress.get(bar)==a)progress.remove(bar);}});animator.start();}
    /**
     * Update text without resetting alpha. The previous version faded the whole label out and in on
     * every value change, so a 120ms progress ticker looked like a flickering display. Only the very
     * first appearance fades in; later changes are applied immediately.
     */
    static void text(TextView view,String value){
        if(value.contentEquals(view.getText()))return;
        boolean first=view.getText().length()==0;
        view.setText(value);
        if(first&&enabled(view.getContext())){view.setAlpha(0);to(view,1,0,1,FAST,IN,null);}
    }
    /** Animate a 0..1 float on a self-drawn control (toggle thumb, swatch ring). */
    private static final Interpolator LINEAR=new LinearInterpolator();
    static void interpolate(View view,float from,float to,long duration,Frame frame){interpolate(view,from,to,duration,STD,frame);}
    static void interpolateLinear(View view,float from,float to,long duration,Frame frame){interpolate(view,from,to,duration,LINEAR,frame);}
    private static void interpolate(View view,float from,float to,long duration,Interpolator curve,Frame onFrame){
        ValueAnimator old=frames.remove(view);if(old!=null)old.cancel();
        if(!enabled(view.getContext())){onFrame.apply(to);return;}
        ValueAnimator animator=ValueAnimator.ofFloat(from,to);frames.put(view,animator);
        animator.setDuration(scaled(view.getContext(),duration));animator.setInterpolator(curve);
        animator.addUpdateListener(a->onFrame.apply((Float)a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){if(frames.get(view)==a)frames.remove(view);}});
        animator.start();
    }
    private static void dim(Window window,float target,long duration,Context context){ValueAnimator old=dimmers.remove(window);if(old!=null)old.cancel();if(!enabled(context)){window.setDimAmount(target);return;}ValueAnimator animator=ValueAnimator.ofFloat(window.getAttributes().dimAmount,target);dimmers.put(window,animator);animator.setDuration(scaled(context,duration));animator.setInterpolator(SHEET_CURVE);animator.addUpdateListener(a->window.setDimAmount((Float)a.getAnimatedValue()));animator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){if(dimmers.get(window)==a)dimmers.remove(window);}});animator.start();}
    static void sheetIn(View panel,Window window,Runnable end){cancel(panel);if(enabled(panel.getContext())){panel.setAlpha(0);panel.setTranslationY(dp(panel,SHEET_IN));}to(panel,1,0,1,SHEET,SHEET_CURVE,end);dim(window,SCRIM,SHEET,panel.getContext());}
    static void sheetOut(View panel,Window window,Runnable end){dim(window,0,SHEET_EXIT,panel.getContext());to(panel,0,panel.getTranslationY()+dp(panel,SHEET_OUT),1,SHEET_EXIT,SHEET_CURVE,end);}
    /** Gesture release settles back with a small, physical overshoot. */
    static void settleSheet(View panel,Window window,Runnable end){to(panel,1,0,1,BASE,BACK,end);dim(window,SCRIM,BASE,panel.getContext());}
    static void cancelWindow(Window window){ValueAnimator animator=dimmers.remove(window);if(animator!=null)animator.cancel();}
    static void finishWindow(Window window){ValueAnimator animator=dimmers.remove(window);if(animator!=null)animator.end();}
}
