package com.snowread.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class EpubDocumentTest {
    static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("EPUB check "+checks);}
    static LinkedHashMap<String,byte[]> files(boolean ncx){
        LinkedHashMap<String,byte[]> f=new LinkedHashMap<>();put(f,"mimetype","application/epub+zip");
        put(f,"META-INF/container.xml","<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='OEBPS/book.opf'/></rootfiles></container>");
        put(f,"OEBPS/book.opf","<package xmlns='http://www.idpf.org/2007/opf' version='"+(ncx?"2.0":"3.0")+"'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>融雪测试书</dc:title><meta name='cover' content='cover'/></metadata><manifest><item id='a' href='a.xhtml' media-type='application/xhtml+xml'/><item id='b' href='b.xhtml' media-type='application/xhtml+xml'/><item id='nav' href='"+(ncx?"toc.ncx":"nav.xhtml")+"' media-type='"+(ncx?"application/x-dtbncx+xml":"application/xhtml+xml")+"' properties='"+(ncx?"":"nav")+"'/><item id='cover' href='cover.png' media-type='image/png' properties='cover-image'/></manifest><spine toc='nav'><itemref idref='b'/><itemref idref='a'/></spine></package>");
        // Reverse archive/manifest order intentionally: spine must win.
        put(f,"OEBPS/a.xhtml","<html xmlns='http://www.w3.org/1999/xhtml'><head><title>错误标题</title><style>HIDDEN STYLE</style></head><body><h1 id='a'>春天到来</h1><p>第二部分正文😀。</p></body></html>");
        put(f,"OEBPS/b.xhtml","<!DOCTYPE html><html xmlns='http://www.w3.org/1999/xhtml'><body><h1 id='b'>雪融时</h1><p>第一部分正文&amp;符号。</p><script>HIDDEN SCRIPT</script><p id='later'>段落目标。</p></body></html>");
        if(ncx)put(f,"OEBPS/toc.ncx","<ncx xmlns='http://www.daisy.org/z3986/2005/ncx/'><navMap><navPoint id='b'><navLabel><text>雪融时</text></navLabel><content src='b.xhtml#b'/><navPoint id='later'><navLabel><text>段落定位</text></navLabel><content src='b.xhtml#later'/></navPoint></navPoint><navPoint id='a'><navLabel><text>春天到来</text></navLabel><content src='a.xhtml#a'/></navPoint></navMap></ncx>");
        else put(f,"OEBPS/nav.xhtml","<html xmlns='http://www.w3.org/1999/xhtml' xmlns:epub='http://www.idpf.org/2007/ops'><body><nav epub:type='toc'><ol><li><a href='b.xhtml#b'>雪融时</a></li><li><a href='b.xhtml#later'>段落定位</a></li><li><a href='a.xhtml#a'>春天到来</a></li></ol></nav><nav epub:type='landmarks'><a href='a.xhtml'>不应出现</a></nav></body></html>");
        f.put("OEBPS/cover.png",Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jN1sAAAAASUVORK5CYII="));return f;
    }
    static void put(Map<String,byte[]> f,String n,String value){f.put(n,value.getBytes(StandardCharsets.UTF_8));}
    static byte[] zip(Map<String,byte[]> f)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(Map.Entry<String,byte[]> e:f.entrySet()){ZipEntry entry=new ZipEntry(e.getKey());entry.setTime(0);z.putNextEntry(entry);z.write(e.getValue());z.closeEntry();}}return out.toByteArray();}
    static void rejects(Map<String,byte[]> f)throws Exception{try{EpubDocument.parse(zip(f));throw new AssertionError("Expected EPUB rejection");}catch(IOException|org.xml.sax.SAXException expected){checks++;}}
    public static void main(String[] args)throws Exception{
        for(boolean ncx:new boolean[]{false,true}){
            EpubDocument epub=EpubDocument.parse(zip(files(ncx)));TextDocument d=epub.document;
            check(epub.title.equals("融雪测试书"));check(d.text.indexOf("第一部分")<d.text.indexOf("第二部分"));check(!d.text.contains("HIDDEN"));check(d.text.contains("正文&符号"));check(epub.cover!=null);
            check(d.chapters.size()==3);check(d.chapters.get(1).title.equals("段落定位"));check(d.text.substring(d.chapters.get(1).offset).startsWith("段落目标"));check(d.numberedChapters.size()==3);check(d.chapters.get(2).title.equals("春天到来"));
            check(d.chapters.get(0).offset==0);check(d.chapters.get(1).offset<d.chapters.get(2).offset);check(d.text.equals(EpubDocument.parse(zip(files(ncx))).document.text));
        }
        Map<String,byte[]> f=files(false);put(f,"../escape","bad");rejects(f);
        f=files(false);put(f,"META-INF/encryption.xml","<encryption/>");rejects(f);
        f=files(false);f.remove("mimetype");rejects(f);
        f=files(false);put(f,"OEBPS/b.xhtml","<!DOCTYPE html [<!ENTITY steal SYSTEM 'file:///not-read'>]><html><body>&steal;</body></html>");rejects(f);
        f=files(false);put(f,"OEBPS/b.xhtml","<!DOCTYPE html SYSTEM 'http://127.0.0.1:1/no-fetch'><html><body><p>安全正文</p></body></html>");check(EpubDocument.parse(zip(f)).document.text.contains("安全正文"));
        f=files(false);put(f,"OEBPS/b.xhtml","<html><body>"+String.join("",Collections.nCopies(140,"<div>"))+"deep"+String.join("",Collections.nCopies(140,"</div>"))+"</body></html>");rejects(f);
        f=files(false);String opf=new String(f.get("OEBPS/book.opf"),StandardCharsets.UTF_8).replace("<metadata ","<metadata ").replace("</metadata>","<meta property='rendition:layout'>pre-paginated</meta></metadata>");put(f,"OEBPS/book.opf",opf);rejects(f);
        f=files(false);f.put("bomb.bin",new byte[8*1024*1024+1]);rejects(f);
        f=files(false);for(int i=0;i<2050;i++)put(f,"extra/"+i,"x");rejects(f);
        for(String href:new String[]{"https://example.invalid/a","../../escape","/absolute","file:///private","//host/a"})try{EpubDocument.resolve("OEBPS/book.opf",href);throw new AssertionError();}catch(IOException expected){checks++;}
        check(EpubDocument.resolve("OEBPS/sub/nav.xhtml","../b.xhtml#x").equals("OEBPS/b.xhtml#x"));check(EpubDocument.supported("A.EPUB"));check(!EpubDocument.supported("A.pdf"));
        System.out.println("EpubDocumentTest: "+checks+" checks passed; EPUB 2/3, spine, TOC anchors, cover, local text and hostile archive/XML limits");
    }
}
