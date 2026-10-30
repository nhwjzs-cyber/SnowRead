package com.snowread.app;

import java.util.*;

/** Shelf rules independent of Android views and storage. Category IDs survive renaming. */
final class ShelfOrganization {
    static final String ALL="@all", PINNED="@pinned", NONE="@none";
    static class Entry {
        String id,title;
        final LinkedHashSet<String> categoryIds=new LinkedHashSet<>();
        long opened,added;
        boolean pinned;
    }
    /** Reading progress source for sorting and filtering; implemented against the concrete book. */
    interface Progress {float ratio(Entry book);}
    static final int SORT_SMART=0,SORT_RECENT=1,SORT_ADDED=2,SORT_TITLE=3;
    static final int SHOW_ALL=0,SHOW_READING=1,SHOW_UNREAD=2;
    /** Re-orders a filtered shelf; SORT_SMART keeps visible()'s pinned-first, most-recent order. */
    static <T extends Entry> ArrayList<T> arrange(ArrayList<T> list,int sort){
        if(sort==SORT_RECENT)Collections.sort(list,(a,b)->{int order=Long.compare(b.opened,a.opened);return order!=0?order:a.id.compareTo(b.id);});
        else if(sort==SORT_ADDED)Collections.sort(list,(a,b)->{int order=Long.compare(b.added,a.added);return order!=0?order:a.id.compareTo(b.id);});
        else if(sort==SORT_TITLE)Collections.sort(list,(a,b)->{int order=a.title.compareToIgnoreCase(b.title);return order!=0?order:a.id.compareTo(b.id);});
        return list;
    }
    static <T extends Entry> ArrayList<T> filterProgress(ArrayList<T> list,int show,Progress progress){
        if(show==SHOW_ALL)return list;
        ArrayList<T> out=new ArrayList<>();
        for(T book:list){float r=progress.ratio(book);boolean started=r>0f,finished=r>=1f;if(show==SHOW_READING?started&&!finished:!started)out.add(book);}
        return out;
    }
    final LinkedHashMap<String,String> categories=new LinkedHashMap<>();

    private String validName(String value,String except) {
        String name=value==null?"":value.trim().replaceAll("\\s+"," ");
        if(name.isEmpty()) throw new IllegalArgumentException("请输入分类名称");
        if(name.codePointCount(0,name.length())>20) throw new IllegalArgumentException("分类名称最多 20 个字");
        if(name.equals("全部")||name.equals("置顶")||name.equals("未分类")) throw new IllegalArgumentException("请换一个分类名称");
        for(Map.Entry<String,String> category:categories.entrySet())
            if(!category.getKey().equals(except)&&category.getValue().equalsIgnoreCase(name))
                throw new IllegalArgumentException("已经有同名分类了");
        return name;
    }
    String add(String name) {
        String value=validName(name,null),id=UUID.randomUUID().toString();categories.put(id,value);return id;
    }
    void rename(String id,String name) {
        requireCategory(id);categories.put(id,validName(name,id));
    }
    void delete(String id,Collection<? extends Entry> books) {
        requireCategory(id);categories.remove(id);
        for(Entry book:books)book.categoryIds.remove(id);
    }
    void assign(Collection<? extends Entry> books,String id) {
        if(!id.isEmpty())requireCategory(id);
        assignAll(books,id.isEmpty()?Collections.emptySet():Collections.singleton(id));
    }
    void assignAll(Collection<? extends Entry> books,Collection<String> ids) {
        for(String id:ids)requireCategory(id);
        for(Entry book:books){book.categoryIds.clear();book.categoryIds.addAll(ids);}
    }
    void updateMembership(Collection<? extends Entry> books,Collection<String> add,Collection<String> remove) {
        for(String id:add)requireCategory(id);
        for(Entry book:books){book.categoryIds.removeAll(remove);book.categoryIds.addAll(add);}
    }
    int membership(Collection<? extends Entry> books,String id) {
        int count=0;for(Entry book:books)if(book.categoryIds.contains(id))count++;
        return count==0?0:count==books.size()?1:2;
    }
    void pin(Collection<? extends Entry> books,boolean pinned) {for(Entry book:books)book.pinned=pinned;}
    private void requireCategory(String id) {
        if(!categories.containsKey(id))throw new IllegalArgumentException("这个分类已不存在");
    }
    String label(String filter) {
        if(PINNED.equals(filter))return "置顶";if(NONE.equals(filter))return "未分类";
        return categories.containsKey(filter)?categories.get(filter):"全部";
    }
    <T extends Entry> ArrayList<T> visible(Collection<T> books,String filter,String query) {
        ArrayList<T> result=new ArrayList<>();String needle=query.trim().toLowerCase(Locale.ROOT);
        for(T book:books) {
            if(!book.title.toLowerCase(Locale.ROOT).contains(needle))continue;
            if(PINNED.equals(filter)&&!book.pinned)continue;
            if(NONE.equals(filter)&&!book.categoryIds.isEmpty())continue;
            if(categories.containsKey(filter)&&!book.categoryIds.contains(filter))continue;
            result.add(book);
        }
        Collections.sort(result,(a,b)-> {
            int order=Boolean.compare(b.pinned,a.pinned);
            if(order==0)order=Long.compare(b.opened,a.opened);
            return order==0?a.id.compareTo(b.id):order;
        });
        return result;
    }
}
