package com.snowread.app;
import java.io.*;import java.util.*;
public final class BookNoteTest {
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("note check "+checks);}
 public static void main(String[] args)throws Exception{
  String body="第一章 春\n窗边的积雪缓缓融化，春天的故事开始了。🌱\n第二章 雪\n一个新的片段。";TextDocument doc=new TextDocument(body,"UTF-8");int p=body.indexOf("积雪");BookNote n=BookNote.create(doc,p,p+12,"我的想法",0);check(n.valid(body.length()));check(n.quote.equals(body.substring(p,p+12)));check(n.edit("另一条备注",1).style==1&&n.note.equals("我的想法"));
  String newer="前言补充。\n"+body;TextDocument changed=new TextDocument(newer,"UTF-8");BookNote mapped=BookNote.remap(Collections.singleton(n),changed).get(0);check(mapped.start==newer.indexOf(n.quote)&&mapped.id.equals(n.id)&&mapped.note.equals(n.note));
  BookNote missing=BookNote.remap(Collections.singleton(n),new TextDocument("第一章 春\n原文已经删除。","UTF-8")).get(0);check(!missing.located()&&missing.quote.equals(n.quote)&&missing.valid(20));check(missing.edit("继续保留",1).start==-1);check(missing.rebind(doc,p,p+6).id.equals(n.id));
  int emoji=body.indexOf("🌱");BookNote pair=BookNote.create(doc,emoji+1,emoji+1,"",0);check(pair.quote.equals("🌱")&&pair.end-pair.start==2);
  BookNote invalid=new BookNote("../bad",n.start,n.end,n.quote,n.note,0,n.created,n.beginAnchor,n.endAnchor);check(!invalid.valid(body.length()));
  StringWriter out=new StringWriter();NoteExport.write(out,"春雪",Arrays.asList(n,missing));check(out.toString().contains("待定位")&&out.toString().contains("> "+n.quote)&&out.toString().contains("我的想法"));
  check(NoteQuery.find(Arrays.asList(n,missing),"我的想法",0).size()==2);check(NoteQuery.find(Arrays.asList(n,missing),"",3).size()==1);check(NoteQuery.find(Arrays.asList(n,missing),"",1).isEmpty());
  check(NoteQuery.find(Collections.singleton(n.edit("HeLLo WORLD",1)),"hello",1).size()==1);check(NoteQuery.find(Collections.singleton(n),"不存在",0).isEmpty());StringWriter plain=new StringWriter();NoteExport.writePlain(plain,"春雪",Arrays.asList(n,missing));check(plain.toString().contains("备注：我的想法")&&plain.toString().contains("待定位"));
  System.out.println("BookNoteTest: "+checks+" checks passed");
 }
}
