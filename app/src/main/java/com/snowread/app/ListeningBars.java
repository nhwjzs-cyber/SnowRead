package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Decorative playback status. Rendering stops on pause, invisibility or reduced motion. */
final class ListeningBars extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private boolean playing;
    ListeningBars(Context context,int color){super(context);paint.setColor(color);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void playing(boolean value){playing=value;invalidate();}
    boolean animating(){return playing&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE&&Motion.enabled(getContext());}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);boolean moving=animating();float unit=getWidth()/7f;double time=SystemClock.uptimeMillis()/260.0;for(int i=0;i<3;i++){float h=getHeight()*(moving?(.3f+.65f*(float)Math.abs(Math.sin(time+i*.9))):(.25f+i*.12f));float x=unit*(1+i*2);canvas.drawRoundRect(x,(getHeight()-h)/2,x+unit,(getHeight()+h)/2,unit/2,unit/2,paint);}if(moving)postInvalidateOnAnimation();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility==VISIBLE)invalidate();}
    @Override protected void onVisibilityChanged(View view,int visibility){super.onVisibilityChanged(view,visibility);if(visibility==VISIBLE)invalidate();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();invalidate();}
}
