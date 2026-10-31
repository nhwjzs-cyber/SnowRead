package com.snowread.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Bounded, offline text-only EPUB 2/3 reader. Never extracts paths or opens URLs. */
final class EpubDocument {
    static final int MAX_TOTAL=24*1024*1024,MAX_XML=2*1024*1024,MAX_ENTRIES=2048,MAX_TEXT=4*1024*1024;
    final TextDocument document;final String title;final byte[] cover;
    private EpubDocument(TextDocument doc,String title,byte[] cover){this.document=doc;this.title=title;this.cover=cover;}
    static boolean name(String name){return name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".epub");}
    static boolean supported(String name){return name!=null&&(name.toLowerCase(Locale.ROOT).endsWith(".txt")||name(name));}
    private static IOException invalid(String message){return new IOException("EPUB："+message);}
    private static void cancelled()throws IOException{if(Thread.currentThread().isInterrupted())throw invalid("已停止解析");}
    static EpubDocument parse(byte[] bytes)throws Exception {
        if(bytes.length>TextDocument.MAX_BYTES)throw invalid("文件最多12 MB");
        Map<String,byte[]> xml=new HashMap<>();Set<String> names=new HashSet<>();int count=0,total=0;boolean mime=false,encrypted=false;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                cancelled();if(++count>MAX_ENTRIES)throw invalid("文件条目过多");String path=entry.getName();validatePath(path,entry.isDirectory());
                if(!names.add(path))throw invalid("存在重复文件路径");if(entry.isDirectory())continue;
                boolean keep=path.equals("mimetype")||path.matches("(?i).*\\.(xml|opf|ncx|xhtml|html|htm)$");
                ByteArrayOutputStream out=keep?new ByteArrayOutputStream():null;int size=0,n;
                while((n=zip.read(buffer))!=-1){cancelled();size+=n;total+=n;if(total>MAX_TOTAL||size>8*1024*1024||keep&&size>MAX_XML)throw invalid("解压内容超过大小限制");if(keep)out.write(buffer,0,n);}
                if(keep)xml.put(path,out.toByteArray());
                if(path.equals("mimetype"))mime="application/epub+zip".equals(new String(out.toByteArray(),StandardCharsets.US_ASCII).trim());
                if(path.equals("META-INF/encryption.xml")||path.equals("META-INF/rights.xml"))encrypted=true;
            }
        }catch(ZipException e){throw invalid("压缩包损坏");}
        if(!mime)throw invalid("缺少有效的 mimetype 标识");if(encrypted)throw invalid("暂不支持 DRM 或含加密资源的书籍");
        final String[] opfPath={null};xml(xml.get("META-INF/container.xml"),new DefaultHandler(){public void startElement(String u,String local,String q,Attributes a){if(local.equals("rootfile")&&opfPath[0]==null)opfPath[0]=a.getValue("full-path");}});
        if(opfPath[0]==null)throw invalid("缺少书籍内容索引");validatePath(opfPath[0],false);
        Package pkg=new Package();xml(xml.get(opfPath[0]),pkg);if(pkg.fixed)throw invalid("暂不支持固定版式，请使用文字型 EPUB");
        if(pkg.spine.isEmpty()||pkg.spine.size()>1000)throw invalid("阅读顺序缺失或章节文件过多");
        ArrayList<Link> links=new ArrayList<>();String navPath=null;boolean ncx=false;
        for(Item item:pkg.items.values())if(Arrays.asList(item.properties.split("\\s+")).contains("nav")){navPath=resolve(opfPath[0],item.href);break;}
        if(navPath==null){Item item=pkg.items.get(pkg.toc);if(item!=null){navPath=resolve(opfPath[0],item.href);ncx=true;}}
        if(navPath!=null){try{Navigation nav=new Navigation(ncx);xml(xml.get(navPath),nav);for(Link link:nav.links)links.add(new Link(resolve(navPath,link.href),link.label));}catch(Exception ignored){links.clear();}}
        StringBuilder text=new StringBuilder();ArrayList<TextDocument.Chapter> chapters=new ArrayList<>();
        for(String id:pkg.spine){
            cancelled();Item item=pkg.items.get(id);if(item==null)throw invalid("阅读顺序引用的文件不存在");
            if(!item.media.equals("application/xhtml+xml")&&!item.media.equals("text/html"))throw invalid("包含暂不支持的正文类型");
            String path=resolve(opfPath[0],item.href);Body body=new Body();xml(xml.get(path),body);
            String content=body.text.toString();if(content.trim().isEmpty())continue;
            if(text.length()>0)text.append("\n\n");int base=text.length();text.append(content);
            if(text.length()>MAX_TEXT)throw invalid("提取后的正文过长");
            int before=chapters.size();ArrayList<Link> here=new ArrayList<>();for(Link link:links)if(filePart(link.href).equals(path))here.add(link);
            if(here.isEmpty())addChapter(chapters,body.heading.length()>0?body.heading.toString():body.title.length()>0?body.title.toString():"第 "+(chapters.size()+1)+" 节",base);
            else for(Link link:here){String fragment=fragment(link.href);Integer at=fragment.isEmpty()?Integer.valueOf(0):body.anchors.get(fragment);if(at!=null)addChapter(chapters,link.label,base+at);}
            if(chapters.size()==before)addChapter(chapters,body.heading.length()>0?body.heading.toString():"第 "+(chapters.size()+1)+" 节",base);
        }
        if(text.toString().trim().isEmpty())throw invalid("未找到可阅读的正文，图片型书籍暂不支持");
        TextDocument doc=new TextDocument(text.toString(),"UTF-8");doc.chapters.clear();doc.numberedChapters.clear();doc.volumes.clear();
        Collections.sort(chapters,(a,b)->Integer.compare(a.offset,b.offset));int last=-1;
        if(chapters.isEmpty()||chapters.get(0).offset>0)addAtStart(chapters);
        for(TextDocument.Chapter ch:chapters)if(ch.offset!=last&&ch.offset<doc.text.length()){last=ch.offset;TextDocument.Chapter row=new TextDocument.Chapter(ch.title,ch.offset,TextDocument.Chapter.CHAPTER,doc.numberedChapters.size()+1,-1,doc.numberedChapters.size()+1);doc.chapters.add(row);doc.numberedChapters.add(row);}
        doc.encodingConfidence="EPUB";
        String coverPath=null;for(Item item:pkg.items.values())if(Arrays.asList(item.properties.split("\\s+")).contains("cover-image")||item.id.equals(pkg.cover)){if(item.media.equals("image/jpeg")||item.media.equals("image/png"))coverPath=resolve(opfPath[0],item.href);}
        byte[] cover=coverPath==null?null:readCover(bytes,coverPath);
        return new EpubDocument(doc,cleanLabel(pkg.title.toString()),cover);
    }
    private static void addAtStart(ArrayList<TextDocument.Chapter> chapters){chapters.add(0,new TextDocument.Chapter("开始阅读",0,TextDocument.Chapter.CHAPTER,1,-1,1));}
    private static void addChapter(ArrayList<TextDocument.Chapter> chapters,String title,int offset)throws IOException{if(chapters.size()>=10000)throw invalid("目录超过一万项");chapters.add(new TextDocument.Chapter(cleanLabel(title),offset,TextDocument.Chapter.CHAPTER,chapters.size()+1,-1,chapters.size()+1));}
    private static String cleanLabel(String text){String value=text.replaceAll("\\s+"," ").trim();int end=Math.min(240,value.length());if(end>0&&Character.isHighSurrogate(value.charAt(end-1)))end--;return value.isEmpty()?"未命名章节":value.substring(0,end);}
    private static void validatePath(String path,boolean directory)throws IOException{if(path==null||path.isEmpty()||path.length()>512||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.indexOf('\0')>=0)throw invalid("文件路径不安全");for(String bit:path.split("/"))if(bit.equals("..")||bit.equals("."))throw invalid("文件路径不安全");}
    static String resolve(String base,String href)throws Exception {
        if(href==null||href.contains("\\"))throw invalid("资源路径无效");URI uri=new URI(href);if(uri.isAbsolute()||uri.getRawAuthority()!=null||uri.getRawQuery()!=null)throw invalid("不读取外部资源");
        String rawPath=uri.getPath();if(rawPath.startsWith("/"))throw invalid("资源路径越界");String path=rawPath.isEmpty()?base:base.substring(0,Math.max(0,base.lastIndexOf('/')+1))+rawPath;
        ArrayDeque<String> parts=new ArrayDeque<>();for(String part:path.split("/")){if(part.isEmpty()||part.equals("."))continue;if(part.equals("..")){if(parts.isEmpty())throw invalid("资源路径越界");parts.removeLast();}else parts.add(part);}
        StringBuilder full=new StringBuilder();for(String part:parts){if(full.length()>0)full.append('/');full.append(part);}validatePath(full.toString(),false);return full+(uri.getFragment()==null?"":"#"+uri.getFragment());
    }
    private static String filePart(String href){int at=href.indexOf('#');return at<0?href:href.substring(0,at);}
    private static String fragment(String href){int at=href.indexOf('#');return at<0?"":href.substring(at+1);}
    private static byte[] readCover(byte[] archive,String path)throws IOException{
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))){ZipEntry e;byte[] buffer=new byte[8192];while((e=zip.getNextEntry())!=null){cancelled();if(!e.getName().equals(path))continue;ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1){if(out.size()+n>2*1024*1024)return null;out.write(buffer,0,n);}byte[] b=out.toByteArray();if(b.length>8&&(b[0]==(byte)0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'||b[0]==(byte)0xff&&b[1]==(byte)0xd8))return b;return null;}}
        return null;
    }
    private static void xml(byte[] bytes,DefaultHandler handler)throws Exception{
        if(bytes==null)throw invalid("缺少必要的章节或索引文件");if(bytes.length>MAX_XML)throw invalid("XML文件过大");
        // Common external XHTML doctypes are removed, never loaded. Internal entity declarations are rejected.
        java.nio.charset.Charset charset=StandardCharsets.UTF_8;
        if(bytes.length>=2&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)charset=StandardCharsets.UTF_16LE;
        else if(bytes.length>=2&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)charset=StandardCharsets.UTF_16BE;
        else if(bytes.length>=4&&bytes[0]=='<'&&bytes[1]==0)charset=StandardCharsets.UTF_16LE;
        else if(bytes.length>=4&&bytes[0]==0&&bytes[1]=='<')charset=StandardCharsets.UTF_16BE;
        String value=charset.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();if(value.startsWith("\uFEFF"))value=value.substring(1);if(value.indexOf('\0')>=0)throw invalid("XML 编码无效");
        java.util.regex.Matcher dt=java.util.regex.Pattern.compile("(?is)<!DOCTYPE[^>]*>").matcher(value);
        StringBuffer safe=new StringBuffer();while(dt.find()){if(dt.group().contains("["))throw invalid("不支持内部实体声明");dt.appendReplacement(safe,"");}dt.appendTail(safe);
        value=safe.toString().replace("&nbsp;","&#160;").replace("&mdash;","&#8212;").replace("&ndash;","&#8211;").replace("&hellip;","&#8230;");
        if(value.toUpperCase(Locale.ROOT).contains("<!DOCTYPE")||value.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw invalid("不支持实体声明");
        SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(true);factory.setValidating(false);
        XMLReader reader=factory.newSAXParser().getXMLReader();
        // Android Expat does not expose every Xerces feature. The input is already DTD-free,
        // and the rejecting resolver remains the final guard on every implementation.
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities","http://apache.org/xml/features/nonvalidating/load-external-dtd"})try{reader.setFeature(feature,false);}catch(SAXException ignored){}
        try{reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(SAXException ignored){}
        reader.setEntityResolver((publicId,systemId)->{throw new SAXException("External entities disabled");});reader.setContentHandler(new DefaultHandler(){int depth,nodes;public void startElement(String u,String n,String q,Attributes a)throws SAXException{if(++depth>128||++nodes>100000)throw new SAXException("XML structure limit");handler.startElement(u,n,q,a);}public void endElement(String u,String n,String q)throws SAXException{handler.endElement(u,n,q);depth--;}public void characters(char[] c,int s,int l)throws SAXException{handler.characters(c,s,l);}});reader.setErrorHandler(handler);reader.parse(new InputSource(new StringReader(value)));
    }
    private static final class Item {String id,href,media,properties;Item(Attributes a){id=a.getValue("id");href=a.getValue("href");media=a.getValue("media-type");properties=a.getValue("properties")==null?"":a.getValue("properties");}}
    private static final class Package extends DefaultHandler {
        final LinkedHashMap<String,Item> items=new LinkedHashMap<>();final ArrayList<String> spine=new ArrayList<>();final StringBuilder title=new StringBuilder();boolean inTitle,fixed;String cover="",toc="",property="";final StringBuilder metaValue=new StringBuilder();
        public void startElement(String u,String n,String q,Attributes a)throws SAXException{
            if(n.equals("title"))inTitle=true;if(n.equals("item")){Item item=new Item(a);if(item.id==null||item.href==null||item.media==null||items.put(item.id,item)!=null)throw new SAXException("Invalid manifest");}
            if(n.equals("spine"))toc=a.getValue("toc");if(n.equals("itemref")&&!"no".equals(a.getValue("linear")))spine.add(a.getValue("idref"));
            if(n.equals("meta")){if("cover".equals(a.getValue("name")))cover=a.getValue("content");property=a.getValue("property");metaValue.setLength(0);}
        }
        public void characters(char[] c,int s,int l){if(inTitle)title.append(c,s,l);if("rendition:layout".equals(property)&&metaValue.length()<1024)metaValue.append(c,s,Math.min(l,1024-metaValue.length()));}
        public void endElement(String u,String n,String q){if(n.equals("title"))inTitle=false;if(n.equals("meta")){if("rendition:layout".equals(property)&&metaValue.toString().trim().equals("pre-paginated"))fixed=true;property="";}}
    }
    private static final class Link {final String href,label;Link(String h,String l){href=h;label=l;}}
    private static final class Navigation extends DefaultHandler {
        final ArrayList<Link> links=new ArrayList<>();final boolean ncx;int tocDepth,depth;String href;StringBuilder label=new StringBuilder();boolean inLabel;final ArrayDeque<LinkBuilder> points=new ArrayDeque<>();
        Navigation(boolean ncx){this.ncx=ncx;}
        public void startElement(String u,String n,String q,Attributes a){depth++;
            if(ncx){if(n.equals("navPoint"))points.push(new LinkBuilder());if(!points.isEmpty()){if(n.equals("content"))points.peek().href=a.getValue("src");if(n.equals("text"))inLabel=true;}}
            else {String type=a.getValue("http://www.idpf.org/2007/ops","type");if(n.equals("nav")&&(type!=null&&Arrays.asList(type.split("\\s+")).contains("toc")))tocDepth=depth;if(tocDepth>0&&n.equals("a")){href=a.getValue("href");label.setLength(0);inLabel=true;}}
        }
        public void characters(char[] c,int s,int l){if(inLabel){if(ncx&&!points.isEmpty())points.peek().label.append(c,s,l);else label.append(c,s,l);}}
        public void endElement(String u,String n,String q){if(ncx){if(n.equals("text"))inLabel=false;if(n.equals("navPoint")&&!points.isEmpty()){LinkBuilder p=points.pop();if(p.href!=null)links.add(new Link(p.href,p.label.toString()));}}else{if(n.equals("a")&&inLabel){if(href!=null)links.add(new Link(href,label.toString()));inLabel=false;}if(depth==tocDepth)tocDepth=0;}depth--;}
        private static final class LinkBuilder {String href;StringBuilder label=new StringBuilder();}
    }
    private static final class Body extends DefaultHandler {
        final StringBuilder text=new StringBuilder(),heading=new StringBuilder(),title=new StringBuilder();final Map<String,Integer> anchors=new HashMap<>();int depth,bodyDepth,skip,headingDepth,titleDepth;
        private boolean block(String n){return n.matches("p|div|section|article|h[1-6]|li|blockquote|tr|br|hr");}
        private void newline(){if(text.length()>0&&text.charAt(text.length()-1)!='\n')text.append('\n');}
        public void startElement(String u,String n,String q,Attributes a)throws SAXException{depth++;if(n.equals("title"))titleDepth=depth;if(n.equals("body"))bodyDepth=depth;
            if(bodyDepth==0)return;if(skip>0){skip++;return;}if(n.matches("script|style|noscript|svg|math|iframe|object")){skip=1;return;}
            if(block(n))newline();String id=a.getValue("id");if(id!=null&&anchors.size()<10000)anchors.put(id,text.length());if(n.equals("h1")&&heading.length()==0)headingDepth=depth;
            if(text.length()>MAX_TEXT)throw new SAXException("Body too long");
        }
        public void characters(char[] c,int s,int l)throws SAXException{
            if(titleDepth>0&&title.length()<240)title.append(c,s,Math.min(l,240-title.length()));
            if(bodyDepth==0||skip>0)return;
            if(headingDepth>0&&heading.length()<240)heading.append(c,s,Math.min(l,240-heading.length()));
            for(int i=s;i<s+l;i++){char ch=c[i];if(Character.isWhitespace(ch)||ch=='\u00a0'){if(text.length()>0&&!Character.isWhitespace(text.charAt(text.length()-1)))text.append(' ');}else text.append(ch);}
            if(text.length()>MAX_TEXT)throw new SAXException("Body too long");
        }
        public void endElement(String u,String n,String q){if(skip>0)skip--;else if(bodyDepth>0&&block(n))newline();if(depth==headingDepth)headingDepth=0;if(depth==titleDepth)titleDepth=0;if(depth==bodyDepth)bodyDepth=0;depth--;}
    }
}
