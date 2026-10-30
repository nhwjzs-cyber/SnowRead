package com.snowread.app;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.View;

/** Portrait cover with bounded wrapped title; custom images are cropped to the same frame. */
final class BookCoverView extends View {
    private String title;
    private int seed;private float bitmapAlpha=1;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    BookCoverView(Context context,String title,int seed) {
        super(context);this.title=title.replaceAll("\\s+"," ").trim();this.seed=seed;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void bind(String title,int seed){Motion.cancel(this);this.title=title.replaceAll("\\s+"," ").trim();this.seed=seed;bitmap=null;bitmapAlpha=1;invalidate();}
    void setBitmap(Bitmap value){setBitmap(value,false);}
    void setBitmap(Bitmap value,boolean reveal){if(bitmap==value)return;Motion.cancel(this);bitmap=value;bitmapAlpha=1;if(reveal&&value!=null&&isAttachedToWindow())Motion.interpolate(this,0,1,Motion.FAST,v->{bitmapAlpha=v;invalidate();});else invalidate();}
    @Override protected void onDetachedFromWindow(){Motion.cancel(this);bitmapAlpha=1;super.onDetachedFromWindow();}
    private float dp(float n) {return n*getResources().getDisplayMetrics().density;}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);float width=getWidth(),height=getHeight();
        RectF frame=new RectF(0,0,width,height);Path mask=new Path();mask.addRoundRect(frame,dp(7),dp(7),Path.Direction.CW);
        canvas.save();canvas.clipPath(mask);
        if(bitmap!=null&&!bitmap.isRecycled()) {
            float[] crop=CoverGeometry.centerCrop(bitmap.getWidth(),bitmap.getHeight(),getWidth(),getHeight());
            Rect source=new Rect((int)crop[0],(int)crop[1],(int)crop[2],(int)crop[3]);
            paint.setColor(0xFF638B99);canvas.drawRect(frame,paint);paint.setAlpha(Math.round(255*bitmapAlpha));canvas.drawBitmap(bitmap,source,frame,paint);paint.setAlpha(255);
        } else {
            int[] colors={0xFF43839A,0xFF5C929D,0xFF638BA6,0xFF6C9492,0xFF507C8D};
            paint.setColor(colors[(seed&Integer.MAX_VALUE)%colors.length]);canvas.drawRect(frame,paint);
            paint.setColor(0x44FFFFFF);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.6f));
            canvas.drawRoundRect(dp(7),dp(7),width-dp(7),height-dp(7),dp(2),dp(2),paint);paint.setStyle(Paint.Style.FILL);
            // Tiny continue-reading thumbnails use a book mark instead of illegible wrapped text.
            if(width<dp(60)){
                paint.setColor(0xDDF5F8FC);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));
                float left=width*.23f,right=width*.77f,top=height*.3f,bottom=height*.7f;
                canvas.drawRoundRect(left,top,right,bottom,dp(2),dp(2),paint);canvas.drawLine(width/2,top,width/2,bottom,paint);
                paint.setStyle(Paint.Style.FILL);canvas.restore();return;
            }
            int labelEnd=Math.min(160,title.length());
            if(labelEnd<title.length()&&labelEnd>0&&Character.isHighSurrogate(title.charAt(labelEnd-1))) labelEnd--;
            String label=title.substring(0,labelEnd);
            TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);text.setColor(0xFFF5F8FC);text.setTypeface(Typeface.create("serif",Typeface.NORMAL));
            int available=Math.max(1,(int)(width-dp(22)));StaticLayout layout=null;
            for(int size=14;size>=10;size--) {
                text.setTextSize(dp(size));
                layout=StaticLayout.Builder.obtain(label,0,label.length(),text,available)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(dp(3),1).build();
                if(layout.getLineCount()<=4&&layout.getHeight()<height-dp(36)) break;
            }
            layout=StaticLayout.Builder.obtain(label,0,label.length(),text,available)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setLineSpacing(dp(3),1)
                .setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build();
            canvas.save();canvas.translate(dp(11),Math.max(dp(15),(height-layout.getHeight()-dp(12))/2));layout.draw(canvas);canvas.restore();
            paint.setColor(0xAAFFFFFF);paint.setTextSize(dp(6));paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("融雪阅读",(width-paint.measureText("融雪阅读"))/2,height-dp(11),paint);
        }
        canvas.restore();
    }
}
