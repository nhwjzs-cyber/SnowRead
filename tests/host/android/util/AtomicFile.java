package android.util;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** JVM test adapter only. Windows cannot run Android's native/POSIX AtomicFile implementation. */
public final class AtomicFile {
    private final File base,fresh;
    private static final Map<String,Integer> starts=new HashMap<>(),finishes=new HashMap<>();
    public static int startsFor(File file){Integer count=starts.get(file.getAbsolutePath());return count==null?0:count;}
    public static int finishesFor(File file){Integer count=finishes.get(file.getAbsolutePath());return count==null?0:count;}
    public AtomicFile(File file){base=file;fresh=new File(file.getPath()+".new");}
    public File getBaseFile(){return base;}
    public FileInputStream openRead()throws IOException{return new FileInputStream(base);}
    public FileOutputStream startWrite()throws IOException{starts.put(base.getAbsolutePath(),startsFor(base)+1);return new FileOutputStream(fresh);}
    public void finishWrite(FileOutputStream out)throws IOException{out.getFD().sync();out.close();Files.move(fresh.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);finishes.put(base.getAbsolutePath(),finishesFor(base)+1);}
    public void failWrite(FileOutputStream out){try{out.close();}catch(IOException ignored){}fresh.delete();}
    public void delete(){base.delete();fresh.delete();new File(base.getPath()+".bak").delete();}
}
