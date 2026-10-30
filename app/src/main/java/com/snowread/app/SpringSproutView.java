package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Two flat sage sprouts unfurling from the sheet's bottom corners: spring follows the melting snow. One pass, never loops. */
final class SpringSproutView extends View {
    static final long DURATION=2900; // 0.8s unfurl, 1.5s hold, 0.6s fade
    private static final float UNFURL=.276f,HOLD_END=.793f;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path stem=new Path(),leaf=new Path();
    private final Runnable start=this::play;
    private final float density;private final boolean night;
    private float phase=1,tail=1;private long lastDraw;
    SpringSproutView(Context context,boolean night){
        super(context);this.night=night;density=getResources().getDisplayMetrics().density;
        setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    /** Scheduled so the sprout unfurls slightly after the flurry once the sheet has settled. */
    void playAfter(long delay){stop();if(Motion.enabled(getContext()))postDelayed(start,delay);}
    void play(){
        stop();if(!Motion.enabled(getContext()))return;
        phase=0;tail=1;lastDraw=0;
        Motion.interpolateLinear(this,0,1,DURATION,value->{phase=value;long now=SystemClock.uptimeMillis();if(value>=1||now-lastDraw>=33){lastDraw=now;invalidate();}});
    }
    void stop(){if(start!=null)removeCallbacks(start);Motion.cancel(this);phase=1;tail=1;invalidate();}
    /** Close path: fade the sprout away from its current state instead of vanishing abruptly. */
    void fadeOutNow(){
        if(start!=null)removeCallbacks(start);Motion.cancel(this);
        if(phase>=1||!isAttachedToWindow()||getWidth()==0||!Motion.enabled(getContext())){phase=1;invalidate();return;}
        Motion.interpolateLinear(this,0,1,140,value->{tail=1-value;long now=SystemClock.uptimeMillis();if(value>=1){phase=1;tail=1;}if(value>=1||now-lastDraw>=33){lastDraw=now;invalidate();}});
    }
    boolean animating(){return phase<1&&isShown()&&Motion.enabled(getContext());}
    private float dp(float value){return value*density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(phase>=1||!Motion.enabled(getContext()))return;
        float raw=Math.min(1f,phase/UNFURL),grow=1-(1-raw)*(1-raw);
        float fade=phase>HOLD_END?1f-(phase-HOLD_END)/(1f-HOLD_END):1f;fade*=fade;
        fade*=tail;
        if(fade<=0||grow<=0)return;
        paint.setStyle(Paint.Style.FILL);
        drawSprout(c,grow,fade,false);drawSprout(c,grow,fade,true);
    }
    /** Stem rises inside the 20dp side gutter so it never crosses the buttons; leaves unfold staggered. */
    private void drawSprout(Canvas c,float grow,float fade,boolean mirror){
        int stemColor=night?0xFF7E9D88:0xFF8CA487,nearColor=night?0xFFA3C2A6:0xFFA8BF9B,farColor=night?0xFF8FB194:0xFF97B18A;
        float h=getHeight(),x0=dp(9),x1=dp(10.5f),x2=dp(13),y0=h,y1=h-dp(24)*grow,y2=h-dp(46)*grow;
        c.save();
        if(mirror){c.translate(getWidth(),0);c.scale(-1,1);}
        stem.reset();stem.moveTo(x0,y0);stem.quadTo(x1,y1,x2,y2);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(stemColor);paint.setAlpha(Math.round(255*fade));
        paint.setStrokeWidth(dp(1.7f));c.drawPath(stem,paint);
        drawLeaf(c,quad(.62f,x0,x1,x2),quad(.62f,y0,y1,y2),dp(9.5f)*ease(clamp01((grow-.35f)/.65f)),-52f,nearColor,fade);
        drawLeaf(c,quad(.88f,x0,x1,x2),quad(.88f,y0,y1,y2),dp(7.5f)*ease(clamp01((grow-.55f)/.45f)),-128f,farColor,fade);
        c.restore();
    }
    private void drawLeaf(Canvas c,float bx,float by,float length,float angle,int color,float fade){
        if(length<=dp(.8f))return;
        c.save();c.translate(bx,by);c.rotate(angle);c.scale(length,length);
        paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setAlpha(Math.round(255*fade));
        leaf.reset();leaf.moveTo(0,0);leaf.quadTo(.5f,-.36f,1,0);leaf.quadTo(.5f,.22f,0,0);leaf.close();
        c.drawPath(leaf,paint);c.restore();
    }
    private static float quad(float t,float p0,float p1,float p2){float u=1-t;return u*u*p0+2*u*t*p1+t*t*p2;}
    private static float clamp01(float value){return value<0?0:value>1?1:value;}
    private static float ease(float value){return 1-(1-value)*(1-value);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(ow>0&&(w!=ow||h!=oh))stop();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int value){super.onWindowVisibilityChanged(value);if(value!=VISIBLE)stop();}
    @Override protected void onVisibilityChanged(View changed,int value){super.onVisibilityChanged(changed,value);if(value!=VISIBLE)stop();}
}
