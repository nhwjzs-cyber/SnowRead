package com.snowread.app;

import java.util.*;

/** Directory row identities survive filtering, volume folding and reversal. */
final class DirectoryRows {
    final TextDocument doc;
    final ArrayList<Integer> rows=new ArrayList<>();
    final HashSet<Integer> folded=new HashSet<>();
    String query="";boolean descending;
    DirectoryRows(TextDocument doc,boolean descending){this.doc=doc;this.descending=descending;rebuild();}
    void rebuild(){rows.clear();String q=query.trim().toLowerCase(Locale.ROOT);for(int i=0;i<doc.chapters.size();i++){TextDocument.Chapter c=doc.chapters.get(i);if(q.isEmpty()){if(c.kind!=TextDocument.Chapter.VOLUME&&folded.contains(c.volumeIndex))continue;}else if(!c.title.toLowerCase(Locale.ROOT).contains(q)&&!(c.volumeIndex>=0&&doc.volumes.get(c.volumeIndex).title.toLowerCase(Locale.ROOT).contains(q)))continue;rows.add(i);}if(descending)Collections.reverse(rows);}
    int chapter(int row){return rows.get(row);}
    int locate(int chapter){query="";folded.remove(doc.chapters.get(chapter).volumeIndex);rebuild();return rows.indexOf(chapter);}
    void toggle(int chapter){int volume=doc.chapters.get(chapter).volumeIndex;if(!folded.remove(volume))folded.add(volume);rebuild();}
}
