package com.snowread.app;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Pure Java text engine; positions are UTF-16 character offsets in normalized text. */
public final class TextDocument {
    public static final int MAX_BYTES = 12 * 1024 * 1024;
    public final String text;
    public final String encoding;
    public String encodingConfidence="手动指定";
    /** All directory entries, including volume headings and prefaces. */
    public final List<Chapter> chapters = new ArrayList<>();
    public final List<Chapter> numberedChapters = new ArrayList<>();
    public final List<Chapter> volumes = new ArrayList<>();
    public final List<Integer> starts = new ArrayList<>();
    public boolean chaptersTruncated;

    public static final class Chapter {
        public static final int SPECIAL=0, CHAPTER=1, VOLUME=2;
        public final String title;
        public final int offset, kind, number, volumeIndex, ordinal;
        Chapter(String title,int offset,int kind,int number,int volumeIndex,int ordinal) {
            this.title=title;this.offset=offset;this.kind=kind;this.number=number;this.volumeIndex=volumeIndex;this.ordinal=ordinal;
        }
    }
    public TextDocument(String text, String encoding) {
        this(text, encoding, ChapterRule.AUTO);
    }
    /** rule selects the recognition strategy (ChapterRule); the text itself is never modified. */
    public TextDocument(String text, String encoding, int rule) {
        this.text = text.replace("\r\n", "\n").replace('\r', '\n');
        this.encoding = encoding;
        int currentVolume=-1;
        ChapterRule.Scan scan=ChapterRule.scan(this.text,rule);
        for (ChapterRule.Match match : scan.matches) {
            if(chapters.size()>=ChapterRule.MAX_CHAPTERS){chaptersTruncated=true;break;}
            if(match.kind==Chapter.VOLUME) currentVolume=volumes.size();
            Chapter entry=new Chapter(match.title,match.offset,match.kind,match.number,currentVolume,match.kind==Chapter.CHAPTER?numberedChapters.size()+1:0);
            chapters.add(entry);
            if(match.kind==Chapter.VOLUME) volumes.add(entry);
            if(match.kind==Chapter.CHAPTER) numberedChapters.add(entry);
        }
        chaptersTruncated=scan.truncated;
        if(chapters.isEmpty() || chapters.get(0).offset>0)
            chapters.add(0,new Chapter("开始阅读",0,Chapter.SPECIAL,-1,-1,0));
        // Bound every rendered row, including novels containing no line breaks.
        int start = 0;
        while (start < this.text.length()) {
            starts.add(start);
            int end = Math.min(start + 1400, this.text.length());
            if (end < this.text.length()) {
                int newline = this.text.lastIndexOf('\n', end - 1);
                if (newline >= start + 450) end = newline + 1;
                if (end > start && Character.isHighSurrogate(this.text.charAt(end - 1))) end--;
            }
            start = end;
        }
        if (starts.isEmpty()) starts.add(0);
    }
    public String row(int index) {
        return text.substring(starts.get(index), index + 1 < starts.size() ? starts.get(index + 1) : text.length());
    }
    public int rowAt(int offset) {
        int at = Collections.binarySearch(starts, Math.max(0, Math.min(offset, text.length())));
        return at >= 0 ? at : Math.max(0, -at - 2);
    }
    public int chapterAt(int offset) {
        int lo = 0, hi = chapters.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (chapters.get(mid).offset <= offset) lo = mid; else hi = mid - 1;
        }
        return lo;
    }
    public int sectionEnd(int section) {
        return section+1<chapters.size()?chapters.get(section+1).offset:text.length();
    }
    public String volumeTitle(Chapter chapter) {
        return chapter.volumeIndex>=0?volumes.get(chapter.volumeIndex).title:"未分卷";
    }
    public String readingTitle(int offset) {
        Chapter c=chapters.get(chapterAt(offset));
        return c.volumeIndex>=0 && c.kind!=Chapter.VOLUME?volumeTitle(c)+" · "+c.title:c.title;
    }
    public int numberedOrdinalAt(int offset) {
        int lo=0,hi=numberedChapters.size();
        while(lo<hi) {int mid=(lo+hi)/2;if(numberedChapters.get(mid).offset<=offset) lo=mid+1;else hi=mid;}
        return lo;
    }
    public List<Chapter> findChapters(int number,int volumeIndex,boolean filterVolume) {
        ArrayList<Chapter> result=new ArrayList<>();
        for(Chapter c:numberedChapters) if(c.number==number && (!filterVolume || c.volumeIndex==volumeIndex)) result.add(c);
        return result;
    }
    /** Arabic/fullwidth digits, Chinese numerals, and English Roman chapter numbers. */
    public static int parseNumber(String value) {
        String token=value.replaceAll("[\\t \\u3000]","");
        StringBuilder normalized=new StringBuilder();
        for(int i=0;i<token.length();i++) {
            char c=token.charAt(i);normalized.append(c>='０'&&c<='９'?(char)('0'+c-'０'):c);
        }
        token=normalized.toString();
        if(token.matches("[0-9]+")) {try{return Integer.parseInt(token);}catch(NumberFormatException e){return -1;}}
        if(token.matches("(?i)[ivxlcdm]+")) {
            int result=0,previous=0;String roman=token.toUpperCase(Locale.ROOT);
            for(int i=roman.length()-1;i>=0;i--) {
                char c=roman.charAt(i);int n=c=='I'?1:c=='V'?5:c=='X'?10:c=='L'?50:c=='C'?100:c=='D'?500:1000;
                result+=n<previous?-n:n;previous=n;
            }
            return result;
        }
        if(token.isEmpty()) return -1;
        boolean units=token.matches(".*[十百千万亿拾佰仟].*");
        long total=0,section=0,digit=0;
        for(int i=0;i<token.length();i++) {
            char c=token.charAt(i);int n=chineseDigit(c);
            if(n>=0) {if(units) digit=n;else total=total*10+n;}
            else if(c=='十'||c=='拾'||c=='百'||c=='佰'||c=='千'||c=='仟') {
                int unit=c=='十'||c=='拾'?10:c=='百'||c=='佰'?100:1000;
                section+=(digit==0?1:digit)*unit;digit=0;
            } else if(c=='万') {total+=(section+digit==0?1:section+digit)*10000;section=0;digit=0;}
            else if(c=='亿') {total=(total+section+digit)*100000000;section=0;digit=0;}
            else return -1;
            if(total+section+digit>Integer.MAX_VALUE) return -1;
        }
        long result=units?total+section+digit:total;
        return result<=Integer.MAX_VALUE?(int)result:-1;
    }
    private static int chineseDigit(char c) {
        if(c>='0'&&c<='9') return c-'0';
        switch(c) {
            case '零':case '〇':return 0;case '一':case '壹':return 1;
            case '二':case '两':case '兩':case '贰':case '貳':return 2;
            case '三':case '叁':case '參':return 3;case '四':case '肆':return 4;
            case '五':case '伍':return 5;case '六':case '陆':case '陸':return 6;
            case '七':case '柒':return 7;case '八':case '捌':return 8;case '九':case '玖':return 9;
            default:return -1;
        }
    }
    public static byte[] readLimited(InputStream input) throws IOException {
        if (input == null) throw new IOException("无法打开文件，请重新选择");
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > MAX_BYTES) throw new IOException("第一版支持不超过 12 MB 的 TXT 文件");
                out.write(buffer, 0, count);
            }
            if (out.size() == 0) throw new IOException("这个文件是空的");
            return out.toByteArray();
        }
    }
    public static TextDocument decode(byte[] bytes, String forced) throws IOException {
        int skip = 0; String charset = forced;String confidence=forced==null?"高":"手动指定";
        if (charset == null) {
            if (bytes.length >= 3 && (bytes[0]&255)==239 && (bytes[1]&255)==187 && (bytes[2]&255)==191) { charset="UTF-8"; skip=3; }
            else if (bytes.length >= 2 && (bytes[0]&255)==255 && (bytes[1]&255)==254) { charset="UTF-16LE"; skip=2; }
            else if (bytes.length >= 2 && (bytes[0]&255)==254 && (bytes[1]&255)==255) { charset="UTF-16BE"; skip=2; }
            else {
                int even = 0, odd = 0, sample = Math.min(bytes.length, 2000);
                for (int i = 0; i < sample; i++) if (bytes[i] == 0) { if (i%2 == 0) even++; else odd++; }
                if (odd > sample / 5 && even < sample / 20) charset = "UTF-16LE";
                else if (even > sample / 5 && odd < sample / 20) charset = "UTF-16BE";
                else {
                    try { strict(bytes, 0, "UTF-8"); charset = "UTF-8"; }
                    catch (CharacterCodingException e) {EncodingDetector.Guess guess=EncodingDetector.legacy(bytes);charset=guess.charset;confidence=guess.confidence;}
                }
            }
        }
        String decoded;
        try { decoded = strict(bytes, skip, charset); }
        catch (CharacterCodingException e) { throw new IOException("编码无法识别，请在文件选项中指定编码后重试", e); }
        if (decoded.startsWith("\uFEFF")) decoded = decoded.substring(1);
        int controls = 0;
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c == 0) throw new IOException("这不像 TXT 文本，请选择纯文本文件");
            if (c < 32 && c != '\n' && c != '\r' && c != '\t' && c != '\f') controls++;
        }
        if (controls > decoded.length() / 100) throw new IOException("这不像 TXT 文本，请选择纯文本文件");
        if (decoded.trim().isEmpty()) throw new IOException("文件没有可阅读的正文");
        TextDocument doc=new TextDocument(decoded,charset);doc.encodingConfidence=confidence;return doc;
    }
    private static String strict(byte[] bytes, int skip, String charset) throws CharacterCodingException {
        return Charset.forName(charset).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, skip, bytes.length-skip)).toString();
    }
}
