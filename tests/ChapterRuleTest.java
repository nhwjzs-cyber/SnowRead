package com.snowread.app;

/** Pure recognition half of the chapter-rule tests: AUTO compatibility, selectable rules,
 *  offsets across a swap and the 10000-entry cap. Persistence lives in ChapterRuleStorageTest. */
public final class ChapterRuleTest {
    static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
    private static TextDocument doc(String body,int rule){return new TextDocument(body,"UTF-8",rule);}
    private static final String BODY="序章\n楔子的内容\n第一章 起点\n正文一\n第二章 河谷\n正文二\nChapter 3\nenglish body\n第3卷 长风\n卷内容\n第四章（终）\n正文三\n他说第一章写得好。\n";
    public static void main(String[] args){
        // AUTO keeps the legacy behavior on a mixed book: preface, 楔子 line, numbered, English, volume.
        TextDocument auto=doc(BODY,ChapterRule.AUTO);
        check(auto.chapters.size()==7);
        check("序章".equals(auto.chapters.get(0).title)&&auto.chapters.get(0).kind==TextDocument.Chapter.SPECIAL&&auto.chapters.get(0).offset==0);
        check("楔子的内容".equals(auto.chapters.get(1).title)&&auto.chapters.get(1).kind==TextDocument.Chapter.SPECIAL);
        check(auto.chapters.get(2).title.startsWith("第一章")&&auto.chapters.get(2).number==1&&auto.chapters.get(2).kind==TextDocument.Chapter.CHAPTER);
        check(auto.chapters.get(3).title.startsWith("第二章")&&auto.chapters.get(3).number==2);
        check(auto.chapters.get(4).title.startsWith("Chapter 3")&&auto.chapters.get(4).number==3);
        check(auto.chapters.get(5).kind==TextDocument.Chapter.VOLUME&&auto.chapters.get(5).number==3);
        check(auto.chapters.get(6).title.startsWith("第四章"));
        check(auto.numberedChapters.size()==4&&auto.volumes.size()==1&&!auto.chaptersTruncated);
        // AUTO ignores mid-sentence mentions: only standalone heading lines match.
        TextDocument mention=doc("他说第一章写得好。\n第二章 真标题\n",ChapterRule.AUTO);
        check(mention.chapters.size()==2&&mention.chapters.get(1).title.startsWith("第二章"));
        // A book without headings falls back to the single 开始阅读 entry.
        TextDocument plain=doc("只有正文，没有标题。\n第二行\n",ChapterRule.AUTO);
        check(plain.chapters.size()==1&&"开始阅读".equals(plain.chapters.get(0).title));
        check(plain.chapterAt(plain.text.length()-1)==0&&plain.sectionEnd(0)==plain.text.length());
        // STANDARD drops the preface and English chapters but keeps numbered/volume lines;
        // the first real heading starts after the preface, so the 开始阅读 fallback appears.
        TextDocument standard=doc(BODY,ChapterRule.STANDARD);
        check(standard.chapters.size()==5);
        check("开始阅读".equals(standard.chapters.get(0).title));
        check(standard.chapters.get(1).title.startsWith("第一章"));
        for(int i=1;i<standard.chapters.size();i++)check(!standard.chapters.get(i).title.startsWith("序章")&&!standard.chapters.get(i).title.startsWith("Chapter"));
        check(standard.numberedChapters.size()==3&&standard.volumes.size()==1);
        // ENGLISH keeps only the Chapter line, again with the fallback before it.
        TextDocument english=doc(BODY,ChapterRule.ENGLISH);
        check(english.chapters.size()==2);
        check("开始阅读".equals(english.chapters.get(0).title)&&english.chapters.get(1).title.startsWith("Chapter 3")&&english.chapters.get(1).number==3);
        // NUMBER_LINES recognizes standalone digit lines (arabic and fullwidth), nothing else.
        TextDocument numbers=doc("第一章 序\n1\n内容一\n２\n内容二\n12345\n不是编号\n03\n",ChapterRule.NUMBER_LINES);
        check(numbers.chapters.size()==4&&"开始阅读".equals(numbers.chapters.get(0).title));
        check(numbers.numberedChapters.size()==3);
        check(numbers.numberedChapters.get(0).number==1&&numbers.numberedChapters.get(1).number==2&&numbers.numberedChapters.get(2).number==3);
        for(TextDocument.Chapter c:numbers.chapters)check(!c.title.startsWith("第一章"));
        // Offsets of shared headings are identical across rules, so a swap preserves positions.
        TextDocument swapped=doc(BODY,ChapterRule.STANDARD);
        int[] shared={2,3,5,6};
        for(int i=0;i<shared.length;i++)check(swapped.chapters.get(i+1).offset==auto.chapters.get(shared[i]).offset);
        int firstOffset=swapped.chapters.get(1).offset;
        check(swapped.chapterAt(firstOffset)==1&&swapped.readingTitle(firstOffset).contains("第一章"));
        // The 10000-entry cap truncates instead of exploding.
        StringBuilder many=new StringBuilder();for(int i=0;i<ChapterRule.MAX_CHAPTERS+5;i++)many.append(i%10000).append('\n');
        TextDocument capped=doc(many.toString(),ChapterRule.NUMBER_LINES);
        check(capped.chapters.size()==ChapterRule.MAX_CHAPTERS&&capped.chaptersTruncated);
        // parseNumber keeps handling the fullwidth digits NUMBER_LINES can produce.
        check(TextDocument.parseNumber("１２３")==123);
        System.out.println("ChapterRuleTest: "+checks+" checks passed; auto compatibility, selectable rules, offsets across a swap and the entry cap");
    }
}
