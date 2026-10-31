package com.snowread.app;

import android.graphics.*;
import android.text.StaticLayout;

/** Curved paper back, reflected ink and directional light; no screen-sized bitmap allocations. */
final class PageCurlRenderer {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path back=new Path();
    void draw(Canvas canvas,StaticLayout old,StaticLayout next,float width,float height,float progress,int direction,int paper){
        canvas.drawColor(paper);next.draw(canvas);
        if(progress>=1)return;
        boolean reverse=direction<0;
        canvas.save();if(reverse){canvas.translate(width,0);canvas.scale(-1,1);}
        PageCurlGeometry g=new PageCurlGeometry(progress,width,height);
        canvas.save();canvas.clipRect(0,0,g.crease,height);canvas.drawColor(paper);front(canvas,old,width,reverse);canvas.restore();
        if(progress<=0){canvas.restore();return;}
        boolean dark=(.2126f*Color.red(paper)+.7152f*Color.green(paper)+.0722f*Color.blue(paper))<90;
        float shadow=Math.max(2,width*.035f);
        paint.setShader(new LinearGradient(g.crease,0,g.crease+shadow,0,Color.argb(dark?65:42,0,0,0),Color.TRANSPARENT,Shader.TileMode.CLAMP));
        canvas.drawRect(g.crease,0,g.crease+shadow,height,paint);paint.setShader(null);
        back.reset();back.moveTo(g.crease,0);back.lineTo(g.crease,height);
        back.cubicTo(g.crease-g.fold*.25f,height-g.lift,g.tip+g.bulge,height-g.lift,g.tip,height-g.lift*2);
        back.quadTo(g.tip+g.bulge,height*.5f,g.tip,g.lift*2);
        back.cubicTo(g.tip+g.bulge,g.lift,g.crease-g.fold*.25f,g.lift,g.crease,0);back.close();
        int shade=blend(paper,Color.BLACK,dark?.12f:.09f),light=blend(paper,Color.WHITE,dark?.07f:.18f);
        paint.setShader(new LinearGradient(g.tip,0,g.crease,0,new int[]{shade,light,paper,shade},new float[]{0,.22f,.83f,1},Shader.TileMode.CLAMP));
        canvas.drawPath(back,paint);paint.setShader(null);
        // Very faint mirrored ink visible through the paper. Night mode keeps it especially quiet.
        int layer=canvas.saveLayerAlpha(0,0,width,height,dark?10:19);
        canvas.clipPath(back);canvas.translate(g.crease*2,0);canvas.scale(-1,1);front(canvas,old,width,reverse);canvas.restoreToCount(layer);
        paint.setColor(blend(paper,dark?Color.WHITE:Color.BLACK,dark?.15f:.12f));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f);canvas.drawPath(back,paint);paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }
    private void front(Canvas canvas,StaticLayout page,float width,boolean reverse){canvas.save();if(reverse){canvas.translate(width,0);canvas.scale(-1,1);}page.draw(canvas);canvas.restore();}
    private static int blend(int a,int b,float f){return Color.rgb(Math.round(Color.red(a)*(1-f)+Color.red(b)*f),Math.round(Color.green(a)*(1-f)+Color.green(b)*f),Math.round(Color.blue(a)*(1-f)+Color.blue(b)*f));}
}
