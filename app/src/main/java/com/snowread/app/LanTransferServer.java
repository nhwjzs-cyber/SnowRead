package com.snowread.app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Explicit, short-lived receiver. No directory listing or download endpoints. */
final class LanTransferServer implements Closeable {
    static final int MAX_FILE=12*1024*1024,MAX_FILES=100;static final long MAX_TOTAL=128L*1024*1024;
    static final class Incoming {final File file;final String name;Incoming(File file,String name){this.file=file;this.name=name;}}
    interface Importer {String commit(List<Incoming> files)throws Exception;}
    final InetAddress address;final String code;
    private final File folder;private final ServerSocket server;private final Importer importer;
    private final ArrayList<Incoming> staged=new ArrayList<>();private final SecureRandom random=new SecureRandom();
    private volatile boolean closed;private volatile Socket client;private Thread thread;
    private final long expires;private volatile String status="等待电脑连接";private int failures,receivedFiles;private long lockedUntil,totalReceived;private String batch="";
    private final String nonce;
    LanTransferServer(InetAddress address,File cache,Importer importer)throws IOException{this(address,cache,importer,600000);}
    LanTransferServer(InetAddress address,File cache,Importer importer,long ttl)throws IOException{
        if(!address.isSiteLocalAddress()&&!address.isLoopbackAddress())throw new IOException("需要局域网地址");this.address=address;this.importer=importer;
        cleanupExpired(cache);folder=new File(cache,"lan-"+UUID.randomUUID());if(!folder.mkdirs())throw new IOException("无法准备接收目录");
        try{server=new ServerSocket(0,4,address);server.setSoTimeout(1000);}catch(IOException e){folder.delete();throw e;}
        code=String.format(Locale.ROOT,"%08d",random.nextInt(100000000));nonce=token();expires=System.nanoTime()+Math.max(100,ttl)*1000000L;
    }
    private static void cleanupExpired(File cache){
        File[] dirs=cache.listFiles();if(dirs==null)return;long now=System.currentTimeMillis();
        for(File dir:dirs){if(!dir.isDirectory()||!dir.getName().matches("lan-[a-f0-9-]{36}")||now-dir.lastModified()<3600000L)continue;
            File[] files=dir.listFiles();if(files==null)continue;for(File f:files)if(f.isFile()&&f.getName().matches("incoming-[a-zA-Z0-9-]+\\.part"))f.delete();dir.delete();
        }
    }
    private String token(){byte[] b=new byte[16];random.nextBytes(b);StringBuilder s=new StringBuilder();for(byte n:b)s.append(String.format(Locale.ROOT,"%02x",n&255));return s.toString();}
    String host(){return address.getHostAddress()+":"+server.getLocalPort();}
    String url(){return "http://"+host()+"/";}
    String status(){return status;}
    boolean isOpen(){return !closed&&System.nanoTime()<expires;}
    synchronized void start(){if(thread!=null)throw new IllegalStateException("already started");thread=new Thread(this::serve,"snowread-lan");thread.setDaemon(true);thread.start();}
    private void serve(){try{while(isOpen()){try{Socket s=server.accept();client=s;try{s.setSoTimeout(10000);if(!s.getInetAddress().isSiteLocalAddress()&&!s.getInetAddress().isLoopbackAddress()){reply(s,403,"text/plain","仅接收局域网请求");continue;}handle(s);}catch(Exception e){try{reply(s,400,"text/plain","请求失败，请重试");}catch(Exception ignored){}}finally{client=null;try{s.close();}catch(Exception ignored){}}}catch(SocketTimeoutException ignored){}}}catch(IOException ignored){}finally{closed=true;try{server.close();}catch(Exception ignored){}clear();folder.delete();status="传书已关闭";}}
    private static String line(InputStream in,int max)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int last=-1;while(b.size()<max){int n=in.read();if(n<0)throw new EOFException();if(last=='\r'&&n=='\n'){byte[] bytes=b.toByteArray();return new String(bytes,0,bytes.length-1,StandardCharsets.ISO_8859_1);}b.write(n);last=n;}throw new IOException("header too long");}
    private void handle(Socket socket)throws Exception{
        long requestDeadline=System.nanoTime()+60000000000L;InputStream guarded=new FilterInputStream(socket.getInputStream()){
            private void guard()throws IOException{if(!isOpen()||System.nanoTime()>requestDeadline)throw new IOException("request timeout");}
            public int read()throws IOException{guard();return super.read();}
            public int read(byte[] b,int o,int n)throws IOException{guard();return super.read(b,o,n);}
        };InputStream in=new BufferedInputStream(guarded);String[] request=line(in,2048).split(" ");if(request.length!=3){reply(socket,400,"text/plain","请求无效");return;}
        HashMap<String,String> headers=new HashMap<>();int size=0;while(true){String h=line(in,8192);size+=h.length()+2;if(size>8192)throw new IOException();if(h.isEmpty())break;int colon=h.indexOf(':');if(colon<1)throw new IOException();String key=h.substring(0,colon).trim().toLowerCase(Locale.ROOT);if(headers.put(key,h.substring(colon+1).trim())!=null)throw new IOException("duplicate header");}
        if(!host().equals(headers.get("host"))){reply(socket,403,"text/plain","地址不匹配，请使用手机显示的地址");return;}
        String origin=headers.get("origin");if(origin!=null&&!origin.equals("http://"+host())){reply(socket,403,"text/plain","不接受跨站请求");return;}
        URI uri=new URI(request[1]);String path=uri.getPath();
        if(request[0].equals("GET")&&path.equals("/")){reply(socket,200,"text/html; charset=utf-8",page());return;}
        if(!request[0].equals("POST")){reply(socket,405,"text/plain","仅支持上传");return;}
        long now=System.nanoTime();if(now<lockedUntil){reply(socket,429,"text/plain","口令错误次数过多，请稍后重试或在手机重启传书");return;}
        String supplied=headers.get("x-snowread-code");if(supplied==null||!MessageDigest.isEqual(code.getBytes(StandardCharsets.US_ASCII),supplied.getBytes(StandardCharsets.US_ASCII))){if(++failures>=10){lockedUntil=now+30000000000L;failures=0;}reply(socket,403,"text/plain","口令错误");return;}
        if(headers.containsKey("transfer-encoding")){reply(socket,411,"text/plain","不支持分块上传");return;}
        long length=Long.parseLong((headers.containsKey("content-length")?headers.get("content-length"):"0"));if(length<0||length>MAX_FILE){reply(socket,413,"text/plain","每本TXT最多12 MB");return;}
        if(path.equals("/begin")){if(length!=0)throw new IOException();if(!staged.isEmpty()){reply(socket,409,"text/plain","还有待导入文件，请在手机停止后重新开启传书");return;}batch=token();reply(socket,200,"text/plain",batch);return;}
        if(batch.isEmpty()||!batch.equals(headers.get("x-snowread-batch"))){reply(socket,409,"text/plain","上传会话已改变，请重新开始");return;}
        if(path.equals("/stage")){
            String raw=uri.getRawQuery();if(raw==null||!raw.startsWith("name="))throw new IOException();String name=URLDecoder.decode(raw.substring(5),"UTF-8");
            if(name.length()>180||name.indexOf('/')>=0||name.indexOf('\\')>=0||name.matches(".*[\\p{Cntrl}].*")||!name.toLowerCase(Locale.ROOT).endsWith(".txt")||name.substring(0,name.length()-4).trim().isEmpty()){reply(socket,400,"text/plain","仅支持不含路径的TXT文件名");return;}
            if(length==0){reply(socket,400,"text/plain","文件为空");return;}if(receivedFiles>=MAX_FILES||totalReceived+length>MAX_TOTAL){reply(socket,413,"text/plain","本次最多100本、合计128 MB，请重启传书继续");return;}
            File temp=File.createTempFile("incoming-",".part",folder);boolean kept=false;
            try{try(OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[16384];long left=length;while(left>0){if(!isOpen()||System.nanoTime()>requestDeadline)throw new IOException("stopped");int n=in.read(b,0,(int)Math.min(left,b.length));if(n<0)throw new EOFException();out.write(b,0,n);left-=n;}}
                if(!isOpen())throw new IOException();staged.add(new Incoming(temp,name));receivedFiles++;totalReceived+=length;kept=true;status="已接收 "+staged.size()+" 本，等待统一导入";reply(socket,200,"text/plain","已接收 "+name);
            }finally{if(!kept)temp.delete();}return;
        }
        if(length!=0)throw new IOException();
        if(path.equals("/clear")){clear();batch="";status="等待电脑连接";reply(socket,200,"text/plain","已清空未导入文件");return;}
        if(path.equals("/commit")){if(staged.isEmpty()){reply(socket,400,"text/plain","没有待导入文件");return;}if(!isOpen())throw new IOException();status="正在识别正文并提交书架…";try{String result=importer.commit(Collections.unmodifiableList(new ArrayList<>(staged)));status=result;clear();batch="";reply(socket,200,"text/plain",result);}catch(Exception e){status="导入失败，临时文件已清理，请重新上传";clear();batch="";reply(socket,500,"text/plain","导入失败，本次未提交项已撤回，请重新上传");}return;}
        reply(socket,404,"text/plain","没有这个入口");
    }
    private void clear(){for(Incoming file:staged)file.file.delete();staged.clear();}
    private void reply(Socket s,int code,String type,String body)throws IOException{byte[] bytes=body.getBytes(StandardCharsets.UTF_8);OutputStream out=s.getOutputStream();String header="HTTP/1.1 "+code+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'nonce-"+nonce+"'; style-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'\r\n\r\n";out.write(header.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();}
    private String page(){return "<!doctype html><html lang='zh'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>融雪阅读 · 传书</title><style>body{background:#f3ede0;color:#193e4b;font:16px system-ui;margin:8vh auto;padding:24px;max-width:520px}main{background:#fffcf5;border:1px solid #d6e2e4;border-radius:24px;padding:24px}input,button{font:inherit;margin:10px 0;padding:12px;box-sizing:border-box;max-width:100%}button{background:#0f5f73;color:white;border:0;border-radius:12px}pre{white-space:pre-wrap;line-height:1.6}</style><main><h1>融雪阅读</h1><p>雪渐融，故事正生长。</p><p>输入手机上的口令，选择TXT。仅在可信局域网使用，HTTP不加密。</p><input id='pin' type='password' inputmode='numeric' placeholder='8位传书口令' maxlength='8'><br><input id='files' type='file' accept='.txt' multiple><br><button id='send'>上传并导入</button><pre id='result' role='status'></pre></main><script nonce='"+nonce+"'>const q=id=>document.getElementById(id);q('send').onclick=async()=>{let batch='';q('send').disabled=true;const code=q('pin').value;const req=async(path,body,extra={})=>{let r=await fetch(path,{method:'POST',headers:{'X-SnowRead-Code':code,'X-SnowRead-Batch':batch,...extra},body});let t=await r.text();if(!r.ok)throw Error(t);return t;};try{let files=[...q('files').files];if(!files.length)throw Error('请先选择TXT文件');if(files.length>100)throw Error('一次最多100本');if(files.some(f=>!f.size||f.size>12582912||!f.name.toLowerCase().endsWith('.txt')))throw Error('仅支持非空TXT，每本不超过12MB');if(files.reduce((n,f)=>n+f.size,0)>134217728)throw Error('一次合计最多128MB');batch=await req('/begin',null);for(let f of files){q('result').textContent='正在上传 '+f.name;await req('/stage?name='+encodeURIComponent(f.name),f,{'Content-Type':'application/octet-stream'});}q('result').textContent='手机正在导入，请稍候…';q('result').textContent=await req('/commit',null);}catch(e){q('result').textContent=e.message; if(batch)try{await req('/clear',null);}catch(_){} }finally{q('send').disabled=false;}};</script></html>";}
    @Override public void close(){closed=true;try{server.close();}catch(Exception ignored){}Socket s=client;if(s!=null)try{s.close();}catch(Exception ignored){}if(thread==null){clear();folder.delete();}}
}
