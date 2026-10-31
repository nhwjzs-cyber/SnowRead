package com.snowread.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline OpenCC dictionary adapter. Length-preserving display conversion, not document rewriting. */
final class ChineseConverter {
    interface Source { InputStream open(String name) throws IOException; }
    private final Dictionary simplified,traditional;
    ChineseConverter(Source source)throws IOException {
        traditional=new Dictionary();traditional.load(source,"STCharacters.txt");traditional.load(source,"STPhrases.txt");
        simplified=new Dictionary();simplified.load(source,"TSCharacters.txt");simplified.load(source,"TSPhrases.txt");
    }
    static boolean valid(int mode){return mode>=0&&mode<=2;}
    String convert(String text,int mode){
        if(!valid(mode))throw new IllegalArgumentException("Invalid script mode");
        if(mode==0||text.isEmpty())return text;
        Dictionary dict=mode==1?simplified:traditional;
        char[] result=text.toCharArray();
        for(int at=0;at<text.length();){
            if((at&4095)==0&&Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            int size=Math.min(dict.longest[text.charAt(at)],text.length()-at);String value=null;
            for(;size>1;size--){value=dict.phrases.get(text.substring(at,at+size));if(value!=null)break;}
            if(value!=null){value.getChars(0,size,result,at);at+=size;}
            else {char replacement=dict.characters[text.charAt(at)];if(replacement!=0)result[at]=replacement;at++;}
        }
        return new String(result);
    }
    private static final class Dictionary {
        final char[] characters=new char[65536];final int[] longest=new int[65536];
        final HashMap<String,String> phrases=new HashMap<>();
        void load(Source source,String name)throws IOException {
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(source.open(name),StandardCharsets.UTF_8))){
                String line;while((line=reader.readLine())!=null){
                    if(line.isEmpty()||line.charAt(0)=='#')continue;
                    int tab=line.indexOf('\t');if(tab<1)continue;
                    String key=line.substring(0,tab),value=line.substring(tab+1).split(" ",2)[0];
                    // Unequal UTF-16 lengths would invalidate bookmarks and annotations. Preserve those originals.
                    if(key.length()!=value.length()||key.length()>64)continue;
                    boolean safe=true;for(int i=0;i<key.length();i++)if(Character.isHighSurrogate(key.charAt(i))!=Character.isHighSurrogate(value.charAt(i))||Character.isLowSurrogate(key.charAt(i))!=Character.isLowSurrogate(value.charAt(i))){safe=false;break;}if(!safe)continue;
                    if(key.length()==1)characters[key.charAt(0)]=value.charAt(0);
                    else {phrases.put(key,value);longest[key.charAt(0)]=Math.max(longest[key.charAt(0)],key.length());}
                }
            }
        }
    }
}
