package com.snowread.app;

/** Conservative filename cleanup: retain ambiguous parenthesized literary titles. */
final class TitleCleaner {
    static String stem(String name){String title=name.replaceFirst("(?i)\\.(txt|epub)$","");return title.replaceAll("\\s+"," ").trim();}
    static String clean(String filename){
        String title=stem(filename);title=title.replaceFirst("(?i)[_\\s-]*(?:作者|著者)\\s*[:：].*$","");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("[【\\[（(]([^】\\]）)]*)[】\\]）)]").matcher(title);StringBuffer out=new StringBuffer();
        while(m.find()){String inner=m.group(1);boolean metadata=inner.matches("(?i).*(?:完结|完本|连载|精校|校对|全文|下载|电子书|TXT|https?://|www\\.|小说网|书屋|书站|书阁|\\d+\\s*[-–~至]\\s*\\d+\\s*[章回卷]|共\\s*\\d+\\s*[章回卷]).*");m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(metadata?" ":m.group()));}m.appendTail(out);title=out.toString().trim();
        if(title.startsWith("《")&&title.endsWith("》"))title=title.substring(1,title.length()-1);
        title=title.replaceAll("\\s+"," ").replaceAll("^[_\\s-]+|[_\\s-]+$","").trim();if(title.isEmpty())title=stem(filename);if(title.length()>240){title=title.substring(0,240);if(Character.isHighSurrogate(title.charAt(239)))title=title.substring(0,239);}return title;
    }
}
