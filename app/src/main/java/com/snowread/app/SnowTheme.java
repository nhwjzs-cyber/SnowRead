package com.snowread.app;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;

/** Shared visual tokens for the shelf, reader controls and dialogs. */
final class SnowTheme {
    static final int ACCENT=0xFF248FA9;
    static final int ACCENT_DARK=0xFF0F5F73;
    static final int ACCENT_SOFT=0xFFE2F3F6;
    static final int BACKGROUND=0xFFF3EDE0;
    static final int PAPER=0xFFF8F2E8;
    static final int WARM_PAPER=0xFFF0E5D1;
    static final int SURFACE=0xFFFFFCF5;
    static final int INK=0xFF193E4B;
    static final int MUTED=0xFF506873;
    static final int BORDER=0xFFD6E2E4;
    static final int NIGHT_BACKGROUND=0xFF11252E;
    static final int NIGHT_SURFACE=0xFF1A333E;
    static final int NIGHT_INK=0xFFDCEFF3;
    static final int NIGHT_MUTED=0xFF92B1BD;
    static final int NIGHT_ACCENT=0xFF8CD6E5;
    /** Translucent paper tint. Keep child text/icons opaque instead of fading the whole toolbar. */
    static GradientDrawable readerToolbar(int surface,boolean dark,float radius,float strokeWidth){
        int rgb=surface&0x00FFFFFF;
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{(dark?0xE8000000:0xEE000000)|rgb,(dark?0xD8000000:0xDC000000)|rgb});
        glass.setCornerRadius(radius);
        glass.setStroke(Math.max(1,Math.round(strokeWidth)),dark?0x668CB8C7:0x99D6E2E4);
        return glass;
    }
    static GradientDrawable rounded(int color,float radius,int stroke,float strokeWidth) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(radius);
        if(strokeWidth>0)shape.setStroke(Math.max(1,Math.round(strokeWidth)),stroke);return shape;
    }
    static RippleDrawable ripple(int color,float radius,int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor),rounded(color,radius,0,0),rounded(Color.WHITE,radius,0,0));
    }
}
