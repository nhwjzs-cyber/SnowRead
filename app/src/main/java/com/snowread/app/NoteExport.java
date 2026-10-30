package com.snowread.app;
import java.io.*;
import java.util.*;
/** Streaming export preserves quotes and unresolved records without changing TXT. */
final class NoteExport {
    static void write(Writer out,String title,Collection<BookNote> notes)throws IOException{
        out.write("# "+title.replace('\n',' ').replace('\r',' ')+" · 阅读笔记\n\n");
        int i=0;for(BookNote n:notes){out.write("## "+(++i)+" · "+(n.style==0?"高亮":"下划线")+" · "+(n.located()?"字符位置 "+n.start:"待定位")+"\n\n");
            String[] lines=n.quote.split("\\r?\\n",-1);for(String line:lines)out.write("> "+line+"\n");out.write("\n");if(!n.note.isEmpty())out.write(n.note+"\n\n");
        }
    }
}
