package com.snowread.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class ChineseConverterTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception {
        Path assets=Paths.get(args[0]);ChineseConverter c=new ChineseConverter(name->Files.newInputStream(assets.resolve(name)));
        String plain="理发师发现头发，发展故事。皇后在后台喝茶。干燥的天气，干杯！\n😀𠮷 ABC 123";
        String traditional="理髮師發現頭髮，發展故事。皇后在後臺喝茶。乾燥的天氣，乾杯！\n😀𠮷 ABC 123";
        String result=c.convert(plain,2);check(result.equals(traditional));check(c.convert(traditional,1).equals(plain));
        check(c.convert(plain,0)==plain);check(c.convert("",2).isEmpty());check(!ChineseConverter.valid(-1));check(!ChineseConverter.valid(3));
        try{c.convert(plain,3);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        Random random=new Random(4064);String alphabet="简繁體转换閱讀頭髮发展後台皇后干杯乾燥😀𠮷\n \t，。ABC";
        for(int round=0;round<800;round++){
            StringBuilder b=new StringBuilder();for(int i=0;i<100;i++){int at=random.nextInt(alphabet.length());char ch=alphabet.charAt(at);if(!Character.isSurrogate(ch))b.append(ch);else b.append("😀");}
            String raw=b.toString();for(int mode=1;mode<=2;mode++){
                String converted=c.convert(raw,mode);check(converted.length()==raw.length());check(converted.equals(c.convert(raw,mode)));
                for(int i=0;i<raw.length();i++){char ch=raw.charAt(i);if(Character.isSurrogate(ch)||Character.isWhitespace(ch)||ch<128)check(converted.charAt(i)==ch);}
            }
        }
        String large=String.join("",Collections.nCopies(20000,plain));long before=System.nanoTime();String transformed=c.convert(large,2);check(transformed.length()==large.length());check(transformed.startsWith(traditional));
        System.out.println("ChineseConverterTest: "+checks+" checks passed; actual dictionaries, phrases, UTF-16 offsets, mixed text; large text "+((System.nanoTime()-before)/1000000)+" ms (host only)");
    }
}
