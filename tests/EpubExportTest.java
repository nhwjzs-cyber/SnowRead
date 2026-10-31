package com.snowread.app;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

public final class EpubExportTest {
    static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError("EPUB export "+checks);}
    public static void main(String[] args)throws Exception{
        TextDocument doc=new TextDocument("第一章 雪\n雪花 & <春天> \"引号\" '书页' 😀\n第二章 春\n原文继续。","UTF-8");String original=doc.text;ByteArrayOutputStream out=new ByteArrayOutputStream();int[] completed={0};EpubExport.write(out,doc,"书名<&>😀",(d,t)->{completed[0]=d;check(d<=t);});
        byte[] archive=out.toByteArray();check(doc.text.equals(original));check(completed[0]>0);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e=zip.getNextEntry();check(e.getName().equals("mimetype")&&e.getMethod()==ZipEntry.STORED);byte[] buffer=new byte[64];int n=zip.read(buffer);check(new String(buffer,0,n,StandardCharsets.US_ASCII).equals("application/epub+zip"));}
        EpubDocument decoded=EpubDocument.parse(archive);check(decoded.title.equals("书名<&>😀"));check(decoded.document.text.contains("雪花 & <春天> \"引号\" '书页' 😀"));check(decoded.document.chapters.size()==doc.chapters.size());
        check(decoded.document.text.indexOf("第二章")>decoded.document.text.indexOf("第一章"));check(EpubExport.escape("\u0001\ud800<>&").equals("��&lt;&gt;&amp;"));
        StringBuilder many=new StringBuilder();for(int i=1;i<=1200;i++)many.append("第").append(i).append("章 雪\n正文与山间来信。\n");TextDocument large=new TextDocument(many.toString(),"UTF-8");out=new ByteArrayOutputStream();EpubExport.write(out,large,"长书",(d,t)->{});decoded=EpubDocument.parse(out.toByteArray());check(decoded.document.chapters.size()==1200);check(decoded.document.text.contains("第1200章"));
        int files=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){ZipEntry e;while((e=zip.getNextEntry())!=null)if(e.getName().matches("EPUB/s[0-9]+.xhtml"))files++;}check(files<10);
        try{EpubExport.write(new ByteArrayOutputStream(),doc,"取消",(d,t)->{throw new java.util.concurrent.CancellationException();});throw new AssertionError();}catch(java.util.concurrent.CancellationException expected){checks++;}
        try{EpubExport.write(new OutputStream(){public void write(int b)throws IOException{throw new IOException("disk full");}},doc,"失败",(d,t)->{});throw new AssertionError();}catch(IOException expected){checks++;}
        try{EpubExport.write(new ByteArrayOutputStream(),new TextDocument(new String(new char[EpubDocument.MAX_TEXT+1]).replace('\0','x'),"UTF-8"),"超限",(d,t)->{});throw new AssertionError();}catch(IOException expected){checks++;}
        System.out.println("EpubExportTest: "+checks+" checks passed; stored mimetype, XML/Unicode, TOC anchors, long book chunking, cancellation and write failure");
    }
}
