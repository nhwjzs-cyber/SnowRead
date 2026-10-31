package com.snowread.app;

import android.graphics.*;

/** Finite, deterministic ice dust. All drawing stays in the side margins, outside the body. */
final class PageTurnDust {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    void draw(Canvas canvas,float t,int direction,int width,int height,int left,int right,float density,boolean dark){
        if(t<=0||t>=1)return;
        float margin=direction>0?left:right;
        if(margin<2*density)return;
        canvas.save();canvas.clipRect(direction>0?0:width-right,0,direction>0?left:width,height);
        int rgb=dark?0xA6CFDA:0x77AABA;
        for(int i=0;i<18;i++){
            float delay=(i%4)*.035f,age=(t-delay)/(1-delay);if(age<0||age>1)continue;
            float seed=((i*37)%101)/100f;
            float x=margin*(.42f+.18f*seed)+(float)Math.sin(i*2.7f+age*2)*density*2.5f;
            if(direction<0)x=width-x;
            float y=height*(.08f+.84f*((i*53)%101)/100f)-age*density*(7+seed*11);
            float alpha=Math.min(1,age/.14f)*(float)Math.pow(1-age,1.6);
            paint.setColor((Math.round((dark?126:108)*Math.max(0,alpha))<<24)|rgb);
            float radius=density*(.55f+seed*.65f)*(1-age*.3f);canvas.drawCircle(x,y,radius,paint);
            if(i%4==0){paint.setStrokeWidth(density*.55f);canvas.drawLine(x-radius*2,y,x+radius*2,y,paint);canvas.drawLine(x,y-radius*2,x,y+radius*2,paint);}
        }
        canvas.restore();
    }
}
