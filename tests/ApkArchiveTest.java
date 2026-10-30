import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Host-only regression tests for install-blocking APK archive errors. */
public final class ApkArchiveTest {
    private static int checks;
    private interface Work {void run()throws Exception;}
    private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static void reject(Work work)throws Exception{boolean failed=false;try{work.run();}catch(IOException expected){failed=true;}check(failed,"Invalid archive accepted");}
    private static void create(File file,boolean stored,String... names)throws Exception{
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){
            for(String name:names){byte[] data={1};ZipEntry entry=new ZipEntry(name);entry.setTime(1767225600000L);
                if(stored){CRC32 crc=new CRC32();crc.update(data);entry.setMethod(ZipEntry.STORED);entry.setSize(1);entry.setCrc(crc.getValue());}
                out.putNextEntry(entry);out.write(data);out.closeEntry();
            }
        }
    }
    private static void verify(File file)throws Exception{ApkArchive.main(new String[]{"verify",file.getPath()});}
    private static void normalize(File input,File output)throws Exception{ApkArchive.main(new String[]{"normalize",input.getPath(),output.getPath()});}
    public static void main(String[] args)throws Exception{
        File home=Files.createTempDirectory(Paths.get(args[0]),"archive-").toFile();File bad=new File(home,"deflated.apk"),good=new File(home,"fixed.apk");
        create(bad,false,"resources.arsc");reject(()->verify(bad));normalize(bad,good);verify(good);
        try(ZipFile original=new ZipFile(bad);ZipFile fixed=new ZipFile(good)){
            check(fixed.getEntry("resources.arsc").getMethod()==ZipEntry.STORED,"Normalizer must use method zero");check(fixed.getInputStream(fixed.getEntry("resources.arsc")).read()==original.getInputStream(original.getEntry("resources.arsc")).read(),"Payload changed");
        }
        File unaligned=new File(home,"unaligned.apk");create(unaligned,true,"pad","resources.arsc");reject(()->verify(unaligned));
        File slash=new File(home,"slash.apk"),normalized=new File(home,"slash-fixed.apk");create(slash,true,"resources.arsc","assets\\fonts\\sample.otf");reject(()->verify(slash));normalize(slash,normalized);verify(normalized);
        try(ZipFile zip=new ZipFile(normalized)){check(zip.getEntry("assets/fonts/sample.otf")!=null,"Asset path was not normalized");check(zip.getEntry("assets/fonts/sample.otf").getMethod()==ZipEntry.STORED,"Font must stay stored");}
        File duplicate=new File(home,"duplicate.apk");create(duplicate,true,"resources.arsc","assets\\x","assets/x");reject(()->normalize(duplicate,new File(home,"duplicate-fixed.apk")));
        File missing=new File(home,"missing.apk");create(missing,true,"asset");reject(()->verify(missing));reject(()->normalize(missing,new File(home,"missing-fixed.apk")));
        long length=bad.length();reject(()->normalize(bad,bad));check(bad.length()==length,"Same-path rejection damaged original input");
        System.out.println("ApkArchiveTest: "+checks+" checks passed; compression, raw alignment, paths, payload preservation, duplicates, missing table and input protection.");
    }
}
