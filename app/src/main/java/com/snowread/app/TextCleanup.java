package com.snowread.app;
import java.io.IOException;
import java.util.*;
/** Local literal rules only: bounded, deterministic, no regular-expression execution. */
final class TextCleanup {
    private static final java.util.regex.Pattern URL=java.util.regex.Pattern.compile("(?i)(?:https?://|www\\.)[^\\s<>]+");
    static final int MAX_RULES=20,MAX_CONFIG=5000,MAX_TOKEN=120;
    static final class Rule {final String from,to;final boolean line;Rule(String f,String t,boolean l){from=f;to=t;line=l;}}
    static final class Result {final String text;final int removed,changed;final List<String> samples;Result(String s,int r,int c,List<String> p){text=s;removed=r;changed=c;samples=p;}}
    static List<Rule> parse(String config)throws IOException {
        if(config==null||config.length()>MAX_CONFIG)throw new IOException("净化规则最多5000字");List<Rule> out=new ArrayList<>();
        for(String row:config.split("\n",-1)){String s=row.trim();if(s.isEmpty())continue;boolean line=s.startsWith("删除整行:");String from,to="";
            if(line)from=s.substring(5).trim();else{int at=s.indexOf("=>");if(at<0)throw new IOException("替换规则请使用：原文 => 替换文字");from=s.substring(0,at).trim();to=s.substring(at+2).trim();}
            if(from.isEmpty()||from.length()>MAX_TOKEN||to.length()>MAX_TOKEN||!unicode(from)||!unicode(to))throw new IOException("每条规则的原文及替换文字最多120字，原文不能为空");
            out.add(new Rule(from,to,line));if(out.size()>MAX_RULES)throw new IOException("每本最多20条规则");
        }return out;
    }
    private static boolean unicode(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))return false;}else if(Character.isLowSurrogate(c)||c<32&&c!='\t')return false;}return true;}
    private static boolean advertisement(String s){
        if(s.length()>240)return false;
        return s.equals("本章未完，请点击下一页继续阅读")||s.equals("本章未完，请点击下一页继续阅读。")||s.equals("本章未完，点击下一页继续阅读")||s.equals("本章未完，请翻页")||URL.matcher(s).matches()||s.startsWith("最新网址：")||s.startsWith("最新域名：")||s.startsWith("手机用户请浏览：");
    }
    static Result apply(String source,String config)throws IOException {
        List<Rule> rules=parse(config);StringBuilder out=new StringBuilder(Math.min(source.length(),65536));List<String> samples=new ArrayList<>();int removed=0,changed=0;
        for(int start=0;start<source.length();){if(Thread.currentThread().isInterrupted())throw new IOException("净化已取消");int nl=source.indexOf('\n',start),end=nl<0?source.length():nl;String row=source.substring(start,end),clean=row;boolean drop=advertisement(row.trim());
            for(Rule rule:rules)if(rule.line&&row.contains(rule.from))drop=true;
            if(drop){removed++;clean="";}else for(Rule rule:rules)if(!rule.line){clean=replace(clean,rule.from,rule.to);if(clean.length()>TextDocument.MAX_BYTES)throw new IOException("替换后的文本过大");}
            if(drop||!clean.equals(row)){changed++;if(samples.size()<8)samples.add("原："+excerpt(row)+"\n后："+(drop?"（移除此行）":excerpt(clean)));}
            if(!drop){out.append(clean);if(nl>=0)out.append('\n');}if(out.length()>TextDocument.MAX_BYTES)throw new IOException("净化后的文本过大");start=nl<0?source.length():nl+1;
        }
        if(out.toString().trim().isEmpty())throw new IOException("规则会移除全部正文，请缩小匹配范围");return new Result(out.toString(),removed,changed,Collections.unmodifiableList(samples));
    }
    private static String replace(String s,String from,String to)throws IOException {long length=s.length();int at=0,hit;while((hit=s.indexOf(from,at))>=0){length+=(long)to.length()-from.length();if(length>TextDocument.MAX_BYTES)throw new IOException("替换后的文本过大");at=hit+from.length();}return s.replace(from,to);}
    private static String excerpt(String s){int end=Math.min(140,s.length());if(end<s.length()&&Character.isHighSurrogate(s.charAt(end-1)))end--;return s.substring(0,end)+(end<s.length()?"…":"");}
}
