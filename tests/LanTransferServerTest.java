package com.snowread.app;
import java.io.*;import java.net.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class LanTransferServerTest {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("LAN check "+checks);}
 static String request(LanTransferServer s,String method,String path,String headers,byte[] body)throws Exception{
  try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(s.address,Integer.parseInt(s.host().split(":")[1])),2000);socket.setSoTimeout(4000);OutputStream out=socket.getOutputStream();out.write((method+" "+path+" HTTP/1.1\r\nHost: "+s.host()+"\r\n"+headers+"\r\n").getBytes(StandardCharsets.UTF_8));out.write(body);out.flush();ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=socket.getInputStream().read(buf))>=0)b.write(buf,0,n);return b.toString("UTF-8");}}
 static String post(LanTransferServer s,String path,String batch,byte[] body)throws Exception{return request(s,"POST",path,"X-SnowRead-Code: "+s.code+"\r\nX-SnowRead-Batch: "+batch+"\r\nContent-Length: "+body.length+"\r\n",body);}
 static String body(String s){return s.substring(s.indexOf("\r\n\r\n")+4);}
 static void code(String s,int c){check(s.startsWith("HTTP/1.1 "+c+" "));}
 public static void main(String[] args)throws Exception{
  File cache=Files.createTempDirectory("snowread-lan-test-").toFile();File stale=new File(cache,"lan-"+UUID.randomUUID());check(stale.mkdir());Files.write(new File(stale,"incoming-123.part").toPath(),new byte[]{1});check(stale.setLastModified(System.currentTimeMillis()-7200000));final int[] commits={0};byte[] txt="第一章 春\n冰雪渐融🌱".getBytes(StandardCharsets.UTF_8);
  try(LanTransferServer s=new LanTransferServer(InetAddress.getLoopbackAddress(),cache,files->{commits[0]++;check(files.size()==2);check(files.get(0).name.equals("春雪.txt"));check(Arrays.equals(txt,Files.readAllBytes(files.get(0).file.toPath())));return "导入2本";})){
   check(!stale.exists());s.start();String home=request(s,"GET","/","",new byte[0]);code(home,200);check(home.contains("融雪阅读")&&!home.contains(s.code));
   code(request(s,"POST","/begin","Content-Length: 0\r\n",new byte[0]),403);
   code(request(s,"POST","/begin","X-SnowRead-Code: "+s.code+"\r\nOrigin: http://evil.test\r\nContent-Length: 0\r\n",new byte[0]),403);
   code(request(s,"POST","/begin","X-SnowRead-Code: "+s.code+"\r\nContent-Length: 0\r\nContent-Length: 0\r\n",new byte[0]),400);
   code(request(s,"POST","/begin","X-SnowRead-Code: "+s.code+"\r\nTransfer-Encoding: chunked\r\n",new byte[0]),411);
   code(request(s,"POST","/stage","X-SnowRead-Code: "+s.code+"\r\nContent-Length: 12582913\r\n",new byte[0]),413);
   String batch=body(post(s,"/begin","",new byte[0]));check(batch.matches("[a-f0-9]{32}"));
   code(post(s,"/stage?name=..%2Fbad.txt",batch,txt),400);code(post(s,"/stage?name=a.epub",batch,txt),400);code(post(s,"/stage?name=a.txt","wrong",txt),409);
   code(post(s,"/stage?name="+URLEncoder.encode("春雪.txt","UTF-8"),batch,txt),200);code(post(s,"/begin","",new byte[0]),409);check(commits[0]==0);
   code(post(s,"/stage?name=b.txt",batch,txt),200);String result=post(s,"/commit",batch,new byte[0]);code(result,200);check(result.contains("导入2本")&&commits[0]==1);code(post(s,"/commit",batch,new byte[0]),409);
   String next=body(post(s,"/begin","",new byte[0]));code(post(s,"/stage?name=c.txt",next,txt),200);code(post(s,"/clear",next,new byte[0]),200);check(commits[0]==1);
   code(request(s,"GET","/library/index.json","",new byte[0]),405);
  }
  waitClean(cache);
  try(LanTransferServer s=new LanTransferServer(InetAddress.getLoopbackAddress(),cache,files->{throw new IOException("simulated failure");})){s.start();String batch=body(post(s,"/begin","",new byte[0]));code(post(s,"/stage?name=a.txt",batch,txt),200);code(post(s,"/commit",batch,new byte[0]),500);code(post(s,"/begin","",new byte[0]),200);}
  waitClean(cache);
  try(LanTransferServer s=new LanTransferServer(InetAddress.getLoopbackAddress(),cache,files->"",100)){s.start();Thread.sleep(150);check(!s.isOpen());}
  waitClean(cache);
  try(LanTransferServer s=new LanTransferServer(InetAddress.getLoopbackAddress(),cache,files->{throw new AssertionError("interrupted upload must not import");})){s.start();String batch=body(post(s,"/begin","",new byte[0]));try(Socket partial=new Socket(s.address,Integer.parseInt(s.host().split(":")[1]))){partial.getOutputStream().write(("POST /stage?name=interrupted.txt HTTP/1.1\r\nHost: "+s.host()+"\r\nX-SnowRead-Code: "+s.code+"\r\nX-SnowRead-Batch: "+batch+"\r\nContent-Length: 1000\r\n\r\nx").getBytes(StandardCharsets.UTF_8));partial.getOutputStream().flush();Thread.sleep(50);long before=System.nanoTime();s.close();check(System.nanoTime()-before<500000000L);}}
  waitClean(cache);check(cache.delete());System.out.println("LanTransferServerTest: "+checks+" checks passed");
 }
 static void waitClean(File root)throws Exception{for(int i=0;i<100&&root.list().length!=0;i++)Thread.sleep(20);check(root.list().length==0);}
}
