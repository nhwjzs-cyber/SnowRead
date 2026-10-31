package com.snowread.app;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Offline text-only EPUB 3 export. Source content, not display conversions or private annotations. */
final class EpubExport {
    interface Progress {void update(int done,int total);}
    static void write(OutputStream output,TextDocument doc,String title,Progress progress)throws Exception{
        if(output==null)throw new IOException("无法打开导出位置");if(doc.text.isEmpty())throw new IOException("正文为空");if(doc.text.length()>EpubDocument.MAX_TEXT)throw new IOException("正文超过当前EPUB的400万字符限制，请先拆分书籍");
        ArrayList<Section> sections=new ArrayList<>();
        for(int start=0;start<doc.text.length();){int end=Math.min(doc.text.length(),start+16000);if(end<doc.text.length()){int line=doc.text.lastIndexOf('\n',end-1),space=doc.text.lastIndexOf(' ',end-1);if(line>start+4000)end=line+1;else if(space>start+4000)end=space+1;if(Character.isHighSurrogate(doc.text.charAt(end-1)))end--;}sections.add(new Section(title,start,end));start=end;}
        if(sections.size()>1000)throw new IOException("正文过长，请先拆分书籍");
        String id="urn:uuid:"+UUID.randomUUID();StringBuilder manifest=new StringBuilder(),spine=new StringBuilder(),nav=new StringBuilder();
        for(int i=0;i<sections.size();i++){String name="s"+i;manifest.append("<item id='"+name+"' href='"+name+".xhtml' media-type='application/xhtml+xml'/>");spine.append("<itemref idref='"+name+"'/>");}
        for(int i=0;i<doc.chapters.size();i++){TextDocument.Chapter chapter=doc.chapters.get(i);int part=partAt(sections,chapter.offset);nav.append("<li><a href='s"+part+".xhtml#c"+i+"'>"+escape(chapter.title)+"</a></li>");}
        java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.ROOT);date.setTimeZone(TimeZone.getTimeZone("UTC"));
        try(ZipOutputStream zip=new BudgetZip(new BufferedOutputStream(new LimitedOutput(output)))){
            byte[] mime="application/epub+zip".getBytes(StandardCharsets.US_ASCII);ZipEntry entry=new ZipEntry("mimetype");CRC32 crc=new CRC32();crc.update(mime);entry.setMethod(ZipEntry.STORED);entry.setSize(mime.length);entry.setCompressedSize(mime.length);entry.setCrc(crc.getValue());zip.putNextEntry(entry);zip.write(mime);zip.closeEntry();
            put(zip,"META-INF/container.xml","<?xml version='1.0' encoding='UTF-8'?><container version='1.0' xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='EPUB/book.opf' media-type='application/oebps-package+xml'/></rootfiles></container>");
            put(zip,"EPUB/book.opf","<?xml version='1.0' encoding='UTF-8'?><package xmlns='http://www.idpf.org/2007/opf' version='3.0' unique-identifier='book-id'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:identifier id='book-id'>"+id+"</dc:identifier><dc:title>"+escape(title)+"</dc:title><dc:language>und</dc:language><meta property='dcterms:modified'>"+date.format(new Date())+"</meta></metadata><manifest><item id='nav' href='nav.xhtml' media-type='application/xhtml+xml' properties='nav'/>"+manifest+"</manifest><spine>"+spine+"</spine></package>");
            put(zip,"EPUB/nav.xhtml",head(title)+"<body><nav xmlns:epub='http://www.idpf.org/2007/ops' epub:type='toc'><h1>目录</h1><ol>"+nav+"</ol></nav></body></html>");
            for(int i=0;i<sections.size();i++){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("已停止导出");progress.update(i,sections.size());Section s=sections.get(i);StringBuilder body=new StringBuilder(head(s.title)).append("<body>");
                body.append("<div style='white-space:pre-wrap'>");int cursor=s.start;
                for(int c=0;c<doc.chapters.size();c++){int at=doc.chapters.get(c).offset;if(at<s.start||at>=s.end)continue;appendText(body,doc.text.substring(cursor,at));body.append("<span id='c"+c+"'></span>");cursor=at;}
                appendText(body,doc.text.substring(cursor,s.end));body.append("</div></body></html>");
                put(zip,"EPUB/s"+i+".xhtml",body.toString());
            }progress.update(sections.size(),sections.size());
        }
    }
    private static int partAt(ArrayList<Section> sections,int offset){int lo=0,hi=sections.size()-1;while(lo<hi){int mid=(lo+hi+1)/2;if(sections.get(mid).start<=offset)lo=mid;else hi=mid-1;}return lo;}
    private static void appendText(StringBuilder b,String text){b.append(escape(text).replace("\n","<br/>"));}
    private static String head(String title){return "<?xml version='1.0' encoding='UTF-8'?><html xmlns='http://www.w3.org/1999/xhtml'><head><title>"+escape(title)+"</title></head>";}
    private static void put(ZipOutputStream zip,String name,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>EpubDocument.MAX_XML)throw new IOException("目录或章节文件过大，请先拆分书籍");zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
    static String escape(String value){StringBuilder b=new StringBuilder();for(int i=0;i<value.length();){int ch=value.codePointAt(i);i+=Character.charCount(ch);if(ch=='&')b.append("&amp;");else if(ch=='<')b.append("&lt;");else if(ch=='>')b.append("&gt;");else if(ch=='\"')b.append("&quot;");else if(ch=='\'')b.append("&apos;");else if(ch==9||ch==10||ch==13||ch>=32&&ch<=0xd7ff||ch>=0xe000&&ch<=0xfffd||ch>=0x10000&&ch<=0x10ffff)b.appendCodePoint(ch);else b.append('\ufffd');}return b.toString();}
    private static final class BudgetZip extends ZipOutputStream {
        private long count;BudgetZip(OutputStream out){super(out);}
        @Override public void write(byte[] b,int off,int len)throws IOException{if(count+len>EpubDocument.MAX_TOTAL)throw new IOException("导出后的正文超过24 MB，请先拆分书籍");count+=len;super.write(b,off,len);}
    }
    private static final class LimitedOutput extends FilterOutputStream {
        private long count;LimitedOutput(OutputStream out){super(out);}
        @Override public void write(byte[] b,int off,int len)throws IOException{if(count+len>TextDocument.MAX_BYTES)throw new IOException("导出的EPUB超过12 MB，请先拆分书籍");count+=len;out.write(b,off,len);}
        @Override public void write(int b)throws IOException{if(++count>TextDocument.MAX_BYTES)throw new IOException("导出的EPUB超过12 MB");out.write(b);}
    }
    private static final class Section {final String title;final int start,end;Section(String title,int start,int end){this.title=title;this.start=start;this.end=end;}}
}
