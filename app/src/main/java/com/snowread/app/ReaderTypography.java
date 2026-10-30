package com.snowread.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.*;
import android.text.style.*;
import android.widget.TextView;

/** Paragraph styles keep original offsets; measurement and rendering use identical spans. */
final class ReaderTypography {
    volatile java.util.List<BookNote> notes=java.util.Collections.emptyList();
    final Typeface face;final boolean justify,indent;final int paragraphDp;
    static Typeset global(Context context){SharedPreferences p=context.getSharedPreferences("reader",0);Typeset t=new Typeset();t.font=Math.max(16,Math.min(36,p.getInt("font",21)));t.margin=Math.max(12,Math.min(56,p.getInt("sideMargin",26)));t.spacing=Math.max(1.2f,Math.min(2.2f,p.getFloat("spacing",1.65f)));t.palette=Math.max(0,Math.min(2,p.getInt("palette",0)));t.customFont=p.getString("customFont","");if(!FontLibrary.validId(t.customFont))t.customFont="";t.fontFamily=Math.max(0,Math.min(2,p.getInt("fontFamily",0)));t.paragraphGap=Math.max(0,Math.min(24,p.getInt("paragraphGap",0)));t.awake=p.getBoolean("awake",false);t.justify=p.getBoolean("justify",false);t.indent=p.getBoolean("indent",false);return t;}
    ReaderTypography(Context context){this(context,null);}
    ReaderTypography(Context context,Typeset scoped){Typeset t=scoped==null?global(context):scoped;face=FontLibrary.face(context,t);justify=t.justify&&Build.VERSION.SDK_INT>=26;indent=t.indent;paragraphDp=t.paragraphGap;}
    ReaderTypography(Context context,Typeset scoped,java.util.Collection<BookNote> notes){this(context,scoped);this.notes=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(notes));}
    void apply(TextView view){view.setTypeface(face);if(Build.VERSION.SDK_INT>=26)view.setJustificationMode(justify?Layout.JUSTIFICATION_MODE_INTER_WORD:Layout.JUSTIFICATION_MODE_NONE);}
    SpannableString style(String whole,int start,int end,float textSize,float density){
        String raw=whole.substring(start,end);SpannableString body=new SpannableString(raw);
        int at=0;while(at<body.length()){
            int newline=raw.indexOf('\n',at);int local=newline<0?raw.length():newline+1;
            if(indent&&(start+at==0||whole.charAt(start+at-1)=='\n')&&!Character.isWhitespace(body.charAt(at)))body.setSpan(new LeadingMarginSpan.Standard(Math.round(textSize*2),0),at,local,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(paragraphDp>0&&newline>=0)body.setSpan(new ParagraphGap(Math.round(paragraphDp*density)),at,local,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            at=local;
        }for(BookNote n:notes){if(!n.located())continue;int a=Math.max(0,n.start-start),b=Math.min(body.length(),n.end-start);if(b>a)body.setSpan(n.style==0?new BackgroundColorSpan(0x6687B8AA):new UnderlineSpan(),a,b,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return body;
    }
    StaticLayout layout(CharSequence body,TextPaint paint,int width,float spacing){StaticLayout.Builder b=StaticLayout.Builder.obtain(body,0,body.length(),paint,Math.max(1,width)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0,spacing).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);if(Build.VERSION.SDK_INT>=26)b.setJustificationMode(justify?Layout.JUSTIFICATION_MODE_INTER_WORD:Layout.JUSTIFICATION_MODE_NONE);return b.build();}
    private static final class ParagraphGap implements LineHeightSpan {
        final int gap;ParagraphGap(int gap){this.gap=gap;}
        public void chooseHeight(CharSequence text,int start,int end,int spanstartv,int v,Paint.FontMetricsInt fm){if(end>start&&text.charAt(end-1)=='\n'){fm.descent+=gap;fm.bottom+=gap;}}
    }
}
