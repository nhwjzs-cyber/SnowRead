package com.snowread.app;

import android.content.Context;
import java.io.IOException;

/** Call prepare from the IO worker before opening a converted book. No network or file mutation. */
final class ChineseText {
    private static volatile ChineseConverter converter;
    static void prepare(Context context)throws IOException {
        if(converter!=null)return;
        synchronized(ChineseText.class){if(converter==null)converter=new ChineseConverter(name->context.getAssets().open("conversion/"+name));}
    }
    static String convert(Context context,String text,int mode)throws IOException {
        if(mode==0)return text;
        prepare(context);return converter.convert(text,mode);
    }
    static String label(String text,int mode){ChineseConverter ready=converter;return ready==null||mode==0?text:ready.convert(text,mode);}
}
