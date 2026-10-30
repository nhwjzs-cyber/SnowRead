import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Build-only APK normalization and Android 11+ resource-table gate. Requires only the build JDK. */
public final class ApkArchive {
    public static void main(String[] args)throws Exception {
        if(args.length==3&&args[0].equals("normalize")){normalize(new File(args[1]),new File(args[2]));return;}
        if(args.length==2&&args[0].equals("verify")){verify(new File(args[1]));return;}
        throw new IllegalArgumentException("Usage: ApkArchive normalize INPUT OUTPUT | verify APK");
    }
    private static boolean signature(String name){
        String upper=name.toUpperCase(Locale.ROOT);
        return upper.startsWith("META-INF/")&&(upper.endsWith(".SF")||upper.endsWith(".RSA")||upper.endsWith(".DSA")||upper.endsWith(".EC")||upper.equals("META-INF/MANIFEST.MF"));
    }
    private static void normalize(File input,File output)throws Exception {
        if(input.getCanonicalFile().equals(output.getCanonicalFile()))throw new IOException("Input and output must be different files");
        HashSet<String> names=new HashSet<>();int tables=0;
        try(ZipFile zip=new ZipFile(input);ZipOutputStream out=new ZipOutputStream(new FileOutputStream(output))){
            Enumeration<? extends ZipEntry> all=zip.entries();byte[] buffer=new byte[32768];
            while(all.hasMoreElements()){
                ZipEntry old=all.nextElement();String name=old.getName().replace('\\','/');
                if(!names.add(name))throw new IOException("Duplicate normalized APK entry: "+name);
                if(signature(name))continue;
                if(name.equals("resources.arsc"))tables++;
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream in=zip.getInputStream(old)){int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
                byte[] data=bytes.toByteArray();ZipEntry next=new ZipEntry(name);if(old.getTime()>=0)next.setTime(old.getTime());
                String lower=name.toLowerCase(Locale.ROOT);
                if(name.equals("resources.arsc")||lower.endsWith(".otf")||lower.endsWith(".ttf")||old.getMethod()==ZipEntry.STORED){
                    CRC32 crc=new CRC32();crc.update(data);next.setMethod(ZipEntry.STORED);next.setSize(data.length);next.setCompressedSize(data.length);next.setCrc(crc.getValue());
                }else next.setMethod(ZipEntry.DEFLATED);
                out.putNextEntry(next);out.write(data);out.closeEntry();
            }
            if(tables!=1)throw new IOException("APK must contain exactly one resources.arsc");
        }
        System.out.println("APK names normalized; resources.arsc explicitly STORED (method 0). Align and sign next.");
    }
    private static int u16(byte[] data,int at){return (data[at]&255)|((data[at+1]&255)<<8);}
    private static long u32(byte[] data,int at){return (u16(data,at)&65535L)|((long)u16(data,at+2)<<16);}
    private static void verify(File file)throws Exception {
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            long length=in.length();if(length<22)throw new IOException("Not a ZIP/APK");
            int tailLength=(int)Math.min(length,65557);byte[] tail=new byte[tailLength];in.seek(length-tailLength);in.readFully(tail);int end=-1;
            for(int i=tailLength-22;i>=0;i--)if(u32(tail,i)==0x06054b50L&&i+22+u16(tail,i+20)==tailLength){end=i;break;}
            if(end<0)throw new IOException("Missing ZIP end record");
            int count=u16(tail,end+10);long central=u32(tail,end+16);
            if(u16(tail,end+4)!=0||u16(tail,end+6)!=0||count==65535||central==0xffffffffL)throw new IOException("Unsupported split/ZIP64 APK");
            HashSet<String> names=new HashSet<>();int tables=0;long position=central;
            for(int i=0;i<count;i++){
                byte[] head=new byte[46];in.seek(position);in.readFully(head);if(u32(head,0)!=0x02014b50L)throw new IOException("Invalid ZIP central header");
                int nameLength=u16(head,28);byte[] rawName=new byte[nameLength];in.readFully(rawName);String name=new String(rawName,StandardCharsets.UTF_8);
                if(name.indexOf('\\')>=0||!names.add(name))throw new IOException("Invalid or duplicate APK name: "+name);
                if(name.equals("resources.arsc")){
                    tables++;int method=u16(head,10);if(method!=ZipEntry.STORED)throw new IOException("resources.arsc must be STORED (method 0), found method "+method+"; Android 11+ rejects this APK");
                    long local=u32(head,42);byte[] localHead=new byte[30];in.seek(local);in.readFully(localHead);
                    if(u32(localHead,0)!=0x04034b50L||u16(localHead,8)!=ZipEntry.STORED)throw new IOException("Resource local ZIP header mismatch");
                    byte[] localName=new byte[u16(localHead,26)];in.readFully(localName);if(!Arrays.equals(rawName,localName))throw new IOException("Resource local name mismatch");
                    long offset=local+30+localName.length+u16(localHead,28);if(offset%4!=0)throw new IOException("resources.arsc data must be 4-byte aligned, found offset "+offset);
                    System.out.println("resources.arsc: STORED (method 0), data offset "+offset+", 4-byte aligned.");
                }
                position+=46L+nameLength+u16(head,30)+u16(head,32);
            }
            if(tables!=1)throw new IOException("APK must contain exactly one resources.arsc");
        }
        System.out.println("Android resource-table packaging gate passed.");
    }
}
