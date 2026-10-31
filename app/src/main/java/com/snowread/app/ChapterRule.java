package com.snowread.app;

import java.util.*;
import java.util.regex.*;

/** Selectable chapter recognition strategies. Matching stays line-anchored; offsets index the
 *  normalized document text and the 10000-entry cap mirrors TextDocument. */
final class ChapterRule {
    static final int AUTO=0,STANDARD=1,ENGLISH=2,NUMBER_LINES=3;
    static final int COUNT=4;
    static final int MAX_CHAPTERS=10000;
    private static final String NUMERALS="零〇一二三四五六七八九十百千万亿两兩壹贰貳叁參肆伍陆陸柒捌玖拾佰仟0-9０-９";
    private static final String SPACE="[\\t \\u3000]*";
    /** Rule AUTO: the original fixed strategy — numbered/volume lines, prefaces and English chapters. */
    private static final Pattern AUTO_HEADING=Pattern.compile(
        "^"+SPACE+"(?:第"+SPACE+"["+NUMERALS+"\\t \\u3000]+[章节節卷回部篇集].{0,64}|" +
        "(?:序章|序言|楔子|引子|前言|后记|尾声|终章|番外).{0,36}|" +
        "(?i:chapter[\\t ]+[0-9ivxlcdm]+(?:[\\t :：.、].{0,64})?))"+SPACE+"$", Pattern.MULTILINE);
    /** Rule STANDARD: only standard 第x章/卷 lines; prefaces and English headings are ignored. */
    private static final Pattern STANDARD_HEADING=Pattern.compile(
        "^"+SPACE+"第"+SPACE+"["+NUMERALS+"\\t \\u3000]+[章节節卷回部篇集].{0,64}"+SPACE+"$", Pattern.MULTILINE);
    /** Rule ENGLISH: only Chapter N lines. */
    private static final Pattern ENGLISH_HEADING=Pattern.compile(
        "^"+SPACE+"(?i:chapter[\\t ]+[0-9ivxlcdm]+(?:[\\t :：.、].{0,64})?)"+SPACE+"$", Pattern.MULTILINE);
    /** Rule NUMBER_LINES: a standalone line of 1-4 digits acts as the chapter number. */
    private static final Pattern NUMBER_LINE=Pattern.compile("^[\\t ]*[0-9０-９]{1,4}[\\t ]*$",Pattern.MULTILINE);
    private static final Pattern NUMBERED=Pattern.compile("^第"+SPACE+"(["+NUMERALS+"\\t \\u3000]+)"+SPACE+"([章节節卷回部篇集])");
    private static final Pattern ENGLISH_MATCH=Pattern.compile("(?i)^chapter[\\t ]+([0-9ivxlcdm]+)(?=[\\t :：.、]|$)");
    static String label(int rule){
        switch(rule){
            case STANDARD:return "第x章 · 卷";
            case ENGLISH:return "Chapter N";
            case NUMBER_LINES:return "单独数字行";
            default:return "自动识别";
        }
    }
    static String describe(int rule){
        switch(rule){
            case STANDARD:return "只认“第x章 / 节 / 卷”标准行，忽略序章、番外与英文章节。";
            case ENGLISH:return "只认“Chapter N”英文章节行。";
            case NUMBER_LINES:return "只认独立的纯数字编号行（1、23…），适合数字分章的书。";
            default:return "现有策略：第x章/卷、序章楔子番外、Chapter N 等常见标题。";
        }
    }
    static boolean valid(int rule){return rule>=0&&rule<COUNT;}
    /** One recognized heading: title is trimmed, offset indexes the normalized text. */
    static final class Match {
        public final String title;public final int offset,kind,number;
        Match(String title,int offset,int kind,int number){this.title=title;this.offset=offset;this.kind=kind;this.number=number;}
    }
    /** Scan outcome: capped match list plus whether further headings exist beyond the cap. */
    static final class Scan {
        public final ArrayList<Match> matches;public final boolean truncated;
        Scan(ArrayList<Match> matches,boolean truncated){this.matches=matches;this.truncated=truncated;}
    }
    /** Scan with the given strategy; every pattern is anchored to its own line. */
    static Scan scan(String text,int rule){
        ArrayList<Match> out=new ArrayList<>();boolean truncated=false;
        if(rule==NUMBER_LINES){
            Matcher m=NUMBER_LINE.matcher(text);
            while(m.find()){
                if(out.size()>=MAX_CHAPTERS){truncated=true;break;}
                String title=m.group().trim();
                out.add(new Match(title,m.start(),TextDocument.Chapter.CHAPTER,TextDocument.parseNumber(title)));
            }
            return new Scan(out,truncated);
        }
        Pattern head=rule==STANDARD?STANDARD_HEADING:rule==ENGLISH?ENGLISH_HEADING:AUTO_HEADING;
        Matcher m=head.matcher(text);
        while(m.find()){
            if(out.size()>=MAX_CHAPTERS){truncated=true;break;}
            String title=m.group().trim().replaceAll("^[\\u3000]+|[\\u3000]+$","");
            int kind=TextDocument.Chapter.SPECIAL,number=-1;
            if(rule==STANDARD){
                Matcher chinese=NUMBERED.matcher(title);
                if(chinese.find()){
                    number=TextDocument.parseNumber(chinese.group(1));
                    kind="卷部篇集".contains(chinese.group(2))?TextDocument.Chapter.VOLUME:TextDocument.Chapter.CHAPTER;
                }else kind=TextDocument.Chapter.CHAPTER;
            }else if(rule==ENGLISH){
                Matcher english=ENGLISH_MATCH.matcher(title);
                kind=TextDocument.Chapter.CHAPTER;
                if(english.find())number=TextDocument.parseNumber(english.group(1));
            }else{
                Matcher chinese=NUMBERED.matcher(title),english=ENGLISH_MATCH.matcher(title);
                if(chinese.find()){
                    number=TextDocument.parseNumber(chinese.group(1));
                    kind="卷部篇集".contains(chinese.group(2))?TextDocument.Chapter.VOLUME:TextDocument.Chapter.CHAPTER;
                }else if(english.find()){kind=TextDocument.Chapter.CHAPTER;number=TextDocument.parseNumber(english.group(1));}
            }
            out.add(new Match(title,m.start(),kind,number));
        }
        return new Scan(out,truncated);
    }
    private ChapterRule(){}
}
