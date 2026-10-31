package com.snowread.app;
import java.util.*;
final class NoteQuery {
 static ArrayList<BookNote> find(Collection<BookNote> all,String query,int filter){String q=query==null?"":query.trim();ArrayList<BookNote> out=new ArrayList<>();for(BookNote n:all)if((filter!=1||n.style==1)&&(filter!=2||n.style==0)&&(filter!=3||!n.located())&&(contains(n.quote,q)||contains(n.note,q)))out.add(n);Collections.sort(out,(a,b)->{if(a.located()!=b.located())return a.located()?-1:1;return a.located()?Integer.compare(a.start,b.start):Long.compare(a.created,b.created);});return out;}
 private static boolean contains(String s,String q){for(int i=0;i<=s.length()-q.length();i++)if(s.regionMatches(true,i,q,0,q.length()))return true;return false;}
}
