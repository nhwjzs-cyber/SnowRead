package com.snowread.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.*;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Two aligned title lines; full names remain in the book's accessible label and details. */
final class ShelfTitleView extends TextView {
    private static final int MIN_SP=13,MAX_SP=16;
    ShelfTitleView(Context context) {
        super(context);setMaxLines(2);setEllipsize(TextUtils.TruncateAt.END);setIncludeFontPadding(false);
        setGravity(Gravity.TOP);setTypeface(Typeface.DEFAULT,Typeface.BOLD);setLineSpacing(dp(1),1);
        setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED);setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
    }
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec)-getCompoundPaddingLeft()-getCompoundPaddingRight();
        int height=MeasureSpec.getSize(heightSpec)-getCompoundPaddingTop()-getCompoundPaddingBottom();
        if(width>0&&height>0) {
            TextPaint measure=new TextPaint(getPaint());int chosen=MIN_SP;StaticLayout fitted=null;
            // Titles from file providers can be longer than ordinary file-system names.
            CharSequence title=getText().length()>1024?getText().subSequence(0,1024):getText();
            for(int sp=MAX_SP;sp>=MIN_SP;sp--) {
                measure.setTextSize(sp*getResources().getDisplayMetrics().scaledDensity);
                StaticLayout layout=StaticLayout.Builder.obtain(title,0,title.length(),measure,width)
                    .setIncludePad(false).setLineSpacing(dp(1),1).setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED).build();
                fitted=layout;
                if(layout.getLineCount()<=2&&layout.getHeight()<=height) {chosen=sp;break;}
            }
            int visibleLines=0;
            if(fitted!=null) while(visibleLines<Math.min(2,fitted.getLineCount())&&fitted.getLineTop(visibleLines+1)<=height) visibleLines++;
            setMaxLines(Math.max(1,visibleLines));
            setTextSize(TypedValue.COMPLEX_UNIT_SP,chosen);
        }
        super.onMeasure(widthSpec,heightSpec);
    }
}
