package com.snowread.app;

import java.util.*;

/** Lazy sentence segmentation. Cleaned TTS text retains an exact mapping to original UTF-16 offsets. */
final class TextChunker {
    static final class SpeechChunk {
        final int start,end;
        final String text;
        private final int[] starts,ends;
        SpeechChunk(int start,int end,String text,int[] starts,int[] ends){this.start=start;this.end=end;this.text=text;this.starts=starts;this.ends=ends;}
        int absoluteStart(int at){at=Math.max(0,Math.min(at,text.length()-1));if(at>0&&Character.isLowSurrogate(text.charAt(at)))at--;return starts[at];}
        int absoluteEnd(int exclusive){int at=Math.max(1,Math.min(exclusive,text.length()));if(at<text.length()&&Character.isHighSurrogate(text.charAt(at-1))&&Character.isLowSurrogate(text.charAt(at)))at++;return ends[at-1];}
    }
    private static boolean space(char c){return Character.isWhitespace(c)||Character.isSpaceChar(c);}
    private static boolean closing(char c){return "”’」』）】》〉\"')]}".indexOf(c)>=0;}
    private static boolean sentence(char c){return "。！？!?…；;\n\r".indexOf(c)>=0;}
    private static int safeEnd(String text,int end){if(end>0&&end<text.length()&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))return end-1;return end;}
    static SpeechChunk next(String text,int from,int limit,int maxChars){
        int endLimit=safeEnd(text,Math.min(text.length(),Math.max(0,limit)));int start=Math.max(0,Math.min(from,endLimit));
        if(start>0&&start<endLimit&&Character.isLowSurrogate(text.charAt(start))&&Character.isHighSurrogate(text.charAt(start-1)))start--;
        while(start<endLimit&&space(text.charAt(start)))start++;
        if(start>=endLimit)return null;
        int bound=safeEnd(text,Math.min(endLimit,start+Math.max(2,maxChars))),end=-1,comma=-1;
        if(bound<=start)bound=Math.min(endLimit,start+2);
        for(int i=start;i<bound;i++){
            char c=text.charAt(i);if("，,、：:".indexOf(c)>=0)comma=i+1;
            if(sentence(c)){end=i+1;while(end<bound&&(sentence(text.charAt(end))||closing(text.charAt(end))))end++;break;}
            // English full stops terminate sentences except decimal points and short abbreviations.
            if(c=='.'&&(i+1==endLimit||space(text.charAt(i+1))||closing(text.charAt(i+1)))){
                int word=i;while(word>start&&Character.isLetter(text.charAt(word-1)))word--;
                String token=text.substring(word,i).toLowerCase(Locale.ROOT);
                if(!Arrays.asList("mr","mrs","ms","dr","prof","sr","jr","vs","etc","e","g","i").contains(token)){end=i+1;while(end<bound&&closing(text.charAt(end)))end++;break;}
            }
        }
        if(end<0)end=bound<endLimit&&comma>start?comma:bound;
        StringBuilder cleaned=new StringBuilder();ArrayList<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();
        for(int i=start;i<end;){int at=i;char c=text.charAt(i++);if(space(c)){while(i<end&&space(text.charAt(i)))i++;if(cleaned.length()==0||i==end)continue;c=' ';}cleaned.append(c);starts.add(at);ends.add(i);}
        if(cleaned.length()==0)return next(text,end,endLimit,maxChars);
        int[] a=new int[starts.size()],b=new int[ends.size()];for(int i=0;i<a.length;i++){a[i]=starts.get(i);b[i]=ends.get(i);}
        return new SpeechChunk(start,end,cleaned.toString(),a,b);
    }
    static List<SpeechChunk> split(String text,int from,int maxChars){ArrayList<SpeechChunk> chunks=new ArrayList<>();SpeechChunk c;while((c=next(text,from,text.length(),maxChars))!=null){chunks.add(c);from=c.end;}return chunks;}
    static boolean pageNumber(SpeechChunk chunk){return chunk.text.trim().matches("(?:第[0-9０-９]{1,5}页|[-—]\\s*[0-9]{1,5}\\s*[-—]|[0-9]{1,5})");}
    static SpeechChunk withoutUrls(SpeechChunk chunk){
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(?:https?://|www\\.)[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(chunk.text);
        boolean[] omitted=new boolean[chunk.text.length()];boolean any=false;while(matcher.find()){any=true;Arrays.fill(omitted,matcher.start(),matcher.end(),true);}if(!any)return chunk;
        StringBuilder text=new StringBuilder();ArrayList<Integer> starts=new ArrayList<>(),ends=new ArrayList<>();for(int i=0;i<chunk.text.length();i++)if(!omitted[i]){char c=chunk.text.charAt(i);if(space(c)&&(text.length()==0||text.charAt(text.length()-1)==' '))continue;text.append(c);starts.add(chunk.starts[i]);ends.add(chunk.ends[i]);}
        while(text.length()>0&&space(text.charAt(text.length()-1))){text.setLength(text.length()-1);starts.remove(starts.size()-1);ends.remove(ends.size()-1);}if(text.length()==0)return null;int[] a=new int[starts.size()],b=new int[ends.size()];for(int i=0;i<a.length;i++){a[i]=starts.get(i);b[i]=ends.get(i);}return new SpeechChunk(chunk.start,chunk.end,text.toString(),a,b);
    }
}
