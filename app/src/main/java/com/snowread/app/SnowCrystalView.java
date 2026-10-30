package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** A drifting flurry of ice crystals across the sheet's header band. One pass, never loops. */
final class SnowCrystalView extends View {
    static final long DURATION=3400,CLOSE_DURATION=Motion.SHEET_EXIT;
    private static final int COUNT=34;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path crystal=crystalPath();private final Particle[] particles=new Particle[COUNT];
    private final Random random=new Random();private final float density;private final boolean night;
    private float phase=1,tail=1;private boolean closing;private long lastDraw;
    private final Runnable start=()->play();
    private static final class Particle {float x,y,dx,dy,size,alpha,angle,spin;boolean crystal;}
    SnowCrystalView(Context context,boolean night){
        super(context);this.night=night;density=getResources().getDisplayMetrics().density;
        for(int i=0;i<COUNT;i++)particles[i]=new Particle();
        setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    static Path crystalPath(){
        Path shape=new Path();for(int arm=0;arm<6;arm++){
            double a=Math.PI*arm/3;float dx=(float)Math.cos(a),dy=(float)Math.sin(a),px=-dy,py=dx;
            shape.moveTo(0,0);shape.lineTo(dx,dy);
            shape.moveTo(dx*.56f+px*.22f,dy*.56f+py*.22f);shape.lineTo(dx*.79f,dy*.79f);shape.lineTo(dx*.56f-px*.22f,dy*.56f-py*.22f);
        }return shape;
    }
    static float opacity(float phase){
        if(phase<=0||phase>=1)return 0;
        if(phase<.13f)return phase/.13f;
        float remaining=(1-phase)/.87f;return remaining*remaining;
    }
    /** Sheet flurry envelope: quick fade-in, holds fully visible, then eases out so it reads clearly. */
    static float sheetFade(float phase){
        if(phase<=0||phase>=1)return 0;
        if(phase<.10f)return phase/.10f;
        if(phase>.66f){float remaining=(1-phase)/.34f;return remaining*remaining;}
        return 1f;
    }
    static void drawCrystal(Canvas c,Paint p,Path shape,float x,float y,float radius,float angle,float density,boolean night,float alpha){
        if(radius<=0||alpha<=0)return;
        c.save();c.translate(x,y);c.rotate(angle);c.scale(radius,radius);
        p.setStyle(Paint.Style.FILL);p.setColor(night?0xFFDFF3FB:0xFFDFEDF4);p.setAlpha(Math.round(255*alpha*.12f));c.drawCircle(0,0,1.55f,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(night?0xFF8FD0EA:0xFFA5D6EC);p.setAlpha(Math.round(255*alpha*.30f));p.setStrokeWidth(1.5f*density/radius);c.drawPath(shape,p); // icy light-blue micro glow under the white core
        p.setColor(night?0xFFCDE9F4:0xFFD3E9F1);p.setAlpha(Math.round(255*alpha*.8f));p.setStrokeWidth(.9f*density/radius);c.drawPath(shape,p);
        p.setColor(Color.WHITE);p.setAlpha(Math.round(255*alpha));p.setStrokeWidth(.55f*density/radius);c.drawPath(shape,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setAlpha(Math.round(255*alpha*.85f));c.drawCircle(0,0,.12f,p);c.restore();
    }
    static void drawGrain(Canvas c,Paint p,float x,float y,float radius,float angle,float density,boolean night,float alpha){
        if(radius<=0||alpha<=0)return;
        p.setStyle(Paint.Style.FILL);p.setColor(night?0xFFB4D6E4:0xFFBCD6E0);p.setAlpha(Math.round(255*alpha*.16f));c.drawCircle(x,y,radius*1.9f,p);
        c.save();c.translate(x,y);c.rotate(angle);c.scale(1,.64f);p.setColor(night?0xFFF2FBFF:Color.WHITE);p.setAlpha(Math.round(255*alpha));c.drawCircle(0,0,radius,p);c.restore();
    }
    void playAfter(long delay){stop();if(Motion.enabled(getContext()))postDelayed(start,delay);}
    private void seed(){
        for(int i=0;i<COUNT;i++){
            Particle p=particles[i];p.crystal=i%3==0;p.size=dp(p.crystal?3.2f+random.nextFloat()*2.4f:1.1f+random.nextFloat()*.9f);
            float left=dp(10)+random.nextFloat()*Math.max(0,getWidth()/2f-dp(40));p.x=i%2==0?left:getWidth()-left;p.y=dp(random.nextFloat()*16);
            p.dx=dp(4+random.nextFloat()*10)*(i%2==0?-1:1);p.dy=dp(14+random.nextFloat()*26);
            p.dx=Math.max(dp(5)-p.x,Math.min(getWidth()-dp(5)-p.x,p.dx));p.dy=Math.min(p.dy,dp(60)-p.y-p.size*2f);
            p.alpha=(night?.24f:.34f)+random.nextFloat()*(night?.14f:.20f);
            p.angle=random.nextFloat()*360;p.spin=(random.nextFloat()-.5f)*16;
        }
    }
    void play(){stop();begin();}
    /** Close trail: the already-visible flurry fades out from its current state; never re-seeds a fresh round. */
    void disperse(){
        if(start!=null)removeCallbacks(start);Motion.cancel(this);
        if(phase>=1||closing||!isAttachedToWindow()||getWidth()==0||!isShown()||!Motion.enabled(getContext()))return;
        closing=true;lastDraw=0;
        Motion.interpolateLinear(this,0,1,CLOSE_DURATION,value->{tail=1-value;long now=SystemClock.uptimeMillis();if(value>=1){phase=1;tail=1;}if(value>=1||now-lastDraw>=33){lastDraw=now;invalidate();}});
    }
    private void begin(){
        if(!isAttachedToWindow()||getWidth()==0||!isShown()||!Motion.enabled(getContext()))return;
        closing=false;tail=1;seed();phase=0;lastDraw=0;
        Motion.interpolateLinear(this,0,1,DURATION,value->{phase=value;long now=SystemClock.uptimeMillis();if(value>=1||now-lastDraw>=33){lastDraw=now;invalidate();}});
    }
    void stop(){if(start!=null)removeCallbacks(start);Motion.cancel(this);phase=1;tail=1;closing=false;invalidate();}
    boolean animating(){return phase<1&&isShown()&&Motion.enabled(getContext());}
    private float dp(float value){return value*density;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(phase>=1||!Motion.enabled(getContext()))return;
        c.save();c.clipRect(0,0,getWidth(),Math.min(getHeight(),dp(64)));
        float fade=sheetFade(phase)*tail;
        for(int i=0;i<COUNT;i++){
            Particle p=particles[i];float x=p.x+p.dx*phase,y=p.y+p.dy*phase,a=p.alpha*fade,r=p.size*(1-.18f*phase);
            if(phase>.72f)y-=(phase-.72f)/.28f*dp(6); // crystals disperse upward as the flurry ends
            if(p.crystal)drawCrystal(c,paint,crystal,x,y,r,p.angle+p.spin*phase,density,night,a);
            else drawGrain(c,paint,x,y,r,p.angle+p.spin*phase,density,night,a);
        }c.restore();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(ow>0&&(w!=ow||h!=oh))stop();}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int value){super.onWindowVisibilityChanged(value);if(value!=VISIBLE)stop();}
    @Override protected void onVisibilityChanged(View changed,int value){super.onVisibilityChanged(changed,value);if(value!=VISIBLE)stop();}
}
