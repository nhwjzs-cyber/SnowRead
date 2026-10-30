package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.widget.FrameLayout;

/** A bounded, one-shot light sweep. Never schedules an idle animation. */
final class GlowFrame extends FrameLayout {
    private final Paint light=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();private final Matrix matrix=new Matrix();
    private float phase=1;private final float radius;private boolean pending;
    GlowFrame(Context context,float radiusDp){super(context);radius=radiusDp*getResources().getDisplayMetrics().density;setClipChildren(false);}
    void pulse(){
        if(!Motion.enabled(getContext())){phase=1;pending=false;invalidate();return;}
        if(!isAttachedToWindow()||getWidth()==0){pending=true;return;}
        pending=false;Motion.interpolate(this,0,1,500,value->{phase=value;invalidate();});
    }
    boolean glowing(){return phase<1&&isShown()&&Motion.enabled(getContext());}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);clip.reset();clip.addRoundRect(0,0,w,h,radius,radius,Path.Direction.CW);
        light.setShader(new LinearGradient(0,0,Math.max(1,w*.55f),0,new int[]{0x00FFFFFF,0x48FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP));
        if(pending)post(this::pulse);
    }
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);if(phase>=1)return;
        canvas.save();canvas.clipPath(clip);matrix.setTranslate(getWidth()*(phase*1.7f-.55f),0);if(light.getShader()!=null)light.getShader().setLocalMatrix(matrix);
        light.setAlpha(Math.round(255*(float)Math.sin(Math.PI*phase)));canvas.drawRect(0,0,getWidth(),getHeight(),light);canvas.restore();
    }
    @Override protected void onDetachedFromWindow(){Motion.cancel(this);phase=1;pending=false;super.onDetachedFromWindow();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(pending&&getWidth()>0)post(this::pulse);}
}
