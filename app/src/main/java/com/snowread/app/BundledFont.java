package com.snowread.app;

import android.content.Context;
import android.graphics.Typeface;

final class BundledFont {
    private static Typeface serif;
    private static boolean attempted;
    static synchronized Typeface serif(Context context){if(!attempted){attempted=true;try{serif=Typeface.createFromAsset(context.getApplicationContext().getAssets(),"fonts/RongxueSerif-Regular.otf");}catch(RuntimeException ignored){serif=Typeface.SERIF;}}return serif;}
}
