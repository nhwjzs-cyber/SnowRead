package com.snowread.app;

/** Optional per-book typography; absent means inherit global preferences. */
final class Typeset {
    String customFont="";
    int font=21,margin=26,palette,fontFamily,paragraphGap;
    float spacing=1.65f;
    boolean awake,justify,indent;
    Typeset copy(){Typeset t=new Typeset();t.customFont=customFont;t.font=font;t.margin=margin;t.palette=palette;t.fontFamily=fontFamily;t.paragraphGap=paragraphGap;t.spacing=spacing;t.awake=awake;t.justify=justify;t.indent=indent;return t;}
    boolean valid(){return customFont!=null&&(customFont.isEmpty()||customFont.matches("[a-f0-9]{64}"))&&font>=16&&font<=36&&margin>=12&&margin<=56&&palette>=0&&palette<=2&&fontFamily>=0&&fontFamily<=2&&paragraphGap>=0&&paragraphGap<=24&&!Float.isNaN(spacing)&&spacing>=1.2f&&spacing<=2.2f;}
}
