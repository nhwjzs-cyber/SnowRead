package com.snowread.app;

import java.util.*;

/** A reviewable remapping plan, applied atomically with the book metadata. */
final class PositionMigration {
    final ArrayList<Integer> marks=new ArrayList<>();
    final LinkedHashMap<Integer,String> notes=new LinkedHashMap<>();
    final LinkedHashMap<Integer,ReadingAnchor> anchors=new LinkedHashMap<>();
    final ArrayList<ReadingAnchor.Unmapped> unresolved=new ArrayList<>();
    private final HashSet<Integer> seen=new HashSet<>();
    final int offset;final boolean approximate;final ReadingAnchor anchor;
    private final boolean strict;
    PositionMigration(TextDocument oldDoc,TextDocument newDoc,int oldOffset,ReadingAnchor saved,List<Integer> oldMarks,Map<Integer,String> oldNotes,Map<Integer,ReadingAnchor> oldAnchors,List<ReadingAnchor.Unmapped> missing){this(oldDoc,newDoc,oldOffset,saved,oldMarks,oldNotes,oldAnchors,missing,false);}
    PositionMigration(TextDocument oldDoc,TextDocument newDoc,int oldOffset,ReadingAnchor saved,List<Integer> oldMarks,Map<Integer,String> oldNotes,Map<Integer,ReadingAnchor> oldAnchors,List<ReadingAnchor.Unmapped> missing,boolean strict){
        this.strict=strict;
        ReadingAnchor.Resolver old=new ReadingAnchor.Resolver(oldDoc),next=new ReadingAnchor.Resolver(newDoc);ReadingAnchor progress=saved!=null&&saved.offset==oldOffset?saved:old.capture(oldOffset);int mapped=strict?next.resolveExact(progress):next.resolve(progress);approximate=mapped<0;offset=mapped<0?ReadingAnchor.safe(newDoc.text,(int)Math.round(progress.wholeRatio*Math.max(0,newDoc.text.length()-1))):mapped;anchor=approximate?progress:next.capture(offset);
        for(Integer p:oldMarks)map(next,oldAnchors.containsKey(p)?oldAnchors.get(p):old.capture(p),oldNotes.get(p));
        for(ReadingAnchor.Unmapped m:missing)map(next,m.anchor,m.note);
        Collections.sort(marks);
    }
    private void map(ReadingAnchor.Resolver next,ReadingAnchor a,String note){int target=strict?next.resolveExact(a):next.resolve(a);if(target<0){unresolved.add(new ReadingAnchor.Unmapped(a,note));return;}if(seen.add(target))marks.add(target);anchors.put(target,next.capture(target));if(note!=null&&!note.isEmpty()){String old=notes.get(target);if(old!=null&&!old.equals(note)&&old.length()+1+note.length()>1000)unresolved.add(new ReadingAnchor.Unmapped(a,note));else notes.put(target,old==null?note:old.equals(note)?old:old+"\n"+note);}}
}
