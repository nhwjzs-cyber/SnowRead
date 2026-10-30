package com.snowread.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/** Ambience layer for the shelf background and sheet headers: a light wash that follows the local
 *  time of day plus live weather particles. Rain falls as slanted streaks with splash rings,
 *  snow drifts as grains and six-armed crystals, sun carries warm dust, overcast layers slow
 *  cloud shadows. Weather comes only from the manual pick. The shelf variant loops while visible; sheet headers play once. */
final class AtmosphereView extends View {
    static final int SUN=0,CLOUD=1,RAIN=2,SNOW=3;
    private static final int DROPS=46,FLAKES=44,SPLASH=10;
    private static final Set<AtmosphereView> attached=Collections.newSetFromMap(new WeakHashMap<AtmosphereView,Boolean>());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Path crystal=SnowCrystalView.crystalPath(),beam=new Path();
    private final float[] px=new float[18],py=new float[18],size=new float[18],speed=new float[18];
    private final float[] dropX=new float[DROPS],dropY=new float[DROPS],dropLen=new float[DROPS],dropV=new float[DROPS],dropA=new float[DROPS];
    private final float[] flakeX=new float[FLAKES],flakeY=new float[FLAKES],flakeR=new float[FLAKES],flakeV=new float[FLAKES],flakeP=new float[FLAKES],flakeSw=new float[FLAKES],flakeA=new float[FLAKES];
    private final boolean[] flakeCrystal=new boolean[FLAKES];
    private final float[] splashX=new float[SPLASH],splashY=new float[SPLASH],splashT=new float[SPLASH];
    private final float density;private final boolean dark,header;
    private int weather=SNOW,period=1,timeMode;private boolean enabled=true,playing,requested;
    private float time;private long lastTick,nextClock;private Shader wash,light,cloud;
    private int live=2,activeDrops,activeFlakes,nextSplash;
    private float rainSlant,snowWind;
    private final Runnable tick=()->step();
    static boolean enabled(Context c){return c.getSharedPreferences("atmosphere",0).getBoolean("enabled",true);}
    /** Manual pick, kept as-is so the panel always shows what the user chose. */
    static int manual(Context c){return Math.max(0,Math.min(3,c.getSharedPreferences("atmosphere",0).getInt("weather",SNOW)));}
    static int weather(Context c){return manual(c);}
    static int period(Context c){int setting=c.getSharedPreferences("atmosphere",0).getInt("time",0);if(setting>=1&&setting<=4)return setting-1;int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return hour>=6&&hour<10?0:hour>=10&&hour<17?1:hour>=17&&hour<20?2:3;}
    static String label(Context c){return new String[]{"晴","阴","雨","雪"}[weather(c)]+" · "+new String[]{"清晨","白昼","黄昏","夜间"}[period(c)];}
    /** Status text honouring dark appearance, which overrides the lighting period to night. */
    static String label(Context c,boolean dark){return new String[]{"晴","阴","雨","雪"}[weather(c)]+" · "+(dark?"夜间":new String[]{"清晨","白昼","黄昏","夜间"}[period(c)]);}
    static void refreshAll(){for(AtmosphereView view:new ArrayList<>(attached))view.refresh();}
    AtmosphereView(Context c,boolean dark,boolean header){
        super(c);this.dark=dark;this.header=header;density=getResources().getDisplayMetrics().density;
        Random random=new Random(7319);for(int i=0;i<px.length;i++){px[i]=random.nextFloat();py[i]=random.nextFloat();size[i]=.7f+random.nextFloat()*1.7f;speed[i]=3+random.nextFloat()*6;}
        setClickable(false);setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    private float dp(float n){return n*density;}
    void refresh(){
        stopTicks();SharedPreferences prefs=getContext().getSharedPreferences("atmosphere",0);enabled=enabled(getContext());weather=weather(getContext());timeMode=prefs.getInt("time",0);period=dark?3:period(getContext());live=2;time=0;nextClock=SystemClock.uptimeMillis()+60000;rebuild();invalidate();
        if(!header)requested=true;startIfVisible();
    }
    void playOnce(){requested=true;time=0;startIfVisible();invalidate();}
    void pause(){requested=false;stopTicks();}
    boolean animating(){return playing;}
    private void startIfVisible(){
        if(!requested||!enabled||playing||!isAttachedToWindow()||!isShown()||getWindowVisibility()!=VISIBLE||!hasWindowFocus()||!Motion.enabled(getContext()))return;
        playing=true;lastTick=SystemClock.uptimeMillis();postDelayed(tick,33);
    }
    private void stopTicks(){playing=false;removeCallbacks(tick);}
    private void step(){
        if(!playing)return;
        if(!enabled||!isShown()||getWindowVisibility()!=VISIBLE||!hasWindowFocus()||!Motion.enabled(getContext())){stopTicks();invalidate();return;}
        long now=SystemClock.uptimeMillis();float dt=Math.min(.08f,(now-lastTick)/1000f);lastTick=now;time+=dt;
        if(timeMode==0&&now>=nextClock){nextClock=now+60000;int current=dark?3:period(getContext());if(current!=period){period=current;rebuild();}}
        advance(dt);
        if(header&&time>=3.4f){pause();invalidate();return;}
        invalidate();postDelayed(tick,33);
    }
    private void rebuild(){
        if(getWidth()==0||getHeight()==0)return;
        int[] colors=period==0?new int[]{0x30F2D4A0,0x0CF4DBB5,0}:period==1?new int[]{0x22E8D9B1,0x09F0E6D2,0}:period==2?new int[]{0x38E4B191,0x16C7BCBC,0}:new int[]{0x35395379,0x162C6A87,0};
        wash=new LinearGradient(0,0,0,getHeight(),colors,new float[]{0,.55f,1},Shader.TileMode.CLAMP);
        int core=period==3?0x388DD9EF:period==2?0x40EDC298:0x3CF5D9A3;
        light=new RadialGradient(0,0,Math.max(dp(90),getWidth()*.68f),new int[]{core,0},null,Shader.TileMode.CLAMP);
        cloud=new RadialGradient(0,0,Math.max(dp(70),getWidth()*.48f),new int[]{dark?0x266B9AA9:0x1B8198AD,0},null,Shader.TileMode.CLAMP);
        beam.reset();beam.moveTo(getWidth()*.76f,-dp(12));beam.lineTo(getWidth()*.94f,0);beam.lineTo(getWidth()*.43f,getHeight()*.72f);beam.lineTo(getWidth()*.32f,getHeight()*.64f);beam.close();
        seed(weather);
    }
    /** Deterministic seed so the static (reduced-motion) frame is stable across redraws. */
    private void seed(int mode){
        int w=getWidth(),h=getHeight();if(w==0||h==0)return;
        float band=header?dp(56):h;
        Random r=new Random(5171);
        rainSlant=(.1f+r.nextFloat()*.14f)*(r.nextBoolean()?1:-1);
        snowWind=dp(10+r.nextFloat()*10);
        activeDrops=0;activeFlakes=0;
        float heavy=live==1?.6f:live==3?1.3f:1f;
        if(mode==RAIN){
            // Shelf rain hugs the side gutters, thin and sparse, so titles and covers stay clean.
            activeDrops=Math.max(6,Math.min(DROPS,Math.round((header?8:16)*heavy)));
            for(int i=0;i<DROPS;i++){
                float side=r.nextBoolean()?.06f+r.nextFloat()*.12f:.82f+r.nextFloat()*.12f;
                dropX[i]=header?r.nextFloat():side;dropY[i]=r.nextFloat()*band;
                dropLen[i]=dp(header?4.5f+r.nextFloat()*4.5f:9+r.nextFloat()*9)*(i%7==0?1.4f:1);
                dropV[i]=dp(header?150+r.nextFloat()*110:430+r.nextFloat()*330)*(live==1?.85f:live==3?1.2f:1);
                dropA[i]=(dark?.12f:.16f)+r.nextFloat()*.12f;
            }
            for(int i=0;i<SPLASH;i++){splashX[i]=r.nextBoolean()?.08f+r.nextFloat()*.1f:.82f+r.nextFloat()*.1f;splashY[i]=header?dp(30+r.nextFloat()*4):h-dp(3+r.nextFloat()*8);splashT[i]=r.nextFloat();}
        }else if(mode==SNOW){
            activeFlakes=Math.max(5,Math.min(FLAKES,Math.round((header?10:14)*heavy)));
            for(int i=0;i<FLAKES;i++){
                flakeX[i]=r.nextFloat();flakeY[i]=r.nextFloat()*band;
                flakeCrystal[i]=i%3==0;
                flakeR[i]=dp(header?1.4f+r.nextFloat()*2.2f:1.3f+r.nextFloat()*2.4f)*(flakeCrystal[i]?1.35f:1)*(i%9==0?1.5f:1);
                flakeV[i]=dp(header?18+r.nextFloat()*18:24+r.nextFloat()*40)*(live==1?.85f:live==3?1.15f:1)*(i%5==0?1.45f:1);
                flakeP[i]=r.nextFloat()*(float)(Math.PI*2);
                flakeSw[i]=dp(4+r.nextFloat()*12);
                flakeA[i]=(dark?.28f:.38f)+r.nextFloat()*.26f;
            }
        }
    }
    private void advance(float dt){
        if(!Motion.enabled(getContext()))return;
        float band=header?dp(56):getHeight();if(band<=0)return;
        for(int i=0;i<activeDrops;i++){
            dropY[i]+=dropV[i]*dt;
            if(dropY[i]>band+dp(14)){
                if(!header&&dropLen[i]>dp(11)&&activeDrops>12){splashX[nextSplash]=dropX[i];splashY[nextSplash]=getHeight()-dp(3+dropX[i]*6);splashT[nextSplash]=0;nextSplash=(nextSplash+1)%SPLASH;}
                dropY[i]=-dropLen[i]-(float)Math.random()*dp(40);dropX[i]=(float)Math.random();
            }
        }
        for(int i=0;i<activeFlakes;i++){
            flakeP[i]+=dt*(.5f+(i%3)*.22f);
            flakeY[i]+=flakeV[i]*dt;
            if(flakeY[i]>band+dp(10)){flakeY[i]=-dp(6)-(float)Math.random()*dp(20);flakeX[i]=(float)Math.random();flakeP[i]=(float)(Math.random()*Math.PI*2);}
        }
        for(int i=0;i<SPLASH;i++)if(splashT[i]<1f)splashT[i]=Math.min(1f,splashT[i]+dt*2.1f);
    }
    private float envelope(){if(!header)return 1;if(!requested&&time==0)return 0;return Math.min(1,time/.25f)*Math.min(1,Math.max(0,(3.4f-time)/.8f));}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(!enabled||wash==null)return;float strength=envelope();if(strength<=0)return;
        boolean motion=Motion.enabled(getContext());float t=motion?time:0;
        c.save();if(header)c.clipRect(0,0,getWidth(),dp(34));
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(Math.round(255*strength));paint.setShader(wash);c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);
        if(weather==SUN){
            c.save();c.translate(getWidth()*(period==2?.12f:.88f)+dp(3)*(float)Math.sin(t*.25f),header?0:getHeight()*.08f);paint.setShader(light);paint.setAlpha(Math.round((170+15*(float)Math.sin(t*.3f))*strength));c.drawCircle(0,0,Math.max(dp(90),getWidth()*.68f),paint);c.restore();paint.setShader(null);
            paint.setColor(period==3?0x068FD4EB:0x08FFF5D5);paint.setAlpha(Math.round(8*strength));c.drawPath(beam,paint);
            if(motion)dust(c,t,strength,false);
        }else if(weather==CLOUD){
            for(int i=0;i<3;i++){c.save();c.translate(getWidth()*(.18f+i*.34f)+dp(12)*(float)Math.sin(t*.10f+i),header?dp(9):getHeight()*(.13f+i*.09f));c.scale(1,.32f);paint.setShader(cloud);paint.setAlpha(Math.round(190*strength));c.drawCircle(0,0,Math.max(dp(70),getWidth()*.48f),paint);c.restore();}
            for(int i=0;i<2;i++){c.save();c.translate(getWidth()*(.06f+i*.52f)+dp(20)*(float)Math.sin(t*.16f+i*2.1f),header?dp(16):getHeight()*(.05f+i*.16f));c.scale(1,.3f);paint.setShader(cloud);paint.setAlpha(Math.round(120*strength));c.drawCircle(0,0,Math.max(dp(40),getWidth()*.26f),paint);c.restore();}
            paint.setShader(null);
        }else if(weather==RAIN)rain(c,t,strength,motion);
        else if(weather==SNOW)snow(c,t,strength,motion);
        c.restore();paint.setShader(null);paint.setAlpha(255);
    }
    private void rain(Canvas c,float t,float strength,boolean motion){
        int color=dark?0xFFB7D9E4:0xFF9FB9C4;
        float sway=(float)Math.sin(t*.45f)*dp(9),swayNext=(float)Math.sin(t*.45f+.35f)*dp(9);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);
        float bottom=getHeight(),fadeZone=dp(56);
        for(int i=0;i<activeDrops;i++){
            float x=dropX[i]*getWidth()+sway*(i%2==0?1:-1)*.4f,y=dropY[i],len=Math.max(dropLen[i],motion?dropV[i]*.035f:dropLen[i]);
            float dx=len*rainSlant+(swayNext-sway)*.2f;
            float near=header?1f:Math.max(0,Math.min(1,(bottom-y)/fadeZone)); // drops dissolve before reaching the viewport bottom
            paint.setStrokeWidth(dp(header?.9f:1f));
            int base=Math.round(255*dropA[i]*strength*(motion?1:.55f)*near);
            paint.setAlpha(Math.round(base*.45f));c.drawLine(x-dx,y-len,x-dx*.5f,y-len*.5f,paint);
            paint.setAlpha(base);c.drawLine(x-dx*.5f,y-len*.5f,x,y,paint);
        }
        paint.setStrokeWidth(dp(1));
        for(int i=0;i<SPLASH;i++){
            float p=splashT[i];if(p>=1f)continue;
            float x=splashX[i]*getWidth(),r=dp(2+8*p);
            paint.setAlpha(Math.round(55*(1-p)*(1-p)*strength));
            c.drawOval(x-r,splashY[i]-r*.32f,x+r,splashY[i]+r*.32f,paint);
        }
        if(!header)for(int i=0;i<2;i++){
            float phase=(t*.16f+i*.5f)%1,r=dp(3+14*phase),cx=getWidth()*(i==0?.10f:.88f),line=getHeight()-dp(3);
            paint.setAlpha(Math.round(16*(1-phase)*strength));
            c.drawOval(cx-r,line-r*.32f,cx+r,line+r*.32f,paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    private void snow(Canvas c,float t,float strength,boolean motion){
        float drift=motion?snowWind*(float)Math.sin(t*.22f):0;
        float ground=header?dp(34):getHeight(),fadeZone=header?dp(10):dp(44);
        for(int i=0;i<activeFlakes;i++){
            float x=flakeX[i]*getWidth()+(float)Math.sin(flakeP[i])*flakeSw[i]+drift*(flakeR[i]/dp(3));
            if(x<-dp(14))x+=getWidth()+dp(28);else if(x>getWidth()+dp(14))x-=getWidth()+dp(28);
            float near=Math.max(0,Math.min(1,(ground-flakeY[i])/fadeZone)); // flakes fade out as they near the bottom
            float a=flakeA[i]*strength*(motion?1:.6f)*(.82f+.18f*(float)Math.sin(flakeP[i]*2.3f))*near;
            if(flakeCrystal[i])SnowCrystalView.drawCrystal(c,paint,crystal,x,flakeY[i],flakeR[i],i*37+time*10,density,dark,a);
            else SnowCrystalView.drawGrain(c,paint,x,flakeY[i],flakeR[i],i*19,density,dark,a);
        }
    }
    private void dust(Canvas c,float t,float strength,boolean snowy){
        int count=header?8:18;float h=header?dp(30):getHeight();
        for(int i=0;i<count;i++){
            float x=px[i]*getWidth()+dp(3)*(float)Math.sin(t*.23f+i),y=(py[i]*h+t*dp(speed[i]*(snowy?1:.35f)))%(h+dp(8))-dp(4);
            if(header&&Math.abs(x-getWidth()/2f)<dp(32))continue;
            float a=(dark?.18f:.24f)*(.65f+.35f*(float)Math.sin(t*.32f+i))*strength;
            if(snowy&&i%5==0)SnowCrystalView.drawCrystal(c,paint,crystal,x,y,dp(size[i]),i*23+t*2,density,dark,a);
            else SnowCrystalView.drawGrain(c,paint,x,y,dp(snowy?size[i]*.55f:.65f),i*19,density,dark,a);
        }
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);rebuild();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached.add(this);refresh();}
    @Override protected void onDetachedFromWindow(){stopTicks();attached.remove(this);super.onDetachedFromWindow();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)startIfVisible();else stopTicks();}
    @Override protected void onWindowVisibilityChanged(int value){super.onWindowVisibilityChanged(value);if(tick==null)return;if(value==VISIBLE)startIfVisible();else stopTicks();}
    @Override protected void onVisibilityChanged(View view,int value){super.onVisibilityChanged(view,value);if(tick==null)return;if(value==VISIBLE)startIfVisible();else stopTicks();}
}
