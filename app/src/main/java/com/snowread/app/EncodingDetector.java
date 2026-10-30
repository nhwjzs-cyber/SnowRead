package com.snowread.app;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Heuristic ranking, not a probability. Ambiguous samples are exposed for manual preview. */
final class EncodingDetector {
    static final class Guess {final String charset,confidence;final double margin;Guess(String charset,String confidence,double margin){this.charset=charset;this.confidence=confidence;this.margin=margin;}}
    private static final HashSet<Character> COMMON=new HashSet<>();
    static {for(int i=0;i<CommonHan.CHARS.length();i++)COMMON.add(CommonHan.CHARS.charAt(i));}
    static Guess legacy(byte[] bytes){String gb=decode(bytes,"GB18030"),big=decode(bytes,"Big5");if(gb==null)return new Guess("Big5",big==null?"低":"高",1);if(big==null)return new Guess("GB18030","高",1);double a=score(gb),b=score(big),margin=Math.abs(a-b);return new Guess(b>a?"Big5":"GB18030",margin>.35?"高":margin>.12?"中":"低",margin);}
    private static String decode(byte[] bytes,String name){try{return Charset.forName(name).newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException e){return null;}}
    static double score(String text){double points=0;int count=0;int step=Math.max(1,text.length()/24000);for(int i=0;i<text.length();i+=step){char c=text.charAt(i);if(c>='\u4e00'&&c<='\u9fff'){points+=COMMON.contains(c)?1.0:-1.8;count++;}else if(c=='\ufffd'||Character.getType(c)==Character.PRIVATE_USE){points-=5;count++;}else if(!Character.isWhitespace(c)&&!Character.isLetterOrDigit(c)&&"。，、！？；：“”‘’（）《》【】…—,.!?;:\"'()-[]".indexOf(c)<0){points-=.6;count++;}}
        if(count==0)return 0;String[] words={"的","了","是","不","在","一","他","她","我","你","人","这","那","这时","说道","不是","没有","這","說","來","個","們","著","為","會","過","沒有","說道"};int sample=Math.min(text.length(),24000);for(String word:words){int at=0;while((at=text.indexOf(word,at))>=0&&at<sample){points+=.45;at+=word.length();}}return points/count;
    }
}
