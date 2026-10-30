package com.snowread.app;

import java.util.*;

public final class ShelfOrganizationTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    private static void reject(Runnable task){boolean rejected=false;try{task.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
    private static ShelfOrganization.Entry book(String id,String title,long opened){ShelfOrganization.Entry e=new ShelfOrganization.Entry();e.id=id;e.title=title;e.opened=opened;return e;}
    public static void main(String[] args) {
        ShelfOrganization shelf=new ShelfOrganization();String fiction=shelf.add("  小说  "),history=shelf.add("历史");
        check("小说".equals(shelf.categories.get(fiction)));check(!fiction.equals(history));
        reject(()->shelf.add(""));reject(()->shelf.add("小说"));reject(()->shelf.add("未分类"));reject(()->shelf.add("置顶"));reject(()->shelf.add("全部"));reject(()->shelf.add(String.join("",Collections.nCopies(21,"字"))));
        String english=shelf.add("SciFi");reject(()->shelf.add("scifi"));
        shelf.rename(english,"Science Fiction");check("Science Fiction".equals(shelf.categories.get(english)));shelf.rename(fiction,"小说");
        reject(()->shelf.rename(fiction,"历史"));reject(()->shelf.rename("missing","新分类"));
        ShelfOrganization.Entry a=book("a","雪国的故事",10),b=book("b","春天",30),c=book("c","Snow Tales",20),d=book("d","雪落",20);
        List<ShelfOrganization.Entry> books=Arrays.asList(a,b,c,d);
        check(shelf.visible(books,ShelfOrganization.ALL,"").equals(Arrays.asList(b,c,d,a)));
        shelf.assign(Arrays.asList(a,c),fiction);shelf.assign(Collections.singletonList(d),history);
        check(shelf.visible(books,fiction,"").equals(Arrays.asList(c,a)));check(shelf.visible(books,ShelfOrganization.NONE,"").equals(Collections.singletonList(b)));
        check(shelf.visible(books,ShelfOrganization.ALL,"  snow ").equals(Collections.singletonList(c)));
        check(shelf.visible(books,fiction,"雪").equals(Collections.singletonList(a)));
        shelf.pin(Arrays.asList(a,c),true);
        check(shelf.visible(books,ShelfOrganization.ALL,"").equals(Arrays.asList(c,a,b,d)));check(shelf.visible(books,ShelfOrganization.PINNED,"").equals(Arrays.asList(c,a)));
        shelf.rename(fiction,"想读");check(a.categoryIds.contains(fiction));check(shelf.visible(books,fiction,"").size()==2);
        shelf.delete(fiction,books);check(a.categoryIds.isEmpty()&&c.categoryIds.isEmpty());check(a.pinned&&c.pinned);check(d.categoryIds.contains(history));check(a.title.equals("雪国的故事")&&a.opened==10);
        check(shelf.visible(books,ShelfOrganization.NONE,"").equals(Arrays.asList(c,a,b)));
        shelf.pin(Collections.singletonList(c),false);check(shelf.visible(books,ShelfOrganization.PINNED,"").equals(Collections.singletonList(a)));
        shelf.assign(Collections.singletonList(d),"");check(d.categoryIds.isEmpty());reject(()->shelf.assign(books,"missing"));reject(()->shelf.delete("missing",books));
        check(shelf.visible(books,"deleted-category","").size()==4);check(shelf.label("missing").equals("全部"));
        // Repeated batch changes must not duplicate, drop, or disturb unrelated entries.
        ArrayList<ShelfOrganization.Entry> large=new ArrayList<>();for(int i=0;i<500;i++)large.add(book(String.format("%04d",i),"书 "+i,i));
        shelf.assign(large,history);shelf.pin(large.subList(0,120),true);
        check(shelf.visible(large,history,"").size()==500);check(shelf.visible(large,ShelfOrganization.PINNED,"").size()==120);
        for(int i=0;i<500;i++){ShelfOrganization.Entry e=large.get(i);check(e.categoryIds.contains(history));check(e.pinned==(i<120));}
        shelf.delete(history,large);for(ShelfOrganization.Entry e:large)check(e.categoryIds.isEmpty());
        String fantasy=shelf.add("玄幻"),following=shelf.add("正在追");
        shelf.assignAll(Arrays.asList(a,b),Arrays.asList(fantasy,following));
        check(a.categoryIds.equals(new LinkedHashSet<>(Arrays.asList(fantasy,following))));
        check(shelf.visible(books,fantasy,"").size()==2&&shelf.visible(books,following,"").size()==2);
        check(shelf.membership(Arrays.asList(a,b),fantasy)==1);check(shelf.membership(Arrays.asList(a,c),fantasy)==2);check(shelf.membership(Collections.singletonList(c),fantasy)==0);
        shelf.updateMembership(Arrays.asList(a,c),Collections.singleton(following),Collections.emptySet());check(c.categoryIds.contains(following));check(a.categoryIds.contains(fantasy));
        shelf.delete(following,books);check(a.categoryIds.contains(fantasy));check(c.categoryIds.isEmpty());check(!a.categoryIds.contains(following));
        shelf.rename(fantasy,"东方玄幻");check(a.categoryIds.contains(fantasy));
        Set<String> before=new HashSet<>(a.categoryIds);reject(()->shelf.assignAll(Collections.singletonList(a),Arrays.asList(fantasy,"missing")));check(a.categoryIds.equals(before));
        shelf.assignAll(Collections.singletonList(a),Collections.emptySet());check(a.categoryIds.isEmpty());
        System.out.println("ShelfOrganizationTest: "+checks+" checks passed");
    }
}
