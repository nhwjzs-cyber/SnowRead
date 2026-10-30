package com.snowread.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small concentric strokes give an edge glow without blur surfaces or bitmap allocations. */
final class SoftGlowDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final float density,radius;private final int color;
    private float strength;
    SoftGlowDrawable(int color,float density,float radiusDp){this.color=color;this.density=density;radius=radiusDp*density;paint.setStyle(Paint.Style.STROKE);}
    void strength(float value){strength=Math.max(0,Math.min(1,value));invalidateSelf();}
    float strength(){return strength;}
    @Override public void draw(Canvas canvas){if(strength<=0)return;Rect r=getBounds();for(int layer=3;layer>=1;layer--){paint.setColor((Math.round((layer==1?150:24)*strength)<<24)|(color&0xFFFFFF));paint.setStrokeWidth(layer*density*1.5f);float inset=3*density;canvas.drawRoundRect(r.left+inset,r.top+inset,r.right-inset,r.bottom-inset,radius,radius,paint);}}
    @Override public void setAlpha(int alpha){}@Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
