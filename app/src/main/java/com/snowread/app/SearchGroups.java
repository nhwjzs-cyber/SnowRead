package com.snowread.app;

import java.util.*;

final class SearchGroups {
    static final class Row {final int chapter,hit,count;Row(int chapter,int hit,int count){this.chapter=chapter;this.hit=hit;this.count=count;}}
    final ArrayList<Row> rows=new ArrayList<>();final HashSet<Integer> folded=new HashSet<>();
    void rebuild(TextDocument doc,SearchResults results){rows.clear();if(results==null)return;int at=0;while(at<results.hits.size()){int chapter=doc.chapterAt(results.hits.get(at).offset),end=at+1;while(end<results.hits.size()&&doc.chapterAt(results.hits.get(end).offset)==chapter)end++;rows.add(new Row(chapter,-1,end-at));if(!folded.contains(chapter))for(int i=at;i<end;i++)rows.add(new Row(chapter,i,0));at=end;}}
}
